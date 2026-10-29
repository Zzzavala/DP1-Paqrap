package pe.pucp.paqrap.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.pucp.paqrap.simulacion.CierreCorrida;
import pe.pucp.paqrap.simulacion.ParametrosCorrida;
import pe.pucp.paqrap.simulacion.ServicioSimulacion;
import pe.pucp.paqrap.visualizador.DetalleVisual;
import pe.pucp.paqrap.visualizador.EstadoVisual;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * API del Visualizador (Modulo C) y de la corrida (Modulos B y D): iniciar y
 * detener, estado en vivo para el mapa, detalle de vehiculo/pedido/almacen,
 * bloqueo manual de tramos, averias, umbrales del semaforo y cierre (V-3).
 */
@RestController
@RequestMapping("/api/simulacion")
public class SimulacionController {

    private record Cache(EstadoVisual estado, byte[] json) {
    }

    public record SolicitudBloqueo(int x1, int y1, int x2, int y2) {
    }

    public record SolicitudAveria(String vehiculo, int tipo, String causa) {
    }

    public record SolicitudUmbrales(double verde, double ambar) {
    }

    public record SolicitudVelocidades(Double auto, Double moto, Double bicicleta) {
    }

    private final ServicioSimulacion simulacion;
    private final ObjectMapper json;
    private final AtomicReference<Cache> cache = new AtomicReference<>();

    public SimulacionController(ServicioSimulacion simulacion, ObjectMapper json) {
        this.simulacion = simulacion;
        this.json = json;
    }

    @PostMapping("/iniciar")
    public EstadoVisual iniciar(@RequestBody(required = false) ParametrosCorrida parametros) {
        return simulacion.iniciar(parametros);
    }

    @PostMapping("/detener")
    public CierreCorrida detener() {
        return simulacion.detener();
    }

    /** Estado compartido por todos los dispositivos (se serializa una sola vez por publicacion). */
    @GetMapping(value = "/estado", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> estado() throws JsonProcessingException {
        EstadoVisual e = simulacion.estado();
        Cache c = cache.get();
        if (c == null || c.estado() != e) {
            c = new Cache(e, json.writeValueAsBytes(e));
            cache.set(c);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(c.json());
    }

    @GetMapping("/vehiculos/{id}")
    public DetalleVisual.Vehiculo vehiculo(@PathVariable String id) {
        return simulacion.detalleVehiculo(id);
    }

    @GetMapping("/pedidos/{id}")
    public DetalleVisual.Pedido pedido(@PathVariable String id) {
        return simulacion.detallePedido(id);
    }

    @GetMapping("/almacenes/{id}")
    public DetalleVisual.Almacen almacen(@PathVariable String id) {
        return simulacion.detalleAlmacen(id);
    }

    @PostMapping("/bloqueos")
    public Map<String, Object> bloqueo(@RequestBody SolicitudBloqueo s) {
        boolean bloqueado = simulacion.alternarBloqueo(s.x1(), s.y1(), s.x2(), s.y2());
        return Map.of("bloqueado", bloqueado,
                "mensaje", bloqueado ? "Tramo bloqueado: las unidades afectadas recalculan su ruta." : "Tramo reabierto.");
    }

    @PostMapping("/averias")
    public Map<String, String> averia(@RequestBody SolicitudAveria s) {
        if (s.vehiculo() == null || s.vehiculo().isBlank()) throw new IllegalArgumentException("Indique la unidad averiada.");
        simulacion.registrarAveria(s.vehiculo(), s.tipo(), s.causa());
        return Map.of("mensaje", "Avería registrada para " + s.vehiculo().toUpperCase() + ".");
    }

    @PutMapping("/umbrales")
    public Map<String, String> umbrales(@RequestBody SolicitudUmbrales s) {
        simulacion.actualizarUmbrales(s.verde(), s.ambar());
        return Map.of("mensaje", "Umbrales actualizados.");
    }

    @PutMapping("/velocidades")
    public Map<String, String> velocidades(@RequestBody SolicitudVelocidades s) {
        simulacion.actualizarVelocidades(s.auto(), s.moto(), s.bicicleta());
        return Map.of("mensaje", "Velocidades actualizadas: aplican desde la siguiente ventana de planificación.");
    }

    @GetMapping("/cierre")
    public CierreCorrida cierre() {
        return simulacion.cierre();
    }

    @GetMapping(value = "/cierre/csv", produces = "text/csv")
    public ResponseEntity<byte[]> cierreCsv() {
        return ReportesCsv.respuesta(ReportesCsv.cierre(simulacion.cierre()), "cierre-corrida.csv");
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
