package pe.pucp.paqrap.planificador;

/**
 * «interface» AlgoritmoMetaheuristico (diagrama, Modulo B): contrato comun de
 * las dos soluciones algoritmicas (patron Strategy). +resolver(modelo): Plan.
 */
public interface AlgoritmoMetaheuristico {

    /** +resolver(modelo): Plan (diagrama). */
    Plan resolver(ModeloRuteo modelo);

    /** Nombre corto del algoritmo ("GA" o "ACO") para reportes y bitacora. */
    String getNombre();
}
