import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import {
  calcularSegmentacion, decisionesPorCodigo, listarSegmentosBorrador, reasignarCaso,
} from '../src/services/calidadDatosService.js'
import { obtenerCatalogosPredio, registrarPredio } from '../src/services/predioService.js'

// Responde el token CSRF cuando se pide y registra el resto de llamadas.
function simularFetch(t, respuesta) {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    if (String(url).endsWith('/auth/interno/csrf')) return Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-prueba' })
    llamadas.push({ url: new URL(url), opciones })
    return Response.json(respuesta)
  })
  return llamadas
}

test('HU036: el formulario toma zonas y usos del backend', async (t) => {
  const llamadas = simularFetch(t, { zonas: ['Z1', 'Z2'], usos: ['Casa habitación'] })
  const catalogos = await obtenerCatalogosPredio()
  assert.deepEqual(catalogos.zonas, ['Z1', 'Z2'])
  assert.equal(llamadas[0].url.pathname, '/api/predios/catalogos')
})

test('HU036: registra el predio con números y nulos en los campos opcionales vacíos', async (t) => {
  const llamadas = simularFetch(t, { codigoPredio: 'P-900001' })
  await registrarPredio({
    codigoContribuyente: ' 014502 ', codigoPredio: 'p-900001', numeroCatastral: '', ubicacion: ' Av. La Marina 2000 ',
    usoPredio: '', tipoPredio: 'URBANO', areaTerreno: '120.5', baseImponible: '85000', fechaAdquisicion: '',
    zona: 'Z1', condicionPropiedad: 'PROPIETARIO', porcentajePropiedad: '100',
  })
  const { url, opciones } = llamadas[0]
  assert.equal(url.pathname, '/api/predios')
  assert.equal(opciones.method, 'POST')
  assert.equal(new Headers(opciones.headers).get('X-XSRF-TOKEN'), 'csrf-prueba')
  assert.deepEqual(JSON.parse(opciones.body), {
    codigoContribuyente: '014502', codigoPredio: 'P-900001', numeroCatastral: null, ubicacion: 'Av. La Marina 2000',
    usoPredio: null, tipoPredio: 'URBANO', areaTerreno: 120.5, baseImponible: 85000, fechaAdquisicion: null,
    zona: 'Z1', condicionPropiedad: 'PROPIETARIO', porcentajePropiedad: 100,
  })
})

test('HU036: solo quien tiene PREDIOS:REGISTRAR ve el registro', () => {
  const pagina = readFileSync(new URL('../src/pages/ConsultaPredio.jsx', import.meta.url), 'utf8')
  assert.match(pagina, /PREDIOS:REGISTRAR/)
  assert.match(pagina, /<RegistroPredio/)
})

test('HU040: consulta las decisiones por tipo de entidad y código SAMNET', async (t) => {
  const llamadas = simularFetch(t, [])
  await decisionesPorCodigo('PREDIO', ' P-000123 ')
  const { url } = llamadas[0]
  assert.equal(url.pathname, '/api/homologacion/decisiones')
  assert.equal(url.searchParams.get('tipoEntidad'), 'PREDIO')
  assert.equal(url.searchParams.get('codigo'), 'P-000123')
})

test('HU028: reasigna el caso a otro registro maestro con su motivo', async (t) => {
  const llamadas = simularFetch(t, { idCaso: 7 })
  await reasignarCaso(7, 41, 'Mismo DNI con distinta grafía')
  const { url, opciones } = llamadas[0]
  assert.equal(url.pathname, '/api/homologacion/casos/7/reasignacion')
  assert.equal(opciones.method, 'POST')
  assert.deepEqual(JSON.parse(opciones.body), { idMaestro: 41, motivo: 'Mismo DNI con distinta grafía' })
})

test('HU030: ejecuta el modelo y lista los segmentos en borrador', async (t) => {
  const llamadas = simularFetch(t, [])
  await calcularSegmentacion()
  await listarSegmentosBorrador()
  assert.equal(llamadas[0].url.pathname, '/api/segmentos/calculos')
  assert.equal(llamadas[0].opciones.method, 'POST')
  assert.equal(llamadas[1].url.searchParams.get('estado'), 'BORRADOR')
})

test('HU030 a HU047: las pantallas nuevas están enlazadas', () => {
  const calidad = readFileSync(new URL('../src/pages/CalidadDatos.jsx', import.meta.url), 'utf8')
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8')
  const plataforma = readFileSync(new URL('../src/pages/Reporteria.jsx', import.meta.url), 'utf8')
  assert.match(calidad, /id: 'auditoria'/)
  assert.match(calidad, /id: 'segmentacion'/)
  assert.match(calidad, /resolver\('reasignar'\)/)
  assert.match(app, /path="\/portal\/tramites-predio"/)
  assert.match(plataforma, /permiso: 'ALERTAS:CONSULTAR'/)
})
