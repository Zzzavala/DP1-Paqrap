package pe.pucp.paqrap.registro;

import pe.pucp.paqrap.comun.ParametrosOperacion;

/**
 * Modalidad de entrega comprometida (diagrama de clases, Modulo A: «enumeration» TipoEntrega).
 * Los nombres de las constantes son exactamente los del diagrama. El plazo por
 * defecto es el del enunciado; el plazo efectivo de una corrida se toma de
 * ParametrosOperacion (D10) y, para los pedidos del archivo de ventas, del
 * propio registro (campo plazo en horas).
 */
public enum TipoEntrega {
    REGULAR_36H(36, "Regular"),
    PRIORIZADA_4H(4, "Priorizada · 4 h"),
    PRIORIZADA_8H(8, "Priorizada · 8 h"),
    PRIORIZADA_12H(12, "Priorizada · 12 h"),
    PRIORIZADA_18H(18, "Priorizada · 18 h");

    public final int plazoHoras;
    private final String etiqueta;

    TipoEntrega(int plazoHoras, String etiqueta) {
        this.plazoHoras = plazoHoras;
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() { return etiqueta; }

    /** Plazo de este tipo en la corrida (D10: configurable por parametro). */
    public int plazoEn(ParametrosOperacion parametros) {
        switch (this) {
            case PRIORIZADA_4H: return parametros.getPlazoPriorizada4H();
            case PRIORIZADA_8H: return parametros.getPlazoPriorizada8H();
            case PRIORIZADA_12H: return parametros.getPlazoPriorizada12H();
            case PRIORIZADA_18H: return parametros.getPlazoPriorizada18H();
            default: return parametros.getPlazoRegularH();
        }
    }

    /** Tipo que corresponde al plazo en horas del archivo de ventas (4, 8, 12, 18 o 36); null si no existe. */
    public static TipoEntrega desdePlazo(int horas) {
        switch (horas) {
            case 4: return PRIORIZADA_4H;
            case 8: return PRIORIZADA_8H;
            case 12: return PRIORIZADA_12H;
            case 18: return PRIORIZADA_18H;
            case 36: return REGULAR_36H;
            default: return null;
        }
    }
}
