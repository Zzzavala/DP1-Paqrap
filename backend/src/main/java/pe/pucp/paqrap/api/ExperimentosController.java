package pe.pucp.paqrap.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.pucp.paqrap.simulacion.ServicioExperimentacion;

import java.util.List;
import java.util.Map;

/** Experimentacion numerica GA vs ACO (B23, D20, C25). */
@RestController
@RequestMapping("/api/experimentos")
public class ExperimentosController {

    private final ServicioExperimentacion experimentacion;

    public ExperimentosController(ServicioExperimentacion experimentacion) {
        this.experimentacion = experimentacion;
    }

    @PostMapping
    public ServicioExperimentacion.Experimento lanzar(@RequestBody(required = false) ServicioExperimentacion.SolicitudExperimento s) {
        return experimentacion.lanzar(s);
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return experimentacion.listar();
    }

    @GetMapping("/{id}")
    public ServicioExperimentacion.Experimento obtener(@PathVariable String id) {
        return experimentacion.obtener(id);
    }

    @GetMapping(value = "/{id}/csv", produces = "text/csv")
    public ResponseEntity<byte[]> csv(@PathVariable String id) {
        return ReportesCsv.respuesta(experimentacion.csv(id), "experimento-" + id + ".csv");
    }
}
