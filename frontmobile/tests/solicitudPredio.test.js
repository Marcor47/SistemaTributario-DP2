import assert from 'node:assert/strict'
import test from 'node:test'
import { infoEstado, observacionRechazo, ordenarSolicitudes, textoAdjuntos, tieneResultado } from '../src/utils/solicitudPredio.js'

test('cada estado del backend se muestra con su texto y tono', () => {
  assert.equal(infoEstado('EN_REVISION').etiqueta, 'En revisión')
  assert.equal(infoEstado('EN_REVISION').tono, 'alerta')
  assert.equal(infoEstado('ACEPTADA').etiqueta, 'Aceptada')
  assert.equal(infoEstado('ACEPTADA').tono, 'exito')
  assert.equal(infoEstado('RECHAZADA').etiqueta, 'Rechazada')
  assert.equal(infoEstado('RECHAZADA').tono, 'critico')
})

test('un estado desconocido o ausente no rompe la pantalla', () => {
  assert.equal(infoEstado('OBSERVADA').etiqueta, 'OBSERVADA')
  assert.equal(infoEstado('OBSERVADA').tono, 'info')
  assert.equal(infoEstado(undefined).etiqueta, 'Sin estado')
})

test('la observación solo aparece en solicitudes rechazadas y con texto', () => {
  assert.equal(observacionRechazo({ estado: 'RECHAZADA', observacion: '  Falta la partida registral  ' }), 'Falta la partida registral')
  assert.equal(observacionRechazo({ estado: 'RECHAZADA', observacion: '   ' }), null)
  assert.equal(observacionRechazo({ estado: 'RECHAZADA' }), null)
  assert.equal(observacionRechazo({ estado: 'EN_REVISION', observacion: 'no debe verse' }), null)
  assert.equal(observacionRechazo({ estado: 'ACEPTADA', observacion: 'no debe verse' }), null)
  assert.equal(observacionRechazo(null), null)
})

test('la observación se lee de motivoRechazo y, si falta, de observacion', () => {
  assert.equal(observacionRechazo({ estado: 'RECHAZADA', motivoRechazo: 'Documentos ilegibles' }), 'Documentos ilegibles')
  assert.equal(observacionRechazo({ estado: 'RECHAZADA', motivoRechazo: '  ', observacion: 'Falta la partida' }), 'Falta la partida')
  assert.equal(observacionRechazo({ estado: 'RECHAZADA', motivoRechazo: 'Uno', observacion: 'Otro' }), 'Uno')
  assert.equal(observacionRechazo({ estado: 'EN_REVISION', motivoRechazo: 'no debe verse' }), null)
})

test('el resultado existe solo cuando la solicitud fue aceptada o rechazada', () => {
  assert.equal(tieneResultado({ estado: 'ACEPTADA' }), true)
  assert.equal(tieneResultado({ estado: 'RECHAZADA' }), true)
  assert.equal(tieneResultado({ estado: 'EN_REVISION' }), false)
  assert.equal(tieneResultado(undefined), false)
})

test('las solicitudes se ordenan de la más reciente a la más antigua sin alterar la lista original', () => {
  const original = [
    { idSolicitud: 1, fechaEnvio: '2026-11-01T09:00:00' },
    { idSolicitud: 3, fechaEnvio: '2026-11-05T08:00:00' },
    { idSolicitud: 2, fechaEnvio: '2026-11-05T08:00:00' },
    { idSolicitud: 4, fechaEnvio: null },
  ]
  assert.deepEqual(ordenarSolicitudes(original).map((s) => s.idSolicitud), [3, 2, 1, 4])
  assert.deepEqual(original.map((s) => s.idSolicitud), [1, 3, 2, 4])
  assert.deepEqual(ordenarSolicitudes(null), [])
})

test('el conteo de adjuntos concuerda en singular y plural', () => {
  assert.equal(textoAdjuntos(1), '1 documento adjunto')
  assert.equal(textoAdjuntos(3), '3 documentos adjuntos')
  assert.equal(textoAdjuntos(undefined), '0 documentos adjuntos')
})
