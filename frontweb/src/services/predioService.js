import { pedir } from './api.js'

export const PREDIOS_POR_PAGINA = 10

// HU002: búsqueda en SAMNET por código predial, catastro, dirección o titular (código, nombre o documento). La
// respuesta viene paginada: { contenido, pagina, tamano, totalElementos, totalPaginas }.
export async function buscarPredios(consulta, { pagina = 0, tamano = PREDIOS_POR_PAGINA } = {}) {
  const texto = String(consulta ?? '').trim()
  if (!texto) return { contenido: [], pagina: 0, tamano, totalElementos: 0, totalPaginas: 0 }
  const parametros = new URLSearchParams({ consulta: texto, pagina: String(pagina), tamano: String(tamano) })
  return pedir(`/predios?${parametros}`)
}
