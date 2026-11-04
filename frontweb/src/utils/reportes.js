const FILTROS_VALIDOS = new Set(['anio', 'anioDesde', 'anioHasta', 'zona', 'tributo', 'regimen', 'tipoDocumento', 'etapa', 'beneficio', 'montoMinimo'])
const CAMPOS_FILTRO = ['anio', 'anioDesde', 'anioHasta', 'zona', 'tributo', 'regimen', 'tipoDocumento', 'etapa', 'beneficio', 'montoMinimo']

export const TRIBUTOS = [
  { valor: 'IMPUESTO_PREDIAL', etiqueta: 'Impuesto Predial' },
  { valor: 'ARBITRIOS', etiqueta: 'Arbitrios' },
]

export const TIPOS_DOCUMENTO = [
  { valor: 'DNI', etiqueta: 'DNI' },
  { valor: 'RUC', etiqueta: 'RUC' },
  { valor: 'CE', etiqueta: 'Carné de extranjería' },
]

export const ETAPAS_COBRANZA = [
  { valor: 'MORA_TEMPRANA', etiqueta: 'Mora temprana' },
  { valor: 'CAMPANA', etiqueta: 'Campaña persuasiva' },
  { valor: 'VALOR_EMITIDO', etiqueta: 'Valor emitido' },
  { valor: 'ESPERA_LEGAL', etiqueta: 'Espera legal' },
  { valor: 'COACTIVA', etiqueta: 'Exigible en vía coactiva' },
]

export const FILTROS_BENEFICIO = [
  { valor: 'AMBOS', etiqueta: 'Ambos' },
  { valor: 'CON_BENEFICIO', etiqueta: 'Con beneficio' },
  { valor: 'SIN_BENEFICIO', etiqueta: 'Sin beneficio' },
]

export function filtrosDisponibles(reporte) {
  return (reporte?.filtros || []).filter((filtro) => FILTROS_VALIDOS.has(filtro))
}

export function opcionesZona(zonas, valorActual) {
  const opciones = Array.isArray(zonas) ? zonas.filter(Boolean) : []
  const actual = valorActual?.trim()
  return [...new Set(actual ? [...opciones, actual] : opciones)]
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
  const anioDesde = permitidos.has('anioDesde') ? Number(filtros.anioDesde) : null
  const anioHasta = permitidos.has('anioHasta') ? Number(filtros.anioHasta) : null
  if (permitidos.has('anioDesde') && (!Number.isInteger(anioDesde) || anioDesde < 1900 || anioDesde > 2100)) {
    errores.anioDesde = 'Ingresa un año entre 1900 y 2100.'
  }
  if (permitidos.has('anioHasta') && (!Number.isInteger(anioHasta) || anioHasta < 1900 || anioHasta > 2100)) {
    errores.anioHasta = 'Ingresa un año entre 1900 y 2100.'
  }
  if (permitidos.has('anioDesde') && permitidos.has('anioHasta')
      && Number.isInteger(anioDesde) && Number.isInteger(anioHasta) && anioDesde > anioHasta) {
    errores.anioHasta = 'El año final no puede ser menor que el año inicial.'
  }
  if (permitidos.has('zona') && filtros.zona?.trim().length > 20) errores.zona = 'La zona no puede superar 20 caracteres.'
  if (permitidos.has('tributo') && filtros.tributo && !TRIBUTOS.some(({ valor }) => valor === filtros.tributo)) errores.tributo = 'Selecciona un tributo válido.'
  if (permitidos.has('regimen') && filtros.regimen?.trim().length > 40) errores.regimen = 'El régimen no puede superar 40 caracteres.'
  if (permitidos.has('tipoDocumento') && filtros.tipoDocumento && !TIPOS_DOCUMENTO.some(({ valor }) => valor === filtros.tipoDocumento)) errores.tipoDocumento = 'Selecciona un tipo de documento válido.'
  if (permitidos.has('etapa') && filtros.etapa && !ETAPAS_COBRANZA.some(({ valor }) => valor === filtros.etapa)) errores.etapa = 'Selecciona una etapa válida.'
  if (permitidos.has('beneficio') && filtros.beneficio && !FILTROS_BENEFICIO.some(({ valor }) => valor === filtros.beneficio)) errores.beneficio = 'Selecciona un filtro de beneficio válido.'
  if (permitidos.has('montoMinimo') && filtros.montoMinimo !== '') {
    const monto = Number(filtros.montoMinimo)
    if (!Number.isFinite(monto) || monto < 0) errores.montoMinimo = 'Ingresa un monto igual o mayor a cero.'
  }
  return errores
}

export function parametrosReporte(reporte, filtros) {
  const parametros = new URLSearchParams()
  const permitidos = new Set(filtrosDisponibles(reporte))
  for (const clave of CAMPOS_FILTRO) {
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

export function etiquetaTipoDocumento(tipoDocumento) {
  return TIPOS_DOCUMENTO.find(({ valor }) => valor === tipoDocumento)?.etiqueta || tipoDocumento || 'Todos'
}

export function etiquetaEtapa(etapa) {
  return ETAPAS_COBRANZA.find(({ valor }) => valor === etapa)?.etiqueta || etapa || 'Todas'
}

export function etiquetaBeneficio(beneficio) {
  return FILTROS_BENEFICIO.find(({ valor }) => valor === beneficio)?.etiqueta || beneficio || 'Ambos'
}

export function porcentaje(valor) {
  const numero = Number(valor)
  return Number.isFinite(numero) ? `${numero.toLocaleString('es-PE', { maximumFractionDigits: 2 })} %` : '—'
}
