import {
  COMPATIBILIDAD, FORMATOS, FORMATOS_EJE, ORDENABLES, PALETA, TIPOS_GRAFICO, claveResaltada, coincideContribuyente,
  decimal, etiquetaSeleccion, porcentajeTexto, redondear, solesCompactos, solesEnteros,
} from './reporteria.js'

const TEXTO = '#3a4a5f'
const TEXTO_ATENUADO = '#51627a'
const LINEA_EJE = '#b9c8da'
const LINEA_GUIA = '#e3ecf5'
const FUENTE = { fontFamily: '"Atkinson Hyperlegible",system-ui,-apple-system,"Segoe UI",sans-serif', color: TEXTO }

const escapar = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))

export function tipoActual(especificacion, tipos) {
  const tipo = tipos?.[especificacion.id] || especificacion.porDefecto
  return COMPATIBILIDAD[especificacion.clase].permitidos.includes(tipo) ? tipo : especificacion.porDefecto
}

export function ordenActual(especificacion, ordenes) {
  return ordenes?.[especificacion.id] || especificacion.ordenInicial || 'ninguno'
}

function permutacion(especificacion, datos, orden) {
  const indices = datos.categorias.map((_, i) => i)
  if (!ORDENABLES.has(especificacion.clase) || orden === 'ninguno') return indices
  const total = (i) => datos.series.reduce((suma, serie) => suma + (serie.valores[i] || 0), 0)
  return indices.sort((a, b) => (orden === 'desc' ? total(b) - total(a) : total(a) - total(b)))
}

function aplicarPermutacion(datos, p) {
  return {
    categorias: p.map((i) => datos.categorias[i]),
    claves: datos.claves ? p.map((i) => datos.claves[i]) : null,
    series: datos.series.map((serie) => ({
      ...serie,
      valores: p.map((i) => serie.valores[i]),
      colores: serie.colores ? p.map((i) => serie.colores[i]) : null,
    })),
  }
}

/* Lo que dibuja un gráfico según su tipo, el orden, la comparación fijada y lo resaltado en la pestaña. */
export function contextoGrafico({ modelo, especificacion, A, S, F, seleccion, filtros, tipos, ordenes, fijada }) {
  const tipo = tipoActual(especificacion, tipos)
  const ctx = { tipo, nota: '', seleccion, etiquetaSeleccion: etiquetaSeleccion(modelo, seleccion) }
  const etiqueta = ctx.etiquetaSeleccion
  if (especificacion.clase === 'relacion') {
    ctx.datos = especificacion.datos(A, filtros)
    if (seleccion) {
      if (seleccion.dimension === 'tributo' && S) {
        ctx.datos = especificacion.datos(S, filtros)
        ctx.nota = `Mostrando solo: ${etiqueta}.`
      } else if (seleccion.dimension !== 'tributo') {
        ctx.resaltarPunto = (punto) => coincideContribuyente(seleccion, punto)
        ctx.nota = `Resaltado: ${etiqueta}.`
      }
    }
    return ctx
  }
  if (especificacion.clase === 'matriz') {
    ctx.datos = especificacion.datos(A, filtros)
    if (seleccion) {
      const fila = claveResaltada(seleccion, 'zona')
      const columna = claveResaltada(seleccion, 'tributo')
      if (fila != null || columna != null) {
        ctx.filaResaltada = fila
        ctx.columnaResaltada = columna
        ctx.nota = `Resaltado: ${etiqueta}.`
      } else if (S) {
        ctx.datos = especificacion.datos(S, filtros)
        ctx.nota = `Mostrando solo: ${etiqueta}.`
      }
    }
    return ctx
  }
  const crudo = especificacion.datos(A, filtros)
  const p = permutacion(especificacion, crudo, ordenActual(especificacion, ordenes))
  ctx.datos = aplicarPermutacion(crudo, p)
  if (especificacion.referencia && fijada && F) ctx.datos.series.push(especificacion.referencia(F, A))
  if (seleccion) {
    const dimension = especificacion.dimensionSerie || especificacion.dimension
    const clave = dimension ? claveResaltada(seleccion, dimension) : null
    if (clave != null) {
      if (especificacion.dimensionSerie) ctx.serieResaltada = clave
      else ctx.claveResaltada = clave
      ctx.nota = `Resaltado: ${etiqueta}.`
    } else if (S) {
      const datosS = especificacion.datos(S, filtros)
      const subconjunto = especificacion.dimension ? aplicarPermutacion(datosS, p) : datosS
      const esBarra = tipo === 'barras' || tipo === 'barrash'
      const esCircular = tipo === 'circular' || tipo === 'dona'
      let modo
      if (tipo === 'tabla') modo = 'columnas'
      else if (esCircular || especificacion.apilado || (esBarra && subconjunto.series.length > 1)) modo = 'reemplazo'
      else if (!esBarra) modo = seleccion.dimension === 'contribuyente' ? 'reemplazo' : 'superpuesto'
      else modo = especificacion.aditivo ? 'superpuesto' : 'lado'
      ctx.subconjunto = subconjunto
      ctx.modo = modo
      ctx.nota = modo === 'reemplazo' ? `Mostrando solo: ${etiqueta}.` : `Resaltado: ${etiqueta}. En color pleno, su parte; en tono claro, el total.`
      if (modo === 'reemplazo') {
        ctx.datos = { ...subconjunto, series: subconjunto.series.concat(ctx.datos.series.filter((serie) => serie.punteada)) }
        ctx.subconjunto = null
      }
    }
  }
  return ctx
}

