package pe.pucp.paqrap.registro;

import pe.pucp.paqrap.comun.ResultadoLectura;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee los archivos oficiales ventas.AAAAMM.txt (data publicada por el curso)
 * y tambien los archivos de carga masiva de pedidos (A9), que usan el mismo
 * formato.
 *
 * Formato de cada linea:
 *   ##d##h##m:posX,posY,cliente,cantidad,plazoHoras
 * ejemplo: 01d00h26m:26,15,c0497,06,36
 *
 * El "plazoHoras" (04, 08, 12, 18 o 36) mapea directo a TipoEntrega. A
 * diferencia de la version por linea de comandos, aqui no se crea todavia el
 * Pedido: se devuelve el registro con el dia/hora/minuto relativos al mes, y
 * la simulacion lo convierte a hora absoluta cuando sabe a que mes pertenece.
 */
public final class LectorVentas {

    /** Registro del archivo de ventas, con la hora relativa al dia 1 a las 00:00 de su mes. */
    public record RegistroVenta(int numeroLinea, int dia, int hora, int minuto, int destinoX, int destinoY,
                                String idCliente, int cantidad, int plazoHoras) {

        public double horasDesdeInicioDeMes() {
            return (dia - 1) * 24.0 + hora + minuto / 60.0;
        }
    }

    private static final Pattern LINEA = Pattern.compile(
            "^(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m:\\s*(-?\\d+),\\s*(-?\\d+),\\s*([^,\\s]+),\\s*(\\d+),\\s*(\\d+)$");

    private LectorVentas() {
    }

    /**
     * Lee y valida un archivo de ventas.
     *
     * @param anchoKm ancho de la ciudad (validacion de cobertura, A13)
     * @param altoKm  alto de la ciudad
     */
    public static ResultadoLectura<RegistroVenta> leer(Reader fuente, int anchoKm, int altoKm) throws IOException {
        ResultadoLectura<RegistroVenta> resultado = new ResultadoLectura<>();
        try (BufferedReader br = new BufferedReader(fuente)) {
            String linea;
            int numeroLinea = 0;
            while ((linea = br.readLine()) != null) {
                numeroLinea++;
                linea = linea.trim();
                if (numeroLinea == 1 && !linea.isEmpty() && linea.charAt(0) == '\uFEFF') linea = linea.substring(1);
                if (linea.isEmpty()) continue;
                resultado.contarLinea();

                Matcher m = LINEA.matcher(linea);
                if (!m.matches()) {
                    resultado.agregarError(numeroLinea, linea, "Formato inesperado (se espera ##d##h##m:x,y,cliente,cantidad,plazo)");
                    continue;
                }
                int dia = Integer.parseInt(m.group(1));
                int hora = Integer.parseInt(m.group(2));
                int minuto = Integer.parseInt(m.group(3));
                int x = Integer.parseInt(m.group(4));
                int y = Integer.parseInt(m.group(5));
                String cliente = m.group(6);
                int cantidad = Integer.parseInt(m.group(7));
                int plazo = Integer.parseInt(m.group(8));

                if (dia < 1 || dia > 31 || hora > 23 || minuto > 59) {
                    resultado.agregarError(numeroLinea, linea, "Fecha u hora fuera de rango");
                    continue;
                }
                if (x < 0 || x > anchoKm || y < 0 || y > altoKm) {
                    resultado.agregarError(numeroLinea, linea, "Destino fuera de la zona de cobertura (" + anchoKm + " x " + altoKm + " km)");
                    continue;
                }
                if (cantidad <= 0) {
                    resultado.agregarError(numeroLinea, linea, "Cantidad de paquetes no valida");
                    continue;
                }
                if (TipoEntrega.desdePlazo(plazo) == null) {
                    resultado.agregarError(numeroLinea, linea, "Plazo desconocido (" + plazo + " h): se aceptan 4, 8, 12, 18 o 36");
                    continue;
                }
                resultado.agregarRegistro(new RegistroVenta(numeroLinea, dia, hora, minuto, x, y, cliente, cantidad, plazo));
            }
        }
        return resultado;
    }
}
