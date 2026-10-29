'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { DetalleAlmacen, DetallePedido, DetalleVehiculo, EventoDetalle } from '@/lib/tipos';
import {
  claseOcupacion, ETIQUETA_EVENTO, ETIQUETA_OCUPACION, ETIQUETA_SEMAFORO, numero, porcentaje, semaforo, semaforoOcupacion, soles,
} from '@/lib/formato';
import type { Seleccion } from './MapaOperacion';

interface Props {
  seleccion: Seleccion;
  enCurso: boolean;
  diaADia: boolean;
  verde: number;
  ambar: number;
  onCerrar: () => void;
  onAbrir: (s: Seleccion) => void;
  /** Va a Panorama y centra el mapa (F01/F03, F09). */
  onVerEnMapa: (x: number, y: number) => void;
  onAviso: (texto: string, tipo?: 'ok' | 'error') => void;
}

const TIPOS_ENTREGA = [
  ['REGULAR_36H', 'Regular · 36 h'],
  ['PRIORIZADA_4H', 'Priorizada · 4 h'],
  ['PRIORIZADA_8H', 'Priorizada · 8 h'],
  ['PRIORIZADA_12H', 'Priorizada · 12 h'],
  ['PRIORIZADA_18H', 'Priorizada · 18 h'],
];

const ICONO_EVENTO: Record<string, string> = {
  RECALCULO: 'R', REASIGNACION: '⇄', TRANSBORDO: '⇄', AVERIA: '!', COLAPSO: '!', REPROGRAMACION: '!',
  ENTREGA: '✓', DISPONIBLE: '✓', DESPACHO: '→', REGRESO: '←', REUBICACION: '↔', ASIGNACION: '→', REGISTRO: '+',
  DIVISION: '÷', EDICION: '✎', CANCELACION: '×',
};
/** Hora "hh:mm" de una fecha del backend ("dd/mm/aaaa hh:mm" u otra que termine en la hora). */
const soloHora = (f: string) => (f && f.length >= 5 ? f.slice(-5) : f);

/** B11: la ruta como lista de tramos origen -> destino, con hora de salida y de llegada. */
function Tramos({ ruta, onAbrir }: { ruta: NonNullable<DetalleVehiculo['ruta']>; onAbrir: (s: Seleccion) => void }) {
  const tramos: { origen: string; destino: string; sale: string; llega: string; pedido: string | null; hecho: boolean; actual: boolean }[] = [];
  let origen = `Almacén ${ruta.almacenSalida}`;
  let sale = ruta.salida;
  let marcadoActual = false;
  for (const p of ruta.paradas) {
    const entregada = p.estado === 'Entregada';
    const actual = !entregada && !marcadoActual;
    if (actual) marcadoActual = true;
    tramos.push({ origen, destino: `(${p.x}, ${p.y}) · pedido ${p.pedido}`, sale, llega: p.llegada, pedido: p.pedido, hecho: entregada, actual });
    origen = `(${p.x}, ${p.y})`;
    sale = p.salida;
  }
  tramos.push({ origen, destino: `Almacén ${ruta.almacenRetorno}`, sale, llega: ruta.regresoEstimado, pedido: null, hecho: false, actual: !marcadoActual });
  return (
    <ol className="tramos-lista">
      {tramos.map((t, i) => (
        <li key={i} className={'tramo' + (t.hecho ? ' hecho' : '') + (t.actual ? ' actual' : '') + (t.pedido ? ' fila-click' : '')}
          onClick={t.pedido ? () => onAbrir({ tipo: 'pedido', id: t.pedido! }) : undefined}>
          <span className="tramo-num">{i + 1}</span>
          <span className="tramo-ruta">{t.origen} → {t.destino}</span>
          <span className="tramo-horas">sale {soloHora(t.sale)} · llega {soloHora(t.llega)}</span>
        </li>
      ))}
    </ol>
  );
}