function opcionCartesiana(especificacion, ctx) {
  const { tipo, datos } = ctx
  const horizontal = tipo === 'barrash'
  const esLinea = tipo === 'linea' || tipo === 'area'
  const pila = especificacion.apilado && tipo !== 'linea' ? 't' : undefined
  const formato = FORMATOS[especificacion.formato]
  const atenuarBase = ctx.subconjunto && (ctx.modo === 'superpuesto' || ctx.modo === 'lado')
  const crear = (serie, opacidad, sufijo, encima) => {
    let opacidadSerie = opacidad
    if (ctx.serieResaltada != null && serie.clave != null) opacidadSerie = serie.clave === ctx.serieResaltada ? 1 : 0.18
    const data = serie.valores.map((v, i) => {
      const punto = { value: v == null ? null : redondear(v), clave: datos.claves ? datos.claves[i] : serie.clave }
      let opacidadPunto = opacidadSerie
      if (ctx.claveResaltada != null && datos.claves) opacidadPunto = datos.claves[i] === ctx.claveResaltada ? 1 : 0.22
      punto.itemStyle = { opacity: opacidadPunto }
      if (serie.colores && !esLinea) punto.itemStyle.color = serie.colores[i]
      return punto
    })
    const resultado = {
      name: serie.nombre + (sufijo || ''),
      type: esLinea ? 'line' : 'bar',
      data,
      stack: serie.punteada ? undefined : pila,
      barMaxWidth: 40,
      itemStyle: { color: serie.color, borderRadius: pila ? 0 : (horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]) },
      emphasis: { disabled: true },
    }
    if (esLinea) {
      resultado.lineStyle = { width: serie.punteada ? 2 : 2.2, opacity: opacidadSerie, type: serie.punteada ? 'dashed' : 'solid' }
      resultado.symbol = 'circle'
      resultado.symbolSize = 6
      resultado.showSymbol = datos.categorias.length <= 14
      if (tipo === 'area' && !serie.punteada) resultado.areaStyle = { opacity: (pila ? 0.8 : 0.18) * opacidadSerie }
      if (especificacion.formato === 'porcentaje') resultado.connectNulls = true
    }
    if (!esLinea && ctx.modo === 'superpuesto') resultado.barGap = '-100%'
    if (encima) resultado.z = 3
    return resultado
  }
  const series = datos.series.map((serie) => crear(serie, atenuarBase ? 0.3 : 1))
  if (ctx.subconjunto) ctx.subconjunto.series.forEach((serie) => series.push(crear(serie, 1, ` · ${ctx.etiquetaSeleccion}`, true)))
  if (pila && !esLinea) {
    const barras = series.filter((serie) => !serie.stack || serie.stack === pila)
    if (barras.length) barras[barras.length - 1].itemStyle.borderRadius = horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]
  }
  const esTemporal = especificacion.clase.startsWith('temporal')
  const etiquetasCategoria = esTemporal ? { hideOverlap: true } : { interval: 0, width: horizontal ? 120 : 92, overflow: 'break' }
  const categoria = { type: 'category', data: datos.categorias, axisTick: { show: false }, axisLine: { lineStyle: { color: LINEA_EJE } }, axisLabel: { color: TEXTO, ...etiquetasCategoria } }
  const valor = { type: 'value', axisLabel: { color: TEXTO_ATENUADO, formatter: FORMATOS_EJE[especificacion.formato] }, splitLine: { lineStyle: { color: LINEA_GUIA } } }
  if (especificacion.formato === 'porcentaje') valor.min = 0
  const leyenda = series.length > 1
  const zoom = esTemporal && datos.categorias.length > 12
  return {
    textStyle: FUENTE,
    animationDuration: 300,
    grid: { left: 6, right: 18, top: leyenda ? 38 : 14, bottom: zoom ? 46 : 6, containLabel: true },
    legend: leyenda ? { top: 0, left: 0, type: 'scroll', icon: 'roundRect', itemWidth: 12, itemHeight: 8, textStyle: { color: TEXTO } } : undefined,
    tooltip: { trigger: 'axis', axisPointer: { type: esLinea ? 'line' : 'shadow' }, valueFormatter: (v) => (v == null ? '—' : formato(v)), confine: true },
    xAxis: horizontal ? valor : { ...categoria, boundaryGap: !esLinea },
    yAxis: horizontal ? { ...categoria, inverse: true } : valor,
    series,
    dataZoom: zoom ? [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 6, borderColor: '#d6e2ef', textStyle: { color: TEXTO_ATENUADO }, labelFormatter: (_, s) => s }] : undefined,
    aria: { enabled: true },
  }
}

