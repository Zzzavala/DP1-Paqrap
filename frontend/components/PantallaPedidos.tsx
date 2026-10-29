'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api, enlaces } from '@/lib/api';
import type { EstadoVisual, FilaPedido } from '@/lib/tipos';
import { numero } from '@/lib/formato';
import type { Seleccion } from './MapaOperacion';

interface Props {
  estado: EstadoVisual | null;
  onAbrir: (s: Seleccion) => void;
  onAviso: (texto: string, tipo?: 'ok' | 'error') => void;
  onIrAParametros: () => void;
}

const TIPOS: [string, string][] = [
  ['REGULAR_36H', 'Regular · 36 h'],
  ['PRIORIZADA_4H', 'Priorizada · 4 h'],
  ['PRIORIZADA_8H', 'Priorizada · 8 h'],
  ['PRIORIZADA_12H', 'Priorizada · 12 h'],
  ['PRIORIZADA_18H', 'Priorizada · 18 h'],
];

const ESTADOS: [string, string][] = [
  ['', 'Todos'],
  ['ACTIVO', 'Activos (no entregados)'],
  ['REGISTRADO', 'Registrado (por despachar)'],
  ['EN_TRANSITO', 'En ruta'],
  ['REASIGNADO', 'Reasignado'],
  ['ENTREGADO', 'Entregado'],
  ['A_TIEMPO', 'Entregado a tiempo'],
  ['INCUMPLIDO', 'Incumplido / reprogramado'],
  ['CANCELADO', 'Cancelado'],
];

