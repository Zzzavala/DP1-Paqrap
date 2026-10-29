package pe.pucp.paqrap.comun;

import java.util.Arrays;

/**
 * Parametros de operacion de una corrida (P6, P9, P15, P16, P17 de Preguntas
 * y Respuestas; D1, D2, D3, D10, D11 de la Lista de Exigencias).
 *
 * En la version por linea de comandos estos valores se leian de
 * parametros.properties en una clase estatica. En el servidor pueden convivir
 * varias corridas a la vez (la visual y los experimentos), asi que cada corrida
 * tiene su propia instancia. Las velocidades se pueden cambiar con la corrida
 * en marcha (P6, P16): se marcan volatile y aplican desde la siguiente ventana
 * de planificacion. La flota solo se fija al iniciar la corrida (P9).
 *
 * Valores por defecto: los del enunciado (velocidades 40/25/12 km/h, costos
 * S/ 8/6/3 por km, capacidades 24/8/4 paquetes) y la flota de la P17
 * (10 autos, 15 motos, 12 bicicletas).
 */
public class ParametrosOperacion {

    private volatile double velocidadAuto = 40.0;
    private volatile double velocidadMoto = 25.0;
    private volatile double velocidadBicicleta = 12.0;

    private double costoKmAuto = 8.00;
    private double costoKmMoto = 6.00;
    private double costoKmBicicleta = 3.00;

    private int capacidadAuto = 24;
    private int capacidadMoto = 8;
    private int capacidadBicicleta = 4;

    private int cantidadAutos = 10;
    private int cantidadMotos = 15;
    private int cantidadBicicletas = 12;

    private int capacidadAlmacenIntermedio = 1000;
    private int[] horasCambioTurno = {7, 15, 23};
    private double duracionAlimentacionH = 1.0;
    private double tiempoServicioH = 1.0;

    private int plazoRegularH = 36;
    private int plazoPriorizada4H = 4;
    private int plazoPriorizada8H = 8;
    private int plazoPriorizada12H = 12;
    private int plazoPriorizada18H = 18;

    private int anchoCiudadKm = 70;
    private int altoCiudadKm = 50;

    // Almacenes de la hoja Aux-Mapa (P5, actualizacion del 08-sep-2026).
    private double almacenCentralX = 27;
    private double almacenCentralY = 14;
    private double almacenNorOesteX = 12;
    private double almacenNorOesteY = 38;
    private double almacenEsteX = 57;
    private double almacenEsteY = 27;

    /** Copia independiente (cada corrida trabaja sobre su propia copia). */
    public ParametrosOperacion copia() {
        ParametrosOperacion c = new ParametrosOperacion();
        c.velocidadAuto = velocidadAuto;
        c.velocidadMoto = velocidadMoto;
        c.velocidadBicicleta = velocidadBicicleta;
        c.costoKmAuto = costoKmAuto;
        c.costoKmMoto = costoKmMoto;
        c.costoKmBicicleta = costoKmBicicleta;
        c.capacidadAuto = capacidadAuto;
        c.capacidadMoto = capacidadMoto;
        c.capacidadBicicleta = capacidadBicicleta;
        c.cantidadAutos = cantidadAutos;
        c.cantidadMotos = cantidadMotos;
        c.cantidadBicicletas = cantidadBicicletas;
        c.capacidadAlmacenIntermedio = capacidadAlmacenIntermedio;
        c.horasCambioTurno = Arrays.copyOf(horasCambioTurno, horasCambioTurno.length);
        c.duracionAlimentacionH = duracionAlimentacionH;
        c.tiempoServicioH = tiempoServicioH;
        c.plazoRegularH = plazoRegularH;
        c.plazoPriorizada4H = plazoPriorizada4H;
        c.plazoPriorizada8H = plazoPriorizada8H;
        c.plazoPriorizada12H = plazoPriorizada12H;
        c.plazoPriorizada18H = plazoPriorizada18H;
        c.anchoCiudadKm = anchoCiudadKm;
        c.altoCiudadKm = altoCiudadKm;
        c.almacenCentralX = almacenCentralX;
        c.almacenCentralY = almacenCentralY;
        c.almacenNorOesteX = almacenNorOesteX;
        c.almacenNorOesteY = almacenNorOesteY;
        c.almacenEsteX = almacenEsteX;
        c.almacenEsteY = almacenEsteY;
        return c;
    }

