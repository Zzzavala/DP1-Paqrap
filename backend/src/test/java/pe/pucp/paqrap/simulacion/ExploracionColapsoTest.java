package pe.pucp.paqrap.simulacion;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/**
 * Exploracion (no corre por defecto): desde que fecha de inicio colapsa la
 * operacion con cada algoritmo. Se ejecuta con -Dpaqrap.explorar=true.
 */
class ExploracionColapsoTest {

    @Test
    void explorarFechasDeColapso() {
        Assumptions.assumeTrue(System.getProperty("paqrap.explorar") != null);
        FuenteDatos fuente = new FuenteDatos("./no-existe");
        LocalDateTime[] inicios = {
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0),
                LocalDateTime.of(2027, 3, 1, 0, 0),
                LocalDateTime.of(2027, 5, 1, 0, 0),
                LocalDateTime.of(2027, 7, 1, 0, 0),
                LocalDateTime.of(2028, 1, 1, 0, 0),
        };
        for (String alg : new String[]{"GA", "ACO"}) {
            for (LocalDateTime inicio : inicios) {
                ParametrosCorrida p = new ParametrosCorrida();
                p.setEscenario(Escenario.COLAPSO_LOGISTICO);
                p.setAlgoritmo(alg);
                p.setFechaInicio(inicio);
                MotorSimulacion m = new MotorSimulacion("explora", p, fuente.datosParaCorrida(), null);
                long t0 = System.currentTimeMillis();
                m.avanzarHasta(m.getHoraInicio() + 24.0 * 45);
                long ms = System.currentTimeMillis() - t0;
                InfoColapso c = m.getColapso();
                System.out.printf("%s desde %s: %s en %d ms | %s%n", alg, inicio.toLocalDate(),
                        c == null ? "SIN colapso en 45 dias" : ("colapso a los " + c.diasSimulados() + " dias (" + c.fecha() + "): " + c.motivo()),
                        ms, m.indicadores());
            }
        }
    }
}
