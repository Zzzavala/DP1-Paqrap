package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.flota.Vehiculo;
import pe.pucp.paqrap.registro.EstadoPedido;
import pe.pucp.paqrap.registro.Pedido;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * AlgoritmoColoniaHormigas (diagrama, Modulo B): segunda implementacion de
 * AlgoritmoMetaheuristico (patron Strategy), "Optimizacion por Colonia de
 * Hormigas (ACO)" del prototipo v04 y del ISA. Atributos: numeroHormigas,
 * numeroIteraciones, alfa, beta, tasaEvaporacion. +resolver(modelo): Plan.
 *
 * Variante Ant Colony System para ruteo con flota heterogenea, capacidad y
 * plazos (VRP con ventanas de tiempo duras):
 * - Cada hormiga construye una solucion completa: toma las unidades en orden de
 *   costo por paquete (con ruido aleatorio) y, para cada una, agrega pedidos
 *   uno a uno mientras quepan, lleguen dentro del plazo y la unidad alcance a
 *   volver antes de su mantenimiento. El siguiente pedido se elige con la
 *   regla pseudoaleatoria proporcional de ACS: con probabilidad q0 el de mayor
 *   tau^alfa * eta^beta, si no por ruleta. La heuristica eta combina cercania
 *   (1 / (1 + km)) y urgencia (holgura restante del plazo).
 * - Actualizacion local de feromona en cada paso y global (solo la mejor
 *   solucion) al final de cada iteracion; busqueda local 2-opt/intercambio
 *   sobre la mejor hormiga de cada iteracion.
 * - Funcion objetivo: costo por km (S/ 8, 6, 3) mas penalizaciones por pedido
 *   fuera de plazo, por ruta que no vuelve antes del mantenimiento y por pedido
 *   que queda sin atender (mas alta si ya no puede esperar a la siguiente ventana).
 *
 * Usa las mismas reglas que el Genetico (alimentacion del conductor, bloqueos
 * segun la hora de salida de cada tramo, stock de cada almacen, mantenimiento),
 * de modo que la comparacion entre ambos sea sobre el mismo problema. Igual que
 * el ACO del equipo (correcciones del 24-sep), la capacidad es restriccion dura
 * y el motor recibe solo los pedidos que todavia pueden llegar a tiempo,
 * ordenados por plazo, hasta el doble de la capacidad libre.
 */
public class AlgoritmoColoniaHormigas implements AlgoritmoMetaheuristico {

    private static final double PENALIZACION_POR_INCUMPLIMIENTO = 10000.0;
    private static final double PENALIZACION_POR_MANTENIMIENTO = 100000.0;
    private static final double PENALIZACION_NO_ATENDIDO_URGENTE = 10000.0;
    private static final double PENALIZACION_NO_ATENDIDO = 1000.0;
    private static final double EVAPORACION_LOCAL = 0.1;
    private static final double FACTOR_URGENCIA = 2.0;
    private static final int MAX_EVALUACIONES_BUSQUEDA_LOCAL = 4000;

    private final int numeroHormigas;
    private final int numeroIteraciones;
    private final double alfa;
    private final double beta;
    private final double tasaEvaporacion;
    private final double q0;
    private final Random random;

    // Estado de una resolucion (se reinicia en cada llamada a resolver).
    private ModeloRuteo modelo;
    private RedVial redVial;
    private double hora;
    private double tiempoServicio;
    private List<Pedido> candidatos;
    private List<Vehiculo> flota;
    private double[][] feromona;
    private double tau0;
    private boolean[] urgente;

    public AlgoritmoColoniaHormigas(long semilla, int numeroHormigas, int numeroIteraciones,
                                    double alfa, double beta, double tasaEvaporacion, double q0) {
        this.numeroHormigas = Math.max(1, numeroHormigas);
        this.numeroIteraciones = Math.max(1, numeroIteraciones);
        this.alfa = alfa;
        this.beta = beta;
        this.tasaEvaporacion = tasaEvaporacion;
        this.q0 = q0;
        this.random = new Random(semilla);
    }

