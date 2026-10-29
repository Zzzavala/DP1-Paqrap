package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.Coordenada;

import java.util.ArrayList;
import java.util.List;

/**
 * Cierre temporal de calles (diagrama, Modulo D): -areaAfectada (D6).
 *
 * Un bloqueo programado viene del archivo mensual de bloqueos
 * (registro ##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn): un intervalo de
 * tiempo y una poligonal abierta de tramos horizontales o verticales. Todos
 * los nodos que recorre esa poligonal quedan bloqueados mientras el bloqueo
 * esta vigente (Preguntas y Respuestas, P7). Las horas son absolutas.
 */
public class Bloqueo {
    private final String idBloqueo;
    private final double horaInicio;
    private final double horaFin;
    private final List<Coordenada> vertices;  // poligonal abierta

    public Bloqueo(String idBloqueo, double horaInicio, double horaFin, List<Coordenada> vertices) {
        this.idBloqueo = idBloqueo;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.vertices = List.copyOf(vertices);
    }

    /** true si el bloqueo rige en la hora dada: [inicio, fin). */
    public boolean estaVigente(double hora) {
        return hora >= horaInicio && hora < horaFin;
    }

    /** Todos los nodos (x, y) por los que pasa la poligonal, incluidos sus extremos. */
    public List<int[]> nodosBloqueados() {
        List<int[]> nodos = new ArrayList<>();
        for (int i = 0; i + 1 < vertices.size(); i++) {
            int x1 = (int) vertices.get(i).x(), y1 = (int) vertices.get(i).y();
            int x2 = (int) vertices.get(i + 1).x(), y2 = (int) vertices.get(i + 1).y();
            int dx = Integer.signum(x2 - x1), dy = Integer.signum(y2 - y1);
            int x = x1, y = y1;
            nodos.add(new int[]{x, y});
            int pasos = 0;
            while ((x != x2 || y != y2) && pasos++ < 1000) {
                // una poligonal oficial solo tiene tramos horizontales o verticales; si viniera
                // un tramo diagonal se recorre primero en x y luego en y
                if (x != x2) x += dx; else y += dy;
                nodos.add(new int[]{x, y});
            }
        }
        if (vertices.size() == 1) {
            nodos.add(new int[]{(int) vertices.get(0).x(), (int) vertices.get(0).y()});
        }
        return nodos;
    }

    public String getIdBloqueo() { return idBloqueo; }
    public double getHoraInicio() { return horaInicio; }
    public double getHoraFin() { return horaFin; }
    public List<Coordenada> getVertices() { return vertices; }
}
