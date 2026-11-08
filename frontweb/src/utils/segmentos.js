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
