package pe.pucp.paqrap.flota;

/** Tipo de unidad de transporte y prefijo de su codigo TTNN (P18): TA, TM, TB. */
public enum TipoVehiculo {
    AUTO("TA", "Auto"),
    MOTO("TM", "Moto"),
    BICICLETA("TB", "Bicicleta");

    private final String prefijo;
    private final String etiqueta;

    TipoVehiculo(String prefijo, String etiqueta) {
        this.prefijo = prefijo;
        this.etiqueta = etiqueta;
    }

    public String getPrefijo() { return prefijo; }
    public String getEtiqueta() { return etiqueta; }
}
