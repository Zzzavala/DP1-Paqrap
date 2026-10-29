'use client';

import { forwardRef, useImperativeHandle, useLayoutEffect, useRef, useState } from 'react';
import type { EstadoVisual } from '@/lib/tipos';
import {
  claseOcupacion, COLOR_AVERIA, ETIQUETA_OCUPACION, ETIQUETA_SEMAFORO, ETIQUETA_TIPO_VEHICULO, fechaHoraCompleta, semaforo,
} from '@/lib/formato';
import { ocupacionAlmacen, ocupacionVehiculo, type Seleccion } from './MapaOperacion';

export interface TooltipApi {
  mostrar: (s: Seleccion | null, x: number, y: number) => void;
}

/**
 * Popup compacto al pasar el mouse sobre un vehiculo, pedido o almacen
 * (prototipo v04, "detalle bajo demanda"): resumen esencial y aviso de que un
 * clic abre el detalle completo. Tiene su propio estado para no redibujar el mapa.
 */
const Tooltip = forwardRef<TooltipApi, { estado: EstadoVisual; minuto: () => number }>(function Tooltip({ estado, minuto }, ref) {
  const [actual, setActual] = useState<{ s: Seleccion; x: number; y: number } | null>(null);
  const caja = useRef<HTMLDivElement>(null);

  useImperativeHandle(ref, () => ({
    mostrar: (s, x, y) => setActual((prev) => {
      if (!s) return prev === null ? prev : null;
      if (prev && prev.s.id === s.id && prev.s.tipo === s.tipo && Math.abs(prev.x - x) < 2 && Math.abs(prev.y - y) < 2) return prev;
      return { s, x, y };
    }),
  }), []);

  useLayoutEffect(() => {
    const el = caja.current;
    if (!el || !actual) return;
    const pad = 16;
    const maxX = window.innerWidth - el.offsetWidth - 10;
    const maxY = window.innerHeight - el.offsetHeight - 10;
    el.style.left = Math.min(actual.x + pad, Math.max(10, maxX)) + 'px';
    el.style.top = Math.min(actual.y + pad, Math.max(10, maxY)) + 'px';
  });

  if (!actual) return null;
  const { s } = actual;
  let contenido: React.ReactNode = null;
  if (s.tipo === 'vehiculo') {
    const v = estado.vehiculos.find((x) => x.id === s.id);
    if (!v) return null;
    const sem = v.holguraMinima == null ? null : semaforo(v.holguraMinima, estado.umbralVerde, estado.umbralAmbar);
    const ocup = ocupacionVehiculo(v, estado.umbralVerde, estado.umbralAmbar);
    contenido = (
      <>
        <div className="tt-title">Vehículo {v.id} · {ETIQUETA_TIPO_VEHICULO[v.tipo]}</div>
        <div className="tt-row"><span>Estado</span><b>{v.estadoEtiqueta}</b></div>
        {v.tipoAveria != null && (
          <div className="tt-row"><span>{v.estado === 'EN_TALLER' ? 'En taller' : 'Avería'}</span>
            <b style={{ color: COLOR_AVERIA[v.tipoAveria] }}>
              Avería tipo {v.tipoAveria} · {v.estado === 'EN_TALLER' ? 'disponible' : 'hasta'}{' '}
              {v.finAveriaMin != null ? fechaHoraCompleta(estado.inicioMs + v.finAveriaMin * 60000) : '—'}
            </b></div>
        )}
        <div className="tt-row"><span>Ocupación</span>
          <b>{v.carga} / {v.capacidad} ({Math.round(ocup.pct)} %) <span className={claseOcupacion(ocup.sem)}>{ETIQUETA_OCUPACION[ocup.sem]}</span></b></div>
        {sem && <div className="tt-row"><span>Urgencia de plazo</span><b>{ETIQUETA_SEMAFORO[sem]}</b></div>}
        <div className="tt-row"><span>Próxima parada</span><b>{v.destino || '—'}</b></div>
      </>
    );
  } else if (s.tipo === 'pedido') {
    const p = estado.pedidos.find((x) => x.id === s.id);
    if (!p) return null;
    const h = ((p.limiteMin - minuto()) / (p.plazo * 60)) * 100;
    const sem = semaforo(h, estado.umbralVerde, estado.umbralAmbar);
    contenido = (
      <>
        <div className="tt-title">Pedido {p.id}</div>
        <div className="tt-row"><span>Cliente</span><b>{p.cliente}</b></div>
        <div className="tt-row"><span>Paquetes · plazo</span><b>{p.cantidad} · {p.plazo} h</b></div>
        <div className="tt-row"><span>Semáforo</span><b>{ETIQUETA_SEMAFORO[sem]}{p.reprogramado ? ' (reprogramado)' : ''}</b></div>
        <div className="tt-row"><span>Unidad asignada</span><b>{p.vehiculos || 'Pendiente'}</b></div>
      </>
    );
  } else {
    const a = estado.almacenes.find((x) => x.id === s.id);
    if (!a) return null;
    const ocup = ocupacionAlmacen(a, estado.umbralVerde, estado.umbralAmbar);
    const pct = ocup?.pct ?? null;
    const enAlmacen = estado.vehiculos.filter((v) => v.tray.length === 0 && Math.abs(v.x - a.x) < 1e-6 && Math.abs(v.y - a.y) < 1e-6).map((v) => v.id);
    contenido = (
      <>
        <div className="tt-title">{a.nombre}</div>
        <div className="tt-row"><span>Stock</span><b>{a.capacidad == null ? 'Sin límite' : `${(a.stock ?? 0).toLocaleString('es-PE')} / ${a.capacidad.toLocaleString('es-PE')} (${Math.round(pct ?? 0)} %)`}</b></div>
        {ocup && <div className="tt-row"><span>Ocupación</span><b><span className={claseOcupacion(ocup.sem)}>{ETIQUETA_OCUPACION[ocup.sem]}</span></b></div>}
        <div className="tt-row"><span>Unidades en el almacén ({enAlmacen.length})</span><b>{enAlmacen.slice(0, 8).join(', ') || 'Ninguna'}{enAlmacen.length > 8 ? '…' : ''}</b></div>
        <div className="tt-row"><span>Unidades que arriban ({a.arriban.length})</span><b>{a.arriban.slice(0, 5).join(' · ') || 'Ninguna'}{a.arriban.length > 5 ? '…' : ''}</b></div>
      </>
    );
  }
  return (
    <div className="hover-tooltip" ref={caja} style={{ left: actual.x + 16, top: actual.y + 16 }}>
      {contenido}
      <div className="tt-more">Clic para ver el detalle completo</div>
    </div>
  );
});

export default Tooltip;
