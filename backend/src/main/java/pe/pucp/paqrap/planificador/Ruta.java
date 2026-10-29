package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.flota.Vehiculo;
import pe.pucp.paqrap.registro.Pedido;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Secuencia de paradas asignada a un vehiculo (diagrama, Modulo B):
 * -idRuta, -vehiculoAsignado, -paradas: List<ParadaRuta>, -distanciaRecorrida, -costoRuta.
 * +agregarParada(p), +calcularTiempoTotal(): Duration.
 *
 * La ruta sale del almacen donde esta la unidad (getAlmacenOrigen()).
 */
public class Ruta {
    private final String idRuta;
    private final Vehiculo vehiculoAsignado;
    private final double tiempoServicioH;
    private List<ParadaRuta> paradas;
    private double distanciaRecorrida;
    private double distanciaSinBloqueos; // misma secuencia de paradas si no hubiera calles bloqueadas
    private double horaInicio;
    private double horaFinEntregas;      // hora en que termina la ultima entrega
    private double costoRuta;

    public Ruta(String idRuta, Vehiculo vehiculoAsignado, double tiempoServicioH) {
        this.idRuta = idRuta;
        this.vehiculoAsignado = vehiculoAsignado;
        this.tiempoServicioH = tiempoServicioH;
        this.paradas = new ArrayList<>();
    }

    /** +agregarParada(p: ParadaRuta) (diagrama). */
    public void agregarParada(ParadaRuta p) { paradas.add(p); }

    public List<Pedido> getPedidosEnOrden() {
        return paradas.stream().map(ParadaRuta::getPedidoAsociado).collect(Collectors.toList());
    }

    /**
     * Reconstruye las paradas para un orden de pedidos dado, recalculando hora
     * estimada de llegada, distancia recorrida y costo sobre la RedVial.
     * Cada tramo se mide con los bloqueos vigentes a la hora en que el vehiculo
     * sale hacia esa parada.
     *
     * @param horaInicio hora absoluta en que el vehiculo sale del almacen a repartir esta ruta.
     */
    public void establecerOrden(List<Pedido> ordenPedidos, RedVial redVial, double horaInicio) {
        List<ParadaRuta> nuevasParadas = new ArrayList<>();
        double horaActual = horaInicio;
        Coordenada posicionActual = vehiculoAsignado.getAlmacenOrigen();
        double distanciaTotal = 0.0;
        double distanciaLibre = 0.0;
        int secuencia = 1;
        for (Pedido p : ordenPedidos) {
            double tramo = redVial.calcularDistancia(posicionActual, p.getUbicacion(), horaActual);
            double libre = redVial.calcularDistancia(posicionActual, p.getUbicacion());
            if (Double.isInfinite(tramo)) tramo = libre; // destino encerrado: se toma el camino sin bloqueos
            distanciaLibre += libre;
            distanciaTotal += tramo;
            horaActual = vehiculoAsignado.avanzar(horaActual, vehiculoAsignado.calcularTiempoViaje(tramo));
            nuevasParadas.add(new ParadaRuta(secuencia++, p, horaActual, tiempoServicioH));
            horaActual = vehiculoAsignado.avanzar(horaActual, tiempoServicioH); // entrega de 1 hora (B7), con pausa de alimentacion si toca
            posicionActual = p.getUbicacion();
        }
        this.horaInicio = horaInicio;
        this.horaFinEntregas = horaActual;
        this.paradas = nuevasParadas;
        this.distanciaRecorrida = distanciaTotal;
        this.distanciaSinBloqueos = distanciaLibre;
        this.costoRuta = vehiculoAsignado.calcularCostoViaje(distanciaTotal);
    }

    /** +calcularTiempoTotal(): Duration (diagrama; hora simulada en que termina la ultima entrega). */
    public double calcularTiempoTotal() {
        if (paradas.isEmpty()) return 0.0;
        return horaFinEntregas;
    }

    /** Hora en que la unidad esta de vuelta en su almacen despues de la ultima entrega (con bloqueos y alimentacion). */
    public double calcularHoraRegreso(RedVial redVial) {
        return calcularHoraRegreso(redVial, vehiculoAsignado.getAlmacenOrigen());
    }

    /** Hora en que la unidad llega al almacen indicado despues de la ultima entrega. */
    public double calcularHoraRegreso(RedVial redVial, Coordenada almacenDestino) {
        if (paradas.isEmpty()) return horaInicio;
        Coordenada ultima = paradas.get(paradas.size() - 1).getPedidoAsociado().getUbicacion();
        double salida = calcularTiempoTotal();
        double regreso = redVial.calcularDistancia(ultima, almacenDestino, salida);
        if (Double.isInfinite(regreso)) regreso = redVial.calcularDistancia(ultima, almacenDestino);
        return vehiculoAsignado.avanzar(salida, vehiculoAsignado.calcularTiempoViaje(regreso));
    }

    public int contarPedidosFueraDePlazo() {
        int c = 0;
        for (ParadaRuta p : paradas) {
            if (p.getHoraEstimadaLlegada() > p.getPedidoAsociado().getHoraLimite() + 1e-9) c++;
        }
        return c;
    }

    /** Paquetes que lleva la unidad al salir. */
    public int getCantidadTotal() {
        int total = 0;
        for (ParadaRuta p : paradas) total += p.getPedidoAsociado().getCantidadUnidades();
        return total;
    }

    public String getIdRuta() { return idRuta; }
    public Vehiculo getVehiculoAsignado() { return vehiculoAsignado; }
    public List<ParadaRuta> getParadas() { return paradas; }
    public double getDistanciaRecorrida() { return distanciaRecorrida; }
    /** Kilometros extra que esta ruta recorre para rodear calles bloqueadas. */
    public double getDesvioPorBloqueos() { return distanciaRecorrida - distanciaSinBloqueos; }
    public double getCostoRuta() { return costoRuta; }
    public double getHoraInicio() { return horaInicio; }
}
