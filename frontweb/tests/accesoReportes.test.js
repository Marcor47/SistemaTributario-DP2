import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test, { before } from 'node:test'
import { obtenerTokenCsrf } from '../src/services/api.js'
import {
  crearRestriccionReporte,
  eliminarRestriccionReporte,
  guardarRestriccionesReportes,
  listarRestriccionesReportes,
  obtenerOpcionesRestricciones,
} from '../src/services/accesoReportesService.js'

const json = (cuerpo, estado = 200) => new Response(JSON.stringify(cuerpo), { status: estado })

before(async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => json({ headerName: 'X-XSRF-TOKEN', token: 'token-prueba' })
  await obtenerTokenCsrf({ renovar: true })
  globalThis.fetch = original
})

test('HU045: las restricciones y su catálogo se consultan en el servidor', async (t) => {
  const rutas = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    rutas.push(new URL(url).pathname)
    return String(url).endsWith('/opciones')
      ? json({ zonas: ['Maranga', 'Pando'], roles: ['GERENCIA'], reportes: ['MOROSOS'], dimensiones: ['REPORTE', 'ZONA'] })
      : json([{ id: 1, rol: 'ANALISTA_RENTAS', tipoReporte: 'PRICOS', dimension: 'ZONA', valor: 'Pando', permitido: false }])
  })
  const lista = await listarRestriccionesReportes()
  const opciones = await obtenerOpcionesRestricciones()
  assert.equal(lista[0].valor, 'Pando')
  assert.deepEqual(opciones.zonas, ['Maranga', 'Pando'])
  assert.deepEqual(rutas, ['/api/admin/restricciones-reportes', '/api/admin/restricciones-reportes/opciones'])
})

test('HU045: crear una restricción de reporte completo no envía zona y usa CSRF', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    llamadas.push({ url: String(url), opciones })
    return json({ id: 2, rol: 'GERENCIA', tipoReporte: 'MOROSOS', dimension: 'REPORTE', valor: null, permitido: false }, 201)
  })
  await crearRestriccionReporte({ rol: 'GERENCIA', tipoReporte: 'MOROSOS', dimension: 'REPORTE', valor: 'Pando' })
  assert.equal(llamadas[0].opciones.method, 'POST')
  assert.equal(llamadas[0].opciones.headers['X-XSRF-TOKEN'], 'token-prueba')
  assert.deepEqual(JSON.parse(llamadas[0].opciones.body), { rol: 'GERENCIA', tipoReporte: 'MOROSOS', dimension: 'REPORTE', valor: null })
})

test('HU045: la configuración completa se guarda en una sola operación con CSRF', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    llamadas.push({ url: String(url), opciones })
    return json([{ id: 3, rol: 'GERENCIA', tipoReporte: 'MOROSOS', dimension: 'ZONA', valor: 'Pando', permitido: false }])
  })
  await guardarRestriccionesReportes({
    GERENCIA: [{ tipoReporte: 'MOROSOS', dimension: 'ZONA', valor: 'Pando' }],
    ANALISTA_RENTAS: [],
  })
  assert.equal(llamadas[0].opciones.method, 'PUT')
  assert.equal(llamadas[0].opciones.headers['X-XSRF-TOKEN'], 'token-prueba')
  assert.deepEqual(JSON.parse(llamadas[0].opciones.body), {
    roles: {
      GERENCIA: [{ tipoReporte: 'MOROSOS', dimension: 'ZONA', valor: 'Pando' }],
      ANALISTA_RENTAS: [],
    },
  })
})

test('HU045: quitar una restricción la elimina por su identificador', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    llamadas.push(`${opciones.method} ${new URL(url).pathname}`)
    return new Response(null, { status: 204 })
  })
  await eliminarRestriccionReporte(7)
  assert.deepEqual(llamadas, ['DELETE /api/admin/restricciones-reportes/7'])
})

test('HU045: la pantalla, el menú y el aviso quedan conectados y sin datos en el navegador', () => {
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8')
  const modulos = readFileSync(new URL('../src/utils/modulos.jsx', import.meta.url), 'utf8')
  const configuracion = readFileSync(new URL('../src/pages/ConfiguracionAccesoReportes.jsx', import.meta.url), 'utf8')
  assert.match(app, /path="\/configuracion\/reportes"/)
  assert.match(app, /REPORTES:CONFIGURAR/)
  assert.match(modulos, /ruta: '\/configuracion\/reportes'/)
  assert.match(modulos, /permiso: 'REPORTES:CONFIGURAR'/)
  assert.match(configuracion, /guardarRestriccionesReportes/)
  assert.match(configuracion, /Solo se muestran los roles con permiso/)
  assert.doesNotMatch(configuracion, /AvisoAccesoReportes/)
  assert.doesNotMatch(configuracion, /localStorage|simulad/i)
})
