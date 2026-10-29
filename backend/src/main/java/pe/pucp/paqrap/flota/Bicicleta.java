package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.ParametrosOperacion;

/**
 * Especializacion de Vehiculo (diagrama, Modulo D): capacidad 4 paquetes,
 * S/ 3.00 por km. Velocidad por defecto 12 km/h (enunciado), configurable en
 * los parametros de la corrida (ver ParametrosOperacion).
 */
public class Bicicleta extends Vehiculo {
    public Bicicleta(String idVehiculo, Almacen almacenBase, ParametrosOperacion parametros, JornadaConductor jornada) {
        super(idVehiculo, almacenBase, parametros, jornada);
    }
    @Override public TipoVehiculo getTipo() { return TipoVehiculo.BICICLETA; }
    @Override public int getCapacidadPaquetes() { return parametros.getCapacidadBicicleta(); }
    @Override public double getVelocidadPromedioKmH() { return parametros.getVelocidadBicicleta(); }
    @Override public double getCostoPorKm() { return parametros.getCostoKmBicicleta(); }
}
