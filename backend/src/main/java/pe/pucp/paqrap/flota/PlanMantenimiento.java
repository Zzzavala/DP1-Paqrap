package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.ConversorTiempo;
import pe.pucp.paqrap.comun.ResultadoLectura;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mantenimiento preventivo (P19 de Preguntas y Respuestas).
 *
 * Archivo mant.preventivo.m1.m2 (por ejemplo mant.preventivo.09.10), una
 * linea por mantenimiento con el formato aaaammdd:TTNN (20260901:TA01).
 * - La unidad no esta disponible ese dia desde las 00:00 hasta las 23:59.
 * - El plan vale para dos meses y se repite cada dos meses: si no hay un
 *   registro para el mes pedido, se usan los dias del primer mes del plan en
 *   los meses impares y los del segundo mes en los pares (enero-febrero,
 *   marzo-abril, ..., setiembre-octubre, noviembre-diciembre).
 *
 * Las horas son absolutas (ver ConversorTiempo).
 */
public class PlanMantenimiento {

    /** Registro del archivo: fecha y codigo de unidad. */
    public record RegistroMantenimiento(YearMonth mes, int dia, String codigoUnidad) {
    }

    private static final Pattern LINEA = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2}):(T[AMB]\\d{2})$");

    // mes -> (dia -> codigo)
    private final Map<YearMonth, TreeMap<Integer, String>> registros = new TreeMap<>();

    public static PlanMantenimiento vacio() {
        return new PlanMantenimiento();
    }

    /** Lee y valida un archivo de mantenimiento preventivo. */
    public static ResultadoLectura<RegistroMantenimiento> leer(Reader fuente) throws IOException {
        ResultadoLectura<RegistroMantenimiento> resultado = new ResultadoLectura<>();
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
                    resultado.agregarError(numero, linea, "Formato inesperado (se espera aaaammdd:TTNN)");
                    continue;
                }
                try {
                    YearMonth mes = YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                    int dia = Integer.parseInt(m.group(3));
                    if (dia < 1 || dia > mes.lengthOfMonth()) {
                        resultado.agregarError(numero, linea, "Dia fuera de rango");
                        continue;
                    }
                    resultado.agregarRegistro(new RegistroMantenimiento(mes, dia, m.group(4)));
                } catch (RuntimeException e) {
                    resultado.agregarError(numero, linea, "Fecha no valida");
                }
            }
        }
        return resultado;
    }

    public static PlanMantenimiento desdeRegistros(List<RegistroMantenimiento> lista) {
        PlanMantenimiento plan = new PlanMantenimiento();
        for (RegistroMantenimiento r : lista) {
            plan.registros.computeIfAbsent(r.mes(), k -> new TreeMap<>()).put(r.dia(), r.codigoUnidad());
        }
        return plan;
    }

    /**
     * Periodos [inicio, fin) de mantenimiento de todas las unidades en un mes,
     * en horas absolutas, aplicando la repeticion cada dos meses.
     */
    public Map<String, List<double[]>> periodosDelMes(YearMonth mes) {
        Map<String, List<double[]>> periodos = new HashMap<>();
        TreeMap<Integer, String> dias = registros.get(mes);
        if (dias == null) {
            int posicion = (mes.getMonthValue() - 1) % 2;
            for (Map.Entry<YearMonth, TreeMap<Integer, String>> e : registros.entrySet()) {
                if ((e.getKey().getMonthValue() - 1) % 2 == posicion) {
                    dias = e.getValue();
                    break;
                }
            }
        }
        if (dias == null) return periodos;
        double inicioMes = ConversorTiempo.inicioDeMes(mes);
        for (Map.Entry<Integer, String> e : dias.entrySet()) {
            int dia = e.getKey();
            if (dia > mes.lengthOfMonth()) continue;
            double inicio = inicioMes + (dia - 1) * 24.0;
            periodos.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(new double[]{inicio, inicio + 24.0});
        }
        return periodos;
    }

    public int totalRegistros() {
        int total = 0;
        for (TreeMap<Integer, String> m : registros.values()) total += m.size();
        return total;
    }
}
