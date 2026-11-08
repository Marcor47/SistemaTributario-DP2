import assert from 'node:assert/strict'
import test from 'node:test'
import { alternarGrupoColumnas, etiquetaCriterioPricos, filtrosDisponibles, GRUPOS_DETALLE_MOROSOS, GRUPOS_MOROSOS, limpiarFiltros, metricaReporte, normalizarOrdenColumnas, opcionesZona, ordenarGrupos, paginarFilas, parametrosPricos, parametrosReporte, reordenarColumnasDeGrupo, reordenarGruposColumnas, validarFiltros, validarPricos } from '../src/utils/reportes.js'

const morosos = { tipo: 'MOROSOS', filtros: ['anioDesde', 'anioHasta', 'zona', 'tributo', 'regimen', 'tipoDocumento', 'etapa', 'beneficio', 'montoMinimo'] }
const pricos = { tipo: 'PRICOS', filtros: ['anio', 'zona', 'tributo', 'montoMinimo'] }
const buenosPagadoresZona = { tipo: 'BUENOS_PAGADORES', filtros: ['periodo', 'zona', 'tributo', 'montoMinimo'] }
const gruposPricos = [
  { clave: 'contribuyente', columnas: ['codigo', 'razonSocial'] },
  { clave: 'domicilio', columnas: ['via', 'numero'] },
  { clave: 'deuda', columnas: ['predial', 'arbitrios'] },
]
const ordenPricos = gruposPricos.flatMap(({ columnas }) => columnas)
const columnasMorosos = ['codigo', 'razonSocial', 'regimen', 'tipoDocumento', 'numeroDocumento', 'via', 'numero', 'departamento', 'interior', 'manzana', 'lote', 'distrito', 'zonas', 'deudaVencida', 'deudaSinBeneficio', 'condicion', 'tipoPersona', 'correo', 'telefonos']
const columnasDetalleMorosos = ['anio', 'codigoPredio', 'zona', 'tributo', 'periodo', 'fechaVencimiento', 'diasVencido', 'etapaCobranza', 'montoSinBeneficio', 'montoConBeneficio']

test('HU006: el catálogo controla los filtros visibles', () => {
  assert.deepEqual(filtrosDisponibles(morosos), ['anioDesde', 'anioHasta', 'zona', 'tributo', 'regimen', 'tipoDocumento', 'etapa', 'beneficio', 'montoMinimo'])
  assert.deepEqual(filtrosDisponibles({ filtros: ['zona', 'desconocido'] }), ['zona'])
})

test('HU006: morosos limpia y envía rango de años y beneficio', () => {
  const filtros = { anio: '2025', anioDesde: '2025', anioHasta: '2026', zona: ' Z2 ', tributo: 'ARBITRIOS', regimen: 'GENERAL', tipoDocumento: 'RUC', etapa: 'COACTIVA', beneficio: 'CON_BENEFICIO', montoMinimo: '1000' }
  assert.equal(limpiarFiltros(morosos, filtros).anio, '')
  assert.equal(parametrosReporte(morosos, filtros).toString(),
    'anioDesde=2025&anioHasta=2026&zona=Z2&tributo=ARBITRIOS&regimen=GENERAL&tipoDocumento=RUC&etapa=COACTIVA&beneficio=CON_BENEFICIO&montoMinimo=1000')
})

test('HU006: construye los parámetros aceptados para PRICOS', () => {
  const consulta = parametrosReporte(pricos, { anio: '2026', zona: '', tributo: 'IMPUESTO_PREDIAL', montoMinimo: '0' })
  assert.equal(consulta.get('anio'), '2026')
  assert.equal(consulta.get('tributo'), 'IMPUESTO_PREDIAL')
  assert.equal(consulta.get('montoMinimo'), '0')
  assert.equal(consulta.has('zona'), false)
})

test('HU006: rechaza filtros inválidos antes de consultar', () => {
  const errores = validarFiltros(pricos, { anio: '1999', zona: '123456789012345678901', tributo: 'ALCABALA', montoMinimo: '-1' })
  assert.deepEqual(Object.keys(errores).sort(), ['anio', 'montoMinimo', 'tributo', 'zona'])
})

