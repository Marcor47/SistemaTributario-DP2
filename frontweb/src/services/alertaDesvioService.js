import { pedir } from './api.js'

// HU039: alertas activas de desvío de meta que alimentan el dashboard.
export function listarAlertasDesvio(indicadorCodigo, { signal } = {}) {
  const filtro = indicadorCodigo?.trim()
  const parametros = new URLSearchParams()
  if (filtro) parametros.set('indicadorCodigo', filtro)
  const consulta = parametros.toString()
  return pedir(`/alertas-desvio${consulta ? `?${consulta}` : ''}`, { signal })
}
