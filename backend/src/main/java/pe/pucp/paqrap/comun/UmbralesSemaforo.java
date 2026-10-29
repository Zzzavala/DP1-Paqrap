package pe.pucp.paqrap.comun;

/**
 * Rangos configurables del semaforo (C10, requisito no funcional d).
 *
 * Se expresan como % de holgura de plazo restante (para pedidos) o % de stock
 * (para almacenes intermedios): verde si el valor es mayor o igual a "verde",
 * ambar si es mayor o igual a "ambar", rojo en otro caso. Por definicion el
 * limite inferior del ambar y el del rojo son el mismo valor (prototipo V-1).
 * Valores por defecto del prototipo v04: verde >= 50 %, ambar >= 20 %, rojo < 20 %.
 */
public class UmbralesSemaforo {

    public enum Color { VERDE, AMBAR, ROJO }

    private double verde = 50.0;
    private double ambar = 20.0;

    public UmbralesSemaforo() {
    }

    public UmbralesSemaforo(double verde, double ambar) {
        this.verde = verde;
        this.ambar = ambar;
        normalizar();
    }

    /** +clasificar(porcentaje): Color. */
    public Color clasificar(double porcentaje) {
        if (porcentaje >= verde) return Color.VERDE;
        if (porcentaje >= ambar) return Color.AMBAR;
        return Color.ROJO;
    }

    /** Garantiza 0 <= ambar <= verde <= 100. */
    public void normalizar() {
        verde = Math.max(0, Math.min(100, verde));
        ambar = Math.max(0, Math.min(verde, ambar));
    }

    public UmbralesSemaforo copia() { return new UmbralesSemaforo(verde, ambar); }

    public double getVerde() { return verde; }
    public void setVerde(double verde) { this.verde = verde; }
    public double getAmbar() { return ambar; }
    public void setAmbar(double ambar) { this.ambar = ambar; }
    /** El rojo empieza donde termina el ambar. */
    public double getRojo() { return ambar; }
}
