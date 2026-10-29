'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api';
import type { EstadoVisual } from './tipos';

/**
 * Consulta periodica del estado compartido de la corrida (C5, C20). Entre dos
 * consultas, el reloj de la simulacion se extrapola en el navegador con el
 * factor de aceleracion, y las unidades se mueven sobre su trayectoria: asi el
 * mapa se anima con fluidez sin pedir la posicion a cada instante.
 */
export function useEstadoSimulacion(intervaloMs = 1000) {
  const [estado, setEstado] = useState<EstadoVisual | null>(null);
  const [error, setError] = useState<string | null>(null);
  const estadoRef = useRef<EstadoVisual | null>(null);
  const recibidoRef = useRef(0);
  const temporizador = useRef<ReturnType<typeof setTimeout> | null>(null);
  const activoRef = useRef(true);

  const consultar = useCallback(async () => {
    try {
      const e = await api.estado();
      if (!activoRef.current) return;
      estadoRef.current = e;
      recibidoRef.current = performance.now();
      setEstado(e);
      setError(null);
    } catch (err) {
      if (activoRef.current) setError(err instanceof Error ? err.message : 'Error de conexión');
    }
  }, []);

  useEffect(() => {
    activoRef.current = true;
    const ciclo = async () => {
      await consultar();
      if (activoRef.current) temporizador.current = setTimeout(ciclo, intervaloMs);
    };
    ciclo();
    return () => {
      activoRef.current = false;
      if (temporizador.current) clearTimeout(temporizador.current);
    };
  }, [consultar, intervaloMs]);

  /** Minuto simulado (desde el inicio de la corrida) en este instante, extrapolado. */
  const minutoAhora = useCallback(() => {
    const e = estadoRef.current;
    if (!e) return 0;
    if (e.estadoCorrida !== 'EN_CURSO') return e.minutoActual;
    const realMs = Math.min(performance.now() - recibidoRef.current, 6000);
    let m = e.minutoActual + (realMs / 60000) * e.factor;
    if (e.minutoFin != null) m = Math.min(m, e.minutoFin);
    return m;
  }, []);

  return { estado, error, minutoAhora, refrescar: consultar };
}

/** Posicion {x, y} sobre una trayectoria plana [x, y, minuto, x, y, minuto, ...] en el minuto dado. */
export function posicionEnTrayectoria(tray: number[], minuto: number): [number, number] | null {
  const n = tray.length / 3;
  if (n === 0) return null;
  if (minuto <= tray[2]) return [tray[0], tray[1]];
  for (let i = 0; i < n - 1; i++) {
    const t0 = tray[3 * i + 2];
    const t1 = tray[3 * (i + 1) + 2];
    if (minuto <= t1) {
      const f = t1 > t0 ? (minuto - t0) / (t1 - t0) : 1;
      return [tray[3 * i] + (tray[3 * (i + 1)] - tray[3 * i]) * f, tray[3 * i + 1] + (tray[3 * (i + 1) + 1] - tray[3 * i + 1]) * f];
    }
  }
  return [tray[3 * (n - 1)], tray[3 * (n - 1) + 1]];
}

/** Km que se recorren sobre la trayectoria entre dos minutos (por las calles de la reticula). */
export function kmEnTrayectoria(tray: number[], desde: number, hasta: number): number {
  const n = tray.length / 3;
  if (n < 2 || hasta <= desde) return 0;
  let km = 0;
  for (let i = 0; i < n - 1; i++) {
    const t0 = tray[3 * i + 2];
    const t1 = tray[3 * (i + 1) + 2];
    const a = Math.max(t0, desde);
    const b = Math.min(t1, hasta);
    if (b <= a || t1 <= t0) continue;
    const seg = Math.abs(tray[3 * (i + 1)] - tray[3 * i]) + Math.abs(tray[3 * (i + 1) + 1] - tray[3 * i + 1]);
    km += (seg * (b - a)) / (t1 - t0);
  }
  return km;
}
