import assert from 'node:assert/strict'
import test from 'node:test'
import { cambiarArchivadoBuzon, cambiarDestacadoBuzon, listarBuzon } from '../src/services/buzonService.js'

test('HU019: la consulta del buzón envía vista, filtros y paginación al backend', async (t) => {
  let ruta = ''
  t.mock.method(globalThis, 'fetch', async (url) => {
    ruta = String(url)
    return new Response(JSON.stringify({ total: 0, sinLeer: 0, documentos: [], pagina: 1, tamanio: 25, totalPaginas: 0 }))
  })

  await listarBuzon({ vista: 'ARCHIVADOS', busqueda: 'reclamo 20', tipo: 'RESPUESTA_RECLAMO', leido: false, pagina: 1, tamanio: 25 })
  const parametros = new URL(ruta).searchParams
  assert.equal(parametros.get('vista'), 'ARCHIVADOS')
  assert.equal(parametros.get('busqueda'), 'reclamo 20')
  assert.equal(parametros.get('tipo'), 'RESPUESTA_RECLAMO')
  assert.equal(parametros.get('leido'), 'false')
  assert.equal(parametros.get('pagina'), '1')
  assert.equal(parametros.get('tamanio'), '25')
})

test('HU019: destacar y archivar usan CSRF y no eliminan mensajes', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    llamadas.push([String(url), opciones])
    if (String(url).endsWith('/auth/interno/csrf')) {
      return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'buzon-token' }))
    }
    return new Response(JSON.stringify({ idDocumento: 7, destacado: true, archivado: true }))
  })

  await cambiarDestacadoBuzon(7, true)
  await cambiarArchivadoBuzon(7, true)
  const cambios = llamadas.filter(([url]) => !url.endsWith('/auth/interno/csrf'))
  assert.ok(cambios[0][0].endsWith('/buzon/7/destacado'))
  assert.ok(cambios[1][0].endsWith('/buzon/7/archivado'))
  assert.equal(cambios[0][1].method, 'PATCH')
  assert.equal(new Headers(cambios[0][1].headers).get('X-CSRF-TOKEN'), 'buzon-token')
  assert.equal(new Headers(cambios[0][1].headers).get('Content-Type'), 'application/json')
  assert.equal(new Headers(cambios[1][1].headers).get('Content-Type'), 'application/json')
  assert.deepEqual(JSON.parse(cambios[1][1].body), { valor: true })
})
