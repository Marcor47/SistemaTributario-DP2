import assert from 'node:assert/strict'
import test from 'node:test'
import {
  FILTROS_PREDIOS_INICIALES,
  TAMANIOS_PAGINA,
  filtrarPredios,
  opcionesDePredios,
  paginarPredios,
} from '../src/utils/predios.js'

const PREDIOS = Array.from({ length: 11 }, (_, indice) => ({
  codigoPredio: `021390-${String(indice + 1).padStart(2, '0')}`,
  ubicacion: indice === 1 ? 'Av. Universitaria 3850, San Miguel' : `Calle Los Álamos ${indice + 1}, San Miguel`,
  tipoPredio: indice < 2 ? 'URBANO' : indice < 4 ? 'RUSTICO' : 'OTRO',
  usoPredio: indice % 2 === 0 ? 'Comercio' : 'Vivienda',
  condicionPropiedad: indice < 3 ? 'PROPIETARIO' : indice < 6 ? 'COPROPIETARIO' : 'POSESIONARIO',
}))

test('HU001: el filtro busca por código de predio sin distinguir mayúsculas', () => {
  assert.deepEqual(filtrarPredios(PREDIOS, '021390-02').map((p) => p.codigoPredio), ['021390-02'])
  assert.deepEqual(filtrarPredios(PREDIOS, '  021390-02  ').map((p) => p.codigoPredio), ['021390-02'])
})

test('HU001: el filtro busca por dirección ignorando tildes y mayúsculas', () => {
  assert.deepEqual(filtrarPredios(PREDIOS, 'UNIVERSITARIA').map((p) => p.codigoPredio), ['021390-02'])
  assert.equal(filtrarPredios(PREDIOS, 'alamos').length, 10)
})

test('HU001: una búsqueda vacía devuelve todos los predios', () => {
  assert.equal(filtrarPredios(PREDIOS, '').length, 11)
  assert.equal(filtrarPredios(PREDIOS, '   ').length, 11)
})

test('HU001: las opciones de filtro son únicas y tienen etiquetas legibles', () => {
  const opciones = opcionesDePredios(PREDIOS)

  assert.deepEqual(opciones.tipos.map((opcion) => opcion.valor), ['OTRO', 'RUSTICO', 'URBANO'])
  assert.deepEqual(opciones.tipos.map((opcion) => opcion.etiqueta), ['Otro', 'Rústico', 'Urbano'])
  assert.deepEqual(opciones.usos.map((opcion) => opcion.valor), ['Comercio', 'Vivienda'])
  assert.deepEqual(
    opciones.condiciones.map((opcion) => opcion.valor),
    ['COPROPIETARIO', 'POSESIONARIO', 'PROPIETARIO'],
  )
})

test('HU001: filtra por tipo, uso y condición de propiedad', () => {
  assert.deepEqual(
    filtrarPredios(PREDIOS, '', { tipo: 'URBANO' }).map((p) => p.codigoPredio),
    ['021390-01', '021390-02'],
  )
  assert.deepEqual(
    filtrarPredios(PREDIOS, '', { uso: 'Comercio' }).map((p) => p.codigoPredio),
    ['021390-01', '021390-03', '021390-05', '021390-07', '021390-09', '021390-11'],
  )
  assert.deepEqual(
    filtrarPredios(PREDIOS, '', { condicion: 'PROPIETARIO' }).map((p) => p.codigoPredio),
    ['021390-01', '021390-02', '021390-03'],
  )
})

test('HU001: los filtros se combinan entre sí y con la búsqueda', () => {
  assert.deepEqual(
    filtrarPredios(PREDIOS, '', { tipo: 'URBANO', uso: 'Comercio', condicion: 'PROPIETARIO' })
      .map((p) => p.codigoPredio),
    ['021390-01'],
  )
  assert.deepEqual(
    filtrarPredios(PREDIOS, 'UNIVERSITARIA', { tipo: 'URBANO' }).map((p) => p.codigoPredio),
    ['021390-02'],
  )
  assert.deepEqual(filtrarPredios(PREDIOS, 'UNIVERSITARIA', { tipo: 'RUSTICO' }), [])
  assert.equal(filtrarPredios(PREDIOS, '', FILTROS_PREDIOS_INICIALES).length, 11)
})

test('HU001: la paginación por defecto muestra 5 predios y calcula las páginas', () => {
  const primera = paginarPredios(PREDIOS, 1, TAMANIOS_PAGINA[0])
  assert.equal(primera.visibles.length, 5)
  assert.equal(primera.paginaActual, 1)
  assert.equal(primera.totalPaginas, 3)
  assert.equal(primera.inicio, 0)
  assert.equal(primera.fin, 5)

  const ultima = paginarPredios(PREDIOS, 3, TAMANIOS_PAGINA[0])
  assert.equal(ultima.visibles.length, 1)
  assert.equal(ultima.visibles[0].codigoPredio, '021390-11')
  assert.equal(ultima.inicio, 10)
  assert.equal(ultima.fin, 11)
})

test('HU001: acepta los tamaños de página 10, 20 y 100', () => {
  assert.equal(paginarPredios(PREDIOS, 1, 10).totalPaginas, 2)
  assert.equal(paginarPredios(PREDIOS, 2, 10).visibles.length, 1)
  assert.equal(paginarPredios(PREDIOS, 1, 20).totalPaginas, 1)
  assert.equal(paginarPredios(PREDIOS, 1, 100).visibles.length, 11)
})

test('HU001: una página fuera de rango se ajusta a la última disponible', () => {
  const paginacion = paginarPredios(PREDIOS, 99, 5)
  assert.equal(paginacion.paginaActual, 3)
  assert.equal(paginacion.visibles[0].codigoPredio, '021390-11')
})

test('HU001: una lista vacía mantiene una sola página sin filas', () => {
  const paginacion = paginarPredios([], 4, 5)
  assert.deepEqual(paginacion.visibles, [])
  assert.equal(paginacion.paginaActual, 1)
  assert.equal(paginacion.totalPaginas, 1)
  assert.equal(paginacion.inicio, 0)
  assert.equal(paginacion.fin, 0)
})
