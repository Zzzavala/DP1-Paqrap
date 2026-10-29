'use client';

import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { AlmacenVisual, EstadoVisual, VehiculoVisual } from '@/lib/tipos';
import {
  BORDE_VACIO, COLOR_AVERIA, COLOR_BLOQUEO, COLOR_OCUPACION, COLOR_RETORNO, COLOR_RUTA, COLOR_SEMAFORO, porcentaje, type Semaforo,
  semaforo, semaforoOcupacion, type SemaforoOcupacion,
} from '@/lib/formato';
import { posicionEnTrayectoria } from '@/lib/useEstadoSimulacion';
import { GlifoAlmacen, GlifoLlave, GlifoPaquete, GlifoVehiculo } from './Iconos';

// Reticula oficial de la ciudad: 70 x 50 km, origen (0,0) en la esquina inferior izquierda (P4).
export const ANCHO_KM = 70;
export const ALTO_KM = 50;
const MARGEN = 2;
const VB_W = ANCHO_KM + 2 * MARGEN;
const VB_H = ALTO_KM + 2 * MARGEN;
const NAVY = '#0f3d5c';
const GRIS = '#8695a3';

/** Vista del mapa en coordenadas del SVG (y invertida): centro y ancho visible en km. */
export interface Vista {
  cx: number;
  cy: number;
  ancho: number;
}

export const VISTA_COMPLETA: Vista = { cx: ANCHO_KM / 2, cy: ALTO_KM / 2, ancho: VB_W };

export function vistaDeZona(zona: string): Vista {
  const w = VB_W * 0.58;
  const h = VB_H * 0.58;
  switch (zona) {
    case 'noroeste':
      return { cx: -MARGEN + w / 2, cy: -MARGEN + h / 2, ancho: w };
    case 'noreste':
      return { cx: ANCHO_KM + MARGEN - w / 2, cy: -MARGEN + h / 2, ancho: w };
    case 'suroeste':
      return { cx: -MARGEN + w / 2, cy: ALTO_KM + MARGEN - h / 2, ancho: w };
    case 'sureste':
      return { cx: ANCHO_KM + MARGEN - w / 2, cy: ALTO_KM + MARGEN - h / 2, ancho: w };
    default:
      return VISTA_COMPLETA;
  }
}

export interface Seleccion {
  tipo: 'vehiculo' | 'pedido' | 'almacen';
  id: string;
}

export interface Filtros {
  soloCriticos: boolean;
  mostrarPedidos: boolean;
  mostrarBloqueos: boolean;
  rutas: 'todas' | 'seleccionada' | 'ninguna';
  tipos: Record<string, boolean>;
  unidadesEnAlmacen: boolean;
  /** F15-F16: colores de ocupacion visibles, compartidos entre las listas del panel y el mapa. */
  semaforoUT: Record<SemaforoOcupacion, boolean>;
  semaforoAlmacen: Record<SemaforoOcupacion, boolean>;
  /** E11/F18: almacen de origen de las unidades visibles ('' = todos), compartido entre el panel y el mapa. */
  origen: string;
}

const TODOS_LOS_COLORES: Record<SemaforoOcupacion, boolean> = { vacio: true, verde: true, ambar: true, rojo: true };

export const FILTROS_POR_DEFECTO: Filtros = {
  soloCriticos: false,
  mostrarPedidos: true,
  mostrarBloqueos: true,
  rutas: 'todas',
  tipos: { AUTO: true, MOTO: true, BICICLETA: true },
  unidadesEnAlmacen: false,
  semaforoUT: { ...TODOS_LOS_COLORES },
  semaforoAlmacen: { ...TODOS_LOS_COLORES },
  origen: '',
};

/** La unidad pasa el filtro de tipo, de origen y de semaforo de ocupacion (las averiadas o en taller siempre se muestran). */
export function vehiculoPasaFiltro(v: VehiculoVisual, f: Filtros, verde: number, ambar: number): boolean {
  if (!f.tipos[v.tipo]) return false;
  if (f.origen && v.origen !== f.origen) return false;
  if (FUERA_DE_SERVICIO.has(v.estado)) return true;
  return f.semaforoUT[ocupacionVehiculo(v, verde, ambar).sem];
}

/** El almacen pasa el filtro de semaforo de ocupacion (el central, sin semaforo, siempre se muestra). */
export function almacenPasaFiltro(a: AlmacenVisual, f: Filtros, verde: number, ambar: number): boolean {
  const o = ocupacionAlmacen(a, verde, ambar);
  return o == null || f.semaforoAlmacen[o.sem];
}