function opcionCircular(especificacion, ctx) {
  const { tipo, datos } = ctx
  const serie = datos.series[0]
  const formato = FORMATOS[especificacion.formato]
  const data = datos.categorias.map((nombre, i) => ({
    name: nombre,
    value: redondear(serie.valores[i] || 0),
    clave: datos.claves ? datos.claves[i] : i,
    itemStyle: {
      color: serie.colores ? serie.colores[i] : PALETA[i % PALETA.length],
      opacity: ctx.claveResaltada != null && datos.claves ? (datos.claves[i] === ctx.claveResaltada ? 1 : 0.25) : 1,
    },
  })).filter((punto) => punto.value > 0)
  return {
    textStyle: FUENTE,
    animationDuration: 300,
    tooltip: { trigger: 'item', confine: true, formatter: (p) => `${p.marker}${escapar(p.name)}<br><b>${formato(p.value)}</b> (${decimal(p.percent)} %)` },
    series: [{
      type: 'pie',
      radius: tipo === 'dona' ? ['44%', '70%'] : [0, '70%'],
      center: ['50%', '52%'],
      data,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: tipo === 'dona' ? 4 : 2 },
      label: { formatter: (p) => `${p.name}\n${decimal(p.percent)} %`, color: TEXTO, fontSize: 12, width: 110, overflow: 'break' },
      labelLine: { length: 8, length2: 8 },
      emphasis: { scale: false },
    }],
    aria: { enabled: true },
  }
}

function opcionCalor(ctx) {
  const d = ctx.datos
  let maximo = 0
  d.matriz.forEach((fila) => fila.forEach((v) => { if (v != null && v > maximo) maximo = v }))
  maximo = Math.max(1, Math.ceil(maximo))
  const data = []
  d.matriz.forEach((fila, r) => fila.forEach((v, c) => {
    if (v == null) return
    const atenuado = (ctx.filaResaltada != null && d.clavesFila[r] !== ctx.filaResaltada) || (ctx.columnaResaltada != null && d.clavesColumna[c] !== ctx.columnaResaltada)
    data.push({ value: [c, r, redondear(v)], clave: d.clavesFila[r], itemStyle: { opacity: atenuado ? 0.2 : 1, borderColor: '#fff', borderWidth: 2, borderRadius: 3 }, label: { color: v > maximo * 0.5 ? '#ffffff' : '#0b0b0b' } })
  }))
  return {
    textStyle: FUENTE,
    animationDuration: 300,
    grid: { left: 6, right: 10, top: 40, bottom: 52, containLabel: true },
    tooltip: { trigger: 'item', confine: true, formatter: (p) => `${escapar(d.filas[p.value[1]])} · ${escapar(d.columnas[p.value[0]])}<br>Morosidad: <b>${porcentajeTexto(p.value[2])}</b>` },
    xAxis: { type: 'category', data: d.columnas, position: 'top', axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: TEXTO, interval: 0, fontSize: 11.5, lineHeight: 14, formatter: (v) => v.replace(' ', '\n') } },
    yAxis: { type: 'category', data: d.filas, inverse: true, axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: TEXTO } },
    visualMap: { min: 0, max: maximo, calculable: false, orient: 'horizontal', left: 'center', bottom: 4, itemWidth: 12, itemHeight: 140, text: ['Mayor morosidad', 'Menor'], textStyle: { color: TEXTO, fontSize: 12 }, inRange: { color: ['#dfe6f5', '#a9b9e3', '#6f88cc', '#3f5fb5', '#17406b'] }, formatter: (v) => `${decimal(v)} %` },
    series: [{ type: 'heatmap', data, label: { show: true, formatter: (p) => decimal(p.value[2]), fontSize: 11.5 }, emphasis: { disabled: true } }],
    aria: { enabled: true },
  }
}

