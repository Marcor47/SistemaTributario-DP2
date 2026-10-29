import assert from 'node:assert/strict'
import test from 'node:test'
import { filtrosDisponibles, limpiarFiltros, metricaReporte, parametrosReporte, validarFiltros } from '../src/utils/reportes.js'

const morosos = { tipo: 'MOROSOS', filtros: ['zona', 'tributo', 'montoMinimo'] }
const pricos = { tipo: 'PRICOS', filtros: ['anio', 'zona', 'tributo', 'montoMinimo'] }

test('HU006: el catálogo controla los filtros visibles', () => {
  assert.deepEqual(filtrosDisponibles(morosos), ['zona', 'tributo', 'montoMinimo'])
  assert.deepEqual(filtrosDisponibles({ filtros: ['zona', 'desconocido'] }), ['zona'])
})

test('HU006: morosos limpia y nunca envía el año', () => {
  const filtros = { anio: '2025', zona: ' Z2 ', tributo: 'ARBITRIOS', montoMinimo: '1000' }
  assert.equal(limpiarFiltros(morosos, filtros).anio, '')
  assert.equal(parametrosReporte(morosos, filtros).toString(), 'zona=Z2&tributo=ARBITRIOS&montoMinimo=1000')
})

test('HU006: construye los parámetros aceptados para PRICOS', () => {
  const consulta = parametrosReporte(pricos, { anio: '2026', zona: '', tributo: 'IMPUESTO_PREDIAL', montoMinimo: '0' })
  assert.equal(consulta.get('anio'), '2026')
  assert.equal(consulta.get('tributo'), 'IMPUESTO_PREDIAL')
  assert.equal(consulta.get('montoMinimo'), '0')
  assert.equal(consulta.has('zona'), false)
})

test('HU006: rechaza filtros inválidos antes de consultar', () => {
  const errores = validarFiltros(pricos, { anio: '1999', zona: '123456789012345678901', tributo: 'ALCABALA', montoMinimo: '-1' })
  assert.deepEqual(Object.keys(errores).sort(), ['anio', 'montoMinimo', 'tributo', 'zona'])
})

test('HU006: selecciona la métrica monetaria de cada reporte', () => {
  assert.equal(metricaReporte('MOROSOS', { deudaVencida: 120 }), 120)
  assert.equal(metricaReporte('PRICOS', { emitidoAnio: 500 }), 500)
  assert.equal(metricaReporte('BUENOS_PAGADORES', { pagadoAnio: 80 }), 80)
})
