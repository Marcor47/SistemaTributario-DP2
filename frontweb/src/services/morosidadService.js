import { pedir } from './api.js'
import { DATOS_DEMO } from '../mocks/morosidad.js'
import { normalizarMorosidad } from '../utils/morosidad.js'

const USAR_MOCK = import.meta.env?.VITE_USAR_MOCK === 'true'

// La consulta es de lectura: el cálculo pertenece al proceso ETL del backend.
export async function obtenerMorosidad(anio, numeroPeriodo, { signal } = {}) {
  if (USAR_MOCK) {
    if ((anio && Number(anio) !== DATOS_DEMO.lote.anio)
      || (numeroPeriodo && Number(numeroPeriodo) !== DATOS_DEMO.lote.numeroPeriodo)) return null
    return { ...structuredClone(DATOS_DEMO), esDemo: true }
  }
  const parametros = new URLSearchParams()
  if (anio) parametros.set('anio', anio)
  if (numeroPeriodo) parametros.set('numeroPeriodo', numeroPeriodo)
  try {
    return normalizarMorosidad(await pedir(`/indicadores/morosidad?${parametros}`, { signal }))
  } catch (error) {
    if (error.estado === 404 && error.codigo === 'MOROSIDAD_NO_CALCULADA') return null
    throw error
  }
}