    @Override
    public String getNombre() { return "ACO"; }

    /** Solucion de una hormiga: secuencia de pedidos por unidad. */
    private static final class Solucion {
        final Map<Vehiculo, List<Integer>> rutas = new LinkedHashMap<>();
        double costo;

        Solucion copia() {
            Solucion s = new Solucion();
            for (Map.Entry<Vehiculo, List<Integer>> e : rutas.entrySet()) s.rutas.put(e.getKey(), new ArrayList<>(e.getValue()));
            s.costo = costo;
            return s;
        }
    }

    /** +resolver(modelo): Plan (diagrama). */
    @Override
    public Plan resolver(ModeloRuteo modelo) {
        this.modelo = modelo;
        this.redVial = modelo.getRedVial();
        this.hora = modelo.getHoraPlanificacion();
        this.tiempoServicio = modelo.getTiempoServicioH();
        this.flota = new ArrayList<>(modelo.getFlotaDisponible());

        List<Pedido> noConsiderados = new ArrayList<>();
        this.candidatos = seleccionarCandidatos(modelo.getPedidos(), noConsiderados);
        if (candidatos.isEmpty() || flota.isEmpty()) {
            List<Pedido> todos = new ArrayList<>(modelo.getPedidos());
            for (Pedido p : todos) p.cambiarEstado(EstadoPedido.REGISTRADO);
            return new Plan("PLAN-ACO", "VENTANA", new ArrayList<>(), todos, hora);
        }

        int n = candidatos.size();
        marcarUrgentes();

        Solucion mejor = construir(true);
        mejor.costo = evaluar(mejor);
        tau0 = 1.0 / (n * Math.max(1.0, costoBase(mejor)));
        feromona = new double[n + flota.size()][n];
        for (double[] fila : feromona) java.util.Arrays.fill(fila, tau0);

        for (int it = 0; it < numeroIteraciones; it++) {
            Solucion mejorIteracion = null;
            for (int k = 0; k < numeroHormigas; k++) {
                Solucion s = construir(false);
                s.costo = evaluar(s);
                if (mejorIteracion == null || s.costo < mejorIteracion.costo) mejorIteracion = s;
            }
            busquedaLocal(mejorIteracion);
            if (mejorIteracion.costo < mejor.costo) mejor = mejorIteracion.copia();
            actualizarFeromonaGlobal(mejor);
        }

        return materializar(mejor, noConsiderados);
    }

    // ---------------------------------------------------------------
    // Preparacion
    // ---------------------------------------------------------------

    /**
     * Pedidos que todavia pueden llegar a tiempo con alguna unidad libre,
     * ordenados por plazo, hasta el doble de la capacidad libre total.
     */
    private List<Pedido> seleccionarCandidatos(List<Pedido> pedidos, List<Pedido> noConsiderados) {
        List<Pedido> ordenados = new ArrayList<>(pedidos);
        ordenados.sort(Comparator.comparingDouble(Pedido::getHoraLimite));
        int capacidadLibre = 0;
        int capacidadMaxima = 0;
        for (Vehiculo v : flota) {
            int cap = Math.min(v.getCapacidadPaquetes(), modelo.stockPara(v));
            capacidadLibre += cap;
            capacidadMaxima = Math.max(capacidadMaxima, cap);
        }
        int tope = Math.max(capacidadMaxima, 2 * capacidadLibre);
        List<Pedido> seleccion = new ArrayList<>();
        int acumulado = 0;
        for (Pedido p : ordenados) {
            boolean alcanzable = false;
            if (p.getCantidadUnidades() <= capacidadMaxima && acumulado + p.getCantidadUnidades() <= tope) {
                for (Vehiculo v : flota) {
                    if (Math.min(v.getCapacidadPaquetes(), modelo.stockPara(v)) < p.getCantidadUnidades()) continue;
                    double d = distancia(v.getAlmacenOrigen(), p.getUbicacion(), hora);
                    if (Double.isInfinite(d)) continue;
                    if (v.avanzar(hora, v.calcularTiempoViaje(d)) <= p.getHoraLimite()) {
                        alcanzable = true;
                        break;
                    }
                }
            }
            if (alcanzable) {
                seleccion.add(p);
                acumulado += p.getCantidadUnidades();
            } else {
                noConsiderados.add(p);
            }
        }
        return seleccion;
    }

