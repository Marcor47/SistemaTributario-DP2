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

export function listarZonas(tipo, { signal } = {}) {
  return pedir(`/reportes/zonas?tipo=${encodeURIComponent(tipo)}`, { signal }).catch(controlarSesion)
}

export function generarMorososPorZona(filtros, { signal } = {}) {
  const parametros = parametrosReporte({ filtros: ['periodo', 'zona', 'tributo', 'montoMinimo'] }, filtros)
  const consulta = parametros.size ? `?${parametros}` : ''
  return pedir(`/reportes/morosos-zona${consulta}`, { signal }).catch(controlarSesion)
}

export function listarVistas({ signal } = {}) {
  return pedir('/reportes/vistas', { signal }).catch(controlarSesion)
}

export function guardarVista(tipo, configuracion) {
  return pedir(`/reportes/${encodeURIComponent(tipo)}/vistas`, {
    method: 'POST', body: JSON.stringify(configuracion),
  }).catch(controlarSesion)
}

export function abrirVista(idVista) {
  return pedir(`/reportes/vistas/${idVista}/abrir`).catch(controlarSesion)
}

export async function descargarReporte(ruta) {
  let respuesta
  try {
    respuesta = await fetch(`${import.meta.env?.VITE_API_URL || 'http://localhost:8080/api'}${ruta}`, { credentials: 'include' })
  } catch {
    throw Object.assign(new Error('No se pudo conectar con el servicio.'), { estado: 0 })
  }
  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => null)
    throw Object.assign(new Error(cuerpo?.mensaje || 'No se pudo exportar el reporte.'), { estado: respuesta.status })
  }
  const nombre = respuesta.headers.get('content-disposition')?.match(/filename="?([^";]+)"?/i)?.[1] || 'reporte'
  return { blob: await respuesta.blob(), nombre }
}
