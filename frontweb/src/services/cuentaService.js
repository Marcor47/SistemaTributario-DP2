import { pedir } from './api.js'

// HU004: estado de cuenta desde SAMNET por la ruta interna del analista. Si SAMNET no responde, el backend entrega
// la última copia con su fecha de corte y desactualizado = true.
export async function obtenerEstadoCuenta(contribuyente) {
  if (!contribuyente?.codigo) throw new Error('No se recibió el contribuyente.')
  return pedir(`/contribuyentes/${encodeURIComponent(contribuyente.codigo)}/estado-cuenta`)
}

export { obtenerEstadoCuentaPortal, obtenerHistorialPagosPortal } from './portalCuentaService.js'
