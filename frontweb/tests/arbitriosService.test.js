import assert from 'node:assert/strict'
import test from 'node:test'
import { obtenerArbitriosPredio } from '../src/services/arbitriosService.js'

function respuesta(datos = {}) {
  return {
    predio: {
      codigoPredio: '021390-01',
      ubicacion: 'Jr. Los Precursores 220, San Miguel',
      usoPredio: 'Comercio',
      zona: 'Z2',
      titulares: [
        {
          codigoContribuyente: '021390',
          nombre: 'INVERSIONES COSTA AZUL S.A.C.',
          condicionPropiedad: 'PROPIETARIO',
          porcentajePropiedad: 100,
        },
      ],
    },
    fechaCorte: '2026-10-19',
    resumen: {
      totalEmitido: 7534.8,
      totalPagado: 3659.76,
      totalDescontado: 0,
      totalSaldo: 3875.04,
      cuotasPagadas: 3,
      cuotasPendientes: 0,
      cuotasVencidas: 3,
    },
    cuotas: [
      {
        idObligacion: 13,
        codigoContribuyente: '021390',
        anio: 2025,
        cuota: 1,
        periodo: '2025-T1',
        fechaVencimiento: '2025-03-31',
        emitido: 1196,
        pagado: 1196,
        descontado: 0,
        saldo: 0,
        estado: 'PAGADA',
        diasVencida: 0,
      },
    ],
    ...datos,
  }
}

test('HU005: consulta los arbitrios del predio con GET, cookie y cancelación', async (t) => {
  const controlador = new AbortController()
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    assert.equal(new URL(url).pathname, '/api/predios/021390-01/arbitrios')
    assert.equal(opciones.method, 'GET')
    assert.equal(opciones.credentials, 'include')
    assert.equal(opciones.signal, controlador.signal)
    assert.equal(opciones.body, undefined)
    return new Response(JSON.stringify(respuesta()))
  })

  const datos = await obtenerArbitriosPredio(' 021390-01 ', { signal: controlador.signal })
  assert.equal(datos.predio.codigoPredio, '021390-01')
  assert.equal(datos.fechaCorte, '2026-10-19')
  assert.equal(datos.resumen.totalSaldo, 3875.04)
  assert.equal(datos.cuotas[0].estado, 'PAGADA')
})

test('HU005: no consulta códigos vacíos ni de más de 20 caracteres', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', () => { throw new Error('No debe consultar') })
  for (const codigo of ['', '   ', null, undefined, '1'.repeat(21)]) {
    await assert.rejects(obtenerArbitriosPredio(codigo), {
      codigo: 'CODIGO_PREDIO_INVALIDO', estado: 400,
    })
  }
  assert.equal(fetch.mock.callCount(), 0)
})

test('HU005: codifica el código predial como un único segmento de la ruta', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    assert.equal(new URL(url).pathname, '/api/predios/P%2F01%3F/arbitrios')
    return new Response(JSON.stringify(respuesta({ predio: { ...respuesta().predio, codigoPredio: 'P/01?' } })))
  })
  await obtenerArbitriosPredio('P/01?')
})

test('HU005: acepta un predio sin arbitrios y conserva el resumen en cero', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify(respuesta({
    resumen: {
      totalEmitido: 0,
      totalPagado: 0,
      totalDescontado: 0,
      totalSaldo: 0,
      cuotasPagadas: 0,
      cuotasPendientes: 0,
      cuotasVencidas: 0,
    },
    cuotas: [],
  }))))

  const datos = await obtenerArbitriosPredio('021390-01')
  assert.deepEqual(datos.cuotas, [])
  assert.equal(datos.resumen.totalSaldo, 0)
})

test('HU005: propaga 401, 403, 404 y 503 con su código y detalles', async (t) => {
  const casos = [
    [401, 'SESION_REQUERIDA'],
    [403, 'ERROR_HTTP'],
    [404, 'PREDIO_NO_ENCONTRADO'],
    [503, 'SAMNET_NO_DISPONIBLE'],
  ]
  let indice = 0
  t.mock.method(globalThis, 'fetch', async () => {
    const [estado, codigo] = casos[indice]
    indice += 1
    return new Response(JSON.stringify({
      codigo, mensaje: `Error ${codigo}`, detalles: ['Detalle del servicio'],
    }), { status: estado })
  })

  for (const [estado, codigo] of casos) {
    await assert.rejects(obtenerArbitriosPredio('021390-01'), {
      estado, codigo, detalles: ['Detalle del servicio'],
    })
  }
})

test('HU005: rechaza una respuesta incompleta o perteneciente a otro predio', async (t) => {
  const respuestas = [
    null,
    respuesta({ predio: { ...respuesta().predio, codigoPredio: '014502-01' } }),
    { ...respuesta(), resumen: null },
    { ...respuesta(), cuotas: null },
    { ...respuesta(), fechaCorte: null },
  ]
  t.mock.method(globalThis, 'fetch', async () => {
    return new Response(JSON.stringify(respuestas.shift()))
  })

  for (let i = 0; i < 5; i += 1) {
    await assert.rejects(obtenerArbitriosPredio('021390-01'), { codigo: 'RESPUESTA_INVALIDA' })
  }
})

test('HU005: una caída de conexión muestra un error y no devuelve datos ficticios', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('Failed to fetch') })
  await assert.rejects(obtenerArbitriosPredio('021390-01'), { codigo: 'SIN_CONEXION', estado: 0 })
})
