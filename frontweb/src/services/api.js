const API = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

// Contrato único de error del backend: { codigo, mensaje, detalles }.
export async function pedir(ruta, opciones = {}) {
  let respuesta
  try {
    respuesta = await fetch(`${API}${ruta}`, {
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      ...opciones,
    })
  } catch {
    throw Object.assign(
      new Error('No se pudo conectar con el servicio. Verifica que el backend esté disponible e intenta nuevamente.'),
      { codigo: 'SIN_CONEXION', estado: 0, detalles: [] },
    )
  }

  let cuerpo
  try {
    cuerpo = await respuesta.json()
  } catch {
    cuerpo = null
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
