import assert from 'node:assert/strict'
import test from 'node:test'
import { fechaHoraBuzon, filtrarDocumentos } from '../src/utils/buzon.js'

test('el filtro de no leídos conserva el estado recibido del servidor', () => {
  const documentos = [{ idDocumento: 1, tipo: 'CONSTANCIA', leido: false }, { idDocumento: 2, tipo: 'ESTADO_CUENTA', leido: true }]
  assert.deepEqual(filtrarDocumentos(documentos, 'SIN_LEER'), [documentos[0]])
  assert.deepEqual(filtrarDocumentos(documentos, 'ESTADO_CUENTA'), [documentos[1]])
  assert.equal(documentos[0].leido, false)
})
test('la fecha municipal conserva la hora de Lima sin desplazamiento por zona del teléfono', () => {
  assert.equal(fechaHoraBuzon('2026-11-04T10:31:00'), '04/11/2026 10:31')
  assert.equal(fechaHoraBuzon(null), '—')
})
