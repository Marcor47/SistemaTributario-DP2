import { ETIQUETAS_ROL } from './roles.js'
import { GRUPOS_DETALLE_MOROSOS, GRUPOS_MOROSOS, GRUPOS_PRICOS } from './reportes.js'
import { normalizar } from './formato.js'

/*
 * HU045 (RF-060): acceso y alcance de la información en reportes.
 *
 * El modelo de análisis define una RestriccionReporte por rol y reporte, con dimensión
 * REPORTE, ZONA o SEGMENTO. Como HU045 todavía no tiene backend, esta configuración se
 * guarda en el navegador (ver accesoReportesService.js) y sirve de contrato para la API.
 */

export const VERSION_ACCESO_REPORTES = 2
export const MAX_VALORES_ALCANCE = 30
export const MAX_LONGITUD_VALOR_ALCANCE = 60
export const ZONAS_SUGERIDAS = ['Z1', 'Z2', 'Z3']
export const ROLES_CON_ACCESO_REPORTES_POR_DEFECTO = ['GERENCIA', 'ANALISTA_RENTAS']

export const MODULOS_ACCESO_REPORTES = [
  {
    id: 'REPORTES',
    titulo: 'Reportes tributarios',
    descripcion: 'Catálogo de la pantalla /reportes: morosos, PRICOS y buenos pagadores.',
  },
  {
    id: 'REPORTERIA',
    titulo: 'Plataforma analítica',
    descripcion: 'Pestañas de /reporteria: resumen, PRICOS, morosos y buenos pagadores.',
  },
]

export const ROLES_ACCESO_REPORTES = Object.keys(ETIQUETAS_ROL)

const ETIQUETAS_MOROSOS = {
  codigo: 'Código',
  razonSocial: 'Razón social',
  regimen: 'Régimen',
  tipoDocumento: 'Tipo doc.',
  numeroDocumento: 'N° doc.',
  via: 'Vía',
  numero: 'N°',
  departamento: 'Dpto',
  interior: 'Interior',
  manzana: 'Mz',
  lote: 'Lote',
  distrito: 'Distrito',
  zonas: 'Zonas',
  deudaVencida: 'Total con beneficio',
  deudaSinBeneficio: 'Total sin beneficio',
  condicion: 'Condición',
  tipoPersona: 'Tipo persona',
  correo: 'Email',
  telefonos: 'Teléfonos',
  anio: 'Año',
  codigoPredio: 'Predio',
  zona: 'Zona',
  tributo: 'Tributo',
  periodo: 'Cuota',
  fechaVencimiento: 'Vencimiento',
  diasVencido: 'Días vencido',
  etapaCobranza: 'Etapa',
  montoSinBeneficio: 'Sin beneficio',
  montoConBeneficio: 'Con beneficio',
}

const ETIQUETAS_PRICOS = {
  codigo: 'Código',
  razonSocial: 'Razón social',
  regimen: 'Régimen',
  tipoDocumento: 'Tipo doc.',
  numeroDocumento: 'N° doc.',
  domicilioFiscal: 'Domicilio fiscal',
  via: 'Vía',
  numero: 'N°',
  departamento: 'Dpto',
  interior: 'Interior',
  manzana: 'Mz',
  lote: 'Lote',
  distrito: 'Distrito',
  baseImponible: 'Base imponible',
  emisionPredial: 'Predial',
  emisionArbitrios: 'Arbitrios',
  deudaAnteriorPredial: 'Predial',
  deudaAnteriorArbitrios: 'Arbitrios',
  deudaActualPredial: 'Predial',
  deudaActualArbitrios: 'Arbitrios',
  porcentajeDescuentoArbitrios: '% dscto. arbitrios',
  condicion: 'Condición',
  tipoPersona: 'Tipo persona',
  correo: 'Email',
  telefonos: 'Teléfonos',
}

const ETIQUETAS_BUENOS_PAGADORES = {
  codigo: 'Código',
  razonSocial: 'Razón social',
  regimen: 'Régimen',
  tipoDocumento: 'Tipo doc.',
  numeroDocumento: 'N° doc.',
  domicilioFiscal: 'Domicilio fiscal',
  zonas: 'Zonas',
  montoEmitidoPredial: 'Emitido predial',
  montoEmitidoArbitrios: 'Emitido arbitrios',
  montoPagadoPredial: 'Pagado predial',
  montoPagadoArbitrios: 'Pagado arbitrios',
  totalEmitido: 'Total emitido',
  totalPagado: 'Total pagado',
  cuotasPagadasATiempo: 'Cuotas a tiempo',
}

