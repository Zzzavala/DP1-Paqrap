package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.comun.ParametrosOperacion;
import pe.pucp.paqrap.comun.UmbralesSemaforo;
import pe.pucp.paqrap.planificador.Planificador;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Todo lo que determina el resultado de una corrida (pantalla V-1 del
 * prototipo): escenario, algoritmo, semilla, fecha/hora de inicio, umbrales
 * del semaforo y parametros de operacion. Misma semilla + mismos archivos =
 * misma corrida (B21, D23, D24).
 *
 * - SIMULACION_5D: 5 dias simulados, ejecucion acelerada para durar entre 30
 *   y 60 minutos reales (B22, C13). Por defecto 45 minutos.
 * - TIEMPO_REAL: reloj 1:1 desde la fecha/hora de inicio (por defecto, ahora
 *   en Lima), sin limite superior (D12).
 * - COLAPSO_LOGISTICO: reloj acelerado sin limite superior; se detiene solo al
 *   detectar el colapso logistico (C14, D14, D21).
 */
public class ParametrosCorrida {

    public static final LocalDateTime INICIO_POR_DEFECTO_5D = LocalDateTime.of(2026, 9, 1, 0, 0);
    public static final LocalDateTime INICIO_POR_DEFECTO_COLAPSO = LocalDateTime.of(2027, 3, 1, 0, 0);
    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private Escenario escenario = Escenario.SIMULACION_5D;
    private String algoritmo = "GA";
    private long semilla = 20260902L;
    private LocalDateTime fechaInicio;
    private UmbralesSemaforo umbrales = new UmbralesSemaforo();
    private int diasSimulacion = 5;
    private double duracionObjetivoMin = 45.0;
    private double factorAceleracionColapso = 160.0;
    private double ventanaPlanificacionMin = 60.0;
    private int umbralColapsoVencidos = 3;
    private int umbralColapsoBacklog = 15;
    private double averiasPorDia = 0.6;
    private boolean generarAverias = true;
    private Map<String, Object> parametrosAlgoritmo = new HashMap<>();
    private ParametrosOperacion operacion = new ParametrosOperacion();

    /** Completa valores faltantes y corrige los que estan fuera de rango. */
    public void normalizar() {
        if (escenario == null) escenario = Escenario.SIMULACION_5D;
        String alg = Planificador.normalizar(algoritmo);
        algoritmo = alg == null ? "GA" : alg;
        if (fechaInicio == null) fechaInicio = fechaInicioPorDefecto(escenario);
        fechaInicio = fechaInicio.truncatedTo(ChronoUnit.MINUTES);
        if (umbrales == null) umbrales = new UmbralesSemaforo();
        umbrales.normalizar();
        diasSimulacion = Math.max(1, Math.min(30, diasSimulacion));
        // B22: la simulacion del periodo debe durar entre 30 y 60 minutos reales
        duracionObjetivoMin = Math.max(30.0, Math.min(60.0, duracionObjetivoMin));
        factorAceleracionColapso = Math.max(1.0, Math.min(20000.0, factorAceleracionColapso));
        ventanaPlanificacionMin = Math.max(5.0, Math.min(240.0, ventanaPlanificacionMin));
        umbralColapsoVencidos = Math.max(1, umbralColapsoVencidos);
        umbralColapsoBacklog = Math.max(1, umbralColapsoBacklog);
        averiasPorDia = Math.max(0.0, Math.min(20.0, averiasPorDia));
        if (parametrosAlgoritmo == null) parametrosAlgoritmo = new HashMap<>();
        if (operacion == null) operacion = new ParametrosOperacion();
        operacion.normalizar();
    }

    public static LocalDateTime fechaInicioPorDefecto(Escenario escenario) {
        switch (escenario) {
            case TIEMPO_REAL: return LocalDateTime.now(ZONA_LIMA).truncatedTo(ChronoUnit.MINUTES);
            case COLAPSO_LOGISTICO: return INICIO_POR_DEFECTO_COLAPSO;
            default: return INICIO_POR_DEFECTO_5D;
        }
    }