function tipoDesdeEtiqueta(etiqueta: string): string {
  const horas = etiqueta.match(/(\d+)\s*h/);
  if (!horas || etiqueta.startsWith('Regular')) return 'REGULAR_36H';
  return `PRIORIZADA_${horas[1]}H`;
}

function Eventos({ eventos, vacio }: { eventos: EventoDetalle[]; vacio: string }) {
  if (!eventos.length) return <p className="empty-hint">{vacio}</p>;
  return (
    <ul className="event-log">
      {eventos.map((e, i) => (
        <li className="event-item" key={i}>
          <div className={'event-icon ' + e.tipo.toLowerCase()}>{ICONO_EVENTO[e.tipo] ?? '•'}</div>
          <div className="event-body">
            <div className="event-type">{ETIQUETA_EVENTO[e.tipo] ?? e.tipo}</div>
            <div className="event-detail">{e.detalle}</div>
            <div className="event-ts">{e.fecha}</div>
          </div>
        </li>
      ))}
    </ul>
  );
}

function Kv({ pares }: { pares: [string, React.ReactNode][] }) {
  return (
    <div className="kv-grid">
      {pares.map(([k, v]) => (
        <div className="kv" key={k}>
          <div className="kv-label">{k}</div>
          <div className="kv-value">{v}</div>
        </div>
      ))}
    </div>
  );
}

