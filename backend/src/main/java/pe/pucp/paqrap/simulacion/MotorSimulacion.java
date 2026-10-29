package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.comun.ConversorTiempo;
import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.comun.ParametrosOperacion;
import pe.pucp.paqrap.flota.Almacen;
import pe.pucp.paqrap.flota.AlmacenCentral;
import pe.pucp.paqrap.flota.AlmacenIntermedio;
import pe.pucp.paqrap.flota.Auto;
import pe.pucp.paqrap.flota.Averia;
import pe.pucp.paqrap.flota.Bicicleta;
import pe.pucp.paqrap.flota.Bloqueo;
import pe.pucp.paqrap.flota.JornadaConductor;
import pe.pucp.paqrap.flota.LectorAverias.RegistroAveria;
import pe.pucp.paqrap.flota.LectorBloqueos.RegistroBloqueo;
import pe.pucp.paqrap.flota.Moto;
import pe.pucp.paqrap.flota.TipoAveria;
import pe.pucp.paqrap.flota.TipoVehiculo;
import pe.pucp.paqrap.flota.Turno;
import pe.pucp.paqrap.flota.Vehiculo;
import pe.pucp.paqrap.planificador.ConfiguracionAlgoritmo;
import pe.pucp.paqrap.planificador.EntregasParciales;
import pe.pucp.paqrap.planificador.EventoReplanificacion;
import pe.pucp.paqrap.planificador.ModeloRuteo;
import pe.pucp.paqrap.planificador.Plan;
import pe.pucp.paqrap.planificador.Planificador;
import pe.pucp.paqrap.planificador.ReasignacionCarga;
import pe.pucp.paqrap.planificador.RedVial;
import pe.pucp.paqrap.planificador.ReplanificacionPorAveria;
import pe.pucp.paqrap.planificador.ReplanificacionPorBloqueo;
import pe.pucp.paqrap.planificador.Ruta;
import pe.pucp.paqrap.registro.CanalPedido;
import pe.pucp.paqrap.registro.Cliente;
import pe.pucp.paqrap.registro.EstadoPedido;
import pe.pucp.paqrap.registro.LectorVentas.RegistroVenta;
import pe.pucp.paqrap.registro.Pedido;
import pe.pucp.paqrap.registro.SeguimientoPedido;
import pe.pucp.paqrap.registro.TipoEntrega;
import pe.pucp.paqrap.visualizador.DetalleVisual;
import pe.pucp.paqrap.visualizador.EstadoVisual;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Motor de simulacion de PaqRap (Modulo D: D12, D13, D14; Modulo B: B9-B12,
 * B18-B20). Es el mismo motor para los 3 escenarios: lo unico que cambia es
 * el reloj (lo marca quien llama a avanzarHasta), el origen de los pedidos y
 * la condicion de cierre (B21).
 *
 * Funciona por eventos, en orden de tiempo:
 * - llegada de pedidos (archivo de ventas del mes, registro manual, carga masiva);
 * - ventanas de planificacion cada N minutos (por defecto 60, como el ciclo de
 *   MainIntegracion del Algoritmo 1): se toman los pedidos pendientes y las
 *   unidades libres y se corre el Planificador con el algoritmo elegido (GA o
 *   ACO); ademas se replanifica de inmediato ante un pedido manual o una averia;
 * - hitos de cada unidad: llegada a cada cliente (el plazo se cumple al llegar,
 *   P11), fin de la entrega (1 h, B7) y regreso a un almacen;
 * - inicio de cada bloqueo programado: las unidades en camino que iban a pasar
 *   por el recalculan su recorrido desde donde estan (B9);
 * - averias (D7): tipo 1 retrasa la ruta; tipos 2 y 3 dejan a la unidad en
 *   taller y su carga se transborda (B10) o se cubre con la carga en transito
 *   de otra unidad hacia un cliente con mas holgura (B11);
 * - recarga de los almacenes intermedios a las 23:59:59 (B15, D9) y cierre de dia;
 * - deteccion del colapso logistico (B24, D21).
 *
 * Es seguro para varios hilos: el hilo de la corrida avanza el reloj y la API
 * consulta o envia comandos; todo pasa por un mismo candado, evento por evento.
 */
public class MotorSimulacion {

    private static final double EPS = 1e-9;
    private static final int MAX_EVENTOS_MEMORIA = 800;
    private static final Locale ES = new Locale("es", "PE");

    /**
     * Averia programada. La del archivo trae la unidad; la generada con la
     * semilla trae un selector (0-1) que elige, al momento de la averia, una de
     * las unidades que estan en camino: asi los dos algoritmos enfrentan las
     * mismas averias (misma hora y tipo) sobre unidades que si llevan carga.
     */
    private record AveriaProgramada(double hora, String unidad, double selector, TipoAveria tipo, String origen) {
    }

    /** Pedido que todavia no llega (registro en el futuro). */
    private record PedidoEntrante(Pedido pedido, CanalPedido canal, String observaciones) {
    }

    /** Candidato a ceder su carga en transito (B11). */
    private record Donante(VehiculoSim vehiculo, ParadaEnCurso parada, List<Pedido> secuencia, double costoExtra) {
    }

    private final ReentrantLock candado = new ReentrantLock();

    // ---------------- configuracion ----------------
    private final String idCorrida;
    private final ParametrosCorrida parametros;
    private final ParametrosOperacion operacion;
    private final DatosCorrida datos;
    private final OyenteCorrida oyente;
    private final Planificador planificador;
    private final RedVial red;
    private final JornadaConductor jornada;
    private final List<Turno> turnos;
    private final ConstructorRecorridos constructor;
    private final AlmacenCentral central;
    private final List<Almacen> almacenes = new ArrayList<>();
    private final List<VehiculoSim> vehiculos = new ArrayList<>();
    private final Map<String, VehiculoSim> vehiculoPorId = new LinkedHashMap<>();
    private final int capacidadMaximaFlota;
    private final double ventanaH;

    // ---------------- tiempo ----------------
    private final double horaInicio;
    private final double horaFin;
    private double horaActual;
    private double proximaPlanificacion;
    private boolean planificacionInmediata;
    private double proximaRecarga;
    private double proximoCierreDia;
    private YearMonth ultimoMesCargado;
    private double proximaCargaMes;

    // ---------------- pedidos ----------------
    private final List<PedidoEntrante> colaFutura = new ArrayList<>();
    private int indiceCola;
    private final Map<String, SeguimientoPedido> pedidos = new LinkedHashMap<>();
    private final Map<String, SeguimientoPedido> activos = new LinkedHashMap<>();
    private final LinkedHashSet<Pedido> pendientes = new LinkedHashSet<>();
    private int secuenciaManual;
    private int secuenciaLote;
    private int secuenciaPartes;

    // ---------------- bloqueos y averias ----------------
    private final List<Bloqueo> bloqueosPorIniciar = new ArrayList<>();
    private int indiceBloqueo;
    private int bloqueosProgramadosEnCorrida;
    private int bloqueosManualesTotales;
    private final PriorityQueue<AveriaProgramada> averiasProgramadas =
            new PriorityQueue<>(Comparator.comparingDouble(AveriaProgramada::hora));
    private final List<Averia> historialAverias = new ArrayList<>();
    private final Map<String, Integer> pedidosAfectadosPorAveria = new HashMap<>();

    // ---------------- indicadores ----------------
    private int registrados;
    private int cancelados;
    private int entregados;
    private int entregadosATiempo;
    private int entregadosTarde;
    private int reRuteosBloqueo;
    private int reRuteosAveria;
    private int reasignaciones;
    private int reprogramaciones;
    private int pedidosDivididos;
    private int paradasRecortadas;
    private int rutasDespachadas;
    private int unidadesEntregadas;
    private int contadorRutas;
    private int contadorVentana;
    private int planificaciones;
    private long tiempoComputoNs;
    private int backlogCritico;
    private double costoCerrado;
    private double kmCerrado;
    private final Map<TipoVehiculo, double[]> kmPorTipo = new LinkedHashMap<>(); // {km, costo, rutas}
    private final TreeMap<Integer, Double> cumplimientoAlCierreDia = new TreeMap<>();
    private final Deque<EventoSimulacion> eventos = new ArrayDeque<>();
    private final Map<String, Integer> eventosPorTipo = new LinkedHashMap<>();
    private long secuenciaEventos;
    private InfoColapso colapso;
    private boolean finalizado;
    private String motivoFin;
    private long version;

    public MotorSimulacion(String idCorrida, ParametrosCorrida parametrosCorrida, DatosCorrida datos, OyenteCorrida oyente) {
        parametrosCorrida.normalizar();
        this.idCorrida = idCorrida;
        this.parametros = parametrosCorrida;
        this.operacion = parametrosCorrida.getOperacion();
        this.datos = datos;
        this.oyente = oyente;

        this.jornada = new JornadaConductor(operacion.getHorasCambioTurno(), operacion.getDuracionAlimentacionH());
        this.turnos = Turno.desdeCambios(operacion.getHorasCambioTurno());
        this.red = new RedVial(operacion.getAnchoCiudadKm(), operacion.getAltoCiudadKm());
        this.constructor = new ConstructorRecorridos(red, operacion.getTiempoServicioH());
        this.planificador = new Planificador(new ConfiguracionAlgoritmo(parametros.getAlgoritmo(), parametros.getParametrosAlgoritmo()));

        this.central = new AlmacenCentral("CENTRAL", "Almacén central", operacion.getUbicacionAlmacenCentral());
        almacenes.add(central);
        almacenes.add(new AlmacenIntermedio("NOR-OESTE", "Intermedio Nor-Oeste", operacion.getUbicacionAlmacenNorOeste(),
                operacion.getCapacidadAlmacenIntermedio()));
        almacenes.add(new AlmacenIntermedio("ESTE", "Intermedio Este", operacion.getUbicacionAlmacenEste(),
                operacion.getCapacidadAlmacenIntermedio()));

        // P10, P17, P18: flota oficial, todas las unidades salen del almacen central
        for (int i = 1; i <= operacion.getCantidadAutos(); i++) agregarVehiculo(new Auto(String.format("TA%02d", i), central, operacion, jornada));
        for (int i = 1; i <= operacion.getCantidadMotos(); i++) agregarVehiculo(new Moto(String.format("TM%02d", i), central, operacion, jornada));
        for (int i = 1; i <= operacion.getCantidadBicicletas(); i++) agregarVehiculo(new Bicicleta(String.format("TB%02d", i), central, operacion, jornada));
        int capMax = 0;
        for (VehiculoSim v : vehiculos) capMax = Math.max(capMax, v.getVehiculo().getCapacidadPaquetes());
        this.capacidadMaximaFlota = capMax;
        for (TipoVehiculo t : TipoVehiculo.values()) kmPorTipo.put(t, new double[3]);

        this.ventanaH = parametros.getVentanaPlanificacionMin() / 60.0;
        this.horaInicio = ConversorTiempo.aHoras(parametros.getFechaInicio());
        this.horaFin = parametros.getEscenario() == Escenario.SIMULACION_5D
                ? horaInicio + parametros.getDiasSimulacion() * 24.0 : Double.POSITIVE_INFINITY;
        this.horaActual = horaInicio;
        this.proximaPlanificacion = horaInicio;
        double inicioDia = ConversorTiempo.inicioDelDia(horaInicio);
        this.proximaRecarga = inicioDia + 24.0 - 1.0 / 3600.0;
        this.proximoCierreDia = inicioDia + 24.0;

        YearMonth mes = YearMonth.from(parametros.getFechaInicio());
        cargarMes(mes);
        while (proximaCargaMes <= horaInicio + EPS) cargarMes(ultimoMesCargado.plusMonths(1));
        generarAveriasPorCantidad();
    }

    private void agregarVehiculo(Vehiculo v) {
        VehiculoSim vs = new VehiculoSim(v);
        vehiculos.add(vs);
        vehiculoPorId.put(v.getIdVehiculo(), vs);
    }

    // =====================================================================
    // Avance del reloj
    // =====================================================================

    /** Procesa, en orden, todos los eventos hasta la hora objetivo (horas absolutas). */
    public void avanzarHasta(double objetivo) {
        double limite = Math.min(objetivo, horaFin);
        while (true) {
            candado.lock();
            try {
                if (finalizado) return;
                double t = proximoEvento();
                if (t > limite + EPS) {
                    if (limite > horaActual) {
                        horaActual = limite;
                        ingresarPedidosHasta(horaActual);
                        version++;
                    }
                    if (horaActual >= horaFin - EPS) finalizar("FIN_PERIODO");
                    return;
                }
                horaActual = Math.max(horaActual, finDelLote(t));
                procesarEventosEn(horaActual);
                version++;
            } finally {
                candado.unlock();
            }
        }
    }

    private double proximoEvento() {
        double t = proximaPlanificacion;
        if (planificacionInmediata) t = Math.min(t, horaActual);
        if (indiceBloqueo < bloqueosPorIniciar.size()) t = Math.min(t, bloqueosPorIniciar.get(indiceBloqueo).getHoraInicio());
        for (VehiculoSim vs : vehiculos) {
            t = Math.min(t, vs.proximoHito(horaActual));
            // P19 (nota 2): inicio del mantenimiento de una unidad que todavia lleva pedidos
            if (vs.getRuta() != null && vs.getRuta().siguienteParada() != null) {
                double mant = vs.getVehiculo().limiteRetorno(horaActual);
                if (mant > horaActual + EPS) t = Math.min(t, mant);
            }
        }
        if (!averiasProgramadas.isEmpty()) t = Math.min(t, averiasProgramadas.peek().hora());
        t = Math.min(t, proximaRecarga);
        t = Math.min(t, proximoCierreDia);
        t = Math.min(t, proximaCargaMes);
        return Math.max(t, horaActual);
    }

    /**
     * Hora del ultimo de los eventos que se procesan juntos (los que caen a menos de EPS de t). Las horas de
     * los eventos se calculan por caminos distintos (ventanas acumuladas, inicio de mes + dia/hora del archivo,
     * trayectorias) y dos eventos "simultaneos" pueden diferir en ~1e-12; procesarlos en la hora del ultimo
     * evita, por ejemplo, planificar una fraccion antes de que empiece un bloqueo y que la ruta no lo vea.
     */
    private double finDelLote(double t) {
        double limite = t + EPS, fin = t;
        if (proximaPlanificacion <= limite) fin = Math.max(fin, proximaPlanificacion);
        for (int i = indiceBloqueo; i < bloqueosPorIniciar.size() && bloqueosPorIniciar.get(i).getHoraInicio() <= limite; i++) {
            fin = Math.max(fin, bloqueosPorIniciar.get(i).getHoraInicio());
        }
        for (VehiculoSim vs : vehiculos) {
            double h = vs.proximoHito(horaActual);
            if (h <= limite) fin = Math.max(fin, h);
        }
        if (!averiasProgramadas.isEmpty() && averiasProgramadas.peek().hora() <= limite) fin = Math.max(fin, averiasProgramadas.peek().hora());
        if (proximaRecarga <= limite) fin = Math.max(fin, proximaRecarga);
        if (proximoCierreDia <= limite) fin = Math.max(fin, proximoCierreDia);
        if (proximaCargaMes <= limite) fin = Math.max(fin, proximaCargaMes);
        return fin;
    }