const ETIQUETAS_REPORTERIA_BASE = {
  puesto: '#',
  codigo: 'Código',
  nombre: 'Contribuyente',
  zona: 'Zona',
  segmento: 'Segmento',
}

const ETIQUETAS_REPORTERIA_RESUMEN = {
  ...ETIQUETAS_REPORTERIA_BASE,
  pagado: 'Pagado en el periodo',
  emitido: 'Emisión del periodo',
  cobrado: '% cobrado',
  deuda: 'Deuda vencida',
}

const ETIQUETAS_REPORTERIA_PRICOS = {
  ...ETIQUETAS_REPORTERIA_BASE,
  uso: 'Uso principal',
  predios: 'Predios',
  emitido: 'Emisión',
  cobrado: 'Cobrado',
  cumplimiento: '% cumplimiento',
  deuda: 'Deuda vencida',
}

const ETIQUETAS_REPORTERIA_MOROSOS = {
  ...ETIQUETAS_REPORTERIA_BASE,
  deuda: 'Deuda vencida',
  vencidas: 'Cuotas vencidas',
  atraso: 'Atraso máximo (meses)',
  tributo: 'Tributo principal de la deuda',
  ultimo: 'Último pago',
}

const ETIQUETAS_REPORTERIA_BUENOS = {
  ...ETIQUETAS_REPORTERIA_BASE,
  pagado: 'Pagado en el periodo',
  aTiempo: 'Cuotas a tiempo',
  emitido: 'Emisión del periodo',
  predios: 'Predios',
  tipo: 'Tipo de persona',
  ultimo: 'Último pago',
}

const GRUPOS_MOROSOS_COMPLETOS = [
  ...GRUPOS_MOROSOS,
  ...GRUPOS_DETALLE_MOROSOS.map((grupo) => ({
    clave: `detalle-${grupo.clave}`,
    etiqueta: `Detalle · ${grupo.etiqueta}`,
    columnas: grupo.columnas,
  })),
]

const GRUPOS_BUENOS_PAGADORES = [
  {
    clave: 'contribuyente',
    etiqueta: 'Contribuyente',
    columnas: ['codigo', 'razonSocial', 'regimen', 'tipoDocumento', 'numeroDocumento'],
  },
  {
    clave: 'domicilio',
    etiqueta: 'Domicilio',
    columnas: ['domicilioFiscal', 'zonas'],
  },
  {
    clave: 'pagos',
    etiqueta: 'Pagos y cumplimiento',
    columnas: [
      'montoEmitidoPredial',
      'montoEmitidoArbitrios',
      'montoPagadoPredial',
      'montoPagadoArbitrios',
      'totalEmitido',
      'totalPagado',
      'cuotasPagadasATiempo',
    ],
  },
]

const COLUMNAS_BASE_REPORTERIA = ['puesto', 'codigo', 'nombre', 'zona', 'segmento']

function gruposReporteria(columnasMetricas) {
  return [
    { clave: 'contribuyente', etiqueta: 'Contribuyente', columnas: COLUMNAS_BASE_REPORTERIA },
    { clave: 'metricas', etiqueta: 'Métricas del ranking', columnas: columnasMetricas },
  ]
}

