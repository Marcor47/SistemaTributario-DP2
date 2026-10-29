const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']

export function opcionesPeriodo(indicador, anio) {
  if (!indicador) return []
  return Array.from({ length: indicador.periodosPorAnio }, (_, indice) => {
    const numero = indice + 1
    let etiqueta = String(anio)
    if (indicador.periodicidad === 'MENSUAL') etiqueta = `${MESES[indice]} ${anio}`
    if (indicador.periodicidad === 'TRIMESTRAL') etiqueta = `Trimestre ${numero} de ${anio}`
    return { numero, etiqueta }
  })
}

export function periodoSugerido(indicador, anio, hoy = new Date()) {
  if (!indicador || Number(anio) !== hoy.getFullYear()) return 1
  if (indicador.periodicidad === 'MENSUAL') return hoy.getMonth() + 1
  if (indicador.periodicidad === 'TRIMESTRAL') return Math.floor(hoy.getMonth() / 3) + 1
  return 1
}

export function etiquetaUnidad(unidad) {
  return { PORCENTAJE: 'Porcentaje', SOLES: 'Soles', CANTIDAD: 'Cantidad' }[unidad] || unidad
}

export function formatoValorMeta(valor, unidad) {
  const numero = Number(valor)
  if (!Number.isFinite(numero)) return '—'
  if (unidad === 'PORCENTAJE') return `${numero.toLocaleString('es-PE', { maximumFractionDigits: 2 })} %`
  if (unidad === 'SOLES') return numero.toLocaleString('es-PE', { style: 'currency', currency: 'PEN', maximumFractionDigits: 2 })
  return numero.toLocaleString('es-PE', { maximumFractionDigits: 2 })
}

export function enlaceMeta(alerta) {
  const parametros = new URLSearchParams()
  if (alerta?.idMeta != null) parametros.set('metaId', alerta.idMeta)
  if (alerta?.indicadorCodigo) parametros.set('indicadorCodigo', alerta.indicadorCodigo)
  if (alerta?.anio != null) parametros.set('anio', alerta.anio)
  const consulta = parametros.toString()
  return consulta ? `/metas?${consulta}` : '/metas'
}

export function validarMeta({ indicador, anio, numeroPeriodo, valorMeta, observacion }) {
  const errores = {}
  const valor = Number(valorMeta)
  if (!indicador) errores.indicadorCodigo = 'Selecciona un indicador.'
  if (!Number.isInteger(Number(anio)) || Number(anio) < 2000 || Number(anio) > 2100) errores.anio = 'Ingresa un año entre 2000 y 2100.'
  if (!Number.isInteger(Number(numeroPeriodo)) || Number(numeroPeriodo) < 1 || Number(numeroPeriodo) > (indicador?.periodosPorAnio ?? 0)) errores.numeroPeriodo = 'Selecciona un periodo válido.'
  if (valorMeta === '' || !Number.isFinite(valor)) errores.valorMeta = 'Ingresa el valor de la meta.'
  else if (valor < 0) errores.valorMeta = 'El valor no puede ser negativo.'
  else if (indicador?.unidadMedida === 'PORCENTAJE' && valor > 100) errores.valorMeta = 'El porcentaje no puede superar 100.'
  else if (!/^\d{1,12}(\.\d{1,2})?$/.test(String(valorMeta))) errores.valorMeta = 'Ingresa como máximo 12 enteros y 2 decimales.'
  if (observacion.length > 250) errores.observacion = 'La observación admite hasta 250 caracteres.'
  return errores
}
