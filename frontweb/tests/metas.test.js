import assert from 'node:assert/strict'
import test from 'node:test'
import { formatoValorMeta, opcionesPeriodo, periodoSugerido, validarMeta } from '../src/utils/metas.js'

test('HU009: genera los periodos según la periodicidad del indicador', () => {
  const mensual = opcionesPeriodo({ periodicidad: 'MENSUAL', periodosPorAnio: 12 }, 2026)
  const trimestral = opcionesPeriodo({ periodicidad: 'TRIMESTRAL', periodosPorAnio: 4 }, 2026)
  const anual = opcionesPeriodo({ periodicidad: 'ANUAL', periodosPorAnio: 1 }, 2026)
  assert.equal(mensual.length, 12)
  assert.equal(mensual[9].etiqueta, 'Octubre 2026')
  assert.equal(trimestral[1].etiqueta, 'Trimestre 2 de 2026')
  assert.equal(anual[0].etiqueta, '2026')
})

test('HU009: propone el periodo vigente al registrar una meta del año actual', () => {
  const hoy = new Date(2026, 9, 22)
  assert.equal(periodoSugerido({ periodicidad: 'MENSUAL' }, 2026, hoy), 10)
  assert.equal(periodoSugerido({ periodicidad: 'TRIMESTRAL' }, 2026, hoy), 4)
  assert.equal(periodoSugerido({ periodicidad: 'ANUAL' }, 2026, hoy), 1)
  assert.equal(periodoSugerido({ periodicidad: 'MENSUAL' }, 2027, hoy), 1)
})

test('HU009: valida el rango de porcentajes, el periodo y la precisión', () => {
  const indicador = { unidadMedida: 'PORCENTAJE', periodosPorAnio: 4 }
  assert.deepEqual(validarMeta({ indicador, anio: 2026, numeroPeriodo: 2, valorMeta: '85.25', observacion: '' }), {})
  assert.equal(validarMeta({ indicador, anio: 2026, numeroPeriodo: 5, valorMeta: '85', observacion: '' }).numeroPeriodo, 'Selecciona un periodo válido.')
  assert.equal(validarMeta({ indicador, anio: 2026, numeroPeriodo: 2, valorMeta: '100.01', observacion: '' }).valorMeta, 'El porcentaje no puede superar 100.')
  assert.match(validarMeta({ indicador, anio: 2026, numeroPeriodo: 2, valorMeta: '85.555', observacion: '' }).valorMeta, /2 decimales/)
})

test('HU009: presenta el valor con la unidad correspondiente', () => {
  assert.match(formatoValorMeta(87.5, 'PORCENTAJE'), /^87[,.]5 %$/)
  assert.match(formatoValorMeta(1250000.5, 'SOLES'), /1[,.]250[,.]000[,.]50/)
  assert.equal(formatoValorMeta(1250, 'CANTIDAD').replace(/[,.]/g, ''), '1250')
})
