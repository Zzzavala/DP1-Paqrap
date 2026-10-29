package pe.pucp.paqrap.comun;

/**
 * Ubicacion geografica (x, y) sobre la reticula vial de la ciudad (70 x 50 km).
 * Corresponde a la clase "Coordenada" del Modulo A en el diagrama de clases
 * (13.ana.diag.clases.v01): -x: double, -y: double. El origen (0, 0) es la
 * esquina inferior izquierda de la ciudad.
 */
public record Coordenada(double x, double y) {

    /** Distancia de Manhattan en km (camino mas corto en la reticula sin bloqueos). */
    public double distanciaManhattan(Coordenada otra) {
        return Math.abs(x - otra.x) + Math.abs(y - otra.y);
    }

    @Override
    public String toString() {
        return "(" + formatear(x) + "," + formatear(y) + ")";
    }

    private static String formatear(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }
}
