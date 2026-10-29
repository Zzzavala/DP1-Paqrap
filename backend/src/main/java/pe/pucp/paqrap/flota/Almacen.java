package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.Coordenada;

/**
 * Almacen (diagrama de clases, Modulo D): «abstract», con ubicacion e inventario.
 */
public abstract class Almacen {
    private final String idAlmacen;
    private final String nombre;
    private final Coordenada ubicacion;
    protected int inventarioActual;

    protected Almacen(String idAlmacen, String nombre, Coordenada ubicacion, int inventarioActual) {
        this.idAlmacen = idAlmacen;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.inventarioActual = inventarioActual;
    }

    /** +despachar(cantidad): boolean (diagrama). */
    public abstract boolean despachar(int cantidad);

    /** +tieneStock(cantidad): boolean (diagrama). */
    public abstract boolean tieneStock(int cantidad);

    /** Devuelve al almacen unidades que regresaron sin entregarse. */
    public abstract void recibir(int cantidad);

    /** Capacidad maxima (Integer.MAX_VALUE si no tiene limite). */
    public abstract int getCapacidad();

    /** true para el almacen central, que es de abastecimiento permanente. */
    public abstract boolean esCentral();

    public String getIdAlmacen() { return idAlmacen; }
    public String getNombre() { return nombre; }
    public Coordenada getUbicacion() { return ubicacion; }
    public int getInventarioActual() { return inventarioActual; }

    @Override
    public String toString() { return nombre; }
}
