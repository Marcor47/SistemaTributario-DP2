const API = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api'

const MENSAJES = {
  400: 'Revisa los datos ingresados e intenta nuevamente.',
  401: 'El código de contribuyente o la contraseña no son correctos.',
  429: 'Demasiados intentos fallidos. Espera unos minutos antes de volver a intentar.',
  503: 'El acceso no está disponible en este momento. Intenta más tarde.',
}

// Modo demo sin backend: solo en desarrollo y con VITE_AUTH_DEMO=true. No se incluye en el build de producción.
export const MODO_DEMO = Boolean(import.meta.env?.DEV && import.meta.env?.VITE_AUTH_DEMO === 'true')
export const CREDENCIALES_DEMO = { codigo: 'DEMO-0001', password: 'demo1234' }

// Referencia visual de la sesión del portal. La autorización real siempre la valida el backend con la cookie.
const CLAVE_SESION_PORTAL = 'sesion_portal'

/**
 * Comprueba si hay una sesión demo activa en el browser (sessionStorage).
 * Devuelve false en Node.js (entorno de tests) donde sessionStorage no existe.
 */
export function esSesionDemo() {
  if (typeof sessionStorage === 'undefined') return false
  return Boolean(sessionStorage.getItem(CLAVE_SESION_PORTAL))
}

/**
 * Obtiene los datos de la sesión demo activa.
 */
export function obtenerSesionDemo() {
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
  if (MODO_DEMO) {
    await new Promise((resolver) => setTimeout(resolver, 600))
    if (codigoContribuyente === CREDENCIALES_DEMO.codigo && password === CREDENCIALES_DEMO.password) {
      // Registrar sesión demo para que portalCuentaService pueda ignorar el 401 del backend
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem(CLAVE_SESION_PORTAL, JSON.stringify({ codigo: codigoContribuyente }))
      }
      return { codigoContribuyente }
    }
    throw Object.assign(new Error(MENSAJES[401]), { estado: 401 })
  }
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
  if (MODO_DEMO) {
    const sesion = obtenerSesionDemo()
    if (sesion) return { codigoContribuyente: sesion.codigo }
    throw Object.assign(new Error('La sesión no está activa.'), { estado: 401 })
  }
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
  // Limpiar sesión demo independientemente del modo
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.removeItem(CLAVE_SESION_PORTAL)
  }
  if (MODO_DEMO) return
  const respuesta = await fetch(`${API}/auth/logout`, { method: 'POST', credentials: 'include' })
  if (!respuesta.ok) throw new Error('No se pudo cerrar la sesión. Intenta nuevamente.')
}