test('HU018: valida régimen, tipo de documento y etapa de morosos', () => {
  const errores = validarFiltros(morosos, {
    anioDesde: '2025', anioHasta: '2026', montoMinimo: '', regimen: 'X'.repeat(41),
    tipoDocumento: 'PASAPORTE', etapa: 'JUZGADO', beneficio: 'OTRO',
  })
  assert.deepEqual(Object.keys(errores).sort(), ['beneficio', 'etapa', 'regimen', 'tipoDocumento'])
})

test('HU018: valida el rango de años de morosos', () => {
  const errores = validarFiltros(morosos, { anioDesde: '2026', anioHasta: '2025', montoMinimo: '' })
  assert.equal(errores.anioHasta, 'El año final no puede ser menor que el año inicial.')
})

test('HU006: selecciona la métrica monetaria de cada reporte', () => {
  assert.equal(metricaReporte('MOROSOS', { deudaVencida: 120 }), 120)
  assert.equal(metricaReporte('PRICOS', { emitidoAnio: 500 }), 500)
  assert.equal(metricaReporte('BUENOS_PAGADORES', { pagadoAnio: 80 }), 80)
})

test('HU018: el selector conserva una zona guardada que no está en el catálogo', () => {
  assert.deepEqual(opcionesZona(['Z1', 'Z2'], 'Z2'), ['Z1', 'Z2'])
  assert.deepEqual(opcionesZona(['Z1', 'Z2'], 'Z9'), ['Z1', 'Z2', 'Z9'])
  assert.deepEqual(opcionesZona(null, ''), [])
})

test('HU037: PRICOS envía el criterio y el límite', () => {
  const parametros = parametrosPricos({ criterio: 'DEUDA_TOTAL', limite: '25' })
  assert.equal(parametros.get('criterio'), 'DEUDA_TOTAL')
  assert.equal(parametros.get('limite'), '25')
  assert.equal(etiquetaCriterioPricos('BASE_IMPONIBLE'), 'Base imponible')
})

test('HU037: valida el criterio y el límite de PRICOS', () => {
  assert.deepEqual(Object.keys(validarPricos({ criterio: 'OTRO', limite: '0' })).sort(), ['criterio', 'limite'])
  assert.deepEqual(validarPricos({ criterio: 'BASE_IMPONIBLE', limite: '10' }), {})
  assert.deepEqual(Object.keys(validarPricos({ criterio: 'BASE_IMPONIBLE', limite: '201' })), ['limite'])
})

test('HU038: construye los filtros del reporte de buenos pagadores por zona', () => {
  const consulta = parametrosReporte(buenosPagadoresZona, { periodo: '2026', zona: ' Z2 ', tributo: 'ARBITRIOS', montoMinimo: '500' })
  assert.equal(consulta.toString(), 'periodo=2026&zona=Z2&tributo=ARBITRIOS&montoMinimo=500')
})

test('HU038: valida el periodo y el monto mínimo antes de consultar', () => {
  const errores = validarFiltros(buenosPagadoresZona, { periodo: '1999', zona: '', tributo: '', montoMinimo: '-1' })
  assert.deepEqual(Object.keys(errores).sort(), ['montoMinimo', 'periodo'])
})

test('HU038: pagina los resultados y corrige una página fuera de rango', () => {
  const filas = Array.from({ length: 23 }, (_, indice) => ({ codigo: indice + 1 }))
  const segunda = paginarFilas(filas, 2, 10)
  assert.deepEqual(segunda.filas.map(({ codigo }) => codigo), [11, 12, 13, 14, 15, 16, 17, 18, 19, 20])
  assert.deepEqual({ pagina: segunda.pagina, totalPaginas: segunda.totalPaginas, desde: segunda.desde, hasta: segunda.hasta }, { pagina: 2, totalPaginas: 3, desde: 11, hasta: 20 })
  assert.equal(paginarFilas(filas, 99, 10).pagina, 3)
  assert.equal(paginarFilas(Array.from({ length: 120 }), 2, 100).filas.length, 20)
})

test('HU037: ordena los grupos según la primera columna del orden plano', () => {
  const orden = ['via', 'numero', 'codigo', 'razonSocial', 'predial', 'arbitrios']
  assert.deepEqual(ordenarGrupos(gruposPricos, orden).map(({ clave }) => clave), ['domicilio', 'contribuyente', 'deuda'])
})

