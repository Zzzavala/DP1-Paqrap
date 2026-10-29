package pe.pucp.paqrap.simulacion;

import java.util.List;
import java.util.Map;

/**
 * Cierre de una corrida (pantalla V-3 del prototipo) y reportes del Modulo D:
 * cumplimiento de plazos (D15), costo total (D16), kilometraje por tipo de
 * vehiculo (D17), utilizacion de la flota (D18), incidencias (D19), momento del
 * colapso (D21), detalle de la ejecucion (D24) y verificacion automatica de la
 * politica de entrega (D25).
 */
public record CierreCorrida(
        String idCorrida,
        String escenario,
        String escenarioEtiqueta,
        String algoritmo,
        long semilla,
        String fechaInicio,
        String fechaFin,
        double horasSimuladas,
        String estado,
        String motivoFin,
        long inicioRealMs,
        long finRealMs,
        long duracionRealSeg,
        Resumen resumen,
        List<DiaCumplimiento> cumplimientoPorDia,
        List<KmPorTipo> kmPorTipo,
        List<UsoVehiculo> utilizacionFlota,
        Incidencias incidencias,
        Verificacion verificacion,
        InfoColapso colapso,
        Map<String, Object> parametros,
        Map<String, String> fuentes,
        UltimaPlanificacion ultimaPlanificacion) {

    /**
     * Ultima planificacion estable al cierre (G08-G10): hora y causa de la ultima planificacion y, por unidad con
     * ruta vigente, almacen de salida, paradas pendientes y regreso.
     */
    public record UltimaPlanificacion(String fecha, String causa, List<RutaVigente> rutas) {
    }

    public record RutaVigente(String vehiculo, String tipo, String ruta, String almacenSalida, String salida,
                              List<ParadaPlan> paradas, String almacenRetorno, String regreso) {
    }

    public record ParadaPlan(String pedido, int cantidad, String llegada) {
    }

    public record Resumen(int pedidosRegistrados, int entregados, int entregadosATiempo, int entregadosTarde,
                          int enCurso, int incumplidosSinEntregar, int cancelados, double cumplimiento,
                          double porcentajeEntregadosATiempo, double costoTotal, double kmTotal, int reRuteosBloqueo,
                          int reRuteosAveria, int reasignaciones, int reprogramaciones, int pedidosDivididos,
                          int paradasRecortadasMantenimiento, int rutasDespachadas, int unidadesEntregadas,
                          double costoPorPedidoEntregado, long tiempoComputoPlanificadorMs, int planificaciones) {
    }

    public record DiaCumplimiento(int dia, String fecha, int registrados, int aTiempo, int incumplidos, int enCurso,
                                  double cumplimiento, Double cumplimientoAcumuladoAlCierre) {
    }

    public record KmPorTipo(String tipo, int unidades, int rutas, double km, double costo) {
    }

    public record UsoVehiculo(String id, String tipo, int rutas, int entregas, int unidades, double km, double costo,
                              double horasEnRuta, double factorCargaPromedio) {
    }

    public record AveriaResumen(String id, String vehiculo, String tipo, String fecha, String origen, String causa,
                                int pedidosAfectados) {
    }

    public record Incidencias(int bloqueosProgramados, int bloqueosManuales, int averias, List<AveriaResumen> detalleAverias,
                              Map<String, Integer> eventosPorTipo) {
    }

    public record Verificacion(int pedidosEntregados, int entregadosFueraDePlazo, List<String> pedidosFueraDePlazo,
                               int pedidosIncumplidos, boolean politicaCumplida, String mensaje) {
    }
}
