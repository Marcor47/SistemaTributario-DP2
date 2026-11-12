import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buscarCoincidenciasVentanilla,
  confirmarPagoSolicitud,
  construirParametrosEmision,
  consultarContribuyente,
  crearSolicitudVentanilla,
  emitirSolicitud,
  firmaDePrueba,
  listarConstancias,
  listarSolicitudesContribuyente,
  normalizarDocumento,
  registrarFirmaSolicitud,
  textoDocumento,
  urlConstanciaPdf,
  verificarDeudaSolicitud,
} from '../src/services/constanciaVentanillaService.js'
import {
  FILTROS_DEUDA_INICIALES, filtrarObligacionesDeuda, opcionesDeuda,
} from '../src/utils/constanciaVentanilla.js'

const json = (cuerpo, estado = 200) => new Response(JSON.stringify(cuerpo), { status: estado })

const consulta = {
  contribuyente: {
    codigoContribuyente: '014502',
    tipoDocumento: 'DNI',
    numeroDocumento: '41028765',
    nombreRazonSocial: 'GARCÍA TORRES, MARÍA ELENA',
  },
  predios: [{ codigoPredio: '014502-01', ubicacion: 'Av. La Marina 1450', tipoPredio: 'URBANO', zona: 'Costanera' }],
  deuda: { total: 0, puedeSolicitar: true, obligaciones: [] },
}

test('HU042: el documento se limpia y la consulta usa el endpoint de ventanilla', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(String(url))
    return json(consulta)
  })

  const datos = await consultarContribuyente(' 41028765 ')
  assert.ok(urls[0].endsWith('/ventanilla/constancias/consulta?documento=41028765'))
  assert.equal(datos.deuda.puedeSolicitar, true)
  assert.equal(normalizarDocumento(' 41.028.765 '), '41028765')
})

test('HU042: la consulta exige un documento válido antes de llamar al servidor', async (t) => {
  const fetchSimulado = t.mock.method(globalThis, 'fetch', async () => json({}))
  await assert.rejects(
    () => consultarContribuyente('   '),
    (err) => err.codigo === 'DOCUMENTO_REQUERIDO',
  )
  await assert.rejects(
    () => consultarContribuyente('1234567890123'),
    (err) => err.codigo === 'DOCUMENTO_INVALIDO',
  )
  assert.equal(fetchSimulado.mock.callCount(), 0)
})

test('HU042: las sugerencias buscan por documento o código y descartan nombres', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(String(url))
    return json([
      { codigo: '014502', nombre: 'GARCÍA TORRES, MARÍA ELENA', tipoDoc: 'DNI', doc: '41028765' },
      { codigo: '021390', nombre: 'INVERSIONES COSTA AZUL S.A.C.', tipoDoc: 'RUC', doc: '20456781239' },
      { codigo: '777777', nombre: '4102 SERVICIOS', tipoDoc: 'DNI', doc: '11111111' },
    ])
  })

  const porDocumento = await buscarCoincidenciasVentanilla(' 4102 ')
  assert.ok(urls[0].endsWith('/contribuyentes/buscar?q=4102'))
  assert.deepEqual(porDocumento.map((contribuyente) => contribuyente.codigo), ['014502'])

  const porCodigo = await buscarCoincidenciasVentanilla('0213')
  assert.deepEqual(porCodigo.map((contribuyente) => contribuyente.codigo), ['021390'])
})

test('HU042: no busca sugerencias con menos de dos caracteres', async (t) => {
  const fetchSimulado = t.mock.method(globalThis, 'fetch', async () => json([]))
  assert.deepEqual(await buscarCoincidenciasVentanilla('4'), [])
  assert.deepEqual(await buscarCoincidenciasVentanilla(''), [])
  assert.equal(fetchSimulado.mock.callCount(), 0)
})

