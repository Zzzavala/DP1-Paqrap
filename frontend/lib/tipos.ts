// Tipos de la API del backend (paquete pe.pucp.paqrap.visualizador y afines).

export type EstadoCorrida = 'SIN_CORRIDA' | 'EN_CURSO' | 'FINALIZADA' | 'DETENIDA';
export type EscenarioId = 'TIEMPO_REAL' | 'SIMULACION_5D' | 'COLAPSO_LOGISTICO';
export type AlgoritmoId = 'GA' | 'ACO';

export interface Kpis {
  registrados: number;
  entregados: number;
  entregadosATiempo: number;
  enCurso: number;
  pendientes: number;
  incumplidos: number;
  cumplimiento: number;
  costo: number;
  km: number;
  flotaActiva: number;
  flotaTotal: number;
  reRuteosBloqueo: number;
  reRuteosAveria: number;
  reasignaciones: number;
  bloqueosVigentes: number;
  pedidosSinUnidad: number;
}

export interface AlmacenVisual {
  id: string;
  nombre: string;
  x: number;
  y: number;
  central: boolean;
  capacidad: number | null;
  stock: number | null;
  /** Unidades en ruta que regresan a este almacen, con su hora estimada de llegada. */
  arriban: string[];
  /** Partes en rutas que ya salieron de este almacen (E20). */
  pedidosEnTransito: number;
  /** Partes asignadas que aun no salen, con su minuto simulado de salida (E21). */
  porSalir: PedidoHora[];
  /** Entregas de las ultimas 24 h simuladas de rutas que salieron de este almacen (E22). */
  entregados: PedidoHora[];
}

export interface PedidoHora {
  pedido: string;
  minuto: number;
}

/** Parte de un pedido (E31): unidad, ruta, almacen de salida y horas en minutos simulados (null si aun no tiene unidad). */
export interface ParteVisual {
  id: string;
  vehiculo: string | null;
  ruta: string | null;
  almacen: string | null;
  cantidad: number;
  salidaMin: number | null;
  llegadaMin: number | null;
  estado: string;
}

export interface VehiculoVisual {
  id: string;
  tipo: 'AUTO' | 'MOTO' | 'BICICLETA';
  estado: string;
  estadoEtiqueta: string;
  x: number;
  y: number;
  carga: number;
  capacidad: number;
  pedidos: string[];
  holguraMinima: number | null;
  destino: string;
  tray: number[];
  base: string;
  /** Tipo de averia vigente (1, 2 o 3) mientras la unidad esta AVERIADA o EN_TALLER; null si no. */
  tipoAveria: number | null;
  /** Minuto simulado en que termina la averia en el lugar (AVERIADA) o la estadia en taller (EN_TALLER). */
  finAveriaMin: number | null;
  /** Minutos simulados de la ruta actual: salida del origen, llegada a la siguiente parada y regreso. */
  salidaMin: number | null;
  llegadaProximaMin: number | null;
  regresoMin: number | null;
  /** Paquetes por pedido a bordo. */
  pedidosCarga: { id: string; cantidad: number }[];
  /** Minuto simulado desde el que la unidad va de regreso al almacen (tramo de retorno de la ruta). */
  retornoDesdeMin: number | null;
  /** Km recorridos en la ruta actual (E09, E17). */
  kmRecorridosRuta: number;
  /** Almacen del que salio en su ruta actual (o donde esta, si no tiene ruta) (E11). */
  origen: string;
}

export interface PedidoVisual {
  id: string;
  x: number;
  y: number;
  cantidad: number;
  plazo: number;
  registroMin: number;
  limiteMin: number;
  estado: string;
  vehiculos: string;
  reprogramado: boolean;
  cliente: string;
  partes: ParteVisual[];
}

export interface BloqueoVisual {
  id: string;
  puntos: number[];
  inicioMin: number;
  finMin: number;
}

export interface AlertaVisual {
  id: number;
  tipo: string;
  minuto: number;
  texto: string;
  vehiculo: string | null;
  pedido: string | null;
}

export interface InfoColapso {
  hora: number;
  fecha: string;
  motivo: string;
  cumplimiento: number;
  pedidosIncumplidos: number;
  pedidosSinUnidad: number;
  pedidosRegistrados: number;
  pedidosEntregados: number;
  diasSimulados: number;
}

