export const ESTADOS_ARBITRIO = {
  PAGADA: { etiqueta: 'Pagada', clase: 'ok' },
  PENDIENTE: { etiqueta: 'Pendiente', clase: 'aviso' },
  VENCIDA: { etiqueta: 'Vencida', clase: 'critico' },
}

const CONDICIONES_PROPIEDAD = {
  PROPIETARIO: 'Propietario',
  COPROPIETARIO: 'Copropietario',
}

export function estadoArbitrio(estado) {
  return ESTADOS_ARBITRIO[estado] ?? { etiqueta: estado || 'Sin estado', clase: 'neutro' }
}

export function etiquetaCondicion(condicion) {
  if (!condicion) return '—'
  return CONDICIONES_PROPIEDAD[String(condicion).toUpperCase()] ?? condicion
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
