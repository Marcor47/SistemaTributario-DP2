import { pedir } from './api.js'
import { EVENTO_SESION_INTERNA_EXPIRADA } from './authInternaService.js'
import { parametrosPricos, parametrosReporte } from '../utils/reportes.js'

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

export function generarReportePricos({ criterio, limite }, { signal } = {}) {
  const parametros = parametrosPricos({ criterio, limite })
  const consulta = parametros.size ? `?${parametros}` : ''
  return pedir(`/reportes/pricos${consulta}`, { signal }).catch(controlarSesion)
}

// Tributos y años con cuotas emitidas en la BD: opciones de los filtros de tributo y año.
export function obtenerOpcionesReporte({ signal } = {}) {
  return pedir('/reportes/opciones', { signal }).catch(controlarSesion)
}

export function listarZonas(tipo, { signal } = {}) {
  return pedir(`/reportes/zonas?tipo=${encodeURIComponent(tipo)}`, { signal }).catch(controlarSesion)
}

export function generarMorososPorZona(filtros, { signal } = {}) {
  const parametros = parametrosReporte({ filtros: ['periodo', 'zona', 'tributo', 'montoMinimo'] }, filtros)
  const consulta = parametros.size ? `?${parametros}` : ''
  return pedir(`/reportes/morosos-zona${consulta}`, { signal }).catch(controlarSesion)
}

export function generarBuenosPagadoresPorZona(filtros, { signal } = {}) {
  const parametros = parametrosReporte({ filtros: ['periodo', 'zona', 'tributo', 'montoMinimo'] }, filtros)
  const consulta = parametros.size ? `?${parametros}` : ''
  return pedir(`/reportes/buenos-pagadores-zona${consulta}`, { signal }).catch(controlarSesion)
}

export function exportarBuenosPagadoresPorZona(filtros, formato, columnas = []) {
  const parametros = parametrosReporte({ filtros: ['periodo', 'zona', 'tributo', 'montoMinimo'] }, filtros)
  parametros.set('formato', formato)
  columnas.forEach((columna) => parametros.append('columnas', columna))
  return descargarReporte(`/reportes/buenos-pagadores-zona/exportar?${parametros}`)
}

export function exportarReportePricos({ criterio, limite, columnas }, formato) {
  const parametros = parametrosPricos({ criterio, limite })
  parametros.set('formato', formato)
  columnas?.forEach((columna) => parametros.append('columnas', columna))
  return descargarReporte(`/reportes/PRICOS/exportar?${parametros}`)
}

export function listarVistas({ signal } = {}) {
  return pedir('/reportes/vistas', { signal }).catch(controlarSesion)
}

async function cabecerasProtegidas() {
  const token = await pedir('/auth/interno/csrf')
  return { [token.headerName]: token.token, 'Content-Type': 'application/json' }
}

export async function guardarVista(tipo, configuracion) {
  return pedir(`/reportes/${encodeURIComponent(tipo)}/vistas`, {
    method: 'POST',
    headers: await cabecerasProtegidas(),
    body: JSON.stringify(configuracion),
  }).catch(controlarSesion)
}

export function abrirVista(idVista) {
  return pedir(`/reportes/vistas/${idVista}/abrir`).catch(controlarSesion)
}

export async function eliminarVista(idVista) {
  return pedir(`/reportes/vistas/${idVista}`, {
    method: 'DELETE',
    headers: await cabecerasProtegidas(),
  }).catch(controlarSesion)
}

export function exportarVista(idVista, formato) {
  return descargarReporte(`/reportes/vistas/${idVista}/exportar?formato=${formato}`)
}

export function nombreDesdeCabecera(cabecera) {
  if (!cabecera) return null
  const extendido = cabecera.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (extendido) {
    try {
      return decodeURIComponent(extendido)
    } catch {
      // Si el valor extendido está mal codificado, se usa filename normal.
    }
  }
  return cabecera.match(/filename="?([^";]+)"?/i)?.[1] ?? null
}

export function nombreDescarga(ruta) {
  const extension = /[?&]formato=PDF(?:&|$)/i.test(ruta) ? 'pdf' : 'xlsx'
  const fecha = new Date().toLocaleDateString('en-CA').replaceAll('-', '')
  const tipo = ruta.match(/\/reportes\/(PRICOS|MOROSOS|morosos-zona|buenos-pagadores-zona)\/exportar/i)?.[1]?.toLowerCase()
  const nombreTipo = tipo === 'pricos' ? 'pricos'
    : tipo?.includes('morosos') ? 'morosos'
      : tipo?.includes('buenos') ? 'buenos_pagadores'
        : null
  return nombreTipo ? `reporte_${nombreTipo}_${fecha}.${extension}` : `reporte_${fecha}.${extension}`
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
  const nombre = nombreDesdeCabecera(respuesta.headers.get('content-disposition')) || nombreDescarga(ruta)
  return { blob: await respuesta.blob(), nombre }
}
