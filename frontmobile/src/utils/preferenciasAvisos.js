// HU016 (RF-022): contrato y textos de la pantalla P12. La anticipación la define la Municipalidad; el
// contribuyente solo decide si quiere recibir el recordatorio.
export const RUTA_PREFERENCIAS_AVISOS = '/portal/preferencias-avisos'

export function cuerpoPreferenciaRecordatorio(activo) {
  return JSON.stringify({ recordatorioVencimiento: Boolean(activo) })
}

export function textoDias(dias) {
  if (dias === null || dias === undefined || String(dias).trim() === '') return '—'
  const numero = Number(dias)
  if (!Number.isInteger(numero) || numero < 0) return '—'
  if (numero === 0) return 'el mismo día del vencimiento'
  return `${numero} ${numero === 1 ? 'día' : 'días'} antes del vencimiento`
}

export function tieneCorreo(preferencia) {
  return Boolean(String(preferencia?.correo ?? '').trim())
}

export function textoRecordatorio(preferencia) {
  if (!tieneCorreo(preferencia)) {
    return 'No tienes un correo registrado; no es posible enviarte recordatorios.'
  }
  return preferencia?.recordatorioVencimiento
    ? `Recibirás un correo ${textoDias(preferencia?.diasAnticipacion)}.`
    : 'No recibirás recordatorios de vencimiento por correo.'
}

export function puedeActivar(preferencia) {
  return tieneCorreo(preferencia)
}