const DEFINICIONES = [
  {
    id: 'MOROSOS',
    modulo: 'REPORTES',
    titulo: 'Reporte de morosos',
    descripcion: 'Contribuyentes con cuotas vencidas, su deuda y el detalle de sus obligaciones.',
    grupos: GRUPOS_MOROSOS_COMPLETOS,
    etiquetas: ETIQUETAS_MOROSOS,
    columnasFijas: [
      'codigo', 'razonSocial', 'deudaVencida',
      'codigoPredio', 'anio', 'tributo', 'periodo', 'montoConBeneficio',
    ],
  },
  {
    id: 'PRICOS',
    modulo: 'REPORTES',
    titulo: 'Reporte de PRICOS',
    descripcion: 'Principales contribuyentes por emisión, con su base imponible y deuda.',
    grupos: GRUPOS_PRICOS,
    etiquetas: ETIQUETAS_PRICOS,
    columnasFijas: [
      'codigo', 'razonSocial', 'baseImponible', 'emisionPredial', 'emisionArbitrios',
      'deudaActualPredial', 'deudaActualArbitrios',
    ],
  },
  {
    id: 'BUENOS_PAGADORES',
    modulo: 'REPORTES',
    titulo: 'Reporte de buenos pagadores',
    descripcion: 'Contribuyentes que pagaron todas sus cuotas del periodo a tiempo.',
    grupos: GRUPOS_BUENOS_PAGADORES,
    etiquetas: ETIQUETAS_BUENOS_PAGADORES,
    columnasFijas: ['codigo', 'razonSocial', 'totalPagado', 'cuotasPagadasATiempo'],
  },
  {
    id: 'REPORTERIA_RESUMEN',
    modulo: 'REPORTERIA',
    titulo: 'Plataforma analítica · Resumen',
    descripcion: 'Indicadores generales y Top 10 de contribuyentes por monto pagado.',
    grupos: gruposReporteria(['pagado', 'emitido', 'cobrado', 'deuda']),
    etiquetas: ETIQUETAS_REPORTERIA_RESUMEN,
    columnasFijas: ['puesto', 'nombre', 'pagado'],
  },
  {
    id: 'REPORTERIA_PRICOS',
    modulo: 'REPORTERIA',
    titulo: 'Plataforma analítica · PRICOS',
    descripcion: 'Concentración de la emisión y cumplimiento de los principales contribuyentes.',
    grupos: gruposReporteria(['uso', 'predios', 'emitido', 'cobrado', 'cumplimiento', 'deuda']),
    etiquetas: ETIQUETAS_REPORTERIA_PRICOS,
    columnasFijas: ['puesto', 'nombre', 'emitido'],
  },
  {
    id: 'REPORTERIA_MOROSOS',
    modulo: 'REPORTERIA',
    titulo: 'Plataforma analítica · Morosos',
    descripcion: 'Deuda vencida por zona, tributo, segmento y antigüedad de la mora.',
    grupos: gruposReporteria(['deuda', 'vencidas', 'atraso', 'tributo', 'ultimo']),
    etiquetas: ETIQUETAS_REPORTERIA_MOROSOS,
    columnasFijas: ['puesto', 'nombre', 'deuda'],
  },
  {
    id: 'REPORTERIA_BUENOS',
    modulo: 'REPORTERIA',
    titulo: 'Plataforma analítica · Buenos pagadores',
    descripcion: 'Contribuyentes con todas sus cuotas del periodo pagadas a tiempo.',
    grupos: gruposReporteria(['pagado', 'aTiempo', 'emitido', 'predios', 'tipo', 'ultimo']),
    etiquetas: ETIQUETAS_REPORTERIA_BUENOS,
    columnasFijas: ['puesto', 'nombre', 'pagado', 'aTiempo'],
  },
]

export const REPORTES_CONFIGURABLES = DEFINICIONES.map((reporte) => {
  const columnas = [...new Set(reporte.grupos.flatMap((grupo) => grupo.columnas))]
  const fijas = new Set(reporte.columnasFijas)
  const columnasFijas = columnas.filter((columna) => fijas.has(columna))
  const columnasConfigurables = columnas.filter((columna) => !fijas.has(columna))
  const configurables = new Set(columnasConfigurables)
  const gruposConfigurables = reporte.grupos
    .map((grupo) => ({
      ...grupo,
      columnas: grupo.columnas.filter((columna) => configurables.has(columna)),
    }))
    .filter((grupo) => grupo.columnas.length > 0)

  return {
    ...reporte,
    columnas,
    columnasFijas,
    columnasConfigurables,
    gruposConfigurables,
    columnasPredeterminadas: [...columnas],
  }
})

export const REPORTE_CONFIGURABLE_POR_ID = new Map(
  REPORTES_CONFIGURABLES.map((reporte) => [reporte.id, reporte]),
)

export function reportesDelModulo(modulo) {
  return REPORTES_CONFIGURABLES.filter((reporte) => reporte.modulo === modulo)
}

export function reporteConfigurable(idReporte) {
  return REPORTE_CONFIGURABLE_POR_ID.get(idReporte) ?? null
}

export function clonarConfiguracion(configuracion) {
  return JSON.parse(JSON.stringify(configuracion))
}

function rolesConAccesoSet(rolesConAcceso) {
  const lista = Array.isArray(rolesConAcceso)
    ? rolesConAcceso
    : ROLES_CON_ACCESO_REPORTES_POR_DEFECTO
  return new Set(lista.filter((rol) => ROLES_ACCESO_REPORTES.includes(rol)))
}

