package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.Coordenada;

/**
 * Falla de una unidad de transporte (diagrama, Modulo D):
 * -vehiculoAfectado: Vehiculo, -causa: String. Se agregan la hora, el tipo y
 * el tiempo de inoperatividad que pide la Lista de Exigencias (D7).
 */
public class Averia {
    private final String idAveria;
    private final Vehiculo vehiculoAfectado;
    private final TipoAveria tipo;
    private final String causa;
    private final double horaInicio;
    private final double horaFinInmovilizacion;
    private final double horaDisponible;
    private final Coordenada ubicacion;
    private final String origen; // PROGRAMADA, ARCHIVO o MANUAL

    public Averia(String idAveria, Vehiculo vehiculoAfectado, TipoAveria tipo, String causa, double horaInicio,
                  double horaFinInmovilizacion, double horaDisponible, Coordenada ubicacion, String origen) {
        this.idAveria = idAveria;
        this.vehiculoAfectado = vehiculoAfectado;
        this.tipo = tipo;
        this.causa = causa;
        this.horaInicio = horaInicio;
        this.horaFinInmovilizacion = horaFinInmovilizacion;
        this.horaDisponible = horaDisponible;
        this.ubicacion = ubicacion;
        this.origen = origen;
    }

    public String getIdAveria() { return idAveria; }
    public Vehiculo getVehiculoAfectado() { return vehiculoAfectado; }
    public TipoAveria getTipo() { return tipo; }
    public String getCausa() { return causa; }
    public double getHoraInicio() { return horaInicio; }
    public double getHoraFinInmovilizacion() { return horaFinInmovilizacion; }
    public double getHoraDisponible() { return horaDisponible; }
    public Coordenada getUbicacion() { return ubicacion; }
    public String getOrigen() { return origen; }
}
