package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.ResultadoLectura;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee un archivo de averias programadas (D7).
 *
 * El curso no publico un formato oficial de averias (el prototipo usaba una
 * agenda fija), asi que se define uno con la misma convencion de tiempo que
 * los archivos de ventas y bloqueos:
 *   ##d##h##m:TTNN:T
 * ejemplo: 02d10h30m:TA03:2  (dia 2 a las 10:30, la unidad TA03 sufre una averia tipo 2)
 * El dia es relativo al mes de inicio de la corrida.
 */
public final class LectorAverias {

    /** Averia programada con la hora relativa al dia 1 a las 00:00 del mes de inicio. */
    public record RegistroAveria(int numeroLinea, double horaRelativa, String codigoUnidad, TipoAveria tipo) {
    }

    private static final Pattern LINEA = Pattern.compile("^(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m:\\s*(T[AMB]\\d{2})\\s*:\\s*([123])$");

    private LectorAverias() {
    }

    public static ResultadoLectura<RegistroAveria> leer(Reader fuente) throws IOException {
        ResultadoLectura<RegistroAveria> resultado = new ResultadoLectura<>();
        try (BufferedReader br = new BufferedReader(fuente)) {
            String linea;
            int numero = 0;
            while ((linea = br.readLine()) != null) {
                numero++;
                linea = linea.trim();
                if (numero == 1 && !linea.isEmpty() && linea.charAt(0) == '\uFEFF') linea = linea.substring(1);
                if (linea.isEmpty() || linea.startsWith("#")) continue;
                resultado.contarLinea();
                Matcher m = LINEA.matcher(linea);
                if (!m.matches()) {
                    resultado.agregarError(numero, linea, "Formato inesperado (se espera ##d##h##m:TTNN:tipo)");
                    continue;
                }
                double hora = (Integer.parseInt(m.group(1)) - 1) * 24.0 + Integer.parseInt(m.group(2))
                        + Integer.parseInt(m.group(3)) / 60.0;
                resultado.agregarRegistro(new RegistroAveria(numero, hora, m.group(4),
                        TipoAveria.desdeNumero(Integer.parseInt(m.group(5)))));
            }
        }
        return resultado;
    }
}