/** Unidades que llevan alguna parte del pedido (todas, no solo la primera). */
export function vehiculosDePedido(estado: EstadoVisual, idPedido: string): string[] {
  const p = estado.pedidos.find((x) => x.id === idPedido);
  return p?.vehiculos ? p.vehiculos.split(', ').filter(Boolean) : [];
}

interface Props {
  estado: EstadoVisual;
  minutoAhora: () => number;
  vista: Vista;
  onVista: (v: Vista) => void;
  filtros: Filtros;
  seleccion: Seleccion | null;
  onAbrir: (s: Seleccion) => void;
  onHover: (s: Seleccion | null, clientX: number, clientY: number) => void;
  onClicTramo: (x1: number, y1: number, x2: number, y2: number) => void;
  onClicCruce: ((x: number, y: number, clientX: number, clientY: number) => void) | null;
}

const EN_BASE = new Set(['EN_ALMACEN', 'MANTENIMIENTO', 'EN_TALLER']);

export const FUERA_DE_SERVICIO = new Set(['AVERIADA', 'EN_TALLER']);

/** Semaforo de ocupacion de la unidad (C27): 100 * carga / capacidad. */
export function ocupacionVehiculo(v: VehiculoVisual, verde: number, ambar: number): { pct: number; sem: SemaforoOcupacion } {
  const pct = porcentaje(v.carga, v.capacidad);
  return { pct, sem: semaforoOcupacion(pct, verde, ambar) };
}

/** Ocupacion de un almacen intermedio (C21): 100 * stock / capacidad; el central no tiene semaforo. */
export function ocupacionAlmacen(a: AlmacenVisual, verde: number, ambar: number): { pct: number; sem: SemaforoOcupacion } | null {
  if (a.central || a.capacidad == null) return null;
  const pct = porcentaje(a.stock ?? 0, a.capacidad);
  return { pct, sem: semaforoOcupacion(pct, verde, ambar) };
}

/** Relleno, borde y color del glifo de la insignia de la unidad. La averia/taller es gris; el resto, por ocupacion. */
function estiloVehiculo(v: VehiculoVisual, verde: number, ambar: number) {
  if (FUERA_DE_SERVICIO.has(v.estado)) return { fill: GRIS, borde: '#fff', glifo: '#fff' };
  const { sem } = ocupacionVehiculo(v, verde, ambar);
  if (sem === 'vacio') return { fill: COLOR_OCUPACION.vacio, borde: BORDE_VACIO, glifo: BORDE_VACIO };
  return { fill: COLOR_OCUPACION[sem], borde: '#fff', glifo: '#fff' };
}

/** Urgencia de plazo de la unidad (pedido mas urgente a bordo): solo se marca en riesgo o critico. */
export function urgenciaVehiculo(v: VehiculoVisual, verde: number, ambar: number): Semaforo | null {
  if (v.holguraMinima == null) return null;
  return semaforo(v.holguraMinima, verde, ambar);
}

const sy = (y: number) => ALTO_KM - y;

/** D10-D13: insignia con el numero y el color del tipo de averia, en la esquina superior derecha del icono. */
export function InsigniaAveria({ tipo, x = 10, y = -10 }: { tipo: number; x?: number; y?: number }) {
  return (
    <g transform={`translate(${x},${y})`}>
      <circle r={7} fill={COLOR_AVERIA[tipo] ?? GRIS} stroke="#fff" strokeWidth={1.5} />
      <text y={3.4} textAnchor="middle" fontSize={9.5} fontWeight={800} fill={tipo === 1 ? '#1c2b36' : '#fff'}>{tipo}</text>
    </g>
  );
}

/** Unidad en taller: insignia blanca con una llave. */
export function InsigniaTaller({ x = 10, y = -10 }: { x?: number; y?: number }) {
  return (
    <g transform={`translate(${x},${y})`}>
      <circle r={7} fill="#fff" stroke={NAVY} strokeWidth={1.5} />
      <GlifoLlave color={NAVY} />
    </g>
  );
}

