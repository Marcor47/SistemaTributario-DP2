import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import {
  DIAS_RECORDATORIO_POR_DEFECTO,
  calcularFechaProgramada,
  descripcionRecordatorio,
  estadoRecordatorio,
  fechaRecordatorio,
  textoAnticipacion,
  validarDiasRecordatorio,
} from '../src/utils/recordatorios.js'
import {
  guardarConfiguracionRecordatorio,
  obtenerConfiguracionRecordatorio,
  obtenerRecordatorioObligacion,
  reiniciarDatosSimulados,
} from '../src/services/recordatorioService.js'

function isoEnDias(dias) {
  const fecha = new Date()
  fecha.setUTCDate(fecha.getUTCDate() + dias)
  return fecha.toISOString().slice(0, 10)
}

test('HU016: los días de anticipación son un entero entre 1 y 10', () => {
  for (const valor of [1, '1', 5, '10', 10]) {
    assert.equal(validarDiasRecordatorio(valor).valido, true, `Debe aceptar ${valor}`)
  }
  for (const valor of ['', null, undefined, 0, 11, -1, '5.5', 'abc', '  ']) {
    assert.equal(validarDiasRecordatorio(valor).valido, false, `Debe rechazar ${valor}`)
  }
  assert.match(validarDiasRecordatorio(11).mensaje, /entre 1 y 10/)
})

test('HU016: la fecha programada se calcula N días antes del vencimiento', () => {
  assert.equal(calcularFechaProgramada('2026-11-30', 5), '2026-11-25')
  assert.equal(calcularFechaProgramada('2026-01-03', 5), '2025-12-29')
  assert.equal(calcularFechaProgramada('2026-03-01', 0), '2026-03-01')
  assert.equal(calcularFechaProgramada('2026-02-30', 5), null)
  assert.equal(calcularFechaProgramada('', 5), null)
  assert.equal(calcularFechaProgramada('2026-11-30', 'x'), null)
})

test('HU016: los estados y textos del recordatorio son legibles', () => {
  assert.deepEqual(estadoRecordatorio('ENVIADO'), { texto: 'Enviado', clase: 'ok' })
  assert.deepEqual(estadoRecordatorio('SIN_CORREO'), { texto: 'Sin correo registrado', clase: 'neutro' })
  assert.deepEqual(estadoRecordatorio('OTRO'), { texto: 'OTRO', clase: 'neutro' })
  assert.equal(textoAnticipacion(1), '1 día antes del vencimiento')
  assert.equal(textoAnticipacion(5), '5 días antes del vencimiento')
  assert.equal(textoAnticipacion(null), '—')
  assert.equal(
    fechaRecordatorio({ estado: 'ENVIADO', fechaEnvio: '2026-11-25T08:00:00' }).etiqueta,
    'Fecha de envío',
  )
  assert.equal(
    fechaRecordatorio({ estado: 'FALLIDO', fechaEnvio: '2026-11-25T08:00:00' }).etiqueta,
    'Fecha del intento',
  )
  assert.equal(
    fechaRecordatorio({ estado: 'PROGRAMADO', fechaProgramada: '2026-11-25T08:00:00' }).etiqueta,
    'Fecha programada',
  )
  assert.equal(
    fechaRecordatorio({ estado: 'SIN_CORREO', fechaProgramada: '2026-11-25T08:00:00' }).etiqueta,
    'Fecha prevista',
  )
  assert.match(descripcionRecordatorio({ estado: 'SIN_CORREO' }), /no tiene correo registrado/)
  assert.match(descripcionRecordatorio({ estado: 'NO_ENVIADO' }), /No se registró un envío/)
})

