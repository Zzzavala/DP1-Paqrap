package pe.pucp.paqrap.simulacion;

/**
 * «enumeration» Escenario (diagrama, Modulo D): tipo de corrida que resuelve
 * el mismo Planificador mediante parametros (B21, RAN-08). Lo unico que cambia
 * entre escenarios es el reloj de la corrida, el origen de los pedidos y la
 * condicion de cierre; el motor de ruteo es el mismo.
 */
public enum Escenario {
    TIEMPO_REAL("Operación día a día (tiempo real)", "operación día a día (tiempo real)"),
    SIMULACION_5D("Simulación del periodo (5 días)", "simulación del periodo (5 días)"),
    COLAPSO_LOGISTICO("Simulación hasta el colapso logístico", "simulación hasta el colapso logístico");

    private final String etiqueta;
    private final String descripcionCorta;

    Escenario(String etiqueta, String descripcionCorta) {
        this.etiqueta = etiqueta;
        this.descripcionCorta = descripcionCorta;
    }

    public String getEtiqueta() { return etiqueta; }
    public String getDescripcionCorta() { return descripcionCorta; }
}