/** Reticula de calles (1 km) con ejes cada 10 km; solo se vuelve a dibujar al cambiar el zoom. */
const Reticula = memo(function Reticula({ escala }: { escala: number }) {
  const lineas: React.ReactNode[] = [];
  for (let x = 0; x <= ANCHO_KM; x++) {
    const mayor = x % 10 === 0;
    lineas.push(
      <line key={`v${x}`} x1={x} y1={0} x2={x} y2={ALTO_KM} stroke={mayor ? '#c3cfda' : '#e4eaf0'}
        strokeWidth={mayor ? 1.3 : 1} vectorEffect="non-scaling-stroke" />,
    );
  }
  for (let y = 0; y <= ALTO_KM; y++) {
    const mayor = y % 10 === 0;
    lineas.push(
      <line key={`h${y}`} x1={0} y1={sy(y)} x2={ANCHO_KM} y2={sy(y)} stroke={mayor ? '#c3cfda' : '#e4eaf0'}
        strokeWidth={mayor ? 1.3 : 1} vectorEffect="non-scaling-stroke" />,
    );
  }
  const fs = 10 * escala;
  for (let x = 0; x <= ANCHO_KM; x += 10) {
    lineas.push(<text key={`tx${x}`} x={x} y={ALTO_KM + 1.4 * (fs / 1)} fontSize={fs} textAnchor="middle" fill="#8695a3">{x}</text>);
  }
  for (let y = 10; y <= ALTO_KM; y += 10) {
    lineas.push(<text key={`ty${y}`} x={-0.6} y={sy(y) + fs * 0.35} fontSize={fs} textAnchor="end" fill="#8695a3">{y}</text>);
  }
  return <g>{lineas}</g>;
});

/**
 * Parte lo que falta de la ruta en el tramo hacia los clientes y el tramo de retorno al almacen (desde que termina
 * la ultima entrega), para dibujarlos distinto y que se note cuando la unidad ya va de regreso.
 */
function tramosRuta(tray: number[], desde: [number, number] | null, minuto: number, retornoDesde: number | null) {
  const pts: [number, number, number][] = [];
  if (desde) pts.push([desde[0], desde[1], minuto]);
  for (let i = 0; i < tray.length; i += 3) pts.push([tray[i], tray[i + 1], tray[i + 2]]);
  const corte = retornoDesde ?? Infinity;
  const ida: string[] = [];
  const vuelta: string[] = [];
  pts.forEach(([x, y, m], k) => {
    if (m <= corte) {
      ida.push(`${x},${sy(y)}`);
      return;
    }
    const previo = pts[k - 1];
    if (!vuelta.length && previo) {
      const f = m > previo[2] ? (corte - previo[2]) / (m - previo[2]) : 1;
      const c = `${previo[0] + (x - previo[0]) * f},${sy(previo[1] + (y - previo[1]) * f)}`;
      ida.push(c);
      vuelta.push(c);
    }
    vuelta.push(`${x},${sy(y)}`);
  });
  return { ida: ida.length >= 2 ? ida.join(' ') : '', vuelta: vuelta.length >= 2 ? vuelta.join(' ') : '' };
}

