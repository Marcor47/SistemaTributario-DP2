import { checkoutValido, idPagoValido } from './pagos.js'
import { configuracionNiubiz } from './niubiz.js'

export const PASARELA_INICIAL = { pago: null, abierto: false, generacion: 0, tokenEntregado: false }

// Solo se reutiliza entre operaciones cuando esta instancia entregó el token del pago confirmado.
// Una operación abandonada o confirmada en otro dispositivo necesita una instancia limpia.
export function actualizarPasarela(anterior, pago) {
  if (!pago || !idPagoValido(pago.idTransaccion)) return anterior
  const mismo = String(anterior.pago?.idTransaccion) === String(pago.idTransaccion)
  if (pago.estado !== 'INICIADA') {
    return mismo ? { ...anterior, pago: { ...anterior.pago, ...pago }, abierto: false } : anterior
  }
  if (!checkoutValido(pago.checkout) || pago.descartado) return anterior
  try { configuracionNiubiz(pago) } catch { return anterior }
  const cambia = anterior.pago && (!mismo || anterior.pago.checkout?.sessionKey !== pago.checkout.sessionKey)
  const reutilizable = anterior.pago?.estado === 'CONFIRMADA' && anterior.tokenEntregado
  return {
    pago, abierto: mismo ? anterior.abierto : false,
    generacion: anterior.generacion + (cambia && !reutilizable ? 1 : 0),
    tokenEntregado: cambia ? false : anterior.tokenEntregado,
  }
}
