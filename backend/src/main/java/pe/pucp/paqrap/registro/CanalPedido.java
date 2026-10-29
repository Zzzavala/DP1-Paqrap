package pe.pucp.paqrap.registro;

/** Canal de origen del pedido (A25). */
public enum CanalPedido {
    ARCHIVO_VENTAS("Archivo de ventas"),
    WEB("Web"),
    APLICACION_MOVIL("Aplicación móvil"),
    CALL_CENTER("Call center"),
    CARGA_MASIVA("Carga masiva"),
    MAPA("Inserción en el mapa");

    private final String etiqueta;

    CanalPedido(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() { return etiqueta; }
}