export default function ModalDetalle({ seleccion, enCurso, diaADia, verde, ambar, onCerrar, onAbrir, onVerEnMapa, onAviso }: Props) {
  const [datos, setDatos] = useState<DetalleVehiculo | DetallePedido | DetalleAlmacen | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [edicion, setEdicion] = useState<{ cantidad: number; tipo: string; observaciones: string } | null>(null);
  const [ocupado, setOcupado] = useState(false);

  const cargar = useCallback(async () => {
    try {
      const d = seleccion.tipo === 'vehiculo' ? await api.vehiculo(seleccion.id)
        : seleccion.tipo === 'pedido' ? await api.pedido(seleccion.id) : await api.almacen(seleccion.id);
      setDatos(d);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cargar el detalle');
    }
  }, [seleccion]);

  useEffect(() => {
    setDatos(null);
    setEdicion(null);
    cargar();
    const t = setInterval(cargar, 1500);
    return () => clearInterval(t);
  }, [cargar]);

  useEffect(() => {
    const tecla = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCerrar();
    };
    document.addEventListener('keydown', tecla);
    return () => document.removeEventListener('keydown', tecla);
  }, [onCerrar]);

  const reportarAveria = async (tipo: number) => {
    setOcupado(true);
    try {
      const r = await api.averia(seleccion.id, tipo);
      onAviso(r.mensaje, 'ok');
      await cargar();
    } catch (e) {
      onAviso(e instanceof Error ? e.message : 'No se pudo registrar la avería', 'error');
    } finally {
      setOcupado(false);
    }
  };

  const guardarEdicion = async () => {
    if (!edicion) return;
    setOcupado(true);
    try {
      await api.editarPedido(seleccion.id, { cantidad: edicion.cantidad, tipoEntrega: edicion.tipo, observaciones: edicion.observaciones });
      onAviso('Pedido actualizado.', 'ok');
      setEdicion(null);
      await cargar();
    } catch (e) {
      onAviso(e instanceof Error ? e.message : 'No se pudo editar el pedido', 'error');
    } finally {
      setOcupado(false);
    }
  };

  const cancelar = async () => {
    if (!window.confirm(`¿Cancelar el pedido ${seleccion.id}? Esta acción no se puede deshacer.`)) return;
    setOcupado(true);
    try {
      await api.cancelarPedido(seleccion.id);
      onAviso(`Pedido ${seleccion.id} cancelado.`, 'ok');
      await cargar();
    } catch (e) {
      onAviso(e instanceof Error ? e.message : 'No se pudo cancelar el pedido', 'error');
    } finally {
      setOcupado(false);
    }
  };

  let cuerpo: React.ReactNode = <p className="empty-hint">Cargando detalle…</p>;
  if (error && !datos) cuerpo = <p className="error-texto">{error}</p>;
  else if (datos && seleccion.tipo === 'vehiculo') {
    const v = datos as DetalleVehiculo;
    cuerpo = (
      <>
        <h3>Vehículo {v.id} · {v.tipo}</h3>
        <span className={'semaforo-pill ' + (v.averia ? 'semaforo-rojo' : 'semaforo-gris')}>{v.estado}</span>
        <div style={{ height: 14 }} />
        <div className="carga-barra" title="Carga actual / capacidad">
          <div className="carga-texto"><span>Ocupación</span>
            <b>{v.carga} / {v.capacidad} paquetes ({Math.round(porcentaje(v.carga, v.capacidad))} %){' '}
              <span className={claseOcupacion(semaforoOcupacion(porcentaje(v.carga, v.capacidad), verde, ambar))}>
                {ETIQUETA_OCUPACION[semaforoOcupacion(porcentaje(v.carga, v.capacidad), verde, ambar)]}</span></b></div>
          <div className="progreso"><div style={{ width: `${v.capacidad ? Math.min(100, (100 * v.carga) / v.capacidad) : 0}%` }} /></div>
        </div>
        <div style={{ height: 12 }} />
        <Kv pares={[
          ['Almacén base', v.almacenBase],
          ['Turno vigente', v.turnoActual || '—'],
          ['Velocidad · costo', `${v.velocidad} km/h · S/ ${v.costoKm.toFixed(2)} por km`],
          ['Posición', `(${numero(v.x, 1)}, ${numero(v.y, 1)}) km`],
          ['Próxima parada', v.proximaParada],
          ['Llegada estimada', v.eta],
          ['Próximo mantenimiento', v.proximoMantenimiento],
          ['Avería', v.averia ?? 'Sin avería'],
          // la alimentación solo se muestra si la pausa ya ocurrió en la corrida
          ...(v.horasAlimentacion.length
            ? [['Pausa de alimentación', v.horasAlimentacion.join(' · ')] as [string, React.ReactNode]]
            : []),
          ['Acumulado en la corrida', `${v.rutasRealizadas} rutas · ${v.entregas} entregas · ${numero(v.km, 1)} km · ${soles(v.costo)}`],
        ]} />
        {enCurso && (
          <div className="acciones-modal">
            <span className="hint">Reportar avería (D7):</span>
            {[1, 2, 3].map((t) => (
              <button key={t} className="btn btn-sm btn-peligro" disabled={ocupado} onClick={() => reportarAveria(t)}>Tipo {t}</button>
            ))}
          </div>
        )}
        <h3 style={{ marginTop: 10 }}>Ruta actual</h3>
        {v.ruta ? (
          <>
            <p className="hint">
              Ruta {v.ruta.id} · {v.ruta.causa} · sale de {v.ruta.almacenSalida} ({v.ruta.salida}) · regresa a {v.ruta.almacenRetorno} (~{v.ruta.regresoEstimado})
            </p>
            <h4>Tramos de la ruta</h4>
            <Tramos ruta={v.ruta} onAbrir={onAbrir} />
            {v.ruta.paradas.length ? (
              <>
                <h4 style={{ marginTop: 12 }}>Pedidos en la ruta</h4>
                <p className="hint" style={{ margin: '0 0 8px' }}>
                  {v.ruta.paradas.length === 1 ? '1 pedido en la ruta' : `${v.ruta.paradas.length} pedidos en la ruta, en orden de entrega`}
                  {' · '}{v.ruta.paradas.reduce((t, p) => t + p.cantidad, 0)} paquetes en total
                </p>
                <ol className="paradas-lista">
                  {v.ruta.paradas.map((p) => {
                    const actual = p.estado === 'Entregando' || (p.estado === 'Pendiente' && v.ruta!.paradas.find((q) => q.estado === 'Pendiente') === p);
                    return (
                      <li key={p.secuencia + p.pedido} className={'parada-card' + (actual ? ' actual' : '') + (p.estado === 'Entregada' ? ' hecha' : '')}
                        onClick={() => onAbrir({ tipo: 'pedido', id: p.pedido })}>
                        <div className="parada-cab">
                          <span className="parada-num">{p.secuencia}</span>
                          <b>{p.pedido}</b>
                          <span className={'semaforo-pill ' + (p.aTiempo ? 'semaforo-verde' : 'semaforo-rojo')}>{p.estado}</span>
                        </div>
                        <div className="parada-det">{p.cantidad} paq. → ({p.x}, {p.y}) · cliente {p.cliente}</div>
                        <div className="parada-det">Llegada {p.llegada} · salida {p.salida} · límite {p.limite}</div>
                      </li>
                    );
                  })}
                </ol>
              </>
            ) : (
              <p className="empty-hint">Se reubica sin carga hacia otro almacén.</p>
            )}
          </>
        ) : (
          <p className="empty-hint">La unidad no tiene ruta asignada en este momento.</p>
        )}
        <h3 style={{ marginTop: 18 }}>Historial de eventos de este vehículo</h3>
        <Eventos eventos={v.eventos} vacio="Sin eventos registrados todavía para esta unidad." />
      </>
    );
  } else if (datos && seleccion.tipo === 'pedido') {
    const p = datos as DetallePedido;
    const sem = semaforo(p.holgura, verde, ambar);
    const pill = p.estado === 'Entregado'
      ? <span className={'semaforo-pill ' + (p.aTiempo ? 'semaforo-verde' : 'semaforo-rojo')}>{p.aTiempo ? 'Entregado a tiempo' : 'Entregado fuera de plazo'}</span>
      : p.estado === 'Cancelado'
        ? <span className="semaforo-pill semaforo-gris">Cancelado</span>
        : <span className={'semaforo-pill semaforo-' + (p.reprogramado ? 'rojo' : sem)}>{p.reprogramado ? 'Reprogramado (incumplido)' : ETIQUETA_SEMAFORO[sem]}</span>;
    cuerpo = (
      <>
        <h3>Pedido {p.id}</h3>
        {pill}
        <div className="acciones-modal" style={{ marginTop: 10 }}>
          <button className="btn btn-sm btn-primary" onClick={() => onVerEnMapa(p.x, p.y)}>Ver ruta en el mapa</button>
          <span className="hint">{p.vehiculos.length > 1 ? `Resalta las rutas de ${p.vehiculos.length} unidades.` : p.vehiculos.length ? 'Resalta la ruta de su unidad.' : 'Aún sin unidad: centra el destino.'}</span>
        </div>
        <div style={{ height: 14 }} />
        <Kv pares={[
          ['Estado', p.estado],
          ['Cliente', p.cliente],
          ['Punto de entrega', `(${p.x}, ${p.y}) km`],
          ['Paquetes', p.cantidad],
          ['Tipo de entrega', p.tipoEntrega],
          ['Plazo', `${p.plazo} horas`],
          ['Registro', p.registro],
          ['Hora límite comprometida', p.limite],
          ['Holgura de plazo restante', p.estado === 'Entregado' || p.estado === 'Cancelado' ? '—' : `${numero(p.holgura, 1)} %`],
          ['Unidad asignada', p.vehiculos.length ? p.vehiculos.join(', ') : 'Pendiente de asignación (en almacén)'],
          ['Almacén de origen', p.almacenes.length ? p.almacenes.join(', ') : '—'],
          ['Entrega real', p.entregaReal ?? '—'],
          ['Canal', p.canal],
          ['Observaciones', p.observaciones || '—'],
        ]} />
        {p.partes.length > 1 && (
          <p className="hint">Partes activas: {p.partes.map((x) => `${x.id} (${x.cantidad} · ${x.estado})`).join(', ')}</p>
        )}
        {p.rutas.length > 0 && <p className="hint">Rutas: {p.rutas.join(', ')}</p>}
        {p.modificable && enCurso && diaADia && (
          <div className="acciones-modal">
            {!edicion ? (
              <>
                <button className="btn btn-sm btn-ghost" onClick={() => setEdicion({ cantidad: p.cantidad, tipo: tipoDesdeEtiqueta(p.tipoEntrega), observaciones: p.observaciones ?? '' })}>Editar pedido</button>
                <button className="btn btn-sm btn-peligro" disabled={ocupado} onClick={cancelar}>Cancelar pedido</button>
                <span className="hint">Solo antes de que salga del almacén (A6, A7).</span>
              </>
            ) : (
              <div className="fila-campos" style={{ width: '100%' }}>
                <label>Paquetes<input type="number" min={1} value={edicion.cantidad} onChange={(e) => setEdicion({ ...edicion, cantidad: Number(e.target.value) })} /></label>
                <label>Tipo de entrega
                  <select value={edicion.tipo} onChange={(e) => setEdicion({ ...edicion, tipo: e.target.value })}>
                    {TIPOS_ENTREGA.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                  </select>
                </label>
                <label>Observaciones<input value={edicion.observaciones} onChange={(e) => setEdicion({ ...edicion, observaciones: e.target.value })} /></label>
                <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
                  <button className="btn btn-sm btn-primary" disabled={ocupado} onClick={guardarEdicion}>Guardar</button>
                  <button className="btn btn-sm btn-ghost" onClick={() => setEdicion(null)}>Cancelar</button>
                </div>
              </div>
            )}
          </div>
        )}
        <h3 style={{ marginTop: 10 }}>Trazabilidad del pedido</h3>
        <Eventos eventos={p.eventos} vacio="Sin eventos todavía." />
      </>
    );
  } else if (datos && seleccion.tipo === 'almacen') {
    const a = datos as DetalleAlmacen;
    const sem = a.porcentaje == null ? null : semaforoOcupacion(a.porcentaje, verde, ambar);
    cuerpo = (
      <>
        <h3>{a.nombre}</h3>
        {sem ? <span className={claseOcupacion(sem)}>Ocupación: {ETIQUETA_OCUPACION[sem]}</span>
          : <p className="empty-hint">Este almacén no tiene capacidad máxima, así que no aplica semaforización de stock.</p>}
        <div style={{ height: 14 }} />
        <Kv pares={[
          ['Ubicación', `(${a.x}, ${a.y}) km`],
          ['Capacidad', a.capacidad == null ? 'Sin capacidad máxima (abastecimiento permanente)' : `${numero(a.capacidad)} unidades`],
          ['Stock actual', a.stock == null ? 'No aplica — surte bajo demanda, sin límite' : `${numero(a.stock)} / ${numero(a.capacidad ?? 0)} (${numero(a.porcentaje ?? 0, 1)} %)`],
          ['Despachado hoy', a.central ? '—' : `${numero(a.despachadoHoy)} unidades`],
          ['Recarga', a.recarga],
          ['Unidades que regresan aquí', a.vehiculosEnCamino.length ? a.vehiculosEnCamino.join(', ') : 'Ninguna'],
        ]} />
        <h3>Unidades en el almacén ({a.vehiculosEnAlmacen.length})</h3>
        {a.vehiculosEnAlmacen.length ? (
          <div className="tipos-entrega">
            {a.vehiculosEnAlmacen.map((id) => (
              <button key={id} className="btn btn-sm btn-ghost" onClick={() => onAbrir({ tipo: 'vehiculo', id })}>{id}</button>
            ))}
          </div>
        ) : <p className="empty-hint">Ninguna unidad estacionada en este momento.</p>}
      </>
    );
  }

  // panel lateral: deja el mapa visible (no lo tapa); se cierra con la X o con Esc
  return (
    <aside className="panel-lateral" role="complementary" aria-label="Detalle">
      <button className="modal-close" type="button" aria-label="Cerrar" onClick={onCerrar}>×</button>
      {cuerpo}
    </aside>
  );
}
