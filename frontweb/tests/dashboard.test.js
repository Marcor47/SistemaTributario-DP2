import assert from 'node:assert/strict'
import test from 'node:test'
import { listarAlertasDesvio } from '../src/services/alertaDesvioService.js'
import {
  etiquetaAlcance,
  etiquetaPeriodo,
  etiquetaSentido,
  esPeriodoActual,
  explicarDesvio,
  filtrarAlertasDelPeriodo,
  formatearDesvio,
  formatearPorcentaje,
  formatearValorIndicador,
  indexarAlertasPorMeta,
  resumirAlertas,
} from '../src/utils/alertas.js'
import { fechaHora } from '../src/utils/formato.js'

const ALERTA_MOROSIDAD = {
  idAlerta: 1,
  indicadorCodigo: 'INDICE_MOROSIDAD',
  indicadorNombre: 'Índice de morosidad',
  unidadMedida: 'PORCENTAJE',
  sentido: 'DESCENDENTE',
  periodo: '2026-10',
  alcance: 'TOTAL',
  valorAlcance: null,
  valorObservado: 14.8,
  valorMeta: 12.5,
  desvioPorcentual: 18.4,
  umbralDesvio: 10,
  idLote: 1,
  fechaCorte: '2026-10-18',
  fechaGeneracion: '2026-10-18T02:08:00',
}

const ALERTA_RECAUDACION = {
  ...ALERTA_MOROSIDAD,
  idAlerta: 2,
  indicadorCodigo: 'PORC_RECAUDACION_EMISION',
  indicadorNombre: 'Recaudación sobre la emisión',
  sentido: 'ASCENDENTE',
  valorObservado: 72,
  valorMeta: 85,
  desvioPorcentual: -15.29,
  fechaGeneracion: '2026-10-18T02:09:00',
}

test('HU039: consulta las alertas activas con y sin filtro de indicador', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(url)
    return new Response(JSON.stringify([]))
  })

  await listarAlertasDesvio()
  await listarAlertasDesvio({ indicadorCodigo: 'INDICE_MOROSIDAD' })

  assert.equal(new URL(urls[0]).search, '')
  assert.equal(new URL(urls[1]).searchParams.get('indicadorCodigo'), 'INDICE_MOROSIDAD')
})

test('HU039: una lista vacía es un estado válido del servicio', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify([])))
  assert.deepEqual(await listarAlertasDesvio(), [])
})

test('HU039: solo considera alertas del periodo vigente', () => {
  const hoy = new Date(2026, 9, 27)

  assert.equal(esPeriodoActual('2026-10', hoy), true)
  assert.equal(esPeriodoActual('2026-09', hoy), false)
  assert.equal(esPeriodoActual('2026-T4', hoy), true)
  assert.equal(esPeriodoActual('2026-T3', hoy), false)
  assert.equal(esPeriodoActual('2026', hoy), true)
  assert.equal(esPeriodoActual('2025', hoy), false)

  const vigentes = filtrarAlertasDelPeriodo([
    ALERTA_MOROSIDAD,
    { ...ALERTA_RECAUDACION, periodo: '2026-09' },
    { ...ALERTA_RECAUDACION, idAlerta: 3, periodo: '2025-10' },
  ], hoy)

  assert.deepEqual(vigentes.map((alerta) => alerta.idAlerta), [1])
})

test('HU039: presenta el periodo de forma legible', () => {
  assert.equal(etiquetaPeriodo('2026-10'), 'Octubre 2026')
  assert.equal(etiquetaPeriodo('2026-T4'), 'Trimestre 4 de 2026')
  assert.equal(etiquetaPeriodo('2026'), '2026')
  assert.equal(etiquetaPeriodo(null), '—')
})

