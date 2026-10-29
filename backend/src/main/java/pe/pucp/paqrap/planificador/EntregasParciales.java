package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.flota.Vehiculo;
import pe.pucp.paqrap.registro.Pedido;

import java.util.ArrayList;
import java.util.List;

/**
 * Entregas parciales (P13 y P14 de Preguntas y Respuestas; A4 de la LE).
 *
 * Se permite entregar un pedido en partes, y cada entrega parcial toma 1 hora
 * de acondicionamiento en el cliente. En cada ventana, un pedido se divide
 * cuando se cumplen las dos condiciones:
 * - ninguna unidad libre puede llevarlo completo (por ejemplo, un pedido de
 *   10 paquetes cuando todos los autos estan fuera y solo quedan motos de 8), y
 * - esperar a la siguiente ventana pone en riesgo el plazo: si se espera una
 *   ventana mas, ni siquiera las unidades libres de ahora (las mas rapidas de
 *   ellas) alcanzarian a llevar las partes a tiempo (se deja 1 hora de margen
 *   por la alimentacion). Es la ultima ventana en que dividir todavia sirve.
 * Ademas, un pedido mas grande que la capacidad de cualquier unidad de la
 * flota (por ejemplo, 60 paquetes) se divide siempre, porque nunca va a
 * caber completo en una sola unidad.
 * Si puede esperar, se espera: puede liberarse una unidad grande, y dividir
 * cuesta una hora de entrega mas por cada parte. Tampoco se divide un pedido
 * que ya no llegaria a tiempo ni saliendo ahora.
 * Las partes son del tamano de la mayor capacidad libre y se planifican como
 * pedidos normales, con el mismo cliente y la misma hora limite. El pedido
 * cuenta como entregado a tiempo solo si todas sus partes llegan a tiempo.
 */
public final class EntregasParciales {

    /** Pedido dividido y las partes que lo reemplazan. */
    public record Division(Pedido original, List<Pedido> partes) {
    }

    private static final double MARGEN_ALIMENTACION_H = 1.0;

    private EntregasParciales() {
    }

    /**
     * Divide los pedidos disponibles que no entran completos en ninguna unidad
     * libre y no pueden esperar. Reemplaza cada pedido dividido por sus partes
     * en la lista de disponibles y devuelve las divisiones realizadas.
     *
     * @param capacidadMaximaFlota mayor capacidad de una unidad en toda la flota (libre o no)
     */
    public static List<Division> dividir(List<Pedido> disponibles, List<Vehiculo> libres, RedVial redVial,
                                         double hora, double ventanaHoras, int capacidadMaximaFlota) {
        List<Division> divisiones = new ArrayList<>();
        int capacidadMaxima = 0;
        double velocidadMaxima = 0.0;
        for (Vehiculo v : libres) {
            capacidadMaxima = Math.max(capacidadMaxima, v.getCapacidadPaquetes());
            velocidadMaxima = Math.max(velocidadMaxima, v.getVelocidadPromedioKmH());
        }
        if (capacidadMaxima == 0) return divisiones;

        for (Pedido p : new ArrayList<>(disponibles)) {
            if (p.getCantidadUnidades() <= capacidadMaxima) continue;
            boolean nuncaCabe = p.getCantidadUnidades() > capacidadMaximaFlota;
            double distancia = Double.POSITIVE_INFINITY;
            for (Vehiculo v : libres) {
                distancia = Math.min(distancia, redVial.calcularDistancia(v.getAlmacenOrigen(), p.getUbicacion(), hora));
            }
            if (Double.isInfinite(distancia)) continue;
            if (!nuncaCabe) {
                if (hora + distancia / velocidadMaxima > p.getHoraLimite()) continue;  // ya no llega ni saliendo ahora
                if (hora + ventanaHoras + distancia / velocidadMaxima + MARGEN_ALIMENTACION_H <= p.getHoraLimite()) {
                    continue;  // puede esperar a la siguiente ventana
                }
            }
            List<Pedido> partes = new ArrayList<>();
            int restante = p.getCantidadUnidades();
            int tamano = nuncaCabe ? capacidadMaximaFlota : capacidadMaxima;
            int numero = 1;
            while (restante > 0) {
                int cantidad = Math.min(tamano, restante);
                partes.add(new Pedido(p, String.valueOf(numero++), cantidad));
                restante -= cantidad;
            }
            int i = disponibles.indexOf(p);
            disponibles.remove(i);
            disponibles.addAll(i, partes);
            divisiones.add(new Division(p, partes));
        }
        return divisiones;
    }
}