/** Modulo A · Registro de pedidos y clientes: registro, carga masiva, busqueda, seguimiento y exportacion. */
export default function PantallaPedidos({ estado, onAbrir, onAviso, onIrAParametros }: Props) {
  const habilitado = estado?.estadoCorrida === 'EN_CURSO' && estado.escenario === 'TIEMPO_REAL';
  const [form, setForm] = useState({ cliente: '', nombreCliente: '', x: 30, y: 20, cantidad: 1, tipoEntrega: 'REGULAR_36H', canal: 'WEB', observaciones: '' });
  const [enviando, setEnviando] = useState(false);
  const [filtros, setFiltros] = useState({ q: '', estado: '', tipo: '', limite: '300' });
  const [filas, setFilas] = useState<FilaPedido[]>([]);
  const [cargando, setCargando] = useState(false);
  const archivo = useRef<HTMLInputElement>(null);

  const buscar = useCallback(async () => {
    setCargando(true);
    try {
      setFilas(await api.buscarPedidos(filtros));
    } catch (e) {
      onAviso(e instanceof Error ? e.message : 'No se pudo buscar', 'error');
    } finally {
      setCargando(false);
    }
  }, [filtros, onAviso]);

  useEffect(() => {
    buscar();
    // se refresca solo al entrar o al cambiar de corrida; luego a demanda
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [estado?.idCorrida]);

  const registrar = async (e: React.FormEvent) => {
    e.preventDefault();
    setEnviando(true);
    try {
      const p = await api.registrarPedido({ ...form, x: Number(form.x), y: Number(form.y), cantidad: Number(form.cantidad) });
      onAviso(`Pedido ${p.id} registrado. Código de seguimiento: ${p.id}. Hora límite: ${p.limite}.`, 'ok');
      setForm({ ...form, observaciones: '' });
      buscar();
    } catch (err) {
      onAviso(err instanceof Error ? err.message : 'No se pudo registrar el pedido', 'error');
    } finally {
      setEnviando(false);
    }
  };

  const cargarLote = async (f: File) => {
    try {
      const r = await api.cargarLote(f);
      onAviso(r.mensaje + (r.errores ? ` (${r.errores} línea(s) con error omitidas)` : ''), r.registrados ? 'ok' : 'error');
      buscar();
    } catch (err) {
      onAviso(err instanceof Error ? err.message : 'No se pudo cargar el archivo', 'error');
    }
  };

  return (
    <section>
      <h2>Registro y seguimiento de pedidos</h2>
      <p className="note">
        Registro de pedidos de la operación día a día (A1–A5, A14, A15, A25), carga masiva desde archivo (A9), seguimiento por
        código (A8, A12, A20), búsqueda por cliente, estado o tipo de entrega (A18) y exportación a hoja de cálculo (A24). Cada pedido
        recibe un identificador único que sirve como código de seguimiento; su hora límite se calcula automáticamente según el tipo de entrega.
      </p>

      <div className="grid-2col">
        <div className="card">
          <h3>Registrar pedido</h3>
          {!habilitado && (
            <div className="banner banner-info">
              El registro manual está disponible durante la «Operación día a día» (tiempo real). En la simulación del periodo y en la de
              colapso, la demanda viene fija del archivo de ventas.{' '}
              <button className="btn btn-sm btn-ghost" onClick={onIrAParametros}>Ir a Parámetros</button>
            </div>
          )}
          <form onSubmit={registrar}>
            <div className="fila-campos">
              <label>Código de cliente<input value={form.cliente} placeholder="ej. c1234" disabled={!habilitado} onChange={(e) => setForm({ ...form, cliente: e.target.value })} /></label>
              <label>Nombre del cliente<input value={form.nombreCliente} disabled={!habilitado} onChange={(e) => setForm({ ...form, nombreCliente: e.target.value })} /></label>
              <label>Coordenada X (0–70 km)<input type="number" min={0} max={70} value={form.x} disabled={!habilitado} onChange={(e) => setForm({ ...form, x: Number(e.target.value) })} /></label>
              <label>Coordenada Y (0–50 km)<input type="number" min={0} max={50} value={form.y} disabled={!habilitado} onChange={(e) => setForm({ ...form, y: Number(e.target.value) })} /></label>
              <label>Paquetes del producto P<input type="number" min={1} max={1000} value={form.cantidad} disabled={!habilitado} onChange={(e) => setForm({ ...form, cantidad: Number(e.target.value) })} /></label>
              <label>Tipo de entrega
                <select value={form.tipoEntrega} disabled={!habilitado} onChange={(e) => setForm({ ...form, tipoEntrega: e.target.value })}>
                  {TIPOS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                </select>
              </label>
              <label>Canal de origen
                <select value={form.canal} disabled={!habilitado} onChange={(e) => setForm({ ...form, canal: e.target.value })}>
                  <option value="WEB">Web</option>
                  <option value="APLICACION_MOVIL">Aplicación móvil</option>
                  <option value="CALL_CENTER">Call center</option>
                </select>
              </label>
              <label>Observaciones<input value={form.observaciones} placeholder="ej. horario preferido" disabled={!habilitado} onChange={(e) => setForm({ ...form, observaciones: e.target.value })} /></label>
            </div>
            <div className="start-bar" style={{ marginTop: 14 }}>
              <button className="btn btn-start" type="submit" disabled={!habilitado || enviando}>Registrar pedido</button>
              <span className="hint">Pedidos de más de 24 paquetes se entregan en partes (A4, P13).</span>
            </div>
          </form>
        </div>

        <div className="card">
          <h3>Carga masiva y exportación</h3>
          <p className="hint">Archivo de texto con una línea por pedido, en el formato del curso: <code>##d##h##m:x,y,cliente,cantidad,plazo</code> (día/hora del mes en curso; los que ya pasaron se registran ahora).</p>
          <input ref={archivo} type="file" accept=".txt,.csv,text/plain" style={{ display: 'none' }}
            onChange={(e) => { const f = e.target.files?.[0]; if (f) cargarLote(f); e.target.value = ''; }} />
          <div className="start-bar" style={{ marginTop: 6 }}>
            <button className="btn btn-primary" disabled={!habilitado} onClick={() => archivo.current?.click()}>Cargar pedidos en lote</button>
            <a className="btn btn-ghost" href={enlaces.pedidosCsv()}>Exportar pedidos (CSV)</a>
          </div>
          <div className="separador" />
          <h3>Seguimiento por código</h3>
          <p className="hint">Escriba el código del pedido en «Buscar» o haga clic en una fila para ver su trazabilidad completa: almacén de origen, unidad, ruta y hora real de entrega.</p>
        </div>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h3>Pedidos de la corrida</h3>
        <div className="fila-campos" style={{ alignItems: 'end' }}>
          <label>Buscar (código o cliente)<input value={filtros.q} onChange={(e) => setFiltros({ ...filtros, q: e.target.value })}
            onKeyDown={(e) => { if (e.key === 'Enter') buscar(); }} /></label>
          <label>Estado
            <select value={filtros.estado} onChange={(e) => setFiltros({ ...filtros, estado: e.target.value })}>
              {ESTADOS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </label>
          <label>Tipo de entrega
            <select value={filtros.tipo} onChange={(e) => setFiltros({ ...filtros, tipo: e.target.value })}>
              <option value="">Todos</option>
              {TIPOS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </label>
          <label>Máximo de filas
            <select value={filtros.limite} onChange={(e) => setFiltros({ ...filtros, limite: e.target.value })}>
              {['100', '300', '1000', '2000'].map((x) => <option key={x}>{x}</option>)}
            </select>
          </label>
          <div><button className="btn btn-primary" onClick={buscar} disabled={cargando}>{cargando ? 'Buscando…' : 'Buscar'}</button></div>
        </div>
        <p className="hint" style={{ marginTop: 10 }}>{numero(filas.length)} pedido(s) — los más recientes primero.</p>
        <div className="tabla-scroll" style={{ maxHeight: 520 }}>
          <table className="daily-table">
            <thead>
              <tr><th>Código</th><th>Cliente</th><th>Destino</th><th className="num">Paq.</th><th>Tipo</th><th>Registro</th><th>Límite</th><th>Estado</th><th>Unidad</th><th>Entrega real</th></tr>
            </thead>
            <tbody>
              {filas.map((f) => (
                <tr key={f.id} className="fila-click" onClick={() => onAbrir({ tipo: 'pedido', id: f.id })}>
                  <td><b>{f.id}</b></td><td>{f.cliente}</td><td>({f.x}, {f.y})</td><td className="num">{f.cantidad}</td><td>{f.tipoEntrega}</td>
                  <td>{f.registro}</td><td>{f.limite}</td>
                  <td>
                    <span className={'semaforo-pill ' + (f.aTiempo === true ? 'semaforo-verde' : f.aTiempo === false ? 'semaforo-rojo' : 'semaforo-gris')}>{f.estado}{f.reprogramado ? ' · reprog.' : ''}</span>
                  </td>
                  <td>{f.vehiculos || '—'}</td><td>{f.entregaReal || '—'}</td>
                </tr>
              ))}
              {!filas.length && <tr><td colSpan={10} className="empty-hint">Sin pedidos que mostrar (inicie una corrida o cambie los filtros).</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
