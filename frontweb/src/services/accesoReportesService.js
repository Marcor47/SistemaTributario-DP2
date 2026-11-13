import {
  clonarConfiguracion,
  configuracionPorDefecto,
  normalizarConfiguracion,
} from '../utils/accesoReportes.js'

/*
 * HU045 (RF-060): acceso y alcance de la información en reportes.
 *
 * El backend de esta historia todavía no está implementado. Este servicio mantiene el mismo
 * contrato que tendrá la API y guarda la configuración en el navegador para la demostración.
 * Cuando se publique el backend, basta con reemplazar los cuerpos de las funciones por:
 *
 *   GET /api/configuracion/reportes-acceso
 *   PUT /api/configuracion/reportes-acceso  { "roles": { ... } }
 *
 * y eliminar el almacenamiento local. La aplicación de las restricciones al visualizar y
 * exportar los reportes también corresponde al backend.
 */
export const ACCESOS_REPORTES_SIMULADOS = true

const CLAVE_ALMACEN = 'hu045.acceso-reportes.v1'

let memoria = null

function almacenDisponible() {
  try {
    return typeof globalThis.localStorage !== 'undefined' ? globalThis.localStorage : null
  } catch {
    return null
  }
}

function leerEstado(rolesConAcceso) {
  const almacen = almacenDisponible()
  if (!almacen) {
    return clonarConfiguracion(memoria ?? configuracionPorDefecto(rolesConAcceso))
  }
  try {
    const crudo = almacen.getItem(CLAVE_ALMACEN)
    return crudo
      ? normalizarConfiguracion(JSON.parse(crudo), rolesConAcceso)
      : configuracionPorDefecto(rolesConAcceso)
  } catch {
    return configuracionPorDefecto(rolesConAcceso)
  }
}

function guardarEstado(estado) {
  memoria = clonarConfiguracion(estado)
  const almacen = almacenDisponible()
  if (!almacen) return
  try {
    almacen.setItem(CLAVE_ALMACEN, JSON.stringify(estado))
  } catch {
    // El fallback en memoria mantiene la configuración durante la sesión.
  }
}

export async function obtenerConfiguracionAccesoReportes({ rolesConAcceso } = {}) {
  return leerEstado(rolesConAcceso)
}

export async function guardarConfiguracionAccesoReportes(
  configuracion,
  { usuario, rolesConAcceso } = {},
) {
  const normalizada = normalizarConfiguracion(configuracion, rolesConAcceso)
  normalizada.actualizadoEn = new Date().toISOString()
  normalizada.actualizadoPor = String(usuario ?? '').trim() || 'Administrador'
  guardarEstado(normalizada)
  return clonarConfiguracion(normalizada)
}

/** Restablece la configuración de este navegador a los valores iniciales. */
export function reiniciarConfiguracionAccesoReportes() {
  memoria = null
  const almacen = almacenDisponible()
  if (!almacen) return
  try {
    almacen.removeItem(CLAVE_ALMACEN)
  } catch {
    // Sin almacenamiento disponible, el estado en memoria ya quedó restablecido.
  }
}
