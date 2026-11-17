import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { listarSolicitudesConstancia, verificarSolicitudConstancia, rechazarSolicitudConstancia } from '../src/services/constanciaGestionService.js'

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

test('la bandeja acepta el filtro por estado para el historial de rechazadas', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(String(url))
    return { ok: true, status: 200, json: async () => [] }
  })

  await listarSolicitudesConstancia('RECHAZADA')
  await listarSolicitudesConstancia()
  assert.ok(urls[0].endsWith('/api/gestion/constancias?estado=RECHAZADA'))
  assert.ok(urls[1].endsWith('/api/gestion/constancias'))
})

test('la bandeja desplaza suavemente al detalle al abrirlo', () => {
  const fuente = readFileSync(new URL('../src/pages/GestionConstancias.jsx', import.meta.url), 'utf8')
  const estilos = readFileSync(new URL('../src/pages/GestionConstancias.css', import.meta.url), 'utf8')

  assert.match(fuente, /scrollIntoView/)
  assert.match(fuente, /behavior: reducirMovimiento \? 'auto' : 'smooth'/)
  assert.match(fuente, /ref=\{detalleRef\}/)
  assert.match(fuente, /prefers-reduced-motion/)
  assert.match(estilos, /scroll-margin-top/)
})
