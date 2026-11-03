export function normalizar(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim()
}

export function moneda(valor) {
  const n = Number(valor)
  if (!isFinite(n)) return '—'
  return 'S/ ' + n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function fecha(iso) {
  if (!iso) return '—'
  const [a, m, d] = iso.split('-')
  return `${d}/${m}/${a}`
}

export function fechaHora(iso) {
  if (!iso) return '—'
  const [parteFecha, parteHora = ''] = String(iso).replace(' ', 'T').split('T')
  const [a, m, d] = parteFecha.split('-')
  if (!a || !m || !d) return '—'
  const hora = parteHora.slice(0, 5)
  return hora ? `${d}/${m}/${a} ${hora}` : `${d}/${m}/${a}`
}
