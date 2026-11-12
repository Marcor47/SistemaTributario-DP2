// Mapa de segmentos (HU030): colores, series de ECharts y formato de la ficha. Sin React, para probarlo con node:test.

// Paleta categórica en orden fijo (un color por segmento, nunca se reasigna al filtrar). K-means da como máximo 8.
export const COLORES_SEGMENTO = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948']
export const COLOR_ATENUADO = '#c9d3df'
export const UMBRAL_GRANDE = 2000

export function colorSegmento(indice) {
  return COLORES_SEGMENTO[indice % COLORES_SEGMENTO.length]
}

/* Agrupa los puntos [idAsignacion, segmento, x, y] por segmento, en el orden de los segmentos. */
export function puntosPorSegmento(mapa) {
  const grupos = mapa.segmentos.map(() => [])
  for (const [id, segmento, x, y] of mapa.puntos) {
    grupos[segmento]?.push([x, y, id])
  }
  return grupos
}

/*
 * Series del gráfico. seleccionado: índice del segmento resaltado (null = todos con su color). resaltados: ids de
 * asignación que se marcan con un anillo (resultados de búsqueda); activo: el de la ficha abierta.
 */
export function seriesMapa(mapa, { seleccionado = null, resaltados = [], activo = null } = {}) {
  const grupos = puntosPorSegmento(mapa)
  const total = mapa.puntos.length
  const grande = total > UMBRAL_GRANDE
  const series = mapa.segmentos.map((segmento, i) => {
    const apagado = seleccionado != null && seleccionado !== i
    return {
      id: `seg-${i}`,
      name: segmento.nombre,
      type: 'scatter',
      data: grupos[i],
      symbolSize: grande ? 4 : 9,
      large: grande,
      largeThreshold: UMBRAL_GRANDE,
      z: apagado ? 1 : 2,
      silent: apagado,
      itemStyle: {
        color: apagado ? COLOR_ATENUADO : colorSegmento(i),
        opacity: apagado ? 0.35 : 0.9,
        borderColor: '#ffffff',
        borderWidth: grande ? 0 : 1,
      },
      emphasis: { scale: 1.6 },
    }
  })
  const ubicacion = new Map(mapa.puntos.map(([id, , x, y]) => [id, [x, y]]))
  const anillos = [...new Set([...resaltados, ...(activo != null ? [activo] : [])])]
    .filter((id) => ubicacion.has(id))
    .map((id) => [...ubicacion.get(id), id])
  series.push({
    id: 'resaltados',
    name: 'Resaltados',
    type: 'scatter',
    data: anillos,
    z: 3,
    symbol: 'circle',
    symbolSize: (valor) => (valor[2] === activo ? 22 : 16),
    itemStyle: { color: 'rgba(0,0,0,0)', borderColor: '#17406B', borderWidth: 3 },
    tooltip: { show: false },
  })
  return series
}

/* Rango de zoom (porcentaje de cada eje) centrado en un punto, para llevar la vista a un resultado de búsqueda. */
export function zoomA(mapa, id, ancho = 0.25) {
  const punto = mapa.puntos.find((p) => p[0] === id)
  if (!punto) return null
  const xs = mapa.puntos.map((p) => p[2])
  const ys = mapa.puntos.map((p) => p[3])
  const rango = (valores, centro) => {
    const min = Math.min(...valores)
    const max = Math.max(...valores)
    const largo = max - min || 1
    const c = ((centro - min) / largo) * 100
    const mitad = (ancho * 100) / 2
    const inicio = Math.max(0, Math.min(100 - 2 * mitad, c - mitad))
    return { start: inicio, end: inicio + 2 * mitad }
  }
  return { x: rango(xs, punto[2]), y: rango(ys, punto[3]) }
}

export function formatearValor(valor, unidad) {
  if (valor == null || Number.isNaN(Number(valor))) return '—'
  const numero = Number(valor)
  const redondeado = Math.abs(numero) >= 10 || unidad === '%' ? Math.round(numero) : Math.round(numero * 10) / 10
  const texto = redondeado.toLocaleString('es-PE')
  if (unidad === '%') return `${texto} %`
  return `${texto} ${unidad}`
}

/* Filas de la ficha: la variable, el valor del contribuyente y la mediana de su segmento. */
export function filasFicha(variables, ficha) {
  return variables.map((v) => ({
    clave: v.clave,
    etiqueta: v.etiqueta,
    valor: formatearValor(ficha.valores?.[v.clave], v.unidad),
    mediana: formatearValor(ficha.medianasSegmento?.[v.clave], v.unidad),
  }))
}

export function porcentajeVarianza(modelo) {
  const v = modelo?.varianzaExplicada ?? []
  return v.length >= 2 ? Math.round((v[0] + v[1]) * 100) : null
}
