package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.registro.Pedido;

/**
 * Parada de una ruta que la unidad esta ejecutando: el pedido (o parte) que
 * entrega, cuando llega y cuando termina de entregar. "retirada" marca una
 * parada que se quito de la ruta en camino (transbordo o reasignacion de carga).
 */
public final class ParadaEnCurso {
    private final Pedido parte;
    private final double horaLlegada;
    private final double horaSalida;
    private boolean entregada;
    private boolean retirada;

    public ParadaEnCurso(Pedido parte, double horaLlegada, double horaSalida) {
        this.parte = parte;
        this.horaLlegada = horaLlegada;
        this.horaSalida = horaSalida;
    }

    public boolean estaPendiente() { return !entregada && !retirada; }
    public void marcarEntregada() { this.entregada = true; }
    public void marcarRetirada() { this.retirada = true; }

    public Pedido getParte() { return parte; }
    public double getHoraLlegada() { return horaLlegada; }
    public double getHoraSalida() { return horaSalida; }
    public boolean estaEntregada() { return entregada; }
    public boolean estaRetirada() { return retirada; }
}
