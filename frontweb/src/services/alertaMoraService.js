import { pedir } from './api.js'

// HU012 (RF-013): alertas de mora temprana por correo. Contrato de back/.../documentos/AlertaMoraController.java.

async function cabecerasCsrf() {
  const token = await pedir('/auth/interno/csrf')
  return { 'Content-Type': 'application/json', [token.headerName]: token.token }
}

/** Evalúa un lote publicado del ETL y envía los avisos por correo; los ya enviados para ese lote no se repiten. */
export async function procesarAlertasMora(idLote) {
  return pedir('/alertas-mora/evaluaciones', {
    method: 'POST',
    headers: await cabecerasCsrf(),
    body: JSON.stringify({ idLote }),
  })
}

export function listarAvisosPorLote(idLote, { signal } = {}) {
  return pedir(`/alertas-mora/lotes/${encodeURIComponent(idLote)}`, { signal })
}

/** Historial de avisos de un contribuyente, por su código o su número de documento. */
export function listarAvisosContribuyente(codigoODocumento, { signal } = {}) {
  return pedir(`/alertas-mora/contribuyentes/${encodeURIComponent(codigoODocumento)}`, { signal })
}