function opcionDispersion(ctx) {
  const puntos = ctx.datos.puntos
  const mayorDeuda = Math.max(1, ...puntos.map((p) => p.deuda))
  const elegido = ctx.seleccion?.dimension === 'contribuyente' ? ctx.seleccion.clave : null
  const data = puntos.map((p) => {
    const activo = ctx.resaltarPunto ? ctx.resaltarPunto(p) : true
    const esElegido = elegido === p.id
    return {
      value: [redondear(p.emitido), redondear(p.porcentajeCobrado), redondear(p.deuda)],
      clave: p.id,
      fila: p,
      name: p.nombre,
      symbolSize: 8 + 30 * Math.sqrt(p.deuda / mayorDeuda),
      itemStyle: { color: esElegido ? '#eb6834' : '#3f5fb5', opacity: activo ? (esElegido ? 1 : 0.8) : 0.15, borderColor: esElegido ? '#0b0b0b' : '#ffffff', borderWidth: esElegido ? 2 : 1 },
    }
  })
  return {
    textStyle: FUENTE,
    animationDuration: 300,
    grid: { left: 8, right: 22, top: 26, bottom: 28, containLabel: true },
    tooltip: { trigger: 'item', confine: true, formatter: (p) => `<b>${escapar(p.name)}</b><br>Emisión: ${solesEnteros(p.value[0])}<br>Cobrado: ${porcentajeTexto(p.value[1])}<br>Deuda vencida: ${solesEnteros(p.value[2])}` },
    xAxis: { type: 'log', name: 'Emisión del periodo (escala logarítmica)', nameLocation: 'middle', nameGap: 26, nameTextStyle: { color: TEXTO_ATENUADO }, axisLabel: { color: TEXTO_ATENUADO, formatter: (v) => solesCompactos(v).replace('S/ ', '') }, splitLine: { lineStyle: { color: LINEA_GUIA } } },
    yAxis: { type: 'value', name: '% cobrado', min: 0, max: 100, nameTextStyle: { color: TEXTO_ATENUADO }, axisLabel: { color: TEXTO_ATENUADO, formatter: (v) => `${v} %` }, splitLine: { lineStyle: { color: LINEA_GUIA } } },
    series: [{ type: 'scatter', data, emphasis: { disabled: true } }],
    aria: { enabled: true },
  }
}

export function opcionGrafico(especificacion, ctx) {
  if (especificacion.clase === 'matriz') return opcionCalor(ctx)
  if (especificacion.clase === 'relacion') return opcionDispersion(ctx)
  if (ctx.tipo === 'circular' || ctx.tipo === 'dona') return opcionCircular(especificacion, ctx)
  return opcionCartesiana(especificacion, ctx)
}

/* Los mismos datos del gráfico como tabla: para el tipo Tabla, el lector de pantalla y la exportación. */
export function tablaDeGrafico(especificacion, ctx) {
  const formato = FORMATOS[especificacion.formato]
  if (especificacion.clase === 'relacion') {
    const puntos = [...ctx.datos.puntos].sort((a, b) => b.emitido - a.emitido)
    return {
      encabezados: ['Contribuyente', 'Emisión', '% cobrado', 'Deuda vencida'],
      numericas: [false, true, true, true],
      filas: puntos.map((p) => ({ clave: p.id, fila: p, crudo: [p.nombre, redondear(p.emitido), redondear(p.porcentajeCobrado), redondear(p.deuda)], texto: [p.nombre, solesEnteros(p.emitido), porcentajeTexto(p.porcentajeCobrado), solesEnteros(p.deuda)], resaltada: ctx.resaltarPunto ? ctx.resaltarPunto(p) : false })),
    }
  }
  if (especificacion.clase === 'matriz') {
    const d = ctx.datos
    return {
      encabezados: ['Zona', ...d.columnas],
      numericas: [false, ...d.columnas.map(() => true)],
      filas: d.filas.map((nombre, i) => ({ clave: d.clavesFila[i], crudo: [nombre, ...d.matriz[i].map(redondear)], texto: [nombre, ...d.matriz[i].map((v) => porcentajeTexto(v))], resaltada: ctx.filaResaltada != null && d.clavesFila[i] === ctx.filaResaltada })),
    }
  }
  const d = ctx.datos
  const series = d.series.concat(ctx.subconjunto ? ctx.subconjunto.series.map((serie) => ({ ...serie, nombre: `${serie.nombre} · ${ctx.etiquetaSeleccion}` })) : [])
  return {
    encabezados: [especificacion.etiquetaCategoria || 'Categoría', ...series.map((serie) => serie.nombre)],
    numericas: [false, ...series.map(() => true)],
    filas: d.categorias.map((nombre, i) => ({
      clave: d.claves ? d.claves[i] : null,
      crudo: [nombre, ...series.map((serie) => redondear(serie.valores[i]))],
      texto: [nombre, ...series.map((serie) => (serie.valores[i] == null ? '—' : formato(serie.valores[i])))],
      resaltada: ctx.claveResaltada != null && d.claves && d.claves[i] === ctx.claveResaltada,
    })),
  }
}

