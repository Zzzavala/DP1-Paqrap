package pe.pucp.paqrap.simulacion;

import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.visualizador.EstadoVisual;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corre el escenario de 5 dias de punta a punta, sin reloj (lo mas rapido
 * posible), con los datos reales de setiembre de 2026 y cada algoritmo.
 */
class MotorSimulacionTest {

    private static final FuenteDatos FUENTE = new FuenteDatos("./no-existe");

    private MotorSimulacion correr5d(String algoritmo) {
        ParametrosCorrida p = new ParametrosCorrida();
        p.setEscenario(Escenario.SIMULACION_5D);
        p.setAlgoritmo(algoritmo);
        p.setFechaInicio(LocalDateTime.of(2026, 9, 1, 0, 0));
        MotorSimulacion m = new MotorSimulacion("prueba-" + algoritmo, p, FUENTE.datosParaCorrida(), null);
        long inicio = System.currentTimeMillis();
        m.avanzarHasta(m.getHoraFin());
        long ms = System.currentTimeMillis() - inicio;
        Map<String, Double> ind = m.indicadores();
        System.out.printf("[%s] 5D en %d ms -> %s%n", algoritmo, ms, ind);
        return m;
    }

    @Test
    void simulacionDelPeriodoConGenetico() {
        MotorSimulacion m = correr5d("GA");
        assertTrue(m.estaFinalizado());
        assertEquals("FIN_PERIODO", m.getMotivoFin());
        Map<String, Double> ind = m.indicadores();
        assertTrue(ind.get("pedidos") > 500, "debe haber pedidos en 5 dias de setiembre");
        assertTrue(ind.get("cumplimiento") > 95.0, "el GA debe cumplir los plazos en setiembre");

        CierreCorrida cierre = m.generarCierre("FINALIZADA", 0, 1000);
        assertNotNull(cierre.resumen());
        assertFalse(cierre.cumplimientoPorDia().isEmpty());
        System.out.println("Cierre GA: " + cierre.resumen());
        System.out.println("Por dia: " + cierre.cumplimientoPorDia());
        System.out.println("Km por tipo: " + cierre.kmPorTipo());
        System.out.println("Incidencias: " + cierre.incidencias());
        System.out.println("Verificacion: " + cierre.verificacion());

        EstadoVisual ev = m.construirEstadoVisual("FINALIZADA", 0, 0);
        assertEquals(37, ev.vehiculos().size());
        assertEquals(3, ev.almacenes().size());
    }

    @Test
    void simulacionDelPeriodoConColoniaDeHormigas() {
        MotorSimulacion m = correr5d("ACO");
        assertTrue(m.estaFinalizado());
        Map<String, Double> ind = m.indicadores();
        assertTrue(ind.get("cumplimiento") > 90.0, "el ACO debe cumplir la gran mayoria de plazos en setiembre");
        System.out.println("Cierre ACO: " + m.generarCierre("FINALIZADA", 0, 1000).resumen());
    }

    /** P6, P16, P17: el cambio de velocidad por tipo aplica en la siguiente ventana, tambien a las unidades en ruta. */
    @Test
    void cambioDeVelocidadEnCalienteRecalculaLasRutasEnCurso() {
        ParametrosCorrida p = new ParametrosCorrida();
        p.setEscenario(Escenario.SIMULACION_5D);
        p.setFechaInicio(LocalDateTime.of(2026, 9, 1, 0, 0));
        MotorSimulacion m = new MotorSimulacion("prueba-velocidad", p, FUENTE.datosParaCorrida(), null);
        m.avanzarHasta(m.getHoraInicio() + 6.5);
        long autosEnRuta = m.construirEstadoVisual("EN_CURSO", 160, 0).vehiculos().stream()
                .filter(v -> v.tipo().equals("AUTO") && v.tray().length > 0).count();
        m.actualizarVelocidades(20.0, null, null);
        m.avanzarHasta(m.getHoraInicio() + 7.0 + 1e-6); // ventana de las 07:00
        assertEquals(20.0, m.detalleVehiculo("TA01").velocidad());
        assertEquals(25.0, m.detalleVehiculo("TM01").velocidad(), "solo cambia el tipo indicado");
        long recalculadas = m.construirEstadoVisual("EN_CURSO", 160, 0).vehiculos().stream()
                .filter(v -> v.tray().length > 0)
                .map(v -> m.detalleVehiculo(v.id()).ruta())
                .filter(r -> r != null && r.causa().equals("Recálculo por cambio de velocidad"))
                .count();
        System.out.printf("Autos en ruta antes del cambio: %d; rutas recalculadas por la nueva velocidad: %d%n", autosEnRuta, recalculadas);
        if (autosEnRuta > 0) assertTrue(recalculadas > 0, "las unidades en ruta deben recalcular su recorrido");
    }

    @Test
    void estadoVisualDuranteLaCorrida() {
        ParametrosCorrida p = new ParametrosCorrida();
        p.setEscenario(Escenario.SIMULACION_5D);
        p.setFechaInicio(LocalDateTime.of(2026, 9, 1, 0, 0));
        MotorSimulacion m = new MotorSimulacion("prueba-visual", p, FUENTE.datosParaCorrida(), null);
        m.avanzarHasta(m.getHoraInicio() + 30.5);
        EstadoVisual ev = m.construirEstadoVisual("EN_CURSO", 160, 10);
        assertFalse(ev.pedidos().isEmpty() && ev.kpis().entregados() == 0);
        long enRuta = ev.vehiculos().stream().filter(v -> v.tray().length > 0).count();
        System.out.printf("A las 30.5 h: %d pedidos activos, %d unidades con ruta, %d bloqueos vigentes, KPIs %s%n",
                ev.pedidos().size(), enRuta, ev.bloqueos().size(), ev.kpis());
        // un bloqueo manual y un pedido no permitido fuera de dia a dia
        boolean bloqueado = m.alternarBloqueoTramo(20, 20, 21, 20);
        assertTrue(bloqueado);
        assertFalse(m.alternarBloqueoTramo(20, 20, 21, 20));
        assertNotNull(m.detalleVehiculo("TA01"));
        assertNotNull(m.detalleAlmacen("CENTRAL"));
    }
}
