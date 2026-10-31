export const FILTROS_COBRANZA_INICIALES = { periodo: '', etapa: '', tributo: '' }

/* Opciones únicas para los selectores, calculadas a partir de las obligaciones cargadas. */
export function opcionesDeCobranza(obligaciones = []) {
  const periodos = [...new Set(obligaciones.map((obligacion) => obligacion.periodo).filter(Boolean))]
    .sort()
  const tributos = [...new Set(obligaciones.map((obligacion) => obligacion.tributoDescripcion).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b, 'es'))

  const etapas = new Map()
  obligaciones.forEach((obligacion) => {
    if (obligacion.etapaActual) {
      etapas.set(obligacion.etapaActual, obligacion.etapaNombre || obligacion.etapaActual)
    }
  })

  return {
    periodos,
    tributos,
    etapas: [...etapas.entries()]
      .map(([valor, etiqueta]) => ({ valor, etiqueta }))
      .sort((a, b) => a.etiqueta.localeCompare(b.etiqueta, 'es')),
  }
}

/* Aplica los tres filtros con lógica AND; un filtro vacío no restringe. */
export function filtrarObligaciones(obligaciones = [], filtros = {}) {
  return obligaciones.filter((obligacion) => (
    (!filtros.periodo || obligacion.periodo === filtros.periodo)
    && (!filtros.etapa || obligacion.etapaActual === filtros.etapa)
    && (!filtros.tributo || obligacion.tributoDescripcion === filtros.tributo)
  ))
}
