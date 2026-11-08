import { pedir } from './api.js'
import { normalizarMorosidad } from '../utils/morosidad.js'

// La consulta es de lectura: el cálculo pertenece al proceso ETL del backend.
export async function obtenerMorosidad(anio, numeroPeriodo, { signal } = {}) {
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

// Años y meses con índice publicado en la BD; son las únicas opciones del filtro.
export async function obtenerPeriodosMorosidad({ signal } = {}) {
  const periodos = await pedir('/indicadores/morosidad/periodos', { signal })
  const porAnio = new Map()
  for (const { anio, numeroPeriodo } of periodos) {
    if (!porAnio.has(anio)) porAnio.set(anio, [])
    porAnio.get(anio).push(numeroPeriodo)
  }
  return [...porAnio.entries()]
    .sort(([a], [b]) => b - a)
    .map(([anio, meses]) => ({ anio, meses: [...new Set(meses)].sort((a, b) => a - b) }))
}
