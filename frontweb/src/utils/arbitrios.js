export const ESTADOS_ARBITRIO = {
  PAGADA: { etiqueta: 'Pagada', clase: 'ok' },
  PENDIENTE: { etiqueta: 'Pendiente', clase: 'aviso' },
  VENCIDA: { etiqueta: 'Vencida', clase: 'critico' },
}

const CONDICIONES_PROPIEDAD = {
  PROPIETARIO: 'Propietario',
  COPROPIETARIO: 'Copropietario',
}

export const FILTROS_ARBITRIOS_INICIALES = { anio: '', estado: '' }

export function estadoArbitrio(estado) {
  return ESTADOS_ARBITRIO[estado] ?? { etiqueta: estado || 'Sin estado', clase: 'neutro' }
}

export function etiquetaCondicion(condicion) {
  if (!condicion) return '—'
  return CONDICIONES_PROPIEDAD[String(condicion).toUpperCase()] ?? condicion
}

/* Opciones de año (de más reciente a más antiguo) y de estado presentes en las cuotas. */
export function opcionesDeArbitrios(cuotas = []) {
  const anios = [...new Set(cuotas.map((cuota) => cuota.anio).filter((anio) => anio != null))]
    .sort((a, b) => Number(b) - Number(a))

  const presentes = new Set(cuotas.map((cuota) => cuota.estado).filter(Boolean))
  const estados = Object.entries(ESTADOS_ARBITRIO)
    .filter(([valor]) => presentes.has(valor))
    .map(([valor, { etiqueta }]) => ({ valor, etiqueta }))

  // Un estado fuera del catálogo se muestra al final con su propio valor, sin inventar etiqueta.
  for (const valor of presentes) {
    if (!ESTADOS_ARBITRIO[valor]) estados.push({ valor, etiqueta: valor })
  }

  return { anios, estados }
}

/* Aplica los filtros de año y estado con lógica AND; un filtro vacío no restringe. */
export function filtrarCuotas(cuotas = [], filtros = {}) {
  return cuotas.filter((cuota) => (
    (!filtros.anio || String(cuota.anio) === String(filtros.anio))
    && (!filtros.estado || cuota.estado === filtros.estado)
  ))
}

/*
 * Con copropiedad, cada titular tiene sus propias cuotas del mismo periodo. Se agrupan por
 * codigoContribuyente y el nombre se toma de predio.titulares; no se inventan titulares.
 */
export function agruparCuotasPorTitular({ cuotas = [], predio } = {}) {
  const titulares = new Map(
    (predio?.titulares ?? []).map((titular) => [titular.codigoContribuyente, titular]),
  )
  const grupos = new Map()

  for (const cuota of cuotas) {
    const codigo = cuota.codigoContribuyente
    if (!grupos.has(codigo)) {
      const titular = titulares.get(codigo)
      grupos.set(codigo, {
        codigoContribuyente: codigo,
        nombre: titular?.nombre || codigo || 'Titular no identificado',
        condicionPropiedad: titular?.condicionPropiedad ?? null,
        porcentajePropiedad: titular?.porcentajePropiedad ?? null,
        cuotas: [],
      })
    }
    grupos.get(codigo).cuotas.push(cuota)
  }

  return [...grupos.values()]
}
