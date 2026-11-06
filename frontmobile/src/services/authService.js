import { pedir } from './api'

// Mismos mensajes que frontweb/src/services/authService.js.
const MENSAJES = {
  400: 'Revisa los datos ingresados e intenta nuevamente.',
  401: 'El código de contribuyente o la contraseña no son correctos.',
  429: 'Demasiados intentos fallidos. Espera unos minutos antes de volver a intentar.',
  503: 'El acceso no está disponible en este momento. Intenta más tarde.',
}

// Acceso ficticio sembrado por Flyway (back/README.md). Solo se muestra en desarrollo.
export const ACCESO_PRUEBA = { codigo: '021390', password: 'Portal021390!' }

export async function iniciarSesion(codigoContribuyente, password) {
  try {
    return await pedir('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ codigoContribuyente, password }),
      avisarExpiracion: false,
    })
  } catch (error) {
    if (error.estado && MENSAJES[error.estado]) error.message = MENSAJES[error.estado]
    throw error
  }
}

export function obtenerSesion() {
  return pedir('/auth/me', { avisarExpiracion: false })
}

export function cerrarSesion() {
  return pedir('/auth/logout', { method: 'POST', avisarExpiracion: false })
}
