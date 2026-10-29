package pe.pucp.paqrap.planificador;

/**
 * Calle entre dos nodos de la reticula vial (diagrama, Modulo B):
 * -nodoA, -nodoB, -dobleSentido: boolean, -bloqueada: boolean.
 * Todas las calles son de doble sentido (requisito no funcional c, B6).
 */
public class AristaVial {
    private final NodoVial nodoA;
    private final NodoVial nodoB;
    private final boolean dobleSentido;
    private boolean bloqueada;

    public AristaVial(NodoVial nodoA, NodoVial nodoB, boolean dobleSentido) {
        this.nodoA = nodoA;
        this.nodoB = nodoB;
        this.dobleSentido = dobleSentido;
        this.bloqueada = false;
    }

    public NodoVial getNodoA() { return nodoA; }
    public NodoVial getNodoB() { return nodoB; }
    public boolean isDobleSentido() { return dobleSentido; }
    public boolean isBloqueada() { return bloqueada; }
    public void setBloqueada(boolean bloqueada) { this.bloqueada = bloqueada; }

    /** true si la arista conecta estos dos nodos, en cualquier sentido. */
    public boolean conecta(NodoVial a, NodoVial b) {
        return (nodoA.equals(a) && nodoB.equals(b)) || (dobleSentido && nodoA.equals(b) && nodoB.equals(a));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AristaVial)) return false;
        AristaVial a = (AristaVial) o;
        return (nodoA.equals(a.nodoA) && nodoB.equals(a.nodoB)) || (nodoA.equals(a.nodoB) && nodoB.equals(a.nodoA));
    }

    @Override
    public int hashCode() { return nodoA.hashCode() + nodoB.hashCode(); }
}