    /** Un pedido es urgente si ya no puede esperar a la siguiente ventana de planificacion. */
    private void marcarUrgentes() {
        urgente = new boolean[candidatos.size()];
        double velocidadMaxima = 0;
        for (Vehiculo v : flota) velocidadMaxima = Math.max(velocidadMaxima, v.getVelocidadPromedioKmH());
        double siguienteVentana = hora + modelo.getVentanaPlanificacionH();
        for (int i = 0; i < candidatos.size(); i++) {
            Pedido p = candidatos.get(i);
            double minimo = Double.POSITIVE_INFINITY;
            for (Vehiculo v : flota) minimo = Math.min(minimo, distancia(v.getAlmacenOrigen(), p.getUbicacion(), hora));
            double llegadaMasTemprana = siguienteVentana + (Double.isInfinite(minimo) ? 0 : minimo / velocidadMaxima);
            urgente[i] = llegadaMasTemprana > p.getHoraLimite();
        }
    }

    // ---------------------------------------------------------------
    // Construccion de una solucion (una hormiga)
    // ---------------------------------------------------------------

    private Solucion construir(boolean voraz) {
        int n = candidatos.size();
        boolean[] asignado = new boolean[n];
        Map<String, Integer> stock = modelo.copiarStock();
        Solucion s = new Solucion();

        List<Vehiculo> orden = new ArrayList<>(flota);
        Map<Vehiculo, Double> clave = new HashMap<>();
        for (Vehiculo v : orden) {
            double eficiencia = v.getCostoPorKm() / Math.max(1, v.getCapacidadPaquetes());
            clave.put(v, voraz ? eficiencia : eficiencia * (0.75 + 0.5 * random.nextDouble()));
        }
        orden.sort(Comparator.comparingDouble(clave::get));

        double[] puntaje = new double[n];
        double[] llegada = new double[n];
        for (Vehiculo v : orden) {
            String base = v.getAlmacenBase().getIdAlmacen();
            int capacidad = Math.min(v.getCapacidadPaquetes(), stock.getOrDefault(base, 0));
            if (capacidad <= 0) continue;
            int desde = n + flota.indexOf(v);
            Coordenada pos = v.getAlmacenOrigen();
            double t = hora;
            double limite = v.limiteRetorno(hora);
            List<Integer> ruta = new ArrayList<>();

            while (true) {
                double total = 0.0;
                int mejor = -1;
                double mejorPuntaje = -1;
                for (int j = 0; j < n; j++) {
                    puntaje[j] = 0;
                    if (asignado[j]) continue;
                    Pedido p = candidatos.get(j);
                    if (p.getCantidadUnidades() > capacidad) continue;
                    double d = distancia(pos, p.getUbicacion(), t);
                    if (Double.isInfinite(d)) continue;
                    double arr = v.avanzar(t, v.calcularTiempoViaje(d));
                    if (arr > p.getHoraLimite()) continue;
                    if (!vuelveAntesDelMantenimiento(v, p.getUbicacion(), arr, limite)) continue;
                    double holgura = Math.max(0, Math.min(1, (p.getHoraLimite() - arr) / Math.max(1e-6, p.getPlazoHoras())));
                    double eta = (1.0 / (1.0 + d)) * (1.0 + FACTOR_URGENCIA * (1.0 - holgura) + (urgente[j] ? FACTOR_URGENCIA : 0));
                    double tau = voraz ? 1.0 : feromona[desde][j];
                    puntaje[j] = Math.pow(tau, alfa) * Math.pow(eta, beta);
                    llegada[j] = arr;
                    total += puntaje[j];
                    if (puntaje[j] > mejorPuntaje) {
                        mejorPuntaje = puntaje[j];
                        mejor = j;
                    }
                }
                if (mejor < 0) break;

                int elegido = mejor;
                if (!voraz && random.nextDouble() >= q0 && total > 0) {
                    double r = random.nextDouble() * total;
                    double acumulado = 0;
                    for (int j = 0; j < n; j++) {
                        if (puntaje[j] <= 0) continue;
                        acumulado += puntaje[j];
                        if (acumulado >= r) {
                            elegido = j;
                            break;
                        }
                    }
                }
                if (!voraz) feromona[desde][elegido] = (1 - EVAPORACION_LOCAL) * feromona[desde][elegido] + EVAPORACION_LOCAL * tau0;

                Pedido p = candidatos.get(elegido);
                ruta.add(elegido);
                asignado[elegido] = true;
                capacidad -= p.getCantidadUnidades();
                if (!v.getAlmacenBase().esCentral()) stock.put(base, stock.get(base) - p.getCantidadUnidades());
                t = v.avanzar(llegada[elegido], tiempoServicio);
                pos = p.getUbicacion();
                desde = elegido;
            }
            if (!ruta.isEmpty()) s.rutas.put(v, ruta);
        }
        return s;
    }

