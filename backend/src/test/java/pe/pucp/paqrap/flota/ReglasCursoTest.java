package pe.pucp.paqrap.flota;

import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.comun.ConversorTiempo;
import pe.pucp.paqrap.comun.ResultadoLectura;
import pe.pucp.paqrap.flota.LectorBloqueos.RegistroBloqueo;
import pe.pucp.paqrap.flota.PlanMantenimiento.RegistroMantenimiento;
import pe.pucp.paqrap.registro.LectorVentas;
import pe.pucp.paqrap.registro.LectorVentas.RegistroVenta;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas del documento de Preguntas y Respuestas del curso (averias, jornada, mantenimiento y formato de archivos). */
class ReglasCursoTest {

    private static final int[] TURNOS = {7, 15, 23};
    private static final double EPS = 1e-9;

    private static double hora(int dia, int h) {
        return ConversorTiempo.aHoras(LocalDateTime.of(2026, 9, dia, h, 0));
    }

    // ---------------- P3: averias ----------------

    @Test
    void averiaTipo1DejaLaUnidadInmovilizadaDosHoras() {
        double t = hora(10, 9);
        assertEquals(t + 2.0, TipoAveria.TIPO_1.calcularHoraDisponible(t, TURNOS), EPS);
        assertEquals(2.0, TipoAveria.TIPO_1.calcularInmovilizacion(t, t + 2.0), EPS);
    }

    @Test
    void averiaTipo2NoEstaDisponibleHastaElFinDelSiguienteTurno() {
        // averia a las 09:00 (turno 07-15): el siguiente turno es 15-23, asi que vuelve a las 23:00
        double t = hora(10, 9);
        assertEquals(hora(10, 23), TipoAveria.TIPO_2.calcularHoraDisponible(t, TURNOS), EPS);
        // se queda en el lugar 4 h como maximo
        assertEquals(4.0, TipoAveria.TIPO_2.calcularInmovilizacion(t, hora(10, 23)), EPS);
    }

    @Test
    void averiaTipo3VuelveDespuesDeDosDiasEnElTurnoDe15a23() {
        double t = hora(10, 9);
        double disponible = TipoAveria.TIPO_3.calcularHoraDisponible(t, TURNOS);
        assertTrue(disponible >= t + 48.0, "al menos 2 dias fuera");
        assertEquals(15.0, ConversorTiempo.horaDelDia(disponible), EPS, "retorna al turno 15:00-23:00");
        assertEquals(4.0, TipoAveria.TIPO_3.calcularInmovilizacion(t, disponible), EPS);
    }

    // ---------------- P12 / P13: jornada y alimentacion ----------------

    @Test
    void elConductorComeUnaHoraDespuesDelCambioDeTurno() {
        JornadaConductor j = new JornadaConductor(TURNOS, 1.0);
        // TA01 (correlativo 1): come 1 h despues del inicio de cada turno -> 08:00, 16:00 y 00:00
        assertEquals(List.of(0.0, 8.0, 16.0), j.horasAlimentacion(1));
        // la unidad 6 come 6 h despues
        assertEquals(List.of(5.0, 13.0, 21.0), j.horasAlimentacion(6));
    }

    @Test
    void laPausaDeAlimentacionSeSumaAlViajeQueLaCruza() {
        JornadaConductor j = new JornadaConductor(TURNOS, 1.0);
        // sale a las 07:30 y viaja 2 h: cruza la pausa de 08:00-09:00, termina a las 10:30
        assertEquals(hora(10, 7) + 0.5 + 2.0 + 1.0, j.avanzar(1, hora(10, 7) + 0.5, 2.0), EPS);
        // un viaje que no la cruza no cambia
        assertEquals(hora(10, 9) + 1.0, j.avanzar(1, hora(10, 9), 1.0), EPS);
    }

    @Test
    void soloSeReportanLasPausasQueYaEmpezaron() {
        JornadaConductor j = new JornadaConductor(TURNOS, 1.0);
        double inicio = hora(10, 7);
        assertTrue(j.pausasOcurridas(1, inicio, hora(10, 7) + 0.5).isEmpty(), "antes de las 08:00 no hubo pausa");
        assertEquals(List.of(hora(10, 8)), j.pausasOcurridas(1, inicio, hora(10, 8) + 0.1));
        // nunca desde antes del inicio de la corrida
        assertFalse(j.pausasOcurridas(1, inicio, hora(10, 20)).contains(hora(10, 0)));
    }

    // ---------------- P19: mantenimiento preventivo ----------------

    @Test
    void elMantenimientoDejaLaUnidadFueraTodoElDiaYSeRepiteCadaDosMeses() throws IOException {
        ResultadoLectura<RegistroMantenimiento> lectura = PlanMantenimiento.leer(new StringReader("20260901:TA01\n20261003:TB10\n"));
        assertTrue(lectura.esValido());
        PlanMantenimiento plan = PlanMantenimiento.desdeRegistros(lectura.getRegistros());

        double[] sep = plan.periodosDelMes(YearMonth.of(2026, 9)).get("TA01").get(0);
        assertEquals(hora(1, 0), sep[0], EPS);
        assertEquals(sep[0] + 24.0, sep[1], EPS, "de 00:00 a 23:59");

        // noviembre repite el plan de septiembre, y diciembre el de octubre
        assertTrue(plan.periodosDelMes(YearMonth.of(2026, 11)).containsKey("TA01"));
        assertTrue(plan.periodosDelMes(YearMonth.of(2026, 12)).containsKey("TB10"));
    }

    @Test
    void elArchivoDeMantenimientoOmiteLineasConOtroFormato() throws IOException {
        ResultadoLectura<RegistroMantenimiento> lectura = PlanMantenimiento.leer(new StringReader("20260901:TA01\n2026-09-02:TB01\n"));
        assertEquals(1, lectura.getTotalErrores(), "la linea con otro formato se informa como error");
        assertEquals(1, lectura.getRegistros().size(), "y solo se conserva la linea valida");
    }

    // ---------------- P7 / P8: formato de bloqueos y ventas ----------------

    @Test
    void ventasSeLeenConElFormatoDelCurso() throws IOException {
        ResultadoLectura<RegistroVenta> r = LectorVentas.leer(new StringReader("11d13h31m:45,43,c9167,12,36\n"), 70, 50);
        assertTrue(r.esValido());
        RegistroVenta v = r.getRegistros().get(0);
        assertEquals(45, v.destinoX());
        assertEquals(43, v.destinoY());
        assertEquals("c9167", v.idCliente());
        assertEquals(12, v.cantidad());
        assertEquals(36, v.plazoHoras());
        assertEquals(10 * 24 + 13 + 31 / 60.0, v.horasDesdeInicioDeMes(), EPS);
    }

    @Test
    void ventasFueraDeLaCiudadSeRechazan() throws IOException {
        ResultadoLectura<RegistroVenta> r = LectorVentas.leer(new StringReader("01d00h00m:71,10,c1,2,36\n"), 70, 50);
        assertFalse(r.esValido());
    }

    @Test
    void bloqueosSeLeenComoPoligonalAbiertaConInicioYFin() throws IOException {
        ResultadoLectura<RegistroBloqueo> r = LectorBloqueos.leer(new StringReader("01d06h00m-01d15h00m:31,21,34,21\n"), 70, 50);
        assertTrue(r.esValido());
        RegistroBloqueo b = r.getRegistros().get(0);
        assertEquals(6.0, b.inicioRelativo(), EPS);
        assertEquals(15.0, b.finRelativo(), EPS);
        assertEquals(2, b.vertices().size());
    }
}
