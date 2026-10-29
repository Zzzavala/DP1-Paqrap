'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '@/lib/api';
import type { EstadoVisual } from '@/lib/tipos';
import {
  BORDE_VACIO, COLOR_BLOQUEO, COLOR_OCUPACION, COLOR_RETORNO, COLOR_RUTA, ETIQUETA_AVERIA, ETIQUETA_OCUPACION, numero, semaforo,
  type SemaforoOcupacion, soles,
} from '@/lib/formato';
import MapaOperacion, {
  acercar, centrarEn, FILTROS_POR_DEFECTO, type Filtros, InsigniaAveria, InsigniaTaller, ocupacionAlmacen, type Seleccion, type Vista, VISTA_COMPLETA, vistaDeZona,
} from './MapaOperacion';
import Tooltip, { type TooltipApi } from './Tooltip';
import { IconoMini } from './Iconos';
import PanelListas, { FiltroSemaforo, type PestanaPanel } from './PanelListas';

/** Rango de ocupacion de cada color con los umbrales vigentes (verde/ambar se aplican al espacio libre). */
function RANGO_OCUPACION(s: SemaforoOcupacion, verde: number, ambar: number): string {
  const v = Math.round(100 - verde);
  const a = Math.round(100 - ambar);
  if (s === 'vacio') return '(0 %)';
  if (s === 'verde') return `(hasta ${v} %)`;
  if (s === 'ambar') return `(${v} – ${a} %)`;
  return `(más de ${a} %)`;
}

interface Props {
  estado: EstadoVisual;
  minutoAhora: () => number;
  seleccion: Seleccion | null;
  onAbrir: (s: Seleccion) => void;
  onAviso: (texto: string, tipo?: 'ok' | 'error') => void;
  refrescar: () => void;
  /** Pedido de enfoque desde fuera (boton «Ver ruta en el mapa»); `n` cambia en cada pedido. */
  foco: { x: number; y: number; n: number } | null;
}

const TIPOS_RAPIDOS: [string, string][] = [
  ['REGULAR_36H', 'Regular · 36 h'],
  ['PRIORIZADA_4H', 'Priorizada · 4 h'],
  ['PRIORIZADA_8H', 'Priorizada · 8 h'],
  ['PRIORIZADA_12H', 'Priorizada · 12 h'],
  ['PRIORIZADA_18H', 'Priorizada · 18 h'],
];

function Kpi({ etiqueta, valor, sub, sem }: { etiqueta: string; valor: string; sub: string; sem?: 'verde' | 'ambar' | 'rojo' | null }) {
  return (
    <div className={'kpi' + (sem ? ' kpi-border-' + sem : '')}>
      <div className="kpi-label">{sem && <span className={'kpi-sem-dot ' + sem} />}{etiqueta}</div>
      <div className="kpi-value">{valor}</div>
      <div className="kpi-sub">{sub}</div>
    </div>
  );
}