export function configuracionPorDefecto(
  rolesConAcceso = ROLES_CON_ACCESO_REPORTES_POR_DEFECTO,
) {
  const conAcceso = rolesConAccesoSet(rolesConAcceso)
  const roles = {}
  for (const rol of ROLES_ACCESO_REPORTES) {
    const reportes = {}
    for (const reporte of REPORTES_CONFIGURABLES) {
      reportes[reporte.id] = {
        permitido: conAcceso.has(rol),
        zonas: [],
        segmentos: [],
        columnas: [...reporte.columnasPredeterminadas],
      }
    }
    roles[rol] = { reportes }
  }
  return {
    version: VERSION_ACCESO_REPORTES,
    actualizadoEn: null,
    actualizadoPor: null,
    roles,
  }
}

function normalizarLista(valores, maximo = MAX_VALORES_ALCANCE) {
  if (!Array.isArray(valores)) return []
  const vistos = new Set()
  const salida = []
  for (const item of valores) {
    const texto = String(item ?? '').trim().slice(0, MAX_LONGITUD_VALOR_ALCANCE)
    if (!texto) continue
    const clave = texto.toLocaleLowerCase('es')
    if (vistos.has(clave)) continue
    vistos.add(clave)
    salida.push(texto)
    if (salida.length >= maximo) break
  }
  return salida
}

function columnasValidas(columnas, reporte) {
  if (!Array.isArray(columnas)) return [...(reporte?.columnasPredeterminadas ?? [])]
  const fijas = new Set(reporte?.columnasFijas ?? [])
  const permitidas = new Set(reporte?.columnas ?? [])
  const seleccionadas = new Set(
    columnas.filter((columna) => permitidas.has(columna) && !fijas.has(columna)),
  )
  // Las columnas fijas identifican el reporte o contienen su dato principal: siempre se conservan.
  return (reporte?.columnas ?? []).filter((columna) => fijas.has(columna) || seleccionadas.has(columna))
}

export function normalizarConfiguracion(
  datos,
  rolesConAcceso = ROLES_CON_ACCESO_REPORTES_POR_DEFECTO,
) {
  const conAcceso = rolesConAccesoSet(rolesConAcceso)
  const base = configuracionPorDefecto(rolesConAcceso)
  if (!datos || typeof datos !== 'object') return base

  const migrar = Number(datos.version ?? 1) < VERSION_ACCESO_REPORTES
  if (typeof datos.actualizadoEn === 'string') base.actualizadoEn = datos.actualizadoEn
  if (typeof datos.actualizadoPor === 'string') base.actualizadoPor = datos.actualizadoPor

  for (const rol of ROLES_ACCESO_REPORTES) {
    const entrada = datos.roles?.[rol]
    if (!entrada || typeof entrada !== 'object') continue
    for (const reporte of REPORTES_CONFIGURABLES) {
      const guardado = entrada.reportes?.[reporte.id]
      if (!guardado || typeof guardado !== 'object') continue
      base.roles[rol].reportes[reporte.id] = {
        permitido: guardado.permitido !== false,
        zonas: normalizarLista(guardado.zonas),
        segmentos: normalizarLista(guardado.segmentos),
        columnas: columnasValidas(guardado.columnas, reporte),
      }
    }
  }

  // Migración del mock anterior: los roles sin REPORTES:CONSULTAR parten con reportes desactivados.
  if (migrar) {
    for (const rol of ROLES_ACCESO_REPORTES) {
      if (conAcceso.has(rol)) continue
      for (const reporte of REPORTES_CONFIGURABLES) {
        base.roles[rol].reportes[reporte.id].permitido = false
      }
    }
  }

  return base
}

export function configuracionDeRol(configuracion, rol) {
  if (!configuracion?.roles?.[rol]) return null
  return configuracion.roles[rol]
}

export function resumenDeRol(configuracion, rol) {
  const configuracionRol = configuracionDeRol(configuracion, rol)
  const reportes = REPORTES_CONFIGURABLES
    .map((definicion) => ({
      definicion,
      valor: configuracionRol?.reportes?.[definicion.id],
    }))
    .filter((item) => item.valor)
  return {
    permitidos: reportes.filter((item) => item.valor.permitido).length,
    total: REPORTES_CONFIGURABLES.length,
    conAlcance: reportes.filter((item) => item.valor.zonas.length > 0 || item.valor.segmentos.length > 0).length,
    columnasLimitadas: reportes.filter((item) => {
      const opcionales = new Set(item.definicion.columnasConfigurables)
      return item.valor.columnas.filter((columna) => opcionales.has(columna)).length < opcionales.size
    }).length,
  }
}

