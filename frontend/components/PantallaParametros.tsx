'use client';

import { useRef } from 'react';
import type { Configuracion, ParametrosCorrida, ResumenArchivo } from '@/lib/tipos';

interface Props {
  config: Configuracion;
  /** Modo de la barra lateral: la simulacion lista 5D y colapso; la operacion dia a dia es tiempo real. */
  modo: 'simulacion' | 'diaria';
  form: ParametrosCorrida;
  setForm: (f: ParametrosCorrida) => void;
  archivos: ResumenArchivo[] | null;
  errorArchivos: string | null;
  bloqueado: boolean;
  ocupado: boolean;
  puedeIniciar: boolean;
  onIniciar: () => void;
  onSubir: (tipo: string, archivos: File[]) => void;
  onQuitar: (tipo: string) => void;
  onUmbralesEnVivo: (verde: number, ambar: number) => void;
  /** P6, P16, P17: cambio de velocidad por tipo de unidad con la corrida en marcha (en caliente). */
  onVelocidadesEnVivo: (auto: number, moto: number, bicicleta: number) => void;
}

const CAMPOS_VELOCIDAD = new Set(['velocidadAuto', 'velocidadMoto', 'velocidadBicicleta']);

const NOMBRE_TIPO: Record<string, string> = {
  ventas: 'Pedidos (ventas)',
  bloqueos: 'Bloqueos de calles',
  mantenimiento: 'Mantenimiento preventivo',
  averias: 'Averías programadas',
};

function duracionTexto(f: ParametrosCorrida): string {
  if (f.escenario === 'SIMULACION_5D') {
    return `${f.diasSimulacion} días simulados (fija) · ejecución acelerada en ${f.duracionObjetivoMin} min reales (rango exigido: 30 a 60 min)`;
  }
  if (f.escenario === 'TIEMPO_REAL') return 'Sin fin automático (tiempo real 1:1, sin aceleración) · se detiene con «Detener corrida»';
  const minPorDia = (24 * 60) / f.factorAceleracionColapso;
  return `Sin límite superior de tiempo · se detiene sola al detectar el colapso logístico (1 día simulado ≈ ${minPorDia.toFixed(1)} min reales)`;
}