export default function MapaOperacion(props: Props) {
  const { estado, minutoAhora, vista, onVista, filtros, seleccion } = props;
  const wrapRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const lineaHoverRef = useRef<SVGLineElement>(null);
  const vehiculosRef = useRef(new Map<string, SVGGElement>());
  const [tam, setTam] = useState({ w: 1000, h: 700 });
  const [arrastrando, setArrastrando] = useState(false);
  const arrastre = useRef<{ x: number; y: number; vista: Vista; movido: boolean } | null>(null);

  // el SVG llena todo el contenedor (pantalla completa): se mide su tamano real y el viewBox toma su proporcion
  useEffect(() => {
    const el = svgRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entradas) => {
      const r = entradas[0]?.contentRect;
      if (r && r.width && r.height) setTam({ w: r.width, h: r.height });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // la vista garantiza que se vea al menos la region pedida; si el contenedor es mas ancho o mas alto, muestra mas ciudad
  const altoVista = (vista.ancho * VB_H) / VB_W;
  const proporcion = tam.w / Math.max(1, tam.h);
  const anchoVis = proporcion > VB_W / VB_H ? altoVista * proporcion : vista.ancho;
  const altoVis = anchoVis / proporcion;
  const viewBox = `${vista.cx - anchoVis / 2} ${vista.cy - altoVis / 2} ${anchoVis} ${altoVis}`;
  const escala = anchoVis / Math.max(240, tam.w); // km por pixel
  const escalaRef = useRef(escala);
  escalaRef.current = escala;
  const verde = estado.umbralVerde;
  const ambar = estado.umbralAmbar;
  const minuto = minutoAhora();

  // ---------------- animacion de las unidades ----------------
  const vehiculosVisibles = useMemo(
    () =>
      estado.vehiculos.filter((v) => {
        if (!vehiculoPasaFiltro(v, filtros, estado.umbralVerde, estado.umbralAmbar)) return false;
        if (EN_BASE.has(v.estado) && v.tray.length === 0) return filtros.unidadesEnAlmacen;
        return true;
      }),
    [estado.vehiculos, filtros, estado.umbralVerde, estado.umbralAmbar],
  );

  const posicionBase = useMemo(() => {
    // unidades en un almacen (si se muestran): en anillos alrededor del almacen
    const m = new Map<string, [number, number]>();
    const porBase = new Map<string, VehiculoVisual[]>();
    for (const v of vehiculosVisibles) {
      if (v.tray.length > 0 || !EN_BASE.has(v.estado)) continue;
      const k = `${v.x},${v.y}`;
      if (!porBase.has(k)) porBase.set(k, []);
      porBase.get(k)!.push(v);
    }
    porBase.forEach((lista) => {
      lista.forEach((v, i) => {
        const anillo = Math.floor(i / 12);
        const enAnillo = Math.min(12, lista.length - anillo * 12);
        const ang = (2 * Math.PI * (i % 12)) / enAnillo;
        const radioPx = 30 + anillo * 24;
        m.set(v.id, [Math.cos(ang) * radioPx, Math.sin(ang) * radioPx]);
      });
    });
    return m;
  }, [vehiculosVisibles]);

  /** C30: orientacion del glifo segun el tramo actual (calles ortogonales): espejo hacia la izquierda, ±90° en vertical. */
  const orientacionGlifo = (v: VehiculoVisual, m: number): string => {
    const n = v.tray.length / 3;
    if (n < 2) return '';
    let i = 0;
    while (i < n - 2 && v.tray[3 * (i + 1) + 2] < m) i++;
    const dx = v.tray[3 * (i + 1)] - v.tray[3 * i];
    const dy = v.tray[3 * (i + 1) + 1] - v.tray[3 * i + 1];
    if (Math.abs(dx) < 1e-9 && Math.abs(dy) < 1e-9) return '';
    if (Math.abs(dx) >= Math.abs(dy)) return dx < 0 ? 'scale(-1,1)' : '';
    return dy > 0 ? 'rotate(-90)' : 'rotate(90)'; // y del mapa crece hacia el norte; en el SVG, hacia abajo
  };

  const transformVehiculo = useCallback(
    (v: VehiculoVisual, m: number, s: number) => {
      const p = v.tray.length ? posicionEnTrayectoria(v.tray, m) ?? [v.x, v.y] : [v.x, v.y];
      const off = posicionBase.get(v.id);
      const x = p[0] + (off ? off[0] * s : 0);
      const y = sy(p[1]) + (off ? off[1] * s : 0);
      return `translate(${x},${y}) scale(${s})`;
    },
    [posicionBase],
  );

  useEffect(() => {
    let raf = 0;
    const paso = () => {
      const m = minutoAhora();
      const s = escalaRef.current;
      for (const v of vehiculosVisibles) {
        const g = vehiculosRef.current.get(v.id);
        if (!g) continue;
        g.setAttribute('transform', transformVehiculo(v, m, s));
        const glifo = g.querySelector('[data-glifo]');
        const o = orientacionGlifo(v, m);
        if (glifo && glifo.getAttribute('transform') !== o) glifo.setAttribute('transform', o);
      }
      raf = requestAnimationFrame(paso);
    };
    raf = requestAnimationFrame(paso);
    return () => cancelAnimationFrame(raf);
  }, [vehiculosVisibles, minutoAhora, transformVehiculo]);

  // ---------------- interaccion ----------------
  const aKm = useCallback((clientX: number, clientY: number): [number, number] | null => {
    const svg = svgRef.current;
    if (!svg) return null;
    const ctm = svg.getScreenCTM();
    if (!ctm) return null;
    const pt = svg.createSVGPoint();
    pt.x = clientX;
    pt.y = clientY;
    const p = pt.matrixTransform(ctm.inverse());
    return [p.x, ALTO_KM - p.y];
  }, []);

  /** Tramo de calle mas cercano al punto (en km) y su distancia en pixeles. */
  const tramoCercano = useCallback((x: number, y: number) => {
    const s = escalaRef.current;
    let mejor: { t: [number, number, number, number]; px: number } | null = null;
    const yr = Math.round(y);
    const x0 = Math.floor(x);
    if (yr >= 0 && yr <= ALTO_KM && x0 >= 0 && x0 < ANCHO_KM) {
      mejor = { t: [x0, yr, x0 + 1, yr], px: Math.abs(y - yr) / s };
    }
    const xr = Math.round(x);
    const y0 = Math.floor(y);
    if (xr >= 0 && xr <= ANCHO_KM && y0 >= 0 && y0 < ALTO_KM) {
      const px = Math.abs(x - xr) / s;
      if (!mejor || px < mejor.px) mejor = { t: [xr, y0, xr, y0 + 1], px };
    }
    return mejor;
  }, []);

  const objetivo = (e: React.PointerEvent | React.MouseEvent) =>
    (e.target as Element).closest('[data-tipo]') as SVGElement | null;

  const ocultarLinea = () => lineaHoverRef.current?.setAttribute('visibility', 'hidden');

  // C05: zoom con pellizco (dos punteros) en pantallas tactiles
  const punteros = useRef(new Map<number, { x: number; y: number }>());
  const pellizco = useRef<{ dist0: number; vista0: Vista } | null>(null);
  const distanciaPunteros = () => {
    const [a, b] = [...punteros.current.values()];
    return a && b ? Math.hypot(a.x - b.x, a.y - b.y) : 0;
  };
  const soltarPuntero = (e: React.PointerEvent) => {
    punteros.current.delete(e.pointerId);
    if (punteros.current.size < 2) pellizco.current = null;
  };

  const alMover = (e: React.PointerEvent) => {
    if (punteros.current.has(e.pointerId)) punteros.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const pz = pellizco.current;
    if (pz) {
      const d = distanciaPunteros();
      if (d > 0) {
        const ancho = Math.max(VB_W / 14, Math.min(VB_W, (pz.vista0.ancho * pz.dist0) / d));
        onVista(limitar({ ...pz.vista0, ancho }));
      }
      return;
    }
    const a = arrastre.current;
    if (a) {
      const dx = e.clientX - a.x;
      const dy = e.clientY - a.y;
      if (!a.movido && Math.hypot(dx, dy) > 4) {
        a.movido = true;
        setArrastrando(true);
        props.onHover(null, 0, 0);
      }
      if (a.movido) {
        const f = escalaRef.current;
        onVista(limitar({ cx: a.vista.cx - dx * f, cy: a.vista.cy - dy * f, ancho: a.vista.ancho }));
        ocultarLinea();
        return;
      }
    }
    const t = objetivo(e);
    if (t) {
      props.onHover({ tipo: t.dataset.tipo as Seleccion['tipo'], id: t.dataset.id! }, e.clientX, e.clientY);
      ocultarLinea();
      return;
    }
    props.onHover(null, 0, 0);
    const km = aKm(e.clientX, e.clientY);
    const linea = lineaHoverRef.current;
    if (!km || !linea) return;
    const s = escalaRef.current;
    const cruceCerca = props.onClicCruce && Math.hypot(km[0] - Math.round(km[0]), km[1] - Math.round(km[1])) / s <= 7;
    const tr = tramoCercano(km[0], km[1]);
    if (!cruceCerca && tr && tr.px <= 8) {
      linea.setAttribute('x1', String(tr.t[0]));
      linea.setAttribute('y1', String(sy(tr.t[1])));
      linea.setAttribute('x2', String(tr.t[2]));
      linea.setAttribute('y2', String(sy(tr.t[3])));
      linea.setAttribute('visibility', 'visible');
    } else {
      ocultarLinea();
    }
  };

  const alPresionar = (e: React.PointerEvent) => {
    if (e.button !== 0) return;
    punteros.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (punteros.current.size === 2) {
      // segundo dedo: empieza el pellizco y se cancela el arrastre
      pellizco.current = { dist0: distanciaPunteros(), vista0: vista };
      arrastre.current = null;
      setArrastrando(false);
      return;
    }
    arrastre.current = { x: e.clientX, y: e.clientY, vista, movido: false };
  };

  const alSoltar = (e: React.PointerEvent) => {
    const enPellizco = pellizco.current != null || punteros.current.size > 1;
    soltarPuntero(e);
    if (enPellizco) {
      arrastre.current = null;
      return;
    }
    const a = arrastre.current;
    arrastre.current = null;
    setArrastrando(false);
    if (!a || a.movido) return;
    const t = objetivo(e);
    if (t) {
      props.onAbrir({ tipo: t.dataset.tipo as Seleccion['tipo'], id: t.dataset.id! });
      return;
    }
    const km = aKm(e.clientX, e.clientY);
    if (!km) return;
    const s = escalaRef.current;
    const nx = Math.round(km[0]);
    const ny = Math.round(km[1]);
    if (props.onClicCruce && Math.hypot(km[0] - nx, km[1] - ny) / s <= 7 && nx >= 0 && nx <= ANCHO_KM && ny >= 0 && ny <= ALTO_KM) {
      props.onClicCruce(nx, ny, e.clientX, e.clientY);
      return;
    }
    const tr = tramoCercano(km[0], km[1]);
    if (tr && tr.px <= 8) props.onClicTramo(tr.t[0], tr.t[1], tr.t[2], tr.t[3]);
  };

  const vistaRef = useRef(vista);
  vistaRef.current = vista;
  const onVistaRef = useRef(onVista);
  onVistaRef.current = onVista;

  // C08: zoom con la rueda, centrado en el cursor, continuo (proporcional a deltaY) y animado ~150 ms
  const animZoom = useRef<{ desde: Vista; hasta: Vista; t0: number; raf: number } | null>(null);
  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return;
    const DURACION = 150;
    const paso = () => {
      const a = animZoom.current;
      if (!a) return;
      const k = Math.min(1, (performance.now() - a.t0) / DURACION);
      const f = 1 - Math.pow(1 - k, 3);
      onVistaRef.current({
        cx: a.desde.cx + (a.hasta.cx - a.desde.cx) * f,
        cy: a.desde.cy + (a.hasta.cy - a.desde.cy) * f,
        ancho: a.desde.ancho + (a.hasta.ancho - a.desde.ancho) * f,
      });
      if (k < 1) a.raf = requestAnimationFrame(paso);
      else animZoom.current = null;
    };
    const alRodar = (e: WheelEvent) => {
      e.preventDefault();
      const ctm = svg.getScreenCTM();
      if (!ctm) return;
      const pt = svg.createSVGPoint();
      pt.x = e.clientX;
      pt.y = e.clientY;
      const p = pt.matrixTransform(ctm.inverse());
      const delta = e.deltaMode === 1 ? e.deltaY * 33 : e.deltaY; // lineas -> pixeles
      const factor = Math.exp(Math.max(-300, Math.min(300, delta)) * 0.0015);
      // los pasos seguidos se acumulan sobre el destino de la animacion en curso
      const base = animZoom.current?.hasta ?? vistaRef.current;
      const nuevoAncho = Math.max(VB_W / 14, Math.min(VB_W, base.ancho * factor));
      const r = nuevoAncho / base.ancho;
      const hasta = limitar({ cx: p.x - (p.x - base.cx) * r, cy: p.y - (p.y - base.cy) * r, ancho: nuevoAncho });
      if (animZoom.current) cancelAnimationFrame(animZoom.current.raf);
      animZoom.current = { desde: vistaRef.current, hasta, t0: performance.now(), raf: 0 };
      animZoom.current.raf = requestAnimationFrame(paso);
    };
    svg.addEventListener('wheel', alRodar, { passive: false });
    return () => {
      svg.removeEventListener('wheel', alRodar);
      if (animZoom.current) cancelAnimationFrame(animZoom.current.raf);
    };
  }, []);

  // ---------------- capas ----------------
  // F01/F03: al seleccionar un pedido se resaltan las rutas de TODAS las unidades que lo llevan
  const claveResaltados = seleccion?.tipo === 'vehiculo' ? seleccion.id
    : seleccion?.tipo === 'pedido' ? vehiculosDePedido(estado, seleccion.id).join(',') : '';
  const resaltados = useMemo(() => new Set(claveResaltados ? claveResaltados.split(',') : []), [claveResaltados]);
  const hayResaltado = resaltados.size > 0;

  const rutas = useMemo(() => {
    if (filtros.rutas === 'ninguna') return [];
    return vehiculosVisibles
      .filter((v) => v.tray.length >= 6)
      .filter((v) => filtros.rutas === 'todas' || resaltados.has(v.id))
      .map((v) => ({ id: v.id, sel: resaltados.has(v.id) }))
      .sort((a, b) => Number(a.sel) - Number(b.sel));
  }, [vehiculosVisibles, filtros.rutas, resaltados]);

  const conteoEnAlmacen = useMemo(() => {
    const m = new Map<string, number>();
    for (const v of estado.vehiculos) {
      if (v.tray.length === 0 && EN_BASE.has(v.estado)) {
        const a = estado.almacenes.find((x) => Math.abs(x.x - v.x) < 1e-6 && Math.abs(x.y - v.y) < 1e-6);
        if (a) m.set(a.id, (m.get(a.id) ?? 0) + 1);
      }
    }
    return m;
  }, [estado.vehiculos, estado.almacenes]);

  return (
    <div
      ref={wrapRef}
      className={'map-wrap' + (arrastrando ? ' arrastrando' : '')}
      onPointerMove={alMover}
      onPointerDown={alPresionar}
      onPointerUp={alSoltar}
      onPointerCancel={soltarPuntero}
      onPointerLeave={(e) => {
        soltarPuntero(e);
        arrastre.current = null;
        setArrastrando(false);
        props.onHover(null, 0, 0);
        ocultarLinea();
      }}
    >
      <svg ref={svgRef} viewBox={viewBox} preserveAspectRatio="xMidYMid meet"
        style={{ width: '100%', height: '100%' }}
        xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Mapa de la ciudad con la operación en curso">
        <rect x={-50} y={-50} width={VB_W + 100} height={VB_H + 100} fill="#fbfcfe" />
        <Reticula escala={Math.round(escala * 1000) / 1000} />

        {filtros.mostrarBloqueos &&
          estado.bloqueos.map((b) => {
            const pts: string[] = [];
            for (let i = 0; i < b.puntos.length; i += 2) pts.push(`${b.puntos[i]},${sy(b.puntos[i + 1])}`);
            return (
              <polyline key={b.id} points={pts.join(' ')} fill="none" stroke={COLOR_BLOQUEO} strokeWidth={5}
                strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" opacity={0.9}>
                <title>{`Calle bloqueada (${b.id}) — dato real del curso`}</title>
              </polyline>
            );
          })}
        {estado.tramosManuales.map((t) => (
          <line key={t.join('-')} x1={t[0]} y1={sy(t[1])} x2={t[2]} y2={sy(t[3])} stroke="#a15b16" strokeWidth={6}
            strokeDasharray="5 4" vectorEffect="non-scaling-stroke">
            <title>Calle bloqueada manualmente (clic para reabrir)</title>
          </line>
        ))}

        {rutas.map((r) => {
          const v = estado.vehiculos.find((x) => x.id === r.id)!;
          const desde = posicionEnTrayectoria(v.tray, minuto);
          let resto = v.tray;
          // recorta lo ya recorrido para que la ruta empiece en la unidad
          for (let i = 0; i < v.tray.length; i += 3) {
            if (v.tray[i + 2] > minuto) {
              resto = v.tray.slice(i);
              break;
            }
            if (i + 3 >= v.tray.length) resto = v.tray.slice(i);
          }
          const opacidad = hayResaltado ? (r.sel ? 0.95 : 0.15) : 0.55;
          const { ida, vuelta } = tramosRuta(resto, desde, minuto, v.retornoDesdeMin);
          return (
            <g key={`ruta-${r.id}`} opacity={opacidad}>
              {ida && (
                <polyline points={ida} fill="none" stroke={COLOR_RUTA} strokeWidth={r.sel ? 3.2 : 2} strokeDasharray="7 5"
                  strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
              )}
              {vuelta && (
                <polyline points={vuelta} fill="none" stroke={COLOR_RETORNO} strokeWidth={r.sel ? 3.6 : 2.6} strokeDasharray="0.5 6"
                  strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
              )}
            </g>
          );
        })}

        <line ref={lineaHoverRef} x1={0} y1={0} x2={0} y2={0} stroke={NAVY} strokeOpacity={0.35} strokeWidth={8}
          strokeLinecap="round" vectorEffect="non-scaling-stroke" visibility="hidden" pointerEvents="none" />

        {estado.almacenes.filter((a) => almacenPasaFiltro(a, filtros, verde, ambar)).map((a) => {
          const ocup = ocupacionAlmacen(a, verde, ambar);
          const vacio = ocup?.sem === 'vacio';
          const color = ocup == null ? NAVY : COLOR_OCUPACION[ocup.sem];
          const sel = seleccion?.tipo === 'almacen' && seleccion.id === a.id;
          const n = conteoEnAlmacen.get(a.id) ?? 0;
          return (
            <g key={a.id} className="map-select-hit" data-tipo="almacen" data-id={a.id}
              transform={`translate(${a.x},${sy(a.y)}) scale(${escala})`}>
              <rect x={-13} y={-13} width={26} height={26} rx={6} fill={color} stroke={sel ? NAVY : vacio ? BORDE_VACIO : '#fff'} strokeWidth={sel ? 3 : vacio ? 2 : 1.5} />
              <GlifoAlmacen color={vacio ? BORDE_VACIO : '#fff'} />
              <text y={27} textAnchor="middle" fontSize={10} fontWeight={700} fill="#0a2c43" stroke="#fbfcfe" strokeWidth={3} paintOrder="stroke">
                {a.nombre}
              </text>
              <text y={39} textAnchor="middle" fontSize={9.5} fontWeight={600} fill={vacio ? BORDE_VACIO : color} stroke="#fbfcfe" strokeWidth={3} paintOrder="stroke">
                {a.capacidad == null ? 'Stock: sin límite' : `Stock ${(a.stock ?? 0).toLocaleString('es-PE')} / ${a.capacidad.toLocaleString('es-PE')} (${Math.round(ocup?.pct ?? 0)} %)`}
              </text>
              {n > 0 && (
                <g transform="translate(14,-14)">
                  <circle r={8} fill="#fff" stroke={NAVY} strokeWidth={1.2} />
                  <text y={3.2} textAnchor="middle" fontSize={8.5} fontWeight={700} fill={NAVY}>{n}</text>
                </g>
              )}
            </g>
          );
        })}

        {filtros.mostrarPedidos &&
          estado.pedidos.map((p) => {
            const h = ((p.limiteMin - minuto) / (p.plazo * 60)) * 100;
            const sem = semaforo(h, verde, ambar);
            if (filtros.soloCriticos && sem === 'verde') return null;
            const sel = seleccion?.tipo === 'pedido' && seleccion.id === p.id;
            return (
              <g key={p.id} className="map-select-hit" data-tipo="pedido" data-id={p.id}
                transform={`translate(${p.x},${sy(p.y)}) scale(${escala})`}>
                <circle r={9} fill={COLOR_SEMAFORO[sem]} stroke={sel ? NAVY : '#fff'} strokeWidth={sel ? 3 : 1.5} />
                <g transform="scale(0.78)">
                  <GlifoPaquete />
                </g>
              </g>
            );
          })}

        {vehiculosVisibles.map((v) => {
          const sel = resaltados.has(v.id);
          const est = estiloVehiculo(v, verde, ambar);
          const urgencia = FUERA_DE_SERVICIO.has(v.estado) ? null : urgenciaVehiculo(v, verde, ambar);
          const atenuado = v.estado === 'ALIMENTACION' || v.estado === 'MANTENIMIENTO' || (hayResaltado && !sel);
          return (
            <g key={v.id} ref={(el) => {
              if (el) vehiculosRef.current.set(v.id, el);
              else vehiculosRef.current.delete(v.id);
            }}
              className="map-select-hit" data-tipo="vehiculo" data-id={v.id} transform={transformVehiculo(v, minuto, escala)}
              opacity={atenuado ? 0.6 : 1}>
              <circle r={12} fill={est.fill} stroke={sel ? '#0a2c43' : est.borde} strokeWidth={sel ? 3 : est.borde === '#fff' ? 1.5 : 2} />
              <g data-glifo="" transform={orientacionGlifo(v, minuto)}>
                <GlifoVehiculo tipo={v.tipo} color={est.glifo} />
              </g>
              {urgencia && urgencia !== 'verde' && (
                <circle cx={-9} cy={-9} r={4.2} fill={COLOR_SEMAFORO[urgencia]} stroke="#fff" strokeWidth={1.4} />
              )}
              {v.estado === 'AVERIADA' && v.tipoAveria != null && <InsigniaAveria tipo={v.tipoAveria} />}
              {v.estado === 'EN_TALLER' && <InsigniaTaller />}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function limitar(v: Vista): Vista {
  return {
    ancho: v.ancho,
    cx: Math.max(-MARGEN, Math.min(ANCHO_KM + MARGEN, v.cx)),
    cy: Math.max(-MARGEN, Math.min(ALTO_KM + MARGEN, v.cy)),
  };
}

export function acercar(v: Vista, factor: number): Vista {
  return limitar({ ...v, ancho: Math.max(VB_W / 14, Math.min(VB_W, v.ancho / factor)) });
}

export function centrarEn(v: Vista, x: number, y: number, anchoMax = VB_W / 1.8): Vista {
  return limitar({ cx: x, cy: ALTO_KM - y, ancho: Math.min(v.ancho, anchoMax) });
}

export const ANCHO_VISTA_COMPLETA = VB_W;
