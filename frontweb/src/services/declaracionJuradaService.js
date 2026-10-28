import { pedir } from './api.js'

// HU003: la API resuelve la vigencia según SAMNET; no se deduce por año.
export async function obtenerDeclaracionJurada(codigoPredio, { signal } = {}) {
  const codigo = String(codigoPredio ?? '').trim()
  if (!codigo || codigo.length > 12) {
    throw Object.assign(new Error('Selecciona un predio con un código de hasta 12 caracteres.'), {
      codigo: 'CODIGO_PREDIO_INVALIDO', estado: 400, detalles: [],
    })
  }

  const datos = await pedir(`/predios/${encodeURIComponent(codigo)}/declaracion-jurada`, {
    method: 'GET', signal,
  })
  if (datos?.predio?.codigoPredio !== codigo || !Array.isArray(datos.historial) || !Array.isArray(datos.advertencias)) {
    throw Object.assign(new Error('No se recibió una respuesta válida para el predio seleccionado. Intenta nuevamente.'), {
      codigo: 'RESPUESTA_INVALIDA', detalles: [],
    })
  }
  return datos
}