    /**
     * Corrige valores fuera de rango (velocidades o capacidades no positivas,
     * turnos mal formados) volviendo al valor por defecto, para que una corrida
     * nunca arranque con un parametro que rompa el ruteo.
     */
    public void normalizar() {
        ParametrosOperacion d = new ParametrosOperacion();
        if (velocidadAuto <= 0) velocidadAuto = d.velocidadAuto;
        if (velocidadMoto <= 0) velocidadMoto = d.velocidadMoto;
        if (velocidadBicicleta <= 0) velocidadBicicleta = d.velocidadBicicleta;
        if (costoKmAuto < 0) costoKmAuto = d.costoKmAuto;
        if (costoKmMoto < 0) costoKmMoto = d.costoKmMoto;
        if (costoKmBicicleta < 0) costoKmBicicleta = d.costoKmBicicleta;
        if (capacidadAuto <= 0) capacidadAuto = d.capacidadAuto;
        if (capacidadMoto <= 0) capacidadMoto = d.capacidadMoto;
        if (capacidadBicicleta <= 0) capacidadBicicleta = d.capacidadBicicleta;
        cantidadAutos = Math.max(0, Math.min(200, cantidadAutos));
        cantidadMotos = Math.max(0, Math.min(200, cantidadMotos));
        cantidadBicicletas = Math.max(0, Math.min(200, cantidadBicicletas));
        if (cantidadAutos + cantidadMotos + cantidadBicicletas == 0) {
            cantidadAutos = d.cantidadAutos;
            cantidadMotos = d.cantidadMotos;
            cantidadBicicletas = d.cantidadBicicletas;
        }
        if (capacidadAlmacenIntermedio <= 0) capacidadAlmacenIntermedio = d.capacidadAlmacenIntermedio;
        if (horasCambioTurno == null || horasCambioTurno.length != 3) horasCambioTurno = d.horasCambioTurno;
        if (duracionAlimentacionH < 0 || duracionAlimentacionH > 3) duracionAlimentacionH = d.duracionAlimentacionH;
        if (tiempoServicioH < 0 || tiempoServicioH > 4) tiempoServicioH = d.tiempoServicioH;
        if (plazoRegularH <= 0) plazoRegularH = d.plazoRegularH;
        if (plazoPriorizada4H <= 0) plazoPriorizada4H = d.plazoPriorizada4H;
        if (plazoPriorizada8H <= 0) plazoPriorizada8H = d.plazoPriorizada8H;
        if (plazoPriorizada12H <= 0) plazoPriorizada12H = d.plazoPriorizada12H;
        if (plazoPriorizada18H <= 0) plazoPriorizada18H = d.plazoPriorizada18H;
        if (anchoCiudadKm <= 0) anchoCiudadKm = d.anchoCiudadKm;
        if (altoCiudadKm <= 0) altoCiudadKm = d.altoCiudadKm;
    }

    /** La mayor de las tres velocidades. */
    public double velocidadMaxima() {
        return Math.max(velocidadAuto, Math.max(velocidadMoto, velocidadBicicleta));
    }

    public String describirVelocidades() {
        return String.format("auto %.0f km/h, moto %.0f km/h, bicicleta %.0f km/h",
                velocidadAuto, velocidadMoto, velocidadBicicleta);
    }

    public Coordenada getUbicacionAlmacenCentral() { return new Coordenada(almacenCentralX, almacenCentralY); }
    public Coordenada getUbicacionAlmacenNorOeste() { return new Coordenada(almacenNorOesteX, almacenNorOesteY); }
    public Coordenada getUbicacionAlmacenEste() { return new Coordenada(almacenEsteX, almacenEsteY); }

