const API = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api'

const MENSAJES = {
  400: 'Revisa los datos ingresados e intenta nuevamente.',
  401: 'El código de contribuyente o la contraseña no son correctos.',
  429: 'Demasiados intentos fallidos. Espera unos minutos antes de volver a intentar.',
  503: 'El acceso no está disponible en este momento. Intenta más tarde.',
}

// Referencia visual de la sesión del portal. La autorización real siempre la valida el backend con la cookie.
const CLAVE_SESION_PORTAL = 'sesion_portal'

/**
 * Comprueba si el navegador recuerda una sesión del portal (sessionStorage). Es solo una referencia visual:
 * el backend valida la cookie en cada consulta. Devuelve false en Node.js (tests), donde no hay sessionStorage.
 */
export function haySesionPortal() {
  if (typeof sessionStorage === 'undefined') return false
  return Boolean(sessionStorage.getItem(CLAVE_SESION_PORTAL))
}

/**
 * Datos de la sesión del portal recordada en el navegador.
 */
export function obtenerSesionPortal() {
  if (typeof sessionStorage === 'undefined') return null
  try {
    const raw = sessionStorage.getItem(CLAVE_SESION_PORTAL)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// El login crea una sesión por cookie, por eso se envían credenciales.
export async function iniciarSesion(codigoContribuyente, password) {
  let respuesta
  try {
    respuesta = await fetch(`${API}/auth/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ codigoContribuyente, password }),
    })
  } catch {
    throw Object.assign(
      new Error('No se pudo conectar con el servicio. Verifica tu conexión e intenta nuevamente.'),
      { estado: 0 },
    )
  }
  if (!respuesta.ok) {
    throw Object.assign(
      new Error(MENSAJES[respuesta.status] || 'Ocurrió un error inesperado. Intenta nuevamente.'),
      { estado: respuesta.status },
    )
  }
  const sesion = await respuesta.json()
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.setItem(CLAVE_SESION_PORTAL, JSON.stringify({ codigo: sesion.codigoContribuyente }))
  }
  return sesion
}

export async function obtenerSesionContribuyente() {
  const respuesta = await fetch(`${API}/auth/me`, { credentials: 'include' })
  if (!respuesta.ok) {
    if (typeof sessionStorage !== 'undefined') sessionStorage.removeItem(CLAVE_SESION_PORTAL)
    throw Object.assign(new Error('La sesión no está activa.'), { estado: respuesta.status })
  }
  const sesion = await respuesta.json()
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.setItem(CLAVE_SESION_PORTAL, JSON.stringify({ codigo: sesion.codigoContribuyente }))
  }
  return sesion
}

export async function cerrarSesion() {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.removeItem(CLAVE_SESION_PORTAL)
  }
  const respuesta = await fetch(`${API}/auth/logout`, { method: 'POST', credentials: 'include' })
  if (!respuesta.ok) throw new Error('No se pudo cerrar la sesión. Intenta nuevamente.')
}
