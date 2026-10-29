package pe.pucp.paqrap.simulacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.pucp.paqrap.visualizador.EstadoVisual;

import java.util.function.BiConsumer;

/**
 * Corrida visual en marcha: un hilo que mueve el reloj de la simulacion al
 * ritmo del escenario y publica el estado para el visualizador.
 *
 * - Simulacion del periodo: 5 dias simulados en la duracion objetivo (30-60
 *   min reales, B22).
 * - Dia a dia: el reloj avanza 1:1 con el reloj real (D12).
 * - Colapso: reloj acelerado (factor configurable) sin limite; termina solo al
 *   detectar el colapso (C14, D14).
 * El reloj no es manipulable por el usuario (prototipo v04): solo se puede
 * iniciar o detener la corrida.
 */
public final class Corrida implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(Corrida.class);
    private static final long PASO_MS = 100;
    private static final long PUBLICACION_MS = 250;

    private final MotorSimulacion motor;
    private final double factor;
    private final BiConsumer<Corrida, CierreCorrida> alTerminar;
    private volatile boolean detenerSolicitado;
    private volatile String estado = "EN_CURSO";
    private volatile EstadoVisual ultimoEstado;
    private volatile CierreCorrida cierre;
    private volatile String error;
    private final long inicioRealMs;
    private volatile long finRealMs;
    private long ultimaPublicacion;

    public Corrida(MotorSimulacion motor, BiConsumer<Corrida, CierreCorrida> alTerminar) {
        this.motor = motor;
        this.factor = motor.getParametros().factorAceleracion();
        this.alTerminar = alTerminar;
        this.inicioRealMs = System.currentTimeMillis();
        publicar(true);
    }

    @Override
    public void run() {
        long t0 = System.nanoTime();
        double horaInicio = motor.getHoraInicio();
        try {
            while (!detenerSolicitado) {
                long inicioPaso = System.nanoTime();
                double transcurridoH = (inicioPaso - t0) / 3.6e12;
                motor.avanzarHasta(horaInicio + transcurridoH * factor);
                publicar(false);
                if (motor.estaFinalizado()) break;
                long dormir = PASO_MS - (System.nanoTime() - inicioPaso) / 1_000_000L;
                if (dormir > 0) Thread.sleep(dormir);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            LOG.error("La corrida {} se detuvo por un error", motor.getIdCorrida(), e);
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        terminar();
    }

    private void terminar() {
        if (!motor.estaFinalizado()) motor.finalizar(error != null ? "ERROR" : "DETENIDA_MANUAL");
        String motivo = motor.getMotivoFin();
        estado = "DETENIDA_MANUAL".equals(motivo) || "ERROR".equals(motivo) ? "DETENIDA" : "FINALIZADA";
        finRealMs = System.currentTimeMillis();
        publicar(true);
        try {
            cierre = motor.generarCierre(estado, inicioRealMs, finRealMs);
        } catch (RuntimeException e) {
            LOG.error("No se pudo generar el cierre de la corrida {}", motor.getIdCorrida(), e);
        }
        if (alTerminar != null && cierre != null) alTerminar.accept(this, cierre);
    }

    private void publicar(boolean forzar) {
        long ahora = System.currentTimeMillis();
        if (!forzar && ahora - ultimaPublicacion < PUBLICACION_MS) return;
        ultimaPublicacion = ahora;
        boolean enCurso = "EN_CURSO".equals(estado) && !motor.estaFinalizado();
        long realSeg = ((finRealMs > 0 ? finRealMs : ahora) - inicioRealMs) / 1000;
        EstadoVisual ev = motor.construirEstadoVisual(enCurso ? "EN_CURSO" : estado, enCurso ? factor : 0, realSeg);
        if (error != null) {
            ev = new EstadoVisual(ev.version(), ev.idCorrida(), ev.estadoCorrida(), ev.escenario(), ev.escenarioEtiqueta(),
                    ev.algoritmo(), ev.semilla(), ev.inicioMs(), ev.minutoActual(), ev.minutoFin(), ev.factor(), ev.tiempoRealSeg(),
                    ev.diaActual(), ev.totalDias(), ev.umbralVerde(), ev.umbralAmbar(), ev.kpis(), ev.almacenes(), ev.vehiculos(),
                    ev.pedidos(), ev.bloqueos(), ev.tramosManuales(), ev.alertas(), ev.colapso(), "La corrida se detuvo por un error: " + error);
        }
        ultimoEstado = ev;
    }

    /** Fuerza una nueva publicacion (despues de un comando del usuario). */
    public void refrescar() {
        publicar(true);
    }

    public void solicitarDetencion() { detenerSolicitado = true; }
    public boolean estaEnCurso() { return "EN_CURSO".equals(estado); }
    public MotorSimulacion getMotor() { return motor; }
    public EstadoVisual getUltimoEstado() { return ultimoEstado; }
    public CierreCorrida getCierre() { return cierre; }
    public String getEstado() { return estado; }
    public long getInicioRealMs() { return inicioRealMs; }
    public long getFinRealMs() { return finRealMs; }
    public String getError() { return error; }
}
