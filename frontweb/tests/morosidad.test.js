import assert from 'node:assert/strict'
import test from 'node:test'
import { calcularIndice, tieneAlertaTotal, etiquetaTributo, DATOS_DEMO, META_DEMO, UMBRAL_DESVIO } from '../src/mocks/morosidad.js'

/*
 * HU008 (RF-009): Tests unitarios del índice de morosidad.
 * Verifican la lógica de cálculo, las etiquetas y la detección de alertas
 * sin depender del backend ni del DOM (sólo Node.js test runner).
 */

test('HU008 RF-009: calcularIndice aplica la fórmula oficial correctamente', () => {
  // 420 / 900 × 100 = 46.6667 (muestra de control del test de integración)
  const resultado = calcularIndice(420, 900)
  assert.ok(Math.abs(resultado - 46.6667) < 0.001, `Esperado ~46.6667, obtenido ${resultado}`)
})

test('HU008 RF-009: calcularIndice devuelve null cuando el monto emitido es cero', () => {
  assert.equal(calcularIndice(100, 0), null)
  assert.equal(calcularIndice(0, 0), null)
})

test('HU008 RF-009: calcularIndice devuelve 0 cuando el monto vencido es cero', () => {
  assert.equal(calcularIndice(0, 500), 0)
})

test('HU008 RF-009: los datos demo tienen índice total, por tributo y por zona', () => {
  assert.ok(DATOS_DEMO.total > 0, 'El índice total debe ser mayor que 0')
  assert.ok(DATOS_DEMO.porTributo.length > 0, 'Debe haber desglose por tributo')
  assert.ok(DATOS_DEMO.porZona.length > 0, 'Debe haber desglose por zona')
})

test('HU008 RF-009: los datos demo incluyen el Impuesto Predial y los arbitrios como un solo tributo', () => {
  const tributos = DATOS_DEMO.porTributo.map((f) => f.valorAlcance)
  assert.deepEqual(tributos, ['PREDIAL', 'ARBITRIOS'])
})

test('HU008 RF-010: la meta de demostración está configurada correctamente', () => {
  assert.equal(META_DEMO, 12.5)
  assert.equal(DATOS_DEMO.meta, 12.5)
})

test('HU008 RF-052: el umbral de alerta de desvío por defecto es 10 %', () => {
  assert.equal(UMBRAL_DESVIO, 10)
})

test('HU008 RF-052: tieneAlertaTotal detecta alerta activa para el TOTAL', () => {
  assert.equal(tieneAlertaTotal(DATOS_DEMO), true)
})

test('HU008 RF-052: tieneAlertaTotal devuelve false cuando no hay alertas activas', () => {
  const sinAlertas = { ...DATOS_DEMO, alertas: [] }
  assert.equal(tieneAlertaTotal(sinAlertas), false)
})

test('HU008 RF-052: tieneAlertaTotal devuelve false cuando la alerta está inactiva', () => {
  const alertaInactiva = { ...DATOS_DEMO, alertas: [{ ...DATOS_DEMO.alertas[0], activa: false }] }
  assert.equal(tieneAlertaTotal(alertaInactiva), false)
})

test('HU008: etiquetaTributo devuelve el nombre legible de cada tributo', () => {
  assert.equal(etiquetaTributo('PREDIAL'), 'Impuesto Predial')
  assert.equal(etiquetaTributo('ARBITRIOS'), 'Arbitrios Municipales')
})

test('HU008: etiquetaTributo devuelve la clave original para claves desconocidas', () => {
  assert.equal(etiquetaTributo('OTRO'), 'OTRO')
})

test('HU008: cada tributo del demo tiene montos emitido y vencido positivos', () => {
  for (const fila of DATOS_DEMO.porTributo) {
    assert.ok(fila.montoEmitido > 0, `montoEmitido inválido para ${fila.valorAlcance}`)
    assert.ok(fila.montoVencido >= 0, `montoVencido inválido para ${fila.valorAlcance}`)
    const indiceCalculado = calcularIndice(fila.montoVencido, fila.montoEmitido)
    assert.ok(
      Math.abs(indiceCalculado - fila.indice) < 0.01,
      `Índice inconsistente para ${fila.valorAlcance}: calculado ${indiceCalculado}, declarado ${fila.indice}`,
    )
  }
})

test('HU008: el lote demo tiene los campos obligatorios de LoteETL (RF-038)', () => {
  const { lote } = DATOS_DEMO
  assert.ok(lote.idLote, 'Falta idLote')
  assert.ok(lote.fechaCorte, 'Falta fechaCorte')
  assert.ok(lote.anio, 'Falta anio')
  assert.ok(lote.numeroPeriodo, 'Falta numeroPeriodo')
  assert.ok(lote.periodo, 'Falta etiqueta de periodo')
})
