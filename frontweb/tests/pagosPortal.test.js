import assert from 'node:assert/strict'
import test from 'node:test'
import { iniciarPagoPortal, consultarPagoPortal, confirmarPagoYRecuperarResultado } from '../src/services/portalCuentaService.js'
import { idPagoValido, mensajeErrorPago, normalizarPago, rutaPasarelaSimulada } from '../src/utils/pagosPortal.js'

const orden = {
  idTransaccion: 42, estado: 'INICIADA', montoBase: 300, montoDescuento: 25, montoTotal: 275,
  obligaciones: ['101', '102'], urlPasarela: '/pasarela-simulada/SIM-00000000-0000-0000-0000-000000000042',
  comprobante: null, dePrueba: true,
}
const confirmada = { ...orden, estado: 'CONFIRMADA', urlPasarela: null, comprobante: 'WEB-42' }
const json = (datos, status = 200) => new Response(JSON.stringify(datos), { status })

test('HU022: el cliente crea el pago total con body vacío y cookie, sin seleccionar obligaciones ni montos', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    llamadas.push({ url, opciones })
    return json(orden, 201)
  })
  const resultado = await iniciarPagoPortal([101], 100)
  assert.equal(resultado.montoTotal, 275)
  assert.equal(llamadas.length, 1)
  assert.equal(new URL(llamadas[0].url).pathname, '/api/portal/pagos')
  assert.equal(llamadas[0].opciones.credentials, 'include')
  assert.equal(llamadas[0].opciones.method, 'POST')
  assert.deepEqual(JSON.parse(llamadas[0].opciones.body), {})
})

test('HU022: consultar la orden permite recuperar el resultado sin confirmar ni enviar un body', async (t) => {
  const controlador = new AbortController()
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    assert.equal(new URL(url).pathname, '/api/portal/pagos/42')
    assert.equal(opciones.method, undefined)
    assert.equal(opciones.body, undefined)
    assert.equal(opciones.credentials, 'include')
    assert.equal(opciones.signal, controlador.signal)
    return json(confirmada)
  })
  assert.equal((await consultarPagoPortal(42, { signal: controlador.signal })).comprobante, 'WEB-42')
})

test('HU022: una URL solo abre la pasarela local si la orden es de prueba y la ruta es válida', () => {
  assert.equal(rutaPasarelaSimulada(orden), `${orden.urlPasarela}?transaccion=42`)
  for (const urlPasarela of ['https://otro.example/pagar', '//otro.example/pagar', '/login', `${orden.urlPasarela}?importe=1`, 'javascript:alert(1)']) {
    assert.throws(() => rutaPasarelaSimulada({ ...orden, urlPasarela }), { codigo: 'PASARELA_SIMULADA_INVALIDA' })
  }
  assert.throws(() => rutaPasarelaSimulada({ ...orden, dePrueba: false }), { codigo: 'PASARELA_SIMULADA_INVALIDA' })
  assert.throws(() => rutaPasarelaSimulada(confirmada), { codigo: 'PASARELA_SIMULADA_INVALIDA' })
})

test('HU022: conserva los importes del servidor y distingue descuento cero de un importe ausente', () => {
  assert.equal(normalizarPago({ ...orden, montoDescuento: 0, montoTotal: 300 }).montoDescuento, 0)
  assert.equal(normalizarPago({ ...orden, montoTotal: '275.00' }).montoTotal, 275)
  for (const cambio of [{ montoTotal: null }, { montoBase: '' }, { montoDescuento: -1 }, { montoTotal: 1 }, { estado: 'inventado' }, { estado: 'CONFIRMADA', comprobante: null }]) {
    assert.throws(() => normalizarPago({ ...orden, ...cambio }), { codigo: 'RESPUESTA_PAGO_INVALIDA' })
  }
})

