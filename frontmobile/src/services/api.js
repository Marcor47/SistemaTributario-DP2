import { Platform } from 'react-native'

// En el emulador de Android, "localhost" es el propio emulador; 10.0.2.2 apunta a la PC.
// En un teléfono físico define EXPO_PUBLIC_API_URL con la IP de la PC (ver .env.example).
const API_POR_DEFECTO = Platform.OS === 'android' ? 'http://10.0.2.2:8080/api' : 'http://localhost:8080/api'
export const API = process.env.EXPO_PUBLIC_API_URL || API_POR_DEFECTO

const SIN_CONEXION = 'No se pudo conectar con el servicio. Verifica tu conexión e intenta nuevamente.'

let alExpirarSesion = null

/** La sesión registra aquí qué hacer cuando el backend responde 401 (sesión vencida o inexistente). */
export function registrarExpiracionSesion(fn) {
  alExpirarSesion = fn
}

// Contrato único de error del backend: { codigo, mensaje, detalles }.
// La sesión viaja en la cookie que React Native guarda tras el login; en web se envía con credentials.
export async function pedir(ruta, opciones = {}) {
  const { avisarExpiracion = true, ...init } = opciones
  const esFormulario = typeof FormData !== 'undefined' && init.body instanceof FormData
  let respuesta
  try {
    respuesta = await fetch(`${API}${ruta}`, {
      credentials: 'include',
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body && !esFormulario ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
  } catch {
    throw Object.assign(new Error(SIN_CONEXION), { codigo: 'SIN_CONEXION', estado: 0, detalles: [] })
  }

  let cuerpo = null
  try {
    cuerpo = await respuesta.json()
  } catch {
    cuerpo = null
  }

  if (!respuesta.ok) {
    if (respuesta.status === 401 && avisarExpiracion) alExpirarSesion?.()
    const mensaje = cuerpo?.mensaje || 'Ocurrió un error inesperado. Intenta nuevamente.'
    throw Object.assign(new Error(mensaje), {
      codigo: cuerpo?.codigo || 'ERROR_INTERNO',
      estado: respuesta.status,
      detalles: cuerpo?.detalles || [],
    })
  }

  return cuerpo
}
