package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.registro.Pedido;

/**
 * Visita a un cliente dentro de una ruta (diagrama, Modulo B):
 * -secuencia: int, -pedidoAsociado: Pedido, -horaEstimadaLlegada, -tiempoServicio.
 */
public class ParadaRuta {
    private final int secuencia;
    private final Pedido pedidoAsociado;
    private final double horaEstimadaLlegada;
    private final double tiempoServicio; // horas (1h fijo, requisito B7)

    public ParadaRuta(int secuencia, Pedido pedidoAsociado, double horaEstimadaLlegada, double tiempoServicio) {
        this.secuencia = secuencia;
        this.pedidoAsociado = pedidoAsociado;
        this.horaEstimadaLlegada = horaEstimadaLlegada;
        this.tiempoServicio = tiempoServicio;
    }

    public int getSecuencia() { return secuencia; }
    public Pedido getPedidoAsociado() { return pedidoAsociado; }
    public double getHoraEstimadaLlegada() { return horaEstimadaLlegada; }
    public double getTiempoServicio() { return tiempoServicio; }

    @Override
    public String toString() {
        boolean aTiempo = horaEstimadaLlegada <= pedidoAsociado.getHoraLimite();
        return String.format("  %d. %s -> llegada %.1fh (limite %.1fh) %s",
                secuencia, pedidoAsociado, horaEstimadaLlegada, pedidoAsociado.getHoraLimite(),
                aTiempo ? "" : "[FUERA DE PLAZO]");
    }
}
