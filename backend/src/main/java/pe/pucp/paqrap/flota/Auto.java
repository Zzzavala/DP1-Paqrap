package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.ParametrosOperacion;

/**
 * Especializacion de Vehiculo (diagrama, Modulo D): capacidad 24 paquetes,
 * S/ 8.00 por km. Velocidad por defecto 40 km/h (enunciado), configurable en
 * los parametros de la corrida (ver ParametrosOperacion).
 */
public class Auto extends Vehiculo {
    public Auto(String idVehiculo, Almacen almacenBase, ParametrosOperacion parametros, JornadaConductor jornada) {
        super(idVehiculo, almacenBase, parametros, jornada);
    }
    @Override public TipoVehiculo getTipo() { return TipoVehiculo.AUTO; }
    @Override public int getCapacidadPaquetes() { return parametros.getCapacidadAuto(); }
    @Override public double getVelocidadPromedioKmH() { return parametros.getVelocidadAuto(); }
    @Override public double getCostoPorKm() { return parametros.getCostoKmAuto(); }
}
