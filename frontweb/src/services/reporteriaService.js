import { pedir } from './api.js'
import { EVENTO_SESION_INTERNA_EXPIRADA } from './authInternaService.js'

const API = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api'

function controlarSesion(error) {
  if (error.estado === 401 && typeof window !== 'undefined') window.dispatchEvent(new Event(EVENTO_SESION_INTERNA_EXPIRADA))
  throw error
}

async function cabecerasProtegidas() {
  const token = await pedir('/auth/interno/csrf')
  return { [token.headerName]: token.token, 'Content-Type': 'application/json' }
}

export function obtenerCatalogo({ signal } = {}) {
  return pedir('/reporteria/catalogo', { signal }).catch(controlarSesion)
}

export function obtenerDatos(consulta, { signal } = {}) {
  const parametros = new URLSearchParams()
  for (const [campo, valor] of Object.entries(consulta)) {
    if (valor !== '' && valor != null) parametros.set(campo, valor)
  }
  return pedir(`/reporteria/datos?${parametros}`, { signal, cache: 'no-store' }).catch(controlarSesion)
}

export function listarVistas({ signal } = {}) {
  return pedir('/reporteria/vistas', { signal }).catch(controlarSesion)
}

export async function guardarVista(nombre, estado) {
  return pedir('/reporteria/vistas', {
    method: 'POST',
    headers: await cabecerasProtegidas(),
    body: JSON.stringify({ nombre, estado }),
  }).catch(controlarSesion)
}

export async function eliminarVista(idVista) {
  const respuesta = await fetch(`${API}/reporteria/vistas/${idVista}`, {
    method: 'DELETE',
    headers: await cabecerasProtegidas(),
    credentials: 'include',
  }).catch(() => {
    throw Object.assign(new Error('No se pudo conectar con el servicio.'), { estado: 0 })
  })
  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => null)
    controlarSesion(Object.assign(new Error(cuerpo?.mensaje || 'No se pudo eliminar la vista.'), { estado: respuesta.status }))
  }
}

export async function exportarVista(formato, vista) {
  const headers = await cabecerasProtegidas()
  let respuesta
  try {
    respuesta = await fetch(`${API}/reporteria/exportar?formato=${formato}`, {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify(vista),
    })
  } catch {
    throw Object.assign(new Error('No se pudo conectar con el servicio.'), { estado: 0 })
  }
  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => null)
    controlarSesion(Object.assign(new Error(cuerpo?.mensaje || 'No se pudo exportar la vista.'), { estado: respuesta.status }))
  }
  const extension = formato === 'PDF' ? 'pdf' : 'xlsx'
  const nombre = respuesta.headers.get('content-disposition')?.match(/filename="?([^";]+)"?/i)?.[1]
    || `plataforma-analitica-${vista.pestana}-${new Date().toLocaleDateString('en-CA')}.${extension}`
  return { blob: await respuesta.blob(), nombre }
}

export function obtenerEstadoAsistente({ signal } = {}) {
  return pedir('/reporteria/asistente/estado', { signal, cache: 'no-store' }).catch(controlarSesion)
}

export async function preguntarAsistente(pregunta, filtros) {
  return pedir('/reporteria/asistente/preguntas', {
    method: 'POST',
    headers: await cabecerasProtegidas(),
    body: JSON.stringify({ pregunta, ...filtros }),
  }).catch(controlarSesion)
}
