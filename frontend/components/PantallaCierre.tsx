'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, enlaces } from '@/lib/api';
import type { CierreCorrida, CorridaHistorial, EstadoVisual } from '@/lib/tipos';
import { duracion, etiquetaCodigo, numero, soles } from '@/lib/formato';

interface Props {
  estado: EstadoVisual | null;
  cierreRecibido: CierreCorrida | null;
}

const ALGORITMO: Record<string, string> = {
  ACO: 'Optimización por Colonia de Hormigas (ACO)',
  GA: 'Algoritmo Genético (GA)',
};
const ETIQUETA_PARAMETRO: Record<string, string> = {
  escenario: 'Escenario',
  algoritmo: 'Algoritmo',
  semilla: 'Semilla',
  fechaInicio: 'Fecha/hora de inicio',
  ventanaPlanificacionMin: 'Ventana de planificación (min)',
  diasSimulacion: 'Días simulados',
  duracionObjetivoMin: 'Duración objetivo (min reales)',
  factorAceleracion: 'Aceleración (h simuladas por h real)',
  umbralColapsoVencidos: 'Colapso: incumplidos simultáneos',
  umbralColapsoBacklog: 'Colapso: pedidos sin unidad',
  umbralVerde: 'Semáforo verde (≥ %)',
  umbralAmbar: 'Semáforo ámbar (≥ %)',
  velocidades: 'Velocidades',
  flota: 'Flota',
  averiasPorDia: 'Averías programadas por día',
};

const ESCENARIO: Record<string, string> = {
  TIEMPO_REAL: 'Día a día',
  SIMULACION_5D: 'Periodo (5 días)',
  COLAPSO_LOGISTICO: 'Hasta el colapso',
};

function Kpi({ etiqueta, valor, sub }: { etiqueta: string; valor: string; sub?: string }) {
  return (
    <div className="kpi">
      <div className="kpi-label">{etiqueta}</div>
      <div className="kpi-value">{valor}</div>
      {sub && <div className="kpi-sub">{sub}</div>}
    </div>
  );
}

