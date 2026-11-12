import assert from 'node:assert/strict'
import test from 'node:test'
import {
  cifrasProcesamiento, estadoAviso, ordenarAvisos, puedeProcesarAlertas, tipoAviso,
  validarContribuyente, validarIdLote,
} from '../src/utils/alertasMora.js'

/* HU012: utilidades de presentación de las alertas de mora temprana (los valores los calcula el backend). */

test('HU012: cada estado del aviso tiene su texto y su estilo, y uno desconocido no rompe la tabla', () => {
  assert.deepEqual(estadoAviso('ENVIADO'), { texto: 'Enviado', clase: 'ok' })
  assert.deepEqual(estadoAviso('PENDIENTE'), { texto: 'Pendiente', clase: 'aviso' })
  assert.deepEqual(estadoAviso('FALLIDO'), { texto: 'Fallido', clase: 'critico' })
  assert.deepEqual(estadoAviso('REBOTADO'), { texto: 'REBOTADO', clase: 'neutro' })
  assert.equal(estadoAviso(undefined).texto, 'Sin estado')
})

test('HU012: el tipo de aviso se muestra legible', () => {
  assert.equal(tipoAviso('MORA_TEMPRANA'), 'Mora temprana')
  assert.equal(tipoAviso('RECORDATORIO_VENCIMIENTO'), 'Recordatorio de vencimiento')
  assert.equal(tipoAviso('OTRO'), 'OTRO')
  assert.equal(tipoAviso(null), '—')
})

test('HU012: solo quien evalúa alertas o gestiona el ETL puede procesar un lote', () => {
  assert.equal(puedeProcesarAlertas({ permisos: ['ALERTAS:CONSULTAR', 'ALERTAS:EVALUAR'] }), true)
  assert.equal(puedeProcesarAlertas({ permisos: ['ETL:GESTIONAR'] }), true)
  assert.equal(puedeProcesarAlertas({ permisos: ['ALERTAS:CONSULTAR'] }), false)
  assert.equal(puedeProcesarAlertas({ permisos: [] }), false)
  assert.equal(puedeProcesarAlertas(null), false)
  assert.equal(puedeProcesarAlertas({}), false)
})

test('HU012: el número de lote debe ser un entero positivo', () => {
  assert.deepEqual(validarIdLote(' 12 '), { valido: true, valor: 12 })
  for (const invalido of ['', '   ', null, undefined, '0', '-3', '1.5', '1e3', 'abc', '12abc', '99999999999999999999']) {
    const resultado = validarIdLote(invalido)
    assert.equal(resultado.valido, false, `debería rechazar ${JSON.stringify(invalido)}`)
    assert.ok(resultado.mensaje)
  }
})

test('HU012: el contribuyente se busca por código o documento, sin símbolos que alteren la ruta', () => {
  assert.deepEqual(validarContribuyente(' 014502 '), { valido: true, valor: '014502' })
  assert.deepEqual(validarContribuyente('41028765'), { valido: true, valor: '41028765' })
  for (const invalido of ['', '  ', null, '014 502', '01/4502', '0145?02', '014502#1', 'a'.repeat(21)]) {
    assert.equal(validarContribuyente(invalido).valido, false, `debería rechazar ${JSON.stringify(invalido)}`)
  }
})

test('HU012: los avisos se ordenan del más reciente al más antiguo y los pendientes van al final', () => {
  const original = [
    { idAviso: 1, fechaEnvio: '2026-11-01T09:00:00' },
    { idAviso: 2, fechaEnvio: null },
    { idAviso: 4, fechaEnvio: '2026-11-05T08:00:00' },
    { idAviso: 3, fechaEnvio: '2026-11-05T08:00:00' },
  ]
  assert.deepEqual(ordenarAvisos(original).map((a) => a.idAviso), [4, 3, 1, 2])
  assert.deepEqual(original.map((a) => a.idAviso), [1, 2, 4, 3])
  assert.deepEqual(ordenarAvisos(undefined), [])
})

test('HU012: las cifras del procesamiento resaltan lo que requiere atención y no inventan valores', () => {
  const cifras = cifrasProcesamiento({
    cuotasEvaluadas: 120, cuotasVencidasDetectadas: 30, alertasEnviadas: 25, sinCorreoRegistrado: 4, alertasFallidas: 1,
  })
  assert.deepEqual(cifras.map((c) => c.valor), [120, 30, 25, 0, 4, 1])
  assert.equal(cifras.find((c) => c.clave === 'alertasEnviadas').tono, 'ok')
  assert.equal(cifras.find((c) => c.clave === 'sinCorreoRegistrado').tono, 'aviso')
  assert.equal(cifras.find((c) => c.clave === 'alertasFallidas').tono, 'critico')

  const sinIncidencias = cifrasProcesamiento({ cuotasEvaluadas: 10, alertasEnviadas: 3 })
  assert.equal(sinIncidencias.find((c) => c.clave === 'sinCorreoRegistrado').tono, null)
  assert.equal(sinIncidencias.find((c) => c.clave === 'alertasFallidas').tono, null)
  assert.equal(cifrasProcesamiento(null).every((c) => c.valor === 0), true)
})
