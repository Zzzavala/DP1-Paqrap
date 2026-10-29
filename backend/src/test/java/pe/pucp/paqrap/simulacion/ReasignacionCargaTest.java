package pe.pucp.paqrap.simulacion;

import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.flota.PlanMantenimiento;
import pe.pucp.paqrap.flota.TipoAveria;
import pe.pucp.paqrap.registro.CanalPedido;
import pe.pucp.paqrap.registro.TipoEntrega;
import pe.pucp.paqrap.visualizador.DetalleVisual;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Averia tipo 2 de una unidad que lleva un pedido priorizado (4 h) que ya no
 * alcanza a reponerse desde el almacen: otra unidad en camino, que lleva carga
 * para un cliente con mas holgura, entrega primero al pedido critico (B10, B11).
 */
class ReasignacionCargaTest {

    @Test
    void averiaConCargaCriticaSeCubreConCargaEnTransito() {
        DatosCorrida sinArchivos = new DatosCorrida(mes -> List.of(), mes -> List.of(), PlanMantenimiento.vacio(), null,
                YearMonth.of(2028, 12), Map.of());
        ParametrosCorrida p = new ParametrosCorrida();
        p.setEscenario(Escenario.TIEMPO_REAL);
        p.setAlgoritmo("GA");
        p.setGenerarAverias(false);
        p.setFechaInicio(LocalDateTime.of(2026, 10, 10, 10, 0));
        MotorSimulacion m = new MotorSimulacion("prueba-b11", p, sinArchivos, null);
        double t0 = m.getHoraInicio();

        String a = m.registrarPedido("cA", "Cliente critico", 68, 48, 4, TipoEntrega.PRIORIZADA_4H, CanalPedido.WEB, null).getIdPedido();
        String b = m.registrarPedido("cB", "Cliente con holgura", 66, 48, 8, TipoEntrega.REGULAR_36H, CanalPedido.WEB, null).getIdPedido();
        m.avanzarHasta(t0 + 0.05);

        DetalleVisual.Pedido da = m.detallePedido(a);
        DetalleVisual.Pedido db = m.detallePedido(b);
        assertEquals(1, da.vehiculos().size(), "el pedido critico sale de inmediato");
        assertEquals(1, db.vehiculos().size(), "el pedido con holgura sale de inmediato");
        String unidadA = da.vehiculos().get(0);
        String unidadB = db.vehiculos().get(0);
        System.out.println("A en " + unidadA + ", B en " + unidadB);

        m.avanzarHasta(t0 + 2.3);
        m.registrarAveria(unidadA, TipoAveria.TIPO_2, "prueba");
        m.avanzarHasta(t0 + 2.35);

        DetalleVisual.Pedido da2 = m.detallePedido(a);
        da2.eventos().forEach(e -> System.out.println("A: " + e.fecha() + " " + e.tipo() + " " + e.detalle()));
        assertTrue(da2.eventos().stream().anyMatch(e -> e.tipo().equals("REASIGNACION")), "A debe recibir carga en transito");
        assertTrue(da2.vehiculos().contains(unidadB), "A lo atiende la unidad que llevaba la carga de B");

        DetalleVisual.Pedido db2 = m.detallePedido(b);
        db2.eventos().forEach(e -> System.out.println("B: " + e.fecha() + " " + e.tipo() + " " + e.detalle()));
        assertTrue(db2.eventos().stream().anyMatch(e -> e.tipo().equals("REASIGNACION")), "B cede parte de su carga");

        m.avanzarHasta(t0 + 12);
        DetalleVisual.Pedido da3 = m.detallePedido(a);
        DetalleVisual.Pedido db3 = m.detallePedido(b);
        assertEquals("Entregado", da3.estado());
        assertEquals(Boolean.TRUE, da3.aTiempo(), "el pedido critico llega dentro de su plazo");
        assertEquals("Entregado", db3.estado());
        assertEquals(Boolean.TRUE, db3.aTiempo(), "el pedido que cedio su carga tambien llega a tiempo");
        CierreCorrida c = m.generarCierre("DETENIDA", 0, 1);
        assertEquals(1, c.resumen().reasignaciones());
        assertNotNull(c.incidencias().detalleAverias());
    }
}
