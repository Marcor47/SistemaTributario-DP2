import { pedir } from './api'
import { idPagoValido, normalizarPago, validarPasarela } from '../utils/pagos'

async function pedirPago(ruta, opciones = {}) {
  const { signal, ...init } = opciones
  const controlador = new AbortController()
  const cancelar = () => controlador.abort()
  if (signal?.aborted) cancelar()
  else signal?.addEventListener('abort', cancelar)
  const limite = setTimeout(cancelar, 30000)
  try {
    return await pedir(ruta, { ...init, signal: controlador.signal })
  } finally {
    clearTimeout(limite)
    signal?.removeEventListener('abort', cancelar)
  }
}

export async function crearPago() {
  // El servidor identifica al contribuyente y cotiza todas sus obligaciones; no se envían importes ni IDs.
  return validarPasarela(normalizarPago(await pedirPago('/portal/pagos', { method: 'POST', body: '{}' })))
}

function referencia(id) {
  if (!idPagoValido(id)) throw Object.assign(new Error('La referencia del pago no es válida.'), { codigo: 'PAGO_NO_ENCONTRADO' })
  return encodeURIComponent(id)
}

function pagoDeLaOperacion(datos, id) {
  const pago = normalizarPago(datos)
  if (String(pago.idTransaccion) !== String(id)) {
    throw Object.assign(new Error('La respuesta no corresponde a esta operación.'), { codigo: 'RESPUESTA_PAGO_INVALIDA' })
  }
  return pago
}

export async function consultarPago(id, { signal } = {}) {
  return pagoDeLaOperacion(await pedirPago(`/portal/pagos/${referencia(id)}`, { signal, cache: 'no-store' }), id)
}

async function confirmarPago(id) {
  return pagoDeLaOperacion(await pedirPago(`/portal/pagos/${referencia(id)}/confirmacion`, { method: 'POST' }), id)
}

export async function confirmarYRecuperar(id) {
  try {
    return { pago: await confirmarPago(id), error: null }
  } catch (error) {
    if (error.estado >= 400 && error.estado < 500) throw error
    // Una respuesta perdida puede corresponder a un pago registrado. Solo se consulta la misma orden.
    try {
      const pago = await consultarPago(id)
      return { pago, error: pago.estado === 'CONFIRMADA' ? null : error }
    } catch (consultaError) {
      if (consultaError.estado === 401) throw consultaError
      throw Object.assign(new Error('No se pudo comprobar el resultado de la confirmación.'), { codigo: 'RESULTADO_DESCONOCIDO' })
    }
  }
}
