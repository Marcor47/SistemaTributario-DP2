import { API, pedir } from './api.js'
import { notificarSesionExpirada } from './authInternaService.js'

const BASE = '/gestion/solicitudes-predio'
const TIPOS_ADJUNTO = ['application/pdf', 'image/jpeg', 'image/png']
const rutaSolicitud = (id) => `${BASE}/${encodeURIComponent(id)}`

async function pedirInterno(ruta, opciones) {
  try {
    return await pedir(ruta, opciones)
  } catch (error) {
    if (error.estado === 401) notificarSesionExpirada()
    throw error
  }
}

export function listarSolicitudesPredio(estado, signal) {
  return pedirInterno(`${BASE}?${new URLSearchParams({ estado })}`, { signal, cache: 'no-store' })
}

export function obtenerSolicitudPredio(id, signal) {
  return pedirInterno(rutaSolicitud(id), { signal, cache: 'no-store' })
}

export async function obtenerAdjuntoPredio(id, idAdjunto, signal) {
  const ruta = `${rutaSolicitud(id)}/adjuntos/${encodeURIComponent(idAdjunto)}`
  let respuesta
  try {
    respuesta = await fetch(`${API}${ruta}`, { credentials: 'include', cache: 'no-store', signal })
  } catch {
    throw new Error('No se pudo conectar con el servicio para abrir el adjunto.')
  }
  if (!respuesta.ok) {
    if (respuesta.status === 401) notificarSesionExpirada()
    const error = await respuesta.json().catch(() => null)
    throw new Error(error?.mensaje || 'No se pudo abrir el adjunto. Intenta nuevamente.')
  }
  const archivo = await respuesta.blob()
  if (!TIPOS_ADJUNTO.includes(archivo.type)) throw new Error('El formato del adjunto no se puede visualizar.')
  return archivo
}

async function enviarDecision(id, accion, datos) {
  const csrf = await pedirInterno('/auth/interno/csrf')
  return pedirInterno(`${rutaSolicitud(id)}/${accion}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(datos),
  })
}

export const aceptarSolicitudPredio = (id, datos) => enviarDecision(id, 'aceptacion', datos)
export const rechazarSolicitudPredio = (id, datos) => enviarDecision(id, 'rechazo', datos)
