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
} from '../src/services/recordatorioService.js'

const json = (cuerpo, estado = 200) => new Response(JSON.stringify(cuerpo), { status: estado })

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

test('HU016: la configuración se lee y se guarda en el servidor con la anticipación validada', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    llamadas.push({ ruta: new URL(url).pathname, metodo: opciones.method ?? 'GET', cuerpo: opciones.body })
    if (String(url).endsWith('/auth/interno/csrf')) return json({ headerName: 'X-XSRF-TOKEN', token: 'token' })
    return json({ diasAnticipacion: 7, minimo: 1, maximo: 10, valorPorDefecto: DIAS_RECORDATORIO_POR_DEFECTO, actualizadoEn: '2026-11-14T09:00:00', actualizadoPor: 'María Fernanda Quispe Rojas' })
  })
  await assert.rejects(guardarConfiguracionRecordatorio(11), { codigo: 'DIAS_RECORDATORIO_INVALIDO' })
  const guardada = await guardarConfiguracionRecordatorio(7)
  assert.equal(guardada.diasAnticipacion, 7)
  const enviada = llamadas.find((l) => l.metodo === 'PUT')
  assert.equal(enviada.ruta, '/api/recordatorios-vencimiento/configuracion')
  assert.deepEqual(JSON.parse(enviada.cuerpo), { diasAnticipacion: 7 })
  const leida = await obtenerConfiguracionRecordatorio()
  assert.equal(leida.valorPorDefecto, DIAS_RECORDATORIO_POR_DEFECTO)
})

test('HU016: el estado del recordatorio de una cuota viene del servidor', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    assert.equal(new URL(url).pathname, '/api/recordatorios-vencimiento/obligaciones/10')
    return json({ idAviso: 55, idObligacion: '10', estado: 'ENVIADO', correoDestino: 'mquispe.rojas@gmail.com', fechaProgramada: '2026-11-20T08:00:00', fechaEnvio: '2026-11-20T08:00:05', diasAnticipacion: 5 })
  })
  const recordatorio = await obtenerRecordatorioObligacion({ id: 10 })
  assert.equal(recordatorio.estado, 'ENVIADO')
  assert.equal(recordatorio.diasAnticipacion, 5)
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

test('HU016: una obligación sin identificador no consulta al servidor', async () => {
  await assert.rejects(obtenerRecordatorioObligacion({}), { codigo: 'OBLIGACION_INVALIDA' })
})
