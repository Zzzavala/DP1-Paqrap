package pe.pucp.paqrap.flota;

/**
 * Tipos de averia de una unidad (D7; prototipo v04, seccion 2):
 * - Tipo 1: la unidad queda inmovilizada 2 h en el lugar y luego continua su
 *   ruta con la carga que lleva.
 * - Tipo 2: queda inmovilizada en el lugar (como maximo 4 h), luego va al
 *   taller y no esta disponible hasta el fin del turno siguiente al de la averia.
 *   Su carga se transborda: los pedidos que llevaba se vuelven a planificar.
 * - Tipo 3: queda inmovilizada 4 h en el lugar, luego va al taller y no esta
 *   disponible por lo menos 2 dias; retorna al inicio del turno 15:00-23:00.
 *   Su carga tambien se transborda.
 *
 * Las duraciones son las de la P3 de Preguntas y Respuestas (y su ampliacion):
 * tipo 1 no disponible 2 h; tipo 2 hasta el fin del turno siguiente; tipo 3 al
 * menos 2 dias, volviendo en el turno de 15:00 a 23:00. En los tipos 2 y 3 la
 * unidad queda 4 h en el lugar y luego pasa (de forma instantanea) al almacen
 * central, igual que los paquetes que no se transbordaron. Quedan como reglas
 * de este enum para poder ajustarlas sin tocar la simulacion.
 */
public enum TipoAveria {
    TIPO_1("Tipo 1 · leve (2 h en el lugar)", 2.0, false),
    TIPO_2("Tipo 2 · moderada (taller hasta fin del siguiente turno)", 4.0, true),
    TIPO_3("Tipo 3 · grave (taller mínimo 2 días)", 4.0, true);

    private static final double HORA_RETORNO_TIPO_3 = 15.0;

    private final String etiqueta;
    private final double maximoInmovilizacionH;
    private final boolean requiereTransbordo;

    TipoAveria(String etiqueta, double maximoInmovilizacionH, boolean requiereTransbordo) {
        this.etiqueta = etiqueta;
        this.maximoInmovilizacionH = maximoInmovilizacionH;
        this.requiereTransbordo = requiereTransbordo;
    }

    /** Hora absoluta desde la que la unidad vuelve a estar disponible. */
    public double calcularHoraDisponible(double horaAveria, int[] horasCambioTurno) {
        switch (this) {
            case TIPO_1:
                return horaAveria + maximoInmovilizacionH;
            case TIPO_2: {
                double finTurnoActual = Turno.finDelTurnoVigente(horasCambioTurno, horaAveria);
                return Turno.finDelTurnoVigente(horasCambioTurno, finTurnoActual);
            }
            default: {
                double minimo = horaAveria + 48.0;
                double dia = Math.floor(minimo / 24.0) * 24.0;
                double retorno = dia + HORA_RETORNO_TIPO_3;
                return retorno >= minimo ? retorno : retorno + 24.0;
            }
        }
    }

    /** Horas que la unidad permanece inmovilizada en el lugar de la averia. */
    public double calcularInmovilizacion(double horaAveria, double horaDisponible) {
        return Math.min(maximoInmovilizacionH, Math.max(0.0, horaDisponible - horaAveria));
    }

    public static TipoAveria desdeNumero(int numero) {
        switch (numero) {
            case 1: return TIPO_1;
            case 2: return TIPO_2;
            case 3: return TIPO_3;
            default: return null;
        }
    }

    public int getNumero() { return ordinal() + 1; }
    public String getEtiqueta() { return etiqueta; }
    public boolean requiereTransbordo() { return requiereTransbordo; }
}
