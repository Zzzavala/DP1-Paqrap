package pe.pucp.paqrap.planificador;

import java.util.Objects;

/**
 * Nodo de la reticula vial (diagrama, Modulo B): -x: int, -y: int, -bloqueado: boolean.
 */
public class NodoVial {
    private final int x;
    private final int y;
    private boolean bloqueado;

    public NodoVial(int x, int y) {
        this.x = x;
        this.y = y;
        this.bloqueado = false;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isBloqueado() { return bloqueado; }
    public void setBloqueado(boolean bloqueado) { this.bloqueado = bloqueado; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodoVial)) return false;
        NodoVial n = (NodoVial) o;
        return x == n.x && y == n.y;
    }

    @Override
    public int hashCode() { return Objects.hash(x, y); }

    @Override
    public String toString() { return "(" + x + "," + y + ")"; }
}
