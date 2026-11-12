import { checkoutValido, idPagoValido, validarPasarela } from './pagos.js'

const ESTADOS = new Set(['INICIADA', 'CONFIRMADA', 'RECHAZADA', 'EXPIRADA'])

export function normalizarOrdenConstancia(datos) {
  if (!datos || !idPagoValido(datos.idTransaccion) || !ESTADOS.has(datos.estado)
    || datos.monto == null || !Number.isFinite(Number(datos.monto)) || Number(datos.monto) <= 0
    || typeof datos.sesionIniciada !== 'boolean'
    || (datos.estado === 'CONFIRMADA' && !datos.comprobante)) {
    throw Object.assign(new Error('No se pudo comprobar la orden de pago.'), { codigo: 'RESPUESTA_PAGO_INVALIDA' })
  }
  return { ...datos, monto: Number(datos.monto) }
}

export function pasarelaConstancia(orden) {
  const pago = validarPasarela({
    idTransaccion: orden.idTransaccion,
    estado: orden.estado,
    montoTotal: orden.monto,
    dePrueba: true,
    urlPasarela: orden.urlPasarela,
    checkout: orden.checkout,
  })
  if (checkoutValido(pago.checkout)
    && (pago.checkout.purchaseNumber.replace(/^0+/, '') || '0') !== String(orden.idTransaccion)) {
    throw Object.assign(new Error('La sesión no corresponde a esta orden.'), { codigo: 'RESPUESTA_PAGO_INVALIDA' })
  }
  return pago
}

export function ordenDeOperacion(datos, idTransaccion) {
  const orden = normalizarOrdenConstancia(datos)
  if (String(orden.idTransaccion) !== String(idTransaccion)) {
    throw Object.assign(new Error('La respuesta no corresponde a esta orden.'), { codigo: 'RESPUESTA_PAGO_INVALIDA' })
  }
  return orden
}

export function recuperarPagoConstancia(orden, idSolicitud, pago) {
  if (!orden || orden.idSolicitud !== idSolicitud || orden.estado !== 'INICIADA'
    || !pago || pago.descartado || pago.estado !== 'INICIADA'
    || String(pago.idTransaccion) !== String(orden.idTransaccion)) return null
  return pago
}

export function seleccionarPagoConstancia(pagoEnPantalla, orden, idSolicitud, ultimoPago) {
  if (idSolicitud != null && pagoEnPantalla?.idSolicitud === idSolicitud) return pagoEnPantalla.pago
  return recuperarPagoConstancia(orden, idSolicitud, ultimoPago)
}
