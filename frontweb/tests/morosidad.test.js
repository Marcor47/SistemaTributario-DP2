import assert from 'node:assert/strict'
import test from 'node:test'
import { etiquetaTributo, numeroOpcional, porcentaje } from '../src/utils/morosidad.js'

/* HU008: utilidades de presentación del índice de morosidad (los valores los calcula el backend). */

test('HU008: etiquetaTributo devuelve el nombre legible de cada tributo', () => {
  assert.equal(etiquetaTributo('IMPUESTO_PREDIAL'), 'Impuesto Predial')
  assert.equal(etiquetaTributo('PREDIAL'), 'Impuesto Predial')
  assert.equal(etiquetaTributo('ARBITRIOS'), 'Arbitrios Municipales')
})

test('HU008: etiquetaTributo devuelve la clave original para claves desconocidas', () => {
  assert.equal(etiquetaTributo('OTRO'), 'OTRO')
})

test('HU008: un valor ausente se muestra como no disponible y no como cero', () => {
  assert.equal(numeroOpcional(''), null)
  assert.equal(numeroOpcional('46.6667'), 46.6667)
  assert.equal(porcentaje(null), 'No disponible')
  assert.match(porcentaje(14.8), /^14[.,]80 %$/)
})