/** V-3 · Cierre del periodo y reportes del Modulo D (D15-D25), con el historial de corridas (D24). */
export default function PantallaCierre({ estado, cierreRecibido }: Props) {
  const [cierre, setCierre] = useState<CierreCorrida | null>(cierreRecibido);
  const [error, setError] = useState<string | null>(null);
  const [historial, setHistorial] = useState<CorridaHistorial[]>([]);
  const [deHistorial, setDeHistorial] = useState(false);

  const enCurso = estado?.estadoCorrida === 'EN_CURSO';
  const hayCorrida = !!estado && estado.estadoCorrida !== 'SIN_CORRIDA';

  const cargarActual = useCallback(async () => {
    if (!hayCorrida) return;
    try {
      setCierre(await api.cierre());
      setDeHistorial(false);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cargar el cierre');
    }
  }, [hayCorrida]);

  const cargarHistorial = useCallback(async () => {
    try {
      setHistorial(await api.corridas());
    } catch {
      setHistorial([]);
    }
  }, []);

  useEffect(() => {
    if (cierreRecibido) {
      setCierre(cierreRecibido);
      setDeHistorial(false);
    }
  }, [cierreRecibido]);

  useEffect(() => {
    if (!cierreRecibido) cargarActual();
    cargarHistorial();
  }, [cargarActual, cargarHistorial, cierreRecibido, estado?.idCorrida, estado?.estadoCorrida]);

  // vista previa que se actualiza mientras la corrida sigue en curso
  useEffect(() => {
    if (!enCurso || deHistorial) return;
    const t = setInterval(cargarActual, 5000);
    return () => clearInterval(t);
  }, [enCurso, deHistorial, cargarActual]);

  const verCorrida = async (id: string) => {
    try {
      setCierre(await api.cierreCorrida(id));
      setDeHistorial(true);
      setError(null);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cargar la corrida');
    }
  };

  const r = cierre?.resumen;
  const esColapso = cierre?.escenario === 'COLAPSO_LOGISTICO';
  return (
    <section>
      <h2>Cierre del periodo</h2>
      <p className="note">
        Se genera al detener la corrida, al completarse el último día de la simulación del periodo o al detectarse el colapso
        logístico. {enCurso && !deHistorial ? 'La corrida sigue en curso: esta es una vista previa con el estado actual.' : ''}
      </p>
      {error && <div className="banner banner-error">{error}</div>}
      {!cierre && !error && (
        <p className="empty-hint">
          Aún no se ha detenido ninguna corrida. Pulsa «Iniciar corrida» y luego «Detener corrida» (o espera a que termine el
          periodo o se detecte el colapso) para generar este cierre.
        </p>
      )}

      {cierre && r && (
        <>
          {deHistorial && (
            <div className="banner banner-info">
              Mostrando una corrida del historial: {cierre.idCorrida}.{' '}
              {hayCorrida && <button className="btn btn-sm btn-ghost" onClick={cargarActual}>Volver a la corrida actual</button>}
            </div>
          )}
          <p className="hint" style={{ marginBottom: 12 }}>
            {cierre.escenarioEtiqueta} · {ALGORITMO[cierre.algoritmo]} · semilla {cierre.semilla} · del {cierre.fechaInicio} al {cierre.fechaFin}
            {' '}({numero(cierre.horasSimuladas, 1)} h simuladas) · {cierre.estado === 'EN_CURSO' ? 'en curso' : cierre.estado.toLowerCase()}
            {cierre.motivoFin ? ` (${cierre.motivoFin === 'FIN_PERIODO' ? 'fin del periodo' : cierre.motivoFin === 'COLAPSO' ? 'colapso detectado' : 'detenida por el usuario'})` : ''}
          </p>
          <div className="v4-kpis">
            <Kpi etiqueta="Cumplimiento total" valor={`${numero(r.cumplimiento, 2)} %`} sub={`${numero(r.pedidosRegistrados)} pedidos · ${numero(r.entregadosATiempo)} entregados a tiempo`} />
            <Kpi etiqueta="Costo total" valor={soles(r.costoTotal)} sub={`${soles(r.costoPorPedidoEntregado, 2)} por pedido entregado`} />
            <Kpi etiqueta="Km recorridos" valor={`${numero(r.kmTotal)} km`} sub={`${numero(r.rutasDespachadas)} rutas despachadas`} />
            <Kpi etiqueta="Rutas recalculadas" valor={String(r.reRuteosBloqueo + r.reRuteosAveria)} sub={`${r.reasignaciones} reasignaciones de carga · ${r.reprogramaciones} reprogramaciones`} />
            <Kpi etiqueta="Tiempo real de ejecución" valor={duracion(cierre.duracionRealSeg)} sub={`cómputo del planificador: ${numero(r.tiempoComputoPlanificadorMs / 1000, 1)} s en ${r.planificaciones} ventanas`} />
            <Kpi etiqueta="Semilla utilizada" valor={String(cierre.semilla)} sub={`algoritmo: ${ALGORITMO[cierre.algoritmo]}`} />
          </div>
          <div className="rerouting-breakdown">
            <span>Desglose de rutas recalculadas — por calle bloqueada: <b>{r.reRuteosBloqueo}</b></span>
            <span>por avería: <b>{r.reRuteosAveria}</b></span>
            <span>entregados fuera de plazo: <b>{r.entregadosTarde}</b></span>
            <span>en curso al cierre: <b>{r.enCurso}</b></span>
            <span>incumplidos sin entregar: <b>{r.incumplidosSinEntregar}</b></span>
            <span>pedidos divididos (P13): <b>{r.pedidosDivididos}</b></span>
          </div>

          {esColapso && (
            <>
              <h3 style={{ marginTop: 22 }}>Resultado del escenario de colapso logístico</h3>
              {cierre.colapso ? (
                <div className="colapso-note">
                  Colapso logístico detectado el <b>{cierre.colapso.fecha}</b>, a los <b>{numero(cierre.colapso.diasSimulados, 2)} días</b> simulados.
                  Motivo: {cierre.colapso.motivo}. Cumplimiento en ese momento: <b>{numero(cierre.colapso.cumplimiento, 2)} %</b>.
                  Pedidos activos que ya no cumplen su plazo: <b>{cierre.colapso.pedidosIncumplidos}</b>. Pedidos sin unidad que no
                  pueden esperar: <b>{cierre.colapso.pedidosSinUnidad}</b>. Pedidos registrados: {numero(cierre.colapso.pedidosRegistrados)} ·
                  entregados: {numero(cierre.colapso.pedidosEntregados)}.
                </div>
              ) : (
                <p className="empty-hint">Aún no se ha detectado el colapso logístico en esta corrida.</p>
              )}
            </>
          )}

          <h3 style={{ marginTop: 22 }}>Cumplimiento por día {esColapso ? '(hasta el colapso)' : ''}</h3>
          <div className="tabla-scroll" style={{ maxHeight: 360 }}>
            <table className="daily-table">
              <thead>
                <tr><th>Día</th><th>Fecha</th><th className="num">Registrados</th><th className="num">A tiempo</th><th className="num">Incumplidos</th><th className="num">En curso</th><th>Cumplimiento</th><th /></tr>
              </thead>
              <tbody>
                {cierre.cumplimientoPorDia.map((d) => (
                  <tr key={d.dia}>
                    <td>Día {d.dia}</td><td>{d.fecha}</td><td className="num">{d.registrados}</td><td className="num">{d.aTiempo}</td>
                    <td className="num">{d.incumplidos}</td><td className="num">{d.enCurso}</td><td>{numero(d.cumplimiento, 1)} %</td>
                    <td><div className="bar-track"><div className="bar-fill" style={{ width: `${Math.max(0, Math.min(100, d.cumplimiento))}%` }} /></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="grid-2col" style={{ marginTop: 18 }}>
            <div className="card">
              <h3>Kilometraje y costo por tipo de vehículo (D16, D17)</h3>
              <table className="table-readonly">
                <thead><tr><th>Tipo</th><th>Unidades</th><th>Rutas</th><th>Km</th><th>Costo</th></tr></thead>
                <tbody>
                  {cierre.kmPorTipo.map((k) => (
                    <tr key={k.tipo}><td>{k.tipo}</td><td>{k.unidades}</td><td>{numero(k.rutas)}</td><td>{numero(k.km)}</td><td>{soles(k.costo)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="card">
              <h3>Verificación de la política de entrega (D25)</h3>
              <span className={'semaforo-pill ' + (cierre.verificacion.politicaCumplida ? 'semaforo-verde' : 'semaforo-rojo')}>
                {cierre.verificacion.politicaCumplida ? 'Cumple la política' : 'No cumple la política'}
              </span>
              <p style={{ marginTop: 10 }}>{cierre.verificacion.mensaje}</p>
              {cierre.verificacion.pedidosFueraDePlazo.length > 0 && (
                <p className="hint">Entregados fuera de plazo: {cierre.verificacion.pedidosFueraDePlazo.join(', ')}</p>
              )}
            </div>
            <div className="card">
              <h3>Incidencias (D19)</h3>
              <p className="hint">
                Bloqueos programados en la corrida: <b>{cierre.incidencias.bloqueosProgramados}</b> · bloqueos manuales:{' '}
                <b>{cierre.incidencias.bloqueosManuales}</b> · averías: <b>{cierre.incidencias.averias}</b>
              </p>
              {cierre.incidencias.detalleAverias.length > 0 ? (
                <table className="table-readonly">
                  <thead><tr><th>Avería</th><th>Unidad</th><th>Tipo</th><th>Fecha</th><th>Pedidos afectados</th></tr></thead>
                  <tbody>
                    {cierre.incidencias.detalleAverias.map((a) => (
                      <tr key={a.id}><td>{a.id} <span className="hint">({a.origen.toLowerCase()})</span></td><td>{a.vehiculo}</td><td>{a.tipo}</td><td>{a.fecha}</td><td>{a.pedidosAfectados}</td></tr>
                    ))}
                  </tbody>
                </table>
              ) : <p className="empty-hint">Sin averías en la corrida.</p>}
              <p className="hint" style={{ marginTop: 8 }}>
                Eventos: {Object.entries(cierre.incidencias.eventosPorTipo).map(([k, v]) => `${etiquetaCodigo(k)}: ${v}`).join(' · ') || 'ninguno'}
              </p>
            </div>
            <div className="card">
              <h3>Detalle de la ejecución (D24)</h3>
              <table className="table-readonly">
                <tbody>
                  {Object.entries(cierre.parametros).filter(([k]) => k !== 'parametrosAlgoritmo').map(([k, v]) => (
                    <tr key={k}><th style={{ width: '45%' }}>{ETIQUETA_PARAMETRO[k] ?? k}</th><td>{String(v)}</td></tr>
                  ))}
                  <tr><th>Parámetros del algoritmo</th><td>{Object.entries((cierre.parametros.parametrosAlgoritmo as Record<string, unknown>) ?? {}).map(([k, v]) => `${k} = ${v}`).join(', ') || 'por defecto'}</td></tr>
                  {Object.entries(cierre.fuentes ?? {}).map(([k, v]) => <tr key={'f' + k}><th>Archivos de {k}</th><td>{v}</td></tr>)}
                  <tr><th>Inicio y fin real</th><td>{new Date(cierre.inicioRealMs).toLocaleString('es-PE')} → {cierre.finRealMs ? new Date(cierre.finRealMs).toLocaleString('es-PE') : 'en curso'}</td></tr>
                </tbody>
              </table>
            </div>
          </div>

          <h3 style={{ marginTop: 22 }}>
            Última planificación estable {cierre.ultimaPlanificacion ? `(${cierre.ultimaPlanificacion.fecha} simulada)` : ''}
          </h3>
          {!cierre.ultimaPlanificacion ? (
            <p className="empty-hint">Esta corrida se guardó antes de que el cierre incluyera la última planificación.</p>
          ) : cierre.ultimaPlanificacion.rutas.length === 0 ? (
            <p className="empty-hint">Al cierre no quedaban rutas vigentes: todas las unidades estaban en su almacén.</p>
          ) : (
            <>
              <p className="hint">
                Origen: {cierre.ultimaPlanificacion.causa} · {cierre.ultimaPlanificacion.rutas.length} unidades con ruta vigente al cierre
                {' '}(pedidos pendientes de entregar en cada ruta, en orden de visita).
              </p>
              <div className="tabla-scroll" style={{ maxHeight: 360 }}>
                <table className="daily-table">
                  <thead>
                    <tr><th>Unidad</th><th>Ruta</th><th>Sale de</th><th>Paradas (pedido · paquetes · llegada)</th><th>Regresa a</th></tr>
                  </thead>
                  <tbody>
                    {cierre.ultimaPlanificacion.rutas.map((r) => (
                      <tr key={r.vehiculo}>
                        <td><b>{r.vehiculo}</b><div className="hint">{r.tipo}</div></td>
                        <td>{r.ruta}</td>
                        <td>{r.almacenSalida}<div className="hint">{r.salida}</div></td>
                        <td>
                          {r.paradas.length === 0 ? <span className="hint">Sin paradas pendientes (retorno o reubicación)</span>
                            : r.paradas.map((p, i) => (
                              <div key={p.pedido + i}>{i + 1}. {p.pedido} · {p.cantidad} paq. · {p.llegada}</div>
                            ))}
                        </td>
                        <td>{r.almacenRetorno}<div className="hint">{r.regreso}</div></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          <h3 style={{ marginTop: 22 }}>Utilización de la flota (D18)</h3>
          <div className="tabla-scroll" style={{ maxHeight: 320 }}>
            <table className="daily-table">
              <thead>
                <tr><th>Unidad</th><th>Tipo</th><th className="num">Rutas</th><th className="num">Entregas</th><th className="num">Paquetes</th><th className="num">Km</th><th className="num">Costo</th><th className="num">Horas en ruta</th><th>Carga promedio</th></tr>
              </thead>
              <tbody>
                {cierre.utilizacionFlota.map((u) => (
                  <tr key={u.id}>
                    <td>{u.id}</td><td>{u.tipo}</td><td className="num">{u.rutas}</td><td className="num">{u.entregas}</td><td className="num">{u.unidades}</td>
                    <td className="num">{numero(u.km)}</td><td className="num">{soles(u.costo)}</td><td className="num">{numero(u.horasEnRuta, 1)}</td>
                    <td><div className="bar-track" title={`${numero(u.factorCargaPromedio, 1)} %`}><div className="bar-fill" style={{ width: `${Math.min(100, u.factorCargaPromedio)}%` }} /></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="start-bar">
            <a className="btn btn-primary" href={deHistorial ? enlaces.corridaCsv(cierre.idCorrida) : enlaces.cierreCsv()}>Exportar reporte (CSV / hoja de cálculo)</a>
            <span className="hint">D22: el CSV se abre directamente en Excel.</span>
          </div>
        </>
      )}

      <div className="separador" />
      <h3>Historial de corridas (D24)</h3>
      {historial.length === 0 ? (
        <p className="empty-hint">Todavía no hay corridas registradas.</p>
      ) : (
        <div className="tabla-scroll" style={{ maxHeight: 360 }}>
          <table className="daily-table">
            <thead>
              <tr><th>Corrida</th><th>Escenario</th><th>Algoritmo</th><th>Inicio simulado</th><th>Estado</th><th className="num">Pedidos</th><th className="num">Cumplimiento</th><th className="num">Costo</th><th /></tr>
            </thead>
            <tbody>
              {historial.map((h) => (
                <tr key={h.id}>
                  <td>{h.id}</td><td>{ESCENARIO[h.escenario] ?? h.escenario}</td><td>{h.algoritmo}</td>
                  <td>{h.fechaInicioSim?.replace('T', ' ').slice(0, 16)}</td>
                  <td><span className="chip-estado">{h.estado?.toLowerCase()}{h.motivoFin === 'COLAPSO' ? ' · colapso' : ''}</span></td>
                  <td className="num">{h.pedidos ?? '—'}</td>
                  <td className="num">{h.cumplimiento != null ? `${numero(h.cumplimiento, 2)} %` : '—'}</td>
                  <td className="num">{h.costoTotal != null ? soles(h.costoTotal) : '—'}</td>
                  <td>{h.tieneCierre && <button className="btn btn-sm btn-ghost" onClick={() => verCorrida(h.id)}>Ver cierre</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
