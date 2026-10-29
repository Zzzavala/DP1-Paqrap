package pe.pucp.paqrap.planificador;

/**
 * Planificador (diagrama, Modulo B): -algoritmoActivo: AlgoritmoMetaheuristico.
 * +generarPlan(modelo): Plan. +replanificar(planVigente, evento): Plan.
 * +seleccionarAlgoritmo(config: ConfiguracionAlgoritmo).
 *
 * Es el contexto del patron Strategy: compone el algoritmo activo por
 * configuracion, sin necesidad de recompilar (RAN-03, B21, CA-03). Igual que
 * en MainIntegracion del Algoritmo 1, cada ventana de planificacion usa una
 * instancia nueva del algoritmo con su propia semilla (semilla de la corrida
 * + numero de ventana), asi la corrida es reproducible: misma semilla y
 * mismos archivos dan siempre la misma corrida.
 */
public class Planificador {

    private ConfiguracionAlgoritmo configuracion;
    private String algoritmoActivo;

    public Planificador(ConfiguracionAlgoritmo configuracion) {
        seleccionarAlgoritmo(configuracion);
    }

    /**
     * +seleccionarAlgoritmo(config): void (diagrama). Acepta "GA"/"GENETICO" o
     * "ACO"/"COLONIA"; cualquier otro valor es un error de uso.
     */
    public void seleccionarAlgoritmo(ConfiguracionAlgoritmo config) {
        String nombre = normalizar(config.getAlgoritmoSeleccionado());
        if (nombre == null) {
            throw new IllegalArgumentException("Algoritmo no reconocido: " + config.getAlgoritmoSeleccionado());
        }
        this.configuracion = config;
        this.algoritmoActivo = nombre;
    }

    /** +generarPlan(...): Plan (diagrama). */
    public Plan generarPlan(ModeloRuteo modelo, long semillaVentana) {
        return crearAlgoritmo(semillaVentana).resolver(modelo);
    }

    /** +replanificar(planVigente, evento): Plan (diagrama): vuelve a resolver el modelo de los pedidos afectados. */
    public Plan replanificar(ModeloRuteo modeloPendiente, EventoReplanificacion evento, long semilla) {
        return crearAlgoritmo(semilla).resolver(modeloPendiente);
    }

    public AlgoritmoMetaheuristico crearAlgoritmo(long semilla) {
        if ("ACO".equals(algoritmoActivo)) {
            return new AlgoritmoColoniaHormigas(semilla,
                    configuracion.getEntero("hormigas", 10),
                    configuracion.getEntero("iteraciones", 25),
                    configuracion.getDecimal("alfa", 1.0),
                    configuracion.getDecimal("beta", 2.0),
                    configuracion.getDecimal("evaporacion", 0.1),
                    configuracion.getDecimal("q0", 0.9));
        }
        return new AlgoritmoGenetico(semilla,
                configuracion.getEntero("poblacion", 30),
                configuracion.getEntero("generaciones", 60),
                configuracion.getDecimal("tasaCruce", 0.85),
                configuracion.getDecimal("tasaMutacion", 0.15),
                configuracion.getEntero("torneo", 4));
    }

    public static String normalizar(String nombre) {
        if (nombre == null) return null;
        switch (nombre.trim().toUpperCase()) {
            case "GA":
            case "GENETICO":
            case "ALGORITMO GENETICO":
                return "GA";
            case "ACO":
            case "COLONIA":
            case "COLONIA DE HORMIGAS":
                return "ACO";
            default:
                return null;
        }
    }

    public String getAlgoritmoActivo() { return algoritmoActivo; }
    public ConfiguracionAlgoritmo getConfiguracion() { return configuracion; }
}
