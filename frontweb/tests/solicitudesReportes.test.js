import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { consultarSolicitudesReporte } from '../src/services/solicitudReporteService.js'
import {
  etiquetaOperacion,
  parametrosSolicitudesReporte,
  textoFiltrosAplicados,
  validarPeriodoSolicitudes,
} from '../src/utils/solicitudesReportes.js'

test('HU044: construye la consulta paginada únicamente con los filtros aplicados', () => {
  const parametros = parametrosSolicitudesReporte({
    usuario: '  gerencia  ', tipo: 'MOROSOS', desde: '2026-11-01', hasta: '2026-11-12',
  }, 2, 25)

  assert.equal(parametros.get('usuario'), 'gerencia')
  assert.equal(parametros.get('tipo'), 'MOROSOS')
  assert.equal(parametros.get('desde'), '2026-11-01')
  assert.equal(parametros.get('hasta'), '2026-11-12')
  assert.equal(parametros.get('pagina'), '1')
  assert.equal(parametros.get('tamanio'), '25')
})

test('HU044: valida que el periodo esté completo y ordenado', () => {
  assert.equal(validarPeriodoSolicitudes({ desde: '', hasta: '' }), '')
  assert.match(validarPeriodoSolicitudes({ desde: '2026-11-01', hasta: '' }), /ambas fechas/)
  assert.match(validarPeriodoSolicitudes({ desde: '2026-11-12', hasta: '2026-11-01' }), /no puede ser posterior/)
  assert.equal(validarPeriodoSolicitudes({ desde: '2026-11-01', hasta: '2026-11-12' }), '')
})

test('HU044: diferencia visualización y formatos de exportación', () => {
  assert.equal(etiquetaOperacion('VISUALIZACION'), 'Visualización en pantalla')
  assert.equal(etiquetaOperacion('EXCEL'), 'Exportación Excel')
  assert.equal(etiquetaOperacion('PDF'), 'Exportación PDF')
  assert.equal(textoFiltrosAplicados('{}'), 'Sin filtros adicionales')
})

test('HU044: presenta los filtros registrados con etiquetas legibles', () => {
  assert.equal(textoFiltrosAplicados('{"zona":"Z2","tributo":"IMPUESTO_PREDIAL","montoMinimo":500}'),
    'Zona: Z2 · Tributo: IMPUESTO PREDIAL · Monto mínimo: 500')
})

test('HU044: consulta la API con credenciales y parámetros de servidor', async (t) => {
  let llamada
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    llamada = { url, opciones }
    return new Response(JSON.stringify({ total: 0, solicitudes: [], frecuencias: [] }), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    })
  })

  await consultarSolicitudesReporte({ usuario: '', tipo: '', desde: '', hasta: '' }, 1, 10)
  const url = new URL(llamada.url)
  assert.equal(url.pathname, '/api/reportes/solicitudes')
  assert.equal(url.searchParams.get('pagina'), '0')
  assert.equal(url.searchParams.get('tamanio'), '10')
  assert.equal(llamada.opciones.credentials, 'include')
})

test('HU044: el menú exige el permiso específico y usa la ruta oficial', () => {
  const modulos = readFileSync(new URL('../src/utils/modulos.jsx', import.meta.url), 'utf8')
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8')
  assert.match(modulos, /ruta: '\/reportes\/solicitudes'[\s\S]*texto: 'Uso de reportes'/)
  assert.match(modulos, /permiso: 'REPORTES:SOLICITUDES'/)
  assert.match(app, /path="\/reportes\/solicitudes"[\s\S]*permiso="REPORTES:SOLICITUDES"/)
})
