/* HU031: presentación de las segmentaciones que la jefatura aprueba y publica en el dashboard. */

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

/*
 * HU031: el dashboard resume la segmentación publicada con un gráfico de barras. La jefatura puede alternar
 * entre la deuda vencida (dónde está el dinero en riesgo) y los contribuyentes (cuántos alcanza cada segmento).
 */
export const METRICAS_SEGMENTO = {
  deuda: {
    etiqueta: 'Deuda vencida',
    valor: (segmento) => Number(segmento?.montoVencidoNoPagado) || 0,
  },
  contribuyentes: {
    etiqueta: 'Contribuyentes',
    valor: (segmento) => Number(segmento?.contribuyentes) || 0,
  },
}

export function prepararBarrasSegmentos(segmentos, metrica = 'deuda') {
  const clave = METRICAS_SEGMENTO[metrica] ? metrica : 'deuda'
  const config = METRICAS_SEGMENTO[clave]
  const filas = (Array.isArray(segmentos) ? segmentos : [])
    .filter((segmento) => segmento && segmento.idSegmento != null)
    .map((segmento) => ({
      idSegmento: segmento.idSegmento,
      nombre: segmento.nombre || 'Segmento sin nombre',
      contribuyentes: Number(segmento.contribuyentes) || 0,
      deuda: Number(segmento.montoVencidoNoPagado) || 0,
      morosidad: segmento.indiceMorosidad == null ? null : Number(segmento.indiceMorosidad),
      valor: config.valor(segmento),
    }))
    .sort((a, b) => b.valor - a.valor || a.nombre.localeCompare(b.nombre, 'es'))

  return {
    metrica: clave,
    etiqueta: config.etiqueta,
    filas,
    maximo: Math.max(0, ...filas.map((fila) => fila.valor)),
  }
}

/* Tres niveles para el color de la barra; el valor siempre se muestra como texto para no depender del color. */
export function nivelMorosidad(morosidad) {
  const valor = morosidad == null ? NaN : Number(morosidad)
  if (!Number.isFinite(valor)) return 'sin-dato'
  if (valor < 5) return 'baja'
  if (valor <= 20) return 'media'
  return 'alta'
}

export function resumirSegmentacionPublicada(segmentos) {
  const lista = (Array.isArray(segmentos) ? segmentos : [])
    .filter((segmento) => segmento && segmento.idSegmento != null)
  if (lista.length === 0) return null

  const primero = lista[0]
  const aprobador = lista.find((segmento) => segmento.aprobador)?.aprobador || null
  const fechaAprobacion = lista.reduce((ultima, segmento) => {
    if (!segmento.fechaAprobacion) return ultima
    return !ultima || segmento.fechaAprobacion > ultima ? segmento.fechaAprobacion : ultima
  }, null)

  return {
    modelo: primero.modelo || 'Modelo sin nombre',
    version: primero.versionModelo || '—',
    segmentos: lista.length,
    contribuyentes: lista.reduce((total, segmento) => total + (Number(segmento.tamano) || 0), 0),
    aprobador,
    fechaAprobacion,
  }
}
