package pe.pucp.paqrap.simulacion;

import java.util.Arrays;

/**
 * Recorrido de una unidad en el tiempo: una poligonal sobre la reticula con la
 * hora en que la unidad pasa por cada punto (horas absolutas). Entre dos
 * puntos la unidad avanza en linea recta a velocidad constante; dos puntos
 * seguidos en el mismo lugar representan una parada (entrega, alimentacion o
 * espera por averia).
 *
 * Con la trayectoria, la posicion de la unidad en cualquier instante es una
 * interpolacion, tanto en el servidor como en el visualizador, que la recibe
 * para animar el mapa sin pedir la posicion a cada momento.
 */
public final class Trayectoria {

    private double[] xs = new double[64];
    private double[] ys = new double[64];
    private double[] ts = new double[64];
    private int n;

    /** Agrega un punto (se ignora si repite exactamente el anterior). */
    public void agregar(double x, double y, double t) {
        if (n > 0 && xs[n - 1] == x && ys[n - 1] == y && ts[n - 1] == t) return;
        if (n > 0 && t < ts[n - 1]) t = ts[n - 1]; // nunca retrocede en el tiempo
        if (n == xs.length) {
            int nuevo = n * 2;
            xs = Arrays.copyOf(xs, nuevo);
            ys = Arrays.copyOf(ys, nuevo);
            ts = Arrays.copyOf(ts, nuevo);
        }
        xs[n] = x;
        ys[n] = y;
        ts[n] = t;
        n++;
    }

    public int tamano() { return n; }
    public double x(int i) { return xs[i]; }
    public double y(int i) { return ys[i]; }
    public double t(int i) { return ts[i]; }
    public double tiempoInicio() { return n == 0 ? 0 : ts[0]; }
    public double tiempoFin() { return n == 0 ? 0 : ts[n - 1]; }

    /** Indice del ultimo punto con hora <= t (0 si t es anterior al inicio). */
    public int indiceEn(double t) {
        if (n == 0 || t <= ts[0]) return 0;
        if (t >= ts[n - 1]) return n - 1;
        int bajo = 0, alto = n - 1;
        while (bajo < alto) {
            int medio = (bajo + alto + 1) >>> 1;
            if (ts[medio] <= t) bajo = medio; else alto = medio - 1;
        }
        return bajo;
    }

    /** Posicion {x, y} en el instante t. */
    public double[] posicionEn(double t) {
        if (n == 0) return new double[]{0, 0};
        int i = indiceEn(t);
        if (i >= n - 1 || t <= ts[i]) return new double[]{xs[i], ys[i]};
        double dt = ts[i + 1] - ts[i];
        double f = dt <= 0 ? 1.0 : (t - ts[i]) / dt;
        return new double[]{xs[i] + (xs[i + 1] - xs[i]) * f, ys[i] + (ys[i + 1] - ys[i]) * f};
    }

    /** Direccion de avance {dx, dy} (signos) del segmento en curso en t; {0, 0} si esta detenida. */
    public int[] direccionEn(double t) {
        if (n < 2) return new int[]{0, 0};
        int i = indiceEn(t);
        // si esta justo en un punto, mira el segmento que sigue
        while (i < n - 1 && xs[i + 1] == xs[i] && ys[i + 1] == ys[i] && ts[i + 1] <= t) i++;
        if (i >= n - 1) return new int[]{0, 0};
        return new int[]{(int) Math.signum(xs[i + 1] - xs[i]), (int) Math.signum(ys[i + 1] - ys[i])};
    }

    /** Km recorridos desde el inicio hasta el instante t. */
    public double kmHasta(double t) {
        double km = 0;
        for (int i = 0; i + 1 < n; i++) {
            if (ts[i] >= t) break;
            double seg = Math.abs(xs[i + 1] - xs[i]) + Math.abs(ys[i + 1] - ys[i]);
            if (ts[i + 1] <= t) {
                km += seg;
            } else {
                double dt = ts[i + 1] - ts[i];
                km += dt <= 0 ? seg : seg * (t - ts[i]) / dt;
                break;
            }
        }
        return km;
    }

    /** Km totales de la trayectoria. */
    public double kmTotal() {
        double km = 0;
        for (int i = 0; i + 1 < n; i++) km += Math.abs(xs[i + 1] - xs[i]) + Math.abs(ys[i + 1] - ys[i]);
        return km;
    }

    /**
     * Tramo restante desde el instante t, compactado para el visualizador:
     * {x, y, t} empezando en la posicion actual, sin los puntos intermedios
     * que estan en linea recta y a la misma velocidad.
     */
    public double[][] restanteCompacto(double t) {
        if (n == 0) return new double[0][];
        int i = indiceEn(t);
        double[] actual = posicionEn(t);
        double[][] tmp = new double[n - i + 1][];
        int k = 0;
        tmp[k++] = new double[]{actual[0], actual[1], Math.max(t, ts[0])};
        for (int j = i + 1; j < n; j++) {
            double[] p = {xs[j], ys[j], ts[j]};
            if (k >= 2 && esIntermedioRecto(tmp[k - 2], tmp[k - 1], p)) {
                tmp[k - 1] = p;
            } else {
                tmp[k++] = p;
            }
        }
        return Arrays.copyOf(tmp, k);
    }

    /** b sobra si a, b y c estan alineados en el mismo eje y b no cambia la velocidad ni es una parada. */
    private static boolean esIntermedioRecto(double[] a, double[] b, double[] c) {
        boolean mismoEje = (a[0] == b[0] && b[0] == c[0]) || (a[1] == b[1] && b[1] == c[1]);
        if (!mismoEje) return false;
        double d1 = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
        double d2 = Math.abs(c[0] - b[0]) + Math.abs(c[1] - b[1]);
        if (d1 == 0 || d2 == 0) return false;
        // mismo sentido
        if (Math.signum(b[0] - a[0]) != Math.signum(c[0] - b[0]) || Math.signum(b[1] - a[1]) != Math.signum(c[1] - b[1])) return false;
        double v1 = d1 / Math.max(1e-12, b[2] - a[2]);
        double v2 = d2 / Math.max(1e-12, c[2] - b[2]);
        return Math.abs(v1 - v2) <= 1e-6 * Math.max(1.0, v1);
    }
}
