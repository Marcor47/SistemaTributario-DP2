import { FECHA_CORTE, generarCuenta } from './cuentas.js'

const ANIO_CORTE = Number(FECHA_CORTE.slice(0, 4))
export const ANIOS_REGULARIDAD = [ANIO_CORTE, ANIO_CORTE - 1, ANIO_CORTE - 2]

// Simula el indicador que entregará el backend/ETL. RF-011 usa todas las cuotas
// emitidas del año como denominador, incluidas las que todavía no vencen.
export function calcularRegularidad(filas, anio, fechaCorte) {
  const cuotas = filas
    .filter((cuota) => cuota.periodo.startsWith(`${anio}-`))
    .map((cuota) => {
      let estado
      if (cuota.fechaPago && cuota.pagado >= cuota.emitido && cuota.fechaPago <= fechaCorte) {
        estado = cuota.fechaPago <= cuota.fechaVencimiento ? 'A tiempo' : 'Con atraso'
      } else {
        estado = cuota.fechaVencimiento < fechaCorte ? 'Vencida sin pagar' : 'Por vencer'
      }
      return { ...cuota, estado }
    })
    .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento) || a.tributo.localeCompare(b.tributo))

  const aTiempo = cuotas.filter((cuota) => cuota.estado === 'A tiempo').length
  return {
    anio,
    aniosDisponibles: ANIOS_REGULARIDAD,
    fechaCorte,
    emitidas: cuotas.length,
    aTiempo,
    conAtraso: cuotas.filter((cuota) => cuota.estado === 'Con atraso').length,
    vencidasSinPagar: cuotas.filter((cuota) => cuota.estado === 'Vencida sin pagar').length,
    porVencer: cuotas.filter((cuota) => cuota.estado === 'Por vencer').length,
    porcentaje: cuotas.length ? (aTiempo / cuotas.length) * 100 : null,
    cuotas,
  }
}

export function generarRegularidad(codigo, anio) {
  return calcularRegularidad(generarCuenta(codigo, FECHA_CORTE), anio, FECHA_CORTE)
}
