package pe.pucp.paqrap.flota;

import pe.pucp.paqrap.comun.Coordenada;
import pe.pucp.paqrap.comun.ParametrosOperacion;

import java.util.ArrayList;
import java.util.List;

/**
 * Vehiculo (diagrama de clases, Modulo D): «abstract» con capacidad, velocidad
 * y costo por km propios de cada especializacion (Auto/Moto/Bicicleta).
 *
 * - Codigo TTNN (P18): TA = auto, TM = moto, TB = bicicleta, NN = correlativo.
 * - La unidad opera las 24 horas; en el cambio de turno solo cambia el
 *   conductor (P12). La pausa de alimentacion de cada conductor se aplica con
 *   avanzar(), ver JornadaConductor.
 * - No esta disponible durante su mantenimiento preventivo (P19) y debe estar
 *   de vuelta en el almacen antes de que empiece.
 * - Tampoco esta disponible mientras dura una averia (D7, ver TipoAveria).
 * - Sale del almacen donde esta estacionada (almacenBase). Al inicio de
 *   cualquier escenario todas estan en el almacen central (P10); despues de
 *   una ruta pueden quedar en un almacen intermedio para recargar ahi.
 */
public abstract class Vehiculo {
    private final String idVehiculo;
    private final int correlativo;
    protected final ParametrosOperacion parametros;
    private final JornadaConductor jornada;
    private String estadoOperativo;
    private Almacen almacenBase;
    private final List<double[]> periodosMantenimiento = new ArrayList<>();
    private double indisponibleHasta = Double.NEGATIVE_INFINITY;

    protected Vehiculo(String idVehiculo, Almacen almacenBase, ParametrosOperacion parametros, JornadaConductor jornada) {
        this.idVehiculo = idVehiculo;
        this.correlativo = extraerCorrelativo(idVehiculo);
        this.almacenBase = almacenBase;
        this.parametros = parametros;
        this.jornada = jornada;
        this.estadoOperativo = "DISPONIBLE";
    }

    public abstract TipoVehiculo getTipo();
    public abstract int getCapacidadPaquetes();
    public abstract double getVelocidadPromedioKmH();
    public abstract double getCostoPorKm();

    /** +estaDisponible(): boolean (diagrama). */
    public boolean estaDisponible() {
        return "DISPONIBLE".equals(estadoOperativo);
    }

    public void setEstadoOperativo(String estadoOperativo) { this.estadoOperativo = estadoOperativo; }
    public String getEstadoOperativo() { return estadoOperativo; }

    /** +calcularTiempoViaje(distanciaKm): Duration (diagrama; se expresa en horas). */
    public double calcularTiempoViaje(double distanciaKm) {
        return distanciaKm / getVelocidadPromedioKmH();
    }

    /** +calcularCostoViaje(distanciaKm): double (diagrama). */
    public double calcularCostoViaje(double distanciaKm) {
        return distanciaKm * getCostoPorKm();
    }

    /** Hora en que termina un trabajo de duracionHoras que empieza en inicio, con las pausas de alimentacion. */
    public double avanzar(double inicio, double duracionHoras) {
        return jornada.avanzar(correlativo, inicio, duracionHoras);
    }

    // ------------------------------------------------------------------
    // Mantenimiento preventivo y averias
    // ------------------------------------------------------------------

    public void agregarMantenimiento(List<double[]> periodos) {
        periodosMantenimiento.addAll(periodos);
    }

    /** true si la unidad puede salir a la hora dada: no esta averiada ni en mantenimiento. */
    public boolean estaOperativa(double hora) {
        if (!estaDisponible()) return false;
        if (hora < indisponibleHasta) return false;
        return !estaEnMantenimiento(hora);
    }

    public boolean estaEnMantenimiento(double hora) {
        for (double[] p : periodosMantenimiento) {
            if (hora >= p[0] && hora < p[1]) return true;
        }
        return false;
    }

    /** Hora limite para estar de vuelta en el almacen si sale a la hora dada (inicio del siguiente mantenimiento). */
    public double limiteRetorno(double hora) {
        double limite = Double.POSITIVE_INFINITY;
        for (double[] p : periodosMantenimiento) {
            if (p[0] >= hora && p[0] < limite) limite = p[0];
        }
        return limite;
    }

    /** Fin del mantenimiento vigente en la hora dada (o la misma hora si no hay). */
    public double finMantenimiento(double hora) {
        for (double[] p : periodosMantenimiento) {
            if (hora >= p[0] && hora < p[1]) return p[1];
        }
        return hora;
    }

    public List<double[]> getPeriodosMantenimiento() { return periodosMantenimiento; }
    public void setIndisponibleHasta(double hora) { this.indisponibleHasta = hora; }
    public double getIndisponibleHasta() { return indisponibleHasta; }

    // ------------------------------------------------------------------
    // Ubicacion
    // ------------------------------------------------------------------

    /** Coordenada del almacen desde donde sale la unidad (punto de partida operativo, no fijado por el diagrama). */
    public Coordenada getAlmacenOrigen() { return almacenBase.getUbicacion(); }
    public Almacen getAlmacenBase() { return almacenBase; }
    public void setAlmacenBase(Almacen almacenBase) { this.almacenBase = almacenBase; }

    public String getIdVehiculo() { return idVehiculo; }
    public int getCorrelativo() { return correlativo; }
    public JornadaConductor getJornada() { return jornada; }

    private static int extraerCorrelativo(String codigo) {
        String digitos = codigo.replaceAll("\\D", "");
        return digitos.isEmpty() ? 1 : Integer.parseInt(digitos);
    }

    @Override
    public String toString() {
        return String.format("%s#%s[cap=%d, vel=%.0fkm/h, costo=S/%.2f/km, %s]",
                getTipo().getEtiqueta(), idVehiculo, getCapacidadPaquetes(),
                getVelocidadPromedioKmH(), getCostoPorKm(), estadoOperativo);
    }
}
