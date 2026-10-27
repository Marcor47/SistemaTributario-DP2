import { pedir } from './api.js'
import { MODO_DEMO, esSesionDemo, obtenerSesionDemo } from './authService.js'
import { FECHA_CORTE, generarCuenta } from '../mocks/cuentas.js'
import { normalizarCuenta } from '../utils/estadoCuentaPortal.js'

// La identidad real se resuelve exclusivamente mediante la cookie en el servidor.
export async function obtenerEstadoCuentaPortal({ signal } = {}) {
  if (MODO_DEMO && esSesionDemo()) {
    return normalizarCuenta({
      contribuyente: { codigo: obtenerSesionDemo().codigo, nombre: 'Contribuyente de demostración', tipoDoc: 'RUC', doc: '20456781239' },
      fechaCorte: FECHA_CORTE, desactualizado: false, esDemo: true,
      filas: generarCuenta('021390', FECHA_CORTE),
    })
  }
  return normalizarCuenta(await pedir('/portal/estado-cuenta', { signal }))
}

// El contrato actual permite consultar pagos aplicados a obligaciones, no un historial de recibos.
export async function obtenerHistorialPagosPortal(opciones) {
  const cuenta = await obtenerEstadoCuentaPortal(opciones)
  return cuenta.filas.filter((fila) => fila.pagado > 0)
}

export function iniciarPagoPortal(obligaciones) {
  return pedir('/portal/pagos', { method: 'POST', body: JSON.stringify({ obligaciones }) })
}

export function confirmarPagoPortal(idTransaccion) {
  return pedir(`/portal/pagos/${encodeURIComponent(idTransaccion)}/confirmacion`, { method: 'POST' })
}
