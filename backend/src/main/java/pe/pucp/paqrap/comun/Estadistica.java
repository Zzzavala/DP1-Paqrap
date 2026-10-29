package pe.pucp.paqrap.comun;

import java.util.List;

/**
 * Estadistica descriptiva y prueba t pareada para la experimentacion numerica
 * entre las dos metaheuristicas (B23). La distribucion t de Student se evalua
 * con la funcion beta incompleta regularizada (fraccion continua de Lentz).
 */
public final class Estadistica {

    /** Resumen de una muestra. */
    public record Resumen(int n, double media, double desviacion, double minimo, double maximo) {
    }

    /** Resultado de la prueba t pareada (dos colas) sobre las diferencias a - b. */
    public record PruebaT(int n, double diferenciaMedia, double estadisticoT, int gradosLibertad, double valorP) {
    }

    private Estadistica() {
    }

    public static Resumen resumir(List<Double> valores) {
        int n = valores.size();
        if (n == 0) return new Resumen(0, 0, 0, 0, 0);
        double suma = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double v : valores) {
            suma += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double media = suma / n;
        double var = 0;
        for (double v : valores) var += (v - media) * (v - media);
        double desv = n > 1 ? Math.sqrt(var / (n - 1)) : 0;
        return new Resumen(n, media, desv, min, max);
    }

    public static PruebaT pruebaTPareada(List<Double> a, List<Double> b) {
        int n = Math.min(a.size(), b.size());
        if (n < 2) return new PruebaT(n, n == 1 ? a.get(0) - b.get(0) : 0, 0, Math.max(0, n - 1), 1.0);
        double[] d = new double[n];
        double suma = 0;
        for (int i = 0; i < n; i++) {
            d[i] = a.get(i) - b.get(i);
            suma += d[i];
        }
        double media = suma / n;
        double var = 0;
        for (double v : d) var += (v - media) * (v - media);
        double desv = Math.sqrt(var / (n - 1));
        int gl = n - 1;
        if (desv < 1e-12) {
            return new PruebaT(n, media, media == 0 ? 0 : Double.POSITIVE_INFINITY, gl, media == 0 ? 1.0 : 0.0);
        }
        double t = media / (desv / Math.sqrt(n));
        double p = 2.0 * (1.0 - cdfT(Math.abs(t), gl));
        return new PruebaT(n, media, t, gl, Math.max(0, Math.min(1, p)));
    }

    /** Funcion de distribucion acumulada de la t de Student con gl grados de libertad. */
    public static double cdfT(double t, int gl) {
        double x = gl / (gl + t * t);
        double ib = betaIncompletaRegularizada(gl / 2.0, 0.5, x);
        return t >= 0 ? 1.0 - 0.5 * ib : 0.5 * ib;
    }

    private static double betaIncompletaRegularizada(double a, double b, double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double lnBeta = logGamma(a + b) - logGamma(a) - logGamma(b) + a * Math.log(x) + b * Math.log(1 - x);
        if (x < (a + 1) / (a + b + 2)) {
            return Math.exp(lnBeta) * fraccionContinua(a, b, x) / a;
        }
        return 1.0 - Math.exp(lnBeta) * fraccionContinua(b, a, 1 - x) / b;
    }

    private static double fraccionContinua(double a, double b, double x) {
        final double minimo = 1e-300;
        double qab = a + b, qap = a + 1, qam = a - 1;
        double c = 1, d = 1 - qab * x / qap;
        if (Math.abs(d) < minimo) d = minimo;
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= 300; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < minimo) d = minimo;
            c = 1 + aa / c;
            if (Math.abs(c) < minimo) c = minimo;
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < minimo) d = minimo;
            c = 1 + aa / c;
            if (Math.abs(c) < minimo) c = minimo;
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-12) break;
        }
        return h;
    }

    private static double logGamma(double x) {
        double[] coef = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x, tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double c : coef) ser += c / ++y;
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
}
