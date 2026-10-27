import { FECHA_CORTE, FECHA_CORTE_ANTERIOR, generarCuenta } from '../mocks/cuentas.js'

// HU004 sigue con datos ficticios. La búsqueda y los datos de identidad vienen de la API.
const RETRASO_MS = 250
const esperar = () => new Promise((r) => setTimeout(r, RETRASO_MS))

// `servicioCaido` solo existe para la demostración del flujo alterno: cuando el
// servicio de cuenta corriente no responde se devuelve la última información
// disponible, con su fecha de corte y marcada como desactualizada.
export async function obtenerEstadoCuenta(contribuyente, { servicioCaido = false } = {}) {
  await esperar()
  if (!contribuyente?.codigo) throw new Error('No se recibió el contribuyente.')
  const fechaCorte = servicioCaido ? FECHA_CORTE_ANTERIOR : FECHA_CORTE
  return {
    contribuyente,
    fechaCorte,
    desactualizado: servicioCaido,
    filas: generarCuenta(contribuyente.codigo, fechaCorte),
  }
}

export { obtenerEstadoCuentaPortal, obtenerHistorialPagosPortal } from './portalCuentaService.js'

