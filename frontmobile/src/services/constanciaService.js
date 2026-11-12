import { pedir } from './api'
import { normalizarOrdenConstancia, ordenDeOperacion, pasarelaConstancia } from '../utils/constancia'

export const listarConstancias = () => pedir('/constancias')
export const consultarDeudaConstancia = () => pedir('/constancias/deuda')
export const iniciarConstancia = () => pedir('/constancias', { method: 'POST' })
export const obtenerConstancia = (id) => pedir(`/constancias/${encodeURIComponent(id)}`)
export const obtenerDocumentoFirma = (id) => pedir(`/constancias/${encodeURIComponent(id)}/documento-firma`)
export const enviarFirmaConstancia = (id, firmaBase64, certificado) => pedir(`/constancias/${encodeURIComponent(id)}/firma`, {
  method: 'POST', body: JSON.stringify({ firmaBase64, certificado }),
})
export async function obtenerOrdenConstancia(id) {
  return normalizarOrdenConstancia(await pedir(`/constancias/${encodeURIComponent(id)}/orden-pago`, { cache: 'no-store' }))
}

export async function iniciarPagoConstancia(id, idTransaccion) {
  const orden = ordenDeOperacion(await pedir(`/constancias/${encodeURIComponent(id)}/orden-pago/sesion`, { method: 'POST' }), idTransaccion)
  return { orden, pago: pasarelaConstancia(orden) }
}

export async function confirmarPagoConstancia(id, transactionToken, idTransaccion) {
  try {
    const orden = ordenDeOperacion(await pedir(`/constancias/${encodeURIComponent(id)}/orden-pago/confirmacion`, {
      method: 'POST', body: JSON.stringify(transactionToken ? { transactionToken } : {}),
    }), idTransaccion)
    return { orden, error: null }
  } catch (error) {
    if (error.estado >= 400 && error.estado < 500) throw error
    try {
      const orden = ordenDeOperacion(await obtenerOrdenConstancia(id), idTransaccion)
      return { orden, error: orden.estado === 'CONFIRMADA' ? null : error }
    } catch (consultaError) {
      if (consultaError.estado === 401) throw consultaError
      throw Object.assign(new Error('No se pudo comprobar el resultado. Consulta el estado antes de intentar de nuevo.'), { codigo: 'RESULTADO_DESCONOCIDO' })
    }
  }
}

const ALFABETO = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

export function firmaDePrueba(hash) {
  const bytes = Array.from(`FIRMA-DE-PRUEBA:${hash}`, (c) => c.charCodeAt(0))
  let salida = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i], b = bytes[i + 1], c = bytes[i + 2]
    salida += ALFABETO[a >> 2] + ALFABETO[((a & 3) << 4) | ((b ?? 0) >> 4)]
      + (b === undefined ? '=' : ALFABETO[((b & 15) << 2) | ((c ?? 0) >> 6)])
      + (c === undefined ? '=' : ALFABETO[c & 63])
  }
  return salida
}

export function textoDocumento(base64) {
  const limpio = base64.replace(/=+$/, '')
  const bytes = []
  for (let i = 0; i < limpio.length; i += 4) {
    const a = ALFABETO.indexOf(limpio[i]), b = ALFABETO.indexOf(limpio[i + 1])
    const c = limpio[i + 2] ? ALFABETO.indexOf(limpio[i + 2]) : 0
    const d = limpio[i + 3] ? ALFABETO.indexOf(limpio[i + 3]) : 0
    bytes.push((a << 2) | (b >> 4))
    if (limpio[i + 2]) bytes.push(((b & 15) << 4) | (c >> 2))
    if (limpio[i + 3]) bytes.push(((c & 3) << 6) | d)
  }
  return decodeURIComponent(bytes.map((n) => `%${n.toString(16).padStart(2, '0')}`).join(''))
}
