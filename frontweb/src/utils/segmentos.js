/* HU031: presentación de las segmentaciones que la jefatura aprueba y publica en la plataforma analítica. */

const ETIQUETAS_ESTADO = {
  BORRADOR: 'Pendiente de aprobación',
  APROBADO: 'Aprobado',
  PUBLICADO: 'Publicado',
  RETIRADO: 'Retirado',
}

const CLASES_ESTADO = {
  BORRADOR: 'aviso',
  APROBADO: 'ok',
  PUBLICADO: 'ok',
  RETIRADO: 'neutro',
}

const ORDEN_ESTADO = {
  BORRADOR: 0,
  APROBADO: 1,
  PUBLICADO: 2,
  RETIRADO: 3,
}

export function etiquetaEstadoSegmento(estado) {
  return ETIQUETAS_ESTADO[estado] || estado || '—'
}

export function claseEstadoSegmento(estado) {
  return CLASES_ESTADO[estado] || 'neutro'
}

/*
 * Agrupa por modelo y estado. La jefatura aprueba la segmentación completa de un modelo, por eso el panel
 * necesita ver juntos los segmentos que se publicarán y los que se retirarán.
 */
export function agruparSegmentos(segmentos) {
  const grupos = new Map()

  for (const segmento of Array.isArray(segmentos) ? segmentos : []) {
    if (!segmento || segmento.idModelo == null) continue
    const clave = `${segmento.idModelo}:${segmento.estado || 'SIN_ESTADO'}`
    let grupo = grupos.get(clave)
    if (!grupo) {
      grupo = {
        clave,
        idModelo: segmento.idModelo,
        modelo: segmento.modelo || 'Modelo sin nombre',
        versionModelo: segmento.versionModelo || '—',
        estado: segmento.estado || 'SIN_ESTADO',
        segmentos: [],
        aprobador: null,
        fechaAprobacion: null,
      }
      grupos.set(clave, grupo)
    }
    grupo.segmentos.push(segmento)
    if (segmento.aprobador) grupo.aprobador = segmento.aprobador
    if (segmento.fechaAprobacion
      && (!grupo.fechaAprobacion || segmento.fechaAprobacion > grupo.fechaAprobacion)) {
      grupo.fechaAprobacion = segmento.fechaAprobacion
    }
  }

  return [...grupos.values()]
    .map((grupo) => {
      const segmentosOrdenados = [...grupo.segmentos].sort((a, b) =>
        String(a.nombre || '').localeCompare(String(b.nombre || ''), 'es'))
      return {
        ...grupo,
        segmentos: segmentosOrdenados,
        contribuyentes: segmentosOrdenados.reduce(
          (total, segmento) => total + (Number(segmento.tamano) || 0), 0),
      }
    })
    .sort((a, b) =>
      (ORDEN_ESTADO[a.estado] ?? 99) - (ORDEN_ESTADO[b.estado] ?? 99)
      || a.modelo.localeCompare(b.modelo, 'es')
      || a.versionModelo.localeCompare(b.versionModelo, 'es'))
}

export function participacionSegmento(segmento, grupo) {
  const total = Number(grupo?.contribuyentes) || 0
  const tamano = Number(segmento?.tamano) || 0
  if (total <= 0) return null
  return (tamano / total) * 100
}

/* El JSON lo entrega el modelo: se muestra sin reinterpretar. */
export function formatearVariablesCaracteristicas(texto) {
  if (!texto) return 'Sin variables registradas.'
  try {
    return JSON.stringify(JSON.parse(texto), null, 2)
  } catch {
    return String(texto)
  }
}

/* Nombres legibles de las variables que usa el modelo de comportamiento de pago. */
const VARIABLES_SEGMENTO = {
  pctATiempo: { etiqueta: 'Cuotas pagadas a tiempo', ayuda: 'Porcentaje de cuotas pagadas hasta su vencimiento.' },
  diasAtraso: { etiqueta: 'Atraso en el pago', ayuda: 'Días entre el vencimiento y el pago. Negativo: paga antes de vencer.' },
  deudaVencidaPct: { etiqueta: 'Deuda vencida', ayuda: 'Parte de lo emitido que está vencida y sin pagar.' },
  antiguedadDeudaAnios: { etiqueta: 'Antigüedad de la deuda', ayuda: 'Años de la deuda pendiente más antigua.' },
  mesesSinPago: { etiqueta: 'Tiempo desde el último pago', ayuda: 'Meses transcurridos desde el último pago registrado.' },
}

