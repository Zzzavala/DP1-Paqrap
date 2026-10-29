package pe.pucp.paqrap.planificador;

import java.util.HashMap;
import java.util.Map;

/**
 * Parametro que selecciona el algoritmo activo sin recompilar (diagrama,
 * Modulo B; B21): -algoritmoSeleccionado: String, -parametrosAlgoritmo: Map.
 *
 * Claves reconocidas en parametrosAlgoritmo:
 * - GA: poblacion, generaciones, tasaCruce, tasaMutacion, torneo.
 * - ACO: hormigas, iteraciones, alfa, beta, evaporacion, q0.
 */
public class ConfiguracionAlgoritmo {
    private final String algoritmoSeleccionado; // "GA" (Genetico) o "ACO" (Colonia de Hormigas)
    private final Map<String, Object> parametrosAlgoritmo;

    public ConfiguracionAlgoritmo(String algoritmoSeleccionado, Map<String, Object> parametrosAlgoritmo) {
        this.algoritmoSeleccionado = algoritmoSeleccionado;
        this.parametrosAlgoritmo = parametrosAlgoritmo == null ? new HashMap<>() : new HashMap<>(parametrosAlgoritmo);
    }

    public String getAlgoritmoSeleccionado() { return algoritmoSeleccionado; }
    public Map<String, Object> getParametrosAlgoritmo() { return parametrosAlgoritmo; }

    public Object getParametro(String clave, Object porDefecto) {
        return parametrosAlgoritmo.getOrDefault(clave, porDefecto);
    }

    public int getEntero(String clave, int porDefecto) {
        Object v = parametrosAlgoritmo.get(clave);
        if (v instanceof Number n) return n.intValue();
        try {
            return v == null ? porDefecto : Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    public double getDecimal(String clave, double porDefecto) {
        Object v = parametrosAlgoritmo.get(clave);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v == null ? porDefecto : Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