    public double getVelocidadAuto() { return velocidadAuto; }
    public void setVelocidadAuto(double velocidadAuto) { this.velocidadAuto = velocidadAuto; }
    public double getVelocidadMoto() { return velocidadMoto; }
    public void setVelocidadMoto(double velocidadMoto) { this.velocidadMoto = velocidadMoto; }
    public double getVelocidadBicicleta() { return velocidadBicicleta; }
    public void setVelocidadBicicleta(double velocidadBicicleta) { this.velocidadBicicleta = velocidadBicicleta; }
    public double getCostoKmAuto() { return costoKmAuto; }
    public void setCostoKmAuto(double costoKmAuto) { this.costoKmAuto = costoKmAuto; }
    public double getCostoKmMoto() { return costoKmMoto; }
    public void setCostoKmMoto(double costoKmMoto) { this.costoKmMoto = costoKmMoto; }
    public double getCostoKmBicicleta() { return costoKmBicicleta; }
    public void setCostoKmBicicleta(double costoKmBicicleta) { this.costoKmBicicleta = costoKmBicicleta; }
    public int getCapacidadAuto() { return capacidadAuto; }
    public void setCapacidadAuto(int capacidadAuto) { this.capacidadAuto = capacidadAuto; }
    public int getCapacidadMoto() { return capacidadMoto; }
    public void setCapacidadMoto(int capacidadMoto) { this.capacidadMoto = capacidadMoto; }
    public int getCapacidadBicicleta() { return capacidadBicicleta; }
    public void setCapacidadBicicleta(int capacidadBicicleta) { this.capacidadBicicleta = capacidadBicicleta; }
    public int getCantidadAutos() { return cantidadAutos; }
    public void setCantidadAutos(int cantidadAutos) { this.cantidadAutos = cantidadAutos; }
    public int getCantidadMotos() { return cantidadMotos; }
    public void setCantidadMotos(int cantidadMotos) { this.cantidadMotos = cantidadMotos; }
    public int getCantidadBicicletas() { return cantidadBicicletas; }
    public void setCantidadBicicletas(int cantidadBicicletas) { this.cantidadBicicletas = cantidadBicicletas; }
    public int getCapacidadAlmacenIntermedio() { return capacidadAlmacenIntermedio; }
    public void setCapacidadAlmacenIntermedio(int capacidadAlmacenIntermedio) { this.capacidadAlmacenIntermedio = capacidadAlmacenIntermedio; }
    public int[] getHorasCambioTurno() { return horasCambioTurno; }
    public void setHorasCambioTurno(int[] horasCambioTurno) { this.horasCambioTurno = horasCambioTurno; }
    public double getDuracionAlimentacionH() { return duracionAlimentacionH; }
    public void setDuracionAlimentacionH(double duracionAlimentacionH) { this.duracionAlimentacionH = duracionAlimentacionH; }
    public double getTiempoServicioH() { return tiempoServicioH; }
    public void setTiempoServicioH(double tiempoServicioH) { this.tiempoServicioH = tiempoServicioH; }
    public int getPlazoRegularH() { return plazoRegularH; }
    public void setPlazoRegularH(int plazoRegularH) { this.plazoRegularH = plazoRegularH; }
    public int getPlazoPriorizada4H() { return plazoPriorizada4H; }
    public void setPlazoPriorizada4H(int plazoPriorizada4H) { this.plazoPriorizada4H = plazoPriorizada4H; }
    public int getPlazoPriorizada8H() { return plazoPriorizada8H; }
    public void setPlazoPriorizada8H(int plazoPriorizada8H) { this.plazoPriorizada8H = plazoPriorizada8H; }
    public int getPlazoPriorizada12H() { return plazoPriorizada12H; }
    public void setPlazoPriorizada12H(int plazoPriorizada12H) { this.plazoPriorizada12H = plazoPriorizada12H; }
    public int getPlazoPriorizada18H() { return plazoPriorizada18H; }
    public void setPlazoPriorizada18H(int plazoPriorizada18H) { this.plazoPriorizada18H = plazoPriorizada18H; }
    public int getAnchoCiudadKm() { return anchoCiudadKm; }
    public void setAnchoCiudadKm(int anchoCiudadKm) { this.anchoCiudadKm = anchoCiudadKm; }
    public int getAltoCiudadKm() { return altoCiudadKm; }
    public void setAltoCiudadKm(int altoCiudadKm) { this.altoCiudadKm = altoCiudadKm; }
    public double getAlmacenCentralX() { return almacenCentralX; }
    public void setAlmacenCentralX(double almacenCentralX) { this.almacenCentralX = almacenCentralX; }
    public double getAlmacenCentralY() { return almacenCentralY; }
    public void setAlmacenCentralY(double almacenCentralY) { this.almacenCentralY = almacenCentralY; }
    public double getAlmacenNorOesteX() { return almacenNorOesteX; }
    public void setAlmacenNorOesteX(double almacenNorOesteX) { this.almacenNorOesteX = almacenNorOesteX; }
    public double getAlmacenNorOesteY() { return almacenNorOesteY; }
    public void setAlmacenNorOesteY(double almacenNorOesteY) { this.almacenNorOesteY = almacenNorOesteY; }
    public double getAlmacenEsteX() { return almacenEsteX; }
    public void setAlmacenEsteX(double almacenEsteX) { this.almacenEsteX = almacenEsteX; }
    public double getAlmacenEsteY() { return almacenEsteY; }
    public void setAlmacenEsteY(double almacenEsteY) { this.almacenEsteY = almacenEsteY; }
}