export interface EstadoVisual {
  version: number;
  idCorrida: string;
  estadoCorrida: EstadoCorrida;
  escenario: EscenarioId;
  escenarioEtiqueta: string;
  algoritmo: AlgoritmoId;
  semilla: number;
  inicioMs: number;
  minutoActual: number;
  minutoFin: number | null;
  factor: number;
  tiempoRealSeg: number;
  diaActual: number;
  totalDias: number | null;
  umbralVerde: number;
  umbralAmbar: number;
  kpis: Kpis;
  almacenes: AlmacenVisual[];
  vehiculos: VehiculoVisual[];
  pedidos: PedidoVisual[];
  bloqueos: BloqueoVisual[];
  tramosManuales: number[][];
  alertas: AlertaVisual[];
  colapso: InfoColapso | null;
  mensaje: string | null;
}

export interface EventoDetalle {
  fecha: string;
  minuto: number;
  tipo: string;
  detalle: string;
}

export interface ParadaDetalle {
  secuencia: number;
  pedido: string;
  cliente: string;
  x: number;
  y: number;
  cantidad: number;
  llegada: string;
  salida: string;
  limite: string;
  estado: string;
  aTiempo: boolean;
}

export interface DetalleVehiculo {
  id: string;
  tipo: string;
  estado: string;
  x: number;
  y: number;
  almacenBase: string;
  capacidad: number;
  carga: number;
  velocidad: number;
  costoKm: number;
  horasAlimentacion: string[];
  turnoActual: string;
  proximoMantenimiento: string;
  averia: string | null;
  ruta: {
    id: string;
    causa: string;
    salida: string;
    regresoEstimado: string;
    almacenSalida: string;
    almacenRetorno: string;
    carga: number;
    paradas: ParadaDetalle[];
  } | null;
  pedidosABordo: string[];
  proximaParada: string;
  eta: string;
  rutasRealizadas: number;
  entregas: number;
  km: number;
  costo: number;
  eventos: EventoDetalle[];
}

export interface DetallePedido {
  id: string;
  cliente: string;
  x: number;
  y: number;
  cantidad: number;
  tipoEntrega: string;
  plazo: number;
  registro: string;
  limite: string;
  estado: string;
  holgura: number;
  vehiculos: string[];
  almacenes: string[];
  rutas: string[];
  entregaReal: string | null;
  aTiempo: boolean | null;
  reprogramado: boolean;
  canal: string;
  observaciones: string | null;
  partes: { id: string; cantidad: number; estado: string }[];
  modificable: boolean;
  eventos: EventoDetalle[];
}

export interface DetalleAlmacen {
  id: string;
  nombre: string;
  x: number;
  y: number;
  central: boolean;
  capacidad: number | null;
  stock: number | null;
  porcentaje: number | null;
  despachadoHoy: number;
  recarga: string;
  vehiculosEnAlmacen: string[];
  vehiculosEnCamino: string[];
}

export interface Configuracion {
  escenarios: { id: EscenarioId; etiqueta: string; descripcion: string; fechaInicioPorDefecto: string }[];
  algoritmos: { id: AlgoritmoId; etiqueta: string; parametros: Record<string, number> }[];
  tiposEntrega: { id: string; etiqueta: string; plazoHoras: number }[];
  flota: { tipo: string; etiqueta: string; letra: string; cantidad: number; capacidad: number; costoKm: number; velocidad: number; codigos: string }[];
  almacenes: { id: string; nombre: string; rol: string; x: number; y: number; capacidad: string; recarga: string }[];
  turnos: { rango: string; alimentacion: string }[];
  reglaAlimentacion: string;
  tiposAveria: { tipo: number; etiqueta: string; transbordo: boolean }[];
  umbrales: { verde: number; ambar: number };
  ciudad: { anchoKm: number; altoKm: number };
  parametrosPorDefecto: ParametrosCorrida;
  operacionPorDefecto: ParametrosOperacion;
  datosDisponibles: { primerMes: string; ultimoMes: string; meses: number };
}

export interface ParametrosOperacion {
  velocidadAuto: number;
  velocidadMoto: number;
  velocidadBicicleta: number;
  costoKmAuto: number;
  costoKmMoto: number;
  costoKmBicicleta: number;
  capacidadAuto: number;
  capacidadMoto: number;
  capacidadBicicleta: number;
  cantidadAutos: number;
  cantidadMotos: number;
  cantidadBicicletas: number;
  capacidadAlmacenIntermedio: number;
  horasCambioTurno: number[];
  duracionAlimentacionH: number;
  tiempoServicioH: number;
  plazoRegularH: number;
  plazoPriorizada4H: number;
  plazoPriorizada8H: number;
  plazoPriorizada12H: number;
  plazoPriorizada18H: number;
  [clave: string]: unknown;
}

