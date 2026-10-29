'use client';

import { useEffect, useRef, useState } from 'react';
import type { AlertaVisual } from '@/lib/tipos';

const TIPOS_ALERTA: Record<string, string> = {
  REASIGNACION: 'Reasignación de pedido',
  TRANSBORDO: 'Transbordo por avería',
  AVERIA: 'Avería',
  COLAPSO: 'Colapso logístico',
  REPROGRAMACION: 'Pedido reprogramado',
  RECALCULO: 'Recálculo de ruta',
  BLOQUEO_MANUAL: 'Bloqueo manual',
  PEDIDO_MANUAL: 'Pedido registrado',
  CANCELACION: 'Pedido cancelado',
};

interface Toast {
  id: number;
  tipo: string;
  texto: string;
}

export interface AvisoLocal {
  id: number;
  texto: string;
  tipo: 'ok' | 'error';
}

/**
 * Alertas visuales (C22): cuando ocurre una reasignacion de pedidos entre
 * vehiculos, una averia, un recalculo por bloqueo o el colapso, aparece un
 * aviso que se cierra solo. Las alertas que ya existian al abrir la pagina no
 * se repiten.
 */
export default function Toasts({ alertas, idCorrida, avisos, onQuitarAviso }: {
  alertas: AlertaVisual[];
  idCorrida: string;
  avisos: AvisoLocal[];
  onQuitarAviso: (id: number) => void;
}) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const ultimo = useRef<{ corrida: string; id: number } | null>(null);

  useEffect(() => {
    const maximo = alertas.reduce((m, a) => Math.max(m, a.id), 0);
    if (!ultimo.current || ultimo.current.corrida !== idCorrida) {
      ultimo.current = { corrida: idCorrida, id: maximo };
      return;
    }
    const nuevas = alertas.filter((a) => a.id > ultimo.current!.id && TIPOS_ALERTA[a.tipo]).sort((a, b) => a.id - b.id);
    ultimo.current = { corrida: idCorrida, id: Math.max(ultimo.current.id, maximo) };
    if (!nuevas.length) return;
    setToasts((prev) => [...prev, ...nuevas.map((a) => ({ id: a.id, tipo: a.tipo, texto: a.texto }))].slice(-5));
    for (const a of nuevas) {
      setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== a.id)), a.tipo === 'COLAPSO' ? 20000 : 8000);
    }
  }, [alertas, idCorrida]);

  return (
    <div className="toasts" aria-live="polite">
      {avisos.map((a) => (
        <div key={'aviso' + a.id} className={'toast ' + (a.tipo === 'error' ? 'averia' : '')}>
          <button aria-label="Cerrar" onClick={() => onQuitarAviso(a.id)}>×</button>
          <div className="toast-tipo">{a.tipo === 'error' ? 'No se pudo completar' : 'Listo'}</div>
          <div>{a.texto}</div>
        </div>
      ))}
      {toasts.map((t) => (
        <div key={t.id} className={'toast ' + t.tipo.toLowerCase()}>
          <button aria-label="Cerrar" onClick={() => setToasts((prev) => prev.filter((x) => x.id !== t.id))}>×</button>
          <div className="toast-tipo">{TIPOS_ALERTA[t.tipo] ?? t.tipo}</div>
          <div>{t.texto}</div>
        </div>
      ))}
    </div>
  );
}
