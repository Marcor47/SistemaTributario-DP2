import { fechaHora } from './formato.js'

// HU016 (RF-022): el recordatorio de vencimiento lo programa el backend; estas utilidades
// presentan su estado y validan los días de anticipación configurables (1 a 10).
export const DIAS_RECORDATORIO_POR_DEFECTO = 5
export const DIAS_RECORDATORIO_MINIMO = 1
export const DIAS_RECORDATORIO_MAXIMO = 10

export const ESTADOS_RECORDATORIO = {
  ENVIADO: { texto: 'Enviado', clase: 'ok' },
  PROGRAMADO: { texto: 'Programado', clase: 'aviso' },
  FALLIDO: { texto: 'Fallido', clase: 'critico' },
  SIN_CORREO: { texto: 'Sin correo registrado', clase: 'neutro' },
  NO_ENVIADO: { texto: 'No enviado', clase: 'neutro' },
}

export function validarDiasRecordatorio(valor) {
  const texto = String(valor ?? '').trim()
  if (!texto) {
    return { valido: false, mensaje: 'Ingresa la cantidad de días de anticipación.' }
  }
  if (!/^\d+$/.test(texto)) {
    return { valido: false, mensaje: 'Ingresa un número entero de días.' }
  }
  const numero = Number(texto)
  if (numero < DIAS_RECORDATORIO_MINIMO || numero > DIAS_RECORDATORIO_MAXIMO) {
    return {
      valido: false,
      mensaje: `Los días de anticipación deben estar entre ${DIAS_RECORDATORIO_MINIMO} y ${DIAS_RECORDATORIO_MAXIMO}.`,
    }
  }
  return { valido: true, valor: numero }
}

function partesFecha(fechaIso) {
  const [anio, mes, dia] = String(fechaIso ?? '').split('-').map(Number)
  if (!anio || !mes || !dia) return null
  const fecha = new Date(Date.UTC(anio, mes - 1, dia))
  if (
    fecha.getUTCFullYear() !== anio
    || fecha.getUTCMonth() !== mes - 1
    || fecha.getUTCDate() !== dia
  ) {
    return null
  }
  return fecha
}

/** Fecha en que se enviaría el recordatorio: N días antes del vencimiento. */
export function calcularFechaProgramada(fechaVencimiento, diasAnticipacion) {
  const fecha = partesFecha(fechaVencimiento)
  const dias = Number(diasAnticipacion)
  if (!fecha || !Number.isInteger(dias) || dias < 0) return null
  fecha.setUTCDate(fecha.getUTCDate() - dias)
  return fecha.toISOString().slice(0, 10)
}

/** Un estado que el front aún no conoce se muestra tal cual, sin romper el detalle. */
export function estadoRecordatorio(estado) {
  return ESTADOS_RECORDATORIO[estado] ?? { texto: estado || 'Sin estado', clase: 'neutro' }
}

export function textoAnticipacion(dias) {
  if (dias === null || dias === undefined || String(dias).trim() === '') return '—'
  const numero = Number(dias)
  if (!Number.isInteger(numero) || numero < 0) return '—'
  if (numero === 0) return 'El mismo día del vencimiento'
  return `${numero} ${numero === 1 ? 'día' : 'días'} antes del vencimiento`
}

export function fechaRecordatorio(recordatorio) {
  if (recordatorio?.fechaEnvio) {
    return {
      etiqueta: recordatorio.estado === 'FALLIDO' ? 'Fecha del intento' : 'Fecha de envío',
      valor: fechaHora(recordatorio.fechaEnvio),
    }
  }
  if (recordatorio?.fechaProgramada) {
    const etiqueta = recordatorio.estado === 'SIN_CORREO' || recordatorio.estado === 'NO_ENVIADO'
      ? 'Fecha prevista'
      : 'Fecha programada'
    return { etiqueta, valor: fechaHora(recordatorio.fechaProgramada) }
  }
  return { etiqueta: 'Fecha de envío', valor: '—' }
}

export function descripcionRecordatorio(recordatorio) {
  if (!recordatorio) return ''
  const cuando = fechaHora(recordatorio.fechaEnvio || recordatorio.fechaProgramada)
  switch (recordatorio.estado) {
    case 'ENVIADO':
      return `El recordatorio se envió por correo el ${cuando}, ${textoAnticipacion(recordatorio.diasAnticipacion)}.`
    case 'PROGRAMADO':
      return `El recordatorio está programado para el ${cuando}.`
    case 'FALLIDO':
      return `El envío del recordatorio falló el ${cuando}; el aviso requiere revisión.`
    case 'SIN_CORREO':
      return 'El contribuyente no tiene correo registrado; no se puede enviar el recordatorio.'
    case 'NO_ENVIADO':
      return 'No se registró un envío de recordatorio para esta obligación.'
    default:
      return `El recordatorio se encuentra en estado ${recordatorio.estado || 'desconocido'}.`
  }
}
