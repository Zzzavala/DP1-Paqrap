// Formatos de fecha, hora, dinero y semaforizacion compartidos por las pantallas.
// Las horas de la simulacion viajan como "epoch ms en UTC" de la fecha local, asi que
// siempre se formatean en UTC para no desplazarlas.

const DIAS_SEM = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export const dos = (n: number) => (n < 10 ? '0' : '') + n;

export function fechaLarga(ms: number): string {
  const d = new Date(ms);
  return `${DIAS_SEM[d.getUTCDay()]} ${dos(d.getUTCDate())} ${MESES[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
}

export function horaMinuto(ms: number): string {
  const d = new Date(ms);
  return `${dos(d.getUTCHours())}:${dos(d.getUTCMinutes())}`;
}

export function fechaHoraCorta(ms: number): string {
  const d = new Date(ms);
  return `${dos(d.getUTCDate())}/${dos(d.getUTCMonth() + 1)} ${dos(d.getUTCHours())}:${dos(d.getUTCMinutes())}`;
}

export function duracion(segundos: number): string {
  const s = Math.max(0, Math.floor(segundos));
  return `${dos(Math.floor(s / 3600))}:${dos(Math.floor((s % 3600) / 60))}:${dos(s % 60)}`;
}

export const soles = (v: number, decimales = 0) =>
  'S/ ' + v.toLocaleString('es-PE', { minimumFractionDigits: decimales, maximumFractionDigits: decimales });

export const numero = (v: number, decimales = 0) =>
  v.toLocaleString('es-PE', { minimumFractionDigits: decimales, maximumFractionDigits: decimales });

export type Semaforo = 'verde' | 'ambar' | 'rojo';

export function semaforo(porcentaje: number, verde: number, ambar: number): Semaforo {
  if (porcentaje >= verde) return 'verde';
  if (porcentaje >= ambar) return 'ambar';
  return 'rojo';
}

export const COLOR_SEMAFORO: Record<Semaforo, string> = { verde: '#2e7d46', ambar: '#c98a10', rojo: '#c0392b' };
export const ETIQUETA_SEMAFORO: Record<Semaforo, string> = { verde: 'A tiempo', ambar: 'En riesgo', rojo: 'Vencido / crítico' };

/**
 * Semaforo de OCUPACION (unidades de transporte y almacenes intermedios). Reutiliza los mismos umbrales
 * configurables del semaforo de plazo, aplicados al espacio libre (libre = 100 - ocupacion), y agrega "vacio".
 * Con los umbrales por defecto (50 / 20): verde hasta 50 % de ocupacion y ambar hasta 80 %.
 */
export type SemaforoOcupacion = 'vacio' | 'verde' | 'ambar' | 'rojo';

export function semaforoOcupacion(ocupPct: number, verde: number, ambar: number): SemaforoOcupacion {
  if (ocupPct <= 0) return 'vacio';
  const libre = 100 - ocupPct;
  if (libre >= verde) return 'verde';
  if (libre >= ambar) return 'ambar';
  return 'rojo';
}

export const COLOR_OCUPACION: Record<SemaforoOcupacion, string> = { vacio: '#f4f6f8', verde: '#2e7d46', ambar: '#c98a10', rojo: '#c0392b' };
/** Borde del icono: el vacio lleva borde gris oscuro para no confundirse con el averiado (relleno gris). */
export const BORDE_VACIO = '#3d4a55';
export const ETIQUETA_OCUPACION: Record<SemaforoOcupacion, string> = {
  vacio: 'Vacío', verde: 'Poca carga', ambar: 'Carga media', rojo: 'Casi lleno / lleno',
};

/** D10-D13: color de la insignia de cada tipo de averia (amarillo, naranja, morado). */
export const COLOR_AVERIA: Record<number, string> = { 1: '#e0b000', 2: '#e8730c', 3: '#6a1b9a' };
export const ETIQUETA_AVERIA: Record<number, string> = {
  1: 'Avería tipo 1 · leve', 2: 'Avería tipo 2 · moderada', 3: 'Avería tipo 3 · grave',
};

/** Clase CSS de la pildora (ocupacion-pill ocup-xxx). */
export const claseOcupacion = (s: SemaforoOcupacion) => 'ocupacion-pill ocup-' + s;

/** Color exclusivo de las calles bloqueadas (no se confunde con el rojo del semaforo). */
export const COLOR_BLOQUEO = '#8e24aa';

/** Ruta: tramo hacia los clientes (azul, guiones) y tramo de retorno al almacen (turquesa, punteado). */
export const COLOR_RUTA = '#0f3d5c';
export const COLOR_RETORNO = '#0e8a9a';

export function porcentaje(parte: number, total: number): number {
  return total > 0 ? (100 * parte) / total : 0;
}

export const ETIQUETA_TIPO_VEHICULO: Record<string, string> = { AUTO: 'Auto', MOTO: 'Moto', BICICLETA: 'Bicicleta' };

export const ETIQUETA_EVENTO: Record<string, string> = {
  RECALCULO: 'Recálculo de ruta', REASIGNACION: 'Reasignación de pedido', TRANSBORDO: 'Transbordo', AVERIA: 'Avería',
  COLAPSO: 'Colapso', REPROGRAMACION: 'Reprogramación', ENTREGA: 'Entrega', DISPONIBLE: 'Disponible', DESPACHO: 'Despacho',
  REGRESO: 'Regreso', REUBICACION: 'Reubicación', ASIGNACION: 'Asignación', REGISTRO: 'Registro', DIVISION: 'Entrega parcial',
  EDICION: 'Edición', CANCELACION: 'Cancelación', BLOQUEO: 'Bloqueo', DESBLOQUEO: 'Desbloqueo', RECARGA: 'Recarga de almacén',
  MANTENIMIENTO: 'Mantenimiento', ALIMENTACION: 'Alimentación', BLOQUEO_MANUAL: 'Bloqueo manual', PEDIDO_MANUAL: 'Pedido manual',
  INFO: 'Aviso',
};

/** Texto en espanol de un codigo del backend (MAYUSCULAS_CON_GUION) que no tiene etiqueta propia. */
export function etiquetaCodigo(codigo: string, etiquetas: Record<string, string> = ETIQUETA_EVENTO): string {
  if (etiquetas[codigo]) return etiquetas[codigo];
  const t = codigo.toLowerCase().replace(/_/g, ' ');
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** dd/mm/aaaa hh:mm de una hora simulada (epoch ms en UTC de la fecha local). */
export function fechaHoraCompleta(ms: number): string {
  const d = new Date(ms);
  return `${dos(d.getUTCDate())}/${dos(d.getUTCMonth() + 1)}/${d.getUTCFullYear()} ${dos(d.getUTCHours())}:${dos(d.getUTCMinutes())}`;
}

/** Fecha y hora actual de Lima (UTC-5) como dd/mm/aaaa hh:mm. */
export function ahoraLimaTexto(): string {
  return fechaHoraCompleta(Date.now() - 5 * 3600 * 1000);
}

/** Minutos simulados transcurridos como "N d hh h mm min". */
export function transcurridoSimulado(minutos: number): string {
  const m = Math.max(0, Math.floor(minutos));
  return `${Math.floor(m / 1440)} d ${dos(Math.floor((m % 1440) / 60))} h ${dos(m % 60)} min`;
}

/** Segundos reales transcurridos como "hh h mm min". */
export function transcurridoReal(segundos: number): string {
  const m = Math.max(0, Math.floor(segundos / 60));
  return `${dos(Math.floor(m / 60))} h ${dos(m % 60)} min`;
}

/** datetime-local (YYYY-MM-DDTHH:mm) a partir de una fecha ISO del backend. */
export function aInputFecha(iso: string | null | undefined): string {
  if (!iso) return '';
  return iso.slice(0, 16);
}

/** Fecha/hora actual de Lima (UTC-5) en formato datetime-local. */
export function ahoraLima(): string {
  const ms = Date.now() - 5 * 3600 * 1000;
  const d = new Date(ms);
  return `${d.getUTCFullYear()}-${dos(d.getUTCMonth() + 1)}-${dos(d.getUTCDate())}T${dos(d.getUTCHours())}:${dos(d.getUTCMinutes())}`;
}
