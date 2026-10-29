package pe.pucp.paqrap.planificador;

import java.util.List;

/**
 * «abstract» EventoReplanificacion (diagrama, Modulo B): base comun de los
 * eventos que fuerzan un nuevo plan. -fechaHora, -planAnterior, -planResultante.
 * +registrarEvento().
 *
 * En la simulacion integrada, el replanteo de rutas lo ejecuta el motor de
 * simulacion (recalcula el recorrido de las unidades afectadas y vuelve a
 * planificar los pedidos que quedaron sin unidad). Cada subclase deja la
 * constancia auditable del cambio (B25): cuando paso, por que, que unidades y
 * pedidos afecto y que se hizo.
 */
public abstract class EventoReplanificacion {
    protected final double fechaHora;
    protected final List<String> vehiculosAfectados;
    protected final List<String> pedidosAfectados;
    protected final String detalle;

    protected EventoReplanificacion(double fechaHora, List<String> vehiculosAfectados, List<String> pedidosAfectados,
                                    String detalle) {
        this.fechaHora = fechaHora;
        this.vehiculosAfectados = List.copyOf(vehiculosAfectados);
        this.pedidosAfectados = List.copyOf(pedidosAfectados);
        this.detalle = detalle;
    }

    /** Causa del cambio de plan: BLOQUEO, AVERIA o REASIGNACION. */
    public abstract String getCausa();

    /** +registrarEvento() (diagrama): texto de la constancia del cambio. */
    public String registrarEvento() {
        return String.format("[%s] %s (unidades: %s; pedidos: %s)", getCausa(), detalle,
                vehiculosAfectados.isEmpty() ? "-" : String.join(", ", vehiculosAfectados),
                pedidosAfectados.isEmpty() ? "-" : String.join(", ", pedidosAfectados));
    }

    public double getFechaHora() { return fechaHora; }
    public List<String> getVehiculosAfectados() { return vehiculosAfectados; }
    public List<String> getPedidosAfectados() { return pedidosAfectados; }
    public String getDetalle() { return detalle; }
}
