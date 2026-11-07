import { pedir } from './api.js'

const ESTADOS = {
  A_TIEMPO: 'A tiempo',
  CON_ATRASO: 'Con atraso',
  VENCIDA_SIN_PAGAR: 'Vencida sin pagar',
  POR_VENCER: 'Por vencer',
}

const TRIBUTOS = { IMPUESTO_PREDIAL: 'Impuesto Predial', ARBITRIOS: 'Arbitrios Municipales' }

// HU010: regularidad de pago calculada por el backend sobre la cuenta corriente del contribuyente en SAMNET.
export function normalizarRegularidad(respuesta, contribuyente) {
  const cuotas = (respuesta.cuotas || []).map((cuota) => ({
    id: cuota.idObligacion,
    periodo: cuota.periodo,
    tributo: TRIBUTOS[cuota.tributo] || cuota.tributo,
    codigoPredio: cuota.codigoPredio,
    fechaVencimiento: cuota.fechaVencimiento,
    fechaPago: cuota.fechaPago,
    emitido: Number(cuota.emitido),
    estado: ESTADOS[cuota.estado] || cuota.estado,
  }))
  const exigibles = respuesta.cuotasExigibles ?? 0
  return {
    contribuyente: { ...contribuyente, nombre: respuesta.nombreContribuyente ?? contribuyente?.nombre },
    anio: respuesta.anio,
    aniosDisponibles: respuesta.aniosDisponibles?.length ? respuesta.aniosDisponibles : [respuesta.anio],
    fechaCorte: respuesta.fechaCorte,
    emitidas: respuesta.totalCuotasEmitidas ?? cuotas.length,
    exigibles,
    aTiempo: respuesta.cuotasPagadasATiempo ?? 0,
    conAtraso: respuesta.cuotasPagadasConAtraso ?? 0,
    vencidasSinPagar: respuesta.cuotasPendientesOVencidas ?? 0,
    porVencer: cuotas.filter((cuota) => cuota.estado === ESTADOS.POR_VENCER).length,
    porcentaje: exigibles > 0 ? Number(respuesta.porcentajeRegularidad) : null,
    segmentoSugerido: respuesta.segmentoSugerido,
    observacion: respuesta.observacion,
    cuotas,
  }
}

export async function obtenerRegularidad(contribuyente, anio) {
  const parametros = anio ? `?anio=${encodeURIComponent(anio)}` : ''
  const respuesta = await pedir(`/contribuyentes/${encodeURIComponent(contribuyente.codigo)}/regularidad${parametros}`)
  return normalizarRegularidad(respuesta, contribuyente)
}
