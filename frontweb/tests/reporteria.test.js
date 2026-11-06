import assert from 'node:assert/strict'
import test from 'node:test'
import { obtenerDatos } from '../src/services/reporteriaService.js'
import {
  COMPATIBILIDAD, PESTANAS, avanceMeta, claveResaltada, columnasRanking, consultaDeFiltros, consultaDeSeleccion, crearModelo,
  etiquetaMes, indicadores, mesAbsoluto, mesIso, moverColumna, normalizarEstado, ordenarFilasRanking, razon, solesCompactos,
  tablaRanking,
} from '../src/utils/reporteria.js'
import { bloquesRespuesta, contextoGrafico, opcionGrafico, ordenActual, partesConNegrita, tablaDeGrafico, tipoActual } from '../src/utils/reporteriaGraficos.js'

const CATALOGO = {
  fechaCorte: '2026-11-04',
  primerMes: '2025-02',
  umbralPrico: 2000,
  zonas: [{ codigo: 'Z01', nombre: 'Z01' }, { codigo: 'SIN_ZONA', nombre: 'Sin zona' }],
  tributos: [{ codigo: 'IMPUESTO_PREDIAL', nombre: 'Impuesto Predial' }, { codigo: 'ARBITRIOS', nombre: 'Arbitrios Municipales' }],
  segmentos: ['Al día', 'Sin segmento'],
  usos: ['Comercio'],
  canales: [{ codigo: 'VENTANILLA', nombre: 'Ventanilla' }, { codigo: 'EN_LINEA', nombre: 'En línea' }],
}

const grupo = (n, valor = 0) => ({
  emitido: Array(n).fill(valor), cobrado: Array(n).fill(valor), recaudado: Array(n).fill(valor), deuda: Array(n).fill(valor),
  cuotas: Array(n).fill(1), cuotasATiempo: Array(n).fill(1), contribuyentes: Array(n).fill(1), morosos: Array(n).fill(0), buenosPagadores: Array(n).fill(1),
})

const datos = (cambios = {}) => ({
  desde: '2026-01',
  hasta: '2026-03',
  totales: { emitido: 1000, cobrado: 800, recaudado: 900, deuda: 200, cuotas: 10, cuotasATiempo: 5, contribuyentes: 4, morosos: 1, buenosPagadores: 2, recaudadoBuenosPagadores: 300 },
  pricos: { cantidad: 1, morosos: 0, emitido: 500, cobrado: 400, deuda: 100 },
  meta: { valor: 90, umbralDesvio: 10, anio: 2026, numeroPeriodo: 3 },
  porMes: grupo(3, 100),
  pricosPorMes: { emitidoPricos: [50, 50, 50], cobradoPricos: [25, 50, 0], emitidoResto: [50, 50, 50], cobradoResto: [50, 50, 50] },
  saldoPorSegmento: [[10, 20, 30], [0, 0, 0]],
  porZona: grupo(2, 500),
  porTributo: grupo(2, 500),
  porSegmento: { ...grupo(2, 100), contribuyentes: [3, 1], deuda: [150, 50] },
  tributoEmitidoPricos: [300, 200],
  zonaTributoEmitido: [[100, 100], [0, 100]],
  zonaTributoDeuda: [[10, 20], [0, 30]],
  antiguedad: [1, 2, 3, 4],
  canal: [3, 1],
  anticipacion: [1, 1, 1, 1],
  usoEmitido: [500],
  puntosPricos: [{ id: 7, nombre: 'Contrib. abc', emitido: 500, porcentajeCobrado: 80, deuda: 100, zona: 0, segmento: 0, uso: 0 }],
  top: { resumen: [], pricos: [], morosos: [], buenos: [] },
  destacado: null,
  ...cambios,
})

test('los periodos salen del catálogo: año de corte, años con datos, últimos 12 meses y acumulado', () => {
  const modelo = crearModelo(CATALOGO)
  assert.deepEqual(Object.keys(modelo.periodos), ['2025', '2026', 'u12', 'acum'])
  assert.equal(modelo.periodoInicial, '2026')
  assert.equal(mesIso(modelo.periodos['2026'].desde), '2026-01')
  assert.equal(mesIso(modelo.periodos['2026'].hasta), '2026-11')
  assert.equal(modelo.periodos['2026'].etiquetaPrevia, 'ene–nov 2025')
  assert.equal(mesIso(modelo.periodos.u12.desde), '2025-12')
  assert.equal(mesIso(modelo.periodos.acum.desde), '2025-02')
  assert.equal(modelo.periodos.acum.previo, null)
  assert.equal(etiquetaMes(mesAbsoluto('2026-03')), 'mar 26')
})

test('consulta los datos con los filtros elegidos y omite los vacíos', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(new URL(url))
    return new Response(JSON.stringify({ totales: {} }))
  })
  const modelo = crearModelo(CATALOGO)
  await obtenerDatos(consultaDeFiltros(modelo, { periodo: '2025', zona: 'Z01', tributo: '', segmento: '' }))
  assert.equal(urls[0].pathname, '/api/reporteria/datos')
  assert.deepEqual(Object.fromEntries(urls[0].searchParams), { desde: '2025-01', hasta: '2025-12', zona: 'Z01' })
})