    private boolean vuelveAntesDelMantenimiento(Vehiculo v, Coordenada destino, double horaLlegada, double limite) {
        if (Double.isInfinite(limite)) return true;
        double finEntrega = v.avanzar(horaLlegada, tiempoServicio);
        double regreso = distancia(destino, v.getAlmacenOrigen(), finEntrega);
        if (Double.isInfinite(regreso)) regreso = redVial.calcularDistancia(destino, v.getAlmacenOrigen());
        return v.avanzar(finEntrega, v.calcularTiempoViaje(regreso)) <= limite;
    }

    // ---------------------------------------------------------------
    // Evaluacion, busqueda local y feromona
    // ---------------------------------------------------------------

    private double evaluar(Solucion s) {
        double costo = 0.0;
        boolean[] atendido = new boolean[candidatos.size()];
        for (Map.Entry<Vehiculo, List<Integer>> e : s.rutas.entrySet()) {
            costo += costoRuta(e.getKey(), e.getValue());
            for (int j : e.getValue()) atendido[j] = true;
        }
        for (int j = 0; j < atendido.length; j++) {
            if (!atendido[j]) costo += urgente[j] ? PENALIZACION_NO_ATENDIDO_URGENTE : PENALIZACION_NO_ATENDIDO;
        }
        return costo;
    }

    /** Costo exacto de una ruta (mismos calculos de tiempo que Ruta.establecerOrden). */
    private double costoRuta(Vehiculo v, List<Integer> ruta) {
        if (ruta.isEmpty()) return 0.0;
        double distanciaTotal = 0.0;
        double t = hora;
        Coordenada pos = v.getAlmacenOrigen();
        int tarde = 0;
        for (int j : ruta) {
            Pedido p = candidatos.get(j);
            double d = distancia(pos, p.getUbicacion(), t);
            if (Double.isInfinite(d)) d = redVial.calcularDistancia(pos, p.getUbicacion());
            distanciaTotal += d;
            t = v.avanzar(t, v.calcularTiempoViaje(d));
            if (t > p.getHoraLimite()) tarde++;
            t = v.avanzar(t, tiempoServicio);
            pos = p.getUbicacion();
        }
        double costo = v.calcularCostoViaje(distanciaTotal) + tarde * PENALIZACION_POR_INCUMPLIMIENTO;
        double limite = v.limiteRetorno(hora);
        if (!Double.isInfinite(limite)) {
            double regreso = distancia(pos, v.getAlmacenOrigen(), t);
            if (Double.isInfinite(regreso)) regreso = redVial.calcularDistancia(pos, v.getAlmacenOrigen());
            if (v.avanzar(t, v.calcularTiempoViaje(regreso)) > limite) costo += PENALIZACION_POR_MANTENIMIENTO;
        }
        return costo;
    }

