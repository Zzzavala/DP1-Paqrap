package pe.pucp.paqrap.api;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.pucp.paqrap.persistencia.CorridaEntidad;
import pe.pucp.paqrap.persistencia.EventoCorridaEntidad;
import pe.pucp.paqrap.persistencia.RutaHistorialEntidad;
import pe.pucp.paqrap.persistencia.ServicioPersistencia;
import pe.pucp.paqrap.simulacion.CierreCorrida;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Historial de corridas (D24) con su cierre, sus rutas (B25) y sus incidencias (D19). */
@RestController
@RequestMapping("/api/corridas")
public class CorridasController {

    private final ServicioPersistencia persistencia;

    public CorridasController(ServicioPersistencia persistencia) {
        this.persistencia = persistencia;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (CorridaEntidad c : persistencia.listarCorridas()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("escenario", c.getEscenario());
            m.put("algoritmo", c.getAlgoritmo());
            m.put("semilla", c.getSemilla());
            m.put("fechaInicioSim", c.getFechaInicioSim());
            m.put("inicioReal", c.getInicioReal());
            m.put("finReal", c.getFinReal());
            m.put("estado", c.getEstado());
            m.put("motivoFin", c.getMotivoFin());
            m.put("cumplimiento", c.getCumplimiento());
            m.put("costoTotal", c.getCostoTotal());
            m.put("pedidos", c.getPedidos());
            m.put("tieneCierre", c.getCierreJson() != null && !c.getCierreJson().isBlank());
            lista.add(m);
        }
        return lista;
    }

    @GetMapping("/{id}")
    public CierreCorrida cierre(@PathVariable String id) {
        return persistencia.cierreDe(id).orElseThrow(() -> new NoSuchElementException("La corrida " + id + " no tiene cierre registrado."));
    }

    @GetMapping(value = "/{id}/csv", produces = "text/csv")
    public ResponseEntity<byte[]> csv(@PathVariable String id) {
        return ReportesCsv.respuesta(ReportesCsv.cierre(cierre(id)), "cierre-" + id + ".csv");
    }

    @GetMapping("/{id}/rutas")
    public Page<RutaHistorialEntidad> rutas(@PathVariable String id, @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "100") int tamano) {
        return persistencia.rutasDe(id, pagina, tamano);
    }

    @GetMapping("/{id}/eventos")
    public Page<EventoCorridaEntidad> eventos(@PathVariable String id, @RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "100") int tamano) {
        return persistencia.eventosDe(id, pagina, tamano);
    }
}