const INDICADORES_SEGMENTO = {
  prediosMediana: { etiqueta: 'Predios por contribuyente', unidad: '' },
  pctImpuestoPredialMediana: { etiqueta: 'Peso del Impuesto Predial en lo emitido', unidad: '%' },
  pctPagosEnLineaMediana: { etiqueta: 'Pagos realizados en línea', unidad: '%' },
}

const NUMERO = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 })

function humanizar(clave) {
  const texto = String(clave).replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim().toLowerCase()
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

function conUnidad(valor, unidad) {
  if (valor === null || valor === undefined || valor === '') return '—'
  if (typeof valor !== 'number') return String(valor)
  const numero = NUMERO.format(valor)
  if (!unidad) return numero
  return unidad === '%' ? `${numero} %` : `${numero} ${unidad}`
}

/* Posición del centro del segmento frente al promedio de todos los contribuyentes (en desviaciones estándar). */
export function nivelFrenteAlPromedio(centro) {
  if (typeof centro !== 'number' || Number.isNaN(centro)) return null
  if (centro >= 1) return { texto: 'Muy por encima del promedio', clase: 'muy-alto' }
  if (centro >= 0.35) return { texto: 'Por encima del promedio', clase: 'alto' }
  if (centro > -0.35) return { texto: 'Similar al promedio', clase: 'medio' }
  if (centro > -1) return { texto: 'Por debajo del promedio', clase: 'bajo' }
  return { texto: 'Muy por debajo del promedio', clase: 'muy-bajo' }
}

export function nivelCohesion(silueta) {
  if (typeof silueta !== 'number' || Number.isNaN(silueta)) return null
  if (silueta >= 0.5) return 'alta'
  if (silueta >= 0.25) return 'moderada'
  return 'baja'
}

/*
 * Convierte el JSON de variables características en filas legibles para la pantalla:
 * variables del modelo (valor típico y comparación con el promedio) e indicadores complementarios.
 */
export function interpretarVariablesCaracteristicas(texto) {
  const vacio = { variables: [], indicadores: [], textoLibre: '' }
  if (!texto) return vacio
  let datos
  try {
    datos = typeof texto === 'string' ? JSON.parse(texto) : texto
  } catch {
    return { ...vacio, textoLibre: String(texto) }
  }
  if (!datos || typeof datos !== 'object' || Array.isArray(datos)) return { ...vacio, textoLibre: String(texto) }

  const fuenteVariables = datos.variables && typeof datos.variables === 'object' ? datos.variables : {}
  const variables = Object.entries(fuenteVariables).map(([clave, valor]) => {
    const conocida = VARIABLES_SEGMENTO[clave]
    const detalle = valor && typeof valor === 'object' ? valor : { mediana: valor }
    return {
      clave,
      etiqueta: conocida?.etiqueta ?? humanizar(clave),
      ayuda: conocida?.ayuda ?? '',
      valor: conUnidad(detalle.mediana, detalle.unidad),
      nivel: nivelFrenteAlPromedio(detalle.centroEstandarizado),
    }
  })

  const indicadores = []
  const descriptivas = datos.descriptivas && typeof datos.descriptivas === 'object' ? datos.descriptivas : {}
  Object.entries(descriptivas).forEach(([clave, valor]) => {
    const conocido = INDICADORES_SEGMENTO[clave]
    indicadores.push({
      clave,
      etiqueta: conocido?.etiqueta ?? humanizar(clave.replace(/Mediana$/, '')),
      valor: conUnidad(valor, conocido?.unidad ?? ''),
    })
  })
  if (typeof datos.siluetaMedia === 'number') {
    indicadores.push({
      clave: 'siluetaMedia',
      etiqueta: 'Cohesión del segmento',
      valor: `${nivelCohesion(datos.siluetaMedia)} (${NUMERO.format(datos.siluetaMedia)})`,
    })
  }
  // Cualquier otro dato simple que entregue el modelo se muestra con su nombre legible.
  Object.entries(datos).forEach(([clave, valor]) => {
    if (['variables', 'descriptivas', 'siluetaMedia'].includes(clave)) return
    if (valor === null || typeof valor === 'object') return
    indicadores.push({ clave, etiqueta: humanizar(clave), valor: conUnidad(valor, '') })
  })
  return { variables, indicadores, textoLibre: '' }
}