export function resumenAccesible(especificacion, ctx, tabla) {
  const nombre = TIPOS_GRAFICO[ctx.tipo]
  const filas = tabla.filas.filter((fila) => typeof fila.crudo[1] === 'number')
  if (!filas.length) return `${nombre}: ${especificacion.titulo}. Sin datos para los filtros elegidos.`
  if (especificacion.clase === 'relacion' || especificacion.clase === 'matriz') return `${nombre}: ${especificacion.titulo}. ${tabla.filas.length} elementos. Cambia a Tabla para leer todos los valores.`
  let mayor = filas[0]
  let menor = filas[0]
  filas.forEach((fila) => {
    if (fila.crudo[1] > mayor.crudo[1]) mayor = fila
    if (fila.crudo[1] < menor.crudo[1]) menor = fila
  })
  return `${nombre}: ${especificacion.titulo}. Valor más alto: ${mayor.texto[0]}, ${mayor.texto[1]}. Valor más bajo: ${menor.texto[0]}, ${menor.texto[1]}. Cambia a Tabla para leer todos los valores.`
}

/* Gráfico pequeño que el asistente incluye en una respuesta. */
export function opcionGraficoAsistente(g) {
  const circular = /torta|circular|pie|dona/i.test(g.tipo || '')
  if (circular) {
    return { color: PALETA, title: { text: g.titulo, textStyle: { fontSize: 12 } }, tooltip: {}, series: [{ type: 'pie', radius: ['35%', '65%'], data: g.categorias.map((nombre, i) => ({ name: nombre, value: g.series[0]?.valores?.[i] })) }] }
  }
  return {
    color: ['#3f5fb5', '#8c9199', ...PALETA.slice(1)],
    title: { text: g.titulo, textStyle: { fontSize: 12 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 56, right: 8, top: 40, bottom: 36 },
    xAxis: { type: 'category', data: g.categorias, axisLabel: { fontSize: 10, interval: 0 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
    series: g.series.map((serie) => ({ name: serie.nombre, type: /linea/i.test(g.tipo) ? 'line' : 'bar', data: serie.valores })),
  }
}

/* Texto de la respuesta del asistente en bloques: párrafos y tablas. Los bloques de código no se muestran. */
export function bloquesRespuesta(texto) {
  const lineas = String(texto || '').replace(/```[a-z]*\s*[\s\S]*?```/g, '').split('\n')
  const bloques = []
  const celdas = (linea) => linea.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim())
  let i = 0
  while (i < lineas.length) {
    if (/^\s*\|.*\|\s*$/.test(lineas[i])) {
      const filas = []
      while (i < lineas.length && /^\s*\|.*\|\s*$/.test(lineas[i])) filas.push(lineas[i++])
      const conDatos = filas.filter((fila) => !/^\s*\|[\s:|-]+\|\s*$/.test(fila))
      if (conDatos.length) bloques.push({ tipo: 'tabla', encabezados: celdas(conDatos[0]), filas: conDatos.slice(1).map(celdas) })
      continue
    }
    if (lineas[i].trim()) bloques.push({ tipo: 'parrafo', texto: lineas[i].replace(/^\s*[-*] /, '• ') })
    i++
  }
  return bloques
}

/* Divide un texto por **negritas**. */
export const partesConNegrita = (texto) => String(texto).split(/(\*\*.+?\*\*)/g).filter(Boolean)
  .map((parte) => (/^\*\*.+\*\*$/.test(parte) ? { negrita: true, texto: parte.slice(2, -2) } : { negrita: false, texto: parte }))
