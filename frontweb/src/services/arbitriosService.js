import { pedir } from './api.js'

// HU005: el código predial admite hasta 20 caracteres según el contrato del backend.
const MAX_CODIGO_PREDIO = 20

// Detalle de arbitrios de un predio. Los arbitrios se manejan como un solo tributo
// ARBITRIOS; la API no entrega el desglose de limpieza, parques y seguridad.
export async function obtenerArbitriosPredio(codigoPredio, { signal } = {}) {
  const codigo = String(codigoPredio ?? '').trim()
  if (!codigo || codigo.length > MAX_CODIGO_PREDIO) {
    throw Object.assign(new Error('Selecciona un predio con un código de hasta 20 caracteres.'), {
      codigo: 'CODIGO_PREDIO_INVALIDO', estado: 400, detalles: [],
    })
  }

  const datos = await pedir(`/predios/${encodeURIComponent(codigo)}/arbitrios`, {
    method: 'GET', signal,
  })

  const respuestaValida = datos
    && typeof datos === 'object'
    && datos.predio?.codigoPredio === codigo
    && datos.resumen
    && typeof datos.resumen === 'object'
    && Array.isArray(datos.cuotas)
    && typeof datos.fechaCorte === 'string'

  if (!respuestaValida) {
    throw Object.assign(new Error('No se recibió el detalle de arbitrios del predio seleccionado. Intenta nuevamente.'), {
      codigo: 'RESPUESTA_INVALIDA', detalles: [],
    })
  }
  return datos
}
