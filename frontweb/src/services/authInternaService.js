const API = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api'
let csrfActual = null

async function errorDe(respuesta) {
  let datos = null
  try { datos = await respuesta.json() } catch { /* respuesta sin JSON */ }
  const mensaje = datos?.mensaje || (respuesta.status === 401
    ? 'La sesión institucional no está activa.'
    : 'No se pudo completar la operación. Intenta nuevamente.')
  return Object.assign(new Error(mensaje), { estado: respuesta.status, codigo: datos?.codigo, detalles: datos?.detalles || [] })
}

async function csrf() {
  if (csrfActual) return csrfActual
  const respuesta = await fetch(`${API}/auth/interno/csrf`, { credentials: 'include' })
  if (!respuesta.ok) throw await errorDe(respuesta)
  csrfActual = await respuesta.json()
  return csrfActual
}

async function solicitar(ruta, opciones = {}, protegido = false) {
  const headers = new Headers(opciones.headers || {})
  if (protegido) {
    const token = await csrf()
    headers.set(token.headerName, token.token)
  }
  const respuesta = await fetch(`${API}${ruta}`, { ...opciones, headers, credentials: 'include' })
  if (!respuesta.ok) throw await errorDe(respuesta)
  if (respuesta.status === 204) return null
  return respuesta.json()
}

export function iniciarSesionInterna(nombreUsuario, clave) {
  return solicitar('/auth/interno/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nombreUsuario, clave }),
  })
}

export const obtenerSesionInterna = () => solicitar('/auth/interno/me')

export async function cerrarSesionInterna() {
  await solicitar('/auth/interno/logout', { method: 'POST' }, true)
  csrfActual = null
}

export function cambiarClaveInterna(claveActual, claveNueva) {
  return solicitar('/auth/interno/cambiar-clave', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ claveActual, claveNueva }),
  }, true)
}

export const listarUsuariosInternos = () => solicitar('/admin/usuarios')
export const listarRolesInternos = () => solicitar('/admin/roles')

export function crearUsuarioInterno(datos) {
  return solicitar('/admin/usuarios', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(datos),
  }, true)
}
