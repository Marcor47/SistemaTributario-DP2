const API = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

const MENSAJES = {
  400: 'Revisa los datos ingresados e intenta nuevamente.',
  401: 'El código de contribuyente o la contraseña no son correctos.',
  403: 'La solicitud de seguridad venció. Actualiza la página e intenta nuevamente.',
  429: 'Demasiados intentos fallidos. Espera unos minutos antes de volver a intentar.',
  503: 'El acceso no está disponible en este momento. Intenta más tarde.',
}

// Modo demo sin backend: solo en desarrollo y con VITE_AUTH_DEMO=true. No se incluye en el build de producción.
export const MODO_DEMO = import.meta.env.DEV && import.meta.env.VITE_AUTH_DEMO === 'true'
export const CREDENCIALES_DEMO = { codigo: 'DEMO-0001', password: 'demo1234' }
const SESION_DEMO = 'sesion-contribuyente-demo'
let csrfActual = null

async function leerRespuesta(respuesta) {
  if (respuesta.status === 204) return null
  try {
    return await respuesta.json()
  } catch {
    return null
  }
}

async function solicitar(ruta, opciones = {}) {
  let respuesta
  try {
    respuesta = await fetch(`${API}${ruta}`, { credentials: 'include', ...opciones })
  } catch {
    throw Object.assign(
      new Error('No se pudo conectar con el servicio. Verifica tu conexión e intenta nuevamente.'),
      { estado: 0 },
    )
  }
  const cuerpo = await leerRespuesta(respuesta)
  if (!respuesta.ok) {
    throw Object.assign(
      new Error(MENSAJES[respuesta.status] || cuerpo?.mensaje || 'Ocurrió un error inesperado. Intenta nuevamente.'),
      { estado: respuesta.status },
    )
  }
  return cuerpo
}

export async function obtenerCabecerasCsrf() {
  if (!csrfActual) csrfActual = await solicitar('/auth/csrf')
  return { [csrfActual.headerName]: csrfActual.token }
}

// El login crea una sesión por cookie, por eso se envían credenciales.
export async function iniciarSesion(codigoContribuyente, password) {
  if (MODO_DEMO) {
    await new Promise((resolver) => setTimeout(resolver, 600))
    if (codigoContribuyente === CREDENCIALES_DEMO.codigo && password === CREDENCIALES_DEMO.password) {
      sessionStorage.setItem(SESION_DEMO, codigoContribuyente)
      return { codigoContribuyente }
    }
    throw Object.assign(new Error(MENSAJES[401]), { estado: 401 })
  }
  const csrf = await obtenerCabecerasCsrf()
  const sesion = await solicitar('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...csrf },
    body: JSON.stringify({ codigoContribuyente, password }),
  })
  csrfActual = null
  return sesion
}

export async function obtenerSesion() {
  if (MODO_DEMO) {
    const codigoContribuyente = sessionStorage.getItem(SESION_DEMO)
    if (!codigoContribuyente) throw Object.assign(new Error('Debes iniciar sesión.'), { estado: 401 })
    return { codigoContribuyente }
  }
  return solicitar('/auth/me')
}

export async function cerrarSesion() {
  if (MODO_DEMO) {
    sessionStorage.removeItem(SESION_DEMO)
    return
  }
  const csrf = await obtenerCabecerasCsrf()
  await solicitar('/auth/logout', { method: 'POST', headers: csrf })
  csrfActual = null
}