test('HU039: indexa las alertas por meta para marcarlas en la tabla', () => {
  const mapa = indexarAlertasPorMeta([
    { ...ALERTA_MOROSIDAD, idMeta: 5 },
    { ...ALERTA_RECAUDACION, idMeta: 2 },
    { ...ALERTA_RECAUDACION, idAlerta: 99, idMeta: null },
  ])
  assert.equal(mapa.size, 2)
  assert.equal(mapa.get(5).idAlerta, 1)
  assert.equal(mapa.get(2).idAlerta, 2)
  assert.equal(indexarAlertasPorMeta(null).size, 0)
})

test('HU039: presenta el desvío con signo y el umbral con dos decimales', () => {
  assert.match(formatearDesvio(18.4), /^\+18[,.]40 %$/)
  assert.match(formatearDesvio(-15.29), /^-15[,.]29 %$/)
  assert.match(formatearDesvio(0), /^0[,.]00 %$/)
  assert.equal(formatearDesvio(null), '—')
  assert.match(formatearPorcentaje(10), /^10[,.]00 %$/)
})

test('HU039: muestra el valor observado y la meta con su unidad', () => {
  assert.match(formatearValorIndicador(14.8, 'PORCENTAJE'), /^14[,.]80 %$/)
  assert.match(formatearValorIndicador(1250000.5, 'SOLES'), /1[,.]250[,.]000[,.]50/)
  assert.equal(formatearValorIndicador(1250, 'CANTIDAD').replace(/[,.]/g, ''), '1250')
  assert.equal(formatearValorIndicador(null, 'PORCENTAJE'), '—')
})

test('HU039: explica el desvío según el sentido del indicador', () => {
  const morosidad = explicarDesvio(ALERTA_MOROSIDAD)
  assert.match(morosidad, /18[,.]40 % por encima de la meta/)
  assert.match(morosidad, /indicador descendente/)

  const recaudacion = explicarDesvio(ALERTA_RECAUDACION)
  assert.match(recaudacion, /15[,.]29 % por debajo de la meta/)
  assert.match(recaudacion, /indicador ascendente/)

  assert.match(explicarDesvio({ sentido: 'DESCENDENTE', desvioPorcentual: 0 }), /coincide con la meta/)
  assert.match(explicarDesvio({}), /No se pudo determinar/)
  assert.match(explicarDesvio({ sentido: 'DESCENDENTE', desvioPorcentual: null }), /No se pudo determinar/)
})

test('HU039: resume alertas, indicadores distintos y la última fecha de corte', () => {
  assert.deepEqual(resumirAlertas([]), { total: 0, indicadores: 0, ultimaCorte: null })
  assert.deepEqual(resumirAlertas([ALERTA_MOROSIDAD, ALERTA_RECAUDACION]), {
    total: 2,
    indicadores: 2,
    ultimaCorte: '2026-10-18',
  })

  const conFechas = [
    { ...ALERTA_MOROSIDAD, fechaCorte: '2026-10-17' },
    { ...ALERTA_RECAUDACION, fechaCorte: '2026-10-19' },
  ]
  assert.equal(resumirAlertas(conFechas).ultimaCorte, '2026-10-19')
})

test('HU039: presenta el alcance, el sentido y la fecha de generación', () => {
  assert.equal(etiquetaAlcance('TOTAL', null), 'Total municipal')
  assert.equal(etiquetaAlcance('TRIBUTO', 'IMPUESTO_PREDIAL'), 'Impuesto Predial')
  assert.equal(etiquetaAlcance('ZONA', 'Z01'), 'Z01')
  assert.equal(etiquetaSentido('DESCENDENTE'), 'Descendente')
  assert.equal(etiquetaSentido('ASCENDENTE'), 'Ascendente')
  assert.equal(fechaHora('2026-10-18T02:08:00'), '18/10/2026 02:08')
  assert.equal(fechaHora('2026-10-18 02:08:00'), '18/10/2026 02:08')
  assert.equal(fechaHora('2026-10-18'), '18/10/2026')
  assert.equal(fechaHora(null), '—')
})
