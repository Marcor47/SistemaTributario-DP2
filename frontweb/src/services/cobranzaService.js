import { pedir } from './api.js'

// HU013: obligaciones vencidas del contribuyente.
export async function listarObligacionesVencidas(codigo) {
  return pedir(`/contribuyentes/${encodeURIComponent(codigo)}/obligaciones-vencidas`)
}

// HU013: detalle de la etapa de cobranza de una obligación vencida.
export async function obtenerEtapaCobranza(id) {
  return pedir(`/obligaciones/${encodeURIComponent(id)}/etapa-cobranza`)
}
