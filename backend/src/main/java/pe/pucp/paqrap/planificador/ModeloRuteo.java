package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.flota.Almacen;
import pe.pucp.paqrap.flota.Turno;
import pe.pucp.paqrap.flota.Vehiculo;
import pe.pucp.paqrap.registro.Pedido;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsula pedidos, flota, almacenes y restricciones como entrada del
 * algoritmo (diagrama, Modulo B): -pedidos, -flotaDisponible, -almacenes,
 * -redVial, -restriccionesTurno, +construirRestricciones().
 *
 * Se agregan dos datos que el diagrama no fija y el ruteo necesita:
 * - horaPlanificacion: instante de la ventana de planificacion (unidades
 *   operativas, bloqueos vigentes y hora de salida de las rutas);
 * - stockDisponible: unidades que todavia puede despachar cada almacen en esta
 *   ventana (B16). Una unidad estacionada en un almacen intermedio solo puede
 *   cargar lo que ese almacen tenga; el central no tiene limite.
 */
public class ModeloRuteo {
    private final List<Pedido> pedidos;
    private final List<Vehiculo> flotaDisponible;
    private final List<Almacen> almacenes;
    private final RedVial redVial;
    private final List<Turno> restriccionesTurno;
    private final double horaPlanificacion;
    private final double tiempoServicioH;
    private final double ventanaPlanificacionH;
    private final Map<String, Integer> stockDisponible = new HashMap<>();

    public ModeloRuteo(List<Pedido> pedidos, List<Vehiculo> flotaDisponible, List<Almacen> almacenes,
                       RedVial redVial, List<Turno> restriccionesTurno, double horaPlanificacion,
                       double tiempoServicioH, double ventanaPlanificacionH) {
        this.pedidos = pedidos;
        this.flotaDisponible = flotaDisponible;
        this.almacenes = almacenes;
        this.redVial = redVial;
        this.restriccionesTurno = restriccionesTurno;
        this.horaPlanificacion = horaPlanificacion;
        this.tiempoServicioH = tiempoServicioH;
        this.ventanaPlanificacionH = ventanaPlanificacionH;
        for (Almacen a : almacenes) stockDisponible.put(a.getIdAlmacen(), a.getInventarioActual());
        construirRestricciones();
    }

    /**
     * +construirRestricciones() (diagrama). Deja en la flota solo las unidades
     * disponibles y operativas a la hora de planificacion; el mantenimiento
     * (P19) y la hora de alimentacion se aplican directamente en el algoritmo
     * al estimar tiempos de viaje.
     */
    public void construirRestricciones() {
        flotaDisponible.removeIf(v -> !v.estaDisponible() || !v.estaOperativa(horaPlanificacion));
    }

    /** Stock que puede cargar una unidad en el almacen donde esta. */
    public int stockPara(Vehiculo v) {
        return stockDisponible.getOrDefault(v.getAlmacenBase().getIdAlmacen(), 0);
    }

    public Map<String, Integer> getStockDisponible() { return stockDisponible; }
    public List<Pedido> getPedidos() { return pedidos; }
    public List<Vehiculo> getFlotaDisponible() { return flotaDisponible; }
    public List<Almacen> getAlmacenes() { return almacenes; }
    public RedVial getRedVial() { return redVial; }
    public List<Turno> getRestriccionesTurno() { return restriccionesTurno; }
    public double getHoraPlanificacion() { return horaPlanificacion; }
    public double getTiempoServicioH() { return tiempoServicioH; }
    public double getVentanaPlanificacionH() { return ventanaPlanificacionH; }

    /** Copia del stock disponible por almacen (cada construccion de solucion descuenta sobre su copia). */
    public Map<String, Integer> copiarStock() { return new HashMap<>(stockDisponible); }
}