test('HU022: no consulta referencias inválidas ni acepta la respuesta de otra operación', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', async () => json({ ...orden, idTransaccion: 43 }))
  for (const id of ['', null, 0, -1, 1.5, '../42', '42?x=1']) {
    assert.equal(idPagoValido(id), false)
    await assert.rejects(consultarPagoPortal(id), { codigo: 'PAGO_NO_ENCONTRADO' })
  }
  assert.equal(fetch.mock.callCount(), 0)
  await assert.rejects(consultarPagoPortal(42), { codigo: 'RESPUESTA_PAGO_INVALIDA' })
})

test('HU022: la confirmación explícita usa solo el ID de la misma orden y espera el resultado del servidor', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    assert.equal(new URL(url).pathname, '/api/portal/pagos/42/confirmacion')
    assert.equal(opciones.method, 'POST')
    assert.equal(opciones.body, undefined)
    return json(confirmada)
  })
  const resultado = await confirmarPagoYRecuperarResultado(42)
  assert.equal(resultado.pago.estado, 'CONFIRMADA')
  assert.equal(resultado.error, null)
  assert.equal(fetch.mock.callCount(), 1)
})

test('HU022: si se pierde la respuesta de confirmación, recupera el comprobante mediante un único GET', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    llamadas.push({ url, opciones })
    if (opciones.method === 'POST') throw new TypeError('Se perdió la respuesta después de registrar')
    return json(confirmada)
  })
  const resultado = await confirmarPagoYRecuperarResultado(42)
  assert.equal(resultado.pago.comprobante, 'WEB-42')
  assert.equal(resultado.error, null)
  assert.deepEqual(llamadas.map((llamada) => new URL(llamada.url).pathname), ['/api/portal/pagos/42/confirmacion', '/api/portal/pagos/42'])
  assert.equal(llamadas.filter((llamada) => llamada.opciones.method === 'POST').length, 1)
})

test('HU022: una orden que sigue iniciada tras un fallo no se convierte en un pago confirmado', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url, opciones) => opciones.method === 'POST'
    ? json({ codigo: 'ERROR_INTERNO', mensaje: 'Error al verificar' }, 500) : json(orden))
  const resultado = await confirmarPagoYRecuperarResultado(42)
  assert.equal(resultado.pago.estado, 'INICIADA')
  assert.equal(resultado.error.codigo, 'ERROR_INTERNO')
})

test('HU022: una pérdida de conexión sin resultado comprobable exige consultar la misma operación', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('Sin conexión') })
  await assert.rejects(confirmarPagoYRecuperarResultado(42), { codigo: 'RESULTADO_DESCONOCIDO' })
  assert.equal(fetch.mock.callCount(), 2)
  assert.equal(mensajeErrorPago({ codigo: 'RESULTADO_DESCONOCIDO' }).accion, 'consultar')
})

test('HU022: saldo cambiado, beneficio indeterminado y sesión vencida no producen reintentos automáticos', async (t) => {
  const errores = [[409, 'SALDO_CAMBIO'], [422, 'BENEFICIO_INDETERMINADO'], [422, 'SIN_COBRO_VIRTUAL'], [401, 'SESION_REQUERIDA']]
  const fetch = t.mock.method(globalThis, 'fetch', async () => {
    const [estado, codigo] = errores.shift()
    return json({ codigo, mensaje: codigo }, estado)
  })
  for (const [estado, codigo] of [...errores]) {
    await assert.rejects(confirmarPagoYRecuperarResultado(42), { estado, codigo })
  }
  assert.equal(fetch.mock.callCount(), 4)
  assert.equal(mensajeErrorPago({ codigo: 'SALDO_CAMBIO' }).accion, 'cuenta')
  assert.equal(mensajeErrorPago({ codigo: 'BENEFICIO_INDETERMINADO' }).accion, 'ventanilla')
  assert.equal(mensajeErrorPago({ codigo: 'SIN_COBRO_VIRTUAL' }).accion, 'ventanilla')
})

test('HU022: una sesión caducada durante la recuperación requiere autenticación', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    if (opciones.method === 'POST') throw new TypeError('Sin respuesta')
    return json({ codigo: 'SESION_REQUERIDA' }, 401)
  })
  await assert.rejects(confirmarPagoYRecuperarResultado(42), { codigo: 'SESION_REQUERIDA', estado: 401 })
})
