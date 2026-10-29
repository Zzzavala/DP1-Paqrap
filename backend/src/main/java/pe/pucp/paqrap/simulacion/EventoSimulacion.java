package pe.pucp.paqrap.simulacion;

/**
 * Evento de la bitacora de la corrida (B25, D19, C22): replanificaciones por
 * bloqueo, averias, reasignaciones de carga, reprogramaciones, pedidos y
 * bloqueos manuales, colapso. El visualizador muestra los recientes como
 * alertas y en el historial de cada vehiculo o pedido.
 *
 * Tipos: RECALCULO, AVERIA, REASIGNACION, TRANSBORDO, REPROGRAMACION,
 * BLOQUEO_MANUAL, PEDIDO_MANUAL, CANCELACION, COLAPSO, INFO.
 */
public record EventoSimulacion(long id, double hora, String tipo, String vehiculo, String pedido, String detalle) {
}
