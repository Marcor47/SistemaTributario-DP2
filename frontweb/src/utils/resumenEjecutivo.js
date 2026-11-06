// HU017 / RF-023: reglas del resumen ejecutivo que la pantalla y el servicio comparten.
export const MAXIMO_PALABRAS = 300

export const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']

export const ETIQUETAS_ESTADO = {
  GENERADO: 'Pendiente de revisión',
  REVISADO: 'Revisado',
}

export function contarPalabras(texto) {
  const limpio = String(texto ?? '').trim()
  return limpio ? limpio.split(/\s+/).length : 0
}

// El periodo viaja como AAAA-MM, igual que el periodo de los valores de indicador.
export function esPeriodoValido(periodo) {
  return /^\d{4}-(0[1-9]|1[0-2])$/.test(String(periodo ?? ''))
}

export function armarPeriodo(anio, mes) {
  return anio && mes ? `${anio}-${String(mes).padStart(2, '0')}` : ''
}

export function formatoPeriodo(periodo) {
  if (!esPeriodoValido(periodo)) return periodo || '—'
  const [anio, mes] = periodo.split('-')
  return `${MESES[Number(mes) - 1]} ${anio}`
}

// Un indicador sin valor no equivale a 0: Number(null) daría 0.
function valorNumerico(valor) {
  if (valor === null || valor === undefined || valor === '') return null
  const numero = Number(valor)
  return Number.isFinite(numero) ? numero : null
}

export function formatoValor(indicador) {
  const numero = valorNumerico(indicador.valor)
  if (numero === null) return 'No disponible'
  const texto = numero.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return indicador.unidad === 'PORCENTAJE' ? `${texto} %` : texto
}

// Una cifra es un porcentaje, un monto en soles o un número con decimales; los años y las fechas no cuentan.
const CIFRA = /S\/\s?\d+(?:[.,]\d+)?|\d+(?:[.,]\d+)?\s?%|\d+[.,]\d+/g

function aNumero(cifra) {
  const limpia = cifra.replace(/S\/|%|\s/g, '').replace(',', '.')
  return Number(limpia)
}

// Cada cifra del texto debe coincidir con un indicador calculado (RF-023). Devuelve cada cifra y si tiene respaldo.
export function verificarCifras(texto, indicadores) {
  const valores = (indicadores ?? []).map((indicador) => valorNumerico(indicador.valor)).filter((v) => v !== null)
  return (String(texto ?? '').match(CIFRA) ?? []).map((cifra) => {
    const valor = aNumero(cifra)
    return { cifra, valor, respaldada: valores.some((candidato) => Math.abs(candidato - valor) < 0.005) }
  })
}

export function nombreArchivo(periodo) {
  return `resumen-ejecutivo-${periodo}.txt`
}
