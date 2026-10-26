import assert from 'node:assert/strict'
import test from 'node:test'
import { obtenerEstadoCuentaPortal, iniciarPagoPortal, confirmarPagoPortal } from '../src/services/portalCuentaService.js'
import { filtrarCuenta, normalizarCuenta, resumirCuenta } from '../src/utils/estadoCuentaPortal.js'

const respuesta = {
  contribuyente: { codigo: '014502', nombre: 'Contribuyente de prueba', tipoDoc: 'DNI', doc: '12345678' },
  fechaCorte: '2026-10-19', desactualizado: false,
  filas: [
    { id: '1', tributo: 'Impuesto Predial', periodo: '2026-T1', emitido: 400, pagado: 150, deuda: 250, estado: 'Vencida' },
    { id: '2', tributo: 'Limpieza Pública', periodo: '2026-11', emitido: 100, pagado: 0, deuda: 100, estado: 'Pendiente' },
    { id: '3', tributo: 'Impuesto Predial', periodo: '2025-T4', emitido: 400, pagado: 400, deuda: 0, estado: 'Pagada' },
  ],
}

test('HU014: consulta exclusivamente la ruta propia con cookie, sin enviar un código', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    llamadas.push({ url, opciones })
    return new Response(JSON.stringify(respuesta), { status: 200 })
  })
  const cuenta = await obtenerEstadoCuentaPortal()
  assert.equal(cuenta.contribuyente.codigo, '014502')
  assert.equal(llamadas.length, 1)
  assert.ok(llamadas[0].url.endsWith('/portal/estado-cuenta'))
  assert.equal(llamadas[0].opciones.credentials, 'include')
  assert.equal(llamadas[0].opciones.body, undefined)
})

test('HU014: propaga la sesión caducada y los errores sin sustituir datos ficticios', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ codigo: 'SESION_REQUERIDA', mensaje: 'Inicie sesión.' }), { status: 401 }))
  await assert.rejects(obtenerEstadoCuentaPortal(), { estado: 401, codigo: 'SESION_REQUERIDA' })
  globalThis.fetch = async () => { throw new Error('Sin red') }
  await assert.rejects(obtenerEstadoCuentaPortal(), { codigo: 'SIN_CONEXION', estado: 0 })
})

test('HU014: conserva fecha de corte y copia desactualizada recibidas del servidor', () => {
  const cuenta = normalizarCuenta({ ...respuesta, fechaCorte: '2026-10-16', desactualizado: true })
  assert.equal(cuenta.fechaCorte, '2026-10-16')
  assert.equal(cuenta.desactualizado, true)
  assert.deepEqual(cuenta.contribuyente, respuesta.contribuyente)
})

test('HU014: suma pagos parciales y distingue deuda vencida de obligaciones vigentes', () => {
  assert.deepEqual(resumirCuenta(normalizarCuenta(respuesta).filas), { emitido: 900, pagado: 550, deuda: 350, vencido: 250, vigente: 100 })
})

test('HU014: filtros combinados y pagos aplicados incluyen obligaciones parcialmente pagadas', () => {
  const filas = normalizarCuenta(respuesta).filas
  assert.deepEqual(filtrarCuenta(filas, { vista: 'pagos' }).map((fila) => fila.id), ['1', '3'])
  assert.deepEqual(filtrarCuenta(filas, { anio: 2026, tributo: 'Impuesto Predial', estado: 'Vencida', texto: 'predial' }).map((fila) => fila.id), ['1'])
  assert.deepEqual(filtrarCuenta(filas, { texto: 'publica' }).map((fila) => fila.id), ['2'])
  assert.deepEqual(filtrarCuenta(filas, { anio: 2024 }), [])
})

test('HU014: no inventa año, vencimiento, recibo ni fecha de pago cuando faltan', () => {
  const cuenta = normalizarCuenta({ ...respuesta, filas: [{ id: 9, tributo: 'Predial', periodo: '', emitido: '100.50', pagado: '40.25' }] })
  assert.equal(cuenta.filas[0].anio, null)
  assert.equal(cuenta.filas[0].deuda, 60.25)
  assert.equal(cuenta.filas[0].fechaVencimiento, undefined)
  assert.equal(cuenta.filas[0].fechaPago, undefined)
})

test('HU014: cuenta sin obligaciones tiene todos los totales en cero', () => {
  assert.deepEqual(resumirCuenta([]), { emitido: 0, pagado: 0, deuda: 0, vencido: 0, vigente: 0 })
})

test('el acceso existente a pagos propaga una caída de red sin inventar confirmaciones', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('Sin red') })
  await assert.rejects(iniciarPagoPortal([1]), { codigo: 'SIN_CONEXION' })
  await assert.rejects(confirmarPagoPortal(12), { codigo: 'SIN_CONEXION' })
})
