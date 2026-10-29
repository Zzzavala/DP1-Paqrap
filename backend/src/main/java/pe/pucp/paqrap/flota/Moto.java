package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.ParametrosOperacion;

/**
 * Especializacion de Vehiculo (diagrama, Modulo D): capacidad 8 paquetes,
 * S/ 6.00 por km. Velocidad por defecto 25 km/h (enunciado), configurable en
 * los parametros de la corrida (ver ParametrosOperacion).
 */
public class Moto extends Vehiculo {
    public Moto(String idVehiculo, Almacen almacenBase, ParametrosOperacion parametros, JornadaConductor jornada) {
        super(idVehiculo, almacenBase, parametros, jornada);
    }
    @Override public TipoVehiculo getTipo() { return TipoVehiculo.MOTO; }
    @Override public int getCapacidadPaquetes() { return parametros.getCapacidadMoto(); }
    @Override public double getVelocidadPromedioKmH() { return parametros.getVelocidadMoto(); }
    @Override public double getCostoPorKm() { return parametros.getCostoKmMoto(); }
}
