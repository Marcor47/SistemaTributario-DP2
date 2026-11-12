import assert from 'node:assert/strict'
import test from 'node:test'
import { buscarEnMapa, obtenerFichaMapa, obtenerMapaSegmentos } from '../src/services/segmentosService.js'
import {
  COLOR_ATENUADO, colorSegmento, filasFicha, formatearValor, porcentajeVarianza,
  puntosPorSegmento, seriesMapa, UMBRAL_GRANDE, zoomA,
} from '../src/utils/mapaSegmentos.js'

const MAPA = {
  modelo: { idModelo: 9, estado: 'BORRADOR', contribuyentes: 4, varianzaExplicada: [0.6, 0.25] },
  variables: [
    { clave: 'pctATiempo', etiqueta: 'Cuotas pagadas a tiempo', unidad: '%' },
    { clave: 'mesesSinPago', etiqueta: 'Meses desde el último pago', unidad: 'meses' },
  ],
  segmentos: [
    { idSegmento: 1, indice: 0, nombre: 'S1 · paga a tiempo', tamano: 2 },
    { idSegmento: 2, indice: 1, nombre: 'S2 · deuda antigua', tamano: 2 },
  ],
  puntos: [[10, 0, 0, 0], [11, 0, 1, 1], [12, 1, 4, 4], [13, 1, 5, 2]],
  puedeVerNombres: true,
}

test('agrupa los puntos por segmento con el id de asignación al final', () => {
  assert.deepEqual(puntosPorSegmento(MAPA), [[[0, 0, 10], [1, 1, 11]], [[4, 4, 12], [5, 2, 13]]])
})

test('cada segmento tiene su color fijo y al resaltar uno los demás se atenúan', () => {
  const todos = seriesMapa(MAPA)
  assert.equal(todos[0].itemStyle.color, colorSegmento(0))
  assert.equal(todos[1].itemStyle.color, colorSegmento(1))
  assert.notEqual(colorSegmento(0), colorSegmento(1))

  const soloSegundo = seriesMapa(MAPA, { seleccionado: 1 })
  assert.equal(soloSegundo[0].itemStyle.color, COLOR_ATENUADO)
  assert.equal(soloSegundo[0].silent, true)
  assert.equal(soloSegundo[1].itemStyle.color, colorSegmento(1))
})

test('los resultados de búsqueda y la ficha abierta se marcan con un anillo', () => {
  const series = seriesMapa(MAPA, { resaltados: [12, 99], activo: 10 })
  const anillos = series.find((s) => s.id === 'resaltados')
  assert.deepEqual(anillos.data, [[4, 4, 12], [0, 0, 10]])
  assert.equal(anillos.symbolSize([0, 0, 10]), 22)
  assert.equal(anillos.symbolSize([4, 4, 12]), 16)
})

test('con muchos puntos usa el modo de gran volumen de ECharts', () => {
  const puntos = Array.from({ length: UMBRAL_GRANDE + 1 }, (_, i) => [i, i % 2, i, i])
  const series = seriesMapa({ ...MAPA, puntos })
  assert.equal(series[0].large, true)
  assert.equal(series[0].symbolSize, 4)
  assert.equal(seriesMapa(MAPA)[0].large, false)
})

test('el zoom a un contribuyente queda dentro de 0 a 100 en ambos ejes', () => {
  const zoom = zoomA(MAPA, 13)
  for (const eje of [zoom.x, zoom.y]) {
    assert.ok(eje.start >= 0 && eje.end <= 100 && eje.end > eje.start)
  }
  assert.equal(zoomA(MAPA, 999), null)
})

test('la ficha compara cada variable con la mediana del segmento', () => {
  const filas = filasFicha(MAPA.variables, { valores: { pctATiempo: 93.75, mesesSinPago: 1.24 }, medianasSegmento: { pctATiempo: 100, mesesSinPago: 8.4 } })
  assert.deepEqual(filas.map((f) => [f.etiqueta, f.valor, f.mediana]), [
    ['Cuotas pagadas a tiempo', '94 %', '100 %'],
    ['Meses desde el último pago', '1.2 meses', '8.4 meses'],
  ])
  assert.equal(formatearValor(null, '%'), '—')
  assert.equal(formatearValor(-147.4, 'días'), '-147 días')
  assert.equal(porcentajeVarianza(MAPA.modelo), 85)
})

test('el servicio pide el mapa, la ficha y la búsqueda a las rutas del backend', async (t) => {
  const rutas = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    rutas.push(String(url))
    return { ok: true, status: 200, json: async () => ({}) }
  })
  await obtenerMapaSegmentos()
  await obtenerMapaSegmentos({ idModelo: 9 })
  await obtenerFichaMapa(12)
  await buscarEnMapa('garcía torres', { idModelo: 9 })
  assert.deepEqual(rutas.map((r) => r.replace(/^.*\/api/, '')), [
    '/segmentos/mapa',
    '/segmentos/mapa?idModelo=9',
    '/segmentos/mapa/asignaciones/12',
    '/segmentos/mapa/busqueda?q=garc%C3%ADa+torres&idModelo=9',
  ])
})
