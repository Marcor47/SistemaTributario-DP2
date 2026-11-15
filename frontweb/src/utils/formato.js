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
  // Un instante con zona (Z o ±hh:mm) se pasa a la hora de Lima antes de mostrarlo.
  if (/T.*(?:Z|[+-]\d{2}:?\d{2})$/i.test(String(iso))) {
    const local = new Date(iso).toLocaleString('sv-SE', { timeZone: 'America/Lima' })
    if (!local.startsWith('Invalid')) return fechaHora(local)
  }
  const [parteFecha, parteHora = ''] = String(iso).replace(' ', 'T').split('T')
  const [a, m, d] = parteFecha.split('-')
  if (!a || !m || !d) return '—'
  const hora = parteHora.slice(0, 5)
  return hora ? `${d}/${m}/${a} ${hora}` : `${d}/${m}/${a}`
}

export const ZONA_LIMA = 'America/Lima'

// Las fechas sin zona del backend ya son hora de Lima (UTC-5, sin horario de verano); las que traen zona se convierten.
function instanteLima(valor) {
  if (valor instanceof Date) return valor
  const texto = String(valor ?? '').trim().replace(' ', 'T')
  if (!texto) return null
  const conZona = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(texto)
  const soloFecha = /^\d{4}-\d{2}-\d{2}$/.test(texto)
  const fechaHoraIso = soloFecha ? `${texto}T00:00:00` : texto
  const instante = new Date(conZona ? fechaHoraIso : `${fechaHoraIso}-05:00`)
  return Number.isNaN(instante.getTime()) ? null : instante
}

// Fecha y hora en hora de Lima con el formato peruano, sea cual sea la zona del navegador.
export function fechaHoraLima(valor, opciones = { dateStyle: 'short', timeStyle: 'short' }) {
  const instante = instanteLima(valor)
  return instante ? instante.toLocaleString('es-PE', { ...opciones, timeZone: ZONA_LIMA }) : '—'
}

// Fecha de hoy en Lima como AAAA-MM-DD (nombres de archivo, valores por defecto de filtros).
export function hoyLima() {
  return new Date().toLocaleDateString('en-CA', { timeZone: ZONA_LIMA })
}
