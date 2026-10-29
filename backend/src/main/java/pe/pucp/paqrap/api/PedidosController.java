package pe.pucp.paqrap.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.pucp.paqrap.comun.ResultadoLectura;
import pe.pucp.paqrap.registro.LectorVentas;
import pe.pucp.paqrap.simulacion.MotorSimulacion;
import pe.pucp.paqrap.simulacion.ServicioSimulacion;
import pe.pucp.paqrap.visualizador.DetalleVisual;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de pedidos y clientes (Modulo A): registro (A1, A2, A5, A14, A25),
 * seguimiento por codigo (A8, A12, A20), edicion y cancelacion antes del
 * despacho (A6, A7), carga masiva (A9), busqueda (A18) y exportacion (A24).
 * El registro se hace sobre la operacion dia a dia en curso.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidosController {

    public record SolicitudPedido(String cliente, String nombreCliente, Integer x, Integer y, Integer cantidad,
                                  String tipoEntrega, String canal, String observaciones) {
    }

    public record EdicionPedido(Integer cantidad, String tipoEntrega, String observaciones) {
    }

    private final ServicioSimulacion simulacion;

    public PedidosController(ServicioSimulacion simulacion) {
        this.simulacion = simulacion;
    }

    @GetMapping
    public List<MotorSimulacion.FilaPedido> buscar(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String estado,
                                                   @RequestParam(required = false) String tipo,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
                                                   @RequestParam(defaultValue = "300") int limite) {
        return simulacion.buscarPedidos(q, estado, tipo, desde, hasta, limite);
    }

    @GetMapping("/{id}")
    public DetalleVisual.Pedido seguimiento(@PathVariable String id) {
        return simulacion.detallePedido(id);
    }

    @PostMapping
    public DetalleVisual.Pedido registrar(@RequestBody SolicitudPedido s) {
        if (s.x() == null || s.y() == null) throw new IllegalArgumentException("Indique las coordenadas (x, y) de entrega.");
        if (s.cantidad() == null) throw new IllegalArgumentException("Indique la cantidad de paquetes.");
        return simulacion.registrarPedido(s.cliente(), s.nombreCliente(), s.x(), s.y(), s.cantidad(), s.tipoEntrega(), s.canal(), s.observaciones());
    }

    @PutMapping("/{id}")
    public DetalleVisual.Pedido editar(@PathVariable String id, @RequestBody EdicionPedido e) {
        return simulacion.editarPedido(id, e.cantidad(), e.tipoEntrega(), e.observaciones());
    }

    @DeleteMapping("/{id}")
    public DetalleVisual.Pedido cancelar(@PathVariable String id, @RequestParam(required = false) String motivo) {
        return simulacion.cancelarPedido(id, motivo);
    }

    @PostMapping("/lote")
    public Map<String, Object> cargarLote(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) throw new IllegalArgumentException("El archivo está vacío.");
        ResultadoLectura<LectorVentas.RegistroVenta> lectura = LectorVentas.leer(
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8), 70, 50);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lineas", lectura.getLineasLeidas());
        r.put("errores", lectura.getTotalErrores());
        r.put("detalleErrores", lectura.getErrores());
        if (lectura.getRegistros().isEmpty()) {
            r.put("registrados", 0);
            r.put("mensaje", "No hay pedidos válidos en el archivo.");
            return r;
        }
        int n = simulacion.cargarLote(lectura.getRegistros());
        r.put("registrados", n);
        r.put("mensaje", n + " pedidos registrados; se planifican automáticamente.");
        return r;
    }

    @GetMapping(value = "/exportar", produces = "text/csv")
    public ResponseEntity<byte[]> exportar() {
        return ReportesCsv.respuesta(simulacion.exportarPedidosCsv(), "pedidos.csv");
    }
}
