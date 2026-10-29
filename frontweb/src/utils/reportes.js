const FILTROS_VALIDOS = new Set(['anio', 'zona', 'tributo', 'montoMinimo'])

export const TRIBUTOS = [
  { valor: 'IMPUESTO_PREDIAL', etiqueta: 'Impuesto Predial' },
  { valor: 'ARBITRIOS', etiqueta: 'Arbitrios' },
]

export function filtrosDisponibles(reporte) {
  return (reporte?.filtros || []).filter((filtro) => FILTROS_VALIDOS.has(filtro))
}

export function limpiarFiltros(reporte, filtros) {
  const permitidos = new Set(filtrosDisponibles(reporte))
  return Object.fromEntries(Object.entries(filtros).map(([clave, valor]) => [clave, permitidos.has(clave) ? valor : '']))
}

export function validarFiltros(reporte, filtros) {
  const permitidos = new Set(filtrosDisponibles(reporte))
  const errores = {}
  if (permitidos.has('anio') && filtros.anio) {
    const anio = Number(filtros.anio)
    if (!Number.isInteger(anio) || anio < 2000 || anio > 2100) errores.anio = 'Ingresa un año entre 2000 y 2100.'
  }
  if (permitidos.has('zona') && filtros.zona?.trim().length > 20) errores.zona = 'La zona no puede superar 20 caracteres.'
  if (permitidos.has('tributo') && filtros.tributo && !TRIBUTOS.some(({ valor }) => valor === filtros.tributo)) errores.tributo = 'Selecciona un tributo válido.'
  if (permitidos.has('montoMinimo') && filtros.montoMinimo !== '') {
    const monto = Number(filtros.montoMinimo)
    if (!Number.isFinite(monto) || monto < 0) errores.montoMinimo = 'Ingresa un monto igual o mayor a cero.'
  }
  return errores
}

export function parametrosReporte(reporte, filtros) {
  const parametros = new URLSearchParams()
  const permitidos = new Set(filtrosDisponibles(reporte))
  for (const clave of ['anio', 'zona', 'tributo', 'montoMinimo']) {
    const valor = filtros[clave]
    if (permitidos.has(clave) && valor !== '' && valor != null) parametros.set(clave, String(valor).trim())
  }
  return parametros
}

export function metricaReporte(tipo, fila) {
  if (tipo === 'MOROSOS') return Number(fila.deudaVencida) || 0
  if (tipo === 'PRICOS') return Number(fila.emitidoAnio) || 0
  return Number(fila.pagadoAnio) || 0
}

export function etiquetaTributo(tributo) {
  return TRIBUTOS.find(({ valor }) => valor === tributo)?.etiqueta || tributo || 'Todos'
}

export function porcentaje(valor) {
  const numero = Number(valor)
  return Number.isFinite(numero) ? `${numero.toLocaleString('es-PE', { maximumFractionDigits: 2 })} %` : '—'
}
