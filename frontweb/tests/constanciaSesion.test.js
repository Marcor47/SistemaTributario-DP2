import assert from 'node:assert/strict'
import test from 'node:test'
import { firmarSolicitud, iniciarSolicitud, obtenerSesion } from '../src/services/constanciaService.js'

test('HU015: la constancia consulta la sesión real con cookie', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    llamadas.push({ url, opciones })
    return new Response(JSON.stringify({ codigoContribuyente: '014502' }), { status: 200 })
  })
  assert.deepEqual(await obtenerSesion(), { codigoContribuyente: '014502' })
  assert.equal(llamadas.length, 1)
  assert.ok(llamadas[0].url.endsWith('/auth/me'))
  assert.equal(llamadas[0].opciones.credentials, 'include')
})

test('HU015: iniciar y firmar la solicitud siempre van al servidor', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    llamadas.push({ url: String(url), opciones })
    return new Response(JSON.stringify({ idSolicitud: 7, estado: 'FIRMADA' }), { status: 200 })
  })
  await iniciarSolicitud()
  await firmarSolicitud(7, 'RklSTUE=', 'CERTIFICADO')
  assert.ok(llamadas[0].url.endsWith('/constancias'))
  assert.equal(llamadas[0].opciones.method, 'POST')
  assert.ok(llamadas[1].url.endsWith('/constancias/7/firma'))
  assert.deepEqual(JSON.parse(llamadas[1].opciones.body), { firmaBase64: 'RklSTUE=', certificado: 'CERTIFICADO' })
})
