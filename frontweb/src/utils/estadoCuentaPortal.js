import { normalizar } from './formato.js'

const importe = (valor) => Number.isFinite(Number(valor)) ? Number(valor) : 0
const redondear = (valor) => Math.round((valor + Number.EPSILON) * 100) / 100

export function normalizarCuenta(respuesta) {
  return {
    ...respuesta,
    desactualizado: Boolean(respuesta.desactualizado || respuesta.esUltimaCopia),
    filas: (respuesta.filas || []).map((fila) => {
      const emitido = importe(fila.emitido)
      const pagado = importe(fila.pagado)
      const deuda = fila.deuda == null ? Math.max(0, redondear(emitido - pagado)) : importe(fila.deuda)
      const estado = { PAGADA: 'Pagada', VENCIDA: 'Vencida', PENDIENTE: 'Pendiente' }[String(fila.estado).toUpperCase()]
        || (deuda === 0 ? 'Pagada' : fila.fechaVencimiento && fila.fechaVencimiento < respuesta.fechaCorte ? 'Vencida' : 'Pendiente')
      return { ...fila, id: String(fila.id), emitido, pagado, deuda, estado,
        anio: fila.anio ?? /^\d{4}/.exec(fila.periodo || '')?.[0] ?? null }
    }),
  }
}

export function resumirCuenta(filas) {
  return filas.reduce((total, fila) => ({
    emitido: redondear(total.emitido + fila.emitido), pagado: redondear(total.pagado + fila.pagado),
    deuda: redondear(total.deuda + fila.deuda),
    vencido: redondear(total.vencido + (fila.estado === 'Vencida' ? fila.deuda : 0)),
    vigente: redondear(total.vigente + (fila.estado === 'Pendiente' ? fila.deuda : 0)),
  }), { emitido: 0, pagado: 0, deuda: 0, vencido: 0, vigente: 0 })
}

export function filtrarCuenta(filas, { anio = '', tributo = '', estado = '', texto = '', vista = 'obligaciones' } = {}) {
  return filas.filter((fila) => (!anio || String(fila.anio) === String(anio))
    && (!tributo || fila.tributo === tributo) && (!estado || fila.estado === estado)
    && (vista !== 'pagos' || fila.pagado > 0)
    && normalizar(`${fila.tributo} ${fila.periodo}`).includes(normalizar(texto)))
}

const CONCEPTOS_TRIMESTRALES = new Set([
  'Impuesto Predial', 'Limpieza Pública', 'Parques y Jardines', 'Seguridad Ciudadana',
])

// Agrupa los conceptos del mismo trimestre y conserva los IDs originales para pagos.
export function agruparCuentaPorPeriodo(filas) {
  const grupos = new Map()
  for (const fila of filas) {
    const esTrimestral = /^\d{4}-T[1-4]$/.test(fila.periodo || '') && CONCEPTOS_TRIMESTRALES.has(fila.tributo)
    const clave = esTrimestral ? `trimestre:${fila.periodo}` : `obligacion:${fila.id}`
    if (!grupos.has(clave)) {
      grupos.set(clave, {
        id: clave, titulo: esTrimestral ? 'Tributos municipales' : fila.tributo,
        periodo: fila.periodo, anio: fila.anio, esTrimestral, conceptos: [],
      })
    }
    grupos.get(clave).conceptos.push(fila)
  }
  return Array.from(grupos.values(), (grupo) => {
    const totales = resumirCuenta(grupo.conceptos)
    const vencimientos = [...new Set(grupo.conceptos.map((fila) => fila.fechaVencimiento))]
    const estado = totales.deuda === 0 ? 'Pagada'
      : grupo.conceptos.some((fila) => fila.estado === 'Vencida' && fila.deuda > 0) ? 'Vencida' : 'Pendiente'
    return {
      ...grupo, emitido: totales.emitido, pagado: totales.pagado, deuda: totales.deuda, estado,
      fechaVencimiento: vencimientos.length === 1 ? vencimientos[0] : null,
      idsPendientes: grupo.conceptos.filter((fila) => fila.deuda > 0).map((fila) => fila.id),
    }
  })
}

export function filtrarPeriodos(grupos, { anio = '', estado = '', texto = '', vista = 'obligaciones' } = {}) {
  return grupos.filter((grupo) => (!anio || String(grupo.anio) === String(anio))
    && (!estado || grupo.estado === estado)
    && (vista !== 'pagos' || grupo.pagado > 0)
    && normalizar(`${grupo.titulo} ${grupo.periodo} ${grupo.conceptos.map((fila) => fila.tributo).join(' ')}`)
      .includes(normalizar(texto)))
}
