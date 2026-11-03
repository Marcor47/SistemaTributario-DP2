import assert from 'node:assert/strict'
import test from 'node:test'
import { buscarCoincidencias } from '../src/services/contribuyenteService.js'

test('HU001: el buscador interno usa /api/contribuyentes/buscar y filtra por tipo y documento', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    const destino = new URL(url)
    assert.equal(destino.pathname, '/api/contribuyentes/buscar')
    assert.equal(destino.searchParams.get('q'), '4102')
    assert.equal(opciones.credentials, 'include')
    return new Response(JSON.stringify([
      { codigo: '014502', nombre: 'GARCÍA TORRES, MARÍA ELENA', tipoDoc: 'DNI', doc: '41028765' },
      { codigo: '099999', nombre: 'OTRA PERSONA', tipoDoc: 'RUC', doc: '41020000000' },
    ]))
  })

  const encontrados = await buscarCoincidencias('DNI', '4102')
  assert.equal(encontrados.length, 1)
  assert.equal(encontrados[0].codigo, '014502')
})

test('HU001: sin mocks, un 401 de la API se propaga al llamador', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    codigo: 'SESION_REQUERIDA',
    mensaje: 'Inicie sesión para continuar con el trámite.',
  }), { status: 401 }))

  await assert.rejects(buscarCoincidencias('DNI', '4102'), (error) => (
    error.estado === 401 && error.codigo === 'SESION_REQUERIDA'
  ))
})

test('HU001: no consulta la API si no se ingresó documento', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', () => {
    throw new Error('No debe consultar la API')
  })

  assert.deepEqual(await buscarCoincidencias('DNI', '   '), [])
  assert.equal(fetch.mock.callCount(), 0)
})