export function actualizarReporte(configuracion, rol, idReporte, cambios) {
  const copia = clonarConfiguracion(configuracion)
  const reporte = reporteConfigurable(idReporte)
  const actual = copia?.roles?.[rol]?.reportes?.[idReporte]
  if (!actual || !reporte) return copia

  copia.roles[rol].reportes[idReporte] = {
    ...actual,
    ...cambios,
    permitido: 'permitido' in cambios ? Boolean(cambios.permitido) : actual.permitido,
    zonas: 'zonas' in cambios ? normalizarLista(cambios.zonas) : actual.zonas,
    segmentos: 'segmentos' in cambios ? normalizarLista(cambios.segmentos) : actual.segmentos,
    columnas: 'columnas' in cambios ? columnasValidas(cambios.columnas, reporte) : actual.columnas,
  }
  return copia
}

export function marcarTodosLosReportes(configuracion, rol, permitido) {
  const copia = clonarConfiguracion(configuracion)
  if (!copia?.roles?.[rol]) return copia
  for (const reporte of REPORTES_CONFIGURABLES) {
    copia.roles[rol].reportes[reporte.id].permitido = Boolean(permitido)
  }
  return copia
}

export function copiarConfiguracionRol(configuracion, rolOrigen, rolDestino) {
  const copia = clonarConfiguracion(configuracion)
  if (!copia?.roles?.[rolOrigen] || !copia?.roles?.[rolDestino]) return copia
  copia.roles[rolDestino] = clonarConfiguracion(copia.roles[rolOrigen])
  return copia
}

export function aplicarConfiguracionATodosLosRoles(
  configuracion,
  rolOrigen,
  rolesDestino = ROLES_ACCESO_REPORTES,
) {
  const copia = clonarConfiguracion(configuracion)
  if (!copia?.roles?.[rolOrigen]) return copia
  const origen = clonarConfiguracion(copia.roles[rolOrigen])
  const destinos = Array.isArray(rolesDestino) ? rolesDestino : ROLES_ACCESO_REPORTES
  for (const rol of destinos) {
    if (copia.roles[rol]) copia.roles[rol] = clonarConfiguracion(origen)
  }
  return copia
}

export function agregarValorAlcance(valores, valor) {
  const actuales = normalizarLista(valores)
  const texto = String(valor ?? '').trim().slice(0, MAX_LONGITUD_VALOR_ALCANCE)
  if (!texto || actuales.length >= MAX_VALORES_ALCANCE) return actuales
  if (actuales.some((item) => item.toLocaleLowerCase('es') === texto.toLocaleLowerCase('es'))) return actuales
  return [...actuales, texto]
}

export function quitarValorAlcance(valores, valor) {
  const texto = String(valor ?? '').trim().toLocaleLowerCase('es')
  return normalizarLista(valores).filter((item) => item.toLocaleLowerCase('es') !== texto)
}

/**
 * Nombres únicos de los segmentos publicados, con el tamaño como dato de apoyo.
 * El valor que se guarda en la configuración sigue siendo el nombre.
 */
export function opcionesSegmentosPublicados(segmentos) {
  const porNombre = new Map()
  for (const segmento of Array.isArray(segmentos) ? segmentos : []) {
    const nombre = String(segmento?.nombre ?? '').trim().slice(0, MAX_LONGITUD_VALOR_ALCANCE)
    if (!nombre) continue
    const clave = normalizar(nombre)
    if (!clave || porNombre.has(clave)) continue
    const tamano = Number(segmento?.tamano)
    porNombre.set(clave, {
      valor: nombre,
      detalle: Number.isFinite(tamano) && tamano > 0
        ? `${tamano} ${tamano === 1 ? 'contribuyente' : 'contribuyentes'}`
        : null,
    })
  }
  return [...porNombre.values()].sort((a, b) => a.valor.localeCompare(b.valor, 'es', { sensitivity: 'base' }))
}

/** Filtra opciones de alcance por nombre o detalle, sin distinguir tildes ni mayúsculas. */
export function filtrarOpcionesAlcance(opciones, texto) {
  const lista = Array.isArray(opciones) ? opciones : []
  const consulta = normalizar(texto)
  if (!consulta) return lista
  return lista.filter((opcion) => {
    const valor = typeof opcion === 'string' ? opcion : opcion?.valor
    const detalle = typeof opcion === 'string' ? '' : opcion?.detalle
    return normalizar(`${valor ?? ''} ${detalle ?? ''}`).includes(consulta)
  })
}
