package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.registro.Pedido;

import java.util.List;

/**
 * Plan de distribucion resultante (diagrama, Modulo B): -idPlan, -escenario,
 * -rutas: List<Ruta>, -costoTotal, -kilometrajeTotal, -porcentajeCumplimiento,
 * -fechaGeneracion. +calcularCostoTotal(), +calcularKilometraje(), +esFactible().
 *
 * El escenario se guarda como texto (nombre del enum Escenario de la
 * simulacion) para no acoplar el planificador al componente de simulacion.
 */
public class Plan {
    private final String idPlan;
    private final String escenario;
    private final List<Ruta> rutas;
    private final List<Pedido> pedidosNoAsignados;
    private double costoTotal;
    private double kilometrajeTotal;
    private double porcentajeCumplimiento;
    private final double fechaGeneracion; // hora simulada en que se genero el plan

    public Plan(String idPlan, String escenario, List<Ruta> rutas, List<Pedido> pedidosNoAsignados, double fechaGeneracion) {
        this.idPlan = idPlan;
        this.escenario = escenario;
        this.rutas = rutas;
        this.pedidosNoAsignados = pedidosNoAsignados;
        this.fechaGeneracion = fechaGeneracion;
        calcularCostoTotal();
        calcularKilometraje();
        calcularCumplimiento();
    }

    /** +calcularCostoTotal(): double (diagrama). */
    public double calcularCostoTotal() {
        costoTotal = rutas.stream().mapToDouble(Ruta::getCostoRuta).sum();
        return costoTotal;
    }

    /** +calcularKilometraje(): double (diagrama). */
    public double calcularKilometraje() {
        kilometrajeTotal = rutas.stream().mapToDouble(Ruta::getDistanciaRecorrida).sum();
        return kilometrajeTotal;
    }

    private double calcularCumplimiento() {
        int totalPedidos = pedidosNoAsignados.size();
        int fueraDePlazo = pedidosNoAsignados.size();
        for (Ruta r : rutas) {
            totalPedidos += r.getParadas().size();
            fueraDePlazo += r.contarPedidosFueraDePlazo();
        }
        porcentajeCumplimiento = totalPedidos == 0 ? 100.0 : 100.0 * (totalPedidos - fueraDePlazo) / totalPedidos;
        return porcentajeCumplimiento;
    }

    /** +esFactible(): boolean (diagrama): 100% de cumplimiento y sin pedidos sin asignar. */
    public boolean esFactible() {
        return porcentajeCumplimiento >= 100.0 && pedidosNoAsignados.isEmpty();
    }

    public String getIdPlan() { return idPlan; }
    public String getEscenario() { return escenario; }
    public List<Ruta> getRutas() { return rutas; }
    public List<Pedido> getPedidosNoAsignados() { return pedidosNoAsignados; }
    public double getCostoTotal() { return costoTotal; }
    public double getKilometrajeTotal() { return kilometrajeTotal; }
    public double getPorcentajeCumplimiento() { return porcentajeCumplimiento; }
    public double getFechaGeneracion() { return fechaGeneracion; }
}
