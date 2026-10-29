package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.flota.Vehiculo;
import pe.pucp.paqrap.registro.EstadoPedido;
import pe.pucp.paqrap.registro.Pedido;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * AlgoritmoGenetico (diagrama, Modulo B): implementa AlgoritmoMetaheuristico
 * mediante el patron Strategy. Atributos del diagrama: tamanoPoblacion,
 * tasaCruce, tasaMutacion, numeroGeneraciones. +resolver(modelo): Plan.
 *
 * Es el "Algoritmo 1" del equipo (version del 24-sep-2026): internamente
 * resuelve, en dos etapas, las dos responsabilidades asignadas para este
 * algoritmo - asignacion de pedidos a vehiculos (construccion inicial,
 * cheapest insertion) y metaheuristica de rutas (busqueda genetica sobre el
 * orden de visita: cruce OX, mutacion swap, seleccion por torneo y elitismo).
 *
 * Cambios respecto a la version por linea de comandos, para integrarlo al
 * sistema completo:
 * - cada unidad sale del almacen donde esta estacionada (central o
 *   intermedio), y la asignacion respeta el stock que cada almacen todavia
 *   puede despachar en la ventana (B16);
 * - el tiempo de servicio viene del modelo (parametro de la corrida).
 * La logica de la asignacion, el fitness y los operadores geneticos es la misma.
 */
public class AlgoritmoGenetico implements AlgoritmoMetaheuristico {

    // soles ficticios por pedido fuera de plazo: mas que cualquier ahorro de km posible en una ruta,
    // para que el GA nunca cambie un pedido a tiempo por uno tarde (la politica es entregar todo en plazo)
    private static final double PENALIZACION_POR_INCUMPLIMIENTO = 10000.0;
    private static final double PENALIZACION_POR_MANTENIMIENTO = 100000.0; // ruta que vuelve despues de iniciar el mantenimiento

    // Atributos declarados en el diagrama de clases:
    private final int tamanoPoblacion;
    private final double tasaCruce;
    private final double tasaMutacion;
    private final int numeroGeneraciones;

    // Parametros adicionales de implementacion (el diagrama no fija estos detalles):
    private final int tamanoTorneo;
    private final Random random;

    public AlgoritmoGenetico(long semilla, int tamanoPoblacion, int numeroGeneraciones,
                             double tasaCruce, double tasaMutacion, int tamanoTorneo) {
        this.tamanoPoblacion = Math.max(2, tamanoPoblacion);
        this.numeroGeneraciones = Math.max(1, numeroGeneraciones);
        this.tasaCruce = tasaCruce;
        this.tasaMutacion = tasaMutacion;
        this.tamanoTorneo = Math.max(1, tamanoTorneo);
        this.random = new Random(semilla);
    }

    @Override
    public String getNombre() { return "GA"; }

    /** +resolver(modelo): Plan (diagrama). */
    @Override
    public Plan resolver(ModeloRuteo modelo) {
        Asignacion inicial = construirAsignacionInicial(modelo);
        return optimizarConGA(modelo, inicial, "PLAN-GA");
    }

    /** Expone el plan antes de la busqueda genetica, solo para comparacion/demostracion. */
    public Plan obtenerPlanInicial(ModeloRuteo modelo) {
        Asignacion inicial = construirAsignacionInicial(modelo);
        return materializarPlan(modelo, inicial.pedidosPorVehiculo, inicial.noAsignados, "PLAN-INICIAL");
    }

    private static class Asignacion {
        Map<Vehiculo, List<Pedido>> pedidosPorVehiculo;
        List<Pedido> noAsignados;
    }

