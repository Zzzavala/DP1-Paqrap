package pe.pucp.paqrap.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import pe.pucp.paqrap.simulacion.CierreCorrida;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Exportacion de reportes a hoja de calculo (D22, A24): CSV separado por ";" con BOM para Excel. */
final class ReportesCsv {

    private ReportesCsv() {
    }

    static ResponseEntity<byte[]> respuesta(String contenido, String nombreArchivo) {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] cuerpo = contenido.getBytes(StandardCharsets.UTF_8);
        byte[] todo = new byte[bom.length + cuerpo.length];
        System.arraycopy(bom, 0, todo, 0, bom.length);
        System.arraycopy(cuerpo, 0, todo, bom.length, cuerpo.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(todo);
    }

    static String cierre(CierreCorrida c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reporte de cierre de corrida PaqRap\n");
        sb.append("Corrida;").append(c.idCorrida()).append('\n');
        sb.append("Escenario;").append(c.escenarioEtiqueta()).append('\n');
        sb.append("Algoritmo;").append(c.algoritmo()).append('\n');
        sb.append("Semilla;").append(c.semilla()).append('\n');
        sb.append("Inicio simulado;").append(c.fechaInicio()).append('\n');
        sb.append("Fin simulado;").append(c.fechaFin()).append('\n');
        sb.append("Estado;").append(c.estado()).append(" (").append(c.motivoFin()).append(")\n");
        sb.append("Duracion real (s);").append(c.duracionRealSeg()).append("\n\n");

        CierreCorrida.Resumen r = c.resumen();
        sb.append("Indicador;Valor\n");
        sb.append("Pedidos registrados;").append(r.pedidosRegistrados()).append('\n');
        sb.append("Entregados;").append(r.entregados()).append('\n');
        sb.append("Entregados a tiempo;").append(r.entregadosATiempo()).append('\n');
        sb.append("Entregados tarde;").append(r.entregadosTarde()).append('\n');
        sb.append("En curso al cierre;").append(r.enCurso()).append('\n');
        sb.append("Incumplidos sin entregar;").append(r.incumplidosSinEntregar()).append('\n');
        sb.append("Cumplimiento (%);").append(num(r.cumplimiento())).append('\n');
        sb.append("Costo total (S/);").append(num(r.costoTotal())).append('\n');
        sb.append("Km recorridos;").append(num(r.kmTotal())).append('\n');
        sb.append("Costo por pedido entregado (S/);").append(num(r.costoPorPedidoEntregado())).append('\n');
        sb.append("Re-ruteos por bloqueo;").append(r.reRuteosBloqueo()).append('\n');
        sb.append("Re-ruteos por averia;").append(r.reRuteosAveria()).append('\n');
        sb.append("Reasignaciones de carga;").append(r.reasignaciones()).append('\n');
        sb.append("Reprogramaciones;").append(r.reprogramaciones()).append('\n');
        sb.append("Rutas despachadas;").append(r.rutasDespachadas()).append('\n');
        sb.append("Tiempo de computo del planificador (ms);").append(r.tiempoComputoPlanificadorMs()).append("\n\n");

        sb.append("Dia;Fecha;Registrados;A tiempo;Incumplidos;En curso;Cumplimiento (%)\n");
        for (CierreCorrida.DiaCumplimiento d : c.cumplimientoPorDia()) {
            sb.append(d.dia()).append(';').append(d.fecha()).append(';').append(d.registrados()).append(';').append(d.aTiempo()).append(';')
                    .append(d.incumplidos()).append(';').append(d.enCurso()).append(';').append(num(d.cumplimiento())).append('\n');
        }
        sb.append('\n');
        sb.append("Tipo de vehiculo;Unidades;Rutas;Km;Costo (S/)\n");
        for (CierreCorrida.KmPorTipo k : c.kmPorTipo()) {
            sb.append(k.tipo()).append(';').append(k.unidades()).append(';').append(k.rutas()).append(';').append(num(k.km())).append(';')
                    .append(num(k.costo())).append('\n');
        }
        sb.append('\n');
        sb.append("Unidad;Tipo;Rutas;Entregas;Paquetes;Km;Costo (S/);Horas en ruta;Carga promedio (%)\n");
        for (CierreCorrida.UsoVehiculo u : c.utilizacionFlota()) {
            sb.append(u.id()).append(';').append(u.tipo()).append(';').append(u.rutas()).append(';').append(u.entregas()).append(';')
                    .append(u.unidades()).append(';').append(num(u.km())).append(';').append(num(u.costo())).append(';')
                    .append(num(u.horasEnRuta())).append(';').append(num(u.factorCargaPromedio())).append('\n');
        }
        sb.append('\n');
        sb.append("Averia;Unidad;Tipo;Fecha;Origen;Pedidos afectados\n");
        for (CierreCorrida.AveriaResumen a : c.incidencias().detalleAverias()) {
            sb.append(a.id()).append(';').append(a.vehiculo()).append(';').append(a.tipo()).append(';').append(a.fecha()).append(';')
                    .append(a.origen()).append(';').append(a.pedidosAfectados()).append('\n');
        }
        sb.append("Bloqueos programados en la corrida;").append(c.incidencias().bloqueosProgramados()).append('\n');
        sb.append("Bloqueos manuales;").append(c.incidencias().bloqueosManuales()).append('\n');
        for (Map.Entry<String, Integer> e : c.incidencias().eventosPorTipo().entrySet()) {
            sb.append("Eventos ").append(e.getKey()).append(';').append(e.getValue()).append('\n');
        }
        sb.append('\n');
        sb.append("Verificacion de la politica de entrega;").append(c.verificacion().politicaCumplida() ? "CUMPLE" : "NO CUMPLE").append('\n');
        sb.append("Detalle;").append(c.verificacion().mensaje()).append('\n');
        if (c.colapso() != null) {
            sb.append('\n').append("Colapso logistico;").append(c.colapso().fecha()).append('\n');
            sb.append("Motivo;").append(c.colapso().motivo()).append('\n');
            sb.append("Cumplimiento en ese momento (%);").append(num(c.colapso().cumplimiento())).append('\n');
            sb.append("Dias simulados hasta el colapso;").append(num(c.colapso().diasSimulados())).append('\n');
        }
        CierreCorrida.UltimaPlanificacion up = c.ultimaPlanificacion();
        if (up != null) {
            sb.append('\n').append("Ultima planificacion estable;").append(up.fecha()).append(';').append(up.causa()).append('\n');
            sb.append("Unidad;Tipo;Ruta;Almacen de salida;Salida;Paradas (pedido x paquetes @ llegada);Almacen de retorno;Regreso\n");
            for (CierreCorrida.RutaVigente rv : up.rutas()) {
                StringBuilder paradas = new StringBuilder();
                for (CierreCorrida.ParadaPlan p : rv.paradas()) {
                    if (paradas.length() > 0) paradas.append(" | ");
                    paradas.append(p.pedido()).append(" x").append(p.cantidad()).append(" @ ").append(p.llegada());
                }
                sb.append(rv.vehiculo()).append(';').append(rv.tipo()).append(';').append(rv.ruta()).append(';').append(rv.almacenSalida()).append(';')
                        .append(rv.salida()).append(';').append(paradas).append(';').append(rv.almacenRetorno()).append(';').append(rv.regreso()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
