import assert from 'node:assert/strict'
import test from 'node:test'
import {
  confirmarPagoPortal,
  iniciarPagoPortal,
  obtenerEstadoCuentaPortal,
  obtenerHistorialPagosPortal,
} from '../src/services/portalCuentaService.js'

test('HU014 RF-018: obtenerEstadoCuentaPortal devuelve los datos del contribuyente propio sin requerir ID en la URL (prevención IDOR)', async () => {
  const cuenta = await obtenerEstadoCuentaPortal()
  assert.ok(cuenta.contribuyente, 'Debe incluir los datos del contribuyente')
  assert.equal(cuenta.contribuyente.codigo, '021390')
  assert.equal(cuenta.contribuyente.tipoDoc, 'RUC')
  assert.equal(cuenta.contribuyente.doc, '20456781239')
  assert.ok(cuenta.fechaCorte, 'Debe incluir la fecha de corte')
  assert.equal(cuenta.desactualizado, false)
  assert.equal(cuenta.esUltimaCopia, false)
  assert.ok(Array.isArray(cuenta.filas), 'Debe incluir la lista de obligaciones')
  assert.ok(cuenta.filas.length > 0, 'Debe tener obligaciones cargadas')
})

test('HU014 RNF-002: cuando SAMNET no responde, la contingencia entrega la última copia con esUltimaCopia = true', async () => {
  const contingencia = await obtenerEstadoCuentaPortal({ simularCaida: true })
  assert.equal(contingencia.desactualizado, true)
  assert.equal(contingencia.esUltimaCopia, true)
  assert.equal(contingencia.fechaCorte, '2026-10-16', 'La copia de contingencia conserva su fecha de corte anterior')
  assert.ok(contingencia.filas.length > 0)
})

test('HU014 RF-018: las multas y sanciones se presentan obligatoriamente por separado de los tributos', async () => {
  const cuenta = await obtenerEstadoCuentaPortal()
  const predial = cuenta.filas.filter((f) => f.categoria === 'PREDIAL')
  const arbitrios = cuenta.filas.filter((f) => f.categoria === 'ARBITRIOS')
  const multas = cuenta.filas.filter((f) => f.categoria === 'MULTAS')

  assert.ok(predial.length > 0, 'Debe contener obligaciones de Impuesto Predial')
  assert.ok(arbitrios.length > 0, 'Debe contener obligaciones de Arbitrios Municipales')
  assert.ok(multas.length > 0, 'Debe contener multas y sanciones separadas')

  // Verificar que ninguna multa está clasificada como impuesto predial o arbitrio
  for (const m of multas) {
    assert.notEqual(m.tributo, 'Impuesto Predial')
    assert.notEqual(m.tributo, 'Limpieza Pública')
    assert.notEqual(m.categoria, 'PREDIAL')
    assert.notEqual(m.categoria, 'ARBITRIOS')
  }
})

test('HU014 RF-014: las obligaciones vencidas muestran su etapa de cobranza inductiva', async () => {
  const cuenta = await obtenerEstadoCuentaPortal()
  const vencidas = cuenta.filas.filter((f) => f.estado === 'Vencida')
  assert.ok(vencidas.length > 0, 'Debe haber cuotas vencidas para comprobar la etapa')

  for (const v of vencidas) {
    assert.ok(v.etapaCobranza, `La cuota vencida ${v.id} debe tener una etapa de cobranza`)
    assert.ok(
      ['Mora temprana', 'Campaña persuasiva', 'Valor emitido', 'Espera legal', 'Exigible en vía coactiva'].includes(
        v.etapaCobranza,
      ),
      `Etapa desconocida: ${v.etapaCobranza}`,
    )
  }
})

test('HU014 RF-062: obtenerHistorialPagosPortal devuelve fecha, canal, monto y beneficio aplicado', async () => {
  const pagos = await obtenerHistorialPagosPortal()
  assert.ok(Array.isArray(pagos))
  assert.ok(pagos.length > 0)

  for (const p of pagos) {
    assert.ok(p.fechaPago, 'Debe incluir fecha de pago')
    assert.ok(['En línea', 'Ventanilla'].includes(p.canal), `Canal inválido: ${p.canal}`)
    assert.ok(typeof p.montoPagado === 'number' && p.montoPagado > 0, 'Monto pagado debe ser positivo')
    assert.ok(typeof p.beneficioAplicado === 'number', 'Debe reportar beneficio aplicado')
  }
})

test('HU014 / HU022: iniciarPagoPortal y confirmarPagoPortal gestionan la orden de pago de cuotas', async () => {
  const orden = await iniciarPagoPortal([5, 6])
  assert.ok(orden.idTransaccion, 'Debe generar un id de transacción')
  assert.ok(['INICIADA', 'PENDIENTE'].includes(orden.estado))

  const confirmacion = await confirmarPagoPortal(orden.idTransaccion)
  assert.equal(confirmacion.idTransaccion, orden.idTransaccion)
  assert.equal(confirmacion.estado, 'CONFIRMADA')
})
