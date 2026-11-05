import assert from 'node:assert/strict'
import test from 'node:test'
import { obtenerEstadoCuentaPortal, iniciarPagoPortal, confirmarPagoPortal } from '../src/services/portalCuentaService.js'
import { agruparCuentaPorPeriodo, filtrarCuenta, filtrarPeriodos, normalizarCuenta, resumirCuenta } from '../src/utils/estadoCuentaPortal.js'

const respuesta = {
  contribuyente: { codigo: '014502', nombre: 'Contribuyente de prueba', tipoDoc: 'DNI', doc: '12345678' },
  fechaCorte: '2026-10-19', desactualizado: false,
  filas: [
    { id: '1', tributo: 'Impuesto Predial', periodo: '2026-T1', emitido: 400, pagado: 150, deuda: 250, estado: 'Vencida' },
    { id: '2', tributo: 'Arbitrios Municipales', periodo: '2026-11', emitido: 100, pagado: 0, deuda: 100, estado: 'Pendiente' },
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
  assert.deepEqual(resumirCuenta(normalizarCuenta(respuesta).filas), { emitido: 900, pagado: 550, deuda: 350, vencido: 250, vigente: 100, interes: 0 })
})

test('HU014: filtros combinados y pagos aplicados incluyen obligaciones parcialmente pagadas', () => {
  const filas = normalizarCuenta(respuesta).filas
  assert.deepEqual(filtrarCuenta(filas, { vista: 'pagos' }).map((fila) => fila.id), ['1', '3'])
  assert.deepEqual(filtrarCuenta(filas, { anio: 2026, tributo: 'Impuesto Predial', estado: 'Vencida', texto: 'predial' }).map((fila) => fila.id), ['1'])
  assert.deepEqual(filtrarCuenta(filas, { texto: 'arbitrios' }).map((fila) => fila.id), ['2'])
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
  assert.deepEqual(resumirCuenta([]), { emitido: 0, pagado: 0, deuda: 0, vencido: 0, vigente: 0, interes: 0 })
})

// Auditoría 2026-11: el predial va por cuota y los arbitrios por mes; un mes de arbitrios suma todos los predios.
function filaArbitrio(id, codigoPredio, mes, emitido, pagado, servicios) {
  return { id, tributo: 'Arbitrios Municipales', tributoCodigo: 'ARBITRIOS', anio: 2026, cuota: mes,
    periodo: `2026-${String(mes).padStart(2, '0')}`, codigoPredio, emitido, pagado, deuda: emitido - pagado,
    interesMoratorio: emitido > pagado ? 1.5 : 0, estado: emitido > pagado ? 'VENCIDA' : 'PAGADA',
    fechaVencimiento: '2026-02-27', servicios }
}

test('HU014: una fila por cuota de predial y una por mes de arbitrios con sus predios y servicios', () => {
  const servicios = [{ servicio: 'BARRIDO_CALLES', monto: 10 }, { servicio: 'SERENAZGO', monto: 15 }]
  const filas = normalizarCuenta({ fechaCorte: '2026-10-19', filas: [
    { id: 1, tributo: 'Impuesto Predial', tributoCodigo: 'IMPUESTO_PREDIAL', anio: 2026, cuota: 1, periodo: '2026-T1',
      emitido: 342, pagado: 342, deuda: 0, estado: 'PAGADA', fechaVencimiento: '2026-02-27', servicios: [] },
    filaArbitrio(2, '014502-01', 1, 25, 25, servicios),
    filaArbitrio(3, '014502-02', 1, 25, 0, servicios),
    filaArbitrio(4, '014502-01', 2, 25, 25, servicios),
  ] }).filas
  const grupos = agruparCuentaPorPeriodo(filas)
  assert.deepEqual(grupos.map((grupo) => grupo.etiqueta), ['1.ª cuota 2026', 'Enero 2026', 'Febrero 2026'])
  assert.equal(grupos[0].titulo, 'Impuesto Predial')
  assert.equal(grupos[0].tieneDetalle, false)
  const enero = grupos[1]
  assert.equal(enero.titulo, 'Arbitrios municipales')
  assert.equal(enero.conceptos.length, 2)
  assert.equal(enero.emitido, 50)
  assert.equal(enero.deuda, 25)
  assert.equal(enero.interes, 1.5)
  assert.equal(enero.estado, 'Vencida')
  assert.deepEqual(enero.idsPendientes, ['3'])
  assert.deepEqual(enero.porServicio.map((s) => [s.codigo, s.monto]), [['BARRIDO_CALLES', 20], ['SERENAZGO', 30]])
  for (const campo of ['emitido', 'pagado', 'deuda']) {
    assert.equal(resumirCuenta(grupos)[campo], resumirCuenta(filas)[campo])
  }
  assert.equal(filtrarPeriodos(grupos, { texto: 'enero' }).length, 1)
  assert.equal(filtrarPeriodos(grupos, { texto: '014502-02' }).length, 1)
  assert.equal(filtrarPeriodos(grupos, { estado: 'Pagada' }).length, 2)
})

test('HU014: otros conceptos no se mezclan con los tributos ni se inventa un vencimiento único', () => {
  const filas = normalizarCuenta({ fechaCorte: '2026-10-19', filas: [
    { id: 1, tributo: 'Impuesto Predial', tributoCodigo: 'IMPUESTO_PREDIAL', anio: 2026, cuota: 1, periodo: '2026-T1', emitido: 10, pagado: 5, deuda: 5, estado: 'VENCIDA', fechaVencimiento: '2026-02-27' },
    { id: 2, tributo: 'Arbitrios Municipales', tributoCodigo: 'ARBITRIOS', anio: 2026, cuota: 3, periodo: '2026-03', codigoPredio: 'A', emitido: 20, pagado: 20, deuda: 0, estado: 'PAGADA', fechaVencimiento: '2026-03-31' },
    { id: 3, tributo: 'Arbitrios Municipales', tributoCodigo: 'ARBITRIOS', anio: 2026, cuota: 3, periodo: '2026-03', codigoPredio: 'B', emitido: 20, pagado: 0, deuda: 20, estado: 'VENCIDA', fechaVencimiento: '2026-04-15' },
    { id: 4, tributo: 'Derecho de trámite', periodo: '2026-T1', emitido: 7, pagado: 0, deuda: 7, estado: 'PENDIENTE' },
  ] }).filas
  const grupos = agruparCuentaPorPeriodo(filas)
  assert.equal(grupos.length, 3)
  assert.equal(grupos[0].deuda, 5)
  assert.deepEqual(grupos[0].idsPendientes, ['1'])
  assert.equal(grupos[1].fechaVencimiento, null)
  assert.equal(grupos[2].titulo, 'Derecho de trámite')
})

test('el acceso existente a pagos propaga una caída de red sin inventar confirmaciones', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('Sin red') })
  await assert.rejects(iniciarPagoPortal([1]), { codigo: 'SIN_CONEXION' })
  await assert.rejects(confirmarPagoPortal(12), { codigo: 'SIN_CONEXION' })
})
