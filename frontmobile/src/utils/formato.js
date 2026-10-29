export function moneda(valor) {
  const importe = Number(valor)
  return Number.isFinite(importe)
    ? `S/ ${importe.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '—'
}

export function fecha(valor) {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor || '')
  return partes ? `${partes[3]}/${partes[2]}/${partes[1]}` : '—'
}