test('HU016: la configuración inicia en 5 días y persiste el cambio', async () => {
  reiniciarDatosSimulados()
  const inicial = await obtenerConfiguracionRecordatorio()
  assert.equal(inicial.diasAnticipacion, DIAS_RECORDATORIO_POR_DEFECTO)
  assert.equal(inicial.valorPorDefecto, DIAS_RECORDATORIO_POR_DEFECTO)
  assert.equal(inicial.minimo, 1)
  assert.equal(inicial.maximo, 10)

  await assert.rejects(guardarConfiguracionRecordatorio(11), {
    codigo: 'DIAS_RECORDATORIO_INVALIDO',
    estado: 400,
  })

  const actualizada = await guardarConfiguracionRecordatorio(7, { usuario: 'Gerencia de Rentas' })
  assert.equal(actualizada.diasAnticipacion, 7)
  assert.equal(actualizada.actualizadoPor, 'Gerencia de Rentas')
  assert.ok(actualizada.actualizadoEn)
  assert.equal((await obtenerConfiguracionRecordatorio()).diasAnticipacion, 7)

  reiniciarDatosSimulados()
  assert.equal((await obtenerConfiguracionRecordatorio()).diasAnticipacion, DIAS_RECORDATORIO_POR_DEFECTO)
})

test('HU016: el aviso simulado es determinista y conserva la anticipación con la que se generó', async () => {
  reiniciarDatosSimulados()
  await guardarConfiguracionRecordatorio(5)

  const obligacion = {
    id: 10,
    fechaVencimiento: isoEnDias(-40),
    contribuyenteCodigo: '021390',
  }
  const primero = await obtenerRecordatorioObligacion(obligacion)
  assert.equal(primero.estado, 'ENVIADO')
  assert.equal(primero.diasAnticipacion, 5)
  assert.equal(primero.idAviso, 'SIM-10')
  assert.equal(primero.idObligacion, '10')
  assert.ok(primero.fechaEnvio)
  assert.match(primero.correoDestino, /@correo\.test$/)

  await guardarConfiguracionRecordatorio(9)
  const historico = await obtenerRecordatorioObligacion(obligacion)
  assert.equal(historico.diasAnticipacion, 5, 'Un cambio de N no reescribe los avisos ya generados')

  const nuevo = await obtenerRecordatorioObligacion({
    id: 11,
    fechaVencimiento: isoEnDias(-40),
    contribuyenteCodigo: '021390',
  })
  assert.equal(nuevo.diasAnticipacion, 9)
  assert.equal(nuevo.estado, 'ENVIADO')

  reiniciarDatosSimulados()
})

test('HU016: el mock representa avisos sin correo, fallidos y programados', async () => {
  reiniciarDatosSimulados()
  await guardarConfiguracionRecordatorio(5)

  const sinCorreo = await obtenerRecordatorioObligacion({ id: 13, fechaVencimiento: isoEnDias(-40) })
  assert.equal(sinCorreo.estado, 'SIN_CORREO')
  assert.equal(sinCorreo.correoDestino, null)

  const fallido = await obtenerRecordatorioObligacion({ id: 12, fechaVencimiento: isoEnDias(-40) })
  assert.equal(fallido.estado, 'FALLIDO')
  assert.ok(fallido.fechaEnvio)

  const programado = await obtenerRecordatorioObligacion({ id: 14, fechaVencimiento: isoEnDias(30) })
  assert.equal(programado.estado, 'PROGRAMADO')
  assert.equal(programado.fechaEnvio, null)
  assert.ok(programado.fechaProgramada)

  reiniciarDatosSimulados()
})

test('HU016: la configuración tiene una ruta y un menú propios solo para el administrador', () => {
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8')
  const modulos = readFileSync(new URL('../src/utils/modulos.jsx', import.meta.url), 'utf8')
  const inicioModulo = modulos.indexOf("ruta: '/configuracion/recordatorios'")

  assert.match(app, /path="\/configuracion\/recordatorios"/)
  assert.match(app, /roles=\{\['ADMINISTRADOR'\]\}/)
  assert.match(app, /ConfiguracionRecordatorios/)
  assert.ok(inicioModulo >= 0)
  assert.match(modulos.slice(inicioModulo, inicioModulo + 500), /roles: \['ADMINISTRADOR'\]/)
  assert.doesNotMatch(modulos.slice(inicioModulo, inicioModulo + 500), /METAS:GESTIONAR/)
})

test('HU016: una obligación sin identificador no consulta el mock', async () => {
  await assert.rejects(obtenerRecordatorioObligacion({}), { codigo: 'OBLIGACION_INVALIDA' })
})
