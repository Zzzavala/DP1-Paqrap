package pe.pucp.paqrap.planificador;

import java.util.List;

/**
 * Traslada productos en transito de un cliente a otro, priorizando el de menor
 * holgura (diagrama, Modulo B; B11): -pedidoOrigenTransito,
 * -pedidoDestinoCritico, -motivo, -fechaHora. +ejecutar(),
 * +dejarConstanciaAuditable().
 *
 * Como el producto P es unico, una unidad en camino puede entregar al cliente
 * mas critico (por ejemplo, uno que quedo sin unidad por una averia) los
 * paquetes que llevaba para un cliente con mas holgura; ese segundo pedido se
 * vuelve a planificar desde un almacen dentro de su propio plazo. El motor de
 * simulacion ejecuta el cambio y esta clase deja la constancia auditable.
 */
public class ReasignacionCarga extends EventoReplanificacion {
    private final String pedidoOrigenTransito;
    private final String pedidoDestinoCritico;
    private final String motivo;

    public ReasignacionCarga(double fechaHora, String pedidoOrigenTransito, String pedidoDestinoCritico,
                             String vehiculo, String motivo, String detalle) {
        super(fechaHora, List.of(vehiculo), List.of(pedidoOrigenTransito, pedidoDestinoCritico), detalle);
        this.pedidoOrigenTransito = pedidoOrigenTransito;
        this.pedidoDestinoCritico = pedidoDestinoCritico;
        this.motivo = motivo;
    }

    @Override
    public String getCausa() { return "REASIGNACION"; }

    /** +dejarConstanciaAuditable() (diagrama). */
    public String dejarConstanciaAuditable() {
        return String.format("Reasignacion de carga: %s -> %s (motivo: %s)", pedidoOrigenTransito, pedidoDestinoCritico, motivo);
    }

    public String getPedidoOrigenTransito() { return pedidoOrigenTransito; }
    public String getPedidoDestinoCritico() { return pedidoDestinoCritico; }
    public String getMotivo() { return motivo; }
}
