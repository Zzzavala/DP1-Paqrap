package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.comun.ResultadoLectura;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee los archivos oficiales de bloqueos programados, bloqueo.AAMM.txt
 * (por ejemplo bloqueo.2609.txt = setiembre de 2026).
 *
 * Formato de cada linea:
 *   ##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn
 * ejemplo: 01d06h00m-01d15h00m:31,21,34,21
 *
 * El primer ##d##h##m es el inicio del bloqueo y el segundo su fin; los
 * puntos forman una poligonal abierta. Se devuelven las horas relativas al
 * dia 1 a las 00:00 del mes; la simulacion las convierte a horas absolutas.
 */
public final class LectorBloqueos {

    /** Registro de bloqueo con horas relativas al inicio de su mes. */
    public record RegistroBloqueo(int numeroLinea, double inicioRelativo, double finRelativo, List<Coordenada> vertices) {
    }

    private static final Pattern LINEA = Pattern.compile(
            "^(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m-(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m:([\\d,\\s]+)$");

    private LectorBloqueos() {
    }

    public static ResultadoLectura<RegistroBloqueo> leer(Reader fuente, int anchoKm, int altoKm) throws IOException {
        ResultadoLectura<RegistroBloqueo> resultado = new ResultadoLectura<>();
        try (BufferedReader br = new BufferedReader(fuente)) {
            String linea;
            int numero = 0;
            while ((linea = br.readLine()) != null) {
                numero++;
                linea = linea.trim();
                if (numero == 1 && !linea.isEmpty() && linea.charAt(0) == '﻿') linea = linea.substring(1);
                if (linea.isEmpty()) continue;
                resultado.contarLinea();
                Matcher m = LINEA.matcher(linea);
                if (!m.matches()) {
                    resultado.agregarError(numero, linea, "Formato inesperado (se espera ##d##h##m-##d##h##m:x1,y1,...)");
                    continue;
                }
                double inicio = aHoras(m.group(1), m.group(2), m.group(3));
                double fin = aHoras(m.group(4), m.group(5), m.group(6));
                if (fin <= inicio) {
                    resultado.agregarError(numero, linea, "El fin del bloqueo no es posterior a su inicio");
                    continue;
                }
                String[] valores = m.group(7).replace(" ", "").split(",");
                if (valores.length < 2 || valores.length % 2 != 0) {
                    resultado.agregarError(numero, linea, "La poligonal debe tener pares x,y");
                    continue;
                }
                List<Coordenada> vertices = new ArrayList<>();
                boolean fueraDeRango = false;
                for (int i = 0; i + 1 < valores.length; i += 2) {
                    int x = Integer.parseInt(valores[i]);
                    int y = Integer.parseInt(valores[i + 1]);
                    if (x < 0 || x > anchoKm || y < 0 || y > altoKm) fueraDeRango = true;
                    vertices.add(new Coordenada(x, y));
                }
                if (fueraDeRango) {
                    resultado.agregarError(numero, linea, "Punto fuera de la ciudad (" + anchoKm + " x " + altoKm + " km)");
                    continue;
                }
                resultado.agregarRegistro(new RegistroBloqueo(numero, inicio, fin, vertices));
            }
        }
        return resultado;
    }

    private static double aHoras(String dia, String hora, String minuto) {
        return (Integer.parseInt(dia) - 1) * 24.0 + Integer.parseInt(hora) + Integer.parseInt(minuto) / 60.0;
    }
}
