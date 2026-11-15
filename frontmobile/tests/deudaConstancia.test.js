import test from 'node:test'
import assert from 'node:assert/strict'
import { agruparDeuda, periodoDeuda } from '../src/utils/deudaConstancia.js'

test('agrupa los importes sin perder cuotas del mismo período de predios distintos', () => {
  const cuotas = [
    { id: 1, tributoCodigo: 'ARBITRIOS', tributo: 'Arbitrios Municipales', periodo: '2026-06', deuda: '22.26' },
    { id: 2, tributoCodigo: 'IMPUESTO_PREDIAL', tributo: 'Impuesto Predial', periodo: '2026-T2', deuda: '75.00' },
    { id: 3, tributoCodigo: 'ARBITRIOS', tributo: 'Arbitrios Municipales', periodo: '2026-04', deuda: '22.26', codigoPredio: 'A' },
    { id: 4, tributoCodigo: 'ARBITRIOS', tributo: 'Arbitrios Municipales', periodo: '2026-04', deuda: '22.26', codigoPredio: 'B' },
  ]
  const grupos = agruparDeuda(cuotas)
  assert.equal(grupos.length, 2)
  assert.equal(grupos[0].total, 66.78)
  assert.deepEqual(grupos[0].items.map((item) => item.id), [3, 4, 1])
  assert.equal(grupos[1].total, 75)
  assert.deepEqual(cuotas.map((item) => item.id), [1, 2, 3, 4])
  assert.deepEqual(agruparDeuda([]), [])
})

test('presenta los períodos como meses o trimestres y conserva valores desconocidos', () => {
  assert.equal(periodoDeuda('2026-04'), 'Abril · 2026')
  assert.equal(periodoDeuda('2026-T1'), '1.º trimestre · 2026')
  assert.equal(periodoDeuda('2026-13'), '2026-13')
  assert.equal(periodoDeuda(null), 'Período no indicado')
})

test('incluye los intereses del total a pagar cuando el servidor los devuelve', () => {
  const [grupo] = agruparDeuda([
    { id: 1, tributo: 'Predial', deuda: '100.00', totalAPagar: '113.25' },
    { id: 2, tributo: 'Predial', deuda: '50.00', totalAPagar: '54.10' },
  ])
  assert.equal(grupo.total, 167.35)
})
