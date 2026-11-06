import { pedir } from './api.js'
import { EVENTO_SESION_INTERNA_EXPIRADA } from './authInternaService.js'

// HU031 / RF-043: revisión, aprobación y publicación de segmentos en el dashboard.
function controlarSesion(error) {
  if (error.estado === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new Event(EVENTO_SESION_INTERNA_EXPIRADA))
  }
  throw error
}

export function listarSegmentos(estado, { signal } = {}) {
  const parametros = new URLSearchParams()
  if (estado) parametros.set('estado', estado)
  const consulta = parametros.toString()
  return pedir(`/segmentos${consulta ? `?${consulta}` : ''}`, { signal }).catch(controlarSesion)
}

export function listarSegmentosPublicados({ signal } = {}) {
  return pedir('/segmentos/publicados', { signal }).catch(controlarSesion)
}

async function cabecerasProtegidas() {
  const token = await pedir('/auth/interno/csrf')
  return { [token.headerName]: token.token, 'Content-Type': 'application/json' }
}

export async function aprobarSegmentacion(idsSegmento) {
  if (!Array.isArray(idsSegmento) || idsSegmento.length === 0) {
    throw Object.assign(new Error('Selecciona una segmentación para aprobar.'), {
      codigo: 'SEGMENTACION_REQUERIDA',
      estado: 400,
      detalles: [],
    })
  }
  try {
    return await pedir('/segmentos/aprobaciones', {
      method: 'POST',
      headers: await cabecerasProtegidas(),
      body: JSON.stringify({ idsSegmento }),
    })
  } catch (error) {
    controlarSesion(error)
  }
}
