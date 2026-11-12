export const TIPOS_REPORTE = [
  { valor: 'MOROSOS', etiqueta: 'Reporte de morosos' },
  { valor: 'PRICOS', etiqueta: 'Reporte de PRICOS' },
  { valor: 'BUENOS_PAGADORES', etiqueta: 'Reporte de buenos pagadores' },
]

export const FILTROS_SOLICITUDES_INICIALES = { usuario: '', tipo: '', desde: '', hasta: '' }

export function parametrosSolicitudesReporte(filtros, pagina, tamanio) {
  const parametros = new URLSearchParams({ pagina: String(Math.max(0, pagina - 1)), tamanio: String(tamanio) })
  if (filtros.usuario?.trim()) parametros.set('usuario', filtros.usuario.trim())
  if (filtros.tipo) parametros.set('tipo', filtros.tipo)
  if (filtros.desde) parametros.set('desde', filtros.desde)
  if (filtros.hasta) parametros.set('hasta', filtros.hasta)
  return parametros
}

export function validarPeriodoSolicitudes({ desde, hasta }) {
  if (Boolean(desde) !== Boolean(hasta)) return 'Selecciona ambas fechas para consultar un periodo.'
  if (desde && hasta && desde > hasta) return 'La fecha inicial no puede ser posterior a la fecha final.'
  return ''
}

export function etiquetaOperacion(operacion) {
  if (operacion === 'EXCEL') return 'Exportación Excel'
  if (operacion === 'PDF') return 'Exportación PDF'
  return 'Visualización en pantalla'
}

export function textoFiltrosAplicados(filtros) {
  if (!filtros?.trim() || filtros.trim() === '{}') return 'Sin filtros adicionales'
  try {
    const datos = JSON.parse(filtros)
    const etiquetas = {
      anio: 'Año', zona: 'Zona', tributo: 'Tributo', montoMinimo: 'Monto mínimo', periodo: 'Periodo',
      regimen: 'Régimen', tipoDocumento: 'Tipo de documento', etapa: 'Etapa', anioDesde: 'Año inicial',
      anioHasta: 'Año final', beneficio: 'Beneficio', criterio: 'Criterio', limite: 'Límite',
    }
    const partes = Object.entries(datos)
      .filter(([, valor]) => valor !== null && valor !== undefined && valor !== '')
      .map(([campo, valor]) => `${etiquetas[campo] || campo}: ${String(valor).replaceAll('_', ' ')}`)
    return partes.length ? partes.join(' · ') : 'Sin filtros adicionales'
  } catch {
    return filtros
  }
}
