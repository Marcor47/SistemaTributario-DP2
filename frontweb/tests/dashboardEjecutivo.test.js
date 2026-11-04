import assert from 'node:assert/strict'
import test from 'node:test'
import { obtenerDashboard, obtenerFiltrosDashboard } from '../src/services/dashboardService.js'

test('HU011: envía las cuatro dimensiones al backend sin calcular importes en el cliente', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(new URL(url)); return new Response(JSON.stringify({ resumen: { montoRecaudado: 147.25 } }))
  })
  const datos = await obtenerDashboard({ anio: 2026, numeroPeriodo: 10, zona: 'Z01', idSegmento: 7, tributo: 'IMPUESTO_PREDIAL' })
  assert.equal(datos.resumen.montoRecaudado, 147.25)
  assert.deepEqual(Object.fromEntries(urls[0].searchParams), { anio: '2026', numeroPeriodo: '10', zona: 'Z01', idSegmento: '7', tributo: 'IMPUESTO_PREDIAL' })
  await obtenerDashboard({ zona: '', tributo: '' })
  assert.equal(urls[1].search, '')
  await obtenerFiltrosDashboard()
  assert.equal(urls[2].pathname, '/api/dashboard/filtros')
})
