import { pedir } from './api.js'

export const obtenerFiltrosDashboard = ({ signal } = {}) => pedir('/dashboard/filtros', { signal })

export function obtenerDashboard(filtros = {}, { signal } = {}) {
  const parametros = new URLSearchParams()
  for (const campo of ['anio', 'numeroPeriodo', 'zona', 'idSegmento', 'tributo']) {
    if (filtros[campo] !== '' && filtros[campo] != null) parametros.set(campo, filtros[campo])
  }
  const consulta = parametros.toString()
  return pedir(`/dashboard${consulta ? `?${consulta}` : ''}`, { signal, cache: 'no-store' })
}