    private void procesarEventosEn(double t) {
        ingresarPedidosHasta(t);
        procesarHitosVehiculos(t);
        procesarInicioMantenimientos(t);
        procesarInicioBloqueos(t);
        procesarAverias(t);
        if (proximaCargaMes <= t + EPS) cargarMes(ultimoMesCargado.plusMonths(1));
        if (proximaRecarga <= t + EPS) {
            for (Almacen a : almacenes) if (a instanceof AlmacenIntermedio ai) ai.recargar();
            proximaRecarga += 24.0;
        }
        if (proximoCierreDia <= t + EPS) {
            int dia = diaDe(proximoCierreDia - 1.0);
            cumplimientoAlCierreDia.put(dia, cumplimientoActual());
            proximoCierreDia += 24.0;
        }
        boolean periodica = proximaPlanificacion <= t + EPS;
        if (periodica || planificacionInmediata) {
            planificar(t, periodica ? "PLANIFICACION" : "REPLANIFICACION_INMEDIATA");
            planificacionInmediata = false;
            if (periodica) {
                while (proximaPlanificacion <= t + EPS) proximaPlanificacion += ventanaH;
            }
            verificarColapso(t);
        }
    }

    // =====================================================================
    // Carga de datos por mes
    // =====================================================================

    private void cargarMes(YearMonth mes) {
        double base = ConversorTiempo.inicioDeMes(mes);
        String prefijo = String.format("%02d%02d", mes.getYear() % 100, mes.getMonthValue());

        // ventas del mes
        List<PedidoEntrante> nuevos = new ArrayList<>();
        for (RegistroVenta r : datos.ventas(mes)) {
            double h = base + r.horasDesdeInicioDeMes();
            if (h < horaInicio - EPS || h >= horaFin) continue;
            TipoEntrega tipo = TipoEntrega.desdePlazo(r.plazoHoras());
            Cliente c = new Cliente(r.idCliente(), "Cliente " + r.idCliente(), new Coordenada(r.destinoX(), r.destinoY()));
            String id = String.format("PV%s-%04d", prefijo, r.numeroLinea());
            nuevos.add(new PedidoEntrante(new Pedido(id, c, r.cantidad(), tipo, r.plazoHoras(), h), CanalPedido.ARCHIVO_VENTAS, null));
        }
        agregarACola(nuevos);

        // bloqueos del mes
        List<Bloqueo> bloqueos = new ArrayList<>();
        for (RegistroBloqueo r : datos.bloqueos(mes)) {
            double ini = base + r.inicioRelativo();
            double fin = base + r.finRelativo();
            if (fin <= horaInicio || ini >= horaFin) continue;
            bloqueos.add(new Bloqueo("BQ" + prefijo + "-" + r.numeroLinea(), ini, fin, r.vertices()));
        }
        bloqueosProgramadosEnCorrida += bloqueos.size();
        red.agregarBloqueosProgramados(bloqueos, horaActual - 1.0);
        List<Bloqueo> porIniciar = new ArrayList<>(bloqueosPorIniciar.subList(indiceBloqueo, bloqueosPorIniciar.size()));
        for (Bloqueo b : bloqueos) if (b.getHoraInicio() > horaActual + EPS) porIniciar.add(b);
        porIniciar.sort(Comparator.comparingDouble(Bloqueo::getHoraInicio));
        bloqueosPorIniciar.clear();
        bloqueosPorIniciar.addAll(porIniciar);
        indiceBloqueo = 0;

        // P19: mantenimiento preventivo del mes
        Map<String, List<double[]>> periodos = datos.getMantenimiento().periodosDelMes(mes);
        for (VehiculoSim vs : vehiculos) {
            List<double[]> p = periodos.get(vs.getId());
            if (p != null) vs.getVehiculo().agregarMantenimiento(p);
        }

        // averias por tasa diaria (escenarios sin fin fijo)
        if (parametros.getEscenario() != Escenario.SIMULACION_5D && datos.getAverias() == null && parametros.isGenerarAverias()) {
            generarAveriasDelMes(mes);
        }

        ultimoMesCargado = mes;
        proximaCargaMes = ConversorTiempo.inicioDeMes(mes.plusMonths(1)) - 72.0;
        if (colaFutura.size() > 20000 && indiceCola > 10000) {
            colaFutura.subList(0, indiceCola).clear();
            indiceCola = 0;
        }
    }

    private void agregarACola(List<PedidoEntrante> nuevos) {
        if (nuevos.isEmpty()) return;
        List<PedidoEntrante> restantes = new ArrayList<>(colaFutura.subList(indiceCola, colaFutura.size()));
        restantes.addAll(nuevos);
        restantes.sort(Comparator.comparingDouble(e -> e.pedido().getHoraRegistro()));
        colaFutura.clear();
        colaFutura.addAll(restantes);
        indiceCola = 0;
    }

    private void ingresarPedidosHasta(double t) {
        while (indiceCola < colaFutura.size() && colaFutura.get(indiceCola).pedido().getHoraRegistro() <= t + EPS) {
            PedidoEntrante e = colaFutura.get(indiceCola++);
            registrar(e.pedido(), e.canal(), e.observaciones());
        }
    }

    private SeguimientoPedido registrar(Pedido p, CanalPedido canal, String observaciones) {
        SeguimientoPedido s = new SeguimientoPedido(p, canal, observaciones);
        pedidos.put(p.getIdPedido(), s);
        activos.put(p.getIdPedido(), s);
        pendientes.add(p);
        registrados++;
        return s;
    }

    // =====================================================================
    // Averias programadas
    // =====================================================================

    private void generarAveriasPorCantidad() {
        if (datos.getAverias() != null) {
            double base = ConversorTiempo.inicioDeMes(YearMonth.from(parametros.getFechaInicio()));
            for (RegistroAveria r : datos.getAverias()) {
                double h = base + r.horaRelativa();
                if (h < horaInicio || h >= horaFin || !vehiculoPorId.containsKey(r.codigoUnidad())) continue;
                averiasProgramadas.add(new AveriaProgramada(h, r.codigoUnidad(), 0, r.tipo(), "ARCHIVO"));
            }
            return;
        }
        if (parametros.getEscenario() != Escenario.SIMULACION_5D || !parametros.isGenerarAverias()) return;
        int cantidad = (int) Math.round(parametros.getAveriasPorDia() * parametros.getDiasSimulacion());
        Random rnd = new Random(parametros.getSemilla() * 31L + 7L);
        double duracion = parametros.getDiasSimulacion() * 24.0;
        for (int i = 0; i < cantidad; i++) {
            double h = horaInicio + 4.0 + rnd.nextDouble() * Math.max(1.0, duracion - 12.0);
            averiasProgramadas.add(new AveriaProgramada(h, null, rnd.nextDouble(), tipoAleatorio(rnd), "PROGRAMADA"));
        }
    }

    private void generarAveriasDelMes(YearMonth mes) {
        double base = ConversorTiempo.inicioDeMes(mes);
        for (int d = 0; d < mes.lengthOfMonth(); d++) {
            double inicioDia = base + d * 24.0;
            if (inicioDia + 24.0 <= horaInicio) continue;
            Random rnd = new Random(parametros.getSemilla() * 1_000_003L + (long) Math.floor(inicioDia / 24.0) * 7919L);
            int cantidad = poisson(parametros.getAveriasPorDia(), rnd);
            for (int i = 0; i < cantidad; i++) {
                double h = inicioDia + rnd.nextDouble() * 24.0;
                double selector = rnd.nextDouble();
                TipoAveria tipo = tipoAleatorio(rnd);
                if (h <= horaInicio) continue;
                averiasProgramadas.add(new AveriaProgramada(h, null, selector, tipo, "PROGRAMADA"));
            }
        }
    }

    private static TipoAveria tipoAleatorio(Random rnd) {
        double r = rnd.nextDouble();
        return r < 0.5 ? TipoAveria.TIPO_1 : (r < 0.85 ? TipoAveria.TIPO_2 : TipoAveria.TIPO_3);
    }

    private static int poisson(double lambda, Random rnd) {
        if (lambda <= 0) return 0;
        double l = Math.exp(-lambda), p = 1.0;
        int k = 0;
        do {
            k++;
            p *= rnd.nextDouble();
        } while (p > l && k < 50);
        return k - 1;
    }

    private void procesarAverias(double t) {
        while (!averiasProgramadas.isEmpty() && averiasProgramadas.peek().hora() <= t + EPS) {
            AveriaProgramada a = averiasProgramadas.poll();
            VehiculoSim vs = a.unidad() != null ? vehiculoPorId.get(a.unidad()) : elegirUnidadParaAveria(t, a.selector());
            if (vs != null) aplicarAveria(vs, a.tipo(), t, "Avería " + (a.origen().equals("ARCHIVO") ? "del archivo de averías" : "programada"), a.origen());
        }
    }

    /** Unidad que sufre una averia generada: una de las que van con carga; si ninguna, una de las operativas. */
    private VehiculoSim elegirUnidadParaAveria(double t, double selector) {
        List<VehiculoSim> candidatas = new ArrayList<>();
        for (VehiculoSim vs : vehiculos) {
            RutaEnCurso r = vs.getRuta();
            if (r != null && r.siguienteParada() != null && vs.getAveria() == null) candidatas.add(vs);
        }
        if (candidatas.isEmpty()) {
            for (VehiculoSim vs : vehiculos) if (vs.getAveria() == null && vs.getVehiculo().estaOperativa(t)) candidatas.add(vs);
        }
        if (candidatas.isEmpty()) return null;
        return candidatas.get(Math.min(candidatas.size() - 1, (int) (selector * candidatas.size())));
    }

    // =====================================================================
    // Hitos de las unidades: entregas, regresos y fin de averias
    // =====================================================================

    private void procesarHitosVehiculos(double t) {
        boolean hubo = true;
        int guarda = 0;
        while (hubo && guarda++ < 10000) {
            hubo = false;
            for (VehiculoSim vs : vehiculos) {
                double h = vs.proximoHito(t);
                if (h <= t + EPS) {
                    procesarHito(vs, t);
                    hubo = true;
                }
            }
        }
    }

    private void procesarHito(VehiculoSim vs, double t) {
        RutaEnCurso r = vs.getRuta();
        if (r != null) {
            ParadaEnCurso p = r.siguienteParada();
            if (p != null && p.getHoraLlegada() <= t + EPS) {
                entregar(vs, p);
                return;
            }
            if (p == null && r.getHoraRegreso() <= t + EPS) {
                terminarRuta(vs, r);
                return;
            }
            return;
        }
        Averia a = vs.getAveria();
        if (a != null && t >= a.getHoraDisponible() - EPS) {
            vs.limpiarAveria();
            vs.getVehiculo().setEstadoOperativo("DISPONIBLE");
            vs.registrarEvento(t, "DISPONIBLE", "Sale del taller y vuelve a estar disponible en " + vs.getVehiculo().getAlmacenBase().getNombre() + ".");
        }
    }

    private void entregar(VehiculoSim vs, ParadaEnCurso p) {
        p.marcarEntregada();
        Pedido parte = p.getParte();
        SeguimientoPedido s = pedidos.get(parte.getIdPedidoOrigen());
        vs.registrarEntrega(parte.getCantidadUnidades());
        unidadesEntregadas += parte.getCantidadUnidades();
        if (s == null) return;
        s.registrarEntrega(parte, p.getHoraLlegada(), vs.getId());
        registrarEntregaReciente(vs, parte, p.getHoraLlegada());
        if (s.estaEntregado() && activos.remove(s.getIdPedido()) != null) {
            entregados++;
            if (s.fueEntregadoATiempo()) entregadosATiempo++; else entregadosTarde++;
        }
    }

    /** Solo para el visualizador (E22): entregas de las ultimas 24 h simuladas, por almacen de salida. No afecta la simulacion. */
    private record EntregaReciente(String almacen, String pedido, double hora) {
    }

    private final Deque<EntregaReciente> entregasRecientes = new ArrayDeque<>();

    private void registrarEntregaReciente(VehiculoSim vs, Pedido parte, double hora) {
        RutaEnCurso r = vs.getRuta();
        if (r == null) return;
        entregasRecientes.addLast(new EntregaReciente(r.getAlmacenSalida().getIdAlmacen(), parte.getIdPedidoOrigen(), hora));
        while (!entregasRecientes.isEmpty() && entregasRecientes.peekFirst().hora() < hora - 24.0) entregasRecientes.pollFirst();
    }

    private void terminarRuta(VehiculoSim vs, RutaEnCurso r) {
        Vehiculo v = vs.getVehiculo();
        double km = r.getTrayectoria().kmTotal();
        acumularKm(vs, r, km, r.getHoraRegreso());
        int sobrante = 0;
        for (ParadaEnCurso p : r.getParadas()) if (p.estaRetirada()) sobrante += p.getParte().getCantidadUnidades();
        if (sobrante > 0) r.getAlmacenRetorno().recibir(sobrante);
        v.setAlmacenBase(r.getAlmacenRetorno());
        r.cerrar(r.getHoraRegreso());
        vs.setRuta(null);
        if (vs.getAveria() != null && r.getHoraRegreso() >= vs.getAveria().getHoraFinInmovilizacion()) vs.limpiarAveria();
        vs.registrarEvento(r.getHoraRegreso(), "REGRESO", "Llega a " + r.getAlmacenRetorno().getNombre()
                + (esReubicacion(r) ? " (reubicación)." : " y queda libre para la siguiente ventana de planificación."));
    }

    /** Suma lo recorrido por una ruta (terminada o cerrada en camino) a los indicadores. */
    private void acumularKm(VehiculoSim vs, RutaEnCurso r, double km, double horaCierre) {
        Vehiculo v = vs.getVehiculo();
        double costo = km * v.getCostoPorKm();
        costoCerrado += costo;
        kmCerrado += km;
        double[] acum = kmPorTipo.get(v.getTipo());
        acum[0] += km;
        acum[1] += costo;
        boolean original = !esContinuacion(r.getCausa()) && !r.getParadas().isEmpty();
        if (original) acum[2] += 1;
        double factor = v.getCapacidadPaquetes() == 0 ? 0 : (double) r.getCargaInicial() / v.getCapacidadPaquetes();
        vs.acumularRuta(km, costo, Math.max(0, horaCierre - r.getHoraInicio()), factor, original);
    }

    private static boolean esContinuacion(String causa) {
        return causa.startsWith("RECALCULO") || causa.startsWith("AVERIA") || causa.equals("REASIGNACION")
                || causa.equals("RETORNO_MANTENIMIENTO");
    }