    /** Solo el costo por km de las rutas (para el tau0 de referencia). */
    private double costoBase(Solucion s) {
        double total = 0.0;
        for (Map.Entry<Vehiculo, List<Integer>> e : s.rutas.entrySet()) total += costoRuta(e.getKey(), e.getValue());
        return total;
    }

    /** 2-opt e intercambio dentro de cada ruta, aceptando la primera mejora. */
    private void busquedaLocal(Solucion s) {
        int evaluaciones = 0;
        for (Map.Entry<Vehiculo, List<Integer>> e : s.rutas.entrySet()) {
            List<Integer> ruta = e.getValue();
            if (ruta.size() < 2) continue;
            double actual = costoRuta(e.getKey(), ruta);
            boolean mejoro = true;
            while (mejoro && evaluaciones < MAX_EVALUACIONES_BUSQUEDA_LOCAL) {
                mejoro = false;
                for (int i = 0; i < ruta.size() - 1 && !mejoro; i++) {
                    for (int k = i + 1; k < ruta.size() && !mejoro; k++) {
                        List<Integer> invertida = new ArrayList<>(ruta);
                        java.util.Collections.reverse(invertida.subList(i, k + 1));
                        double costo = costoRuta(e.getKey(), invertida);
                        evaluaciones++;
                        if (costo < actual - 1e-9) {
                            ruta.clear();
                            ruta.addAll(invertida);
                            actual = costo;
                            mejoro = true;
                        }
                    }
                }
            }
        }
        s.costo = evaluar(s);
    }

    private void actualizarFeromonaGlobal(Solucion mejor) {
        for (double[] fila : feromona) {
            for (int j = 0; j < fila.length; j++) fila[j] = Math.max(tau0 * 0.01, (1 - tasaEvaporacion) * fila[j]);
        }
        double deposito = tasaEvaporacion / Math.max(1.0, mejor.costo);
        int n = candidatos.size();
        for (Map.Entry<Vehiculo, List<Integer>> e : mejor.rutas.entrySet()) {
            int desde = n + flota.indexOf(e.getKey());
            for (int j : e.getValue()) {
                feromona[desde][j] += deposito;
                desde = j;
            }
        }
    }

    private Plan materializar(Solucion s, List<Pedido> noConsiderados) {
        List<Ruta> rutas = new ArrayList<>();
        boolean[] atendido = new boolean[candidatos.size()];
        int contador = 1;
        for (Map.Entry<Vehiculo, List<Integer>> e : s.rutas.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            List<Pedido> orden = new ArrayList<>();
            for (int j : e.getValue()) {
                Pedido p = candidatos.get(j);
                p.cambiarEstado(EstadoPedido.ASIGNADO_A_RUTA);
                orden.add(p);
                atendido[j] = true;
            }
            Ruta r = new Ruta("R" + (contador++), e.getKey(), tiempoServicio);
            r.establecerOrden(orden, redVial, hora);
            rutas.add(r);
        }
        List<Pedido> noAsignados = new ArrayList<>(noConsiderados);
        for (int j = 0; j < atendido.length; j++) if (!atendido[j]) noAsignados.add(candidatos.get(j));
        for (Pedido p : noAsignados) p.cambiarEstado(EstadoPedido.REGISTRADO);
        return new Plan("PLAN-ACO", "VENTANA", rutas, noAsignados, hora);
    }

    private double distancia(Coordenada origen, Coordenada destino, double t) {
        return redVial.calcularDistancia(origen, destino, t);
    }
}
