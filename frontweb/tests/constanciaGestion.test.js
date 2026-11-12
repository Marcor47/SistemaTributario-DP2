import test from 'node:test'
import assert from 'node:assert/strict'
import { verificarSolicitudConstancia, rechazarSolicitudConstancia } from '../src/services/constanciaGestionService.js'

test('las acciones del operador usan la ruta autorizada y el token CSRF', async (t) => {
  const original = globalThis.fetch
  const llamadas = []
  t.after(() => { globalThis.fetch = original })
  globalThis.fetch = async (url, opciones) => {
    llamadas.push({ url, opciones })
    return { ok: true, status: 200, json: async () => url.endsWith('/csrf')
      ? { headerName: 'X-CSRF-TOKEN', token: 'token-prueba' } : { estado: 'ORDEN_PAGO_EMITIDA' } }
  }

  await verificarSolicitudConstancia(17)
  assert.match(llamadas[0].url, /\/api\/auth\/interno\/csrf$/)
  assert.match(llamadas[1].url, /\/api\/gestion\/constancias\/17\/verificacion$/)
  assert.equal(llamadas[1].opciones.method, 'POST')
  assert.equal(llamadas[1].opciones.headers['X-CSRF-TOKEN'], 'token-prueba')
  assert.equal(llamadas[1].opciones.credentials, 'include')

  await rechazarSolicitudConstancia(17, 'Datos incompletos')
  assert.match(llamadas[3].url, /\/api\/gestion\/constancias\/17\/rechazo$/)
  assert.deepEqual(JSON.parse(llamadas[3].opciones.body), { motivo: 'Datos incompletos' })
})
