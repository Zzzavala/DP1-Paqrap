// Cliente de la API del backend PaqRap. Por defecto usa rutas relativas ("/api/..."):
// en Vercel/Docker Next.js las reenvia al backend (BACKEND_URL) y en el modo estatico las
// atiende el mismo servicio Spring Boot. NEXT_PUBLIC_API_URL permite llamar directo a otro dominio.
import type {
  CierreCorrida,
  Configuracion,
  CorridaHistorial,
  DetalleAlmacen,
  DetallePedido,
  DetalleVehiculo,
  EstadoVisual,
  FilaPedido,
  ParametrosCorrida,
  ResumenArchivo,
} from './tipos';

export const BASE_API = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/+$/, '');

export class ErrorApi extends Error {
  constructor(message: string, public estado: number) {
    super(message);
  }
}

async function pedir<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  let respuesta: Response;
  try {
    respuesta = await fetch(`${BASE_API}${ruta}`, { cache: 'no-store', ...opciones });
  } catch {
    throw new ErrorApi('No se pudo conectar con el backend de PaqRap.', 0);
  }
  if (!respuesta.ok) {
    let mensaje = `Error ${respuesta.status}`;
    try {
      const cuerpo = await respuesta.json();
      if (cuerpo && typeof cuerpo.error === 'string') mensaje = cuerpo.error;
    } catch {
      // cuerpo no JSON
    }
    throw new ErrorApi(mensaje, respuesta.status);
  }
  return (await respuesta.json()) as T;
}

function json(metodo: string, cuerpo?: unknown): RequestInit {
  return {
    method: metodo,
    headers: { 'Content-Type': 'application/json' },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  };
}

export const api = {
  salud: () => pedir<{ estado: string }>('/api/salud'),
  configuracion: () => pedir<Configuracion>('/api/config'),
  archivos: (escenario: string, fechaInicio: string, dias: number) =>
    pedir<ResumenArchivo[]>(
      `/api/datos/archivos?escenario=${encodeURIComponent(escenario)}&fechaInicio=${encodeURIComponent(fechaInicio)}&dias=${dias}`,
    ),
  subirArchivo: (tipo: string, archivo: File, mes?: string) => {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const consulta = mes ? `?mes=${encodeURIComponent(mes)}` : '';
    return pedir<{ valido: boolean; mensaje: string; registros: number; errores: number }>(`/api/datos/archivos/${tipo}${consulta}`, {
      method: 'POST',
      body: datos,
    });
  },
  quitarArchivo: (tipo: string) => pedir<{ mensaje: string }>(`/api/datos/archivos/${tipo}`, { method: 'DELETE' }),

  estado: () => pedir<EstadoVisual>('/api/simulacion/estado'),
  iniciar: (p: Partial<ParametrosCorrida>) => pedir<EstadoVisual>('/api/simulacion/iniciar', json('POST', p)),
  detener: () => pedir<CierreCorrida>('/api/simulacion/detener', json('POST')),
  cierre: () => pedir<CierreCorrida>('/api/simulacion/cierre'),
  vehiculo: (id: string) => pedir<DetalleVehiculo>(`/api/simulacion/vehiculos/${encodeURIComponent(id)}`),
  pedido: (id: string) => pedir<DetallePedido>(`/api/simulacion/pedidos/${encodeURIComponent(id)}`),
  almacen: (id: string) => pedir<DetalleAlmacen>(`/api/simulacion/almacenes/${encodeURIComponent(id)}`),
  alternarBloqueo: (x1: number, y1: number, x2: number, y2: number) =>
    pedir<{ bloqueado: boolean; mensaje: string }>('/api/simulacion/bloqueos', json('POST', { x1, y1, x2, y2 })),
  averia: (vehiculo: string, tipo: number, causa?: string) =>
    pedir<{ mensaje: string }>('/api/simulacion/averias', json('POST', { vehiculo, tipo, causa })),
  umbrales: (verde: number, ambar: number) => pedir<{ mensaje: string }>('/api/simulacion/umbrales', json('PUT', { verde, ambar })),
  velocidades: (auto: number, moto: number, bicicleta: number) =>
    pedir<{ mensaje: string }>('/api/simulacion/velocidades', json('PUT', { auto, moto, bicicleta })),

  registrarPedido: (p: {
    cliente?: string;
    nombreCliente?: string;
    x: number;
    y: number;
    cantidad: number;
    tipoEntrega: string;
    canal: string;
    observaciones?: string;
  }) => pedir<DetallePedido>('/api/pedidos', json('POST', p)),
  editarPedido: (id: string, cambios: { cantidad?: number; tipoEntrega?: string; observaciones?: string }) =>
    pedir<DetallePedido>(`/api/pedidos/${encodeURIComponent(id)}`, json('PUT', cambios)),
  cancelarPedido: (id: string) => pedir<DetallePedido>(`/api/pedidos/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  buscarPedidos: (filtros: Record<string, string>) => {
    const q = new URLSearchParams(Object.entries(filtros).filter(([, v]) => v !== ''));
    return pedir<FilaPedido[]>(`/api/pedidos?${q.toString()}`);
  },
  cargarLote: (archivo: File) => {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return pedir<{ registrados: number; errores: number; mensaje: string }>('/api/pedidos/lote', { method: 'POST', body: datos });
  },

  corridas: () => pedir<CorridaHistorial[]>('/api/corridas'),
  cierreCorrida: (id: string) => pedir<CierreCorrida>(`/api/corridas/${encodeURIComponent(id)}`),
};

export const enlaces = {
  cierreCsv: () => `${BASE_API}/api/simulacion/cierre/csv`,
  corridaCsv: (id: string) => `${BASE_API}/api/corridas/${encodeURIComponent(id)}/csv`,
  pedidosCsv: () => `${BASE_API}/api/pedidos/exportar`,
};
