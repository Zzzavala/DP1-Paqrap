package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.Coordenada;

/**
 * Almacen intermedio (diagrama, Modulo D): capacidad maxima de 1,000 unidades
 * (configurable) y recarga instantanea cada 24 horas a las 23:59:59 (B15, B16, D9).
 */
public class AlmacenIntermedio extends Almacen {
    private final int capacidadMaxima;
    private int unidadesDespachadasHoy;

    public AlmacenIntermedio(String idAlmacen, String nombre, Coordenada ubicacion, int capacidadMaxima) {
        super(idAlmacen, nombre, ubicacion, capacidadMaxima);
        this.capacidadMaxima = capacidadMaxima;
    }

    @Override
    public boolean despachar(int cantidad) {
        if (inventarioActual < cantidad) return false;
        inventarioActual -= cantidad;
        unidadesDespachadasHoy += cantidad;
        return true;
    }

    @Override
    public boolean tieneStock(int cantidad) { return inventarioActual >= cantidad; }

    @Override
    public void recibir(int cantidad) { inventarioActual = Math.min(capacidadMaxima, inventarioActual + cantidad); }

    /** +recargar() (diagrama): recarga instantanea a capacidad maxima (cada 24h de simulacion). */
    public void recargar() {
        inventarioActual = capacidadMaxima;
        unidadesDespachadasHoy = 0;
    }

    /** +estaLleno(): boolean (diagrama). */
    public boolean estaLleno() { return inventarioActual >= capacidadMaxima; }

    /** % de stock disponible respecto a su capacidad (semaforizacion del almacen). */
    public double porcentajeStock() { return capacidadMaxima == 0 ? 0 : 100.0 * inventarioActual / capacidadMaxima; }

    @Override public int getCapacidad() { return capacidadMaxima; }
    @Override public boolean esCentral() { return false; }
    public int getUnidadesDespachadasHoy() { return unidadesDespachadasHoy; }
}
