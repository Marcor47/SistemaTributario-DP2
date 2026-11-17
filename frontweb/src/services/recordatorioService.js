import { pedir } from './api.js'
import { validarDiasRecordatorio } from '../utils/recordatorios.js'

/*
 * HU016 (RF-022): recordatorio de vencimiento por correo.
 *   GET  /api/recordatorios-vencimiento/configuracion       -> { diasAnticipacion, minimo, maximo, valorPorDefecto, ... }
 *   PUT  /api/recordatorios-vencimiento/configuracion       { diasAnticipacion }
 *   POST /api/recordatorios-vencimiento/procesar            -> resultado del envío del día
 *   GET  /api/recordatorios-vencimiento/envios              -> detalle paginado de los envíos del día
 *   GET  /api/recordatorios-vencimiento/obligaciones/{id}   -> estado del recordatorio de una cuota
 */
function errorRecordatorio(codigo, mensaje) {
  return Object.assign(new Error(mensaje), { codigo, estado: 422, detalles: [] })
}

export function obtenerConfiguracionRecordatorio() {
  return pedir('/recordatorios-vencimiento/configuracion')
}

export async function guardarConfiguracionRecordatorio(diasAnticipacion) {
  const validacion = validarDiasRecordatorio(diasAnticipacion)
  if (!validacion.valido) throw errorRecordatorio('DIAS_RECORDATORIO_INVALIDO', validacion.mensaje)
  return pedir('/recordatorios-vencimiento/configuracion', {
    method: 'PUT',
    body: JSON.stringify({ diasAnticipacion: validacion.valor }),
  })
}

export function procesarRecordatorios() {
  return pedir('/recordatorios-vencimiento/procesar', { method: 'POST' })
}

/*
 * Detalle paginado de los recordatorios procesados en el día: enviados, registrados y fallidos.
 * `pagina` es 0-based en el servidor; la pantalla trabaja en 1-based.
 */
export function listarEnviosRecordatorios({ fecha, pagina = 0, tamanio = 10, signal } = {}) {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamanio: String(tamanio) })
  if (fecha) parametros.set('fecha', fecha)
  return pedir(`/recordatorios-vencimiento/envios?${parametros}`, { signal })
}

export async function obtenerRecordatorioObligacion(obligacion) {
  const datos = obligacion && typeof obligacion === 'object' ? obligacion : { id: obligacion }
  const id = String(datos.id ?? '').trim()
  if (!/^\d+$/.test(id)) {
    throw errorRecordatorio('OBLIGACION_INVALIDA', 'No se identificó la obligación para consultar su recordatorio.')
  }
  return pedir(`/recordatorios-vencimiento/obligaciones/${id}`)
}
