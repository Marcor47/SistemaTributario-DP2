import { pedir } from './api.js'

/*
 * HU045 (RF-060): restricciones de acceso a los reportes por rol, aplicadas por el backend al consultar y exportar.
 *   GET    /api/admin/restricciones-reportes            -> [{ id, rol, tipoReporte, dimension, valor, permitido }]
 *   GET    /api/admin/restricciones-reportes/opciones   -> { zonas, segmentos, roles, reportes, dimensiones }
 *   POST   /api/admin/restricciones-reportes            { rol, tipoReporte, dimension, valor }
 *   PUT    /api/admin/restricciones-reportes            { roles: { ROL: [{ tipoReporte, dimension, valor }] } }
 *   DELETE /api/admin/restricciones-reportes/{id}
 */
const RUTA = '/admin/restricciones-reportes'

export function listarRestriccionesReportes(opciones = {}) {
  return pedir(RUTA, opciones)
}

export function obtenerOpcionesRestricciones(opciones = {}) {
  return pedir(`${RUTA}/opciones`, opciones)
}

export function crearRestriccionReporte({ rol, tipoReporte, dimension, valor }) {
  return pedir(RUTA, {
    method: 'POST',
    body: JSON.stringify({
      rol,
      tipoReporte,
      dimension,
      valor: dimension === 'REPORTE' ? null : valor,
    }),
  })
}

export function guardarRestriccionesReportes(roles) {
  return pedir(RUTA, {
    method: 'PUT',
    body: JSON.stringify({ roles }),
  })
}

export function eliminarRestriccionReporte(id) {
  return pedir(`${RUTA}/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
