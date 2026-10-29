package pe.pucp.paqrap.simulacion;

/**
 * Momento y condiciones en que se produce el colapso logistico (D21, B24):
 * hora simulada, motivo, cumplimiento en ese instante, pedidos que ya no
 * cumplen su plazo y pedidos que no pueden esperar y no tienen unidad.
 */
public record InfoColapso(double hora, String fecha, String motivo, double cumplimiento, int pedidosIncumplidos,
                          int pedidosSinUnidad, int pedidosRegistrados, int pedidosEntregados, double diasSimulados) {
}