/** V-1 · Parametros de la corrida: todo lo que determina el resultado, para que sea reproducible y auditable. */
export default function PantallaParametros(props: Props) {
  const { config, form, setForm, archivos, bloqueado } = props;
  const entradas = useRef<Record<string, HTMLInputElement | null>>({});
  const op = form.operacion;
  const set = <K extends keyof ParametrosCorrida>(k: K, v: ParametrosCorrida[K]) => setForm({ ...form, [k]: v });
  const setOp = (k: string, v: number) => setForm({ ...form, operacion: { ...op, [k]: v } });
  const setAlg = (k: string, v: number) => setForm({ ...form, parametrosAlgoritmo: { ...form.parametrosAlgoritmo, [k]: v } });
  const algoritmo = config.algoritmos.find((a) => a.id === form.algoritmo);
  const archivosValidos = !!archivos && archivos.every((a) => a.valido);

  const cambiarUmbral = (verde: number, ambar: number) => {
    const v = Math.max(0, Math.min(100, verde));
    const a = Math.max(0, Math.min(v, ambar));
    setForm({ ...form, umbrales: { verde: v, ambar: a } });
    if (bloqueado) props.onUmbralesEnVivo(v, a);
  };

  return (
    <section>
      <h2>{props.modo === 'diaria' ? 'Parámetros de la operación día a día' : 'Parámetros de la simulación'}</h2>
      <p className="note">
        {props.modo === 'diaria' ? 'La operación día a día corre en tiempo real desde la fecha de inicio.' : 'Elige la simulación (periodo de 5 días o colapso logístico) y la fecha de inicio.'} El planificador
        es el Algoritmo Genético (GA) y la semilla es fija, así que misma fecha + mismos archivos = misma corrida. El escenario queda
        fijo una vez iniciada la corrida; los umbrales del semáforo se pueden ajustar en cualquier momento.
      </p>
      {props.modo === 'diaria' && (
        <div className="banner banner-info">
          <b>Operación día a día · tiempo real.</b> No es una simulación: el reloj avanza 1:1 desde la hora de hoy y los pedidos llegan
          del archivo de ventas y del registro manual. Se cierra con «Detener corrida».
        </div>
      )}
      {bloqueado && (
        <div className="banner banner-info">
          Hay una corrida en curso: los parámetros quedan fijos hasta detenerla, excepto los umbrales del semáforo y las velocidades
          por tipo de unidad, que se cambian en caliente en «Parámetros avanzados».
        </div>
      )}

      <div className="grid-2col">
        <div className="card">
          <h3>Archivos de entrada</h3>
          {props.errorArchivos && <p className="error-texto">{props.errorArchivos}</p>}
          {!archivos && !props.errorArchivos && <p className="empty-hint">Validando archivos…</p>}
          {archivos && (
            <ul className="files-list">
              {archivos.map((a) => (
                <li className="file-row" key={a.tipo}>
                  <div style={{ minWidth: 0 }}>
                    <div className="file-name">{NOMBRE_TIPO[a.tipo] ?? a.tipo}: {a.nombre}</div>
                    <div className="file-meta">
                      {a.descripcion}{a.errores > 0 ? ` · ${a.errores} línea(s) con formato inválido omitidas` : ''}
                    </div>
                    {a.advertencia && <div className="file-meta" style={{ color: 'var(--amber)', fontWeight: 600 }}>⚠ {a.advertencia}</div>}
                  </div>
                  <div className="file-actions">
                    <span className={'badge ' + (a.valido ? 'badge-ok' : 'badge-error')}>
                      <span className="badge-dot" />{a.valido ? (a.personalizado ? 'Personalizado' : 'Validado') : 'No válido'}
                    </span>
                    <input type="file" accept=".txt,.csv,text/plain" style={{ display: 'none' }}
                      multiple={a.tipo === 'ventas' || a.tipo === 'bloqueos'}
                      ref={(el) => { entradas.current[a.tipo] = el; }}
                      onChange={(e) => {
                        const fs = Array.from(e.target.files ?? []);
                        if (fs.length) props.onSubir(a.tipo, fs);
                        e.target.value = '';
                      }} />
                    <button className="btn btn-sm btn-ghost" disabled={bloqueado}
                      title={a.tipo === 'ventas' || a.tipo === 'bloqueos' ? 'Reemplazar por archivos propios (puede elegir 2 a la vez)' : 'Reemplazar por un archivo propio'}
                      onClick={() => entradas.current[a.tipo]?.click()}>{a.tipo === 'ventas' || a.tipo === 'bloqueos' ? 'Subir (1 o 2)' : 'Subir'}</button>
                    {a.personalizado && (
                      <button className="btn btn-sm btn-ghost" disabled={bloqueado} onClick={() => props.onQuitar(a.tipo)}>Quitar</button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
          <p className="hint" style={{ marginTop: 10 }}>
            <b>Caso del 30 de septiembre:</b> el periodo de 5 días cruza de septiembre a octubre, así que en «Pedidos» y en «Bloqueos»
            puede subir 2 archivos a la vez, uno por mes (p. ej. <code>ventas.202609.txt</code> y <code>ventas.202610.txt</code>). Si el
            nombre no indica el mes, el primero (por orden alfabético) se usa para el mes de la fecha de inicio y el segundo para el mes siguiente.
          </p>
          <p className="hint" style={{ marginTop: 10 }}>
            Formatos: ventas <code>##d##h##m:x,y,cliente,cantidad,plazo</code> · bloqueos <code>##d##h##m-##d##h##m:x1,y1,…</code> ·
            mantenimiento <code>aaaammdd:TTNN</code> · averías <code>##d##h##m:TTNN:tipo</code>. Datos publicados disponibles:
            {' '}{config.datosDisponibles.primerMes} a {config.datosDisponibles.ultimoMes}.
          </p>
        </div>

        <div className="card">
          <h3>Configuración de la corrida</h3>
          <label>Escenario de la corrida
            <select value={form.escenario} disabled={bloqueado} onChange={(e) => {
              const esc = e.target.value as ParametrosCorrida['escenario'];
              const def = config.escenarios.find((x) => x.id === esc);
              setForm({ ...form, escenario: esc, fechaInicio: def ? def.fechaInicioPorDefecto.slice(0, 16) : form.fechaInicio });
            }}>
              {config.escenarios
                .filter((e) => (props.modo === 'diaria' ? e.id === 'TIEMPO_REAL' : e.id !== 'TIEMPO_REAL'))
                .map((e) => <option key={e.id} value={e.id}>{e.etiqueta}</option>)}
            </select>
          </label>
          <label>Fecha/hora de inicio {form.escenario === 'TIEMPO_REAL' && <span className="hint">(por defecto, ahora en Lima)</span>}
            <input type="datetime-local" value={form.fechaInicio ?? ''} disabled={bloqueado} onChange={(e) => set('fechaInicio', e.target.value)} />
          </label>
          <label>Duración de la corrida
            <input type="text" value={duracionTexto(form)} disabled readOnly />
          </label>
          <label>Tipos de entrega incluidos
            <div className="tipos-entrega">
              {config.tiposEntrega.map((t) => <span className="tag" key={t.id}>{t.etiqueta} · {t.plazoHoras} h de plazo</span>)}
              <span className="tag">Pedido del archivo de ventas · plazo del registro</span>
            </div>
          </label>
        </div>

        <div className="card">
          <h3>Umbrales de semaforización (% de holgura de plazo restante)</h3>
          <div className="umbral-row">
            <label>Verde — a tiempo<br />holgura ≥
              <input type="number" min={0} max={100} value={form.umbrales.verde} onChange={(e) => cambiarUmbral(Number(e.target.value), form.umbrales.ambar)} />%
            </label>
            <label>Ámbar — en riesgo<br />holgura ≥
              <input type="number" min={0} max={100} value={form.umbrales.ambar} onChange={(e) => cambiarUmbral(form.umbrales.verde, Number(e.target.value))} />%
            </label>
            <label>Rojo — vencido / crítico<br />holgura &lt;
              <input type="number" min={0} max={100} value={form.umbrales.ambar} onChange={(e) => cambiarUmbral(form.umbrales.verde, Number(e.target.value))} />%
            </label>
          </div>
          <p className="hint" style={{ marginTop: 8 }}>
            La semaforización se calcula sobre el % de holgura de plazo restante, nunca sobre horas absolutas; en los almacenes
            intermedios, sobre el % de stock. El límite inferior de «ámbar» y el de «rojo» son el mismo valor por definición.
            {bloqueado ? ' Los cambios se aplican a la corrida en curso desde la siguiente actualización del mapa.' : ''}
          </p>
        </div>

        <div className="card">
          <h3>Flota disponible <span className="hint">(P17 · códigos TTNN de la P18)</span></h3>
          <table className="table-readonly">
            <thead><tr><th>Tipo</th><th>Unidades</th><th>Capacidad</th><th>Costo</th><th>Velocidad</th></tr></thead>
            <tbody>
              <tr><td><span className="fleet-letter">A</span>Auto</td><td>{op.cantidadAutos} (TA01–TA{String(op.cantidadAutos).padStart(2, '0')})</td><td>{op.capacidadAuto} paquetes</td><td>S/ {op.costoKmAuto.toFixed(2)} / km</td><td>{op.velocidadAuto} km/h</td></tr>
              <tr><td><span className="fleet-letter">M</span>Moto</td><td>{op.cantidadMotos} (TM01–TM{String(op.cantidadMotos).padStart(2, '0')})</td><td>{op.capacidadMoto} paquetes</td><td>S/ {op.costoKmMoto.toFixed(2)} / km</td><td>{op.velocidadMoto} km/h</td></tr>
              <tr><td><span className="fleet-letter">B</span>Bicicleta</td><td>{op.cantidadBicicletas} (TB01–TB{String(op.cantidadBicicletas).padStart(2, '0')})</td><td>{op.capacidadBicicleta} paquetes</td><td>S/ {op.costoKmBicicleta.toFixed(2)} / km</td><td>{op.velocidadBicicleta} km/h</td></tr>
            </tbody>
          </table>
          <p className="hint" style={{ marginTop: 8 }}>Todas las unidades salen del almacén central al inicio de cualquier escenario (P10). Se editan en «Parámetros avanzados».</p>
        </div>

        <div className="card">
          <h3>Almacenes <span className="hint">(solo lectura)</span></h3>
          <table className="table-readonly">
            <thead><tr><th>Almacén</th><th>Ubicación</th><th>Capacidad</th><th>Recarga</th></tr></thead>
            <tbody>
              {config.almacenes.map((a) => (
                <tr key={a.id}><td><b>{a.nombre}</b><br /><span className="hint">{a.rol}</span></td><td>({a.x}, {a.y})</td>
                  <td>{a.id === 'CENTRAL' ? a.capacidad : `${op.capacidadAlmacenIntermedio} unid.`}</td><td>{a.recarga}</td></tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="card">
          <h3>Turnos de trabajo</h3>
          <ul className="turnos-list">
            {config.turnos.map((t) => <li key={t.rango}><b>{t.rango}</b> · alimentación {t.alimentacion}</li>)}
          </ul>
          <p className="hint" style={{ marginTop: 8 }}>{config.reglaAlimentacion} En el cambio de turno solo cambia el conductor; la unidad sigue operando (P12).</p>
        </div>

        <div className="card span2">
          <details className="avanzado">
            <summary>Parámetros avanzados (velocidades, costos, flota, planificación, colapso y algoritmo)</summary>
            <div className="subtitulo">Escenario</div>
            <div className="fila-campos">
              <label>Duración objetivo del periodo (min reales, 30–60)
                <input type="number" min={30} max={60} value={form.duracionObjetivoMin} disabled={bloqueado} onChange={(e) => set('duracionObjetivoMin', Number(e.target.value))} />
              </label>
              <label>Días del periodo
                <input type="number" min={1} max={30} value={form.diasSimulacion} disabled={bloqueado} onChange={(e) => set('diasSimulacion', Number(e.target.value))} />
              </label>
              <label>Aceleración del colapso (h simuladas por h real)
                <input type="number" min={1} max={20000} value={form.factorAceleracionColapso} disabled={bloqueado} onChange={(e) => set('factorAceleracionColapso', Number(e.target.value))} />
              </label>
              <label>Ventana de planificación (min)
                <input type="number" min={5} max={240} value={form.ventanaPlanificacionMin} disabled={bloqueado} onChange={(e) => set('ventanaPlanificacionMin', Number(e.target.value))} />
              </label>
              <label>Colapso: pedidos incumplidos simultáneos
                <input type="number" min={1} value={form.umbralColapsoVencidos} disabled={bloqueado} onChange={(e) => set('umbralColapsoVencidos', Number(e.target.value))} />
              </label>
              <label>Colapso: pedidos sin unidad que no pueden esperar
                <input type="number" min={1} value={form.umbralColapsoBacklog} disabled={bloqueado} onChange={(e) => set('umbralColapsoBacklog', Number(e.target.value))} />
              </label>
              <label>Averías programadas por día
                <input type="number" min={0} max={20} step={0.1} value={form.averiasPorDia} disabled={bloqueado || !form.generarAverias} onChange={(e) => set('averiasPorDia', Number(e.target.value))} />
              </label>
              <label style={{ alignSelf: 'end' }}>
                <input type="checkbox" checked={form.generarAverias} disabled={bloqueado} onChange={(e) => set('generarAverias', e.target.checked)} />Generar averías con la semilla
              </label>
            </div>
            <div className="subtitulo">Velocidades (km/h) y costos (S/ por km) · D2, D3 · las velocidades se pueden cambiar en caliente (P6, P16)</div>
            <div className="fila-campos">
              {([['velocidadAuto', 'Velocidad auto'], ['velocidadMoto', 'Velocidad moto'], ['velocidadBicicleta', 'Velocidad bicicleta'],
                ['costoKmAuto', 'Costo auto'], ['costoKmMoto', 'Costo moto'], ['costoKmBicicleta', 'Costo bicicleta']] as const).map(([k, l]) => (
                <label key={k}>{l}<input type="number" min={0} step={0.5} value={Number(op[k])} disabled={bloqueado && !CAMPOS_VELOCIDAD.has(k)}
                  onChange={(e) => setOp(k, Number(e.target.value))} /></label>
              ))}
            </div>
            {bloqueado && (
              <div className="start-bar" style={{ marginTop: 4 }}>
                <button className="btn btn-sm" disabled={!(op.velocidadAuto > 0 && op.velocidadMoto > 0 && op.velocidadBicicleta > 0)}
                  onClick={() => props.onVelocidadesEnVivo(op.velocidadAuto, op.velocidadMoto, op.velocidadBicicleta)}>
                  Aplicar velocidades a la corrida en curso
                </button>
                <span className="hint">Aplican desde la siguiente ventana de planificación, también a las unidades que ya están en ruta (P17).</span>
              </div>
            )}
            <div className="subtitulo">Flota y almacenes · D1</div>
            <div className="fila-campos">
              {([['cantidadAutos', 'Autos'], ['cantidadMotos', 'Motos'], ['cantidadBicicletas', 'Bicicletas'],
                ['capacidadAuto', 'Capacidad auto'], ['capacidadMoto', 'Capacidad moto'], ['capacidadBicicleta', 'Capacidad bicicleta'],
                ['capacidadAlmacenIntermedio', 'Capacidad de cada intermedio']] as const).map(([k, l]) => (
                <label key={k}>{l}<input type="number" min={0} value={Number(op[k])} disabled={bloqueado} onChange={(e) => setOp(k, Number(e.target.value))} /></label>
              ))}
            </div>
            <div className="subtitulo">Plazos de entrega (h) y tiempos · D10, D11</div>
            <div className="fila-campos">
              {([['plazoRegularH', 'Regular'], ['plazoPriorizada4H', 'Priorizada 4 h'], ['plazoPriorizada8H', 'Priorizada 8 h'],
                ['plazoPriorizada12H', 'Priorizada 12 h'], ['plazoPriorizada18H', 'Priorizada 18 h'], ['tiempoServicioH', 'Entrega al cliente (h)'],
                ['duracionAlimentacionH', 'Alimentación (h)']] as const).map(([k, l]) => (
                <label key={k}>{l}<input type="number" min={0} step={0.5} value={Number(op[k])} disabled={bloqueado} onChange={(e) => setOp(k, Number(e.target.value))} /></label>
              ))}
              <label>Cambios de turno (h, separadas por coma)
                <input value={op.horasCambioTurno.join(', ')} disabled={bloqueado}
                  onChange={(e) => {
                    const horas = e.target.value.split(',').map((x) => Number(x.trim())).filter((x) => Number.isFinite(x) && x >= 0 && x < 24);
                    if (horas.length === 3) setForm({ ...form, operacion: { ...op, horasCambioTurno: horas } });
                  }} />
              </label>
            </div>
            <div className="subtitulo">Parámetros del algoritmo · {algoritmo?.etiqueta}</div>
            <div className="fila-campos">
              {Object.entries(form.parametrosAlgoritmo).map(([k, v]) => (
                <label key={k}>{k}<input type="number" step="any" value={v} disabled={bloqueado} onChange={(e) => setAlg(k, Number(e.target.value))} /></label>
              ))}
            </div>
          </details>
        </div>
      </div>

      <div className="start-bar">
        <button className="btn btn-start btn-lg" disabled={!props.puedeIniciar || props.ocupado || bloqueado || !archivosValidos} onClick={props.onIniciar}>Iniciar corrida</button>
        <span className="hint">
          {bloqueado ? 'Hay una corrida en curso.' : archivosValidos ? 'Los archivos de entrada están validados. Puedes iniciar la corrida.'
            : 'Falta validar todos los archivos de entrada.'}
        </span>
      </div>
    </section>
  );
}