test('HU042: crear la solicitud envía el documento con CSRF al paso 1', async (t) => {
  const envios = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    if (String(url).endsWith('/auth/interno/csrf')) {
      return json({ headerName: 'X-XSRF-TOKEN', token: 'token-123' })
    }
    envios.push({ url: String(url), opciones })
    return json({ idSolicitud: 15, canal: 'VENTANILLA', estado: 'INICIADA' }, 201)
  })

  const creada = await crearSolicitudVentanilla(' 41028765 ')
  assert.equal(creada.estado, 'INICIADA')
  assert.ok(envios[0].url.endsWith('/ventanilla/constancias/solicitudes'))
  assert.equal(envios[0].opciones.method, 'POST')
  assert.equal(envios[0].opciones.headers['X-XSRF-TOKEN'], 'token-123')
  assert.deepEqual(JSON.parse(envios[0].opciones.body), { documentoContribuyente: '41028765' })
})

test('HU042: la firma simulada sigue el patrón del firmador de prueba y viaja al servidor', async (t) => {
  assert.equal(firmaDePrueba('hash-sha256'), btoa('FIRMA-DE-PRUEBA:hash-sha256'))

  const envios = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    if (String(url).endsWith('/auth/interno/csrf')) {
      return json({ headerName: 'X-XSRF-TOKEN', token: 't' })
    }
    envios.push({ url: String(url), opciones })
    return json({ idSolicitud: 15, estado: 'FIRMADA' })
  })

  const firmada = await registrarFirmaSolicitud(15, firmaDePrueba('hash-sha256'), 'CERTIFICADO DE PRUEBA')
  assert.equal(firmada.estado, 'FIRMADA')
  assert.ok(envios[0].url.endsWith('/ventanilla/constancias/15/firma'))
  assert.deepEqual(JSON.parse(envios[0].opciones.body), {
    firmaBase64: btoa('FIRMA-DE-PRUEBA:hash-sha256'),
    certificado: 'CERTIFICADO DE PRUEBA',
  })
})

test('HU042: verificación, pago y emisión van por pasos con CSRF y sin documento en la emisión', async (t) => {
  const envios = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    if (String(url).endsWith('/auth/interno/csrf')) {
      return json({ headerName: 'X-XSRF-TOKEN', token: 't' })
    }
    envios.push({ url: String(url), opciones })
    return json({ idSolicitud: 15, estado: 'EMITIDA' }, 201)
  })

  await verificarDeudaSolicitud(15)
  await confirmarPagoSolicitud(15)
  const emitida = await emitirSolicitud(15, {
    predios: ['014502-01', '014502-01', ' '],
    prediosTexto: 'Texto que el backend ignora',
    rangoFechas: '  Periodo 2024 - 2026  ',
  })

  assert.equal(emitida.estado, 'EMITIDA')
  assert.ok(envios[0].url.endsWith('/ventanilla/constancias/15/verificacion'))
  assert.ok(envios[1].url.endsWith('/ventanilla/constancias/15/pago'))
  assert.ok(envios[2].url.endsWith('/ventanilla/constancias/15/emitir'))
  for (const envio of envios) {
    assert.equal(envio.opciones.method, 'POST')
    assert.equal(envio.opciones.headers['X-XSRF-TOKEN'], 't')
  }
  assert.deepEqual(JSON.parse(envios[2].opciones.body), {
    predios: ['014502-01'],
    prediosTexto: null,
    rangoFechas: 'Periodo 2024 - 2026',
  })
})

test('HU042: sin predios seleccionados se envía el texto libre y los máximos se validan antes de enviar', async (t) => {
  const fetchSimulado = t.mock.method(globalThis, 'fetch', async () => json({}))

  assert.deepEqual(construirParametrosEmision({
    predios: [], prediosTexto: '  Predio rural sin código  ', rangoFechas: '',
  }), {
    predios: [],
    prediosTexto: 'Predio rural sin código',
    rangoFechas: null,
  })

  await assert.rejects(
    () => emitirSolicitud(15, { prediosTexto: 'x'.repeat(401) }),
    /400 caracteres/,
  )
  await assert.rejects(
    () => emitirSolicitud(15, { rangoFechas: 'x'.repeat(61) }),
    /60 caracteres/,
  )
  assert.equal(fetchSimulado.mock.callCount(), 0)
})

