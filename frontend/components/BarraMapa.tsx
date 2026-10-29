'use client';

import { useEffect, useState } from 'react';
import type { EstadoVisual } from '@/lib/tipos';
import {
  ahoraLimaTexto, BORDE_VACIO, COLOR_OCUPACION, ETIQUETA_OCUPACION, fechaHoraCompleta, numero, semaforoOcupacion, transcurridoReal,
  transcurridoSimulado,
} from '@/lib/formato';
import { ocupacionesGlobales } from './TopBar';

interface Props {
  estado: EstadoVisual | null;
  minutoAhora: () => number;
  puedeIniciar: boolean;
  ocupado: boolean;
  onIniciar: () => void;
  onDetener: () => void;
  infoAbierta: boolean;
  onInfo: () => void;
}

const ESTADO_TEXTO: Record<string, string> = {
  SIN_CORRIDA: 'Sin corrida',
  EN_CURSO: 'En curso',
  FINALIZADA: 'Finalizada',
  DETENIDA: 'Detenida',
};

/**
 * Barra minima sobre el mapa a pantalla completa: estado, los 4 tiempos de la rubrica (C12-C16: fecha-hora simulada,
 * transcurrido simulado, fecha-hora actual y transcurrido real) siempre visibles, y los controles de la corrida.
 */
export default function BarraMapa({ estado, minutoAhora, puedeIniciar, ocupado, onIniciar, onDetener, infoAbierta, onInfo }: Props) {
  const [, setTic] = useState(0);
  // la hora actual solo existe en el navegador: se muestra despues de montar para no desajustar la pagina estatica
  const [montado, setMontado] = useState(false);
  const [base, setBase] = useState({ seg: 0, desde: 0 });
  useEffect(() => {
    setMontado(true);
    const t = setInterval(() => setTic((x) => x + 1), 500);
    return () => clearInterval(t);
  }, []);
  useEffect(() => {
    if (estado) setBase({ seg: estado.tiempoRealSeg, desde: performance.now() });
  }, [estado]);
  const enCurso = estado?.estadoCorrida === 'EN_CURSO';
  const hay = !!estado && estado.estadoCorrida !== 'SIN_CORRIDA';
  const minuto = hay ? minutoAhora() : 0;
  const segundosReales = estado ? (enCurso ? base.seg + (performance.now() - base.desde) / 1000 : estado.tiempoRealSeg) : 0;
  // G01-G04: ocupacion global de la flota y de los almacenes, siempre visible con su semaforo
  const ocup = estado && hay ? ocupacionesGlobales(estado) : null;
  const pildora = (etiqueta: string, pct: number, detalle: string) => {
    const sem = semaforoOcupacion(pct, estado!.umbralVerde, estado!.umbralAmbar);
    return (
      <span className="barra-ocup" title={`${etiqueta}: ${detalle} · ${ETIQUETA_OCUPACION[sem]}`}>
        <i style={{ background: COLOR_OCUPACION[sem], borderColor: sem === 'vacio' ? BORDE_VACIO : COLOR_OCUPACION[sem] }} />
        <em>{etiqueta}</em><b>{numero(pct, 0)} %</b>
      </span>
    );
  };
  return (
    <div className="barra-mapa" onPointerDown={(e) => e.stopPropagation()}>
      <span className={'barra-estado' + (enCurso ? ' en-curso' : '')}>{estado ? ESTADO_TEXTO[estado.estadoCorrida] : 'Conectando…'}</span>
      {ocup && (
        <div className="barra-ocupaciones">
          {pildora('Flota', ocup.flota.pct, `${numero(ocup.flota.carga)} / ${numero(ocup.flota.cap)} paquetes en unidades operativas`)}
          {pildora('Almacenes', ocup.almacenes.pct, `${numero(ocup.almacenes.stock)} / ${numero(ocup.almacenes.cap)} en intermedios (central sin límite)`)}
        </div>
      )}
      <div className="barra-tiempos" aria-live="polite">
        <span><em>Simulado</em><b>{hay && estado ? fechaHoraCompleta(estado.inicioMs + minuto * 60000) : '—'}</b></span>
        <span><em>Transcurrido sim.</em><b>{hay ? transcurridoSimulado(minuto) : '—'}</b></span>
        <span><em>Actual</em><b suppressHydrationWarning>{montado ? ahoraLimaTexto() : '—'}</b></span>
        <span><em>Transcurrido real</em><b>{hay ? transcurridoReal(segundosReales) : '—'}</b></span>
      </div>
      <button className={'btn-flot' + (infoAbierta ? ' activo' : '')} onClick={onInfo} title="Ocupación global, escenario e información de la corrida">Información</button>
      <button className="btn btn-sm btn-start" disabled={!puedeIniciar || ocupado || enCurso} onClick={onIniciar}>Iniciar</button>
      <button className="btn btn-sm btn-stop" disabled={!enCurso || ocupado} onClick={onDetener}>Detener</button>
    </div>
  );
}
