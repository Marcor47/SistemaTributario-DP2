import { normalizar } from './formato.js'

// HU045: límites compartidos por los selectores de zonas y segmentos ocultos.
export const MAX_VALORES_ALCANCE = 30
export const MAX_LONGITUD_VALOR_ALCANCE = 80

export function filtrarOpcionesAlcance(opciones, texto) {
  const lista = Array.isArray(opciones) ? opciones : []
  const consulta = normalizar(texto)
  if (!consulta) return lista
  return lista.filter((opcion) => {
    const valor = typeof opcion === 'string' ? opcion : opcion?.valor
    const detalle = typeof opcion === 'string' ? '' : opcion?.detalle
    return normalizar(`${valor ?? ''} ${detalle ?? ''}`).includes(consulta)
  })
}
