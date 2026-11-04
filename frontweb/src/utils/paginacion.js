export const TAMANIOS_PAGINA = [5, 10, 20, 100]

/* Devuelve la página visible y ajusta la página cuando queda fuera de rango. */
export function paginar(items = [], pagina = 1, porPagina = TAMANIOS_PAGINA[0]) {
  const tamano = TAMANIOS_PAGINA.includes(Number(porPagina)) ? Number(porPagina) : TAMANIOS_PAGINA[0]
  const total = items.length
  const totalPaginas = Math.max(1, Math.ceil(total / tamano))
  const paginaActual = Math.min(Math.max(1, Number(pagina) || 1), totalPaginas)
  const inicio = (paginaActual - 1) * tamano
  const fin = Math.min(inicio + tamano, total)

  return {
    visibles: items.slice(inicio, fin),
    paginaActual,
    totalPaginas,
    inicio,
    fin,
    total,
  }
}
