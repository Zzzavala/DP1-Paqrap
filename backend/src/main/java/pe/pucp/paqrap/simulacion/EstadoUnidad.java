package pe.pucp.paqrap.simulacion;

/** Estado de una unidad en la simulacion, tal como lo muestra el visualizador. */
public enum EstadoUnidad {
    EN_ALMACEN("En almacén"),
    EN_RUTA("En ruta"),
    ENTREGANDO("Entregando"),
    ALIMENTACION("En alimentación"),
    RETORNANDO("Retornando"),
    REUBICANDO("Reubicándose"),
    AVERIADA("Averiada"),
    EN_TALLER("En taller"),
    MANTENIMIENTO("Mantenimiento preventivo");

    private final String etiqueta;

    EstadoUnidad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() { return etiqueta; }
}
