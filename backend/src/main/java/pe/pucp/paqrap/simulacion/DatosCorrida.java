package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.flota.LectorAverias.RegistroAveria;
import pe.pucp.paqrap.flota.LectorBloqueos.RegistroBloqueo;
import pe.pucp.paqrap.flota.PlanMantenimiento;
import pe.pucp.paqrap.registro.LectorVentas.RegistroVenta;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Archivos de entrada que usa una corrida (pantalla V-1, "Archivos de
 * entrada"): ventas y bloqueos por mes, plan de mantenimiento y, si el usuario
 * lo subio, el archivo de averias programadas. Se fija al iniciar la corrida:
 * subir otro archivo despues solo afecta a la corrida siguiente.
 */
public final class DatosCorrida {

    private final Function<YearMonth, List<RegistroVenta>> ventas;
    private final Function<YearMonth, List<RegistroBloqueo>> bloqueos;
    private final PlanMantenimiento mantenimiento;
    private final List<RegistroAveria> averias;
    private final YearMonth ultimoMesConVentas;
    private final Map<String, String> descripcionFuentes;

    public DatosCorrida(Function<YearMonth, List<RegistroVenta>> ventas, Function<YearMonth, List<RegistroBloqueo>> bloqueos,
                       PlanMantenimiento mantenimiento, List<RegistroAveria> averias, YearMonth ultimoMesConVentas,
                       Map<String, String> descripcionFuentes) {
        this.ventas = ventas;
        this.bloqueos = bloqueos;
        this.mantenimiento = mantenimiento;
        this.averias = averias;
        this.ultimoMesConVentas = ultimoMesConVentas;
        this.descripcionFuentes = descripcionFuentes;
    }

    public List<RegistroVenta> ventas(YearMonth mes) { return ventas.apply(mes); }
    public List<RegistroBloqueo> bloqueos(YearMonth mes) { return bloqueos.apply(mes); }
    public PlanMantenimiento getMantenimiento() { return mantenimiento; }
    /** Averias del archivo subido por el usuario, o null si se generan con la semilla. */
    public List<RegistroAveria> getAverias() { return averias; }
    public YearMonth getUltimoMesConVentas() { return ultimoMesConVentas; }
    public Map<String, String> getDescripcionFuentes() { return descripcionFuentes; }
}