    /** Ruta sin entregas para llevar una unidad vacia a otro almacen (no un retorno). */
    private static boolean esReubicacion(RutaEnCurso r) {
        return r.getParadas().isEmpty() && !"RETORNO_MANTENIMIENTO".equals(r.getCausa());
    }

    // =====================================================================
    // Planificacion
    // =====================================================================

    /** Solo para el cierre (G08-G10): hora y causa de la ultima planificacion. */
    private double horaUltimaPlanificacion = Double.NaN;
    private String causaUltimaPlanificacion = "";

    private void planificar(double t, String causa) {
        red.descartarCacheAnteriorA(t);
        planificaciones++;
        horaUltimaPlanificacion = t;
        causaUltimaPlanificacion = causa;
        aplicarCambioDeVelocidad(t);
        reprogramarInviables(t);
        reubicarUnidadesSinStock(t);

        List<Pedido> disponibles = new ArrayList<>(pendientes);
        List<Vehiculo> libres = new ArrayList<>();
        for (VehiculoSim vs : vehiculos) {
            if (vs.getRuta() == null && vs.getAveria() == null && vs.getVehiculo().estaOperativa(t)) libres.add(vs.getVehiculo());
        }
        if (disponibles.isEmpty() || libres.isEmpty()) {
            backlogCritico = contarCriticos(t);
            return;
        }

        // P13: si ninguna unidad libre puede llevar un pedido completo y no puede esperar, se entrega en partes
        for (EntregasParciales.Division d : EntregasParciales.dividir(disponibles, libres, red, t, ventanaH, capacidadMaximaFlota)) {
            pendientes.remove(d.original());
            pendientes.addAll(d.partes());
            SeguimientoPedido s = pedidos.get(d.original().getIdPedidoOrigen());
            if (s != null) {
                s.reemplazarParte(d.original(), d.partes());
                s.registrarEvento(t, "DIVISION", "Se entrega en " + d.partes().size() + " partes (ninguna unidad libre lo lleva completo, P13).");
            }
            pedidosDivididos++;
        }

        ModeloRuteo modelo = new ModeloRuteo(disponibles, libres, almacenes, red, turnos, t,
                operacion.getTiempoServicioH(), ventanaH);
        long inicio = System.nanoTime();
        Plan plan = planificador.generarPlan(modelo, parametros.getSemilla() + contadorVentana++);
        tiempoComputoNs += System.nanoTime() - inicio;

        for (Ruta ruta : plan.getRutas()) {
            if (ruta.getParadas().isEmpty()) continue;
            Vehiculo v = ruta.getVehiculoAsignado();
            List<Pedido> orden = new ArrayList<>(ruta.getPedidosEnOrden());
            // P19: la unidad debe estar de vuelta antes de su mantenimiento; si no, se quitan paradas del final
            double limite = v.limiteRetorno(t);
            if (!Double.isInfinite(limite)) {
                Ruta prueba = new Ruta(ruta.getIdRuta(), v, operacion.getTiempoServicioH());
                prueba.establecerOrden(orden, red, t);
                while (!orden.isEmpty() && prueba.calcularHoraRegreso(red) > limite) {
                    Pedido quitado = orden.remove(orden.size() - 1);
                    quitado.cambiarEstado(EstadoPedido.REGISTRADO);
                    paradasRecortadas++;
                    prueba.establecerOrden(orden, red, t);
                }
                if (orden.isEmpty()) continue;
            }
            despachar(vehiculoPorId.get(v.getIdVehiculo()), orden, t, causa);
        }
        backlogCritico = contarCriticos(t);
    }

    private void despachar(VehiculoSim vs, List<Pedido> orden, double t, String causa) {
        Vehiculo v = vs.getVehiculo();
        Almacen salida = v.getAlmacenBase();
        int carga = 0;
        for (Pedido p : orden) carga += p.getCantidadUnidades();
        salida.despachar(carga);
        Almacen retorno = elegirAlmacenRetorno(v, orden.get(orden.size() - 1).getUbicacion(), t);
        String id = "R" + (++contadorRutas);
        Coordenada base = salida.getUbicacion();
        RutaEnCurso r = constructor.construir(id, causa, v, salida, retorno, base.x(), base.y(), t, Double.NaN,
                (int) Math.round(base.x()), (int) Math.round(base.y()), orden);
        vs.setRuta(r);
        for (Pedido p : orden) {
            p.cambiarEstado(EstadoPedido.EN_TRANSITO);
            pendientes.remove(p);
            SeguimientoPedido s = pedidos.get(p.getIdPedidoOrigen());
            if (s != null) s.registrarAsignacion(t, v.getIdVehiculo(), id, salida.getNombre(), p);
        }
        rutasDespachadas++;
        vs.registrarEvento(t, "DESPACHO", String.format(ES, "Sale de %s con %d paquetes para %d pedido(s) (ruta %s); regreso estimado a %s: %s.",
                salida.getNombre(), carga, orden.size(), id, retorno.getNombre(), fecha(r.getHoraRegreso())));
        registrarRuta(r, orden);
    }

    /**
     * Almacen al que vuelve la unidad (A10): el mas cercano a su ultima entrega
     * que tenga stock para recargarla (el central siempre tiene). Si tiene un
     * mantenimiento preventivo en las proximas 48 h, vuelve a su almacen base (P19).
     */
    private Almacen elegirAlmacenRetorno(Vehiculo v, Coordenada desde, double t) {
        if (v.limiteRetorno(t) - t < 48.0) return v.getAlmacenBase();
        Almacen mejor = central;
        double mejorDistancia = central.getUbicacion().distanciaManhattan(desde);
        for (Almacen a : almacenes) {
            if (a.esCentral()) continue;
            if (a.getInventarioActual() < 2 * v.getCapacidadPaquetes()) continue;
            double d = a.getUbicacion().distanciaManhattan(desde);
            if (d < mejorDistancia) {
                mejorDistancia = d;
                mejor = a;
            }
        }
        return mejor;
    }

    /** Unidades libres en un almacen intermedio sin stock suficiente: van vacias al almacen con stock mas cercano. */
    private void reubicarUnidadesSinStock(double t) {
        if (pendientes.isEmpty()) return;
        for (VehiculoSim vs : vehiculos) {
            Vehiculo v = vs.getVehiculo();
            if (vs.getRuta() != null || vs.getAveria() != null || !v.estaOperativa(t)) continue;
            Almacen base = v.getAlmacenBase();
            if (base.esCentral() || base.getInventarioActual() >= v.getCapacidadPaquetes()) continue;
            Almacen destino = central;
            double mejor = central.getUbicacion().distanciaManhattan(base.getUbicacion());
            for (Almacen a : almacenes) {
                if (a == base || a.esCentral() || a.getInventarioActual() < 2 * v.getCapacidadPaquetes()) continue;
                double d = a.getUbicacion().distanciaManhattan(base.getUbicacion());
                if (d < mejor) {
                    mejor = d;
                    destino = a;
                }
            }
            Coordenada c = base.getUbicacion();
            RutaEnCurso r = constructor.construir("R" + (++contadorRutas), "REUBICACION", v, base, destino, c.x(), c.y(), t,
                    Double.NaN, (int) Math.round(c.x()), (int) Math.round(c.y()), List.of());
            if (v.limiteRetorno(t) < r.getHoraRegreso()) continue;
            vs.setRuta(r);
            vs.registrarEvento(t, "REUBICACION", base.getNombre() + " no tiene stock para cargarla: se reubica vacía en " + destino.getNombre() + ".");
            registrarRuta(r, List.of());
        }
    }

    /**
     * Reprogramacion (A21): un pedido que ya no puede llegar dentro del plazo
     * comprometido ni saliendo ahora desde el almacen mas cercano a la mayor
     * velocidad queda registrado como incumplido y se sigue atendiendo con un
     * nuevo plazo operativo (la politica es que todo llegue en plazo; esto solo
     * ocurre cuando la operacion ya no da abasto).
     */
    private void reprogramarInviables(double t) {
        double velMax = operacion.velocidadMaxima();
        for (Pedido p : pendientes) {
            double minimo = distanciaMinimaDesdeAlmacenes(p, t);
            if (t + minimo / velMax <= p.getHoraLimite() + EPS) continue;
            double nuevo = t + Math.max(p.getPlazoHoras(), ventanaH + minimo / velMax + 1.0);
            boolean primeraVez = !p.estaReprogramado();
            p.reprogramar(nuevo);
            SeguimientoPedido s = pedidos.get(p.getIdPedidoOrigen());
            if (s != null && !s.estaReprogramado()) {
                s.marcarIncumplido(t, "No alcanza a llegar dentro del plazo comprometido (" + fecha(s.getHoraLimite())
                        + "); se reprograma con nuevo plazo operativo hasta " + fecha(nuevo) + ".");
                reprogramaciones++;
                if (primeraVez) registrarEvento(t, "REPROGRAMACION", null, s.getIdPedido(),
                        "Pedido " + s.getIdPedido() + " reprogramado: ya no llega dentro de su plazo comprometido.");
            }
        }
    }

    private double distanciaMinimaDesdeAlmacenes(Pedido p, double t) {
        double minimo = Double.POSITIVE_INFINITY;
        for (Almacen a : almacenes) {
            if (!a.tieneStock(p.getCantidadUnidades())) continue;
            double d = red.calcularDistancia(a.getUbicacion(), p.getUbicacion(), t);
            if (Double.isInfinite(d)) d = red.calcularDistancia(a.getUbicacion(), p.getUbicacion());
            minimo = Math.min(minimo, d);
        }
        return Double.isInfinite(minimo) ? red.calcularDistancia(central.getUbicacion(), p.getUbicacion()) : minimo;
    }

    /** Pedidos pendientes que ya no pueden esperar a la siguiente ventana (sin unidad que los atienda). */
    private int contarCriticos(double t) {
        double velMax = operacion.velocidadMaxima();
        Set<String> ordenes = new LinkedHashSet<>();
        for (Pedido p : pendientes) {
            if (p.estaReprogramado()) continue;
            double minimo = distanciaMinimaDesdeAlmacenes(p, t);
            if (t + ventanaH + minimo / velMax > p.getHoraLimite()) ordenes.add(p.getIdPedidoOrigen());
        }
        return ordenes.size();
    }

    // =====================================================================
    // Mantenimiento preventivo (P19)
    // =====================================================================

    /**
     * P19, nota 2: si una unidad sigue en ruta al empezar su mantenimiento (00:00), retorna de inmediato a su
     * almacen aunque no haya entregado; lo que llevaba se vuelve a planificar. El planificador ya lo evita
     * (recorta paradas para volver antes), pero un recalculo posterior por averia, bloqueo o reasignacion
     * puede alargar la ruta.
     */
    private void procesarInicioMantenimientos(double t) {
        for (VehiculoSim vs : vehiculos) {
            RutaEnCurso r = vs.getRuta();
            Vehiculo v = vs.getVehiculo();
            if (r == null || r.siguienteParada() == null || !v.estaEnMantenimiento(t)) continue;
            List<Pedido> aBordo = paradasPendientes(r);
            Averia a = vs.getAveria();
            double espera = a != null && t < a.getHoraFinInmovilizacion() ? a.getHoraFinInmovilizacion() : Double.NaN;
            reconstruirRuta(vs, t, List.of(), v.getAlmacenBase(), "RETORNO_MANTENIMIENTO", espera);
            for (Pedido parte : aBordo) transbordar(parte, vs, t, "inicia su mantenimiento preventivo", "por mantenimiento");
            planificacionInmediata = true;
            String detalle = "Empieza su mantenimiento preventivo: retorna de inmediato a " + v.getAlmacenBase().getNombre()
                    + " y sus " + aBordo.size() + " entrega(s) pendiente(s) se vuelven a planificar (P19).";
            vs.registrarEvento(t, "MANTENIMIENTO", detalle);
            registrarEvento(t, "MANTENIMIENTO", vs.getId(), null, "Vehículo " + vs.getId() + ": " + detalle);
        }
    }

    // =====================================================================
    // Bloqueos (B9)
    // =====================================================================

    private void procesarInicioBloqueos(double t) {
        while (indiceBloqueo < bloqueosPorIniciar.size() && bloqueosPorIniciar.get(indiceBloqueo).getHoraInicio() <= t + EPS) {
            Bloqueo b = bloqueosPorIniciar.get(indiceBloqueo++);
            if (b.getHoraFin() <= t) continue;
            // el inicio se procesa con tolerancia EPS: el recalculo se hace en la hora exacta de inicio para que
            // la red ya considere vigente este bloqueo (si no, el nuevo camino lo ignoraria y lo atravesaria)
            double tb = Math.max(t, b.getHoraInicio());
            Set<Long> nodos = red.nodosDe(b);
            List<String> afectados = new ArrayList<>();
            for (VehiculoSim vs : vehiculos) {
                RutaEnCurso r = vs.getRuta();
                if (r == null) continue;
                if (vs.getAveria() != null && tb < vs.getAveria().getHoraFinInmovilizacion()) continue;
                if (!cruzaNodosEnTramoActual(r, tb, nodos, b.getHoraFin())) continue;
                reconstruirRuta(vs, tb, paradasPendientes(r), r.getAlmacenRetorno(), "RECALCULO_BLOQUEO", Double.NaN);
                reRuteosBloqueo++;
                afectados.add(vs.getId());
                String detalle = "Nueva calle bloqueada (" + b.getIdBloqueo() + ") en un tramo de su ruta: ruta recalculada para el mismo vehículo, evitando el tramo.";
                vs.registrarEvento(tb, "RECALCULO", detalle);
                registrarEvento(tb, "RECALCULO", vs.getId(), null, "Vehículo " + vs.getId() + ": ruta recalculada por el bloqueo " + b.getIdBloqueo() + ".");
            }
            if (!afectados.isEmpty()) {
                registrarReplanificacion(new ReplanificacionPorBloqueo(tb, b.getIdBloqueo(), afectados, List.of(),
                        "Inicio del bloqueo programado " + b.getIdBloqueo() + ": las unidades recalculan su recorrido"));
            }
        }
    }

    /** true si lo que falta del tramo en curso pasa por algun nodo del bloqueo mientras rige. */
    private boolean cruzaNodosEnTramoActual(RutaEnCurso r, double t, Set<Long> nodos, double finBloqueo) {
        Trayectoria tr = r.getTrayectoria();
        int i = tr.indiceEn(t);
        int k = r.tramoEn(t);
        int finIdx = r.getFinTramo()[k];
        for (int j = i + 1; j < finIdx; j++) {
            if (tr.t(j) >= finBloqueo) break;
            double x = tr.x(j), y = tr.y(j);
            if (x != Math.rint(x) || y != Math.rint(y)) continue;
            if (nodos.contains(RedVial.claveNodo((int) x, (int) y))) return true;
        }
        return false;
    }

