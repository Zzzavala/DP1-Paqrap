package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.flota.Almacen;
import pe.pucp.paqrap.flota.Vehiculo;

import java.util.List;

/**
 * Ruta que una unidad esta ejecutando en la simulacion: sus paradas con hora de
 * llegada y salida, la trayectoria completa en el tiempo (salida del almacen,
 * entregas, pausas de alimentacion y regreso) y el almacen al que vuelve.
 *
 * Cuando la ruta cambia en camino (bloqueo, averia, reasignacion) se cierra la
 * ruta vigente en ese instante y la unidad sigue con una ruta de continuacion
 * que parte desde donde esta.
 */
public final class RutaEnCurso {
    private final String idRuta;
    private final String causa;
    private final Vehiculo vehiculo;
    private final Almacen almacenSalida;
    private final Almacen almacenRetorno;
    private final double horaInicio;
    private final double horaRegreso;
    private final List<ParadaEnCurso> paradas;
    private final Trayectoria trayectoria;
    private final int[] inicioTramo;   // indice de trayectoria donde empieza cada tramo (paradas + regreso)
    private final int[] finTramo;      // indice de trayectoria donde llega al final de cada tramo
    private final List<double[]> pausas;
    private final int cargaInicial;
    private double horaCierre = Double.NaN;

    public RutaEnCurso(String idRuta, String causa, Vehiculo vehiculo, Almacen almacenSalida, Almacen almacenRetorno,
                       double horaInicio, double horaRegreso, List<ParadaEnCurso> paradas, Trayectoria trayectoria,
                       int[] inicioTramo, int[] finTramo, List<double[]> pausas, int cargaInicial) {
        this.idRuta = idRuta;
        this.causa = causa;
        this.vehiculo = vehiculo;
        this.almacenSalida = almacenSalida;
        this.almacenRetorno = almacenRetorno;
        this.horaInicio = horaInicio;
        this.horaRegreso = horaRegreso;
        this.paradas = paradas;
        this.trayectoria = trayectoria;
        this.inicioTramo = inicioTramo;
        this.finTramo = finTramo;
        this.pausas = pausas;
        this.cargaInicial = cargaInicial;
    }

    /** Hora del siguiente hito: llegada a la siguiente parada pendiente o regreso al almacen. */
    public double proximoHito() {
        for (ParadaEnCurso p : paradas) if (p.estaPendiente()) return p.getHoraLlegada();
        return horaRegreso;
    }

    /** Parada pendiente que sigue (null si ya solo falta regresar). */
    public ParadaEnCurso siguienteParada() {
        for (ParadaEnCurso p : paradas) if (p.estaPendiente()) return p;
        return null;
    }

    /** Indice del tramo que la unidad recorre (o en el que esta parada) en el instante t. */
    public int tramoEn(double t) {
        int i = trayectoria.indiceEn(t);
        for (int k = 0; k < finTramo.length; k++) {
            if (i < finTramo[k]) return k;
        }
        return finTramo.length - 1;
    }

    /** Paquetes que la unidad todavia lleva a bordo en el instante t. */
    public int cargaEn(double t) {
        int carga = 0;
        for (ParadaEnCurso p : paradas) {
            if (p.estaRetirada()) continue;
            if (!p.estaEntregada() || p.getHoraLlegada() > t) carga += p.getParte().getCantidadUnidades();
        }
        return carga;
    }

    /** true si en el instante t la unidad esta detenida en una pausa de alimentacion. */
    public boolean estaEnPausa(double t) {
        for (double[] p : pausas) if (t >= p[0] && t < p[1]) return true;
        return false;
    }

    /** true si en el instante t la unidad esta entregando en una parada. */
    public boolean estaEntregando(double t) {
        for (ParadaEnCurso p : paradas) {
            if (!p.estaRetirada() && t >= p.getHoraLlegada() && t < p.getHoraSalida()) return true;
        }
        return false;
    }

    /** true si ya entrego todo y va de regreso al almacen. */
    public boolean estaRetornando(double t) {
        for (ParadaEnCurso p : paradas) if (!p.estaRetirada() && t < p.getHoraSalida()) return false;
        return t < horaRegreso;
    }

    public void cerrar(double hora) { this.horaCierre = hora; }

    public String getIdRuta() { return idRuta; }
    public String getCausa() { return causa; }
    public Vehiculo getVehiculo() { return vehiculo; }
    public Almacen getAlmacenSalida() { return almacenSalida; }
    public Almacen getAlmacenRetorno() { return almacenRetorno; }
    public double getHoraInicio() { return horaInicio; }
    public double getHoraRegreso() { return horaRegreso; }
    public List<ParadaEnCurso> getParadas() { return paradas; }
    public Trayectoria getTrayectoria() { return trayectoria; }
    public int[] getInicioTramo() { return inicioTramo; }
    public int[] getFinTramo() { return finTramo; }
    public List<double[]> getPausas() { return pausas; }
    public int getCargaInicial() { return cargaInicial; }
    public double getHoraCierre() { return horaCierre; }
}
