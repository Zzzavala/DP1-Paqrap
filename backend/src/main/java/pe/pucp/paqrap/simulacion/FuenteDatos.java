package pe.pucp.paqrap.simulacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import pe.pucp.paqrap.comun.ConversorTiempo;
import pe.pucp.paqrap.comun.ResultadoLectura;
import pe.pucp.paqrap.flota.LectorAverias;
import pe.pucp.paqrap.flota.LectorAverias.RegistroAveria;
import pe.pucp.paqrap.flota.LectorBloqueos;
import pe.pucp.paqrap.flota.LectorBloqueos.RegistroBloqueo;
import pe.pucp.paqrap.flota.PlanMantenimiento;
import pe.pucp.paqrap.flota.PlanMantenimiento.RegistroMantenimiento;
import pe.pucp.paqrap.registro.LectorVentas;
import pe.pucp.paqrap.registro.LectorVentas.RegistroVenta;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fuente de los archivos de entrada de las corridas (A9, D6, D7, P19).
 *
 * - Datos publicados por el curso (ventas.AAAAMM.txt de 2026 a 2028,
 *   bloqueo.AAMM.txt y mant.preventivo.09.10): vienen empaquetados en el
 *   servicio (classpath "datos/") y, si existe la carpeta configurada en
 *   paqrap.datos-externos-dir, los archivos de esa carpeta tienen prioridad
 *   (asi se agregan meses nuevos sin recompilar).
 * - Archivos personalizados: el usuario puede subir desde V-1 un archivo de
 *   ventas, de bloqueos, de averias o de mantenimiento; se valida y reemplaza
 *   al publicado para las corridas siguientes, hasta que se quite.
 */
@Component
public class FuenteDatos {