    /** true si lo que falta de la ruta recorre el tramo (x1,y1)-(x2,y2) en cualquier sentido. */
    private boolean cruzaTramo(RutaEnCurso r, double t, int x1, int y1, int x2, int y2) {
        Trayectoria tr = r.getTrayectoria();
        int i = Math.max(0, tr.indiceEn(t) - 1);
        for (int j = i; j + 1 < tr.tamano(); j++) {
            double ax = tr.x(j), ay = tr.y(j), bx = tr.x(j + 1), by = tr.y(j + 1);
            if (tr.t(j + 1) < t) continue;
            boolean ida = cubre(ax, ay, bx, by, x1, y1, x2, y2);
            if (ida) return true;
        }
        return false;
    }

    private static boolean cubre(double ax, double ay, double bx, double by, int x1, int y1, int x2, int y2) {
        // segmento a-b (sobre un eje) contiene el tramo unitario (x1,y1)-(x2,y2)
        if (y1 == y2 && ay == by && Math.abs(ay - y1) < 1e-9) {
            double lo = Math.min(ax, bx), hi = Math.max(ax, bx);
            return Math.min(x1, x2) >= lo - 1e-9 && Math.max(x1, x2) <= hi + 1e-9 && hi - lo > 1e-9;
        }
        if (x1 == x2 && ax == bx && Math.abs(ax - x1) < 1e-9) {
            double lo = Math.min(ay, by), hi = Math.max(ay, by);
            return Math.min(y1, y2) >= lo - 1e-9 && Math.max(y1, y2) <= hi + 1e-9 && hi - lo > 1e-9;
        }
        return false;
    }

    private static List<Pedido> paradasPendientes(RutaEnCurso r) {
        List<Pedido> lista = new ArrayList<>();
        for (ParadaEnCurso p : r.getParadas()) if (p.estaPendiente()) lista.add(p.getParte());
        return lista;
    }

    /**
     * La unidad sigue desde donde esta con otra secuencia de paradas: se cierra
     * la ruta vigente (contando lo recorrido hasta ahora) y se arma una ruta de
     * continuacion. Si esta entregando, termina esa entrega antes de salir.
     */
    private void reconstruirRuta(VehiculoSim vs, double t, List<Pedido> paradas, Almacen retorno, String causa, double esperarHasta) {
        RutaEnCurso actual = vs.getRuta();
        Vehiculo v = vs.getVehiculo();
        double[] pos = actual != null ? actual.getTrayectoria().posicionEn(t) : new double[]{v.getAlmacenOrigen().x(), v.getAlmacenOrigen().y()};
        double espera = esperarHasta;
        if (actual != null) {
            for (ParadaEnCurso p : actual.getParadas()) {
                if (!p.estaRetirada() && t >= p.getHoraLlegada() - EPS && t < p.getHoraSalida()) {
                    espera = Double.isNaN(espera) ? p.getHoraSalida() : Math.max(espera, p.getHoraSalida());
                }
            }
        }
        int[] nodo = nodoDeReanudacion(actual, t, pos);
        Almacen salida = actual != null ? actual.getAlmacenSalida() : v.getAlmacenBase();
        String id = actual != null ? actual.getIdRuta() : "R" + (++contadorRutas);
        if (actual != null) {
            acumularKm(vs, actual, actual.getTrayectoria().kmHasta(t), t);
            for (ParadaEnCurso p : actual.getParadas()) if (p.estaPendiente()) p.marcarRetirada();
            actual.cerrar(t);
        }
        RutaEnCurso nueva = constructor.construir(id, causa, v, salida, retorno, pos[0], pos[1], t, espera, nodo[0], nodo[1], paradas);
        vs.setRuta(nueva);
        registrarRuta(nueva, paradas);
    }

    /** Nodo hacia el que sigue la unidad: el siguiente de su camino, salvo que este bloqueado (entonces vuelve). */
    private int[] nodoDeReanudacion(RutaEnCurso r, double t, double[] pos) {
        double x = pos[0], y = pos[1];
        int rx = (int) Math.round(x), ry = (int) Math.round(y);
        boolean enNodo = Math.abs(x - rx) < 1e-6 && Math.abs(y - ry) < 1e-6;
        if (r == null || enNodo) return new int[]{rx, ry};
        int[] dir = r.getTrayectoria().direccionEn(t);
        if (dir[0] == 0 && dir[1] == 0) return new int[]{rx, ry};
        int bx = dir[0] > 0 ? (int) Math.ceil(x) : dir[0] < 0 ? (int) Math.floor(x) : rx;
        int by = dir[1] > 0 ? (int) Math.ceil(y) : dir[1] < 0 ? (int) Math.floor(y) : ry;
        int ax = dir[0] > 0 ? (int) Math.floor(x) : dir[0] < 0 ? (int) Math.ceil(x) : rx;
        int ay = dir[1] > 0 ? (int) Math.floor(y) : dir[1] < 0 ? (int) Math.ceil(y) : ry;
        boolean bloqueado = red.estaNodoBloqueado(bx, by, t) || red.estaTramoBloqueado(ax, ay, bx, by);
        return bloqueado ? new int[]{ax, ay} : new int[]{bx, by};
    }

    // =====================================================================
    // Averias (B10, B11)
    // =====================================================================

    private boolean aplicarAveria(VehiculoSim vs, TipoAveria tipo, double t, String causa, String origen) {
        Vehiculo v = vs.getVehiculo();
        if (vs.getAveria() != null && t < vs.getAveria().getHoraDisponible()) return false;
        if (vs.getRuta() == null && v.estaEnMantenimiento(t)) return false;
        double disponible = tipo.calcularHoraDisponible(t, operacion.getHorasCambioTurno());
        double inmovilizacion = tipo.calcularInmovilizacion(t, disponible);
        double[] pos = vs.posicionEn(t);
        Coordenada lugar = new Coordenada(pos[0], pos[1]);
        Averia averia = new Averia("AV" + (historialAverias.size() + 1), v, tipo, causa, t, t + inmovilizacion, disponible, lugar, origen);
        historialAverias.add(averia);
        RutaEnCurso r = vs.getRuta();
        List<String> pedidosAfectados = new ArrayList<>();

        if (tipo == TipoAveria.TIPO_1) {
            vs.setAveria(averia, lugar);
            if (r != null) {
                List<Pedido> restantes = paradasPendientes(r);
                for (Pedido p : restantes) pedidosAfectados.add(p.getIdPedidoOrigen());
                reconstruirRuta(vs, t, restantes, r.getAlmacenRetorno(), "AVERIA_TIPO_1", t + inmovilizacion);
                reRuteosAveria++;
                rescatarParadasTardias(vs, t);
            } else {
                v.setIndisponibleHasta(disponible);
            }
        } else {
            v.setEstadoOperativo("AVERIADO");
            v.setIndisponibleHasta(disponible);
            List<Pedido> aBordo = new ArrayList<>();
            if (r != null) {
                aBordo = paradasPendientes(r);
                acumularKm(vs, r, r.getTrayectoria().kmHasta(t), t);
                for (ParadaEnCurso p : r.getParadas()) if (p.estaPendiente()) p.marcarRetirada();
                r.cerrar(t);
                vs.setRuta(null);
            }
            vs.setAveria(averia, lugar);
            v.setAlmacenBase(central); // va al taller del almacen central
            for (Pedido parte : aBordo) {
                pedidosAfectados.add(parte.getIdPedidoOrigen());
                transbordar(parte, vs, t);
            }
            if (!aBordo.isEmpty()) {
                reRuteosAveria++;
                planificacionInmediata = true;
            }
        }
        pedidosAfectadosPorAveria.put(averia.getIdAveria(), pedidosAfectados.size());
        String detalle = String.format(ES, "Vehículo %s: %s. %s%s", v.getIdVehiculo(), tipo.getEtiqueta(),
                tipo == TipoAveria.TIPO_1 ? "Queda inmovilizado 2 h y luego continúa su ruta"
                        : "Queda inmovilizado en el lugar y pasa al taller; disponible desde " + fecha(disponible),
                pedidosAfectados.isEmpty() ? "." : " (" + pedidosAfectados.size() + " pedido(s) a bordo afectados).");
        vs.registrarEvento(t, "AVERIA", detalle);
        registrarEvento(t, "AVERIA", v.getIdVehiculo(), null, detalle);
        registrarReplanificacion(new ReplanificacionPorAveria(t, averia, List.of(v.getIdVehiculo()), pedidosAfectados, detalle));
        return true;
    }

    /** Transbordo (B10): la parte que llevaba la unidad averiada vuelve a planificarse; si es critica se busca carga en transito (B11). */
    private void transbordar(Pedido parte, VehiculoSim averiado, double t) {
        transbordar(parte, averiado, t, "sufrió una avería", "por avería");
    }

    private void transbordar(Pedido parte, VehiculoSim averiado, double t, String motivo, String causa) {
        SeguimientoPedido s = pedidos.get(parte.getIdPedidoOrigen());
        Pedido nueva = new Pedido(parte, "T" + (++secuenciaPartes), parte.getCantidadUnidades());
        parte.cambiarEstado(EstadoPedido.REASIGNADO);
        if (s != null) {
            s.reemplazarParte(parte, List.of(nueva));
            s.marcarReasignado(t, "Transbordo: la unidad " + averiado.getId() + " " + motivo + "; "
                    + parte.getCantidadUnidades() + " unid. se vuelven a planificar.");
        }
        pendientes.add(nueva);
        registrarEvento(t, "TRANSBORDO", averiado.getId(), parte.getIdPedidoOrigen(),
                "Pedido " + parte.getIdPedidoOrigen() + ": su carga en " + averiado.getId() + " se vuelve a planificar " + causa + ".");
        // si ninguna unidad libre alcanza a llegar con margen, se busca carga en transito (B11)
        if (llegadaMasTempranaConUnidadLibre(nueva, t) + 0.25 > nueva.getHoraLimite()) intentarReasignacion(nueva, t, averiado);
    }

    /** Hora mas temprana en que una unidad libre (en su almacen, operativa y con capacidad) llegaria al pedido. */
    private double llegadaMasTempranaConUnidadLibre(Pedido p, double t) {
        double mejor = Double.POSITIVE_INFINITY;
        for (VehiculoSim vs : vehiculos) {
            Vehiculo v = vs.getVehiculo();
            if (vs.getRuta() != null || vs.getAveria() != null || !v.estaOperativa(t)) continue;
            if (v.getCapacidadPaquetes() < p.getCantidadUnidades() || !v.getAlmacenBase().tieneStock(p.getCantidadUnidades())) continue;
            double d = red.calcularDistancia(v.getAlmacenOrigen(), p.getUbicacion(), t);
            if (Double.isInfinite(d)) continue;
            mejor = Math.min(mejor, v.avanzar(t, v.calcularTiempoViaje(d)));
        }
        return mejor;
    }

    /** Despues de una averia tipo 1: si alguna parada ya no llega a tiempo, se intenta cubrirla con carga en transito. */
    private void rescatarParadasTardias(VehiculoSim vs, double t) {
        RutaEnCurso r = vs.getRuta();
        if (r == null) return;
        List<Pedido> tardias = new ArrayList<>();
        for (ParadaEnCurso p : r.getParadas()) {
            if (p.estaPendiente() && p.getHoraLlegada() > p.getParte().getHoraLimite() + EPS) tardias.add(p.getParte());
        }
        if (tardias.isEmpty()) return;
        boolean cambio = false;
        List<Pedido> restantes = paradasPendientes(r);
        for (Pedido critico : tardias) {
            if (intentarReasignacion(critico, t, vs)) {
                restantes.remove(critico);
                cambio = true;
            }
        }
        if (cambio) reconstruirRuta(vs, t, restantes, r.getAlmacenRetorno(), "AVERIA_TIPO_1", vs.getAveria() != null
                ? vs.getAveria().getHoraFinInmovilizacion() : Double.NaN);
    }

    /**
     * Reasignacion de carga en transito (B11): busca una unidad en camino que
     * lleve paquetes para un cliente con mas holgura y que pueda llegar a
     * tiempo al pedido critico; le entrega a este esos paquetes y el pedido que
     * los cede se vuelve a despachar desde un almacen dentro de su propio plazo.
     */
    private boolean intentarReasignacion(Pedido critico, double t, VehiculoSim excluido) {
        Donante mejor = null;
        double velMax = operacion.velocidadMaxima();
        for (VehiculoSim y : vehiculos) {
            if (y == excluido) continue;
            RutaEnCurso r = y.getRuta();
            if (r == null || r.getParadas().isEmpty()) continue;
            if (y.getAveria() != null && t < y.getAveria().getHoraFinInmovilizacion()) continue;
            List<Pedido> pendientesY = paradasPendientes(r);
            for (ParadaEnCurso pb : r.getParadas()) {
                if (!pb.estaPendiente() || pb.getHoraLlegada() <= t) continue;
                Pedido b = pb.getParte();
                if (b.getCantidadUnidades() < critico.getCantidadUnidades()) continue;
                if (b.getHoraLimite() <= critico.getHoraLimite()) continue;
                double minimoB = distanciaMinimaDesdeAlmacenes(b, t);
                if (t + ventanaH + minimoB / velMax + 1.0 > b.getHoraLimite()) continue; // B no alcanzaria a reponerse
                List<Pedido> secuencia = new ArrayList<>();
                secuencia.add(critico);
                for (Pedido q : pendientesY) {
                    if (q != b) secuencia.add(q);
                    else if (b.getCantidadUnidades() > critico.getCantidadUnidades()) secuencia.add(b); // el resto se marca abajo
                }
                double[] pos = r.getTrayectoria().posicionEn(t);
                int[] nodo = nodoDeReanudacion(r, t, pos);
                double salida = t;
                for (ParadaEnCurso p : r.getParadas()) {
                    if (!p.estaRetirada() && t >= p.getHoraLlegada() - EPS && t < p.getHoraSalida()) salida = Math.max(salida, p.getHoraSalida());
                }
                double[] llegadas = constructor.estimarLlegadas(y.getVehiculo(), new Coordenada(nodo[0], nodo[1]), salida, secuencia);
                boolean factible = true;
                for (int i = 0; i < secuencia.size(); i++) {
                    if (llegadas[i] > secuencia.get(i).getHoraLimite() + EPS) {
                        factible = false;
                        break;
                    }
                }
                if (!factible) continue;
                double extra = new Coordenada(nodo[0], nodo[1]).distanciaManhattan(critico.getUbicacion());
                if (mejor == null || extra < mejor.costoExtra()) mejor = new Donante(y, pb, secuencia, extra);
            }
        }
        if (mejor == null) return false;
        aplicarReasignacion(mejor, critico, t);
        return true;
    }

