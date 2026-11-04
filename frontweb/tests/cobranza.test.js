import assert from 'node:assert/strict'
import test from 'node:test'
import { FILTROS_COBRANZA_INICIALES, filtrarObligaciones, opcionesDeCobranza } from '../src/utils/cobranza.js'
import { TAMANIOS_PAGINA, paginar } from '../src/utils/paginacion.js'

const OBLIGACIONES = [
  {
    id: 1,
    periodo: '2025-T4',
    tributoDescripcion: 'Arbitrios Municipales',
    etapaActual: 'ESPERA_LEGAL',
    etapaNombre: 'En espera legal',
  },
  {
    id: 2,
    periodo: '2025-T4',
    tributoDescripcion: 'Impuesto Predial',
    etapaActual: 'COACTIVA',
    etapaNombre: 'Exigible en vía coactiva',
  },
  {
    id: 3,
    periodo: '2026-T1',
    tributoDescripcion: 'Arbitrios Municipales',
    etapaActual: 'MORA_TEMPRANA',
    etapaNombre: 'Mora temprana',
  },
  {
    id: 4,
    periodo: '2026-T1',
    tributoDescripcion: 'Impuesto Predial',
    etapaActual: 'COACTIVA',
    etapaNombre: 'Exigible en vía coactiva',
  },
  {
    id: 5,
    periodo: '2026-T2',
    tributoDescripcion: 'Arbitrios Municipales',
    etapaActual: 'MORA_TEMPRANA',
    etapaNombre: 'Mora temprana',
  },
]

test('HU013: los filtros se construyen con valores únicos de las obligaciones', () => {
  const opciones = opcionesDeCobranza(OBLIGACIONES)

  assert.deepEqual(opciones.periodos, ['2025-T4', '2026-T1', '2026-T2'])
  assert.deepEqual(opciones.tributos, ['Arbitrios Municipales', 'Impuesto Predial'])
  assert.deepEqual(
    opciones.etapas.map((etapa) => etapa.valor).sort(),
    ['COACTIVA', 'ESPERA_LEGAL', 'MORA_TEMPRANA'],
  )
  assert.equal(opciones.etapas.length, 3)
  assert.ok(opciones.etapas.some((etapa) => etapa.etiqueta === 'Exigible en vía coactiva'))
})

test('HU013: un filtro vacío devuelve todas las obligaciones', () => {
  assert.equal(filtrarObligaciones(OBLIGACIONES, FILTROS_COBRANZA_INICIALES).length, 5)
})

test('HU013: los filtros de periodo, etapa y tributo se combinan con AND', () => {
  assert.deepEqual(
    filtrarObligaciones(OBLIGACIONES, { periodo: '2026-T1' }).map((o) => o.id),
    [3, 4],
  )
  assert.deepEqual(
    filtrarObligaciones(OBLIGACIONES, { etapa: 'COACTIVA' }).map((o) => o.id),
    [2, 4],
  )
  assert.deepEqual(
    filtrarObligaciones(OBLIGACIONES, { tributo: 'Arbitrios Municipales' }).map((o) => o.id),
    [1, 3, 5],
  )
  assert.deepEqual(
    filtrarObligaciones(OBLIGACIONES, {
      periodo: '2026-T1',
      etapa: 'COACTIVA',
      tributo: 'Impuesto Predial',
    }).map((o) => o.id),
    [4],
  )
  assert.deepEqual(
    filtrarObligaciones(OBLIGACIONES, { periodo: '2026-T1', etapa: 'COACTIVA', tributo: 'Arbitrios Municipales' }),
    [],
  )
})

test('HU013: la paginación usa 5 filas por defecto y permite 10, 20 y 100', () => {
  const obligaciones = Array.from({ length: 26 }, (_, indice) => ({ id: indice + 1 }))

  const primera = paginar(obligaciones, 1, TAMANIOS_PAGINA[0])
  assert.equal(primera.visibles.length, 5)
  assert.equal(primera.totalPaginas, 6)
  assert.equal(primera.inicio, 0)
  assert.equal(primera.fin, 5)

  const ultima = paginar(obligaciones, 6, TAMANIOS_PAGINA[0])
  assert.equal(ultima.visibles.length, 1)
  assert.equal(ultima.visibles[0].id, 26)

  assert.equal(paginar(obligaciones, 1, 10).totalPaginas, 3)
  assert.equal(paginar(obligaciones, 1, 20).totalPaginas, 2)
  assert.equal(paginar(obligaciones, 1, 100).totalPaginas, 1)
  assert.equal(paginar(obligaciones, 99, 5).paginaActual, 6)
})
