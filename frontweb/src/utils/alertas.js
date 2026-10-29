import { etiquetaTributo } from './morosidad.js'

/* HU039: funciones de presentación de las alertas de desvío de meta. */

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']

export function etiquetaPeriodo(periodo) {
  if (!periodo) return '—'
  const mensual = /^(\d{4})-(\d{2})$/.exec(periodo)
  if (mensual) {
    const [, anio, mes] = mensual
    return `${MESES[Number(mes) - 1] || mes} ${anio}`
  }
  const trimestral = /^(\d{4})-T([1-4])$/.exec(periodo)
  if (trimestral) return `Trimestre ${trimestral[2]} de ${trimestral[1]}`
  return periodo
}

export function esPeriodoActual(periodo, hoy = new Date()) {
  if (!periodo) return false
  const anio = hoy.getFullYear()
  if (/^\d{4}-\d{2}$/.test(periodo)) {
    const mes = String(hoy.getMonth() + 1).padStart(2, '0')
    return periodo === `${anio}-${mes}`
  }
  if (/^\d{4}-T[1-4]$/.test(periodo)) {
    const trimestre = Math.floor(hoy.getMonth() / 3) + 1
    return periodo === `${anio}-T${trimestre}`
  }
  if (/^\d{4}$/.test(periodo)) return periodo === String(anio)
  return false
}

export function filtrarAlertasDelPeriodo(alertas, hoy = new Date()) {
  return (Array.isArray(alertas) ? alertas : []).filter((alerta) => esPeriodoActual(alerta.periodo, hoy))
}

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

export function indexarAlertasPorMeta(alertas) {
  const mapa = new Map()
  for (const alerta of Array.isArray(alertas) ? alertas : []) {
    if (alerta?.idMeta != null && !mapa.has(alerta.idMeta)) mapa.set(alerta.idMeta, alerta)
  }
  return mapa
}
