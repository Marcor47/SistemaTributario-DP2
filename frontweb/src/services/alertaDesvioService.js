import { pedir } from './api.js'

// HU039: alertas activas de desvío de meta que muestra la plataforma analítica.
export function listarAlertasDesvio({ indicadorCodigo, signal } = {}) {
  const parametros = new URLSearchParams()
  const filtro = indicadorCodigo?.trim()
  if (filtro) parametros.set('indicadorCodigo', filtro)
  const consulta = parametros.toString()
  return pedir(`/alertas-desvio${consulta ? `?${consulta}` : ''}`, { signal })
}
