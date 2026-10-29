const ESTADOS = { PAGADA: 'Pagada', VENCIDA: 'Vencida', PENDIENTE: 'Pendiente' }
const TRIBUTOS_TRIMESTRALES = new Set(['Impuesto Predial', 'Arbitrios Municipales'])

export function normalizarCuenta(datos) {
  if (!datos?.contribuyente?.codigo || !datos.fechaCorte || !Array.isArray(datos.filas)) {
    throw new Error('No se recibió un estado de cuenta válido. Actualiza la consulta.')
  }
  const filas = datos.filas.map((fila) => {
    const campos = ['emitido', 'pagado', 'deuda']
    if (campos.some((campo) => fila[campo] == null || fila[campo] === ''
      || !Number.isFinite(Number(fila[campo])) || Number(fila[campo]) < 0)
      || !fila.id || !ESTADOS[String(fila.estado).toUpperCase()]) {
      throw new Error('No se pudieron interpretar los importes de la cuenta. Actualiza la consulta.')
    }
    return {
      ...fila, id: String(fila.id), emitido: Number(fila.emitido), pagado: Number(fila.pagado),
      deuda: Number(fila.deuda), estado: ESTADOS[String(fila.estado).toUpperCase()],
      anio: /^\d{4}/.exec(fila.periodo || '')?.[0] || '',
    }
  })
  return { ...datos, filas, desactualizado: Boolean(datos.desactualizado || datos.esUltimaCopia) }
}

// Estos totales solo presentan la cuenta. La cotización del pago viene exclusivamente del backend.
export function resumirCuenta(filas) {
  const centimos = filas.reduce((total, fila) => ({
    emitido: total.emitido + Math.round(fila.emitido * 100),
    pagado: total.pagado + Math.round(fila.pagado * 100),
    deuda: total.deuda + Math.round(fila.deuda * 100),
    vencido: total.vencido + (fila.estado === 'Vencida' ? Math.round(fila.deuda * 100) : 0),
  }), { emitido: 0, pagado: 0, deuda: 0, vencido: 0 })
  return Object.fromEntries(Object.entries(centimos).map(([campo, valor]) => [campo, valor / 100]))
}

export function agruparPeriodos(filas) {
  const grupos = new Map()
  for (const fila of filas) {
    const trimestral = /^\d{4}-T[1-4]$/.test(fila.periodo || '') && TRIBUTOS_TRIMESTRALES.has(fila.tributo)
    const id = trimestral ? `periodo:${fila.periodo}` : `obligacion:${fila.id}`
    if (!grupos.has(id)) grupos.set(id, {
      id, periodo: fila.periodo, anio: fila.anio,
      titulo: trimestral ? 'Tributos municipales' : fila.tributo, conceptos: [],
    })
    grupos.get(id).conceptos.push(fila)
  }
  return Array.from(grupos.values(), (grupo) => {
    const totales = resumirCuenta(grupo.conceptos)
    return {
      ...grupo, ...totales,
      estado: totales.deuda === 0 ? 'Pagada' : totales.vencido > 0 ? 'Vencida' : 'Pendiente',
    }
  }).sort((a, b) => String(b.periodo).localeCompare(String(a.periodo)))
}
