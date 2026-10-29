package pe.pucp.paqrap.comun;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de leer y validar un archivo de entrada (ventas, bloqueos,
 * averias): los registros validos y las lineas rechazadas con su motivo.
 * Una linea con formato inesperado se omite sin romper el resto de la carga
 * (mismo criterio que LectorVentas del Algoritmo 1 y el prototipo).
 */
public class ResultadoLectura<T> {

    /** Linea rechazada del archivo. */
    public record ErrorLinea(int numeroLinea, String contenido, String motivo) {
    }

    private static final int MAX_ERRORES_GUARDADOS = 50;

    private final List<T> registros = new ArrayList<>();
    private final List<ErrorLinea> errores = new ArrayList<>();
    private int lineasLeidas;
    private int totalErrores;

    public void agregarRegistro(T registro) { registros.add(registro); }

    public void agregarError(int numeroLinea, String contenido, String motivo) {
        totalErrores++;
        if (errores.size() < MAX_ERRORES_GUARDADOS) errores.add(new ErrorLinea(numeroLinea, contenido, motivo));
    }

    public void contarLinea() { lineasLeidas++; }

    /** Un archivo es valido si tiene al menos un registro y la gran mayoria de sus lineas son correctas. */
    public boolean esValido() {
        return !registros.isEmpty() && totalErrores <= Math.max(5, lineasLeidas / 20);
    }

    public List<T> getRegistros() { return registros; }
    public List<ErrorLinea> getErrores() { return errores; }
    public int getLineasLeidas() { return lineasLeidas; }
    public int getTotalErrores() { return totalErrores; }
}
