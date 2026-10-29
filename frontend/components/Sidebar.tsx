'use client';

import { useEffect } from 'react';

export type Modo = 'simulacion' | 'diaria';
export type PestanaId = 'parametros' | 'panorama' | 'cierre' | 'pedidos';

interface Props {
  abierto: boolean;
  onCerrar: () => void;
  modo: Modo;
  onModo: (m: Modo) => void;
  pestana: PestanaId;
  onIr: (p: PestanaId) => void;
  /** Hay una corrida en curso: se avisa de que su modo no cambia al navegar. */
  corridaEnCurso: boolean;
  modoCorrida: Modo | null;
}

const ITEMS: Record<Modo, [PestanaId, string][]> = {
  simulacion: [
    ['parametros', '1 · Parámetros de la simulación'],
    ['panorama', '2 · Panorama (mapa)'],
    ['cierre', '3 · Cierre'],
    ['pedidos', '4 · Pedidos'],
  ],
  diaria: [
    ['parametros', '1 · Parámetros de la operación'],
    ['panorama', '2 · Panorama (mapa)'],
    ['cierre', '3 · Cierre'],
    ['pedidos', '4 · Pedidos y registro manual'],
  ],
};

/**
 * Barra lateral que se esconde con un clic: deja pasar entre la simulacion (5D y colapso) y la operacion
 * dia a dia (tiempo real, que no es una simulacion) y ir a cada pantalla. Se abre con el boton ☰.
 */
export default function Sidebar({ abierto, onCerrar, modo, onModo, pestana, onIr, corridaEnCurso, modoCorrida }: Props) {
  useEffect(() => {
    if (!abierto) return;
    const tecla = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCerrar();
    };
    document.addEventListener('keydown', tecla);
    return () => document.removeEventListener('keydown', tecla);
  }, [abierto, onCerrar]);

  return (
    <>
      {abierto && <div className="sidebar-fondo" onClick={onCerrar} />}
      <aside className={'sidebar' + (abierto ? ' abierta' : '')} aria-hidden={!abierto} aria-label="Menú principal">
        <div className="sidebar-cab">
          <b>PaqRap</b>
          <button className="modal-close" type="button" aria-label="Ocultar menú" onClick={onCerrar}>×</button>
        </div>
        <div className="sidebar-modos" role="tablist" aria-label="Modo">
          <button role="tab" aria-selected={modo === 'simulacion'} className={modo === 'simulacion' ? 'activo' : ''}
            onClick={() => onModo('simulacion')}>Simulación</button>
          <button role="tab" aria-selected={modo === 'diaria'} className={modo === 'diaria' ? 'activo' : ''}
            onClick={() => onModo('diaria')}>Operación día a día</button>
        </div>
        <p className="sidebar-nota">
          {modo === 'simulacion'
            ? 'Simulación acelerada: periodo de 5 días o colapso logístico.'
            : 'Tiempo real (1:1) con la hora de hoy. No es una simulación: los pedidos se registran en vivo.'}
        </p>
        <nav>
          {ITEMS[modo].map(([id, etiqueta]) => (
            <button key={id} className={'sidebar-item' + (pestana === id ? ' activo' : '')}
              onClick={() => { onIr(id); onCerrar(); }}>{etiqueta}</button>
          ))}
        </nav>
        {corridaEnCurso && modoCorrida && modoCorrida !== modo && (
          <p className="sidebar-aviso">
            Hay una corrida de {modoCorrida === 'diaria' ? 'operación día a día' : 'simulación'} en curso. Deténgala para iniciar una de este modo.
          </p>
        )}
      </aside>
    </>
  );
}
