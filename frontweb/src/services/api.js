export const API = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api'

const METODOS_PROTEGIDOS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
let tokenCsrf = null

function sinConexion() {
  return Object.assign(
    new Error('No se pudo conectar con el servicio. Verifica tu conexión e intenta nuevamente.'),
    { codigo: 'SIN_CONEXION', estado: 0, detalles: [] },
  )
}

// El backend emite el token CSRF en /auth/interno/csrf (cookie XSRF-TOKEN + valor). Se pide una vez y se reutiliza.
export async function obtenerTokenCsrf({ renovar = false } = {}) {
  if (tokenCsrf && !renovar) return tokenCsrf
  let respuesta
  try {
    respuesta = await fetch(`${API}/auth/interno/csrf`, { credentials: 'include' })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw sinConexion()
  }
  if (!respuesta.ok) {
    throw Object.assign(new Error('No se pudo preparar la operación. Intenta nuevamente.'), {
      codigo: 'CSRF_NO_DISPONIBLE', estado: respuesta.status, detalles: [],
    })
  }
  tokenCsrf = await respuesta.json()
  return tokenCsrf
}

export function olvidarTokenCsrf() {
  tokenCsrf = null
}

// Cabeceras con el token CSRF para las peticiones que modifican datos hechas con fetch directo (descargas, FormData).
export async function cabecerasCsrf(cabeceras = {}) {
  const token = await obtenerTokenCsrf()
  return { ...cabeceras, [token.headerName]: token.token }
}

// Las mismas rutas que el backend protege (SeguridadWebConfig): las del portal del contribuyente usan su propia sesión.
function requiereCsrf(ruta) {
  const camino = String(ruta).split('?')[0]
  if (camino.startsWith('/auth/') && !camino.startsWith('/auth/interno/')) return false
  return camino !== '/auth/interno/login' && !camino.startsWith('/portal/') && camino !== '/constancias'
    && !camino.startsWith('/constancias/')
}

async function enviar(ruta, opciones, renovarCsrf) {
  const metodo = String(opciones.method || 'GET').toUpperCase()
  const esFormulario = typeof FormData !== 'undefined' && opciones.body instanceof FormData
  const headers = { ...(opciones.headers ?? (esFormulario ? {} : { 'Content-Type': 'application/json' })) }
  const traeToken = Object.keys(headers).some((clave) => /csrf|xsrf/i.test(clave))
  if (METODOS_PROTEGIDOS.has(metodo) && requiereCsrf(ruta) && (renovarCsrf || !traeToken)) {
    const token = await obtenerTokenCsrf({ renovar: renovarCsrf })
    Object.keys(headers).filter((clave) => /csrf|xsrf/i.test(clave)).forEach((clave) => delete headers[clave])
    headers[token.headerName] = token.token
  }
  try {
    return await fetch(`${API}${ruta}`, { credentials: 'include', ...opciones, headers })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw sinConexion()
  }
}

function nombreDeCabecera(respuesta, porDefecto) {
  const cabecera = respuesta.headers.get('Content-Disposition') || ''
  const utf8 = cabecera.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8) return decodeURIComponent(utf8[1])
  const simple = cabecera.match(/filename="?([^";]+)"?/i)
  return simple ? simple[1] : porDefecto
}

// Descarga binaria (PDF, Word, Excel): devuelve { blob, nombre } con el tipo MIME que envía el servidor.
export async function pedirArchivo(ruta, nombrePorDefecto, opciones = {}) {
  const metodo = String(opciones.method || 'GET').toUpperCase()
  let respuesta = await enviar(ruta, opciones, false)
  if (respuesta.status === 403 && METODOS_PROTEGIDOS.has(metodo) && requiereCsrf(ruta)) respuesta = await enviar(ruta, opciones, true)
  if (!respuesta.ok) {
    let cuerpo = null
    try { cuerpo = await respuesta.json() } catch { cuerpo = null }
    throw Object.assign(new Error(cuerpo?.mensaje || 'No se pudo descargar el archivo. Intenta nuevamente.'), {
      codigo: cuerpo?.codigo || 'ERROR_DESCARGA', estado: respuesta.status, detalles: cuerpo?.detalles || [],
    })
  }
  return { blob: await respuesta.blob(), nombre: nombreDeCabecera(respuesta, nombrePorDefecto) }
}

// Guarda en el equipo un archivo recibido del servidor.
export function guardarArchivo({ blob, nombre }) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  document.body.append(enlace)
  enlace.click()
  setTimeout(() => {
    URL.revokeObjectURL(url)
    enlace.remove()
  }, 1000)
}

// Contrato único de error del backend: { codigo, mensaje, detalles }.
export async function pedir(ruta, opciones = {}) {
  const metodo = String(opciones.method || 'GET').toUpperCase()
  let respuesta = await enviar(ruta, opciones, false)
  // Un token vencido (p. ej. después de volver a iniciar sesión) se renueva una sola vez.
  if (respuesta.status === 403 && METODOS_PROTEGIDOS.has(metodo) && requiereCsrf(ruta)) {
    respuesta = await enviar(ruta, opciones, true)
  }

  let cuerpo = null
  if (respuesta.status !== 204) {
    try {
      cuerpo = await respuesta.json()
    } catch {
      cuerpo = null
    }
  }

  if (!respuesta.ok) {
    const mensaje = cuerpo?.mensaje || 'Ocurrió un error inesperado. Intenta nuevamente.'
    throw Object.assign(new Error(mensaje), cuerpo || {}, {
      codigo: cuerpo?.codigo || 'ERROR_INTERNO',
      estado: respuesta.status,
      detalles: cuerpo?.detalles || [],
    })
  }

  return cuerpo
}
