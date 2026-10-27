import assert from 'node:assert/strict'
import test from 'node:test'
import { obtenerMorosidad } from '../src/services/morosidadService.js'
import { normalizarMorosidad, etiquetaTributo, porcentaje } from '../src/utils/morosidad.js'
import { DATOS_DEMO } from '../src/mocks/morosidad.js'

const respuesta = { idLote: 8, fechaCorte: '2026-10-19', anio: 2026, numeroPeriodo: 10, periodo: '2026-10', total: 46.6667,
  porTributo: [{ valorAlcance: 'IMPUESTO_PREDIAL', indice: 50 }], porZona: [{ valorAlcance: 'Z01', indice: 0 }] }

test('HU008: consulta última carga sin limitar al año actual y envía filtros cuando se solicitan', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => { urls.push(url); return new Response(JSON.stringify(respuesta)) })
  await obtenerMorosidad()
  const datos = await obtenerMorosidad(2026, 10)
  assert.equal(new URL(urls[0]).search, '')
  assert.equal(new URL(urls[1]).searchParams.get('anio'), '2026')
  assert.equal(new URL(urls[1]).searchParams.get('numeroPeriodo'), '10')
  assert.equal(datos.total, 46.6667)
  assert.equal(datos.porZona[0].indice, 0)
})

test('HU008: ausencia de cálculo es estado vacío; otros errores se conservan', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ codigo: 'MOROSIDAD_NO_CALCULADA' }), { status: 404 }))
  assert.equal(await obtenerMorosidad(), null)
  globalThis.fetch = async () => new Response(JSON.stringify({ codigo: 'ERROR_INTERNO', mensaje: 'Servicio no disponible' }), { status: 500 })
  await assert.rejects(obtenerMorosidad(), { estado: 500 })
})

test('HU008: valores ausentes no se convierten en un índice cero ni en montos inventados', () => {
  const datos = normalizarMorosidad({ ...respuesta, total: null })
  assert.equal(datos.total, null)
  assert.equal(datos.montoEmitidoTotal, null)
  assert.equal(datos.porTributo[0].montoEmitido, null)
  assert.equal(etiquetaTributo('IMPUESTO_PREDIAL'), 'Impuesto Predial')
  assert.equal(porcentaje(null), 'No disponible')
})

test('HU008: el detalle de cálculo conserva la muestra de control 420 / 900', () => {
  const datos = normalizarMorosidad({ ...respuesta, total: { montoEmitido: '900', montoVencidoNoPagado: '420', indice: '46.6667' } })
  assert.equal(datos.montoEmitidoTotal, 900)
  assert.equal(datos.montoVencidoTotal, 420)
  assert.equal(datos.total, 46.6667)
})

test('HU008: ambos desgloses de demostración concilian con los montos institucionales', () => {
  for (const filas of [DATOS_DEMO.porTributo, DATOS_DEMO.porZona]) {
    assert.equal(filas.reduce((total, fila) => total + fila.montoEmitido, 0), DATOS_DEMO.montoEmitidoTotal)
    assert.equal(filas.reduce((total, fila) => total + fila.montoVencido, 0), DATOS_DEMO.montoVencidoTotal)
  }
})
