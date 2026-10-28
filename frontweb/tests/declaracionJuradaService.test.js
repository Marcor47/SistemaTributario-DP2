import assert from 'node:assert/strict'
import test from 'node:test'
import { obtenerDeclaracionJurada } from '../src/services/declaracionJuradaService.js'

function respuesta(datos = {}) {
  return {
    predio: { codigoPredio: '014502-01', numeroCatastral: '150136-001-014', ubicacion: 'Av. La Marina 1450' },
    declaracionVigente: { idDeclaracion: 'DJ-2024-001450', anio: 2024, autoavaluo: 185000, esVigente: true },
    historial: [{ idDeclaracion: 'DJ-2025-001451', anio: 2025, autoavaluo: 190000, esVigente: false }],
    fechaConsulta: '2026-10-27', advertencias: [], ...datos,
  }
}

test('HU003: consulta el predio seleccionado con GET, cookie y cancelación, sin inferir vigencia por año', async (t) => {
  const controlador = new AbortController()
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    assert.equal(new URL(url).pathname, '/api/predios/014502-01/declaracion-jurada')
    assert.equal(opciones.method, 'GET')
    assert.equal(opciones.credentials, 'include')
    assert.equal(opciones.signal, controlador.signal)
    assert.equal(opciones.body, undefined)
    return new Response(JSON.stringify(respuesta()))
  })
  const datos = await obtenerDeclaracionJurada(' 014502-01 ', { signal: controlador.signal })
  assert.equal(datos.declaracionVigente.anio, 2024)
  assert.equal(datos.historial[0].anio, 2025)
  assert.equal(datos.historial[0].esVigente, false)
})

test('HU003: no consulta códigos vacíos ni de más de 12 caracteres', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', () => { throw new Error('No debe consultar') })
  for (const codigo of ['', '   ', null, '1234567890123']) {
    await assert.rejects(obtenerDeclaracionJurada(codigo), { codigo: 'CODIGO_PREDIO_INVALIDO', estado: 400 })
  }
  assert.equal(fetch.mock.callCount(), 0)
})

test('HU003: codifica el código predial como un único segmento de la ruta', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    assert.equal(new URL(url).pathname, '/api/predios/P%2F01%3F/declaracion-jurada')
    return new Response(JSON.stringify(respuesta({ predio: { codigoPredio: 'P/01?' } })))
  })
  await obtenerDeclaracionJurada('P/01?')
})

test('HU003: conserva las advertencias, la ausencia de vigente y el historial sin promover una DJ histórica', async (t) => {
  const aviso = { codigo: 'SIN_DECLARACION_VIGENTE', mensaje: 'SAMNET no reporta una declaración vigente.' }
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify(respuesta({
    declaracionVigente: null, advertencias: [aviso],
  }))))
  const datos = await obtenerDeclaracionJurada('014502-01')
  assert.equal(datos.declaracionVigente, null)
  assert.equal(datos.historial.length, 1)
  assert.deepEqual(datos.advertencias, [aviso])
})

test('HU003: diferencia un predio sin declaraciones de un predio inexistente', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify(respuesta({
    declaracionVigente: null, historial: [],
    advertencias: [{ codigo: 'SIN_DECLARACIONES', mensaje: 'El predio no tiene declaraciones.' }],
  }))))
  const datos = await obtenerDeclaracionJurada('014502-01')
  assert.equal(datos.declaracionVigente, null)
  assert.deepEqual(datos.historial, [])
  assert.equal(datos.advertencias[0].codigo, 'SIN_DECLARACIONES')

  globalThis.fetch = async () => new Response(JSON.stringify({
    codigo: 'PREDIO_NO_ENCONTRADO', mensaje: 'El predio no existe en SAMNET.', detalles: [],
  }), { status: 404 })
  await assert.rejects(obtenerDeclaracionJurada('999999-99'), { codigo: 'PREDIO_NO_ENCONTRADO', estado: 404 })
})

test('HU003: propaga las inconsistencias de vigencia y la indisponibilidad de SAMNET para permitir reintentar', async (t) => {
  const respuestas = [
    [409, 'DECLARACION_VIGENTE_AMBIGUA'], [503, 'SAMNET_NO_DISPONIBLE'], [500, 'ERROR_INTERNO'],
  ]
  t.mock.method(globalThis, 'fetch', async () => {
    const [estado, codigo] = respuestas.shift()
    return new Response(JSON.stringify({ codigo, mensaje: codigo, detalles: ['Detalle del servicio'] }), { status: estado })
  })
  for (const [estado, codigo] of [...respuestas]) {
    await assert.rejects(obtenerDeclaracionJurada('014502-01'), {
      estado, codigo, detalles: ['Detalle del servicio'],
    })
  }
  globalThis.fetch = async () => new Response(JSON.stringify(respuesta()))
  assert.equal((await obtenerDeclaracionJurada('014502-01')).declaracionVigente.idDeclaracion, 'DJ-2024-001450')
})

test('HU003: una caída de conexión muestra un error y no devuelve datos ficticios', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('Failed to fetch') })
  await assert.rejects(obtenerDeclaracionJurada('014502-01'), { codigo: 'SIN_CONEXION', estado: 0 })
})

test('HU003: rechaza una respuesta incompleta o perteneciente a otro predio', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify(respuesta({ predio: { codigoPredio: '021390-01' } }))))
  await assert.rejects(obtenerDeclaracionJurada('014502-01'), { codigo: 'RESPUESTA_INVALIDA' })
  globalThis.fetch = async () => new Response('null')
  await assert.rejects(obtenerDeclaracionJurada('014502-01'), { codigo: 'RESPUESTA_INVALIDA' })
  globalThis.fetch = async () => new Response(JSON.stringify({ predio: { codigoPredio: '014502-01' } }))
  await assert.rejects(obtenerDeclaracionJurada('014502-01'), { codigo: 'RESPUESTA_INVALIDA' })
})