    private static final Logger LOG = LoggerFactory.getLogger(FuenteDatos.class);
    private static final Pattern NOMBRE_VENTAS = Pattern.compile("ventas\\.?(\\d{4})(\\d{2})(\\.txt)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOMBRE_BLOQUEOS = Pattern.compile("bloqueo[s]?\\.?(\\d{2})(\\d{2})(\\.txt)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOMBRE_BLOQUEOS_LARGO = Pattern.compile("(\\d{4})(\\d{2})\\.bloquead", Pattern.CASE_INSENSITIVE);

    /** Archivo subido por el usuario, ya validado. */
    public record ArchivoSubido<T>(String tipo, String nombre, YearMonth mes, LocalDateTime cargado, ResultadoLectura<T> lectura) {
    }

    /** Resumen de un archivo para la pantalla V-1. */
    public record ResumenArchivo(String tipo, String nombre, String descripcion, int registros, int lineas, int errores,
                                 boolean valido, boolean personalizado, List<ResultadoLectura.ErrorLinea> detalleErrores,
                                 String advertencia) {
    }

    private final int anchoKm = 70;
    private final int altoKm = 50;
    private final Path carpetaExterna;

    private final Map<YearMonth, ResultadoLectura<RegistroVenta>> ventasPublicadas = new ConcurrentHashMap<>();
    private final Map<YearMonth, ResultadoLectura<RegistroBloqueo>> bloqueosPublicados = new ConcurrentHashMap<>();
    private volatile ResultadoLectura<RegistroMantenimiento> mantenimientoPublicado;
    private final TreeSet<YearMonth> mesesConVentas = new TreeSet<>();

    private final Map<YearMonth, ArchivoSubido<RegistroVenta>> ventasPersonalizadas = new ConcurrentHashMap<>();
    private final Map<YearMonth, ArchivoSubido<RegistroBloqueo>> bloqueosPersonalizados = new ConcurrentHashMap<>();
    private volatile ArchivoSubido<RegistroAveria> averiasPersonalizadas;
    private volatile ArchivoSubido<RegistroMantenimiento> mantenimientoPersonalizado;

    public FuenteDatos(@Value("${paqrap.datos-externos-dir:./datos-externos}") String carpetaExterna) {
        this.carpetaExterna = Path.of(carpetaExterna);
        descubrirMeses();
    }

    private void descubrirMeses() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (Resource r : resolver.getResources("classpath*:datos/ventas/ventas.*.txt")) {
                mesDeNombreVentas(r.getFilename()).ifPresent(mesesConVentas::add);
            }
        } catch (IOException e) {
            LOG.warn("No se pudieron listar los archivos de ventas empaquetados: {}", e.getMessage());
        }
        if (Files.isDirectory(carpetaExterna)) {
            try (var archivos = Files.list(carpetaExterna)) {
                archivos.forEach(p -> mesDeNombreVentas(p.getFileName().toString()).ifPresent(mesesConVentas::add));
            } catch (IOException e) {
                LOG.warn("No se pudo leer la carpeta de datos externos {}: {}", carpetaExterna, e.getMessage());
            }
        }
        LOG.info("Archivos de ventas disponibles: {} meses ({} a {})", mesesConVentas.size(),
                mesesConVentas.isEmpty() ? "-" : mesesConVentas.first(), mesesConVentas.isEmpty() ? "-" : mesesConVentas.last());
    }

    // ------------------------------------------------------------------
    // Datos publicados
    // ------------------------------------------------------------------

    public ResultadoLectura<RegistroVenta> ventasPublicadas(YearMonth mes) {
        return ventasPublicadas.computeIfAbsent(mes, m -> {
            String nombre = String.format("ventas.%04d%02d.txt", m.getYear(), m.getMonthValue());
            try (Reader r = abrir("ventas", nombre)) {
                if (r == null) return new ResultadoLectura<>();
                return LectorVentas.leer(r, anchoKm, altoKm);
            } catch (IOException e) {
                LOG.warn("Error leyendo {}: {}", nombre, e.getMessage());
                return new ResultadoLectura<>();
            }
        });
    }

    public ResultadoLectura<RegistroBloqueo> bloqueosPublicados(YearMonth mes) {
        return bloqueosPublicados.computeIfAbsent(mes, m -> {
            String nombre = String.format("bloqueo.%02d%02d.txt", m.getYear() % 100, m.getMonthValue());
            try (Reader r = abrir("bloqueos", nombre)) {
                if (r == null) return new ResultadoLectura<>();
                return LectorBloqueos.leer(r, anchoKm, altoKm);
            } catch (IOException e) {
                LOG.warn("Error leyendo {}: {}", nombre, e.getMessage());
                return new ResultadoLectura<>();
            }
        });
    }

    public ResultadoLectura<RegistroMantenimiento> mantenimientoPublicado() {
        if (mantenimientoPublicado == null) {
            synchronized (this) {
                if (mantenimientoPublicado == null) {
                    ResultadoLectura<RegistroMantenimiento> lectura = new ResultadoLectura<>();
                    try (Reader r = abrir("mantenimiento", "mant.preventivo.09.10.txt")) {
                        if (r != null) lectura = PlanMantenimiento.leer(r);
                    } catch (IOException e) {
                        LOG.warn("Error leyendo el plan de mantenimiento: {}", e.getMessage());
                    }
                    mantenimientoPublicado = lectura;
                }
            }
        }
        return mantenimientoPublicado;
    }

    /** Abre un archivo publicado: primero la carpeta externa, luego el classpath. null si no existe. */
    private Reader abrir(String subcarpeta, String nombre) throws IOException {
        Path externo = carpetaExterna.resolve(nombre);
        if (!Files.isRegularFile(externo)) externo = carpetaExterna.resolve(subcarpeta).resolve(nombre);
        if (Files.isRegularFile(externo)) return Files.newBufferedReader(externo, StandardCharsets.UTF_8);
        ClassPathResource recurso = new ClassPathResource("datos/" + subcarpeta + "/" + nombre);
        if (!recurso.exists()) return null;
        InputStream in = recurso.getInputStream();
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Archivos personalizados (subidos desde V-1)
    // ------------------------------------------------------------------

    public ArchivoSubido<?> subir(String tipo, String nombreArchivo, byte[] contenido, YearMonth mesPorDefecto) throws IOException {
        String nombre = nombreArchivo == null ? tipo : Path.of(nombreArchivo).getFileName().toString();
        try (Reader r = new InputStreamReader(new ByteArrayInputStream(contenido), StandardCharsets.UTF_8)) {
            switch (tipo) {
                case "ventas": {
                    YearMonth mes = mesDeNombreVentas(nombre).orElse(mesPorDefecto);
                    ArchivoSubido<RegistroVenta> a = new ArchivoSubido<>(tipo, nombre, mes, LocalDateTime.now(),
                            LectorVentas.leer(r, anchoKm, altoKm));
                    if (a.lectura().esValido()) ventasPersonalizadas.put(mes, a);
                    return a;
                }
                case "bloqueos": {
                    YearMonth mes = mesDeNombreBloqueos(nombre).orElse(mesPorDefecto);
                    ArchivoSubido<RegistroBloqueo> a = new ArchivoSubido<>(tipo, nombre, mes, LocalDateTime.now(),
                            LectorBloqueos.leer(r, anchoKm, altoKm));
                    if (a.lectura().esValido()) bloqueosPersonalizados.put(mes, a);
                    return a;
                }
                case "averias": {
                    ArchivoSubido<RegistroAveria> a = new ArchivoSubido<>(tipo, nombre, mesPorDefecto, LocalDateTime.now(),
                            LectorAverias.leer(r));
                    if (a.lectura().esValido()) averiasPersonalizadas = a;
                    return a;
                }
                case "mantenimiento": {
                    ArchivoSubido<RegistroMantenimiento> a = new ArchivoSubido<>(tipo, nombre, mesPorDefecto, LocalDateTime.now(),
                            PlanMantenimiento.leer(r));
                    if (a.lectura().esValido()) mantenimientoPersonalizado = a;
                    return a;
                }
                default:
                    throw new IllegalArgumentException("Tipo de archivo desconocido: " + tipo);
            }
        }
    }

    /** Quita los archivos personalizados de un tipo (vuelven a usarse los publicados). */
    public void quitarPersonalizados(String tipo) {
        switch (tipo) {
            case "ventas": ventasPersonalizadas.clear(); break;
            case "bloqueos": bloqueosPersonalizados.clear(); break;
            case "averias": averiasPersonalizadas = null; break;
            case "mantenimiento": mantenimientoPersonalizado = null; break;
            case "todos":
                ventasPersonalizadas.clear();
                bloqueosPersonalizados.clear();
                averiasPersonalizadas = null;
                mantenimientoPersonalizado = null;
                break;
            default: throw new IllegalArgumentException("Tipo de archivo desconocido: " + tipo);
        }
    }

    // ------------------------------------------------------------------
    // Datos de una corrida y resumen para V-1
    // ------------------------------------------------------------------

    /** Fija los archivos que usara una corrida (publicados + personalizados vigentes). */
    public DatosCorrida datosParaCorrida() {
        Map<YearMonth, ArchivoSubido<RegistroVenta>> ventasP = new LinkedHashMap<>(ventasPersonalizadas);
        Map<YearMonth, ArchivoSubido<RegistroBloqueo>> bloqueosP = new LinkedHashMap<>(bloqueosPersonalizados);
        ArchivoSubido<RegistroAveria> averiasP = averiasPersonalizadas;
        ArchivoSubido<RegistroMantenimiento> mantP = mantenimientoPersonalizado;

        PlanMantenimiento plan = PlanMantenimiento.desdeRegistros(
                mantP != null ? mantP.lectura().getRegistros() : mantenimientoPublicado().getRegistros());
        YearMonth ultimo = mesesConVentas.isEmpty() ? YearMonth.of(2028, 12) : mesesConVentas.last();
        for (YearMonth m : ventasP.keySet()) if (m.isAfter(ultimo)) ultimo = m;

        Map<String, String> fuentes = new LinkedHashMap<>();
        fuentes.put("ventas", ventasP.isEmpty() ? "Publicados por el curso (ventas.AAAAMM.txt)" : "Personalizados: " + ventasP.size() + " mes(es)");
        fuentes.put("bloqueos", bloqueosP.isEmpty() ? "Publicados por el curso (bloqueo.AAMM.txt)" : "Personalizados: " + bloqueosP.size() + " mes(es)");
        fuentes.put("mantenimiento", mantP == null ? "mant.preventivo.09.10 (publicado)" : mantP.nombre());
        fuentes.put("averias", averiasP == null ? "Generadas con la semilla de la corrida" : averiasP.nombre());

        return new DatosCorrida(
                mes -> ventasP.containsKey(mes) ? ventasP.get(mes).lectura().getRegistros() : ventasPublicadas(mes).getRegistros(),
                mes -> bloqueosP.containsKey(mes) ? bloqueosP.get(mes).lectura().getRegistros() : bloqueosPublicados(mes).getRegistros(),
                plan,
                averiasP == null ? null : new ArrayList<>(averiasP.lectura().getRegistros()),
                ultimo,
                fuentes);
    }

    /** Archivos que usaria una corrida con este escenario y fecha de inicio, con su validacion. */
    public List<ResumenArchivo> resumenPara(Escenario escenario, LocalDateTime inicio, int dias) {
        List<ResumenArchivo> lista = new ArrayList<>();
        YearMonth mesInicio = YearMonth.from(inicio);
        List<YearMonth> meses = new ArrayList<>();
        if (escenario == Escenario.SIMULACION_5D) {
            for (YearMonth m = mesInicio; !m.isAfter(YearMonth.from(inicio.plusDays(dias))); m = m.plusMonths(1)) meses.add(m);
        } else {
            meses.add(mesInicio);
            meses.add(mesInicio.plusMonths(1));
        }

        // Pedidos
        int pedidosPeriodo = 0, lineas = 0, errores = 0;
        boolean personalizado = false, valido = true;
        List<String> nombres = new ArrayList<>();
        List<ResultadoLectura.ErrorLinea> detalle = new ArrayList<>();
        double h0 = ConversorTiempo.aHoras(inicio);
        double h1 = escenario == Escenario.SIMULACION_5D ? h0 + dias * 24.0 : Double.POSITIVE_INFINITY;
        List<Double> ventasDelPeriodo = new ArrayList<>();
        for (YearMonth m : meses) {
            ArchivoSubido<RegistroVenta> p = ventasPersonalizadas.get(m);
            ResultadoLectura<RegistroVenta> l = p != null ? p.lectura() : ventasPublicadas(m);
            if (p != null) personalizado = true;
            nombres.add(p != null ? p.nombre() : String.format("ventas.%04d%02d.txt", m.getYear(), m.getMonthValue()));
            double base = ConversorTiempo.inicioDeMes(m);
            for (RegistroVenta r : l.getRegistros()) {
                double h = base + r.horasDesdeInicioDeMes();
                if (h >= h0 && h < h1) {
                    pedidosPeriodo++;
                    ventasDelPeriodo.add(h);
                }
            }
            lineas += l.getLineasLeidas();
            errores += l.getTotalErrores();
            detalle.addAll(l.getErrores());
            if (m.equals(mesInicio) && l.getRegistros().isEmpty()) valido = false;
        }
        String descPedidos = escenario == Escenario.SIMULACION_5D
                ? pedidosPeriodo + " pedidos en los " + dias + " días del periodo"
                : (escenario == Escenario.COLAPSO_LOGISTICO
                ? "Pedidos desde la fecha de inicio, mes a mes, hasta el colapso (" + pedidosPeriodo + " en los 2 primeros meses)"
                : "Pedidos llegan en tiempo real desde la fecha de inicio (" + pedidosPeriodo + " en los 2 primeros meses) + registro manual");
        lista.add(new ResumenArchivo("ventas", String.join(", ", nombres), descPedidos, pedidosPeriodo, lineas, errores,
                valido, personalizado, limitar(detalle), advertenciaDiasSinPedidos(escenario, ventasDelPeriodo, h0, dias)));

        // Bloqueos
        int bloqueosPeriodo = 0;
        lineas = 0;
        errores = 0;
        personalizado = false;
        nombres = new ArrayList<>();
        detalle = new ArrayList<>();
        for (YearMonth m : meses) {
            ArchivoSubido<RegistroBloqueo> p = bloqueosPersonalizados.get(m);
            ResultadoLectura<RegistroBloqueo> l = p != null ? p.lectura() : bloqueosPublicados(m);
            if (p != null) personalizado = true;
            nombres.add(p != null ? p.nombre() : String.format("bloqueo.%02d%02d.txt", m.getYear() % 100, m.getMonthValue()));
            double base = ConversorTiempo.inicioDeMes(m);
            for (RegistroBloqueo r : l.getRegistros()) {
                double fin = base + r.finRelativo();
                double ini = base + r.inicioRelativo();
                if (fin > h0 && ini < h1) bloqueosPeriodo++;
            }
            lineas += l.getLineasLeidas();
            errores += l.getTotalErrores();
            detalle.addAll(l.getErrores());
        }
        lista.add(new ResumenArchivo("bloqueos", String.join(", ", nombres),
                bloqueosPeriodo + " bloqueos de calles " + (escenario == Escenario.SIMULACION_5D ? "en el periodo" : "en los 2 primeros meses"),
                bloqueosPeriodo, lineas, errores, true, personalizado, limitar(detalle), null));

        // Mantenimiento
        ArchivoSubido<RegistroMantenimiento> mp = mantenimientoPersonalizado;
        ResultadoLectura<RegistroMantenimiento> ml = mp != null ? mp.lectura() : mantenimientoPublicado();
        lista.add(new ResumenArchivo("mantenimiento", mp != null ? mp.nombre() : "mant.preventivo.09.10",
                ml.getRegistros().size() + " mantenimientos preventivos (plan bimestral, se repite cada 2 meses)",
                ml.getRegistros().size(), ml.getLineasLeidas(), ml.getTotalErrores(), ml.esValido(), mp != null, limitar(ml.getErrores()), null));

        // Averias
        ArchivoSubido<RegistroAveria> ap = averiasPersonalizadas;
        if (ap != null) {
            lista.add(new ResumenArchivo("averias", ap.nombre(), ap.lectura().getRegistros().size() + " averías programadas (archivo)",
                    ap.lectura().getRegistros().size(), ap.lectura().getLineasLeidas(), ap.lectura().getTotalErrores(),
                    ap.lectura().esValido(), true, limitar(ap.lectura().getErrores()), null));
        } else {
            lista.add(new ResumenArchivo("averias", "averías programadas",
                    "Se generan con la semilla de la corrida (tasa configurable por día)", 0, 0, 0, true, false, List.of(), null));
        }
        return lista;
    }

    /** Avisa si algun dia del periodo de 5 dias no tiene ningun pedido (p. ej. el 30/09 en los datos publicados). */
    private static String advertenciaDiasSinPedidos(Escenario escenario, List<Double> horasDePedidos, double h0, int dias) {
        if (escenario != Escenario.SIMULACION_5D) return null;
        double inicioDia = Math.floor(h0 / 24.0) * 24.0;
        List<String> vacios = new ArrayList<>();
        for (int d = 0; d <= dias; d++) {
            double desde = Math.max(h0, inicioDia + d * 24.0);
            double hasta = Math.min(h0 + dias * 24.0, inicioDia + (d + 1) * 24.0);
            if (hasta <= desde) continue;
            boolean hay = false;
            for (double h : horasDePedidos) if (h >= desde && h < hasta) { hay = true; break; }
            if (!hay) vacios.add(ConversorTiempo.formatear(desde).substring(0, 5));
        }
        if (vacios.isEmpty()) return null;
        return "Sin pedidos el " + String.join(", ", vacios) + ". Suba el archivo de ventas de ese mes si esos días deben tener pedidos.";
    }

    private static List<ResultadoLectura.ErrorLinea> limitar(List<ResultadoLectura.ErrorLinea> errores) {
        return errores.size() <= 10 ? errores : new ArrayList<>(errores.subList(0, 10));
    }

    public List<YearMonth> getMesesConVentas() { return new ArrayList<>(mesesConVentas); }

    private static Optional<YearMonth> mesDeNombreVentas(String nombre) {
        if (nombre == null) return Optional.empty();
        Matcher m = NOMBRE_VENTAS.matcher(nombre);
        if (!m.find()) return Optional.empty();
        try {
            return Optional.of(YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<YearMonth> mesDeNombreBloqueos(String nombre) {
        if (nombre == null) return Optional.empty();
        Matcher largo = NOMBRE_BLOQUEOS_LARGO.matcher(nombre);
        try {
            if (largo.find()) return Optional.of(YearMonth.of(Integer.parseInt(largo.group(1)), Integer.parseInt(largo.group(2))));
            Matcher m = NOMBRE_BLOQUEOS.matcher(nombre);
            if (m.find()) return Optional.of(YearMonth.of(2000 + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
