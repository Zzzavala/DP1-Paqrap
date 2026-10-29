package pe.pucp.paqrap.visualizador;

import pe.pucp.paqrap.simulacion.InfoColapso;

import java.util.List;

/**
 * Estado de la corrida que consume el visualizador (Modulo C: C1-C4, C7-C12,
 * C16, C20-C22). Todos los dispositivos conectados reciben el mismo estado; la
 * zona visible, el zoom y la seleccion son locales a cada dispositivo (C5, C6).
 *
 * Para que el mapa se anime con fluidez sin consultar al servidor a cada
 * instante, las horas viajan como "minutos simulados desde el inicio de la
 * corrida" y cada vehiculo trae su trayectoria restante con la hora de paso
 * por cada punto; el navegador interpola la posicion con el reloj de la
 * corrida (minutoActual + tiempo transcurrido x factor).
 */
public record EstadoVisual(
        long version,
        String idCorrida,
        String estadoCorrida,
        String escenario,
        String escenarioEtiqueta,
        String algoritmo,
        long semilla,
        long inicioMs,
        double minutoActual,
        Double minutoFin,
        double factor,
        long tiempoRealSeg,
        int diaActual,
        Integer totalDias,
        double umbralVerde,
        double umbralAmbar,
        Kpis kpis,
        List<AlmacenVisual> almacenes,
        List<VehiculoVisual> vehiculos,
        List<PedidoVisual> pedidos,
        List<BloqueoVisual> bloqueos,
        List<int[]> tramosManuales,
        List<AlertaVisual> alertas,
        InfoColapso colapso,
        String mensaje) {

    public record Kpis(int registrados, int entregados, int entregadosATiempo, int enCurso, int pendientes,
                       int incumplidos, double cumplimiento, double costo, double km, int flotaActiva, int flotaTotal,
                       int reRuteosBloqueo, int reRuteosAveria, int reasignaciones, int bloqueosVigentes,
                       int pedidosSinUnidad) {
    }

    /**
     * Almacen en el mapa. Campos agregados para la rubrica (E18-E22): partes en rutas que ya salieron de este
     * almacen, partes asignadas que aun no salen (con su minuto de salida) y entregas de las ultimas 24 h simuladas.
     */
    public record AlmacenVisual(String id, String nombre, double x, double y, boolean central, Integer capacidad,
                                Integer stock, List<String> arriban, int pedidosEnTransito, List<PedidoHora> porSalir,
                                List<PedidoHora> entregados) {
    }

    /** Pedido con un minuto simulado asociado (salida prevista o entrega). */
    public record PedidoHora(String pedido, double minuto) {
    }

    /** Parte de un pedido (E31): unidad y ruta que la llevan, almacen de salida y horas en minutos simulados. */
    public record ParteVisual(String id, String vehiculo, String ruta, String almacen, int cantidad, Double salidaMin,
                              Double llegadaMin, String estado) {
    }

    /**
     * Unidad en el mapa. Campos agregados para la rubrica: tipo de averia (1/2/3) y su fin (minutos), horas de la
     * ruta actual en minutos simulados (salida, llegada a la siguiente parada, regreso) y paquetes por pedido a bordo.
     * Tambien el minuto desde el que la unidad va de regreso al almacen (para dibujar ese tramo distinto), los km
     * recorridos en la ruta actual (E09, E17) y el almacen del que salio (E11).
     */
    public record VehiculoVisual(String id, String tipo, String estado, String estadoEtiqueta, double x, double y,
                                 int carga, int capacidad, List<String> pedidos, Double holguraMinima, String destino,
                                 double[] tray, String base, Integer tipoAveria, Double finAveriaMin, Double salidaMin,
                                 Double llegadaProximaMin, Double regresoMin, List<CargaPedido> pedidosCarga,
                                 Double retornoDesdeMin, double kmRecorridosRuta, String origen) {
    }

    /** Paquetes de un pedido que lleva una unidad. */
    public record CargaPedido(String id, int cantidad) {
    }

    public record PedidoVisual(String id, double x, double y, int cantidad, double plazo, double registroMin,
                               double limiteMin, String estado, String vehiculos, boolean reprogramado, String cliente,
                               List<ParteVisual> partes) {
    }

    public record BloqueoVisual(String id, double[] puntos, double inicioMin, double finMin) {
    }

    public record AlertaVisual(long id, String tipo, double minuto, String texto, String vehiculo, String pedido) {
    }
}
