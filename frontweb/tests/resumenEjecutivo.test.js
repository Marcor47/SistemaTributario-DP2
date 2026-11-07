import assert from 'node:assert/strict'
import test from 'node:test'
import { descargarResumen, generarResumen, marcarRevisado } from '../src/services/resumenEjecutivoService.js'
import {
  armarPeriodo, contarPalabras, esPeriodoValido, formatoPeriodo, formatoValor, nombreArchivo,
  verificarCifras,
} from '../src/utils/resumenEjecutivo.js'

const indicadores = [
  { codigo: 'INDICE_MOROSIDAD', nombre: 'Índice de morosidad', valor: 14.8, unidad: 'PORCENTAJE' },
  { codigo: 'PORC_RECAUDACION_EMISION', nombre: 'Recaudación respecto de la emisión', valor: 72, unidad: 'PORCENTAJE' },
]
const sinConexion = () => { throw new TypeError('fetch failed') }
const json = (cuerpo, estado = 200) => new Response(JSON.stringify(cuerpo), { status: estado })

test('HU017: el periodo se arma y se valida como AAAA-MM', () => {
  assert.equal(armarPeriodo('2026', '3'), '2026-03')
  assert.equal(armarPeriodo('2026', ''), '')
  assert.equal(esPeriodoValido('2026-10'), true)
  for (const invalido of ['2026-13', '2026-00', '26-10', '', null]) assert.equal(esPeriodoValido(invalido), false)
  assert.equal(formatoPeriodo('2026-10'), 'Octubre 2026')
})

test('HU017: cuenta palabras ignorando espacios repetidos y vacíos', () => {
  assert.equal(contarPalabras('  uno   dos\ntres '), 3)
  assert.equal(contarPalabras(''), 0)
  assert.equal(contarPalabras(null), 0)
})

test('HU017: cada cifra del texto se contrasta con los indicadores y los años no cuentan como cifras', () => {
  const texto = 'En octubre de 2026 la morosidad fue de 14,80 % y la recaudación de 72,00 %; la meta era 99,5 %.'
  const cifras = verificarCifras(texto, indicadores)
  assert.deepEqual(cifras.map((c) => c.valor), [14.8, 72, 99.5])
  assert.deepEqual(cifras.map((c) => c.respaldada), [true, true, false])
  assert.deepEqual(verificarCifras('Sin cifras y solo el año 2026.', indicadores), [])
})

test('HU017: un indicador sin valor no respalda un 0 inventado en el texto', () => {
  const sinValor = [{ codigo: 'INDICE_MOROSIDAD', valor: null, unidad: 'PORCENTAJE' }]
  assert.equal(verificarCifras('La morosidad fue de 0,00 %.', sinValor)[0].respaldada, false)
})

test('HU017: los montos en soles también se verifican', () => {
  const montos = [{ codigo: 'META', valor: 1250.5, unidad: 'SOLES' }]
  assert.equal(verificarCifras('Se recaudó S/ 1250.50 en el mes.', montos)[0].respaldada, true)
  assert.equal(verificarCifras('Se recaudó S/ 900.10 en el mes.', montos)[0].respaldada, false)
})

test('HU017: los valores se muestran con dos decimales y el símbolo de porcentaje', () => {
  assert.match(formatoValor({ valor: 14.8, unidad: 'PORCENTAJE' }), /^14[.,]80 %$/)
  assert.equal(formatoValor({ valor: null, unidad: 'PORCENTAJE' }), 'No disponible')
  assert.equal(nombreArchivo('2026-10'), 'resumen-ejecutivo-2026-10.txt')
})

test('HU017: genera el resumen en el servidor con el periodo elegido', async (t) => {
  const pedidos = []
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    pedidos.push({ url: String(url), cuerpo: JSON.parse(opciones.body), metodo: opciones.method })
    return json({ idResumen: 7, periodo: '2026-10', texto: 'Texto.', indicadoresUsados: [], estado: 'GENERADO' })
  })
  const resumen = await generarResumen('2026-10')
  assert.equal(pedidos[0].metodo, 'POST')
  assert.ok(pedidos[0].url.endsWith('/resumenes-ejecutivos'))
  assert.deepEqual(pedidos[0].cuerpo, { periodo: '2026-10' })
  assert.equal(resumen.idResumen, 7)
})

test('HU017: un periodo mal formado no llega al servidor', async (t) => {
  const fetchSimulado = t.mock.method(globalThis, 'fetch', async () => json({}))
  await assert.rejects(generarResumen('2026-13'), { codigo: 'PERIODO_INVALIDO' })
  assert.equal(fetchSimulado.mock.callCount(), 0)
})

test('HU017: los errores del servidor distintos de «no disponible» se conservan', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => json({ codigo: 'SIN_INDICADORES', mensaje: 'Sin indicadores.' }, 422))
  await assert.rejects(generarResumen('2026-10'), { codigo: 'SIN_INDICADORES', estado: 422 })
})

test('HU017: sin conexión se informa el error; no se inventa un resumen', async (t) => {
  t.mock.method(globalThis, 'fetch', sinConexion)
  await assert.rejects(generarResumen('2026-10'), { codigo: 'SIN_CONEXION' })
})

test('HU017: si el servicio aún no existe en el servidor se avisa que no está disponible', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => json({ codigo: 'RUTA_NO_ENCONTRADA', mensaje: 'La ruta no existe.' }, 404))
  await assert.rejects(generarResumen('2026-10'), { codigo: 'SERVICIO_NO_DISPONIBLE', estado: 404 })
})

test('HU017: un periodo sin indicadores calculados conserva el error del servidor', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => json({ codigo: 'PERIODO_SIN_INDICADORES', mensaje: 'Sin indicadores.' }, 422))
  await assert.rejects(generarResumen('2020-01'), { codigo: 'PERIODO_SIN_INDICADORES', estado: 422 })
})

test('HU017: no se descarga un resumen sin revisar', async () => {
  await assert.rejects(descargarResumen({ idResumen: 7, periodo: '2026-09', estado: 'GENERADO' }),
    { codigo: 'RESUMEN_SIN_REVISAR' })
})

test('HU017: revisa y descarga en el servidor', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    urls.push(`${opciones.method ?? 'GET'} ${new URL(url).pathname}`)
    return String(url).endsWith('/descarga')
      ? json({ texto: 'Texto revisado.' })
      : json({ idResumen: 7, periodo: '2026-10', texto: 'Texto revisado.', estado: 'REVISADO', indicadoresUsados: [] })
  })
  const revisado = await marcarRevisado({ idResumen: 7, periodo: '2026-10', estado: 'GENERADO' })
  assert.equal(revisado.estado, 'REVISADO')
  const archivo = await descargarResumen(revisado)
  assert.equal(archivo.contenido, 'Texto revisado.')
  assert.deepEqual(urls, ['POST /api/resumenes-ejecutivos/7/revision', 'GET /api/resumenes-ejecutivos/7/descarga'])
})
