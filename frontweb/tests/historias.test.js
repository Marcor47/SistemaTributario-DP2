import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { generarCuenta } from '../src/mocks/cuentas.js'
import { calcularRegularidad, generarRegularidad } from '../src/mocks/regularidad.js'
import { FORMULARIOS } from '../src/mocks/formularios.js'
import { obtenerRegularidad, tieneDatosRegularidad } from '../src/services/regularidadService.js'
import { descargarFormulario, obtenerFormularios } from '../src/services/formularioService.js'

test('HU010: cuenta pagos anticipados y al vencimiento, y conserva cuotas futuras en el denominador', () => {
  const filas = [
    { id: 'anticipada', periodo: '2026-T1', tributo: 'Predial', emitido: 100, pagado: 100, fechaVencimiento: '2026-03-31', fechaPago: '2026-03-25' },
    { id: 'puntual', periodo: '2026-T1', tributo: 'Limpieza', emitido: 100, pagado: 100, fechaVencimiento: '2026-03-31', fechaPago: '2026-03-31' },
    { id: 'atrasada', periodo: '2026-T1', tributo: 'Parques', emitido: 100, pagado: 100, fechaVencimiento: '2026-03-31', fechaPago: '2026-04-02' },
    { id: 'impaga', periodo: '2026-T2', tributo: 'Predial', emitido: 100, pagado: 0, fechaVencimiento: '2026-06-30', fechaPago: null },
    { id: 'futura', periodo: '2026-T4', tributo: 'Predial', emitido: 100, pagado: 0, fechaVencimiento: '2026-12-31', fechaPago: null },
    { id: 'otro-anio', periodo: '2025-T1', tributo: 'Predial', emitido: 100, pagado: 100, fechaVencimiento: '2025-03-31', fechaPago: '2025-03-31' },
  ]
  const resultado = calcularRegularidad(filas, 2026, '2026-10-19')
  assert.equal(resultado.emitidas, 5)
  assert.equal(resultado.aTiempo, 2)
  assert.equal(resultado.porcentaje, 40)
  assert.equal(resultado.conAtraso, 1)
  assert.equal(resultado.vencidasSinPagar, 1)
  assert.equal(resultado.porVencer, 1)
  assert.equal(filas[0].estado, undefined, 'El cálculo no modifica las cuotas de origen')
})

test('HU010: un pago parcial o posterior a la fecha de corte no cuenta como cuota pagada a tiempo', () => {
  const resultado = calcularRegularidad([
    { id: 'parcial', periodo: '2026-T1', tributo: 'Predial', emitido: 100, pagado: 50, fechaVencimiento: '2026-03-31', fechaPago: '2026-03-01' },
    { id: 'posterior', periodo: '2026-T4', tributo: 'Predial', emitido: 100, pagado: 100, fechaVencimiento: '2026-12-31', fechaPago: '2026-12-01' },
  ], 2026, '2026-10-19')
  assert.equal(resultado.aTiempo, 0)
  assert.equal(resultado.porcentaje, 0)
})

test('HU010: diferencia un año sin cuotas de una regularidad de cero', () => {
  assert.equal(generarRegularidad('033218', 2024).porcentaje, null)
  assert.equal(generarRegularidad('021390', 2026).porcentaje, 0)
  assert.equal(generarRegularidad('033218', 2026).porcentaje, 50)
  assert.equal(generarRegularidad('014502', 2026).porcentaje, 100)
})

test('HU010: un contribuyente real sin datos ficticios no rompe la ficha', () => {
  assert.equal(tieneDatosRegularidad('072405'), false)
  assert.equal(tieneDatosRegularidad('072845'), true)
  assert.deepEqual(generarCuenta('072405', '2026-10-19'), [])
})

test('HU010: el servicio devuelve la ficha correcta y valida contribuyente y año', async () => {
  const ficha = await obtenerRegularidad('033218', 2025)
  assert.equal(ficha.contribuyente.doc, '09215430')
  assert.equal(ficha.anio, 2025)
  assert.equal(ficha.emitidas, 8)
  await assert.rejects(obtenerRegularidad('inexistente'), /No se encontró/)
  await assert.rejects(obtenerRegularidad('033218', 2030), /año solicitado/)
})

test('HU049: el catálogo publica una sola versión vigente por documento y permite buscar sin tildes', async () => {
  const documentos = await obtenerFormularios()
  assert.equal(documentos.length, 4)
  assert.equal(new Set(documentos.map((item) => item.codigo)).size, 4)
  const constancia = documentos.find((item) => item.codigo === 'PL-001')
  assert.equal(constancia.version, 2)
  assert.equal(constancia.fechaPublicacion, '2026-10-19')
  assert.equal((await obtenerFormularios('declaracion jurada'))[0].codigo, 'FO-002')
  assert.equal((await obtenerFormularios('PL-001')).length, 1)
  assert.deepEqual(await obtenerFormularios('documento inexistente'), [])
})

test('HU049: una nueva publicación sustituye a la anterior y bloquea su descarga', async () => {
  const constancia = FORMULARIOS.find((item) => item.codigo === 'PL-001')
  constancia.versiones.push({ numero: 3, fechaPublicacion: '2026-10-21', archivo: 'constancia-no-adeudo-v3.docx' })
  try {
    const [vigente] = await obtenerFormularios('PL-001')
    assert.equal(vigente.version, 3)
    assert.equal(vigente.fechaPublicacion, '2026-10-21')
    await assert.rejects(descargarFormulario('PL-001', 2), /La versión cambió/)
    assert.equal(constancia.versiones[0].fechaPublicacion, '2026-09-01', 'Se conserva el registro de publicaciones anteriores')
  } finally {
    constancia.versiones.pop()
  }
  await assert.rejects(descargarFormulario('inexistente', 1), /no está disponible/)
})

test('HU049: cada versión vigente tiene un archivo Word descargable', async () => {
  for (const documento of FORMULARIOS) {
    const vigente = documento.versiones.at(-1)
    const archivo = await readFile(new URL(`../public/formularios/${vigente.archivo}`, import.meta.url))
    assert.equal(archivo.subarray(0, 2).toString(), 'PK')
    assert.ok(archivo.includes(Buffer.from('[Content_Types].xml')))
    assert.ok(archivo.includes(Buffer.from('word/document.xml')))
  }
})
