export const FILTROS_DEUDA_INICIALES = { tributo: '', anio: '', estado: '' }

const unicos = (valores) => [...new Set(valores.filter(Boolean))]

export function opcionesDeuda(obligaciones = []) {
  return {
    tributos: unicos(obligaciones.map((obligacion) => obligacion.tributo)).sort((a, b) => a.localeCompare(b, 'es')),
    anios: unicos(obligaciones.map((obligacion) => obligacion.anio)).sort((a, b) => Number(b) - Number(a)),
    estados: unicos(obligaciones.map((obligacion) => obligacion.estado)).sort((a, b) => a.localeCompare(b, 'es')),
  }
}

export function filtrarObligacionesDeuda(obligaciones = [], filtros = FILTROS_DEUDA_INICIALES) {
  return obligaciones.filter((obligacion) => (
    (!filtros.tributo || obligacion.tributo === filtros.tributo)
    && (!filtros.anio || String(obligacion.anio) === String(filtros.anio))
    && (!filtros.estado || obligacion.estado === filtros.estado)
  ))
}
