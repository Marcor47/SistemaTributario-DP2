import assert from 'node:assert/strict'
import test from 'node:test'
import { ESTADOS_RECLAMO, fechaHoraLocal, ordenarReclamos, validarReclamo } from '../src/utils/reclamos.js'

test('el contribuyente solo ve estados municipales simplificados', () => {
  assert.deepEqual(Object.values(ESTADOS_RECLAMO).map((estado) => estado.etiqueta), ['Enviado', 'En revisión', 'Atendido', 'Cancelado'])
})

test('la validación rechaza incidentes futuros y comentarios vacíos', () => {
  const ahora = new Date(2026, 10, 12, 10, 0)
  const errores = validarReclamo({ tipoReclamo: 'COBRANZA', referencia: '', fechaHoraIncidente: fechaHoraLocal(new Date(2026, 10, 12, 11, 0)), comentario: '  ' }, ahora)
  assert.equal(errores.fechaHoraIncidente, 'El incidente no puede ser futuro.')
  assert.equal(errores.comentario, 'Describe lo ocurrido.')
})

test('los reclamos se ordenan del más reciente sin alterar la entrada', () => {
  const original = [{ idReclamo: 1, fechaRegistro: '2026-11-01T10:00' }, { idReclamo: 2, fechaRegistro: '2026-11-02T10:00' }]
  assert.deepEqual(ordenarReclamos(original).map((item) => item.idReclamo), [2, 1])
  assert.deepEqual(original.map((item) => item.idReclamo), [1, 2])
})
