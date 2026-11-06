import { SERVICIOS, TRIBUTO_ARBITRIOS, TRIBUTO_PREDIAL, montoServicio, nombreMes } from './estadoCuenta.js'
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
    interes: redondear(total.interes + importe(fila.interesMoratorio)),
  }), { emitido: 0, pagado: 0, deuda: 0, vencido: 0, vigente: 0, interes: 0 })
}

export function filtrarCuenta(filas, { anio = '', tributo = '', estado = '', texto = '', vista = 'obligaciones' } = {}) {
  return filas.filter((fila) => (!anio || String(fila.anio) === String(anio))
    && (!tributo || fila.tributo === tributo) && (!estado || fila.estado === estado)
    && (vista !== 'pagos' || fila.pagado > 0)
    && normalizar(`${fila.tributo} ${fila.periodo}`).includes(normalizar(texto)))
}

// El Impuesto Predial va por cuota (4 al año) y los arbitrios por mes: una fila por cuota de predial y una por mes de
// arbitrios que suma todos los predios del contribuyente. Se conservan los IDs originales para pagar.
function claveDe(fila) {
  if (fila.tributoCodigo === TRIBUTO_PREDIAL) return `predial:${fila.anio}-${fila.cuota}`
  if (fila.tributoCodigo === TRIBUTO_ARBITRIOS) return `arbitrios:${fila.anio}-${fila.cuota}`
  return `obligacion:${fila.id}`
}

function tituloDe(fila) {
  if (fila.tributoCodigo === TRIBUTO_PREDIAL) return 'Impuesto Predial'
  if (fila.tributoCodigo === TRIBUTO_ARBITRIOS) return 'Arbitrios municipales'
  return fila.tributo
}

function etiquetaDe(fila) {
  if (fila.tributoCodigo === TRIBUTO_PREDIAL) return `${fila.cuota}.ª cuota ${fila.anio}`
  if (fila.tributoCodigo === TRIBUTO_ARBITRIOS) return `${nombreMes(fila.cuota)} ${fila.anio}`
  return fila.periodo
}

export function agruparCuentaPorPeriodo(filas) {
  const grupos = new Map()
  for (const fila of filas) {
    const clave = claveDe(fila)
    if (!grupos.has(clave)) {
      grupos.set(clave, {
        id: clave, titulo: tituloDe(fila), etiqueta: etiquetaDe(fila), periodo: fila.periodo, anio: fila.anio,
        tieneDetalle: fila.tributoCodigo === TRIBUTO_ARBITRIOS, conceptos: [],
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
      ...grupo, emitido: totales.emitido, pagado: totales.pagado, deuda: totales.deuda, interes: totales.interes,
      estado, fechaVencimiento: vencimientos.length === 1 ? vencimientos[0] : null,
      idsPendientes: grupo.conceptos.filter((fila) => fila.deuda > 0).map((fila) => fila.id),
      porServicio: SERVICIOS.map((servicio) => ({
        ...servicio,
        monto: redondear(grupo.conceptos.reduce((total, fila) => total + importe(montoServicio(fila, servicio.codigo)), 0)),
      })).filter((servicio) => servicio.monto > 0),
    }
  })
}

export function filtrarPeriodos(grupos, { anio = '', estado = '', texto = '', vista = 'obligaciones' } = {}) {
  return grupos.filter((grupo) => (!anio || String(grupo.anio) === String(anio))
    && (!estado || grupo.estado === estado)
    && (vista !== 'pagos' || grupo.pagado > 0)
    && normalizar(`${grupo.titulo} ${grupo.etiqueta} ${grupo.periodo} ${grupo.conceptos.map((fila) => `${fila.tributo} ${fila.codigoPredio ?? ''}`).join(' ')}`)
      .includes(normalizar(texto)))
}
