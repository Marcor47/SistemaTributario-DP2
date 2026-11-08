const FILTROS_VALIDOS = new Set(['anio', 'periodo', 'anioDesde', 'anioHasta', 'zona', 'tributo', 'regimen', 'tipoDocumento', 'etapa', 'beneficio', 'montoMinimo'])
const CAMPOS_FILTRO = ['anio', 'periodo', 'anioDesde', 'anioHasta', 'zona', 'tributo', 'regimen', 'tipoDocumento', 'etapa', 'beneficio', 'montoMinimo']

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

const COLUMNAS_CONTRIBUYENTE = ['codigo', 'razonSocial', 'regimen', 'tipoDocumento', 'numeroDocumento']
const COLUMNAS_DOMICILIO = ['via', 'numero', 'departamento', 'interior', 'manzana', 'lote', 'distrito']
const COLUMNAS_DATOS_CONTRIBUYENTE = ['condicion', 'tipoPersona', 'correo', 'telefonos']

export const GRUPOS_PRICOS = [
  { clave: 'contribuyente', etiqueta: 'Contribuyente', columnas: COLUMNAS_CONTRIBUYENTE },
  { clave: 'domicilio', etiqueta: 'Domicilio fiscal', columnas: ['domicilioFiscal', ...COLUMNAS_DOMICILIO] },
  { clave: 'baseImponible', etiqueta: 'Base imponible', columnas: ['baseImponible'] },
  { clave: 'emision', etiqueta: 'Emisión', columnas: ['emisionPredial', 'emisionArbitrios'] },
  { clave: 'deudaAnterior', etiqueta: 'Deuda años anteriores', columnas: ['deudaAnteriorPredial', 'deudaAnteriorArbitrios'] },
  {
    clave: 'deudaActual',
    etiqueta: 'Deuda con el año de corte',
    columnas: ['deudaActualPredial', 'deudaActualArbitrios', 'porcentajeDescuentoArbitrios'],
  },
  { clave: 'datosContribuyente', etiqueta: 'Datos del contribuyente', columnas: COLUMNAS_DATOS_CONTRIBUYENTE },
]

export const GRUPOS_MOROSOS = [
  { clave: 'contribuyente', etiqueta: 'Contribuyente', columnas: COLUMNAS_CONTRIBUYENTE },
  { clave: 'domicilio', etiqueta: 'Domicilio fiscal', columnas: [...COLUMNAS_DOMICILIO, 'zonas'] },
  { clave: 'deuda', etiqueta: 'Deuda vencida', columnas: ['deudaVencida', 'deudaSinBeneficio'] },
  { clave: 'datosContribuyente', etiqueta: 'Datos del contribuyente', columnas: COLUMNAS_DATOS_CONTRIBUYENTE },
]

export const GRUPOS_DETALLE_MOROSOS = [
  { clave: 'obligacion', etiqueta: 'Obligación', columnas: ['anio', 'codigoPredio', 'zona', 'tributo', 'periodo'] },
  { clave: 'vencimiento', etiqueta: 'Vencimiento', columnas: ['fechaVencimiento', 'diasVencido', 'etapaCobranza'] },
  { clave: 'montos', etiqueta: 'Montos', columnas: ['montoSinBeneficio', 'montoConBeneficio'] },
]

export const CRITERIOS_PRICOS = [
  { valor: 'BASE_IMPONIBLE', etiqueta: 'Base imponible', descripcion: 'Ordena por la mayor base imponible de los predios del contribuyente.' },
  { valor: 'DEUDA_TOTAL', etiqueta: 'Deuda total', descripcion: 'Ordena por la mayor deuda vencida de todos los años.' },
]

export function validarPricos({ criterio, limite }) {
  const errores = {}
  if (!CRITERIOS_PRICOS.some(({ valor }) => valor === criterio)) {
    errores.criterio = 'Selecciona un criterio válido.'
  }
  const cantidad = Number(limite)
  if (limite === '' || limite == null || !Number.isInteger(cantidad) || cantidad < 1 || cantidad > 200) {
    errores.limite = 'Ingresa un límite entre 1 y 200.'
  }
  return errores
}