    // ---------------------------------------------------------------
    // Etapa 1: Asignacion de pedidos a vehiculos (cheapest insertion).
    // ---------------------------------------------------------------
    private Asignacion construirAsignacionInicial(ModeloRuteo modelo) {
        List<Vehiculo> flota = modelo.getFlotaDisponible();
        RedVial redVial = modelo.getRedVial();
        double horaPlanificacion = modelo.getHoraPlanificacion();
        double tiempoServicio = modelo.getTiempoServicioH();
        Map<String, Integer> stockRestante = modelo.copiarStock();

        Map<Vehiculo, List<Pedido>> asignacion = new LinkedHashMap<>();
        Map<Vehiculo, Integer> capacidadRestante = new LinkedHashMap<>();
        Map<Vehiculo, Coordenada> posicionActual = new LinkedHashMap<>();
        Map<Vehiculo, Double> horaActual = new LinkedHashMap<>();
        Map<Vehiculo, Double> limiteRetorno = new LinkedHashMap<>();

        for (Vehiculo v : flota) {
            asignacion.put(v, new ArrayList<>());
            capacidadRestante.put(v, v.getCapacidadPaquetes());
            posicionActual.put(v, v.getAlmacenOrigen());
            horaActual.put(v, horaPlanificacion);
            limiteRetorno.put(v, v.limiteRetorno(horaPlanificacion));
        }

        List<Pedido> ordenados = new ArrayList<>(modelo.getPedidos());
        ordenados.sort(Comparator.comparingDouble(Pedido::getHoraLimite));

        List<Pedido> noAsignados = new ArrayList<>();

        for (Pedido pedido : ordenados) {
            Vehiculo mejorVehiculo = null;
            double mejorCosto = Double.MAX_VALUE;

            for (Vehiculo v : flota) {
                if (!v.estaOperativa(horaPlanificacion)) continue;
                if (capacidadRestante.get(v) < pedido.getCantidadUnidades()) continue;
                // B16: la unidad solo carga lo que todavia tiene su almacen
                if (stockRestante.getOrDefault(v.getAlmacenBase().getIdAlmacen(), 0) < pedido.getCantidadUnidades()) continue;
                double tramo = redVial.calcularDistancia(posicionActual.get(v), pedido.getUbicacion(), horaActual.get(v));
                if (Double.isInfinite(tramo)) continue;
                double tiempoLlegada = v.avanzar(horaActual.get(v), v.calcularTiempoViaje(tramo));
                if (tiempoLlegada > pedido.getHoraLimite()) continue;
                if (!vuelveAntesDelMantenimiento(v, redVial, pedido.getUbicacion(), tiempoLlegada,
                        limiteRetorno.get(v), tiempoServicio)) continue;
                double costoInsercion = v.calcularCostoViaje(tramo);
                if (costoInsercion < mejorCosto) {
                    mejorCosto = costoInsercion;
                    mejorVehiculo = v;
                }
            }

            if (mejorVehiculo == null) {
                noAsignados.add(pedido);
                continue;
            }

            asignacion.get(mejorVehiculo).add(pedido);
            pedido.cambiarEstado(EstadoPedido.ASIGNADO_A_RUTA);
            capacidadRestante.put(mejorVehiculo, capacidadRestante.get(mejorVehiculo) - pedido.getCantidadUnidades());
            String base = mejorVehiculo.getAlmacenBase().getIdAlmacen();
            stockRestante.put(base, stockRestante.get(base) - pedido.getCantidadUnidades());
            double tramo = redVial.calcularDistancia(posicionActual.get(mejorVehiculo), pedido.getUbicacion(),
                    horaActual.get(mejorVehiculo));
            double llegada = mejorVehiculo.avanzar(horaActual.get(mejorVehiculo), mejorVehiculo.calcularTiempoViaje(tramo));
            double nuevaHora = mejorVehiculo.avanzar(llegada, tiempoServicio);
            horaActual.put(mejorVehiculo, nuevaHora);
            posicionActual.put(mejorVehiculo, pedido.getUbicacion());
        }

        // los que no entraron vuelven a "registrado" (siguen pendientes para la siguiente ventana)
        for (Pedido p : noAsignados) p.cambiarEstado(EstadoPedido.REGISTRADO);

        Asignacion resultado = new Asignacion();
        resultado.pedidosPorVehiculo = asignacion;
        resultado.noAsignados = noAsignados;
        return resultado;
    }

    /**
     * Mantenimiento preventivo (P19): si la unidad entrega este pedido, tiene
     * que alcanzar a volver al almacen antes de que empiece su mantenimiento.
     */
    private boolean vuelveAntesDelMantenimiento(Vehiculo v, RedVial redVial, Coordenada destino,
                                                double horaLlegada, double limite, double tiempoServicio) {
        if (Double.isInfinite(limite)) return true;
        double finEntrega = v.avanzar(horaLlegada, tiempoServicio);
        double regreso = redVial.calcularDistancia(destino, v.getAlmacenOrigen(), finEntrega);
        if (Double.isInfinite(regreso)) regreso = redVial.calcularDistancia(destino, v.getAlmacenOrigen());
        return v.avanzar(finEntrega, v.calcularTiempoViaje(regreso)) <= limite;
    }

