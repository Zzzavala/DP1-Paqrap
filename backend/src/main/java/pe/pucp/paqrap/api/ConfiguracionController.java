package pe.pucp.paqrap.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.pucp.paqrap.comun.ParametrosOperacion;
import pe.pucp.paqrap.comun.UmbralesSemaforo;
import pe.pucp.paqrap.flota.JornadaConductor;
import pe.pucp.paqrap.flota.TipoAveria;
import pe.pucp.paqrap.registro.TipoEntrega;
import pe.pucp.paqrap.simulacion.Escenario;
import pe.pucp.paqrap.simulacion.FuenteDatos;
import pe.pucp.paqrap.simulacion.ParametrosCorrida;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Salud del servicio y valores por defecto que muestra la pantalla V-1 (flota, almacenes, turnos, escenarios). */
@RestController
@RequestMapping("/api")
public class ConfiguracionController {

    private final FuenteDatos fuente;

    public ConfiguracionController(FuenteDatos fuente) {
        this.fuente = fuente;
    }

    @GetMapping("/salud")
    public Map<String, Object> salud() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estado", "OK");
        m.put("servicio", "PaqRap");
        m.put("version", "1.0.0");
        m.put("horaServidor", LocalDateTime.now(ParametrosCorrida.ZONA_LIMA).toString());
        return m;
    }

    @GetMapping("/config")
    public Map<String, Object> configuracion() {
        ParametrosOperacion op = new ParametrosOperacion();
        ParametrosCorrida def = new ParametrosCorrida();
        Map<String, Object> m = new LinkedHashMap<>();

        List<Map<String, Object>> escenarios = new ArrayList<>();
        for (Escenario e : Escenario.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", e.name());
            x.put("etiqueta", e.getEtiqueta());
            x.put("descripcion", e.getDescripcionCorta());
            x.put("fechaInicioPorDefecto", ParametrosCorrida.fechaInicioPorDefecto(e).toString());
            escenarios.add(x);
        }
        m.put("escenarios", escenarios);

        m.put("algoritmos", List.of(
                Map.of("id", "ACO", "etiqueta", "Optimización por Colonia de Hormigas (ACO)",
                        "parametros", Map.of("hormigas", 10, "iteraciones", 25, "alfa", 1.0, "beta", 2.0, "evaporacion", 0.1, "q0", 0.9)),
                Map.of("id", "GA", "etiqueta", "Algoritmo Genético (GA)",
                        "parametros", Map.of("poblacion", 30, "generaciones", 60, "tasaCruce", 0.85, "tasaMutacion", 0.15, "torneo", 4))));

        List<Map<String, Object>> tipos = new ArrayList<>();
        for (TipoEntrega t : TipoEntrega.values()) tipos.add(Map.of("id", t.name(), "etiqueta", t.getEtiqueta(), "plazoHoras", t.plazoHoras));
        m.put("tiposEntrega", tipos);

        m.put("flota", List.of(
                Map.of("tipo", "AUTO", "etiqueta", "Auto", "letra", "A", "cantidad", op.getCantidadAutos(), "capacidad", op.getCapacidadAuto(),
                        "costoKm", op.getCostoKmAuto(), "velocidad", op.getVelocidadAuto(), "codigos", "TA01–TA" + String.format("%02d", op.getCantidadAutos())),
                Map.of("tipo", "MOTO", "etiqueta", "Moto", "letra", "M", "cantidad", op.getCantidadMotos(), "capacidad", op.getCapacidadMoto(),
                        "costoKm", op.getCostoKmMoto(), "velocidad", op.getVelocidadMoto(), "codigos", "TM01–TM" + String.format("%02d", op.getCantidadMotos())),
                Map.of("tipo", "BICICLETA", "etiqueta", "Bicicleta", "letra", "B", "cantidad", op.getCantidadBicicletas(), "capacidad", op.getCapacidadBicicleta(),
                        "costoKm", op.getCostoKmBicicleta(), "velocidad", op.getVelocidadBicicleta(), "codigos", "TB01–TB" + String.format("%02d", op.getCantidadBicicletas()))));

        m.put("almacenes", List.of(
                Map.of("id", "CENTRAL", "nombre", "Almacén central", "rol", "Abastecimiento permanente (sin capacidad máxima)",
                        "x", op.getAlmacenCentralX(), "y", op.getAlmacenCentralY(), "capacidad", "Sin capacidad máxima", "recarga", "—"),
                Map.of("id", "NOR-OESTE", "nombre", "Intermedio Nor-Oeste", "rol", "Almacén intermedio",
                        "x", op.getAlmacenNorOesteX(), "y", op.getAlmacenNorOesteY(), "capacidad", op.getCapacidadAlmacenIntermedio() + " unid.",
                        "recarga", "Diaria, 23:59:59 (instantánea)"),
                Map.of("id", "ESTE", "nombre", "Intermedio Este", "rol", "Almacén intermedio",
                        "x", op.getAlmacenEsteX(), "y", op.getAlmacenEsteY(), "capacidad", op.getCapacidadAlmacenIntermedio() + " unid.",
                        "recarga", "Diaria, 23:59:59 (instantánea)")));

        int[] cambios = op.getHorasCambioTurno();
        List<Map<String, Object>> turnos = new ArrayList<>();
        for (int i = 0; i < cambios.length; i++) {
            int ini = cambios[i], fin = cambios[(i + 1) % cambios.length];
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("rango", String.format("%02d:00 – %02d:00", ini, fin));
            t.put("alimentacion", String.format("1 h escalonada por unidad entre %02d:00 y %02d:00 (≥ 1 h del cambio de turno)",
                    (ini + 1) % 24, (fin + 23) % 24));
            turnos.add(t);
        }
        m.put("turnos", turnos);
        m.put("reglaAlimentacion", "Cada conductor come 1 h por turno; la unidad NN empieza " +
                "1 + ((NN − 1) mod 6) h después del inicio del turno (TA01: 08:00, 16:00 y 00:00). Desfases: "
                + JornadaConductor.desfaseAlimentacion(1) + " a " + JornadaConductor.desfaseAlimentacion(6) + " h.");

        List<Map<String, Object>> averias = new ArrayList<>();
        for (TipoAveria t : TipoAveria.values()) averias.add(Map.of("tipo", t.getNumero(), "etiqueta", t.getEtiqueta(), "transbordo", t.requiereTransbordo()));
        m.put("tiposAveria", averias);

        UmbralesSemaforo u = new UmbralesSemaforo();
        m.put("umbrales", Map.of("verde", u.getVerde(), "ambar", u.getAmbar()));
        m.put("ciudad", Map.of("anchoKm", op.getAnchoCiudadKm(), "altoKm", op.getAltoCiudadKm()));
        m.put("parametrosPorDefecto", def);
        m.put("operacionPorDefecto", op);

        List<YearMonth> meses = fuente.getMesesConVentas();
        m.put("datosDisponibles", Map.of(
                "primerMes", meses.isEmpty() ? "" : meses.get(0).toString(),
                "ultimoMes", meses.isEmpty() ? "" : meses.get(meses.size() - 1).toString(),
                "meses", meses.size()));
        return m;
    }
}
