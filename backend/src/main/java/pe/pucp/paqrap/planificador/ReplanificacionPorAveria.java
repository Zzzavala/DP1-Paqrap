package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.flota.Averia;

import java.util.List;

/**
 * Redistribuye la carga de una unidad averiada (diagrama, Modulo B; B10):
 * -averiaOrigen: Averia. En una averia tipo 1 la unidad recalcula su ruta con
 * el retraso; en tipos 2 y 3 sus pedidos se transbordan: vuelven a planificarse
 * desde los almacenes o se cubren con la carga de otra unidad en camino
 * (ReasignacionCarga).
 */
public class ReplanificacionPorAveria extends EventoReplanificacion {
    private final Averia averiaOrigen;

    public ReplanificacionPorAveria(double fechaHora, Averia averiaOrigen, List<String> vehiculosAfectados,
                                    List<String> pedidosAfectados, String detalle) {
        super(fechaHora, vehiculosAfectados, pedidosAfectados, detalle);
        this.averiaOrigen = averiaOrigen;
    }

    @Override
    public String getCausa() { return "AVERIA"; }

    public Averia getAveriaOrigen() { return averiaOrigen; }
}