    // ---------------------------------------------------------------
    // Etapa 2: Metaheuristica genetica sobre el orden de visita.
    // ---------------------------------------------------------------
    private static class Individuo {
        Map<Vehiculo, List<Pedido>> rutas;
        double fitness;
        Individuo(Map<Vehiculo, List<Pedido>> rutas) { this.rutas = rutas; }
    }

    private Plan optimizarConGA(ModeloRuteo modelo, Asignacion inicial, String idPlan) {
        RedVial redVial = modelo.getRedVial();
        double horaPlanificacion = modelo.getHoraPlanificacion();
        double tiempoServicio = modelo.getTiempoServicioH();

        // Sin rutas con 2 o mas paradas no hay orden que optimizar.
        boolean hayOrdenQueOptimizar = inicial.pedidosPorVehiculo.values().stream().anyMatch(l -> l.size() >= 2);
        if (!hayOrdenQueOptimizar) {
            return materializarPlan(modelo, inicial.pedidosPorVehiculo, inicial.noAsignados, idPlan);
        }

        List<Individuo> poblacion = new ArrayList<>();
        Individuo semilla = new Individuo(copiarRutas(inicial.pedidosPorVehiculo));
        semilla.fitness = calcularFitness(redVial, semilla.rutas, horaPlanificacion, tiempoServicio);
        poblacion.add(semilla);
        for (int i = 1; i < tamanoPoblacion; i++) {
            Individuo ind = new Individuo(mezclarAleatoriamente(inicial.pedidosPorVehiculo));
            ind.fitness = calcularFitness(redVial, ind.rutas, horaPlanificacion, tiempoServicio);
            poblacion.add(ind);
        }

        Individuo mejorGlobal = mejorDe(poblacion);

        for (int gen = 0; gen < numeroGeneraciones; gen++) {
            List<Individuo> nuevaPoblacion = new ArrayList<>();
            nuevaPoblacion.add(clonarIndividuo(mejorGlobal)); // elitismo

            while (nuevaPoblacion.size() < tamanoPoblacion) {
                Individuo padre1 = seleccionTorneo(poblacion);
                Individuo padre2 = seleccionTorneo(poblacion);
                Individuo hijo = (random.nextDouble() < tasaCruce) ? cruzar(padre1, padre2) : clonarIndividuo(padre1);
                mutar(hijo);
                hijo.fitness = calcularFitness(redVial, hijo.rutas, horaPlanificacion, tiempoServicio);
                nuevaPoblacion.add(hijo);
            }

            poblacion = nuevaPoblacion;
            Individuo mejorGen = mejorDe(poblacion);
            if (mejorGen.fitness < mejorGlobal.fitness) mejorGlobal = clonarIndividuo(mejorGen);
        }

        return materializarPlan(modelo, mejorGlobal.rutas, inicial.noAsignados, idPlan);
    }

    /**
     * Costo de un individuo: costo por km de cada ruta mas la penalizacion por
     * pedidos fuera de plazo. Cada tramo se mide con los bloqueos vigentes a
     * la hora en que el vehiculo sale hacia esa parada, asi que un orden que
     * pasa por una calle cerrada en ese momento sale mas caro (desvio) y tarda
     * mas, y la busqueda genetica tiende a evitarlo. Los tiempos incluyen la
     * pausa de alimentacion del conductor, y una ruta que no vuelve antes del
     * mantenimiento de la unidad queda descartada por la penalizacion.
     */
    private double calcularFitness(RedVial redVial, Map<Vehiculo, List<Pedido>> rutas, double horaInicio,
                                   double tiempoServicio) {
        double total = 0.0;
        for (Map.Entry<Vehiculo, List<Pedido>> e : rutas.entrySet()) {
            Vehiculo v = e.getKey();
            if (e.getValue().isEmpty()) continue;
            double distancia = 0.0;
            double hora = horaInicio;
            Coordenada pos = v.getAlmacenOrigen();
            int fueraDePlazo = 0;
            for (Pedido p : e.getValue()) {
                double tramo = redVial.calcularDistancia(pos, p.getUbicacion(), hora);
                if (Double.isInfinite(tramo)) tramo = redVial.calcularDistancia(pos, p.getUbicacion());
                distancia += tramo;
                hora = v.avanzar(hora, v.calcularTiempoViaje(tramo));
                if (hora > p.getHoraLimite()) fueraDePlazo++;
                hora = v.avanzar(hora, tiempoServicio);
                pos = p.getUbicacion();
            }
            total += v.calcularCostoViaje(distancia) + fueraDePlazo * PENALIZACION_POR_INCUMPLIMIENTO;

            double limite = v.limiteRetorno(horaInicio);
            if (!Double.isInfinite(limite)) {
                double regreso = redVial.calcularDistancia(pos, v.getAlmacenOrigen(), hora);
                if (Double.isInfinite(regreso)) regreso = redVial.calcularDistancia(pos, v.getAlmacenOrigen());
                if (v.avanzar(hora, v.calcularTiempoViaje(regreso)) > limite) total += PENALIZACION_POR_MANTENIMIENTO;
            }
        }
        return total;
    }