    private void aplicarReasignacion(Donante d, Pedido critico, double t) {
        VehiculoSim y = d.vehiculo();
        RutaEnCurso r = y.getRuta();
        Pedido b = d.parada().getParte();
        SeguimientoPedido sb = pedidos.get(b.getIdPedidoOrigen());
        SeguimientoPedido sa = pedidos.get(critico.getIdPedidoOrigen());

        // el pedido B cede critico.cantidad paquetes: el resto (si hay) sigue a bordo y lo cedido se repone desde un almacen
        List<Pedido> secuencia = new ArrayList<>(d.secuencia());
        List<Pedido> nuevasB = new ArrayList<>();
        int resto = b.getCantidadUnidades() - critico.getCantidadUnidades();
        if (resto > 0) {
            Pedido bResto = new Pedido(b, "R" + (++secuenciaPartes), resto);
            int idx = secuencia.indexOf(b);
            if (idx >= 0) secuencia.set(idx, bResto);
            nuevasB.add(bResto);
        }
        Pedido bRepuesto = new Pedido(b, "D" + (++secuenciaPartes), critico.getCantidadUnidades());
        nuevasB.add(bRepuesto);
        b.cambiarEstado(EstadoPedido.REASIGNADO);
        pendientes.add(bRepuesto);
        if (sb != null) {
            sb.reemplazarParte(b, nuevasB);
            sb.marcarReasignado(t, critico.getCantidadUnidades() + " unid. que llevaba " + y.getId()
                    + " se entregan al pedido más crítico " + critico.getIdPedidoOrigen() + "; se reponen desde un almacén.");
        }

        pendientes.remove(critico);
        critico.cambiarEstado(EstadoPedido.EN_TRANSITO);
        if (sa != null) {
            sa.registrarAsignacion(t, y.getId(), r.getIdRuta(), "carga en tránsito de " + y.getId(), critico);
            sa.marcarReasignado(t, "Recibe " + critico.getCantidadUnidades() + " unid. en tránsito de " + y.getId()
                    + " (antes para " + b.getIdPedidoOrigen() + ") para cumplir su plazo.");
        }
        reconstruirRuta(y, t, secuencia, r.getAlmacenRetorno(), "REASIGNACION", Double.NaN);
        if (resto > 0) {
            // la parte que queda a bordo ya figura en la nueva ruta: se registra su asignacion
            for (Pedido p : nuevasB) if (p != bRepuesto && sb != null) {
                p.cambiarEstado(EstadoPedido.EN_TRANSITO);
                sb.registrarAsignacion(t, y.getId(), r.getIdRuta(), "carga en tránsito", p);
            }
        }
        reasignaciones++;
        String detalle = "Productos en camino reasignados: " + y.getId() + " entrega " + critico.getCantidadUnidades()
                + " unid. al pedido " + critico.getIdPedidoOrigen() + " (más crítico) en lugar del pedido " + b.getIdPedidoOrigen() + ".";
        y.registrarEvento(t, "REASIGNACION", detalle);
        registrarEvento(t, "REASIGNACION", y.getId(), critico.getIdPedidoOrigen(), detalle);
        registrarReplanificacion(new ReasignacionCarga(t, b.getIdPedidoOrigen(), critico.getIdPedidoOrigen(), y.getId(),
                "Pedido más crítico ante una incidencia", detalle));
    }

    // =====================================================================
    // Colapso logistico (B24, D21)
    // =====================================================================

    private void verificarColapso(double t) {
        if (parametros.getEscenario() != Escenario.COLAPSO_LOGISTICO || colapso != null) return;
        int incumplidos = contarIncumplidosActivos(t);
        boolean porVencidos = incumplidos >= parametros.getUmbralColapsoVencidos();
        boolean porBacklog = backlogCritico >= parametros.getUmbralColapsoBacklog();
        if (!porVencidos && !porBacklog) return;
        String motivo = porVencidos
                ? incumplidos + " pedidos activos ya no cumplen su plazo de entrega de forma simultánea (umbral: "
                + parametros.getUmbralColapsoVencidos() + ")"
                : backlogCritico + " pedidos activos quedaron sin ninguna unidad disponible que los atienda a tiempo (umbral: "
                + parametros.getUmbralColapsoBacklog() + ")";
        colapso = new InfoColapso(t, fecha(t), motivo, redondear(cumplimientoActual(), 2), incumplidos, backlogCritico,
                registrados - cancelados, entregados, redondear((t - horaInicio) / 24.0, 2));
        registrarEvento(t, "COLAPSO", null, null, "Colapso logístico detectado: " + motivo + ".");
        finalizar("COLAPSO");
    }

    private int contarIncumplidosActivos(double t) {
        int n = 0;
        for (SeguimientoPedido s : activos.values()) if (s.esIncumplido(t)) n++;
        return n;
    }

    private double cumplimientoActual() {
        int total = registrados - cancelados;
        if (total <= 0) return 100.0;
        int incumplidos = entregadosTarde + contarIncumplidosActivos(horaActual);
        return 100.0 * (total - incumplidos) / total;
    }

    /** Marca la corrida como terminada (fin del periodo, colapso o detencion manual). */
    public void finalizar(String motivo) {
        candado.lock();
        try {
            if (finalizado) return;
            finalizado = true;
            motivoFin = motivo;
            for (Pedido p : pendientes) p.cambiarEstado(EstadoPedido.REGISTRADO);
            version++;
        } finally {
            candado.unlock();
        }
    }

    // =====================================================================
    // Comandos desde el visualizador y el registro de pedidos
    // =====================================================================

    /** Cierra o vuelve a abrir un tramo de calle (bloqueo manual, V-2). Devuelve true si quedo bloqueado. */
    public boolean alternarBloqueoTramo(int x1, int y1, int x2, int y2) {
        return conCandado(() -> {
            double t = horaActual;
            if (Math.abs(x1 - x2) + Math.abs(y1 - y2) != 1) throw new IllegalArgumentException("El tramo debe unir dos cruces vecinos.");
            if (red.estaNodoBloqueado(x1, y1, t) && red.estaNodoBloqueado(x2, y2, t)) {
                throw new IllegalStateException("Ese tramo ya está bloqueado por los datos reales del curso y no se puede alterar desde el mapa.");
            }
            if (red.estaTramoBloqueado(x1, y1, x2, y2)) {
                red.desbloquearTramo(x1, y1, x2, y2);
                registrarEvento(t, "BLOQUEO_MANUAL", null, null, String.format("Tramo (%d,%d)-(%d,%d) reabierto por el usuario.", x1, y1, x2, y2));
                version++;
                return false;
            }
            if (!red.bloquearTramo(x1, y1, x2, y2)) throw new IllegalArgumentException("Tramo fuera de la ciudad.");
            bloqueosManualesTotales++;
            List<String> afectados = new ArrayList<>();
            for (VehiculoSim vs : vehiculos) {
                RutaEnCurso r = vs.getRuta();
                if (r == null || !cruzaTramo(r, t, x1, y1, x2, y2)) continue;
                if (vs.getAveria() != null && t < vs.getAveria().getHoraFinInmovilizacion()) {
                    reconstruirRuta(vs, t, paradasPendientes(r), r.getAlmacenRetorno(), "RECALCULO_TRAMO_MANUAL",
                            vs.getAveria().getHoraFinInmovilizacion());
                } else {
                    reconstruirRuta(vs, t, paradasPendientes(r), r.getAlmacenRetorno(), "RECALCULO_TRAMO_MANUAL", Double.NaN);
                }
                reRuteosBloqueo++;
                afectados.add(vs.getId());
                String detalle = "Calle bloqueada manualmente por el usuario en el Panorama operativo: ruta recalculada para el vehículo "
                        + vs.getId() + ", evitando el tramo.";
                vs.registrarEvento(t, "RECALCULO", detalle);
                registrarEvento(t, "RECALCULO", vs.getId(), null, "Vehículo " + vs.getId() + ": ruta recalculada por un tramo bloqueado a mano.");
            }
            registrarEvento(t, "BLOQUEO_MANUAL", null, null, String.format("Tramo (%d,%d)-(%d,%d) bloqueado por el usuario (%d vehículo(s) recalculan su ruta).",
                    x1, y1, x2, y2, afectados.size()));
            version++;
            return true;
        });
    }

    /** Registro de un pedido en la operacion dia a dia (A1, A2, A5, A14, A15, A25; prototipo V-2). */
    public SeguimientoPedido registrarPedido(String idCliente, String nombreCliente, int x, int y, int cantidad,
                                             TipoEntrega tipo, CanalPedido canal, String observaciones) {
        return conCandado(() -> {
            if (parametros.getEscenario() != Escenario.TIEMPO_REAL) {
                throw new IllegalStateException("El registro manual de pedidos solo está disponible en el escenario «Operación día a día».");
            }
            if (finalizado) throw new IllegalStateException("La corrida ya terminó.");
            validarDestino(x, y, cantidad);
            String prefijo = canal == CanalPedido.MAPA ? "PM-" : "PW-";
            String id = prefijo + (++secuenciaManual);
            String cliente = idCliente == null || idCliente.isBlank() ? "c-" + id.toLowerCase(Locale.ROOT) : idCliente.trim();
            Cliente c = new Cliente(cliente, nombreCliente == null || nombreCliente.isBlank() ? "Cliente " + cliente : nombreCliente.trim(),
                    new Coordenada(x, y));
            Pedido p = new Pedido(id, c, cantidad, tipo, tipo.plazoEn(operacion), horaActual);
            SeguimientoPedido s = registrar(p, canal, observaciones);
            planificacionInmediata = true;
            registrarEvento(horaActual, "PEDIDO_MANUAL", null, id, "Pedido " + id + " registrado (" + tipo.getEtiqueta() + ", "
                    + cantidad + " unid.) en (" + x + "," + y + "); se asigna automáticamente.");
            version++;
            return s;
        });
    }

    /** Carga masiva de pedidos durante la operacion dia a dia (A9): horas relativas al mes en curso. */
    public int cargarPedidosEnLote(List<RegistroVenta> registros) {
        return conCandado(() -> {
            if (parametros.getEscenario() != Escenario.TIEMPO_REAL) {
                throw new IllegalStateException("La carga masiva durante la corrida solo está disponible en «Operación día a día».");
            }
            YearMonth mes = ConversorTiempo.mesDe(horaActual);
            double base = ConversorTiempo.inicioDeMes(mes);
            List<PedidoEntrante> nuevos = new ArrayList<>();
            for (RegistroVenta r : registros) {
                double h = Math.max(horaActual, base + r.horasDesdeInicioDeMes());
                String id = "PL-" + (++secuenciaLote);
                Cliente c = new Cliente(r.idCliente(), "Cliente " + r.idCliente(), new Coordenada(r.destinoX(), r.destinoY()));
                nuevos.add(new PedidoEntrante(new Pedido(id, c, r.cantidad(), TipoEntrega.desdePlazo(r.plazoHoras()), r.plazoHoras(), h),
                        CanalPedido.CARGA_MASIVA, null));
            }
            agregarACola(nuevos);
            ingresarPedidosHasta(horaActual);
            planificacionInmediata = true;
            registrarEvento(horaActual, "PEDIDO_MANUAL", null, null, "Carga masiva: " + nuevos.size() + " pedidos registrados.");
            version++;
            return nuevos.size();
        });
    }

    /** Edicion de un pedido que todavia no sale del almacen (A6). */
    public SeguimientoPedido editarPedido(String id, Integer cantidad, TipoEntrega tipo, String observaciones) {
        return conCandado(() -> {
            SeguimientoPedido s = pedidos.get(id);
            if (s == null) throw new IllegalArgumentException("No existe el pedido " + id + ".");
            if (!s.esModificable()) throw new IllegalStateException("El pedido " + id + " ya fue asignado o despachado y no se puede editar.");
            if (cantidad == null && tipo == null) {
                s.setObservaciones(observaciones);
                s.registrarEvento(horaActual, "EDICION", "Observaciones actualizadas.");
                version++;
                return s;
            }
            Pedido original = s.getPedidoOriginal();
            int nuevaCantidad = cantidad == null ? original.getCantidadUnidades() : cantidad;
            TipoEntrega nuevoTipo = tipo == null ? original.getTipoEntrega() : tipo;
            validarDestino((int) original.getUbicacion().x(), (int) original.getUbicacion().y(), nuevaCantidad);
            double plazo = tipo == null ? original.getPlazoHoras() : nuevoTipo.plazoEn(operacion);
            Pedido nuevo = new Pedido(original.getIdPedido(), original.getCliente(), nuevaCantidad, nuevoTipo, plazo, original.getHoraRegistro());
            for (Pedido p : s.getPartesActivas()) pendientes.remove(p);
            SeguimientoPedido ns = new SeguimientoPedido(nuevo, s.getCanal(), observaciones != null ? observaciones : s.getObservaciones());
            for (SeguimientoPedido.EventoPedido e : s.getEventos()) if (!"REGISTRO".equals(e.tipo())) ns.registrarEvento(e.hora(), e.tipo(), e.detalle());
            ns.registrarEvento(horaActual, "EDICION", "Pedido editado: " + nuevaCantidad + " unid., " + nuevoTipo.getEtiqueta() + ".");
            pedidos.put(id, ns);
            activos.put(id, ns);
            pendientes.add(nuevo);
            planificacionInmediata = parametros.getEscenario() == Escenario.TIEMPO_REAL;
            version++;
            return ns;
        });
    }

    /** Cancelacion de un pedido antes de su despacho (A7). */
    public SeguimientoPedido cancelarPedido(String id, String motivo) {
        return conCandado(() -> {
            SeguimientoPedido s = pedidos.get(id);
            if (s == null) throw new IllegalArgumentException("No existe el pedido " + id + ".");
            if (!s.esModificable()) throw new IllegalStateException("El pedido " + id + " ya fue despachado y no se puede cancelar.");
            for (Pedido p : s.getPartesActivas()) pendientes.remove(p);
            s.cancelar(horaActual, motivo == null || motivo.isBlank() ? "Cancelado por el usuario." : motivo);
            activos.remove(id);
            cancelados++;
            registrarEvento(horaActual, "CANCELACION", null, id, "Pedido " + id + " cancelado antes de su despacho.");
            version++;
            return s;
        });
    }

    /** Registro de una averia desde el visualizador (D7). */
    public boolean registrarAveria(String idVehiculo, TipoAveria tipo, String causa) {
        return conCandado(() -> {
            VehiculoSim vs = vehiculoPorId.get(idVehiculo);
            if (vs == null) throw new IllegalArgumentException("No existe la unidad " + idVehiculo + ".");
            if (finalizado) throw new IllegalStateException("La corrida ya terminó.");
            boolean ok = aplicarAveria(vs, tipo, horaActual, causa == null || causa.isBlank() ? "Reportada por el usuario" : causa, "MANUAL");
            if (!ok) throw new IllegalStateException("La unidad " + idVehiculo + " ya está averiada o en mantenimiento.");
            version++;
            return true;
        });
    }

