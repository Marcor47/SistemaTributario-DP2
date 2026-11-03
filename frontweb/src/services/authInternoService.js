import { pedir } from './api.js'

const MENSAJES = {
  400: 'Revisa los datos ingresados e intenta nuevamente.',
  401: 'El usuario o la contraseña no son correctos.',
  429: 'Demasiados intentos fallidos. Espera unos minutos antes de volver a intentar.',
}

// Devuelve { nombreUsuario, nombreCompleto, roles }. La sesión queda en la cookie del backend.
export async function iniciarSesionInterno(nombreUsuario, clave) {
  try {
    return await pedir('/auth/interno/login', {
      method: 'POST',
      body: JSON.stringify({ nombreUsuario, clave }),
    })
  } catch (err) {
    throw Object.assign(new Error(MENSAJES[err.estado] || err.message), { estado: err.estado })
  }
}

// Lanza con estado 401 cuando no hay sesión interna vigente.
export function obtenerSesionInterna() {
  return pedir('/auth/interno/me')
}
