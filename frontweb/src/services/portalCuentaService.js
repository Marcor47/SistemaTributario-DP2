import { pedir } from './api.js'
import { normalizarCuenta } from '../utils/estadoCuentaPortal.js'
import { idPagoValido, normalizarPago } from '../utils/pagosPortal.js'

// La identidad real se resuelve exclusivamente mediante la cookie en el servidor.
export async function obtenerEstadoCuentaPortal({ signal } = {}) {
  return normalizarCuenta(await pedir('/portal/estado-cuenta', { signal }))
}

// El contrato actual permite consultar pagos aplicados a obligaciones, no un historial de recibos.
export async function obtenerHistorialPagosPortal(opciones) {
  const cuenta = await obtenerEstadoCuentaPortal(opciones)
  return cuenta.filas.filter((fila) => fila.pagado > 0)
}

export async function iniciarPagoPortal() {
  return normalizarPago(await pedir('/portal/pagos', { method: 'POST', body: '{}' }))
}

function referenciaPago(id) {
  if (!idPagoValido(id)) throw Object.assign(new Error('La referencia del pago no es válida.'), { codigo: 'PAGO_NO_ENCONTRADO' })
  return encodeURIComponent(id)
}

export async function consultarPagoPortal(idTransaccion, { signal } = {}) {
  referenciaPago(idTransaccion)
  const pago = normalizarPago(await pedir(`/portal/pagos/${referenciaPago(idTransaccion)}`, { signal }))
  if (String(pago.idTransaccion) !== String(idTransaccion)) {
    throw Object.assign(new Error('La respuesta no corresponde al pago solicitado.'), { codigo: 'RESPUESTA_PAGO_INVALIDA' })
  }
  return pago
}

export async function confirmarPagoPortal(idTransaccion) {
  referenciaPago(idTransaccion)
  const pago = normalizarPago(await pedir(`/portal/pagos/${referenciaPago(idTransaccion)}/confirmacion`, { method: 'POST' }))
  if (String(pago.idTransaccion) !== String(idTransaccion)) {
    throw Object.assign(new Error('La respuesta no corresponde al pago solicitado.'), { codigo: 'RESPUESTA_PAGO_INVALIDA' })
  }
  return pago
}

// Si se pierde la respuesta del POST, consulta la misma orden; nunca crea ni confirma otra automáticamente.
export async function confirmarPagoYRecuperarResultado(idTransaccion) {
  try {
    return { pago: await confirmarPagoPortal(idTransaccion), error: null }
  } catch (error) {
    if (error.estado === 401 || (error.estado >= 400 && error.estado < 500)) throw error
    try {
      const pago = await consultarPagoPortal(idTransaccion)
      return { pago, error: pago.estado === 'CONFIRMADA' ? null : error }
    } catch (consultaError) {
      if (consultaError.estado === 401) throw consultaError
      throw Object.assign(new Error('No se pudo comprobar el resultado de la confirmación.'), { codigo: 'RESULTADO_DESCONOCIDO' })
    }
  }
}

