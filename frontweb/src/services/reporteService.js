import { pedir } from './api.js'
import { EVENTO_SESION_INTERNA_EXPIRADA } from './authInternaService.js'
import { parametrosReporte } from '../utils/reportes.js'

function controlarSesion(error) {
  if (error.estado === 401 && typeof window !== 'undefined') window.dispatchEvent(new Event(EVENTO_SESION_INTERNA_EXPIRADA))
  throw error
}

export function obtenerCatalogoReportes({ signal } = {}) {
  return pedir('/reportes', { signal }).catch(controlarSesion)
}

export function generarReporte(reporte, filtros, { signal } = {}) {
  const parametros = parametrosReporte(reporte, filtros)
  const consulta = parametros.size ? `?${parametros}` : ''
  return pedir(`/reportes/${encodeURIComponent(reporte.tipo)}${consulta}`, { signal }).catch(controlarSesion)
}
