package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.flota.Averia;
import pe.pucp.paqrap.flota.Vehiculo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Estado de una unidad durante la simulacion: la ruta que ejecuta, la averia
 * que la tiene detenida y sus acumulados (rutas, entregas, km, costo) para los
 * reportes de utilizacion de flota (D17, D18).
 */
public final class VehiculoSim {

    /** Evento del historial de la unidad que muestra el detalle del visualizador. */
    public record EventoUnidad(double hora, String tipo, String detalle) {
    }

    private static final int MAX_EVENTOS = 40;

    private final Vehiculo vehiculo;
    private RutaEnCurso ruta;
    private Averia averia;
    private Coordenada posicionAveria;
    private final Deque<EventoUnidad> eventos = new ArrayDeque<>();

    private int rutasRealizadas;
    private int paradasEntregadas;
    private int unidadesEntregadas;
    private double kmRecorridos;
    private double costo;
    private double horasEnRuta;
    private double sumaFactorCarga;

    public VehiculoSim(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    /** Hora del siguiente evento propio de la unidad (parada, regreso, fin de averia). */
    public double proximoHito(double ahora) {
        double t = Double.POSITIVE_INFINITY;
        if (ruta != null) t = ruta.proximoHito();
        if (averia != null && ruta == null) {
            if (ahora < averia.getHoraFinInmovilizacion()) t = Math.min(t, averia.getHoraFinInmovilizacion());
            else t = Math.min(t, averia.getHoraDisponible());
        }
        return t;
    }

    /** Estado de la unidad en el instante t. */
    public EstadoUnidad estadoEn(double t) {
        if (averia != null && t >= averia.getHoraInicio()) {
            if (t < averia.getHoraFinInmovilizacion()) return EstadoUnidad.AVERIADA;
            if (ruta == null && t < averia.getHoraDisponible()) return EstadoUnidad.EN_TALLER;
        }
        if (ruta == null) {
            return vehiculo.estaEnMantenimiento(t) ? EstadoUnidad.MANTENIMIENTO : EstadoUnidad.EN_ALMACEN;
        }
        if (ruta.estaEnPausa(t)) return EstadoUnidad.ALIMENTACION;
        if (ruta.getParadas().isEmpty()) {
            return "RETORNO_MANTENIMIENTO".equals(ruta.getCausa()) ? EstadoUnidad.RETORNANDO : EstadoUnidad.REUBICANDO;
        }
        if (ruta.estaEntregando(t)) return EstadoUnidad.ENTREGANDO;
        if (ruta.estaRetornando(t)) return EstadoUnidad.RETORNANDO;
        return EstadoUnidad.EN_RUTA;
    }

    /** Posicion {x, y} en el instante t. */
    public double[] posicionEn(double t) {
        if (ruta != null) return ruta.getTrayectoria().posicionEn(t);
        if (averia != null && posicionAveria != null && t < averia.getHoraFinInmovilizacion()) {
            return new double[]{posicionAveria.x(), posicionAveria.y()};
        }
        Coordenada base = vehiculo.getAlmacenOrigen();
        return new double[]{base.x(), base.y()};
    }

    public void registrarEvento(double hora, String tipo, String detalle) {
        eventos.addLast(new EventoUnidad(hora, tipo, detalle));
        while (eventos.size() > MAX_EVENTOS) eventos.removeFirst();
    }

    /** Acumula lo recorrido por una ruta terminada o cerrada en camino. */
    public void acumularRuta(double km, double costoRuta, double horas, double factorCarga, boolean cuentaComoRuta) {
        kmRecorridos += km;
        costo += costoRuta;
        horasEnRuta += horas;
        if (cuentaComoRuta) {
            rutasRealizadas++;
            sumaFactorCarga += factorCarga;
        }
    }

    public void registrarEntrega(int unidades) {
        paradasEntregadas++;
        unidadesEntregadas += unidades;
    }

    public Vehiculo getVehiculo() { return vehiculo; }
    public String getId() { return vehiculo.getIdVehiculo(); }
    public RutaEnCurso getRuta() { return ruta; }
    public void setRuta(RutaEnCurso ruta) { this.ruta = ruta; }
    public Averia getAveria() { return averia; }
    public void setAveria(Averia averia, Coordenada posicion) {
        this.averia = averia;
        this.posicionAveria = posicion;
    }
    public void limpiarAveria() {
        this.averia = null;
        this.posicionAveria = null;
    }
    public List<EventoUnidad> getEventos() { return new ArrayList<>(eventos); }
    public int getRutasRealizadas() { return rutasRealizadas; }
    public int getParadasEntregadas() { return paradasEntregadas; }
    public int getUnidadesEntregadas() { return unidadesEntregadas; }
    public double getKmRecorridos() { return kmRecorridos; }
    public double getCosto() { return costo; }
    public double getHorasEnRuta() { return horasEnRuta; }
    public double getFactorCargaPromedio() { return rutasRealizadas == 0 ? 0 : sumaFactorCarga / rutasRealizadas; }
}