    private Plan materializarPlan(ModeloRuteo modelo, Map<Vehiculo, List<Pedido>> rutasPorVehiculo,
                                  List<Pedido> noAsignados, String idPlan) {
        List<Ruta> rutas = new ArrayList<>();
        int contador = 1;
        for (Map.Entry<Vehiculo, List<Pedido>> e : rutasPorVehiculo.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            Ruta r = new Ruta("R" + (contador++), e.getKey(), modelo.getTiempoServicioH());
            r.establecerOrden(e.getValue(), modelo.getRedVial(), modelo.getHoraPlanificacion());
            rutas.add(r);
        }
        return new Plan(idPlan, "VENTANA", rutas, noAsignados, modelo.getHoraPlanificacion());
    }

    private Individuo seleccionTorneo(List<Individuo> poblacion) {
        Individuo mejor = null;
        for (int i = 0; i < tamanoTorneo; i++) {
            Individuo candidato = poblacion.get(random.nextInt(poblacion.size()));
            if (mejor == null || candidato.fitness < mejor.fitness) mejor = candidato;
        }
        return mejor;
    }

    private Individuo cruzar(Individuo padre1, Individuo padre2) {
        Map<Vehiculo, List<Pedido>> rutasHijo = new LinkedHashMap<>();
        for (Vehiculo v : padre1.rutas.keySet()) {
            rutasHijo.put(v, ordenCruzadoOX(padre1.rutas.get(v), padre2.rutas.get(v)));
        }
        return new Individuo(rutasHijo);
    }

    private List<Pedido> ordenCruzadoOX(List<Pedido> orden1, List<Pedido> orden2) {
        int n = orden1.size();
        if (n < 2) return new ArrayList<>(orden1);
        int a = random.nextInt(n);
        int b = random.nextInt(n);
        int inicio = Math.min(a, b);
        int fin = Math.max(a, b);

        List<Pedido> hijo = new ArrayList<>(Collections.nCopies(n, null));
        Set<String> idsUsados = new HashSet<>();
        for (int i = inicio; i <= fin; i++) {
            hijo.set(i, orden1.get(i));
            idsUsados.add(orden1.get(i).getIdPedido());
        }
        int posicion = (fin + 1) % n;
        for (Pedido p : orden2) {
            if (idsUsados.contains(p.getIdPedido())) continue;
            hijo.set(posicion, p);
            idsUsados.add(p.getIdPedido());
            posicion = (posicion + 1) % n;
        }
        return hijo;
    }

    private void mutar(Individuo individuo) {
        for (List<Pedido> orden : individuo.rutas.values()) {
            if (orden.size() < 2) continue;
            if (random.nextDouble() < tasaMutacion) {
                int i = random.nextInt(orden.size());
                int j = random.nextInt(orden.size());
                Collections.swap(orden, i, j);
            }
        }
    }

    private Map<Vehiculo, List<Pedido>> mezclarAleatoriamente(Map<Vehiculo, List<Pedido>> base) {
        Map<Vehiculo, List<Pedido>> copia = copiarRutas(base);
        for (List<Pedido> orden : copia.values()) Collections.shuffle(orden, random);
        return copia;
    }

    private Map<Vehiculo, List<Pedido>> copiarRutas(Map<Vehiculo, List<Pedido>> base) {
        Map<Vehiculo, List<Pedido>> copia = new LinkedHashMap<>();
        for (Map.Entry<Vehiculo, List<Pedido>> e : base.entrySet()) copia.put(e.getKey(), new ArrayList<>(e.getValue()));
        return copia;
    }

    private Individuo clonarIndividuo(Individuo original) {
        Individuo copia = new Individuo(copiarRutas(original.rutas));
        copia.fitness = original.fitness;
        return copia;
    }

    private Individuo mejorDe(List<Individuo> poblacion) {
        Individuo mejor = poblacion.get(0);
        for (Individuo i : poblacion) if (i.fitness < mejor.fitness) mejor = i;
        return mejor;
    }
}
