package pe.pucp.paqrap.flota;

import java.util.ArrayList;
import java.util.List;

/**
 * Turno de trabajo del conductor (diagrama, Modulo D): jornadas de 8 horas
 * con cambio a las 07:00, 15:00 y 23:00 (enunciado; configurable, D11).
 *
 * El turno es del conductor, no de la unidad: la unidad sigue operando y el
 * conductor entrante la alcanza donde este (P12). El planificador usa los
 * turnos solo para ubicar la hora de alimentacion, ver JornadaConductor.
 */
public class Turno {

    private final String idTurno;
    private final double horaInicio;
    private final double horaFin;

    public Turno(String idTurno, double horaInicio, double horaFin) {
        this.idTurno = idTurno;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    /** Los tres turnos de la jornada a partir de las horas de cambio de turno. */
    public static List<Turno> desdeCambios(int[] horasCambio) {
        List<Turno> turnos = new ArrayList<>();
        String[] nombres = {"T1", "T2", "T3"};
        for (int i = 0; i < horasCambio.length; i++) {
            int inicio = horasCambio[i];
            int fin = horasCambio[(i + 1) % horasCambio.length];
            turnos.add(new Turno(i < nombres.length ? nombres[i] : "T" + (i + 1), inicio, fin));
        }
        return turnos;
    }

    /** true si la hora dada (modulo 24h) cae dentro de este turno. */
    public boolean estaVigente(double horaDelDia) {
        double h = horaDelDia % 24.0;
        if (h < 0) h += 24.0;
        if (horaInicio <= horaFin) return h >= horaInicio && h < horaFin;
        return h >= horaInicio || h < horaFin; // turno que cruza medianoche
    }

    /** Hora absoluta en que termina el turno vigente en la hora absoluta t. */
    public static double finDelTurnoVigente(int[] horasCambio, double t) {
        double inicioDia = Math.floor(t / 24.0) * 24.0;
        double mejor = Double.POSITIVE_INFINITY;
        for (int d = 0; d <= 1; d++) {
            for (int h : horasCambio) {
                double cambio = inicioDia + d * 24.0 + h;
                if (cambio > t + 1e-9 && cambio < mejor) mejor = cambio;
            }
        }
        return mejor;
    }

    public String getIdTurno() { return idTurno; }
    public double getHoraInicio() { return horaInicio; }
    public double getHoraFin() { return horaFin; }

    @Override
    public String toString() { return idTurno; }
}
