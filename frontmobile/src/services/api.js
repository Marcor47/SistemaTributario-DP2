import { Platform } from 'react-native'
import Constants from 'expo-constants'

function apiPorDefecto() {
  const respaldo = Platform.OS === 'android' ? 'http://10.0.2.2:8080/api' : 'http://localhost:8080/api'
  if (Platform.OS === 'web' || !__DEV__) return respaldo
  // En Expo Go por LAN, el servidor de Expo y el backend están en la misma PC.
  // Solo se detectan direcciones privadas: un túnel de Expo no publica el puerto del backend.
  const hostUri = Constants.expoConfig?.hostUri
  if (hostUri) {
    try {
      const host = new URL(`http://${hostUri}`).hostname
      if (/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(host)) {
        return `http://${host}:8080/api`
      }
    } catch { /* Sin dirección LAN válida, se usan los valores del simulador. */ }
  }
  return respaldo
}

// Una URL explícita siempre tiene prioridad (servidor remoto, puerto distinto o túnel independiente).
export const API = (process.env.EXPO_PUBLIC_API_URL?.trim() || apiPorDefecto()).replace(/\/+$/, '')

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
