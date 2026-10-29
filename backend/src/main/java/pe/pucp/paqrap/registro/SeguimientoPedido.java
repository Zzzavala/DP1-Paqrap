package pe.pucp.paqrap.registro;

import pe.pucp.paqrap.comun.Coordenada;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pedido del cliente con su trazabilidad completa (Modulo A: A8, A12, A14,
 * A15, A19, A20, A21, A25; diagrama: HistorialEstadoPedido).
 *
 * El Planificador trabaja con objetos Pedido, que pueden ser el pedido entero o
 * partes de el (entregas parciales, transbordos por averia o reasignaciones de
 * carga). Esta clase agrupa todas las partes de un mismo pedido del cliente y
 * deriva su estado: el pedido esta entregado cuando se entregaron todas sus
 * unidades, y cuenta como entregado a tiempo solo si todas sus partes llegaron
 * dentro del plazo comprometido (mismo criterio que MainIntegracion del
 * Algoritmo 1).
 */
public class SeguimientoPedido {

    /** Evento de la bitacora del pedido (cambio de estado, asignacion, reasignacion, reprogramacion...). */
    public record EventoPedido(double hora, String tipo, String detalle) {
    }

    private final Pedido pedidoOriginal;
    private final CanalPedido canal;
    private String observaciones;
    private final Map<String, Pedido> partesActivas = new LinkedHashMap<>();
    private final Set<String> vehiculos = new LinkedHashSet<>();
    private final Set<String> almacenesOrigen = new LinkedHashSet<>();
    private final Set<String> rutas = new LinkedHashSet<>();
    private final List<EventoPedido> eventos = new ArrayList<>();
    private int unidadesEntregadas;
    private double horaEntregaReal = Double.NaN;
    private boolean entregaTardia;
    private boolean incumplido;
    private boolean reprogramado;
    private boolean cancelado;
    private boolean reasignado;
    private EstadoPedido estado = EstadoPedido.REGISTRADO;

    public SeguimientoPedido(Pedido pedidoOriginal, CanalPedido canal, String observaciones) {
        this.pedidoOriginal = pedidoOriginal;
        this.canal = canal;
        this.observaciones = observaciones;
        partesActivas.put(pedidoOriginal.getIdPedido(), pedidoOriginal);
        registrarEvento(pedidoOriginal.getHoraRegistro(), "REGISTRO",
                "Pedido registrado (" + canal.getEtiqueta() + "): " + pedidoOriginal.getCantidadUnidades()
                        + " unid., plazo " + formatearHoras(pedidoOriginal.getPlazoHoras()) + " h.");
    }

    // ------------------------------------------------------------------
    // Partes
    // ------------------------------------------------------------------

    /** Reemplaza una parte activa por otras (division P13, transbordo o reasignacion). */
    public void reemplazarParte(Pedido parte, List<Pedido> nuevas) {
        partesActivas.remove(parte.getIdPedido());
        for (Pedido p : nuevas) partesActivas.put(p.getIdPedido(), p);
    }

    public void agregarParte(Pedido parte) {
        partesActivas.put(parte.getIdPedido(), parte);
    }

    /** Retira una parte que deja de existir sin entregarse (su carga pasa a otra parte). */
    public void retirarParte(Pedido parte) {
        partesActivas.remove(parte.getIdPedido());
    }

    /** Registra la entrega de una parte en el cliente (P11: el plazo se cumple al llegar). */
    public void registrarEntrega(Pedido parte, double hora, String idVehiculo) {
        partesActivas.remove(parte.getIdPedido());
        parte.cambiarEstado(EstadoPedido.ENTREGADO);
        unidadesEntregadas += parte.getCantidadUnidades();
        if (hora > pedidoOriginal.getHoraLimiteCompromiso() + 1e-9) entregaTardia = true;
        if (Double.isNaN(horaEntregaReal) || hora > horaEntregaReal) horaEntregaReal = hora;
        String detalle = (parte.esParte() ? "Parte " + parte.getIdPedido() + " (" + parte.getCantidadUnidades() + " unid.)"
                : parte.getCantidadUnidades() + " unid.") + " entregada por " + idVehiculo
                + (hora > pedidoOriginal.getHoraLimiteCompromiso() + 1e-9 ? " FUERA DE PLAZO." : " dentro del plazo.");
        registrarEvento(hora, "ENTREGA", detalle);
        if (estaEntregado()) {
            estado = EstadoPedido.ENTREGADO;
            if (entregaTardia) incumplido = true;
        }
    }

    public void registrarAsignacion(double hora, String idVehiculo, String idRuta, String almacen, Pedido parte) {
        vehiculos.add(idVehiculo);
        rutas.add(idRuta);
        almacenesOrigen.add(almacen);
        if (estado != EstadoPedido.ENTREGADO) estado = EstadoPedido.EN_TRANSITO;
        registrarEvento(hora, "ASIGNACION", (parte.esParte() ? "Parte " + parte.getIdPedido() + " (" + parte.getCantidadUnidades()
                + " unid.)" : parte.getCantidadUnidades() + " unid.") + " despachada desde " + almacen + " en " + idVehiculo
                + " (ruta " + idRuta + ").");
    }

