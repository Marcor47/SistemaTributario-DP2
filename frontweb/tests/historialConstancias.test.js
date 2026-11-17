import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

test('HU020/HU042: el historial separa emitidas y rechazadas y pagina ambas tablas', () => {
  const fuente = readFileSync(new URL('../src/pages/HistorialConstancias.jsx', import.meta.url), 'utf8')

  assert.match(fuente, /PaginacionTabla/)
  assert.match(fuente, /listarConstancias\(\)/)
  assert.match(fuente, /listarSolicitudesConstancia\('RECHAZADA'\)/)
  assert.match(fuente, /Emitidas/)
  assert.match(fuente, /Rechazadas/)
  assert.match(fuente, /urlConstanciaPdf/)
})

test('HU020/HU042: las tres pestañas de constancias incluyen el historial', () => {
  const fuente = readFileSync(new URL('../src/pages/ConstanciasNoAdeudo.jsx', import.meta.url), 'utf8')

  assert.match(fuente, /titulo: 'Solicitudes por atender'/)
  assert.match(fuente, /titulo: 'Ventanilla'/)
  assert.match(fuente, /titulo: 'Historial'/)
  assert.match(fuente, /HistorialConstancias/)
})
