const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']

export function periodoDeuda(periodo) {
  const trimestre = /^(\d{4})-T([1-4])$/.exec(periodo ?? '')
  if (trimestre) return `${trimestre[2]}.º trimestre · ${trimestre[1]}`
  const mes = /^(\d{4})-(\d{2})$/.exec(periodo ?? '')
  if (mes && Number(mes[2]) >= 1 && Number(mes[2]) <= 12) return `${MESES[Number(mes[2]) - 1]} · ${mes[1]}`
  return periodo || 'Período no indicado'
}

export function agruparDeuda(obligaciones = []) {
  const grupos = new Map()
  for (const item of obligaciones) {
    const clave = item.tributoCodigo || item.tributo
    if (!grupos.has(clave)) grupos.set(clave, { clave, titulo: item.tributo, items: [], centimos: 0 })
    const grupo = grupos.get(clave)
    grupo.items.push(item)
    grupo.centimos += Math.round(Number(item.totalAPagar ?? item.deuda) * 100)
  }
  return [...grupos.values()].map((grupo) => ({
    ...grupo,
    total: grupo.centimos / 100,
    items: [...grupo.items].sort((a, b) => String(a.periodo).localeCompare(String(b.periodo))),
  }))
}
