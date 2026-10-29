package pe.pucp.paqrap.persistencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pe.pucp.paqrap.comun.ConversorTiempo;
import pe.pucp.paqrap.simulacion.CierreCorrida;
import pe.pucp.paqrap.simulacion.EventoSimulacion;
import pe.pucp.paqrap.simulacion.OyenteCorrida;
import pe.pucp.paqrap.simulacion.ParametrosCorrida;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persistencia de las corridas (B25, D24): el motor de simulacion no espera a
 * la base de datos; las rutas y eventos se encolan y se graban por lotes cada
 * dos segundos. Por defecto la base es H2 en archivo (carpeta de datos del
 * servicio); en la nube se puede apuntar a PostgreSQL con las variables
 * SPRING_DATASOURCE_*.
 */
@Service
public class ServicioPersistencia {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioPersistencia.class);
    private static final int MAXIMO_EN_COLA = 200_000;

    private final CorridaRepositorio corridas;
    private final RutaHistorialRepositorio rutas;
    private final EventoCorridaRepositorio eventos;
    private final ExperimentoRepositorio experimentos;
    private final ObjectMapper json;

    private final ConcurrentLinkedQueue<RutaHistorialEntidad> colaRutas = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<EventoCorridaEntidad> colaEventos = new ConcurrentLinkedQueue<>();
    private final AtomicInteger enCola = new AtomicInteger();

    public ServicioPersistencia(CorridaRepositorio corridas, RutaHistorialRepositorio rutas, EventoCorridaRepositorio eventos,
                                ExperimentoRepositorio experimentos, ObjectMapper json) {
        this.corridas = corridas;
        this.rutas = rutas;
        this.eventos = eventos;
        this.experimentos = experimentos;
        this.json = json;
    }

    /** Oyente que encola lo que produce una corrida para grabarlo en segundo plano. */
    public OyenteCorrida oyentePara(String idCorrida) {
        return new OyenteCorrida() {
            @Override
            public void rutaRegistrada(RegistroRuta r) {
                if (enCola.get() >= MAXIMO_EN_COLA) return;
                RutaHistorialEntidad e = new RutaHistorialEntidad();
                e.setCorridaId(idCorrida);
                e.setIdRuta(r.idRuta());
                e.setVehiculo(r.vehiculo());
                e.setCausa(r.causa());
                e.setHoraSalida(ConversorTiempo.aFecha(r.horaSalida()));
                e.setHoraRegreso(ConversorTiempo.aFecha(r.horaRegreso()));
                e.setAlmacenSalida(r.almacenSalida());
                e.setAlmacenRetorno(r.almacenRetorno());
                e.setPedidos(r.pedidos());
                e.setUnidades(r.unidades());
                e.setKm(r.km());
                e.setCosto(r.costo());
                colaRutas.add(e);
                enCola.incrementAndGet();
            }

            @Override
            public void eventoRegistrado(EventoSimulacion ev) {
                if (enCola.get() >= MAXIMO_EN_COLA) return;
                EventoCorridaEntidad e = new EventoCorridaEntidad();
                e.setCorridaId(idCorrida);
                e.setHoraSim(ConversorTiempo.aFecha(ev.hora()));
                e.setTipo(ev.tipo());
                e.setVehiculo(ev.vehiculo());
                e.setPedido(ev.pedido());
                String d = ev.detalle();
                e.setDetalle(d != null && d.length() > 1990 ? d.substring(0, 1990) : d);
                colaEventos.add(e);
                enCola.incrementAndGet();
            }
        };
    }

    /** Graba por lotes lo que este en cola. */
    @Scheduled(fixedDelay = 2000)
    public void volcar() {
        try {
            List<RutaHistorialEntidad> lote = new ArrayList<>();
            RutaHistorialEntidad r;
            while (lote.size() < 2000 && (r = colaRutas.poll()) != null) lote.add(r);
            if (!lote.isEmpty()) {
                rutas.saveAll(lote);
                enCola.addAndGet(-lote.size());
            }
            List<EventoCorridaEntidad> loteEventos = new ArrayList<>();
            EventoCorridaEntidad e;
            while (loteEventos.size() < 2000 && (e = colaEventos.poll()) != null) loteEventos.add(e);
            if (!loteEventos.isEmpty()) {
                eventos.saveAll(loteEventos);
                enCola.addAndGet(-loteEventos.size());
            }
        } catch (RuntimeException ex) {
            LOG.warn("No se pudo grabar el historial de la corrida: {}", ex.getMessage());
        }
    }

    public void iniciarCorrida(String id, ParametrosCorrida p, Instant inicioReal) {
        try {
            CorridaEntidad c = new CorridaEntidad();
            c.setId(id);
            c.setEscenario(p.getEscenario().name());
            c.setAlgoritmo(p.getAlgoritmo());
            c.setSemilla(p.getSemilla());
            c.setFechaInicioSim(p.getFechaInicio());
            c.setInicioReal(inicioReal);
            c.setEstado("EN_CURSO");
            c.setParametrosJson(json.writeValueAsString(p));
            corridas.save(c);
        } catch (Exception ex) {
            LOG.warn("No se pudo registrar el inicio de la corrida {}: {}", id, ex.getMessage());
        }
    }

    public void finalizarCorrida(CierreCorrida cierre, Instant finReal) {
        try {
            volcar();
            CorridaEntidad c = corridas.findById(cierre.idCorrida()).orElseGet(CorridaEntidad::new);
            c.setId(cierre.idCorrida());
            c.setEscenario(cierre.escenario());
            c.setAlgoritmo(cierre.algoritmo());
            c.setSemilla(cierre.semilla());
            c.setFinReal(finReal);
            if (c.getInicioReal() == null) c.setInicioReal(Instant.ofEpochMilli(cierre.inicioRealMs()));
            c.setEstado(cierre.estado());
            c.setMotivoFin(cierre.motivoFin());
            c.setCumplimiento(cierre.resumen().cumplimiento());
            c.setCostoTotal(cierre.resumen().costoTotal());
            c.setPedidos(cierre.resumen().pedidosRegistrados());
            c.setCierreJson(json.writeValueAsString(cierre));
            corridas.save(c);
        } catch (Exception ex) {
            LOG.warn("No se pudo grabar el cierre de la corrida {}: {}", cierre.idCorrida(), ex.getMessage());
        }
    }

    public List<CorridaEntidad> listarCorridas() {
        return corridas.findTop50ByOrderByInicioRealDesc();
    }

    public Optional<CierreCorrida> cierreDe(String id) {
        return corridas.findById(id).map(CorridaEntidad::getCierreJson).filter(s -> s != null && !s.isBlank()).map(s -> {
            try {
                return json.readValue(s, CierreCorrida.class);
            } catch (Exception ex) {
                LOG.warn("Cierre ilegible de la corrida {}: {}", id, ex.getMessage());
                return null;
            }
        });
    }

    public Page<RutaHistorialEntidad> rutasDe(String id, int pagina, int tamano) {
        return rutas.findByCorridaIdOrderByIdAsc(id, PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(500, tamano))));
    }

    public Page<EventoCorridaEntidad> eventosDe(String id, int pagina, int tamano) {
        return eventos.findByCorridaIdOrderByIdAsc(id, PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(500, tamano))));
    }

    // ------------------------------------------------------------------
    // Experimentos
    // ------------------------------------------------------------------

    public void guardarExperimento(String id, String estado, String descripcion, Object contenido, Instant creado, Instant terminado) {
        try {
            ExperimentoEntidad e = experimentos.findById(id).orElseGet(ExperimentoEntidad::new);
            e.setId(id);
            e.setEstado(estado);
            e.setDescripcion(descripcion != null && descripcion.length() > 290 ? descripcion.substring(0, 290) : descripcion);
            e.setCreado(creado);
            e.setTerminado(terminado);
            e.setContenidoJson(json.writeValueAsString(contenido));
            experimentos.save(e);
        } catch (Exception ex) {
            LOG.warn("No se pudo grabar el experimento {}: {}", id, ex.getMessage());
        }
    }

    public List<ExperimentoEntidad> listarExperimentos() {
        return experimentos.findTop30ByOrderByCreadoDesc();
    }

    public Optional<ExperimentoEntidad> experimento(String id) {
        return experimentos.findById(id);
    }
}
