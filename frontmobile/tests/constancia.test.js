import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizarOrdenConstancia, ordenDeOperacion, pasarelaConstancia, recuperarPagoConstancia, seleccionarPagoConstancia } from '../src/utils/constancia.js'

const checkout = {
  sessionKey: 'sesion', merchantId: '456879852', purchaseNumber: '000000000123',
  checkoutUrl: 'https://static-content-qas.vnforapps.com/env/sandbox/js/checkout.js',
  callbackUrl: 'https://localhost/retorno', timeoutUrl: 'https://localhost/timeout',
}
const orden = { idTransaccion: 123, monto: '10.00', estado: 'INICIADA', sesionIniciada: true, checkout }

test('adapta la orden al checkout sin inventar importe ni operación', () => {
  const normalizada = normalizarOrdenConstancia(orden)
  const pago = pasarelaConstancia(normalizada)
  assert.equal(pago.idTransaccion, 123)
  assert.equal(pago.montoTotal, 10)
  assert.equal(pago.checkout, checkout)
})

test('rechaza una sesión de otra operación y respuestas incompletas', () => {
  assert.throws(() => pasarelaConstancia(normalizarOrdenConstancia({
    ...orden, checkout: { ...checkout, purchaseNumber: '000000000124' },
  })), /no corresponde/)
  assert.throws(() => normalizarOrdenConstancia({ ...orden, monto: 0 }))
  assert.throws(() => normalizarOrdenConstancia({ ...orden, sesionIniciada: undefined }))
  assert.throws(() => normalizarOrdenConstancia({ ...orden, estado: 'CONFIRMADA', comprobante: null }))
  assert.throws(() => ordenDeOperacion(orden, 124), /no corresponde/)
})

test('recupera solo el pago pendiente de la misma solicitud y no uno descartado', () => {
  const pago = pasarelaConstancia(normalizarOrdenConstancia(orden))
  const ordenSeleccionada = { ...orden, idSolicitud: 7 }
  assert.equal(recuperarPagoConstancia(ordenSeleccionada, 7, pago), pago)
  assert.equal(recuperarPagoConstancia(ordenSeleccionada, 8, pago), null)
  assert.equal(recuperarPagoConstancia(ordenSeleccionada, 7, { ...pago, descartado: true }), null)
  assert.equal(recuperarPagoConstancia(ordenSeleccionada, 7, { ...pago, idTransaccion: 124 }), null)
  assert.equal(recuperarPagoConstancia({ ...ordenSeleccionada, estado: 'CONFIRMADA' }, 7, pago), null)
})

test('la pantalla inicia sin solicitud ni pago y selecciona solo el pago correspondiente', () => {
  assert.equal(seleccionarPagoConstancia(null, null, undefined, null), null)
  const pago = pasarelaConstancia(normalizarOrdenConstancia(orden))
  assert.equal(seleccionarPagoConstancia({ idSolicitud: 7, pago }, null, 7, null), pago)
  assert.equal(seleccionarPagoConstancia({ idSolicitud: 7, pago }, null, 8, null), null)
})
