import { normalizar } from './formato.js'

export const TAMANIOS_PAGINA = [5, 10, 20, 100]

/* Filtra por código de predio o dirección, sin distinguir mayúsculas ni tildes. */
export function filtrarPredios(predios = [], texto = '') {
  const consulta = normalizar(texto)
  if (!consulta) return predios
  return predios.filter((predio) => (
    normalizar(predio?.codigoPredio).includes(consulta)
    || normalizar(predio?.ubicacion).includes(consulta)
  ))
}

/* Devuelve la página visible y ajusta la página cuando queda fuera de rango. */
export function paginarPredios(predios = [], pagina = 1, porPagina = TAMANIOS_PAGINA[0]) {
  const tamano = TAMANIOS_PAGINA.includes(Number(porPagina)) ? Number(porPagina) : TAMANIOS_PAGINA[0]
  const total = predios.length
  const totalPaginas = Math.max(1, Math.ceil(total / tamano))
  const paginaActual = Math.min(Math.max(1, Number(pagina) || 1), totalPaginas)
  const inicio = (paginaActual - 1) * tamano
  const fin = Math.min(inicio + tamano, total)

  return {
    visibles: predios.slice(inicio, fin),
    paginaActual,
    totalPaginas,
    inicio,
    fin,
    total,
  }
}
