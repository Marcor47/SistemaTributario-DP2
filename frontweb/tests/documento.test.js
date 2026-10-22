import assert from 'node:assert/strict'
import test from 'node:test'
import { limpiarDocumento, validarDocumento } from '../src/utils/documento.js'

test('DNI: solo acepta dígitos y hasta 8 caracteres', () => {
  assert.equal(limpiarDocumento('45a8-7', 'DNI'), '4587')
  assert.equal(limpiarDocumento('1234567890', 'DNI'), '12345678')
})

test('RUC: solo acepta dígitos y hasta 11 caracteres', () => {
  assert.equal(limpiarDocumento('20-456A', 'RUC'), '20456')
  assert.equal(limpiarDocumento('204567812399', 'RUC'), '20456781239')
})

test('CE: acepta alfanuméricos, quita tildes, convierte a mayúsculas y limita a 12', () => {
  assert.equal(limpiarDocumento('00ab-12', 'CE'), '00AB12')
  assert.equal(limpiarDocumento('ábc 123', 'CE'), 'ABC123')
  assert.equal(limpiarDocumento('ABCDEFGHIJKLMN', 'CE'), 'ABCDEFGHIJKL')
})

test('valida el mínimo de búsqueda y el prefijo de un RUC completo', () => {
  assert.match(validarDocumento('DNI', '4'), /al menos 2/)
  assert.equal(validarDocumento('DNI', '4587'), '')
  assert.match(validarDocumento('RUC', '30456781239'), /empezar con 10, 15, 17 o 20/)
  assert.equal(validarDocumento('RUC', '20456781239'), '')
  assert.equal(validarDocumento('CE', '00AB12'), '')
})