test('HU037: las columnas se reordenan solo dentro de su propio grupo', () => {
  assert.deepEqual(reordenarColumnasDeGrupo(ordenPricos, gruposPricos, 'contribuyente', ['razonSocial', 'codigo']), ['razonSocial', 'codigo', 'via', 'numero', 'predial', 'arbitrios'])
  assert.deepEqual(reordenarColumnasDeGrupo(ordenPricos, gruposPricos, 'domicilio', ['numero', 'via']), ['codigo', 'razonSocial', 'numero', 'via', 'predial', 'arbitrios'])
  assert.deepEqual(reordenarColumnasDeGrupo(ordenPricos, gruposPricos, 'contribuyente', ['razonSocial', 'via', 'codigo']), ['razonSocial', 'codigo', 'via', 'numero', 'predial', 'arbitrios'])
})

test('HU037: mover una cabecera superior arrastra sus columnas en su orden actual', () => {
  assert.deepEqual(reordenarGruposColumnas(ordenPricos, gruposPricos, ['domicilio', 'deuda', 'contribuyente']), ['via', 'numero', 'predial', 'arbitrios', 'codigo', 'razonSocial'])
  assert.deepEqual(reordenarGruposColumnas(ordenPricos, gruposPricos, ['deuda', 'contribuyente', 'domicilio']), ['predial', 'arbitrios', 'codigo', 'razonSocial', 'via', 'numero'])
  const conOrdenInterno = ['razonSocial', 'codigo', 'via', 'numero', 'predial', 'arbitrios']
  assert.deepEqual(reordenarGruposColumnas(conOrdenInterno, gruposPricos, ['deuda', 'contribuyente', 'domicilio']), ['predial', 'arbitrios', 'razonSocial', 'codigo', 'via', 'numero'])
})

test('HU037: normaliza órdenes guardados incompletos o inválidos', () => {
  assert.deepEqual(normalizarOrdenColumnas(['via', 'fantasma', 'codigo', 'via'], gruposPricos), ['via', 'numero', 'codigo', 'razonSocial', 'predial', 'arbitrios'])
  assert.deepEqual(normalizarOrdenColumnas(null, gruposPricos), ordenPricos)
})

test('HU018: los grupos de morosos cubren todas las columnas sin duplicarlas', () => {
  assert.deepEqual(GRUPOS_MOROSOS.flatMap(({ columnas }) => columnas), columnasMorosos)
  assert.deepEqual(GRUPOS_DETALLE_MOROSOS.flatMap(({ columnas }) => columnas), columnasDetalleMorosos)
})

test('HU018: mover el grupo Domicilio fiscal lleva también Zonas', () => {
  const movido = reordenarGruposColumnas(columnasMorosos, GRUPOS_MOROSOS, ['domicilio', 'contribuyente', 'deuda', 'datosContribuyente'])
  assert.deepEqual(movido.slice(0, 13), [...columnasMorosos.slice(5, 13), ...columnasMorosos.slice(0, 5)])
})

test('HU018: las columnas del detalle solo se reordenan dentro de su grupo', () => {
  const movido = reordenarColumnasDeGrupo(columnasDetalleMorosos, GRUPOS_DETALLE_MOROSOS, 'montos', ['montoConBeneficio', 'montoSinBeneficio'])
  assert.deepEqual(movido.slice(0, 5), columnasDetalleMorosos.slice(0, 5))
  assert.deepEqual(movido.slice(-2), ['montoConBeneficio', 'montoSinBeneficio'])
})

test('HU018: la cabecera de grupo oculta y vuelve a mostrar todas sus columnas', () => {
  const grupo = GRUPOS_MOROSOS.find(({ clave }) => clave === 'domicilio')
  const ocultas = alternarGrupoColumnas(columnasMorosos, grupo, columnasMorosos)
  assert.deepEqual(ocultas, columnasMorosos.filter((columna) => !grupo.columnas.includes(columna)))
  const mostradas = alternarGrupoColumnas(['codigo'], grupo, columnasMorosos)
  assert.deepEqual(mostradas, ['codigo', ...grupo.columnas])
})

test('HU018: el grupo no puede ocultar la última columna visible', () => {
  const grupo = GRUPOS_MOROSOS.find(({ clave }) => clave === 'domicilio')
  assert.deepEqual(alternarGrupoColumnas(grupo.columnas, grupo, columnasMorosos), grupo.columnas)
})
