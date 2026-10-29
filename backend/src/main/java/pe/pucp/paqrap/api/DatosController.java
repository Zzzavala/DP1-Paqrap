package pe.pucp.paqrap.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.pucp.paqrap.simulacion.Escenario;
import pe.pucp.paqrap.simulacion.FuenteDatos;
import pe.pucp.paqrap.simulacion.ParametrosCorrida;
import pe.pucp.paqrap.simulacion.ServicioSimulacion;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Archivos de entrada de las corridas (V-1): validacion de los publicados y carga de personalizados (A9, D6, D7). */
@RestController
@RequestMapping("/api/datos")
public class DatosController {

    private final FuenteDatos fuente;
    private final ServicioSimulacion simulacion;

    public DatosController(FuenteDatos fuente, ServicioSimulacion simulacion) {
        this.fuente = fuente;
        this.simulacion = simulacion;
    }

    @GetMapping("/archivos")
    public List<FuenteDatos.ResumenArchivo> archivos(
            @RequestParam(defaultValue = "SIMULACION_5D") String escenario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(defaultValue = "5") int dias) {
        Escenario esc = Escenario.valueOf(escenario.toUpperCase(Locale.ROOT));
        LocalDateTime inicio = fechaInicio != null ? fechaInicio : ParametrosCorrida.fechaInicioPorDefecto(esc);
        return fuente.resumenPara(esc, inicio, Math.max(1, Math.min(30, dias)));
    }

    @PostMapping("/archivos/{tipo}")
    public Map<String, Object> subir(@PathVariable String tipo, @RequestParam("archivo") MultipartFile archivo,
                                     @RequestParam(required = false) String mes) throws IOException {
        String t = tipo.toLowerCase(Locale.ROOT);
        if (archivo == null || archivo.isEmpty()) throw new IllegalArgumentException("El archivo está vacío.");
        YearMonth porDefecto = mes != null && !mes.isBlank() ? YearMonth.parse(mes) : simulacion.mesDeReferencia();
        FuenteDatos.ArchivoSubido<?> a = fuente.subir(t, archivo.getOriginalFilename(), archivo.getBytes(), porDefecto);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tipo", a.tipo());
        r.put("nombre", a.nombre());
        r.put("mes", a.mes() == null ? null : a.mes().toString());
        r.put("registros", a.lectura().getRegistros().size());
        r.put("lineas", a.lectura().getLineasLeidas());
        r.put("errores", a.lectura().getTotalErrores());
        r.put("valido", a.lectura().esValido());
        r.put("detalleErrores", a.lectura().getErrores());
        r.put("mensaje", a.lectura().esValido()
                ? "Archivo validado: se usará en las siguientes corridas."
                : "El archivo no es válido y no se usará (revise el formato de las líneas con error).");
        return r;
    }

    @DeleteMapping("/archivos/{tipo}")
    public Map<String, String> quitar(@PathVariable String tipo) {
        fuente.quitarPersonalizados(tipo.toLowerCase(Locale.ROOT));
        return Map.of("mensaje", "Se volverán a usar los archivos publicados por el curso.");
    }
}