/** V-2 · Panorama operativo: franja de KPI, mapa con la operacion en vivo y detalle bajo demanda. */
export default function PantallaPanorama({ estado, minutoAhora, seleccion, onAbrir, onAviso, refrescar, foco }: Props) {
  const [vista, setVista] = useState<Vista>(VISTA_COMPLETA);
  const [zona, setZona] = useState('full');
  const [filtros, setFiltros] = useState<Filtros>(FILTROS_POR_DEFECTO);
  const [verFiltros, setVerFiltros] = useState(false);
  // "mapa libre": todos los paneles empiezan recogidos y se abren a demanda
  const [leyenda, setLeyenda] = useState(false);
  const [metricas, setMetricas] = useState(false);
  const [verBuscar, setVerBuscar] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [errorBusqueda, setErrorBusqueda] = useState<string | null>(null);
  const [menu, setMenu] = useState<{ x: number; y: number; cx: number; cy: number } | null>(null);
  const [cantidadMenu, setCantidadMenu] = useState(1);
  const tooltip = useRef<TooltipApi>(null);
  // E01: panel izquierdo con las listas; empieza recogido
  const [verPanel, setVerPanel] = useState(false);
  const [pestanaPanel, setPestanaPanel] = useState<PestanaPanel>('transporte');

  const k = estado.kpis;
  const enCurso = estado.estadoCorrida === 'EN_CURSO';
  const diaADia = estado.escenario === 'TIEMPO_REAL';
  const intermedios = estado.almacenes.filter((a) => !a.central);
  const central = estado.almacenes.find((a) => a.central);

  // zona visible: local a este dispositivo (C6), no se sincroniza con los demas
  useEffect(() => {
    setVista(vistaDeZona(zona));
  }, [zona]);

  /** Centra el mapa en (x, y) sin alejar la vista (F05-F09). */
  const enfocar = useCallback((x: number, y: number) => setVista((v) => centrarEn(v, x, y)), []);

  useEffect(() => {
    if (foco) enfocar(foco.x, foco.y);
  }, [foco, enfocar]);

  // F06/F08: un clic en el mapa lleva el panel (si esta abierto) a la lista que corresponde y resalta la fila
  useEffect(() => {
    if (!seleccion) return;
    setPestanaPanel(seleccion.tipo === 'vehiculo' ? 'transporte' : seleccion.tipo === 'almacen' ? 'almacenes' : 'envios');
  }, [seleccion]);

  useEffect(() => {
    const cerrar = () => setMenu(null);
    if (menu) {
      const t = setTimeout(() => document.addEventListener('click', cerrar), 0);
      return () => {
        clearTimeout(t);
        document.removeEventListener('click', cerrar);
      };
    }
  }, [menu]);

  const buscar = async () => {
    const id = busqueda.trim();
    if (!id) {
      setErrorBusqueda(null);
      return;
    }
    try {
      const p = await api.pedido(id);
      setErrorBusqueda(null);
      setZona('full');
      setVista((v) => centrarEn(v, p.x, p.y));
      onAbrir({ tipo: 'pedido', id: p.id });
    } catch {
      setErrorBusqueda(`No se encontró ningún pedido con ID "${id}".`);
    }
  };

  const alClicTramo = useCallback(async (x1: number, y1: number, x2: number, y2: number) => {
    if (!enCurso) {
      onAviso('Inicie una corrida para bloquear calles desde el mapa.', 'error');
      return;
    }
    try {
      const r = await api.alternarBloqueo(x1, y1, x2, y2);
      onAviso(r.mensaje, 'ok');
      refrescar();
    } catch (e) {
      onAviso(e instanceof Error ? e.message : 'No se pudo bloquear el tramo', 'error');
    }
  }, [enCurso, onAviso, refrescar]);

  const alClicCruce = useCallback((x: number, y: number, clientX: number, clientY: number) => {
    setMenu({ x, y, cx: clientX, cy: clientY });
    setCantidadMenu(1);
  }, []);

  const registrarDesdeMapa = async (tipo: string) => {
    if (!menu) return;
    const { x, y } = menu;
    setMenu(null);
    try {
      const p = await api.registrarPedido({ x, y, cantidad: Math.max(1, cantidadMenu), tipoEntrega: tipo, canal: 'MAPA' });
      onAviso(`Pedido ${p.id} registrado en (${x}, ${y}); se asigna automáticamente a la unidad disponible.`, 'ok');
      refrescar();
    } catch (e) {
      onAviso(e instanceof Error ? e.message : 'No se pudo registrar el pedido', 'error');
    }
  };

  const onHover = useCallback((s: Seleccion | null, x: number, y: number) => tooltip.current?.mostrar(s, x, y), []);

  const semCumplimiento = semaforo(k.cumplimiento, 95, 85);
  return (
    <section className="panorama">
      <div className={'mapa-pantalla' + (verPanel ? ' con-panel' : '')}>
        <MapaOperacion
          estado={estado}
          minutoAhora={minutoAhora}
          vista={vista}
          onVista={setVista}
          filtros={filtros}
          seleccion={seleccion}
          onAbrir={onAbrir}
          onHover={onHover}
          onClicTramo={alClicTramo}
          onClicCruce={diaADia && enCurso ? alClicCruce : null}
        />

        <div className="mapa-controles" onPointerDown={(e) => e.stopPropagation()}>
          <button className={'btn-flot' + (verPanel ? ' activo' : '')} onClick={() => setVerPanel((x) => !x)}>Panel</button>
          <button className={'btn-flot' + (metricas ? ' activo' : '')} onClick={() => setMetricas((x) => !x)}>Métricas</button>
          <button className={'btn-flot' + (leyenda ? ' activo' : '')} onClick={() => setLeyenda((x) => !x)}>Leyenda</button>
          <button className={'btn-flot' + (verFiltros ? ' activo' : '')} onClick={() => setVerFiltros((x) => !x)}>Filtros</button>
          <button className={'btn-flot' + (verBuscar ? ' activo' : '')} onClick={() => setVerBuscar((x) => !x)}>Buscar pedido</button>
          <span className="sep-flot" />
          <button className="btn-flot" title="Alejar" onClick={() => setVista((v) => acercar(v, 1 / 1.25))}>−</button>
          <button className="btn-flot" title="Restablecer zoom" onClick={() => setVista(vistaDeZona(zona))}>100%</button>
          <button className="btn-flot" title="Acercar" onClick={() => setVista((v) => acercar(v, 1.25))}>+</button>
        </div>

        {estado.mensaje && <div className="banner banner-error mapa-aviso">{estado.mensaje}</div>}
        {estado.colapso && (
          <div className="banner banner-error mapa-aviso">
            <b>Colapso logístico detectado</b> el {estado.colapso.fecha}: {estado.colapso.motivo}. Cumplimiento en ese momento: {numero(estado.colapso.cumplimiento, 1)} %.
          </div>
        )}

        {verPanel && (
          <PanelListas estado={estado} minutoAhora={minutoAhora} seleccion={seleccion} onAbrir={onAbrir} enfocar={enfocar}
            filtros={filtros} setFiltros={setFiltros} pestana={pestanaPanel} setPestana={setPestanaPanel} onCerrar={() => setVerPanel(false)} />
        )}

        {verBuscar && (
          <div className="panel-flot panel-buscar" onPointerDown={(e) => e.stopPropagation()}>
            <label>Buscar pedido por ID
              <div className="track-row">
                <input value={busqueda} placeholder="ej. PV2609-0001" autoFocus onChange={(e) => setBusqueda(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); buscar(); } }} />
                <button className="btn btn-ghost" type="button" onClick={buscar}>Buscar</button>
              </div>
            </label>
            {errorBusqueda && <span className="error-texto">{errorBusqueda}</span>}
          </div>
        )}

        {verFiltros && (
          <div className="panel-flot panel-filtros" onPointerDown={(e) => e.stopPropagation()}>
            <label>Zona visible <span className="hint">(solo en este dispositivo)</span>
              <select value={zona} onChange={(e) => setZona(e.target.value)}>
                <option value="full">Vista completa</option>
                <option value="noroeste">Cuadrante noroeste</option>
                <option value="noreste">Cuadrante noreste</option>
                <option value="suroeste">Cuadrante suroeste</option>
                <option value="sureste">Cuadrante sureste</option>
              </select>
            </label>
            <label><input type="checkbox" checked={filtros.soloCriticos} onChange={(e) => setFiltros({ ...filtros, soloCriticos: e.target.checked })} />Solo pedidos en riesgo o críticos</label>
            <label><input type="checkbox" checked={filtros.mostrarPedidos} onChange={(e) => setFiltros({ ...filtros, mostrarPedidos: e.target.checked })} />Mostrar pedidos</label>
            <label><input type="checkbox" checked={filtros.mostrarBloqueos} onChange={(e) => setFiltros({ ...filtros, mostrarBloqueos: e.target.checked })} />Mostrar calles bloqueadas</label>
            <label><input type="checkbox" checked={filtros.unidadesEnAlmacen} onChange={(e) => setFiltros({ ...filtros, unidadesEnAlmacen: e.target.checked })} />Mostrar unidades en almacén</label>
            <div className="subtitulo" style={{ marginTop: 4 }}>Semáforo de ocupación</div>
            <FiltroSemaforo titulo="Unidades" valor={filtros.semaforoUT} onCambio={(v) => setFiltros({ ...filtros, semaforoUT: v })} />
            <FiltroSemaforo titulo="Almacenes" valor={filtros.semaforoAlmacen} onCambio={(v) => setFiltros({ ...filtros, semaforoAlmacen: v })} />
            <div className="subtitulo" style={{ marginTop: 4 }}>Tipos de vehículo</div>
            {(['AUTO', 'MOTO', 'BICICLETA'] as const).map((t) => (
              <label key={t}><input type="checkbox" checked={filtros.tipos[t]} onChange={(e) => setFiltros({ ...filtros, tipos: { ...filtros.tipos, [t]: e.target.checked } })} />
                {t === 'AUTO' ? 'Autos' : t === 'MOTO' ? 'Motos' : 'Bicicletas'}</label>
            ))}
            <label>Rutas
              <select value={filtros.rutas} onChange={(e) => setFiltros({ ...filtros, rutas: e.target.value as Filtros['rutas'] })}>
                <option value="todas">Todas (tenues) y la seleccionada resaltada</option>
                <option value="seleccionada">Solo la del vehículo seleccionado</option>
                <option value="ninguna">Ninguna</option>
              </select>
            </label>
          </div>
        )}

        {metricas && (
          <div className="kpi-strip mapa-kpis" onPointerDown={(e) => e.stopPropagation()}>
            <Kpi etiqueta="Cumplimiento a tiempo" valor={`${numero(k.cumplimiento, 1)} %`} sub={`${numero(k.registrados - k.incumplidos)} de ${numero(k.registrados)} pedidos no vencidos`} sem={k.registrados ? semCumplimiento : null} />
            <Kpi etiqueta="Pedidos" valor={`${numero(k.entregados)} entregados`} sub={`${numero(k.enCurso)} en ruta · ${numero(k.pendientes)} por despachar`} />
            <Kpi etiqueta="Costo acumulado" valor={soles(k.costo)} sub={`${numero(k.km)} km recorridos según tipo de vehículo`} />
            <Kpi etiqueta="Flota activa" valor={`${k.flotaActiva} / ${k.flotaTotal}`} sub="vehículos operativos (sin avería ni mantenimiento)" />
            <Kpi etiqueta="Almacén central" valor="Sin límite" sub="Abastecimiento permanente (sin capacidad máxima)" />
            {intermedios.map((a) => {
              const o = ocupacionAlmacen(a, estado.umbralVerde, estado.umbralAmbar);
              return (
                <Kpi key={a.id} etiqueta={`Stock ${a.nombre}`} valor={`${numero(a.stock ?? 0)} / ${numero(a.capacidad ?? 0)} (${numero(o?.pct ?? 0)} %)`}
                  sub={`${o ? ETIQUETA_OCUPACION[o.sem] : ''} · recarga diaria 23:59:59`} sem={o && o.sem !== 'vacio' ? o.sem : null} />
              );
            })}
            <Kpi etiqueta="Rutas recalculadas" valor={String(k.reRuteosBloqueo + k.reRuteosAveria)}
              sub={`bloqueo: ${k.reRuteosBloqueo} · avería: ${k.reRuteosAveria} · reasignaciones: ${k.reasignaciones}`} />
          </div>
        )}

        {leyenda && (
          <div className="panel-flot panel-leyenda" onPointerDown={(e) => e.stopPropagation()}>
            <div className="legend">
              <div className="subtitulo">Pedidos · semáforo de plazo</div>
              <div className="legend-item"><IconoMini tipo="pedido" color="#2e7d46" />Paquete = pedido a tiempo</div>
              <div className="legend-item"><IconoMini tipo="pedido" color="#c98a10" />Paquete = pedido en riesgo</div>
              <div className="legend-item"><IconoMini tipo="pedido" color="#c0392b" />Paquete = pedido vencido / crítico</div>
              <div className="subtitulo" style={{ marginTop: 6 }}>Unidades y almacenes intermedios · semáforo de ocupación</div>
              {(['vacio', 'verde', 'ambar', 'rojo'] as const).map((s) => (
                <div className="legend-item" key={s}>
                  <IconoMini tipo="vehiculo" color={COLOR_OCUPACION[s]} forma="AUTO" borde={s === 'vacio' ? BORDE_VACIO : undefined} />
                  <IconoMini tipo="almacen" color={COLOR_OCUPACION[s]} borde={s === 'vacio' ? BORDE_VACIO : undefined} />
                  {ETIQUETA_OCUPACION[s]} {RANGO_OCUPACION(s, estado.umbralVerde, estado.umbralAmbar)}
                </div>
              ))}
              <div className="legend-item"><IconoMini tipo="vehiculo" color="#0f3d5c" forma="AUTO" /><IconoMini tipo="vehiculo" color="#0f3d5c" forma="MOTO" /><IconoMini tipo="vehiculo" color="#0f3d5c" forma="BICICLETA" />Forma = auto / moto / bicicleta</div>
              <div className="legend-item"><svg width={18} height={18} viewBox="-9 -9 18 18" aria-hidden><circle r={4.5} fill="#c98a10" stroke="#fff" strokeWidth={1.4} /></svg>
                <svg width={18} height={18} viewBox="-9 -9 18 18" aria-hidden><circle r={4.5} fill="#c0392b" stroke="#fff" strokeWidth={1.4} /></svg>
                Punto en la esquina = urgencia de plazo (lleva un pedido en riesgo o crítico)</div>
              <div className="legend-item"><IconoMini tipo="almacen" color="#0f3d5c" />Almacén central: sin límite de stock, no lleva semáforo</div>
              <div className="subtitulo" style={{ marginTop: 6 }}>Circunstancias</div>
              {[1, 2, 3].map((t) => (
                <div className="legend-item" key={t}>
                  <svg width={18} height={18} viewBox="-9 -9 18 18" aria-hidden><InsigniaAveria tipo={t} x={0} y={0} /></svg>
                  {ETIQUETA_AVERIA[t]} (unidad gris detenida en el lugar)
                </div>
              ))}
              <div className="legend-item">
                <svg width={18} height={18} viewBox="-9 -9 18 18" aria-hidden><InsigniaTaller x={0} y={0} /></svg>
                Unidad en taller (visible con «Mostrar unidades en almacén»)
              </div>
              <div className="legend-item"><span className="legend-line" style={{ borderColor: COLOR_BLOQUEO, borderTopWidth: 5 }} />Calle bloqueada (dato real del curso)</div>
              <div className="legend-item"><span className="legend-line dashed" style={{ borderColor: '#a15b16', borderTopWidth: 5 }} />Calle bloqueada manualmente (clic en el mapa)</div>
              <div className="legend-item"><span className="legend-line dashed" style={{ borderColor: COLOR_RUTA }} />Ruta hacia los clientes (más tenue = vehículo no seleccionado)</div>
              <div className="legend-item"><span className="legend-line dotted" style={{ borderColor: COLOR_RETORNO }} />Retorno al almacén (después de la última entrega)</div>
              <div className="legend-item"><span className="hint">Retícula de 70 × 50 km; cada línea es una calle (1 km).</span></div>
            </div>
            <p className="hint" style={{ margin: '10px 0 0' }}>
              Pasa el mouse sobre un vehículo, pedido o almacén para ver su resumen y haz clic para abrir su detalle en el panel lateral.
              Clic en un tramo de calle: bloquea o reabre ese tramo. Rueda: zoom · arrastrar: desplazar.{' '}
              {diaADia
                ? 'En «Operación día a día», clic cerca de un cruce para registrar un pedido nuevo.'
                : 'El registro manual de pedidos solo está disponible en «Operación día a día».'}
            </p>
          </div>
        )}

      </div>

      <Tooltip ref={tooltip} estado={estado} minuto={minutoAhora} />

      {menu && (
        <div className="menu-pedido" style={{ left: menu.cx, top: menu.cy }} onClick={(e) => e.stopPropagation()}>
          <div className="cantidad">
            Nuevo pedido en ({menu.x}, {menu.y}) · paquetes
            <input type="number" min={1} max={200} value={cantidadMenu} onChange={(e) => setCantidadMenu(Number(e.target.value))} />
          </div>
          {TIPOS_RAPIDOS.map(([id, etiqueta]) => (
            <button key={id} className="opcion" onClick={() => registrarDesdeMapa(id)}>{etiqueta}</button>
          ))}
          <button className="opcion" style={{ color: '#8695a3' }} onClick={() => setMenu(null)}>Cancelar</button>
        </div>
      )}
    </section>
  );
}
