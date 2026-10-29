package pe.pucp.paqrap.registro;

/**
 * Estados del ciclo de vida del pedido (diagrama de clases, Modulo A: «enumeration» EstadoPedido).
 * Se agrega CANCELADO (A7: cancelar un pedido antes de que sea despachado).
 */
public enum EstadoPedido {
    REGISTRADO("Registrado"),
    EN_ALMACEN("En almacén"),
    ASIGNADO_A_RUTA("Asignado a ruta"),
    EN_TRANSITO("En ruta"),
    REASIGNADO("Reasignado"),
    ENTREGADO("Entregado"),
    NO_ENTREGADO_EN_PLAZO("No entregado en plazo"),
    CANCELADO("Cancelado");

    private final String etiqueta;

    EstadoPedido(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() { return etiqueta; }
}
