package pe.pucp.paqrap.comun;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Reloj comun de todo el sistema.
 *
 * Todas las horas que usan el Planificador y la simulacion son "horas
 * absolutas": horas (con decimales) transcurridas desde la EPOCA (01-ene-2026
 * a las 00:00). Asi una hora del archivo de ventas de cualquier mes, una hora
 * de bloqueo y una hora de mantenimiento se comparan directamente, y las
 * operaciones de la jornada (Math.floor(hora / 24)) siguen cayendo a las 00:00.
 *
 * Para el visualizador, una hora absoluta se expresa tambien como "epoch ms"
 * de la fecha local interpretada en UTC (solo una escala numerica comun; el
 * frontend la formatea siempre en UTC para no desplazarla).
 */
public final class ConversorTiempo {

    public static final LocalDateTime EPOCA = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final long EPOCA_MS = EPOCA.toInstant(ZoneOffset.UTC).toEpochMilli();
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "PE"));

    private ConversorTiempo() {
    }

    /** Hora absoluta de una fecha. */
    public static double aHoras(LocalDateTime fecha) {
        return Duration.between(EPOCA, fecha).toMillis() / 3_600_000.0;
    }

    /** Hora absoluta del dia 1 a las 00:00 de un mes. */
    public static double inicioDeMes(YearMonth mes) {
        return aHoras(mes.atDay(1).atStartOfDay());
    }

    /** Fecha correspondiente a una hora absoluta (redondeada al segundo). */
    public static LocalDateTime aFecha(double horas) {
        long segundos = Math.round(horas * 3600.0);
        return EPOCA.plusSeconds(segundos);
    }

    /** Mes que contiene la hora absoluta. */
    public static YearMonth mesDe(double horas) {
        return YearMonth.from(aFecha(horas));
    }

    /** Escala numerica para el frontend: epoch ms de la fecha local tomada como UTC. */
    public static long aEpochMs(double horas) {
        return EPOCA_MS + Math.round(horas * 3_600_000.0);
    }

    /** Hora del dia (0 a 24) de una hora absoluta. */
    public static double horaDelDia(double horas) {
        double h = horas % 24.0;
        return h < 0 ? h + 24.0 : h;
    }

    /** Inicio (00:00) del dia que contiene la hora absoluta. */
    public static double inicioDelDia(double horas) {
        return Math.floor(horas / 24.0) * 24.0;
    }

    public static String formatear(double horas) {
        return aFecha(horas).format(FORMATO);
    }
}