test('HU042: el historial filtra por documento solo cuando se indica', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(String(url))
    return json([])
  })

  await listarConstancias('41028765')
  await listarConstancias()
  assert.ok(urls[0].endsWith('/ventanilla/constancias?documento=41028765'))
  assert.ok(urls[1].endsWith('/ventanilla/constancias'))
})

test('HU042: las solicitudes del contribuyente se piden por documento', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(String(url))
    return json([])
  })

  const datos = await listarSolicitudesContribuyente(' 41.028.765 ')
  assert.ok(urls[0].endsWith('/ventanilla/constancias/solicitudes?documento=41028765'))
  assert.deepEqual(datos, [])
})

test('HU042: el listado de solicitudes exige un documento válido antes de llamar al servidor', async (t) => {
  const fetchSimulado = t.mock.method(globalThis, 'fetch', async () => json([]))

  await assert.rejects(
    () => listarSolicitudesContribuyente('   '),
    (err) => err.codigo === 'DOCUMENTO_REQUERIDO',
  )
  assert.equal(fetchSimulado.mock.callCount(), 0)
})

test('HU042: la descarga apunta al PDF binario de la constancia', () => {
  assert.ok(urlConstanciaPdf('CNA-2026-000001').endsWith('/ventanilla/constancias/CNA-2026-000001/descarga'))
  assert.ok(urlConstanciaPdf('CNA 2026/01').endsWith('/ventanilla/constancias/CNA%202026%2F01/descarga'))
})

test('HU042: el texto del documento de firma se decodifica del base64', () => {
  assert.equal(textoDocumento(btoa('SOLICITUD DE CONSTANCIA')), 'SOLICITUD DE CONSTANCIA')
})

const obligaciones = [
  { id: '1', tributo: 'Impuesto Predial', anio: 2025, periodo: '2025-T3', estado: 'Vencida' },
  { id: '2', tributo: 'Arbitrios Municipales', anio: 2025, periodo: '2025-08', estado: 'Vencida' },
  { id: '3', tributo: 'Impuesto Predial', anio: 2026, periodo: '2026-T1', estado: 'Pendiente' },
  { id: '4', tributo: 'Arbitrios Municipales', anio: 2026, periodo: '2026-01', estado: 'Pendiente' },
  { id: '5', tributo: 'Impuesto Predial', anio: 2025, periodo: '2025-T3', estado: 'Vencida' },
]

test('HU042: los filtros de deuda salen de las obligaciones sin duplicados', () => {
  assert.deepEqual(opcionesDeuda(obligaciones), {
    tributos: ['Arbitrios Municipales', 'Impuesto Predial'],
    anios: [2026, 2025],
    estados: ['Pendiente', 'Vencida'],
  })
  assert.deepEqual(opcionesDeuda(), { tributos: [], anios: [], estados: [] })
})

test('HU042: la tabla de deudas filtra por tributo, año y estado', () => {
  const ids = (filtros) => filtrarObligacionesDeuda(obligaciones, { ...FILTROS_DEUDA_INICIALES, ...filtros })
    .map((obligacion) => obligacion.id)

  assert.deepEqual(ids({ tributo: 'Impuesto Predial' }), ['1', '3', '5'])
  assert.deepEqual(ids({ anio: '2025' }), ['1', '2', '5'])
  assert.deepEqual(ids({ anio: 2026 }), ['3', '4'])
  assert.deepEqual(ids({ estado: 'Pendiente' }), ['3', '4'])
  assert.deepEqual(ids({ tributo: 'Impuesto Predial', anio: 2026, estado: 'Pendiente' }), ['3'])
  assert.deepEqual(ids({ anio: 2024 }), [])
  assert.deepEqual(filtrarObligacionesDeuda(obligaciones), obligaciones)
})