test('un resaltado reemplaza al filtro de su dimensión o agrega el contribuyente', () => {
  const modelo = crearModelo(CATALOGO)
  const filtros = { periodo: '2026', zona: '', tributo: '', segmento: '' }
  assert.equal(consultaDeSeleccion(modelo, filtros, { dimension: 'zona', clave: 1 }).zona, 'SIN_ZONA')
  assert.equal(consultaDeSeleccion(modelo, filtros, { dimension: 'tributo', clave: 1 }).tributo, 'ARBITRIOS')
  assert.equal(consultaDeSeleccion(modelo, filtros, { dimension: 'segmento', clave: 0 }).segmento, 'Al día')
  assert.equal(consultaDeSeleccion(modelo, filtros, { dimension: 'uso', clave: 0 }).uso, 'Comercio')
  assert.equal(consultaDeSeleccion(modelo, filtros, { dimension: 'contribuyente', clave: 7 }).contribuyente, 7)
  assert.equal(claveResaltada({ dimension: 'contribuyente', clave: 7, zona: 1 }, 'zona'), 1)
  assert.equal(claveResaltada({ dimension: 'contribuyente', clave: 7, esPrico: false, uso: 0 }, 'uso'), null)
  assert.equal(claveResaltada(null, 'zona'), undefined)
})

test('cada gráfico solo admite los tipos compatibles y vuelve al suyo si la vista guarda otro', () => {
  const modelo = crearModelo(CATALOGO)
  for (const pestana of PESTANAS) {
    assert.equal(modelo.graficos[pestana.id].length, 4)
    for (const especificacion of modelo.graficos[pestana.id]) {
      assert.ok(COMPATIBILIDAD[especificacion.clase].permitidos.includes(especificacion.porDefecto), especificacion.id)
    }
  }
  const ranking = modelo.graficos.morosos.find((e) => e.id === 'm4')
  assert.equal(tipoActual(ranking, { m4: 'circular' }), 'barrash')
  assert.equal(tipoActual(ranking, { m4: 'tabla' }), 'tabla')
  assert.equal(ordenActual(ranking, {}), 'desc')
})

test('todos los gráficos generan opciones y tabla con los datos de la consulta', () => {
  const modelo = crearModelo(CATALOGO)
  const A = datos()
  const filtros = { periodo: '2026', zona: '', tributo: '', segmento: '' }
  for (const pestana of PESTANAS) {
    for (const especificacion of modelo.graficos[pestana.id]) {
      const contexto = contextoGrafico({ modelo, especificacion, A, S: null, F: null, seleccion: null, filtros, tipos: {}, ordenes: {}, fijada: null })
      assert.ok(opcionGrafico(especificacion, contexto), especificacion.id)
      assert.ok(tablaDeGrafico(especificacion, contexto).encabezados.length > 1, especificacion.id)
    }
  }
})

test('un gráfico de otra dimensión resaltada usa el subconjunto; el de la misma dimensión atenúa el resto', () => {
  const modelo = crearModelo(CATALOGO)
  const filtros = { periodo: '2026', zona: '', tributo: '', segmento: '' }
  const A = datos()
  const S = datos({ porTributo: { ...grupo(2, 10) } })
  const porZona = modelo.graficos.morosos.find((e) => e.id === 'm4')
  const misma = contextoGrafico({ modelo, especificacion: porZona, A, S, F: null, seleccion: { dimension: 'zona', clave: 1 }, filtros, tipos: {}, ordenes: {}, fijada: null })
  assert.equal(misma.claveResaltada, 1)
  assert.equal(misma.subconjunto, undefined)
  const dona = modelo.graficos.resumen.find((e) => e.id === 'r2')
  const otra = contextoGrafico({ modelo, especificacion: dona, A, S, F: null, seleccion: { dimension: 'zona', clave: 1 }, filtros, tipos: {}, ordenes: {}, fijada: null })
  assert.equal(otra.datos.series[0].valores[0], 10)
  assert.match(otra.nota, /Mostrando solo/)
})

test('la meta viene del servidor: alerta solo si el desvío supera su umbral, y sin meta no se calcula', () => {
  const modelo = crearModelo(CATALOGO)
  assert.equal(Math.round(avanceMeta(datos()).avance * 10) / 10, 88.9)
  const conAlerta = indicadores(modelo, 'resumen', datos({ meta: { valor: 95, umbralDesvio: 10 }, totales: { ...datos().totales, cobrado: 700 } }))
  assert.equal(conAlerta[2].alerta, true)
  assert.match(conAlerta[2].marca.texto, /umbral 10\.0 %/)
  const sinMeta = indicadores(modelo, 'resumen', datos({ meta: null }))
  assert.equal(sinMeta[2].valor, '—')
  assert.match(sinMeta[2].detalle, /No hay una meta/)
})

