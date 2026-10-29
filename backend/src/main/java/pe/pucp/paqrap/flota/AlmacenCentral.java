package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.Coordenada;

/**
 * Almacen central (diagrama, Modulo D): abastecimiento permanente, se considera
 * de inventario infinito (enunciado, D8). Todas las unidades salen de aqui al
 * inicio de cualquier escenario (P10).
 */
public class AlmacenCentral extends Almacen {

    public AlmacenCentral(String idAlmacen, String nombre, Coordenada ubicacion) {
        super(idAlmacen, nombre, ubicacion, Integer.MAX_VALUE);
    }

    @Override public boolean despachar(int cantidad) { return true; } // inventario infinito
    @Override public boolean tieneStock(int cantidad) { return true; }
    @Override public void recibir(int cantidad) { }
    @Override public int getCapacidad() { return Integer.MAX_VALUE; }
    @Override public boolean esCentral() { return true; }
}