    /** Horas simuladas por hora real con las que avanza el reloj de la corrida. */
    public double factorAceleracion() {
        switch (escenario) {
            case TIEMPO_REAL: return 1.0;
            case COLAPSO_LOGISTICO: return factorAceleracionColapso;
            default: return (diasSimulacion * 24.0) / (duracionObjetivoMin / 60.0);
        }
    }

    public ParametrosCorrida copia() {
        ParametrosCorrida c = new ParametrosCorrida();
        c.escenario = escenario;
        c.algoritmo = algoritmo;
        c.semilla = semilla;
        c.fechaInicio = fechaInicio;
        c.umbrales = umbrales == null ? new UmbralesSemaforo() : umbrales.copia();
        c.diasSimulacion = diasSimulacion;
        c.duracionObjetivoMin = duracionObjetivoMin;
        c.factorAceleracionColapso = factorAceleracionColapso;
        c.ventanaPlanificacionMin = ventanaPlanificacionMin;
        c.umbralColapsoVencidos = umbralColapsoVencidos;
        c.umbralColapsoBacklog = umbralColapsoBacklog;
        c.averiasPorDia = averiasPorDia;
        c.generarAverias = generarAverias;
        c.parametrosAlgoritmo = parametrosAlgoritmo == null ? new HashMap<>() : new HashMap<>(parametrosAlgoritmo);
        c.operacion = operacion == null ? new ParametrosOperacion() : operacion.copia();
        return c;
    }

    public Escenario getEscenario() { return escenario; }
    public void setEscenario(Escenario escenario) { this.escenario = escenario; }
    public String getAlgoritmo() { return algoritmo; }
    public void setAlgoritmo(String algoritmo) { this.algoritmo = algoritmo; }
    public long getSemilla() { return semilla; }
    public void setSemilla(long semilla) { this.semilla = semilla; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }
    public UmbralesSemaforo getUmbrales() { return umbrales; }
    public void setUmbrales(UmbralesSemaforo umbrales) { this.umbrales = umbrales; }
    public int getDiasSimulacion() { return diasSimulacion; }
    public void setDiasSimulacion(int diasSimulacion) { this.diasSimulacion = diasSimulacion; }
    public double getDuracionObjetivoMin() { return duracionObjetivoMin; }
    public void setDuracionObjetivoMin(double duracionObjetivoMin) { this.duracionObjetivoMin = duracionObjetivoMin; }
    public double getFactorAceleracionColapso() { return factorAceleracionColapso; }
    public void setFactorAceleracionColapso(double factorAceleracionColapso) { this.factorAceleracionColapso = factorAceleracionColapso; }
    public double getVentanaPlanificacionMin() { return ventanaPlanificacionMin; }
    public void setVentanaPlanificacionMin(double ventanaPlanificacionMin) { this.ventanaPlanificacionMin = ventanaPlanificacionMin; }
    public int getUmbralColapsoVencidos() { return umbralColapsoVencidos; }
    public void setUmbralColapsoVencidos(int umbralColapsoVencidos) { this.umbralColapsoVencidos = umbralColapsoVencidos; }
    public int getUmbralColapsoBacklog() { return umbralColapsoBacklog; }
    public void setUmbralColapsoBacklog(int umbralColapsoBacklog) { this.umbralColapsoBacklog = umbralColapsoBacklog; }
    public double getAveriasPorDia() { return averiasPorDia; }
    public void setAveriasPorDia(double averiasPorDia) { this.averiasPorDia = averiasPorDia; }
    public boolean isGenerarAverias() { return generarAverias; }
    public void setGenerarAverias(boolean generarAverias) { this.generarAverias = generarAverias; }
    public Map<String, Object> getParametrosAlgoritmo() { return parametrosAlgoritmo; }
    public void setParametrosAlgoritmo(Map<String, Object> parametrosAlgoritmo) { this.parametrosAlgoritmo = parametrosAlgoritmo; }
    public ParametrosOperacion getOperacion() { return operacion; }
    public void setOperacion(ParametrosOperacion operacion) { this.operacion = operacion; }
}
