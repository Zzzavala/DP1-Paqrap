package pe.pucp.paqrap.simulacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.pucp.paqrap.comun.Estadistica;
import pe.pucp.paqrap.persistencia.ExperimentoEntidad;
import pe.pucp.paqrap.persistencia.ServicioPersistencia;
import pe.pucp.paqrap.planificador.Planificador;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Experimentacion numerica entre las dos metaheuristicas (B23, D20, C25,
 * D23): corre el mismo escenario sin animacion (lo mas rapido posible) con el
 * Genetico y con la Colonia de Hormigas sobre las mismas instancias (mismos
 * archivos, misma fecha de inicio, misma semilla de averias) y compara costo,
 * cumplimiento, kilometraje y tiempo de computo con una prueba t pareada.
 *
 * Cada instancia es un periodo distinto de los datos publicados (por defecto,
 * 5 dias consecutivos a partir de la fecha de inicio); en el escenario de
 * colapso se mide ademas cuantos dias aguanta cada algoritmo (B24).
 */
@Service
public class ServicioExperimentacion {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioExperimentacion.class);
    private static final DateTimeFormatter ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public record SolicitudExperimento(String escenario, LocalDateTime fechaInicio, Integer instancias, Integer diasEntreInstancias,
                                       Long semillaBase, List<String> algoritmos, Double averiasPorDia, Integer diasMaximosColapso,
                                       Map<String, Object> parametrosGA, Map<String, Object> parametrosACO) {
    }

    public record CorridaExperimento(String algoritmo, int instancia, String fechaInicio, long semilla,
                                     Map<String, Double> indicadores, long tiempoRealMs, String colapso) {
    }

    public record ResumenMetrica(String metrica, String etiqueta, boolean menorEsMejor, Map<String, Estadistica.Resumen> porAlgoritmo,
                                 Estadistica.PruebaT prueba, String mejor, boolean significativo) {
    }

    /** Estado y resultados de un experimento. */
    public static final class Experimento {
        public String id;
        public String estado = "EN_COLA";
        public String descripcion;
        public int totalCorridas;
        public int corridasHechas;
        public double progreso;
        public long creadoMs;
        public Long terminadoMs;
        public SolicitudExperimento solicitud;
        public List<CorridaExperimento> corridas = new java.util.concurrent.CopyOnWriteArrayList<>();
        public List<ResumenMetrica> resumen = new ArrayList<>();
        public String conclusion;
        public String mensaje;
    }

    private static final String[][] METRICAS = {
            {"cumplimiento", "Cumplimiento de plazos (%)", "false"},
            {"costo", "Costo total (S/)", "true"},
            {"km", "Kilometraje total (km)", "true"},
            {"costoPorPedido", "Costo por pedido entregado (S/)", "true"},
            {"incumplidos", "Pedidos incumplidos", "true"},
            {"tiempoComputoMs", "Tiempo de cómputo del planificador (ms)", "true"},
            {"diasHastaColapso", "Días simulados hasta el colapso", "false"},
    };

    private final FuenteDatos fuente;
    private final ServicioPersistencia persistencia;
    private final ObjectMapper json;
    private final Map<String, Experimento> experimentos = new ConcurrentHashMap<>();
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "paqrap-experimentos");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    public ServicioExperimentacion(FuenteDatos fuente, ServicioPersistencia persistencia, ObjectMapper json) {
        this.fuente = fuente;
        this.persistencia = persistencia;
        this.json = json;
    }

    public Experimento lanzar(SolicitudExperimento s) {
        SolicitudExperimento sol = normalizar(s);
        Experimento e = new Experimento();
        e.id = "E-" + LocalDateTime.now(ParametrosCorrida.ZONA_LIMA).format(ID);
        while (experimentos.containsKey(e.id)) e.id += "x";
        e.solicitud = sol;
        e.creadoMs = System.currentTimeMillis();
        e.totalCorridas = sol.instancias() * sol.algoritmos().size();
        Escenario esc = Escenario.valueOf(sol.escenario());
        e.descripcion = String.format(Locale.ROOT, "%s · %s · %d instancias desde %s", esc.getEtiqueta(),
                String.join(" vs ", sol.algoritmos()), sol.instancias(), sol.fechaInicio().toLocalDate());
        experimentos.put(e.id, e);
        hilo.submit(() -> ejecutar(e));
        return e;
    }

    private SolicitudExperimento normalizar(SolicitudExperimento s) {
        String escenario = s == null || s.escenario() == null ? Escenario.SIMULACION_5D.name() : s.escenario().toUpperCase(Locale.ROOT);
        Escenario esc;
        try {
            esc = Escenario.valueOf(escenario);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Escenario no válido para experimentar: " + escenario);
        }
        if (esc == Escenario.TIEMPO_REAL) throw new IllegalArgumentException("La experimentación usa los escenarios de periodo o de colapso.");
        LocalDateTime inicio = s == null || s.fechaInicio() == null ? ParametrosCorrida.fechaInicioPorDefecto(esc) : s.fechaInicio();
        int instancias = s == null || s.instancias() == null ? 5 : Math.max(1, Math.min(30, s.instancias()));
        int dias = s == null || s.diasEntreInstancias() == null ? 5 : Math.max(1, Math.min(60, s.diasEntreInstancias()));
        long semilla = s == null || s.semillaBase() == null ? 20260902L : s.semillaBase();
        List<String> algoritmos = new ArrayList<>();
        if (s != null && s.algoritmos() != null) {
            for (String a : s.algoritmos()) {
                String n = Planificador.normalizar(a);
                if (n != null && !algoritmos.contains(n)) algoritmos.add(n);
            }
        }
        if (algoritmos.isEmpty()) algoritmos = List.of("GA", "ACO");
        double averias = s == null || s.averiasPorDia() == null ? 0.6 : Math.max(0, Math.min(10, s.averiasPorDia()));
        int maxColapso = s == null || s.diasMaximosColapso() == null ? 60 : Math.max(1, Math.min(365, s.diasMaximosColapso()));
        return new SolicitudExperimento(esc.name(), inicio, instancias, dias, semilla, algoritmos, averias, maxColapso,
                s == null || s.parametrosGA() == null ? new HashMap<>() : s.parametrosGA(),
                s == null || s.parametrosACO() == null ? new HashMap<>() : s.parametrosACO());
    }

    private void ejecutar(Experimento e) {
        e.estado = "EN_CURSO";
        guardar(e);
        SolicitudExperimento s = e.solicitud;
        Escenario esc = Escenario.valueOf(s.escenario());
        try {
            for (int i = 0; i < s.instancias(); i++) {
                LocalDateTime inicio = s.fechaInicio().plusDays((long) i * s.diasEntreInstancias());
                long semilla = s.semillaBase() + i;
                for (String alg : s.algoritmos()) {
                    ParametrosCorrida p = new ParametrosCorrida();
                    p.setEscenario(esc);
                    p.setAlgoritmo(alg);
                    p.setSemilla(semilla);
                    p.setFechaInicio(inicio);
                    p.setAveriasPorDia(s.averiasPorDia());
                    p.setParametrosAlgoritmo(new HashMap<>("ACO".equals(alg) ? s.parametrosACO() : s.parametrosGA()));
                    MotorSimulacion m = new MotorSimulacion(e.id + "-" + alg + "-" + i, p, fuente.datosParaCorrida(), null);
                    long t0 = System.currentTimeMillis();
                    double fin = esc == Escenario.SIMULACION_5D ? m.getHoraFin() : m.getHoraInicio() + 24.0 * s.diasMaximosColapso();
                    m.avanzarHasta(fin);
                    long ms = System.currentTimeMillis() - t0;
                    Map<String, Double> ind = new LinkedHashMap<>(m.indicadores());
                    InfoColapso c = m.getColapso();
                    if (esc == Escenario.COLAPSO_LOGISTICO) {
                        ind.put("diasHastaColapso", c != null ? c.diasSimulados() : (double) s.diasMaximosColapso());
                    }
                    e.corridas.add(new CorridaExperimento(alg, i + 1, inicio.toString(), semilla, ind, ms,
                            c == null ? (esc == Escenario.COLAPSO_LOGISTICO ? "Sin colapso en " + s.diasMaximosColapso() + " días" : null)
                                    : c.fecha() + " · " + c.motivo()));
                    e.corridasHechas++;
                    e.progreso = (double) e.corridasHechas / e.totalCorridas;
                }
                resumir(e);
                guardar(e);
            }
            resumir(e);
            e.estado = "TERMINADO";
        } catch (Throwable ex) {
            LOG.error("Experimento {} con error", e.id, ex);
            e.estado = "ERROR";
            e.mensaje = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        e.terminadoMs = System.currentTimeMillis();
        guardar(e);
    }

    private void resumir(Experimento e) {
        List<ResumenMetrica> lista = new ArrayList<>();
        List<String> algoritmos = e.solicitud.algoritmos();
        StringBuilder conclusion = new StringBuilder();
        for (String[] m : METRICAS) {
            String clave = m[0];
            boolean menor = Boolean.parseBoolean(m[2]);
            Map<String, List<Double>> valores = new LinkedHashMap<>();
            for (String a : algoritmos) valores.put(a, new ArrayList<>());
            for (int i = 1; i <= e.solicitud.instancias(); i++) {
                Map<String, Double> fila = new HashMap<>();
                for (CorridaExperimento c : e.corridas) if (c.instancia() == i && c.indicadores().containsKey(clave)) fila.put(c.algoritmo(), c.indicadores().get(clave));
                if (fila.size() == algoritmos.size()) for (String a : algoritmos) valores.get(a).add(fila.get(a));
            }
            if (valores.values().iterator().next().isEmpty()) continue;
            Map<String, Estadistica.Resumen> porAlg = new LinkedHashMap<>();
            for (Map.Entry<String, List<Double>> v : valores.entrySet()) porAlg.put(v.getKey(), Estadistica.resumir(v.getValue()));
            Estadistica.PruebaT prueba = null;
            String mejor = null;
            boolean significativo = false;
            if (algoritmos.size() == 2) {
                String a = algoritmos.get(0), b = algoritmos.get(1);
                prueba = Estadistica.pruebaTPareada(valores.get(a), valores.get(b));
                double ma = porAlg.get(a).media(), mb = porAlg.get(b).media();
                if (Math.abs(ma - mb) > 1e-9) mejor = (menor ? ma < mb : ma > mb) ? a : b;
                significativo = prueba.n() >= 2 && prueba.valorP() < 0.05;
                if (mejor != null && (clave.equals("costo") || clave.equals("cumplimiento") || clave.equals("diasHastaColapso"))) {
                    conclusion.append(String.format(Locale.ROOT, "%s: mejor %s (%.2f vs %.2f%s). ", m[1], mejor,
                            porAlg.get(mejor).media(), porAlg.get(mejor.equals(a) ? b : a).media(),
                            significativo ? String.format(Locale.ROOT, ", diferencia significativa p=%.3f", prueba.valorP())
                                    : String.format(Locale.ROOT, ", sin diferencia significativa p=%.3f", prueba.valorP())));
                }
            }
            lista.add(new ResumenMetrica(clave, m[1], menor, porAlg, prueba, mejor, significativo));
        }
        e.resumen = lista;
        e.conclusion = conclusion.toString().trim();
    }

    private void guardar(Experimento e) {
        persistencia.guardarExperimento(e.id, e.estado, e.descripcion, e, Instant.ofEpochMilli(e.creadoMs),
                e.terminadoMs == null ? null : Instant.ofEpochMilli(e.terminadoMs));
    }

    public Experimento obtener(String id) {
        Experimento e = experimentos.get(id);
        if (e != null) return e;
        ExperimentoEntidad ent = persistencia.experimento(id).orElseThrow(() -> new NoSuchElementException("No existe el experimento " + id + "."));
        try {
            Experimento leido = json.readValue(ent.getContenidoJson(), Experimento.class);
            if ("EN_CURSO".equals(leido.estado) || "EN_COLA".equals(leido.estado)) {
                leido.estado = "INTERRUMPIDO";
                leido.mensaje = "El servicio se reinició antes de terminar este experimento.";
            }
            return leido;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo leer el experimento " + id + ": " + ex.getMessage());
        }
    }

    /** Experimentos en memoria y los ultimos guardados. */
    public List<Map<String, Object>> listar() {
        Map<String, Map<String, Object>> filas = new LinkedHashMap<>();
        List<Experimento> enMemoria = new ArrayList<>(experimentos.values());
        enMemoria.sort((a, b) -> Long.compare(b.creadoMs, a.creadoMs));
        for (Experimento e : enMemoria) filas.put(e.id, fila(e.id, e.estado, e.descripcion, e.creadoMs, e.progreso));
        for (ExperimentoEntidad ent : persistencia.listarExperimentos()) {
            if (filas.containsKey(ent.getId())) continue;
            String estado = "EN_CURSO".equals(ent.getEstado()) || "EN_COLA".equals(ent.getEstado()) ? "INTERRUMPIDO" : ent.getEstado();
            filas.put(ent.getId(), fila(ent.getId(), estado, ent.getDescripcion(),
                    ent.getCreado() == null ? 0 : ent.getCreado().toEpochMilli(), "TERMINADO".equals(estado) ? 1.0 : 0.0));
        }
        return new ArrayList<>(filas.values());
    }

    private static Map<String, Object> fila(String id, String estado, String descripcion, long creado, double progreso) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("estado", estado);
        m.put("descripcion", descripcion);
        m.put("creadoMs", creado);
        m.put("progreso", progreso);
        return m;
    }

    /** Resultados en CSV (D22). */
    public String csv(String id) {
        Experimento e = obtener(id);
        StringBuilder sb = new StringBuilder("instancia;algoritmo;fecha_inicio;semilla;pedidos;entregados;cumplimiento;costo;km;costo_por_pedido;incumplidos;tiempo_computo_ms;tiempo_real_ms;dias_hasta_colapso;colapso\n");
        for (CorridaExperimento c : e.corridas) {
            Map<String, Double> i = c.indicadores();
            sb.append(c.instancia()).append(';').append(c.algoritmo()).append(';').append(c.fechaInicio()).append(';').append(c.semilla()).append(';')
                    .append(num(i.get("pedidos"))).append(';').append(num(i.get("entregados"))).append(';').append(num(i.get("cumplimiento"))).append(';')
                    .append(num(i.get("costo"))).append(';').append(num(i.get("km"))).append(';').append(num(i.get("costoPorPedido"))).append(';')
                    .append(num(i.get("incumplidos"))).append(';').append(num(i.get("tiempoComputoMs"))).append(';').append(c.tiempoRealMs()).append(';')
                    .append(num(i.get("diasHastaColapso"))).append(';').append(c.colapso() == null ? "" : c.colapso().replace(';', ',')).append('\n');
        }
        return sb.toString();
    }

    private static String num(Double v) {
        return v == null ? "" : String.format(Locale.ROOT, "%.2f", v);
    }

    @PreDestroy
    public void apagar() {
        hilo.shutdownNow();
    }
}
