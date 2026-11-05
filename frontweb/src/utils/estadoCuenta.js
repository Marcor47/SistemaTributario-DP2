import { normalizar } from './formato.js'

// Así emite San Miguel: el Impuesto Predial por contribuyente en 4 cuotas trimestrales y los arbitrios por predio y
// mes, con el monto de cada servicio. Estas funciones ordenan la respuesta de /estado-cuenta para la pantalla.

export const TRIBUTO_PREDIAL = 'IMPUESTO_PREDIAL'
export const TRIBUTO_ARBITRIOS = 'ARBITRIOS'

export const SERVICIOS = [
  { codigo: 'BARRIDO_CALLES', corto: 'Barrido' },
  { codigo: 'RECOLECCION_RESIDUOS', corto: 'Recolección' },
  { codigo: 'PARQUES_JARDINES', corto: 'Parques' },
  { codigo: 'SERENAZGO', corto: 'Serenazgo' },
]

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Setiembre', 'Octubre',
  'Noviembre', 'Diciembre']

const sumar = (filas, campo) => Math.round(filas.reduce((total, fila) => total + Number(fila[campo] ?? 0), 0) * 100) / 100

export function nombreMes(numero) {
  return MESES[numero - 1] ?? `Mes ${numero}`
}

export function etiquetaCuota(fila) {
  return fila.tributoCodigo === TRIBUTO_ARBITRIOS ? nombreMes(fila.cuota) : `${fila.cuota}.ª cuota`
}

// Años con cuotas, del más reciente al más antiguo.
export function aniosDeCuenta(cuenta) {
  return [...new Set((cuenta?.filas ?? []).map((fila) => fila.anio))].sort((a, b) => b - a)
}

// Por defecto se abre el año de la fecha de corte; si no tiene cuotas, el más reciente.
export function anioInicial(cuenta) {
  const anios = aniosDeCuenta(cuenta)
  const anioCorte = Number(String(cuenta?.fechaCorte ?? '').slice(0, 4))
  return anios.includes(anioCorte) ? anioCorte : anios[0] ?? null
}

export function resumenDelAnio(cuenta, anio) {
  return (cuenta?.resumenAnual ?? []).find((resumen) => resumen.anio === anio) ?? null
}

export function cuotasPredial(cuenta, anio) {
  return (cuenta?.filas ?? [])
    .filter((fila) => fila.anio === anio && fila.tributoCodigo === TRIBUTO_PREDIAL)
    .sort((a, b) => a.cuota - b.cuota)
}

export function montoServicio(fila, codigo) {
  return fila.servicios?.find((servicio) => servicio.servicio === codigo)?.monto ?? 0
}

export function totalesDe(filas) {
  return {
    emitido: sumar(filas, 'emitido'),
    pagado: sumar(filas, 'pagado'),
    deuda: sumar(filas, 'deuda'),
    interesMoratorio: sumar(filas, 'interesMoratorio'),
    totalAPagar: sumar(filas, 'totalAPagar'),
    vencidas: filas.filter((fila) => fila.estado === 'Vencida').length,
  }
}

// Arbitrios del año agrupados por predio, en el orden de los predios del contribuyente.
export function arbitriosPorPredio(cuenta, anio) {
  const predios = new Map((cuenta?.predios ?? []).map((predio) => [predio.codigoPredio, predio]))
  const grupos = new Map()
  ;(cuenta?.filas ?? [])
    .filter((fila) => fila.anio === anio && fila.tributoCodigo === TRIBUTO_ARBITRIOS)
    .forEach((fila) => {
      if (!grupos.has(fila.codigoPredio)) grupos.set(fila.codigoPredio, [])
      grupos.get(fila.codigoPredio).push(fila)
    })
  return [...grupos.entries()]
    .map(([codigoPredio, filas]) => {
      const ordenadas = [...filas].sort((a, b) => a.cuota - b.cuota)
      return {
        codigoPredio,
        predio: predios.get(codigoPredio) ?? { codigoPredio },
        filas: ordenadas,
        totales: totalesDe(ordenadas),
        porServicio: SERVICIOS.map((servicio) => ({
          ...servicio,
          monto: Math.round(ordenadas.reduce((total, fila) => total + Number(montoServicio(fila, servicio.codigo)), 0) * 100) / 100,
        })),
      }
    })
    .sort((a, b) => a.codigoPredio.localeCompare(b.codigoPredio))
}

// Para listas largas: busca por código o dirección y, si se pide, deja solo los predios con deuda.
export function filtrarGrupos(grupos, texto, soloConDeuda) {
  const consulta = normalizar(texto)
  return grupos.filter((grupo) =>
    (!soloConDeuda || grupo.totales.deuda > 0)
    && (!consulta
      || normalizar(grupo.codigoPredio).includes(consulta)
      || normalizar(grupo.predio.ubicacion).includes(consulta)))
}