    /**
     * Cambio de velocidades por tipo de unidad con la corrida en marcha (P6, P16, P17): las rutas nuevas ya salen
     * con la nueva velocidad y, en la siguiente ventana de planificacion, las unidades que estan en ruta recalculan
     * su recorrido con ella (P17: todo vehiculo en el almacen o en ruta se vuelve a planificar en cada iteracion).
     */
    public void actualizarVelocidades(Double auto, Double moto, Double bicicleta) {
        conCandado(() -> {
            if (auto != null && auto > 0) operacion.setVelocidadAuto(auto);
            if (moto != null && moto > 0) operacion.setVelocidadMoto(moto);
            if (bicicleta != null && bicicleta > 0) operacion.setVelocidadBicicleta(bicicleta);
            recalcularPorVelocidad = true;
            registrarEvento(horaActual, "INFO", null, null, "Velocidades actualizadas: " + operacion.describirVelocidades()
                    + ". Aplican desde la siguiente ventana de planificación.");
            version++;
            return null;
        });
    }

    private boolean recalcularPorVelocidad;

    /** P17: en la ventana siguiente a un cambio de velocidad, las unidades en ruta rehacen su recorrido con la nueva velocidad. */
    private void aplicarCambioDeVelocidad(double t) {
        if (!recalcularPorVelocidad) return;
        recalcularPorVelocidad = false;
        int recalculadas = 0;
        for (VehiculoSim vs : vehiculos) {
            RutaEnCurso r = vs.getRuta();
            if (r == null) continue;
            Averia a = vs.getAveria();
            double espera = a != null && t < a.getHoraFinInmovilizacion() ? a.getHoraFinInmovilizacion() : Double.NaN;
            String causa = r.getParadas().isEmpty() ? r.getCausa() : "RECALCULO_VELOCIDAD";
            reconstruirRuta(vs, t, paradasPendientes(r), r.getAlmacenRetorno(), causa, espera);
            vs.registrarEvento(t, "RECALCULO", "Ruta recalculada con la nueva velocidad de su tipo de unidad ("
                    + String.format(ES, "%.0f", vs.getVehiculo().getVelocidadPromedioKmH()) + " km/h).");
            recalculadas++;
        }
        registrarEvento(t, "INFO", null, null, "Nueva velocidad aplicada: " + recalculadas + " unidad(es) en ruta recalcularon su recorrido ("
                + operacion.describirVelocidades() + ").");
    }

    /** Umbrales del semaforo (C10): aplican desde la siguiente actualizacion del mapa. */
    public void actualizarUmbrales(double verde, double ambar) {
        conCandado(() -> {
            parametros.getUmbrales().setVerde(verde);
            parametros.getUmbrales().setAmbar(ambar);
            parametros.getUmbrales().normalizar();
            version++;
            return null;
        });
    }

    private void validarDestino(int x, int y, int cantidad) {
        if (x < 0 || x > operacion.getAnchoCiudadKm() || y < 0 || y > operacion.getAltoCiudadKm()) {
            throw new IllegalArgumentException("La dirección de entrega está fuera de la zona de cobertura (0-"
                    + operacion.getAnchoCiudadKm() + " x 0-" + operacion.getAltoCiudadKm() + " km) (A13).");
        }
        if (cantidad <= 0 || cantidad > 1000) throw new IllegalArgumentException("La cantidad debe estar entre 1 y 1000 paquetes.");
    }

    private <T> T conCandado(Supplier<T> accion) {
        candado.lock();
        try {
            return accion.get();
        } finally {
            candado.unlock();
        }
    }

    // =====================================================================
    // Bitacora
    // =====================================================================

    private void registrarEvento(double hora, String tipo, String vehiculo, String pedido, String detalle) {
        EventoSimulacion e = new EventoSimulacion(++secuenciaEventos, hora, tipo, vehiculo, pedido, detalle);
        eventos.addLast(e);
        while (eventos.size() > MAX_EVENTOS_MEMORIA) eventos.removeFirst();
        eventosPorTipo.merge(tipo, 1, Integer::sum);
        if (oyente != null) oyente.eventoRegistrado(e);
    }

    /** Constancia auditable de un replanteo (B25): solo se persiste, no se muestra como alerta. */
    private void registrarReplanificacion(EventoReplanificacion evento) {
        if (oyente == null) return;
        oyente.eventoRegistrado(new EventoSimulacion(++secuenciaEventos, evento.getFechaHora(), "REPLANIFICACION_" + evento.getCausa(),
                evento.getVehiculosAfectados().isEmpty() ? null : evento.getVehiculosAfectados().get(0),
                evento.getPedidosAfectados().isEmpty() ? null : evento.getPedidosAfectados().get(0), evento.registrarEvento()));
    }

    private void registrarRuta(RutaEnCurso r, List<Pedido> paradas) {
        if (oyente == null) return;
        StringBuilder ids = new StringBuilder();
        for (Pedido p : paradas) {
            if (ids.length() > 0) ids.append(",");
            ids.append(p.getIdPedido());
        }
        double km = r.getTrayectoria().kmTotal();
        oyente.rutaRegistrada(new OyenteCorrida.RegistroRuta(r.getIdRuta(), r.getVehiculo().getIdVehiculo(), r.getCausa(),
                r.getHoraInicio(), r.getHoraRegreso(), r.getAlmacenSalida().getNombre(), r.getAlmacenRetorno().getNombre(),
                ids.length() > 3900 ? ids.substring(0, 3900) : ids.toString(), r.getCargaInicial(), km,
                km * r.getVehiculo().getCostoPorKm()));
    }

    // =====================================================================
    // Estado para el visualizador
    // =====================================================================

    public EstadoVisual construirEstadoVisual(String estadoCorrida, double factor, long tiempoRealSeg) {
        candado.lock();
        try {
            double t = horaActual;
            Map<Pedido, String> vehiculoDeParte = new HashMap<>();
            // E31: unidad, ruta y horas de cada parte que va en una ruta
            Map<Pedido, EstadoVisual.ParteVisual> parteEnRuta = new HashMap<>();
            for (VehiculoSim vs : vehiculos) {
                RutaEnCurso r = vs.getRuta();
                if (r == null) continue;
                for (ParadaEnCurso p : r.getParadas()) {
                    if (!p.estaPendiente()) continue;
                    Pedido parte = p.getParte();
                    vehiculoDeParte.put(parte, vs.getId());
                    parteEnRuta.put(parte, new EstadoVisual.ParteVisual(parte.getIdPedido(), vs.getId(), r.getIdRuta(),
                            r.getAlmacenSalida().getNombre(), parte.getCantidadUnidades(), minutos(r.getHoraInicio()),
                            minutos(p.getHoraLlegada()), r.getHoraInicio() <= t ? "En ruta" : "Por salir"));
                }
            }

            List<EstadoVisual.AlmacenVisual> listaAlmacenes = new ArrayList<>();
            for (Almacen a : almacenes) {
                // unidades en ruta que regresan a este almacen, con su hora estimada de llegada
                List<String> arriban = new ArrayList<>();
                // E20-E21: partes en transito desde este almacen y partes asignadas que aun no salen
                int enTransito = 0;
                List<EstadoVisual.PedidoHora> porSalir = new ArrayList<>();
                for (VehiculoSim vs : vehiculos) {
                    RutaEnCurso r = vs.getRuta();
                    if (r == null) continue;
                    if (r.getAlmacenRetorno() == a) arriban.add(vs.getId() + " · " + fecha(r.getHoraRegreso()));
                    if (r.getAlmacenSalida() != a) continue;
                    for (ParadaEnCurso p : r.getParadas()) {
                        if (!p.estaPendiente()) continue;
                        if (r.getHoraInicio() <= t) enTransito++;
                        else porSalir.add(new EstadoVisual.PedidoHora(p.getParte().getIdPedidoOrigen(), minutos(r.getHoraInicio())));
                    }
                }
                // E22: entregas de las ultimas 24 h simuladas cuyas rutas salieron de este almacen
                List<EstadoVisual.PedidoHora> entregadosAlmacen = new ArrayList<>();
                for (EntregaReciente e : entregasRecientes) {
                    if (e.almacen().equals(a.getIdAlmacen()) && e.hora() >= t - 24.0) {
                        entregadosAlmacen.add(new EstadoVisual.PedidoHora(e.pedido(), minutos(e.hora())));
                    }
                }
                listaAlmacenes.add(new EstadoVisual.AlmacenVisual(a.getIdAlmacen(), a.getNombre(), a.getUbicacion().x(), a.getUbicacion().y(),
                        a.esCentral(), a.esCentral() ? null : a.getCapacidad(), a.esCentral() ? null : a.getInventarioActual(), arriban,
                        enTransito, porSalir, entregadosAlmacen));
            }

            List<EstadoVisual.VehiculoVisual> listaVehiculos = new ArrayList<>();
            int flotaActiva = 0;
            double costoActivo = 0, kmActivo = 0;
            for (VehiculoSim vs : vehiculos) {
                Vehiculo v = vs.getVehiculo();
                EstadoUnidad estado = vs.estadoEn(t);
                if (estado != EstadoUnidad.AVERIADA && estado != EstadoUnidad.EN_TALLER && estado != EstadoUnidad.MANTENIMIENTO) flotaActiva++;
                double[] pos = vs.posicionEn(t);
                RutaEnCurso r = vs.getRuta();
                List<String> aBordo = new ArrayList<>();
                Double holguraMin = null;
                String destino = "";
                double[] tray = new double[0];
                int carga = 0;
                Double salidaMin = null, llegadaProximaMin = null, regresoMin = null, retornoDesdeMin = null;
                double kmRuta = 0;
                String almacenOrigen = v.getAlmacenBase().getNombre();
                Map<String, Integer> cargaPorPedido = new LinkedHashMap<>();
                if (r != null) {
                    double km = r.getTrayectoria().kmHasta(t);
                    kmActivo += km;
                    costoActivo += km * v.getCostoPorKm();
                    kmRuta = km;
                    almacenOrigen = r.getAlmacenSalida().getNombre();
                    // el regreso al almacen empieza al terminar la ultima entrega (o desde la salida, si no lleva entregas)
                    double finEntregas = r.getHoraInicio();
                    for (ParadaEnCurso p : r.getParadas()) if (!p.estaRetirada()) finEntregas = Math.max(finEntregas, p.getHoraSalida());
                    retornoDesdeMin = minutos(finEntregas);
                    carga = r.cargaEn(t);
                    salidaMin = minutos(r.getHoraInicio());
                    regresoMin = minutos(r.getHoraRegreso());
                    ParadaEnCurso proxima = r.siguienteParada();
                    llegadaProximaMin = proxima != null ? minutos(proxima.getHoraLlegada()) : regresoMin;
                    for (ParadaEnCurso p : r.getParadas()) {
                        if (!p.estaPendiente()) continue;
                        String origen = p.getParte().getIdPedidoOrigen();
                        cargaPorPedido.merge(origen, p.getParte().getCantidadUnidades(), Integer::sum);
                        if (!aBordo.contains(origen)) aBordo.add(origen);
                        double h = p.getParte().calcularHolguraPorcentaje(t);
                        if (holguraMin == null || h < holguraMin) holguraMin = h;
                    }
                    ParadaEnCurso sig = r.siguienteParada();
                    destino = sig != null ? "Punto de entrega " + sig.getParte().getIdPedidoOrigen()
                            : (esReubicacion(r) ? "Reubicación a " : "Retorno a ") + r.getAlmacenRetorno().getNombre();
                    tray = aplanar(r.getTrayectoria().restanteCompacto(t));
                }
                // averia vigente (D10-D13): en el lugar hasta el fin de la inmovilizacion; en taller hasta que vuelve a estar disponible
                Integer tipoAveria = null;
                Double finAveriaMin = null;
                Averia av = vs.getAveria();
                if (av != null && (estado == EstadoUnidad.AVERIADA || estado == EstadoUnidad.EN_TALLER)) {
                    tipoAveria = av.getTipo().getNumero();
                    finAveriaMin = minutos(estado == EstadoUnidad.AVERIADA ? av.getHoraFinInmovilizacion() : av.getHoraDisponible());
                }
                List<EstadoVisual.CargaPedido> pedidosCarga = new ArrayList<>();
                cargaPorPedido.forEach((id, cant) -> pedidosCarga.add(new EstadoVisual.CargaPedido(id, cant)));
                listaVehiculos.add(new EstadoVisual.VehiculoVisual(v.getIdVehiculo(), v.getTipo().name(), estado.name(), estado.getEtiqueta(),
                        redondear(pos[0], 3), redondear(pos[1], 3), carga, v.getCapacidadPaquetes(), aBordo,
                        holguraMin == null ? null : redondear(holguraMin, 1), destino, tray, v.getAlmacenBase().getNombre(),
                        tipoAveria, finAveriaMin, salidaMin, llegadaProximaMin, regresoMin, pedidosCarga,
                        retornoDesdeMin, redondear(kmRuta, 2), almacenOrigen));
            }

            List<EstadoVisual.PedidoVisual> listaPedidos = new ArrayList<>();
            int enCurso = 0, pendientesCount = 0, incumplidosActivos = 0;
            for (SeguimientoPedido s : activos.values()) {
                boolean enRuta = s.getPartesActivas().stream().anyMatch(p -> p.getEstado() == EstadoPedido.EN_TRANSITO);
                if (enRuta) enCurso++; else pendientesCount++;
                if (s.esIncumplido(t)) incumplidosActivos++;
                List<String> enVehiculos = new ArrayList<>();
                List<EstadoVisual.ParteVisual> partes = new ArrayList<>();
                for (Pedido p : s.getPartesActivas()) {
                    String idv = vehiculoDeParte.get(p);
                    if (idv != null && !enVehiculos.contains(idv)) enVehiculos.add(idv);
                    EstadoVisual.ParteVisual pv = parteEnRuta.get(p);
                    partes.add(pv != null ? pv : new EstadoVisual.ParteVisual(p.getIdPedido(), null, null, null,
                            p.getCantidadUnidades(), null, null, p.getEstado().getEtiqueta()));
                }
                listaPedidos.add(new EstadoVisual.PedidoVisual(s.getIdPedido(), s.getUbicacion().x(), s.getUbicacion().y(),
                        s.getCantidadUnidades(), s.getPlazoHoras(), minutos(s.getHoraRegistro()), minutos(s.getHoraLimite()),
                        s.getEstado().getEtiqueta(), String.join(", ", enVehiculos), s.esIncumplido(t), s.getCliente().getIdCliente(),
                        partes));
            }

            List<EstadoVisual.BloqueoVisual> listaBloqueos = new ArrayList<>();
            for (Bloqueo b : red.bloqueosVigentes(t)) {
                double[] puntos = new double[b.getVertices().size() * 2];
                for (int i = 0; i < b.getVertices().size(); i++) {
                    puntos[2 * i] = b.getVertices().get(i).x();
                    puntos[2 * i + 1] = b.getVertices().get(i).y();
                }
                listaBloqueos.add(new EstadoVisual.BloqueoVisual(b.getIdBloqueo(), puntos, minutos(b.getHoraInicio()), minutos(b.getHoraFin())));
            }

            List<EstadoVisual.AlertaVisual> alertas = new ArrayList<>();
            List<EventoSimulacion> recientes = new ArrayList<>(eventos);
            for (int i = recientes.size() - 1; i >= 0 && alertas.size() < 40; i--) {
                EventoSimulacion e = recientes.get(i);
                alertas.add(new EstadoVisual.AlertaVisual(e.id(), e.tipo(), minutos(e.hora()), e.detalle(), e.vehiculo(), e.pedido()));
            }

            int total = registrados - cancelados;
            int incumplidos = entregadosTarde + incumplidosActivos;
            double cumplimiento = total <= 0 ? 100.0 : 100.0 * (total - incumplidos) / total;
            EstadoVisual.Kpis kpis = new EstadoVisual.Kpis(total, entregados, entregadosATiempo, enCurso, pendientesCount, incumplidos,
                    redondear(cumplimiento, 1), redondear(costoCerrado + costoActivo, 2), redondear(kmCerrado + kmActivo, 1),
                    flotaActiva, vehiculos.size(), reRuteosBloqueo, reRuteosAveria, reasignaciones, listaBloqueos.size(), backlogCritico);

            Integer totalDias = parametros.getEscenario() == Escenario.SIMULACION_5D ? parametros.getDiasSimulacion() : null;
            int dia = totalDias == null ? diaDe(t) : Math.min(diaDe(t), totalDias);
            return new EstadoVisual(version, idCorrida, estadoCorrida, parametros.getEscenario().name(), parametros.getEscenario().getEtiqueta(),
                    parametros.getAlgoritmo(), parametros.getSemilla(), ConversorTiempo.aEpochMs(horaInicio), minutos(t),
                    Double.isInfinite(horaFin) ? null : minutos(horaFin), factor, tiempoRealSeg, dia, totalDias,
                    parametros.getUmbrales().getVerde(), parametros.getUmbrales().getAmbar(), kpis, listaAlmacenes, listaVehiculos,
                    listaPedidos, listaBloqueos, red.getTramosBloqueados(), alertas, colapso, null);
        } finally {
            candado.unlock();
        }
    }

