package pe.pucp.paqrap.flota;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Jornada de los conductores y hora de alimentacion (D4, D5, B13, B14).
 *
 * - Turnos de 8 horas con cambio a las 07:00, 15:00 y 23:00 (enunciado;
 *   configurables, D11).
 * - La unidad de transporte no se detiene en el cambio de turno: el conductor
 *   entrante la alcanza donde este y el cambio toma un tiempo despreciable
 *   (P12 de Preguntas y Respuestas). Por eso las unidades operan las 24 horas.
 * - Cada conductor tiene 1 hora de alimentacion dentro de su jornada, al menos
 *   una hora despues del cambio de turno y al menos una hora antes del
 *   siguiente (enunciado). La hora se escalona por el correlativo de la
 *   unidad para que no paren todas a la vez: la unidad NN empieza a comer
 *   1 + ((NN - 1) mod 6) horas despues del inicio de cada turno. Por ejemplo,
 *   TA01 come de 08:00 a 09:00, de 16:00 a 17:00 y de 00:00 a 01:00.
 * - Si la hora de alimentacion llega mientras la unidad viaja o entrega, la
 *   unidad se detiene 1 hora ahi mismo y luego sigue. Si esta en el almacen
 *   sin trabajo, la pausa no afecta nada.
 *
 * Las horas son absolutas (ver ConversorTiempo), y la EPOCA cae a las 00:00,
 * asi que Math.floor(t / 24) sigue marcando el inicio de cada dia.
 *
 * En la version por linea de comandos esta clase era estatica; en el servidor
 * cada corrida tiene su instancia porque los cambios de turno son parametros.
 */
public final class JornadaConductor {

    private final int[] inicioTurnos;
    private final double duracionAlimentacionH;

    public JornadaConductor(int[] inicioTurnos, double duracionAlimentacionH) {
        this.inicioTurnos = Arrays.copyOf(inicioTurnos, inicioTurnos.length);
        Arrays.sort(this.inicioTurnos);
        this.duracionAlimentacionH = duracionAlimentacionH;
    }

    /** Horas despues del inicio de cada turno en que empieza la alimentacion de la unidad (1 a 6). */
    public static double desfaseAlimentacion(int correlativo) {
        return 1 + Math.floorMod(correlativo - 1, 6);
    }

    /** Inicio de la primera pausa de alimentacion que todavia no termino en el instante t. */
    public double proximaPausa(int correlativo, double t) {
        double inicioDia = Math.floor(t / 24.0) * 24.0;
        double desfase = desfaseAlimentacion(correlativo);
        double mejor = Double.POSITIVE_INFINITY;
        for (int d = -1; d <= 1; d++) {
            for (int inicioTurno : inicioTurnos) {
                double pausa = inicioDia + d * 24.0 + inicioTurno + desfase;
                if (pausa + duracionAlimentacionH > t && pausa < mejor) mejor = pausa;
            }
        }
        return mejor;
    }

    /** true si en el instante t el conductor de la unidad esta en su hora de alimentacion. */
    public boolean estaEnPausa(int correlativo, double t) {
        if (duracionAlimentacionH <= 0) return false;
        double pausa = proximaPausa(correlativo, t);
        return t >= pausa && t < pausa + duracionAlimentacionH;
    }

    /**
     * Hora en que termina un trabajo de duracionHoras (viaje o entrega) que la
     * unidad empieza en inicio, sumando la pausa de alimentacion que caiga en el camino.
     */
    public double avanzar(int correlativo, double inicio, double duracionHoras) {
        if (duracionAlimentacionH <= 0) return inicio + duracionHoras;
        double t = inicio;
        double restante = duracionHoras;
        while (true) {
            double pausa = proximaPausa(correlativo, t);
            if (t >= pausa) {
                t = pausa + duracionAlimentacionH;   // esta comiendo: espera a que termine
                continue;
            }
            if (t + restante <= pausa) return t + restante;
            restante -= pausa - t;
            t = pausa + duracionAlimentacionH;
        }
    }

    /** Horas del dia (0-24) en que empieza la alimentacion de la unidad, una por turno. */
    public List<Double> horasAlimentacion(int correlativo) {
        List<Double> horas = new ArrayList<>();
        for (int inicioTurno : inicioTurnos) horas.add((inicioTurno + desfaseAlimentacion(correlativo)) % 24.0);
        horas.sort(Double::compare);
        return horas;
    }

    /** Inicios de las pausas de alimentacion de la unidad que ya empezaron entre desde y hasta (inclusive), en orden. */
    public List<Double> pausasOcurridas(int correlativo, double desde, double hasta) {
        List<Double> pausas = new ArrayList<>();
        if (duracionAlimentacionH <= 0) return pausas;
        double desfase = desfaseAlimentacion(correlativo);
        double inicioDia = Math.floor(hasta / 24.0) * 24.0;
        for (int d = -2; d <= 0; d++) {
            for (int inicioTurno : inicioTurnos) {
                double pausa = inicioDia + d * 24.0 + inicioTurno + desfase;
                if (pausa >= desde && pausa <= hasta) pausas.add(pausa);
            }
        }
        pausas.sort(Double::compare);
        return pausas;
    }

    public int[] getInicioTurnos() { return Arrays.copyOf(inicioTurnos, inicioTurnos.length); }
    public double getDuracionAlimentacionH() { return duracionAlimentacionH; }
}
