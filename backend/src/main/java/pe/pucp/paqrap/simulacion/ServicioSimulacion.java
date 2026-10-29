package pe.pucp.paqrap.simulacion;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.pucp.paqrap.comun.ConversorTiempo;
import pe.pucp.paqrap.flota.TipoAveria;
import pe.pucp.paqrap.persistencia.ServicioPersistencia;
import pe.pucp.paqrap.registro.CanalPedido;
import pe.pucp.paqrap.registro.LectorVentas.RegistroVenta;
import pe.pucp.paqrap.registro.SeguimientoPedido;
import pe.pucp.paqrap.registro.TipoEntrega;
import pe.pucp.paqrap.visualizador.DetalleVisual;
import pe.pucp.paqrap.visualizador.EstadoVisual;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Corrida visual del sistema (una a la vez, compartida por todos los
 * dispositivos conectados, C5): la inicia o detiene cualquier usuario desde
 * V-1/V-2 y todos ven el mismo reloj, la misma flota y los mismos pedidos.
 * Los comandos (bloquear un tramo, registrar un pedido o una averia) se
 * aplican sobre esa corrida en el instante simulado actual.
 */
@Service
public class ServicioSimulacion {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioSimulacion.class);
    private static final DateTimeFormatter ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final FuenteDatos fuente;
    private final ServicioPersistencia persistencia;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "paqrap-corrida");
        t.setDaemon(true);
        return t;
    });

    private volatile Corrida actual;
    private volatile EstadoVisual estadoInicial;

    public ServicioSimulacion(FuenteDatos fuente, ServicioPersistencia persistencia) {
        this.fuente = fuente;
        this.persistencia = persistencia;
    }

    // ------------------------------------------------------------------
    // Ciclo de vida de la corrida
    // ------------------------------------------------------------------

    public synchronized EstadoVisual iniciar(ParametrosCorrida solicitud) {
        if (actual != null && actual.estaEnCurso()) {
            throw new IllegalStateException("Ya hay una corrida en curso. Deténgala antes de iniciar otra.");
        }
        ParametrosCorrida p = solicitud == null ? new ParametrosCorrida() : solicitud.copia();
        p.normalizar();
        String id = "C-" + LocalDateTime.now(ParametrosCorrida.ZONA_LIMA).format(ID) + "-"
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xffff)).toUpperCase(Locale.ROOT);
        DatosCorrida datos = fuente.datosParaCorrida();
        MotorSimulacion motor = new MotorSimulacion(id, p, datos, persistencia.oyentePara(id));
        persistencia.iniciarCorrida(id, p, Instant.now());
        Corrida corrida = new Corrida(motor, (c, cierre) -> persistencia.finalizarCorrida(cierre, Instant.ofEpochMilli(c.getFinRealMs())));
        actual = corrida;
        hilo.submit(corrida);
        LOG.info("Corrida {} iniciada: {} con {} desde {} (factor {}x)", id, p.getEscenario(), p.getAlgoritmo(),
                p.getFechaInicio(), String.format(Locale.ROOT, "%.1f", p.factorAceleracion()));
        return corrida.getUltimoEstado();
    }

    /** Detiene la corrida en curso y devuelve su cierre (V-3). */
    public CierreCorrida detener() {
        Corrida c = actual;
        if (c == null) throw new NoSuchElementException("No hay ninguna corrida iniciada.");
        if (c.estaEnCurso()) {
            c.solicitarDetencion();
            long limite = System.currentTimeMillis() + 15_000;
            while (c.getCierre() == null && System.currentTimeMillis() < limite) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (c.getCierre() == null) throw new IllegalStateException("La corrida todavía se está cerrando; vuelva a intentarlo en unos segundos.");
        return c.getCierre();
    }

    /** Estado para el visualizador (si no hay corrida, la flota en el almacen central y sin pedidos). */
    public EstadoVisual estado() {
        Corrida c = actual;
        if (c != null && c.getUltimoEstado() != null) return c.getUltimoEstado();
        return estadoSinCorrida();
    }

    private EstadoVisual estadoSinCorrida() {
        EstadoVisual e = estadoInicial;
        if (e == null) {
            ParametrosCorrida p = new ParametrosCorrida();
            p.normalizar();
            MotorSimulacion vista = new MotorSimulacion("SIN_CORRIDA", p, fuente.datosParaCorrida(), null);
            e = vista.construirEstadoVisual("SIN_CORRIDA", 0, 0);
            estadoInicial = e;
        }
        return e;
    }

    public CierreCorrida cierre() {
        Corrida c = actual;
        if (c == null) throw new NoSuchElementException("No hay ninguna corrida iniciada.");
        if (c.getCierre() != null) return c.getCierre();
        // vista previa del cierre con el estado actual
        return c.getMotor().generarCierre(c.getEstado(), c.getInicioRealMs(), System.currentTimeMillis());
    }

    // ------------------------------------------------------------------
    // Consultas de detalle
    // ------------------------------------------------------------------

    public DetalleVisual.Vehiculo detalleVehiculo(String id) {
        DetalleVisual.Vehiculo d = motor().detalleVehiculo(id.toUpperCase(Locale.ROOT));
        if (d == null) throw new NoSuchElementException("No existe la unidad " + id + ".");
        return d;
    }

    public DetalleVisual.Pedido detallePedido(String id) {
        DetalleVisual.Pedido d = motor().detallePedido(id.trim());
        if (d == null) throw new NoSuchElementException("No se encontró ningún pedido con ID \"" + id + "\".");
        return d;
    }

    public DetalleVisual.Almacen detalleAlmacen(String id) {
        DetalleVisual.Almacen d = motor().detalleAlmacen(id);
        if (d == null) throw new NoSuchElementException("No existe el almacén " + id + ".");
        return d;
    }

    public List<MotorSimulacion.FilaPedido> buscarPedidos(String texto, String estado, String tipo, LocalDateTime desde,
                                                          LocalDateTime hasta, int limite) {
        Corrida c = actual;
        if (c == null) return List.of();
        return c.getMotor().buscarPedidos(texto, estado, tipo, desde, hasta, Math.max(1, Math.min(2000, limite)));
    }

    public String exportarPedidosCsv() {
        return motor().exportarPedidosCsv();
    }

    // ------------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------------

    public boolean alternarBloqueo(int x1, int y1, int x2, int y2) {
        Corrida c = corridaEnCurso();
        boolean r = c.getMotor().alternarBloqueoTramo(x1, y1, x2, y2);
        c.refrescar();
        return r;
    }

    public DetalleVisual.Pedido registrarPedido(String idCliente, String nombre, int x, int y, int cantidad, String tipo,
                                                String canal, String observaciones) {
        Corrida c = corridaEnCurso();
        TipoEntrega t = parsearTipo(tipo);
        CanalPedido ca = parsearCanal(canal);
        SeguimientoPedido s = c.getMotor().registrarPedido(idCliente, nombre, x, y, cantidad, t, ca, observaciones);
        c.refrescar();
        return c.getMotor().detallePedido(s.getIdPedido());
    }

    public int cargarLote(List<RegistroVenta> registros) {
        Corrida c = corridaEnCurso();
        int n = c.getMotor().cargarPedidosEnLote(registros);
        c.refrescar();
        return n;
    }

    public DetalleVisual.Pedido editarPedido(String id, Integer cantidad, String tipo, String observaciones) {
        Corrida c = corridaEnCurso();
        TipoEntrega t = tipo == null || tipo.isBlank() ? null : parsearTipo(tipo);
        SeguimientoPedido s = c.getMotor().editarPedido(id, cantidad, t, observaciones);
        c.refrescar();
        return c.getMotor().detallePedido(s.getIdPedido());
    }

    public DetalleVisual.Pedido cancelarPedido(String id, String motivo) {
        Corrida c = corridaEnCurso();
        SeguimientoPedido s = c.getMotor().cancelarPedido(id, motivo);
        c.refrescar();
        return c.getMotor().detallePedido(s.getIdPedido());
    }

    public void registrarAveria(String vehiculo, int tipo, String causa) {
        Corrida c = corridaEnCurso();
        TipoAveria t = TipoAveria.desdeNumero(tipo);
        if (t == null) throw new IllegalArgumentException("El tipo de avería debe ser 1, 2 o 3.");
        c.getMotor().registrarAveria(vehiculo.toUpperCase(Locale.ROOT), t, causa);
        c.refrescar();
    }

    public void actualizarUmbrales(double verde, double ambar) {
        Corrida c = actual;
        if (c == null) throw new NoSuchElementException("No hay ninguna corrida iniciada.");
        c.getMotor().actualizarUmbrales(verde, ambar);
        c.refrescar();
    }

    public void actualizarVelocidades(Double auto, Double moto, Double bicicleta) {
        Corrida c = corridaEnCurso();
        c.getMotor().actualizarVelocidades(auto, moto, bicicleta);
        c.refrescar();
    }

    /** Mes al que se asocia un archivo subido sin mes en el nombre: el de la corrida en curso o el de hoy. */
    public java.time.YearMonth mesDeReferencia() {
        Corrida c = actual;
        if (c != null) return ConversorTiempo.mesDe(c.getMotor().getHoraActual());
        return java.time.YearMonth.from(ParametrosCorrida.INICIO_POR_DEFECTO_5D);
    }

    private Corrida corridaEnCurso() {
        Corrida c = actual;
        if (c == null || !c.estaEnCurso()) throw new IllegalStateException("No hay ninguna corrida en curso.");
        return c;
    }

    private MotorSimulacion motor() {
        Corrida c = actual;
        if (c == null) throw new NoSuchElementException("No hay ninguna corrida iniciada.");
        return c.getMotor();
    }

    private static TipoEntrega parsearTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) return TipoEntrega.REGULAR_36H;
        try {
            return TipoEntrega.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            try {
                TipoEntrega t = TipoEntrega.desdePlazo(Integer.parseInt(tipo.trim()));
                if (t != null) return t;
            } catch (NumberFormatException ignorada) {
                // sigue al error
            }
            throw new IllegalArgumentException("Tipo de entrega desconocido: " + tipo);
        }
    }

    private static CanalPedido parsearCanal(String canal) {
        if (canal == null || canal.isBlank()) return CanalPedido.WEB;
        try {
            return CanalPedido.valueOf(canal.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CanalPedido.WEB;
        }
    }

    @PreDestroy
    public void apagar() {
        Corrida c = actual;
        if (c != null && c.estaEnCurso()) c.solicitarDetencion();
        hilo.shutdown();
        try {
            hilo.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