    private double[] aplanar(double[][] puntos) {
        double[] plano = new double[puntos.length * 3];
        for (int i = 0; i < puntos.length; i++) {
            plano[3 * i] = redondear(puntos[i][0], 3);
            plano[3 * i + 1] = redondear(puntos[i][1], 3);
            plano[3 * i + 2] = redondear(minutos(puntos[i][2]), 3);
        }
        return plano;
    }

    // =====================================================================
    // Detalle (modal del visualizador) y consultas
    // =====================================================================

    public DetalleVisual.Vehiculo detalleVehiculo(String id) {
        return conCandado(() -> {
            VehiculoSim vs = vehiculoPorId.get(id);
            if (vs == null) return null;
            Vehiculo v = vs.getVehiculo();
            double t = horaActual;
            double[] pos = vs.posicionEn(t);
            RutaEnCurso r = vs.getRuta();
            DetalleVisual.RutaDetalle rutaDetalle = null;
            List<String> aBordo = new ArrayList<>();
            String proxima = "—", eta = "—";
            if (r != null) {
                List<DetalleVisual.ParadaDetalle> paradas = new ArrayList<>();
                int sec = 1;
                for (ParadaEnCurso p : r.getParadas()) {
                    if (p.estaRetirada()) continue;
                    Pedido parte = p.getParte();
                    String estado = p.estaEntregada() ? "Entregada" : (t >= p.getHoraLlegada() ? "Entregando" : "Pendiente");
                    paradas.add(new DetalleVisual.ParadaDetalle(sec++, parte.getIdPedidoOrigen(), parte.getCliente().getIdCliente(),
                            parte.getUbicacion().x(), parte.getUbicacion().y(), parte.getCantidadUnidades(), fecha(p.getHoraLlegada()),
                            fecha(p.getHoraSalida()), fecha(parte.getHoraLimiteCompromiso()), estado,
                            p.getHoraLlegada() <= parte.getHoraLimiteCompromiso() + 1e-6));
                    if (p.estaPendiente() && !aBordo.contains(parte.getIdPedidoOrigen())) aBordo.add(parte.getIdPedidoOrigen());
                }
                rutaDetalle = new DetalleVisual.RutaDetalle(r.getIdRuta(), describirCausa(r.getCausa()), fecha(r.getHoraInicio()),
                        fecha(r.getHoraRegreso()), r.getAlmacenSalida().getNombre(), r.getAlmacenRetorno().getNombre(),
                        r.cargaEn(t), paradas);
                ParadaEnCurso sig = r.siguienteParada();
                if (sig != null) {
                    proxima = "Punto de entrega " + sig.getParte().getIdPedidoOrigen();
                    eta = fecha(sig.getHoraLlegada());
                } else {
                    proxima = (esReubicacion(r) ? "Reubicación a " : "Retorno a ") + r.getAlmacenRetorno().getNombre();
                    eta = fecha(r.getHoraRegreso());
                }
            }
            // solo las pausas de alimentacion que ya ocurrieron en esta corrida (las mas recientes, ultimas 24 h)
            List<String> horas = new ArrayList<>();
            for (double h : jornada.pausasOcurridas(v.getCorrelativo(), Math.max(horaInicio, t - 24.0), t)) {
                String fin = fecha(h + operacion.getDuracionAlimentacionH());
                horas.add(fecha(h) + " – " + fin.substring(fin.length() - 5));
            }
            String turno = "";
            for (Turno tu : turnos) if (tu.estaVigente(ConversorTiempo.horaDelDia(t))) turno = String.format("%02d:00–%02d:00", (int) tu.getHoraInicio(), (int) tu.getHoraFin());
            double proxMant = v.limiteRetorno(t);
            String averia = vs.getAveria() == null ? null : vs.getAveria().getTipo().getEtiqueta() + " · disponible desde " + fecha(vs.getAveria().getHoraDisponible());
            List<DetalleVisual.Evento> evs = new ArrayList<>();
            List<VehiculoSim.EventoUnidad> lista = vs.getEventos();
            for (int i = lista.size() - 1; i >= 0; i--) {
                VehiculoSim.EventoUnidad e = lista.get(i);
                evs.add(new DetalleVisual.Evento(fecha(e.hora()), minutos(e.hora()), e.tipo(), e.detalle()));
            }
            double km = vs.getKmRecorridos() + (r != null ? r.getTrayectoria().kmHasta(t) : 0);
            return new DetalleVisual.Vehiculo(v.getIdVehiculo(), v.getTipo().getEtiqueta(), vs.estadoEn(t).getEtiqueta(), redondear(pos[0], 2),
                    redondear(pos[1], 2), v.getAlmacenBase().getNombre(), v.getCapacidadPaquetes(), r == null ? 0 : r.cargaEn(t),
                    v.getVelocidadPromedioKmH(), v.getCostoPorKm(), horas, turno,
                    Double.isInfinite(proxMant) ? (v.estaEnMantenimiento(t) ? "Hoy (en mantenimiento)" : "Sin programar en los datos cargados") : fecha(proxMant),
                    averia, rutaDetalle, aBordo, proxima, eta, vs.getRutasRealizadas() + (r != null && !esContinuacion(r.getCausa()) && !r.getParadas().isEmpty() ? 1 : 0),
                    vs.getParadasEntregadas(), redondear(km, 1), redondear(km * v.getCostoPorKm(), 2), evs);
        });
    }

