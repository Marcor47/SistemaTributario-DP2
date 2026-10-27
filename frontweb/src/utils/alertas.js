import { etiquetaTributo } from './morosidad.js'

/* HU039: funciones de presentación de las alertas de desvío de meta. */

export function formatearPorcentaje(valor) {
  if (valor === null || valor === undefined || valor === '') return '—'
  const numero = Number(valor)
  if (!Number.isFinite(numero)) return '—'
  return `${numero.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} %`
}

export function formatearDesvio(valor) {
  if (valor === null || valor === undefined || valor === '') return '—'
  const numero = Number(valor)
  if (!Number.isFinite(numero)) return '—'
  if (Math.abs(numero) < 0.005) return formatearPorcentaje(0)
  const signo = numero > 0 ? '+' : '-'
  return `${signo}${formatearPorcentaje(Math.abs(numero))}`
}

export function formatearValorIndicador(valor, unidad) {
  if (valor === null || valor === undefined || valor === '') return '—'
  const numero = Number(valor)
  if (!Number.isFinite(numero)) return '—'
  if (unidad === 'PORCENTAJE') {
    return `${numero.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} %`
  }
  if (unidad === 'SOLES') {
    return `S/ ${numero.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  }
  return numero.toLocaleString('es-PE', { maximumFractionDigits: 2 })
}

export function etiquetaSentido(sentido) {
  return { ASCENDENTE: 'Ascendente', DESCENDENTE: 'Descendente' }[sentido] || sentido || '—'
}

export function etiquetaAlcance(alcance, valorAlcance) {
  if (alcance === 'TOTAL') return 'Total municipal'
  if (alcance === 'TRIBUTO') return valorAlcance ? etiquetaTributo(valorAlcance) : '—'
  if (alcance === 'ZONA') return valorAlcance || 'Zona'
  if (alcance === 'SEGMENTO') return valorAlcance || 'Segmento'
  return valorAlcance || '—'
}

export function explicarDesvio(alerta) {
  if (alerta?.desvioPorcentual === null || alerta?.desvioPorcentual === undefined || alerta?.desvioPorcentual === '') {
    return 'No se pudo determinar el desvío frente a la meta.'
  }
  const desvio = Number(alerta.desvioPorcentual)
  if (!Number.isFinite(desvio)) return 'No se pudo determinar el desvío frente a la meta.'
  if (Math.abs(desvio) < 0.005) return 'El valor observado coincide con la meta configurada.'

  const posicion = desvio > 0 ? 'por encima' : 'por debajo'
  const sentido = alerta?.sentido === 'DESCENDENTE'
    ? 'En un indicador descendente, superar la meta es desfavorable.'
    : alerta?.sentido === 'ASCENDENTE'
      ? 'En un indicador ascendente, quedar por debajo de la meta es desfavorable.'
      : ''
  return `El valor observado está ${formatearPorcentaje(Math.abs(desvio))} ${posicion} de la meta.${sentido ? ` ${sentido}` : ''}`
}

export function resumirAlertas(alertas) {
  const lista = Array.isArray(alertas) ? alertas : []
  const indicadores = new Set(lista.map((alerta) => alerta.indicadorCodigo).filter(Boolean))
  const fechas = lista.map((alerta) => alerta.fechaCorte).filter(Boolean).sort()
  return {
    total: lista.length,
    indicadores: indicadores.size,
    ultimaCorte: fechas.at(-1) || null,
  }
}
