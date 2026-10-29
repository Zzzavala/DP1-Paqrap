package pe.pucp.paqrap.registro;

import pe.pucp.paqrap.comun.Coordenada;

/**
 * Pedido (diagrama de clases, Modulo A). Es la unidad con la que trabaja el
 * Planificador: identificacion, cliente (y por lo tanto ubicacion de entrega),
 * cantidad, tipo de entrega, hora de registro/limite y estado.
 *
 * Un pedido del cliente puede llegar al Planificador entero o en partes:
 * - entregas parciales (P13, P14): EntregasParciales divide un pedido que no
 *   entra completo en ninguna unidad libre;
 * - transbordo por averia y reasignacion de carga en transito (B10, B11).
 * Cada parte es otro objeto Pedido con el mismo idPedidoOrigen, el mismo
 * cliente y la misma hora limite comprometida.
 *
 * Hay dos horas limite:
 * - horaLimiteCompromiso: la comprometida con el cliente al registrar el
 *   pedido (A3). Nunca cambia y es la que mide el cumplimiento.
 * - horaLimite: la que usa el Planificador. Es igual a la de compromiso salvo
 *   que el pedido se reprograme (A21) porque ya no puede llegar a tiempo; en
 *   ese caso se le da un nuevo plazo operativo y el pedido queda registrado
 *   como incumplido.
 *
 * Se omiten nivelCriticidad, observaciones, horaEntregaReal y codigoSeguimiento:
 * se llevan a nivel del pedido del cliente en SeguimientoPedido.
 */
public class Pedido {
    private final String idPedido;
    private final String idPedidoOrigen; // igual a idPedido salvo en una parte
    private final Cliente cliente;
    private final int cantidadUnidades;
    private final TipoEntrega tipoEntrega;
    private final double plazoHoras;
    private final double horaRegistro;
    private final double horaLimiteCompromiso;
    private double horaLimite;
    private EstadoPedido estado;
    private boolean reprogramado;

    public Pedido(String idPedido, Cliente cliente, int cantidadUnidades, TipoEntrega tipoEntrega,
                  double plazoHoras, double horaRegistro) {
        this.idPedido = idPedido;
        this.idPedidoOrigen = idPedido;
        this.cliente = cliente;
        this.cantidadUnidades = cantidadUnidades;
        this.tipoEntrega = tipoEntrega;
        this.plazoHoras = plazoHoras;
        this.horaRegistro = horaRegistro;
        this.horaLimiteCompromiso = horaRegistro + plazoHoras;
        this.estado = EstadoPedido.REGISTRADO;
        calcularHoraLimite();
    }

    /**
     * Parte de un pedido (P13, transbordo o reasignacion): mismo cliente, mismo
     * tipo de entrega y mismas horas limite. Su id es el del pedido del que sale
     * mas un sufijo (PV2609-0001-1, PV2609-0001-T2).
     */
    public Pedido(Pedido deQueSale, String sufijo, int cantidadUnidades) {
        this.idPedido = deQueSale.idPedido + "-" + sufijo;
        this.idPedidoOrigen = deQueSale.idPedidoOrigen;
        this.cliente = deQueSale.cliente;
        this.cantidadUnidades = cantidadUnidades;
        this.tipoEntrega = deQueSale.tipoEntrega;
        this.plazoHoras = deQueSale.plazoHoras;
        this.horaRegistro = deQueSale.horaRegistro;
        this.horaLimiteCompromiso = deQueSale.horaLimiteCompromiso;
        this.horaLimite = deQueSale.horaLimite;
        this.reprogramado = deQueSale.reprogramado;
        this.estado = EstadoPedido.REGISTRADO;
    }

    /** +calcularHoraLimite() (diagrama). */
    public double calcularHoraLimite() {
        this.horaLimite = horaRegistro + plazoHoras;
        return this.horaLimite;
    }

    /** +calcularHolgura(): Duration (diagrama). Se expresa en horas restantes hasta el plazo comprometido. */
    public double calcularHolgura(double horaActual) {
        return horaLimiteCompromiso - horaActual;
    }

    /** Holgura restante como % del plazo comprometido (base de la semaforizacion, A19). */
    public double calcularHolguraPorcentaje(double horaActual) {
        return plazoHoras <= 0 ? 0.0 : 100.0 * calcularHolgura(horaActual) / plazoHoras;
    }

    /** +cambiarEstado(nuevo, responsable) (diagrama, simplificado sin bitacora: la bitacora esta en SeguimientoPedido). */
    public void cambiarEstado(EstadoPedido nuevo) {
        this.estado = nuevo;
    }

    /**
     * Reprogramacion (A21): el pedido ya no llega dentro del plazo comprometido;
     * el Planificador lo sigue atendiendo con un nuevo plazo operativo.
     */
    public void reprogramar(double nuevaHoraLimite) {
        this.horaLimite = nuevaHoraLimite;
        this.reprogramado = true;
    }

    public String getIdPedido() { return idPedido; }
    public String getIdPedidoOrigen() { return idPedidoOrigen; }
    public boolean esParte() { return !idPedido.equals(idPedidoOrigen); }
    public Cliente getCliente() { return cliente; }
    public Coordenada getUbicacion() { return cliente.getCoordenadas(); }
    public int getCantidadUnidades() { return cantidadUnidades; }
    public TipoEntrega getTipoEntrega() { return tipoEntrega; }
    public double getPlazoHoras() { return plazoHoras; }
    public double getHoraRegistro() { return horaRegistro; }
    /** Hora limite con la que planifica el algoritmo (igual a la comprometida salvo reprogramacion). */
    public double getHoraLimite() { return horaLimite; }
    public double getHoraLimiteCompromiso() { return horaLimiteCompromiso; }
    public EstadoPedido getEstado() { return estado; }
    public boolean estaReprogramado() { return reprogramado; }

    @Override
    public String toString() {
        return String.format("Pedido#%s[%s, %dud, %s, limite=%.1fh, %s]",
                idPedido, getUbicacion(), cantidadUnidades, tipoEntrega, horaLimite, estado);
    }
}