test('los indicadores comparan contra la referencia y saben qué dirección es mejora', () => {
  const modelo = crearModelo(CATALOGO)
  const A = datos()
  const R = datos({ totales: { ...A.totales, deuda: 100, emitido: 1000 } })
  const morosos = indicadores(modelo, 'morosos', A, R)
  assert.equal(morosos[0].variacion.mejora, false)
  assert.equal(morosos[2].variacion.texto, '+10.0 pp')
  assert.equal(morosos[3].valor, 'Al día')
  const buenos = indicadores(modelo, 'buenos', A)
  assert.equal(buenos[3].valor, '25.0 %')
})

test('las columnas del ranking respetan el orden y las ocultas; mover no pierde columnas', () => {
  const modelo = crearModelo(CATALOGO)
  const configuracion = modelo.rankings.morosos
  const { todas, visibles } = columnasRanking(configuracion, ['codigo'], ['deuda', 'nombre'])
  assert.equal(todas[0].clave, 'deuda')
  assert.equal(todas[1].clave, 'nombre')
  assert.ok(!visibles.some((c) => c.clave === 'codigo'))
  const movidas = moverColumna(todas, 'nombre', 0)
  assert.equal(movidas[0], 'nombre')
  assert.equal(movidas.length, todas.length)
})

test('ordena el ranking y lo exporta con las columnas visibles', () => {
  const modelo = crearModelo(CATALOGO)
  const configuracion = modelo.rankings.morosos
  const filas = [
    { id: 1, codigo: 'AAA', nombre: 'A', zona: 0, segmento: 0, deuda: 10, cuotasVencidas: 1, atrasoMaximo: 2, tributoPrincipal: 0, ultimoPago: null },
    { id: 2, codigo: 'BBB', nombre: 'B', zona: 1, segmento: 1, deuda: 30, cuotasVencidas: 2, atrasoMaximo: 1, tributoPrincipal: 1, ultimoPago: '2026-03' },
  ]
  const puestos = new Map([[2, 1], [1, 2]])
  const columnaDeuda = configuracion.columnas.find((c) => c.clave === 'deuda')
  assert.deepEqual(ordenarFilasRanking(filas, columnaDeuda, 'desc', puestos).map((f) => f.id), [2, 1])
  const { visibles } = columnasRanking(configuracion, ['codigo', 'segmento'], [])
  const tabla = tablaRanking(visibles, ordenarFilasRanking(filas, columnaDeuda, 'desc', puestos), puestos)
  assert.equal(tabla.encabezados[0], '#')
  assert.deepEqual(tabla.filas[0].slice(0, 4), [1, 'B', 'Sin zona', 30])
  assert.equal(tabla.filas[0].at(-1), 'mar 2026')
  assert.equal(tabla.filas[1].at(-1), 'Sin pagos')
})

test('una vista guardada con valores que ya no existen vuelve a los valores por defecto', () => {
  const modelo = crearModelo(CATALOGO)
  const base = { pestana: 'resumen', filtros: { periodo: '2026', zona: '', tributo: '', segmento: '' }, tipos: {}, ordenes: {}, ocultas: {}, ordenColumnas: {}, ordenTabla: {}, fijada: null }
  const abierta = normalizarEstado(modelo, { pestana: 'otra', filtros: { periodo: '1999', zona: 'Z99', tributo: 'LIMPIEZA_PUBLICA', segmento: 'Antiguo' }, fijada: { periodo: '1999' } }, base)
  assert.equal(abierta.pestana, 'resumen')
  assert.deepEqual(abierta.filtros, base.filtros)
  assert.equal(abierta.fijada, null)
  const valida = normalizarEstado(modelo, { pestana: 'morosos', filtros: { periodo: '2025', zona: 'Z01', tributo: 'ARBITRIOS', segmento: 'Al día' } }, base)
  assert.equal(valida.pestana, 'morosos')
  assert.equal(valida.filtros.zona, 'Z01')
})

test('la respuesta del asistente se interpreta como texto: sin HTML, con tablas y negritas', () => {
  const bloques = bloquesRespuesta('Hola **Z01** <img src=x>\n\n| Zona | Deuda |\n|---|---|\n| Z01 | 10 |\n```grafico\n{"a":1}\n```\n- punto')
  assert.deepEqual(bloques[0], { tipo: 'parrafo', texto: 'Hola **Z01** <img src=x>' })
  assert.deepEqual(bloques[1], { tipo: 'tabla', encabezados: ['Zona', 'Deuda'], filas: [['Z01', '10']] })
  assert.deepEqual(bloques[2], { tipo: 'parrafo', texto: '• punto' })
  assert.deepEqual(partesConNegrita('a **b** c'), [{ negrita: false, texto: 'a ' }, { negrita: true, texto: 'b' }, { negrita: false, texto: ' c' }])
})

test('formatos de montos y razones', () => {
  assert.equal(solesCompactos(1500000), 'S/ 1.50 M')
  assert.equal(solesCompactos(25000), 'S/ 25.0 mil')
  assert.equal(razon(1, 0), null)
})