export function parametrosPricos({ criterio, limite }) {
  const parametros = new URLSearchParams()
  if (criterio) parametros.set('criterio', String(criterio).trim())
  if (limite !== '' && limite != null) parametros.set('limite', String(limite).trim())
  return parametros
}

export function etiquetaCriterioPricos(criterio) {
  return CRITERIOS_PRICOS.find(({ valor }) => valor === criterio)?.etiqueta || criterio || 'No definido'
}

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
  if (permitidos.has('periodo') && filtros.periodo) {
    const periodo = Number(filtros.periodo)
    if (!Number.isInteger(periodo) || periodo < 2000 || periodo > 2100) errores.periodo = 'Ingresa un periodo entre 2000 y 2100.'
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

export function paginarFilas(filas, paginaSolicitada, tamanioSolicitado) {
  const tamanio = [10, 25, 50, 100].includes(Number(tamanioSolicitado)) ? Number(tamanioSolicitado) : 10
  const total = Array.isArray(filas) ? filas.length : 0
  const totalPaginas = Math.max(1, Math.ceil(total / tamanio))
  const pagina = Math.min(Math.max(1, Number(paginaSolicitada) || 1), totalPaginas)
  const inicio = (pagina - 1) * tamanio
  return { filas: (filas || []).slice(inicio, inicio + tamanio), pagina, tamanio, total, totalPaginas, desde: total ? inicio + 1 : 0, hasta: Math.min(inicio + tamanio, total) }
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

/* Ordena los grupos según la primera columna que aparece en el orden plano. */
export function ordenarGrupos(grupos, orden) {
  const posicion = (grupo) => {
    const indices = grupo.columnas.map((columna) => orden.indexOf(columna)).filter((indice) => indice >= 0)
    return indices.length ? Math.min(...indices) : Number.MAX_SAFE_INTEGER
  }
  return [...grupos].sort((a, b) => posicion(a) - posicion(b))
}

/* Alterna todo un grupo: oculta sus columnas si ya estaban todas, o las muestra si faltaba alguna. */
export function alternarGrupoColumnas(visibles, grupo, orden) {
  const visiblesSet = new Set(visibles)
  const todasVisibles = grupo.columnas.every((columna) => visiblesSet.has(columna))
  if (todasVisibles) {
    if (visiblesSet.size === grupo.columnas.length) return visibles
    grupo.columnas.forEach((columna) => visiblesSet.delete(columna))
  } else {
    grupo.columnas.forEach((columna) => visiblesSet.add(columna))
  }
  return orden.filter((columna) => visiblesSet.has(columna))
}

/* Reconstruye el orden plano al mover grupos completos, conservando el orden interno de cada grupo. */
export function reordenarGruposColumnas(orden, grupos, claves) {
  const porClave = new Map(grupos.map((grupo) => [grupo.clave, grupo]))
  return claves.flatMap((clave) => {
    const grupo = porClave.get(clave)
    return grupo ? orden.filter((columna) => grupo.columnas.includes(columna)) : []
  })
}

/* Reconstruye el orden plano al mover columnas dentro de un grupo. */
export function reordenarColumnasDeGrupo(orden, grupos, grupoClave, columnas) {
  const porClave = new Map(grupos.map((grupo) => [grupo.clave, grupo]))
  return ordenarGrupos(grupos, orden).flatMap(({ clave }) => {
    const grupo = porClave.get(clave)
    if (clave === grupoClave) return columnas.filter((columna) => grupo.columnas.includes(columna))
    return orden.filter((columna) => grupo.columnas.includes(columna))
  })
}

/* Restaura un orden guardado: descarta columnas inválidas, agrega las faltantes y mantiene los grupos contiguos. */
export function normalizarOrdenColumnas(orden, grupos) {
  const todas = grupos.flatMap((grupo) => grupo.columnas)
  const guardadas = Array.isArray(orden)
    ? orden.filter((columna, indice) => todas.includes(columna) && orden.indexOf(columna) === indice)
    : []
  const propuesto = [...guardadas, ...todas.filter((columna) => !guardadas.includes(columna))]
  return ordenarGrupos(grupos, propuesto).flatMap((grupo) => {
    return grupo.columnas
      .filter((columna) => propuesto.includes(columna))
      .sort((a, b) => propuesto.indexOf(a) - propuesto.indexOf(b))
  })
}