    public void registrarEvento(double hora, String tipo, String detalle) {
        eventos.add(new EventoPedido(hora, tipo, detalle));
    }

    public void marcarReasignado(double hora, String detalle) {
        reasignado = true;
        if (estado != EstadoPedido.ENTREGADO) estado = EstadoPedido.REASIGNADO;
        registrarEvento(hora, "REASIGNACION", detalle);
    }

    /** Vuelve el estado a "registrado" cuando todas sus partes quedan pendientes otra vez. */
    public void marcarPendiente() {
        if (estado != EstadoPedido.ENTREGADO && estado != EstadoPedido.CANCELADO) {
            boolean algunaEnRuta = partesActivas.values().stream().anyMatch(p -> p.getEstado() == EstadoPedido.EN_TRANSITO);
            estado = algunaEnRuta ? EstadoPedido.EN_TRANSITO : (reasignado ? EstadoPedido.REASIGNADO : EstadoPedido.REGISTRADO);
        }
    }

    /** Reprogramacion (A21): el pedido ya no cumple el plazo comprometido. */
    public void marcarIncumplido(double hora, String detalle) {
        incumplido = true;
        if (!reprogramado) {
            reprogramado = true;
            registrarEvento(hora, "REPROGRAMACION", detalle);
        }
    }

    public void cancelar(double hora, String motivo) {
        cancelado = true;
        estado = EstadoPedido.CANCELADO;
        for (Pedido p : partesActivas.values()) p.cambiarEstado(EstadoPedido.CANCELADO);
        partesActivas.clear();
        registrarEvento(hora, "CANCELACION", motivo == null || motivo.isBlank() ? "Pedido cancelado." : motivo);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public boolean estaEntregado() {
        return unidadesEntregadas >= pedidoOriginal.getCantidadUnidades();
    }

    /** Pedido que todavia debe atenderse (no entregado ni cancelado). */
    public boolean estaActivo() {
        return !cancelado && !estaEntregado();
    }

    /** Vencido: no se entrego y ya paso la hora limite comprometida. */
    public boolean estaVencido(double horaActual) {
        return estaActivo() && horaActual > pedidoOriginal.getHoraLimiteCompromiso() + 1e-9;
    }

    /** Entregado completo y todas sus partes dentro del plazo comprometido. */
    public boolean fueEntregadoATiempo() {
        return estaEntregado() && !entregaTardia;
    }

    /** No cumple la politica de entrega: entregado tarde, reprogramado o vencido sin entregar. */
    public boolean esIncumplido(double horaActual) {
        if (cancelado) return false;
        return incumplido || entregaTardia || estaVencido(horaActual);
    }

    /** true si el pedido todavia no sale de un almacen (se puede editar o cancelar: A6, A7). */
    public boolean esModificable() {
        if (!estaActivo()) return false;
        return partesActivas.values().stream().allMatch(p -> p.getEstado() == EstadoPedido.REGISTRADO)
                && unidadesEntregadas == 0 && vehiculos.isEmpty();
    }

    public String getIdPedido() { return pedidoOriginal.getIdPedido(); }
    public Pedido getPedidoOriginal() { return pedidoOriginal; }
    public Cliente getCliente() { return pedidoOriginal.getCliente(); }
    public Coordenada getUbicacion() { return pedidoOriginal.getUbicacion(); }
    public int getCantidadUnidades() { return pedidoOriginal.getCantidadUnidades(); }
    public TipoEntrega getTipoEntrega() { return pedidoOriginal.getTipoEntrega(); }
    public double getPlazoHoras() { return pedidoOriginal.getPlazoHoras(); }
    public double getHoraRegistro() { return pedidoOriginal.getHoraRegistro(); }
    public double getHoraLimite() { return pedidoOriginal.getHoraLimiteCompromiso(); }
    public CanalPedido getCanal() { return canal; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public List<Pedido> getPartesActivas() { return new ArrayList<>(partesActivas.values()); }
    public Set<String> getVehiculos() { return Collections.unmodifiableSet(vehiculos); }
    public Set<String> getAlmacenesOrigen() { return Collections.unmodifiableSet(almacenesOrigen); }
    public Set<String> getRutas() { return Collections.unmodifiableSet(rutas); }
    public List<EventoPedido> getEventos() { return Collections.unmodifiableList(eventos); }
    public int getUnidadesEntregadas() { return unidadesEntregadas; }
    public double getHoraEntregaReal() { return horaEntregaReal; }
    public boolean tuvoEntregaTardia() { return entregaTardia; }
    public boolean estaCancelado() { return cancelado; }
    public boolean fueReasignado() { return reasignado; }
    public boolean estaReprogramado() { return reprogramado; }
    public EstadoPedido getEstado() { return estado; }

    private static String formatearHoras(double h) {
        return h == Math.rint(h) ? String.valueOf((long) h) : String.format("%.1f", h);
    }
}
