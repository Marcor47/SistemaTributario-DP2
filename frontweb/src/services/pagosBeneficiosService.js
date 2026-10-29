import { pedir } from './api.js'
import { EVENTO_SESION_INTERNA_EXPIRADA } from './authInternaService.js'

// HU048: consulta autenticada de SAMNET, sin respaldo de datos locales.
export async function obtenerPagosBeneficios(consulta, { signal } = {}) {
  const parametros = new URLSearchParams({ consulta: String(consulta ?? '').trim() })
  try {
    return await pedir(`/contribuyentes/pagos-beneficios?${parametros}`, {
      signal,
      cache: 'no-store',
    })
  } catch (error) {
    if (!signal?.aborted && error.estado === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new Event(EVENTO_SESION_INTERNA_EXPIRADA))
    }
    throw error
  }
}