export interface ParametrosCorrida {
  escenario: EscenarioId;
  algoritmo: AlgoritmoId;
  semilla: number;
  fechaInicio: string | null;
  umbrales: { verde: number; ambar: number };
  diasSimulacion: number;
  duracionObjetivoMin: number;
  factorAceleracionColapso: number;
  ventanaPlanificacionMin: number;
  umbralColapsoVencidos: number;
  umbralColapsoBacklog: number;
  averiasPorDia: number;
  generarAverias: boolean;
  parametrosAlgoritmo: Record<string, number>;
  operacion: ParametrosOperacion;
}

export interface ResumenArchivo {
  tipo: string;
  nombre: string;
  descripcion: string;
  registros: number;
  lineas: number;
  errores: number;
  valido: boolean;
  personalizado: boolean;
  detalleErrores: { numeroLinea: number; contenido: string; motivo: string }[];
  advertencia?: string | null;
}

export interface CierreCorrida {
  idCorrida: string;
  escenario: EscenarioId;
  escenarioEtiqueta: string;
  algoritmo: AlgoritmoId;
  semilla: number;
  fechaInicio: string;
  fechaFin: string;
  horasSimuladas: number;
  estado: string;
  motivoFin: string | null;
  inicioRealMs: number;
  finRealMs: number;
  duracionRealSeg: number;
  resumen: {
    pedidosRegistrados: number;
    entregados: number;
    entregadosATiempo: number;
    entregadosTarde: number;
    enCurso: number;
    incumplidosSinEntregar: number;
    cancelados: number;
    cumplimiento: number;
    porcentajeEntregadosATiempo: number;
    costoTotal: number;
    kmTotal: number;
    reRuteosBloqueo: number;
    reRuteosAveria: number;
    reasignaciones: number;
    reprogramaciones: number;
    pedidosDivididos: number;
    paradasRecortadasMantenimiento: number;
    rutasDespachadas: number;
    unidadesEntregadas: number;
    costoPorPedidoEntregado: number;
    tiempoComputoPlanificadorMs: number;
    planificaciones: number;
  };
  cumplimientoPorDia: {
    dia: number;
    fecha: string;
    registrados: number;
    aTiempo: number;
    incumplidos: number;
    enCurso: number;
    cumplimiento: number;
    cumplimientoAcumuladoAlCierre: number | null;
  }[];
  kmPorTipo: { tipo: string; unidades: number; rutas: number; km: number; costo: number }[];
  utilizacionFlota: {
    id: string;
    tipo: string;
    rutas: number;
    entregas: number;
    unidades: number;
    km: number;
    costo: number;
    horasEnRuta: number;
    factorCargaPromedio: number;
  }[];
  incidencias: {
    bloqueosProgramados: number;
    bloqueosManuales: number;
    averias: number;
    detalleAverias: { id: string; vehiculo: string; tipo: string; fecha: string; origen: string; causa: string; pedidosAfectados: number }[];
    eventosPorTipo: Record<string, number>;
  };
  verificacion: {
    pedidosEntregados: number;
    entregadosFueraDePlazo: number;
    pedidosFueraDePlazo: string[];
    pedidosIncumplidos: number;
    politicaCumplida: boolean;
    mensaje: string;
  };
  colapso: InfoColapso | null;
  parametros: Record<string, unknown>;
  fuentes: Record<string, string>;
  /** G08-G10: rutas vigentes al cierre (null en corridas guardadas antes de este cambio). */
  ultimaPlanificacion: {
    fecha: string;
    causa: string;
    rutas: {
      vehiculo: string;
      tipo: string;
      ruta: string;
      almacenSalida: string;
      salida: string;
      paradas: { pedido: string; cantidad: number; llegada: string }[];
      almacenRetorno: string;
      regreso: string;
    }[];
  } | null;
}

export interface FilaPedido {
  id: string;
  cliente: string;
  x: number;
  y: number;
  cantidad: number;
  tipoEntrega: string;
  plazo: number;
  registro: string;
  limite: string;
  estado: string;
  vehiculos: string;
  entregaReal: string;
  aTiempo: boolean | null;
  reprogramado: boolean;
  canal: string;
  observaciones: string;
  holgura: number;
  modificable: boolean;
}

export interface CorridaHistorial {
  id: string;
  escenario: EscenarioId;
  algoritmo: AlgoritmoId;
  semilla: number;
  fechaInicioSim: string;
  inicioReal: string;
  finReal: string | null;
  estado: string;
  motivoFin: string | null;
  cumplimiento: number | null;
  costoTotal: number | null;
  pedidos: number | null;
  tieneCierre: boolean;
}

export interface ResumenEstadistico {
  n: number;
  media: number;
  desviacion: number;
  minimo: number;
  maximo: number;
}

