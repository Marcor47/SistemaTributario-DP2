import { pedir } from './api.js'
import { EVENTO_SESION_INTERNA_EXPIRADA } from './authInternaService.js'
import { parametrosSolicitudesReporte } from '../utils/solicitudesReportes.js'

function controlarSesion(error) {
  if (error.estado === 401 && typeof window !== 'undefined') window.dispatchEvent(new Event(EVENTO_SESION_INTERNA_EXPIRADA))
  throw error
}

export function consultarSolicitudesReporte(filtros, pagina, tamanio, { signal } = {}) {
  const parametros = parametrosSolicitudesReporte(filtros, pagina, tamanio)
  return pedir(`/reportes/solicitudes?${parametros}`, { signal, cache: 'no-store' }).catch(controlarSesion)
}
