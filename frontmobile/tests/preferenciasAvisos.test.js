import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import {
  cuerpoPreferenciaRecordatorio,
  puedeActivar,
  RUTA_PREFERENCIAS_AVISOS,
  textoDias,
  textoRecordatorio,
} from '../src/utils/preferenciasAvisos.js'

test('HU016: P12 usa la ruta del portal y envía solo la preferencia', () => {
  assert.equal(RUTA_PREFERENCIAS_AVISOS, '/portal/preferencias-avisos')
  assert.deepEqual(JSON.parse(cuerpoPreferenciaRecordatorio(false)), { recordatorioVencimiento: false })
  assert.deepEqual(JSON.parse(cuerpoPreferenciaRecordatorio('si')), { recordatorioVencimiento: true })
  const servicio = readFileSync(new URL('../src/services/preferenciaAvisoService.js', import.meta.url), 'utf8')
  assert.match(servicio, /pedir\(RUTA_PREFERENCIAS_AVISOS/)
  assert.match(servicio, /method: 'PUT'/)
  assert.match(servicio, /cuerpoPreferenciaRecordatorio/)
})

test('HU016: los textos muestran la anticipación y el estado real del correo', () => {
  assert.equal(textoDias(5), '5 días antes del vencimiento')
  assert.equal(textoDias(1), '1 día antes del vencimiento')
  assert.equal(textoDias(0), 'el mismo día del vencimiento')
  assert.equal(textoDias(null), '—')
  assert.equal(puedeActivar({ correo: 'maria@example.org' }), true)
  assert.equal(puedeActivar({ correo: '  ' }), false)
  assert.match(textoRecordatorio({ correo: 'maria@example.org', recordatorioVencimiento: true, diasAnticipacion: 5 }), /5 días/)
  assert.match(textoRecordatorio({ correo: 'maria@example.org', recordatorioVencimiento: false }), /No recibirás/)
  assert.match(textoRecordatorio({ correo: '' }), /No tienes un correo registrado/)
})

test('HU016: P12 está registrada, enlazada desde el inicio y usa el backend', () => {
  const layout = readFileSync(new URL('../src/app/_layout.jsx', import.meta.url), 'utf8')
  const inicio = readFileSync(new URL('../src/app/index.jsx', import.meta.url), 'utf8')
  const pantalla = readFileSync(new URL('../src/app/notificaciones.jsx', import.meta.url), 'utf8')
  assert.match(layout, /name="notificaciones"/)
  assert.match(inicio, /ruta: '\/notificaciones'/)
  assert.match(pantalla, /guardarRecordatorioVencimiento/)
  assert.doesNotMatch(pantalla, /localStorage|simulad/i)
})