    public DetalleVisual.Pedido detallePedido(String id) {
        return conCandado(() -> {
            SeguimientoPedido s = pedidos.get(id);
            if (s == null) {
                // busqueda sin distinguir mayusculas
                for (Map.Entry<String, SeguimientoPedido> e : pedidos.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(id)) {
                        s = e.getValue();
                        break;
                    }
                }
                if (s == null) return null;
            }
            return construirDetallePedido(s, horaActual);
        });
    }

    private DetalleVisual.Pedido construirDetallePedido(SeguimientoPedido s, double t) {
        List<DetalleVisual.Parte> partes = new ArrayList<>();
        for (Pedido p : s.getPartesActivas()) partes.add(new DetalleVisual.Parte(p.getIdPedido(), p.getCantidadUnidades(), p.getEstado().getEtiqueta()));
        List<DetalleVisual.Evento> evs = new ArrayList<>();
        List<SeguimientoPedido.EventoPedido> lista = s.getEventos();
        for (int i = lista.size() - 1; i >= 0; i--) {
            SeguimientoPedido.EventoPedido e = lista.get(i);
            evs.add(new DetalleVisual.Evento(fecha(e.hora()), minutos(e.hora()), e.tipo(), e.detalle()));
        }
        Boolean aTiempo = s.estaEntregado() ? Boolean.valueOf(s.fueEntregadoATiempo()) : (s.esIncumplido(t) ? Boolean.FALSE : null);
        return new DetalleVisual.Pedido(s.getIdPedido(), s.getCliente().getIdCliente(), s.getUbicacion().x(), s.getUbicacion().y(),
                s.getCantidadUnidades(), s.getTipoEntrega().getEtiqueta(), s.getPlazoHoras(), fecha(s.getHoraRegistro()),
                fecha(s.getHoraLimite()), s.getEstado().getEtiqueta(),
                redondear(s.estaActivo() ? s.getPedidoOriginal().calcularHolguraPorcentaje(t) : 0, 1),
                new ArrayList<>(s.getVehiculos()), new ArrayList<>(s.getAlmacenesOrigen()), new ArrayList<>(s.getRutas()),
                Double.isNaN(s.getHoraEntregaReal()) ? null : fecha(s.getHoraEntregaReal()), aTiempo, s.estaReprogramado(),
                s.getCanal().getEtiqueta(), s.getObservaciones(), partes, s.esModificable(), evs);
    }

    public DetalleVisual.Almacen detalleAlmacen(String id) {
        return conCandado(() -> {
            for (Almacen a : almacenes) {
                if (!a.getIdAlmacen().equalsIgnoreCase(id)) continue;
                List<String> enAlmacen = new ArrayList<>(), enCamino = new ArrayList<>();
                for (VehiculoSim vs : vehiculos) {
                    if (vs.getRuta() == null && vs.getVehiculo().getAlmacenBase() == a) enAlmacen.add(vs.getId());
                    if (vs.getRuta() != null && vs.getRuta().getAlmacenRetorno() == a) enCamino.add(vs.getId());
                }
                Integer cap = a.esCentral() ? null : a.getCapacidad();
                Integer stock = a.esCentral() ? null : a.getInventarioActual();
                Double pct = a instanceof AlmacenIntermedio ai ? redondear(ai.porcentajeStock(), 1) : null;
                int despachado = a instanceof AlmacenIntermedio ai2 ? ai2.getUnidadesDespachadasHoy() : 0;
                return new DetalleVisual.Almacen(a.getIdAlmacen(), a.getNombre(), a.getUbicacion().x(), a.getUbicacion().y(), a.esCentral(),
                        cap, stock, pct, despachado, a.esCentral() ? "Abastecimiento permanente (sin capacidad máxima)" : "Diaria, 23:59:59 (instantánea)",
                        enAlmacen, enCamino);
            }
            return null;
        });
    }

    /** Fila de la busqueda de pedidos (A18, A24). */
    public record FilaPedido(String id, String cliente, double x, double y, int cantidad, String tipoEntrega, double plazo,
                             String registro, String limite, String estado, String vehiculos, String entregaReal, Boolean aTiempo,
                             boolean reprogramado, String canal, String observaciones, double holgura, boolean modificable) {
    }

    /** Busca pedidos de la corrida por texto (id o cliente), estado, tipo de entrega y rango de fechas. */
    public List<FilaPedido> buscarPedidos(String texto, String estado, String tipo, LocalDateTime desde, LocalDateTime hasta, int limite) {
        return conCandado(() -> {
            List<FilaPedido> filas = new ArrayList<>();
            String q = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
            double h0 = desde == null ? Double.NEGATIVE_INFINITY : ConversorTiempo.aHoras(desde);
            double h1 = hasta == null ? Double.POSITIVE_INFINITY : ConversorTiempo.aHoras(hasta);
            List<SeguimientoPedido> lista = new ArrayList<>(pedidos.values());
            for (int i = lista.size() - 1; i >= 0 && filas.size() < limite; i--) {
                SeguimientoPedido s = lista.get(i);
                if (!q.isEmpty() && !s.getIdPedido().toLowerCase(Locale.ROOT).contains(q)
                        && !s.getCliente().getIdCliente().toLowerCase(Locale.ROOT).contains(q)) continue;
                if (estado != null && !estado.isBlank() && !coincideEstado(s, estado, horaActual)) continue;
                if (tipo != null && !tipo.isBlank() && !s.getTipoEntrega().name().equalsIgnoreCase(tipo)) continue;
                if (s.getHoraRegistro() < h0 || s.getHoraRegistro() > h1) continue;
                filas.add(fila(s));
            }
            return filas;
        });
    }

    private boolean coincideEstado(SeguimientoPedido s, String estado, double t) {
        switch (estado.toUpperCase(Locale.ROOT)) {
            case "ACTIVO": return s.estaActivo();
            case "INCUMPLIDO": return s.esIncumplido(t);
            case "A_TIEMPO": return s.fueEntregadoATiempo();
            default: return s.getEstado().name().equalsIgnoreCase(estado);
        }
    }

    private FilaPedido fila(SeguimientoPedido s) {
        Boolean aTiempo = s.estaEntregado() ? Boolean.valueOf(s.fueEntregadoATiempo()) : (s.esIncumplido(horaActual) ? Boolean.FALSE : null);
        return new FilaPedido(s.getIdPedido(), s.getCliente().getIdCliente(), s.getUbicacion().x(), s.getUbicacion().y(),
                s.getCantidadUnidades(), s.getTipoEntrega().getEtiqueta(), s.getPlazoHoras(), fecha(s.getHoraRegistro()),
                fecha(s.getHoraLimite()), s.getEstado().getEtiqueta(), String.join(", ", s.getVehiculos()),
                Double.isNaN(s.getHoraEntregaReal()) ? "" : fecha(s.getHoraEntregaReal()), aTiempo, s.estaReprogramado(),
                s.getCanal().getEtiqueta(), s.getObservaciones() == null ? "" : s.getObservaciones(),
                redondear(s.estaActivo() ? s.getPedidoOriginal().calcularHolguraPorcentaje(horaActual) : 0, 1), s.esModificable());
    }

    /** Todos los pedidos de la corrida en CSV (A24, D22). */
    public String exportarPedidosCsv() {
        return conCandado(() -> {
            StringBuilder sb = new StringBuilder("id;cliente;x;y;cantidad;tipo_entrega;plazo_h;registro;limite;estado;vehiculos;entrega_real;a_tiempo;reprogramado;canal;observaciones\n");
            for (SeguimientoPedido s : pedidos.values()) {
                FilaPedido f = fila(s);
                sb.append(f.id()).append(';').append(f.cliente()).append(';').append((int) f.x()).append(';').append((int) f.y()).append(';')
                        .append(f.cantidad()).append(';').append(f.tipoEntrega()).append(';').append((int) f.plazo()).append(';')
                        .append(f.registro()).append(';').append(f.limite()).append(';').append(f.estado()).append(';')
                        .append(f.vehiculos()).append(';').append(f.entregaReal()).append(';')
                        .append(f.aTiempo() == null ? "" : (f.aTiempo() ? "SI" : "NO")).append(';').append(f.reprogramado() ? "SI" : "NO").append(';')
                        .append(f.canal()).append(';').append(f.observaciones().replace(';', ',').replace('\n', ' ')).append('\n');
            }
            return sb.toString();
        });
    }

    // =====================================================================
    // Cierre (V-3, reportes D15-D25)
    // =====================================================================

    /** G08-G10: rutas vigentes al momento del cierre (la ultima planificacion estable). Se llama con el candado tomado. */
    private CierreCorrida.UltimaPlanificacion ultimaPlanificacion() {
        List<CierreCorrida.RutaVigente> rutas = new ArrayList<>();
        for (VehiculoSim vs : vehiculos) {
            RutaEnCurso r = vs.getRuta();
            if (r == null) continue;
            List<CierreCorrida.ParadaPlan> paradas = new ArrayList<>();
            for (ParadaEnCurso p : r.getParadas()) {
                if (!p.estaPendiente()) continue;
                paradas.add(new CierreCorrida.ParadaPlan(p.getParte().getIdPedido(), p.getParte().getCantidadUnidades(), fecha(p.getHoraLlegada())));
            }
            rutas.add(new CierreCorrida.RutaVigente(vs.getId(), vs.getVehiculo().getTipo().getEtiqueta(), r.getIdRuta(),
                    r.getAlmacenSalida().getNombre(), fecha(r.getHoraInicio()), paradas, r.getAlmacenRetorno().getNombre(),
                    fecha(r.getHoraRegreso())));
        }
        return new CierreCorrida.UltimaPlanificacion(Double.isNaN(horaUltimaPlanificacion) ? "—" : fecha(horaUltimaPlanificacion),
                describirCausa(causaUltimaPlanificacion), rutas);
    }

    public CierreCorrida generarCierre(String estadoCorrida, long inicioRealMs, long finRealMs) {
        return conCandado(() -> {
            double t = horaActual;
            int total = registrados - cancelados;
            int incumplidosActivos = contarIncumplidosActivos(t);
            int enCurso = 0;
            for (SeguimientoPedido s : activos.values()) if (!s.esIncumplido(t)) enCurso++;
            int incumplidos = entregadosTarde + incumplidosActivos;
            double cumplimiento = total <= 0 ? 100.0 : 100.0 * (total - incumplidos) / total;
            double kmActivo = 0, costoActivo = 0;
            for (VehiculoSim vs : vehiculos) {
                if (vs.getRuta() == null) continue;
                double km = vs.getRuta().getTrayectoria().kmHasta(t);
                kmActivo += km;
                costoActivo += km * vs.getVehiculo().getCostoPorKm();
            }
            double costoTotal = costoCerrado + costoActivo;
            CierreCorrida.Resumen resumen = new CierreCorrida.Resumen(total, entregados, entregadosATiempo, entregadosTarde, enCurso,
                    incumplidosActivos, cancelados, redondear(cumplimiento, 2), entregados == 0 ? 100.0 : redondear(100.0 * entregadosATiempo / entregados, 2),
                    redondear(costoTotal, 2), redondear(kmCerrado + kmActivo, 1), reRuteosBloqueo, reRuteosAveria, reasignaciones, reprogramaciones,
                    pedidosDivididos, paradasRecortadas, rutasDespachadas, unidadesEntregadas,
                    entregados == 0 ? 0 : redondear(costoTotal / entregados, 2), tiempoComputoNs / 1_000_000L, planificaciones);

            // cumplimiento por dia de registro
            Map<Integer, int[]> porDia = new TreeMap<>(); // {registrados, aTiempo, incumplidos, enCurso}
            for (SeguimientoPedido s : pedidos.values()) {
                if (s.estaCancelado()) continue;
                int dia = diaDe(s.getHoraRegistro());
                int[] c = porDia.computeIfAbsent(dia, k -> new int[4]);
                c[0]++;
                if (s.fueEntregadoATiempo()) c[1]++;
                else if (s.esIncumplido(t)) c[2]++;
                else c[3]++;
            }
            int ultimoDia = diaDe(Math.max(horaInicio, t - 1e-6));
            for (int d = 1; d <= ultimoDia; d++) porDia.computeIfAbsent(d, k -> new int[4]);
            List<CierreCorrida.DiaCumplimiento> dias = new ArrayList<>();
            double inicioDia = ConversorTiempo.inicioDelDia(horaInicio);
            for (Map.Entry<Integer, int[]> e : porDia.entrySet()) {
                int[] c = e.getValue();
                double pct = c[0] == 0 ? 100.0 : 100.0 * (c[0] - c[2]) / c[0];
                String f = ConversorTiempo.aFecha(inicioDia + (e.getKey() - 1) * 24.0).toLocalDate().toString();
                dias.add(new CierreCorrida.DiaCumplimiento(e.getKey(), f, c[0], c[1], c[2], c[3], redondear(pct, 2),
                        cumplimientoAlCierreDia.containsKey(e.getKey()) ? redondear(cumplimientoAlCierreDia.get(e.getKey()), 2) : null));
            }

            List<CierreCorrida.KmPorTipo> porTipo = new ArrayList<>();
            for (TipoVehiculo tipo : TipoVehiculo.values()) {
                double[] acum = kmPorTipo.get(tipo);
                double km = acum[0], costo = acum[1];
                int unidades = 0;
                for (VehiculoSim vs : vehiculos) {
                    if (vs.getVehiculo().getTipo() != tipo) continue;
                    unidades++;
                    if (vs.getRuta() != null) {
                        double k = vs.getRuta().getTrayectoria().kmHasta(t);
                        km += k;
                        costo += k * vs.getVehiculo().getCostoPorKm();
                    }
                }
                porTipo.add(new CierreCorrida.KmPorTipo(tipo.getEtiqueta(), unidades, (int) acum[2], redondear(km, 1), redondear(costo, 2)));
            }

            List<CierreCorrida.UsoVehiculo> uso = new ArrayList<>();
            for (VehiculoSim vs : vehiculos) {
                double km = vs.getKmRecorridos() + (vs.getRuta() != null ? vs.getRuta().getTrayectoria().kmHasta(t) : 0);
                uso.add(new CierreCorrida.UsoVehiculo(vs.getId(), vs.getVehiculo().getTipo().getEtiqueta(), vs.getRutasRealizadas(),
                        vs.getParadasEntregadas(), vs.getUnidadesEntregadas(), redondear(km, 1), redondear(km * vs.getVehiculo().getCostoPorKm(), 2),
                        redondear(vs.getHorasEnRuta(), 1), redondear(100.0 * vs.getFactorCargaPromedio(), 1)));
            }

            List<CierreCorrida.AveriaResumen> averias = new ArrayList<>();
            for (Averia a : historialAverias) {
                averias.add(new CierreCorrida.AveriaResumen(a.getIdAveria(), a.getVehiculoAfectado().getIdVehiculo(), a.getTipo().getEtiqueta(),
                        fecha(a.getHoraInicio()), a.getOrigen(), a.getCausa(), pedidosAfectadosPorAveria.getOrDefault(a.getIdAveria(), 0)));
            }
            CierreCorrida.Incidencias incidencias = new CierreCorrida.Incidencias(bloqueosProgramadosEnCorrida, bloqueosManualesTotales,
                    historialAverias.size(), averias, new LinkedHashMap<>(eventosPorTipo));

            List<String> fuera = new ArrayList<>();
            int fueraTotal = 0;
            for (SeguimientoPedido s : pedidos.values()) {
                if (s.estaEntregado() && !s.fueEntregadoATiempo()) {
                    fueraTotal++;
                    if (fuera.size() < 50) fuera.add(s.getIdPedido());
                }
            }
            boolean cumple = fueraTotal == 0 && incumplidosActivos == 0;
            String mensaje = cumple
                    ? "Todos los pedidos entregados llegaron dentro de su plazo comprometido y ningún pedido activo está vencido."
                    : String.format(ES, "%d pedido(s) entregado(s) fuera de plazo y %d pedido(s) activo(s) que ya no cumplen su plazo.", fueraTotal, incumplidosActivos);
            CierreCorrida.Verificacion verificacion = new CierreCorrida.Verificacion(entregados, fueraTotal, fuera, incumplidos, cumple, mensaje);

            Map<String, Object> resumenParametros = new LinkedHashMap<>();
            resumenParametros.put("escenario", parametros.getEscenario().getEtiqueta());
            resumenParametros.put("algoritmo", "ACO".equals(parametros.getAlgoritmo()) ? "Optimización por Colonia de Hormigas (ACO)" : "Algoritmo Genético (GA)");
            resumenParametros.put("semilla", parametros.getSemilla());
            resumenParametros.put("fechaInicio", fecha(horaInicio));
            resumenParametros.put("ventanaPlanificacionMin", parametros.getVentanaPlanificacionMin());
            if (parametros.getEscenario() == Escenario.SIMULACION_5D) {
                resumenParametros.put("diasSimulacion", parametros.getDiasSimulacion());
                resumenParametros.put("duracionObjetivoMin", parametros.getDuracionObjetivoMin());
            }
            if (parametros.getEscenario() == Escenario.COLAPSO_LOGISTICO) {
                resumenParametros.put("factorAceleracion", parametros.getFactorAceleracionColapso());
                resumenParametros.put("umbralColapsoVencidos", parametros.getUmbralColapsoVencidos());
                resumenParametros.put("umbralColapsoBacklog", parametros.getUmbralColapsoBacklog());
            }
            resumenParametros.put("umbralVerde", parametros.getUmbrales().getVerde());
            resumenParametros.put("umbralAmbar", parametros.getUmbrales().getAmbar());
            resumenParametros.put("velocidades", operacion.describirVelocidades());
            resumenParametros.put("flota", operacion.getCantidadAutos() + " autos, " + operacion.getCantidadMotos() + " motos, "
                    + operacion.getCantidadBicicletas() + " bicicletas");
            resumenParametros.put("averiasPorDia", parametros.isGenerarAverias() ? parametros.getAveriasPorDia() : 0);
            resumenParametros.put("parametrosAlgoritmo", parametros.getParametrosAlgoritmo());

            return new CierreCorrida(idCorrida, parametros.getEscenario().name(), parametros.getEscenario().getEtiqueta(),
                    parametros.getAlgoritmo(), parametros.getSemilla(), fecha(horaInicio), fecha(t), redondear(t - horaInicio, 3),
                    estadoCorrida, motivoFin, inicioRealMs, finRealMs, Math.max(0, (finRealMs - inicioRealMs) / 1000), resumen, dias,
                    porTipo, uso, incidencias, verificacion, colapso, resumenParametros, datos.getDescripcionFuentes(),
                    ultimaPlanificacion());
        });
    }

    private String describirCausa(String causa) {
        switch (causa) {
            case "PLANIFICACION": return "Ventana de planificación";
            case "REPLANIFICACION_INMEDIATA": return "Replanificación inmediata (pedido nuevo o incidencia)";
            case "RECALCULO_BLOQUEO": return "Recálculo por calle bloqueada";
            case "RECALCULO_TRAMO_MANUAL": return "Recálculo por tramo bloqueado a mano";
            case "RECALCULO_VELOCIDAD": return "Recálculo por cambio de velocidad";
            case "AVERIA_TIPO_1": return "Recálculo por avería tipo 1";
            case "REASIGNACION": return "Reasignación de carga en tránsito";
            case "REUBICACION": return "Reubicación sin carga";
            case "RETORNO_MANTENIMIENTO": return "Retorno inmediato por mantenimiento preventivo";
            default: return causa;
        }
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    /** Dia de la corrida (1 = dia de la fecha de inicio). */
    private int diaDe(double hora) {
        return (int) Math.floor((hora - ConversorTiempo.inicioDelDia(horaInicio)) / 24.0) + 1;
    }

    private double minutos(double hora) {
        return redondear((hora - horaInicio) * 60.0, 3);
    }

    private static String fecha(double hora) {
        return ConversorTiempo.formatear(hora);
    }

    private static double redondear(double valor, int decimales) {
        if (Double.isNaN(valor) || Double.isInfinite(valor)) return 0;
        double f = Math.pow(10, decimales);
        return Math.round(valor * f) / f;
    }

    // ---------------- consultas simples ----------------

    public String getIdCorrida() { return idCorrida; }
    public ParametrosCorrida getParametros() { return parametros; }
    public double getHoraInicio() { return horaInicio; }
    public double getHoraFin() { return horaFin; }
    public double getHoraActual() { return conCandado(() -> horaActual); }
    public boolean estaFinalizado() { return conCandado(() -> finalizado); }
    public String getMotivoFin() { return conCandado(() -> motivoFin); }
    public InfoColapso getColapso() { return conCandado(() -> colapso); }
    public long getVersion() { return conCandado(() -> version); }
    public long getTiempoComputoMs() { return conCandado(() -> tiempoComputoNs / 1_000_000L); }

    /** Indicadores numericos para la experimentacion (B23, D20). */
    public Map<String, Double> indicadores() {
        return conCandado(() -> {
            Map<String, Double> m = new LinkedHashMap<>();
            int total = registrados - cancelados;
            int incAct = contarIncumplidosActivos(horaActual);
            int incumplidos = entregadosTarde + incAct;
            double kmActivo = 0, costoActivo = 0;
            for (VehiculoSim vs : vehiculos) {
                if (vs.getRuta() == null) continue;
                double km = vs.getRuta().getTrayectoria().kmHasta(horaActual);
                kmActivo += km;
                costoActivo += km * vs.getVehiculo().getCostoPorKm();
            }
            m.put("pedidos", (double) total);
            m.put("entregados", (double) entregados);
            m.put("entregadosATiempo", (double) entregadosATiempo);
            m.put("incumplidos", (double) incumplidos);
            m.put("cumplimiento", total == 0 ? 100.0 : 100.0 * (total - incumplidos) / total);
            m.put("costo", costoCerrado + costoActivo);
            m.put("km", kmCerrado + kmActivo);
            m.put("costoPorPedido", entregados == 0 ? 0 : (costoCerrado + costoActivo) / entregados);
            m.put("reasignaciones", (double) reasignaciones);
            m.put("reRuteos", (double) (reRuteosBloqueo + reRuteosAveria));
            m.put("tiempoComputoMs", tiempoComputoNs / 1_000_000.0);
            m.put("horasSimuladas", horaActual - horaInicio);
            return m;
        });
    }
}
