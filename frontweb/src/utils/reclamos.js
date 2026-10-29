// HU050 / RF-064: mismas reglas que TipoReclamo, EstadoReclamo y la tabla operativo.reclamo del backend.
export const TIPOS_RECLAMO = {
  COBRANZA: { etiqueta: 'Cobranza', reclamantes: ['CONTRIBUYENTE', 'TRABAJADOR'] },
  SERVICIO: { etiqueta: 'Servicio', reclamantes: ['CONTRIBUYENTE'] },
  ATENCION: { etiqueta: 'Atención', reclamantes: ['CONTRIBUYENTE'] },
  AREA_RELACIONADA: { etiqueta: 'Área relacionada', reclamantes: ['TRABAJADOR'] },
  DOCUMENTO: { etiqueta: 'Documento', reclamantes: ['TRABAJADOR'] },
  OTRO: { etiqueta: 'Otro', reclamantes: ['CONTRIBUYENTE', 'TRABAJADOR'] },
}

export const ESTADOS_RECLAMO = {
  ENVIADO: { etiqueta: 'Enviado', clase: 'neutro' },
  EN_REVISION: { etiqueta: 'En revisión', clase: 'aviso' },
  APROBADO_CON_RESPUESTA: { etiqueta: 'Aprobado con respuesta', clase: 'ok' },
  CANCELADO: { etiqueta: 'Cancelado', clase: 'critico' },
}

export const MAXIMO_COMENTARIO = 2000
export const MAXIMO_REFERENCIA = 150

export const etiquetaTipo = (tipo) => TIPOS_RECLAMO[tipo]?.etiqueta ?? tipo
export const estadoReclamo = (estado) => ESTADOS_RECLAMO[estado] ?? { etiqueta: estado, clase: 'neutro' }

// Tipos que puede elegir quien reclama; el formulario es el mismo para todos y solo cambia esta lista.
export function tiposPara(reclamante) {
  return Object.entries(TIPOS_RECLAMO)
    .filter(([, tipo]) => tipo.reclamantes.includes(reclamante))
    .map(([valor, tipo]) => ({ valor, etiqueta: tipo.etiqueta }))
}

// Valor de <input type="datetime-local"> (hora local, sin zona) para la fecha indicada.
export function aFechaHoraLocal(fecha) {
  const dos = (n) => String(n).padStart(2, '0')
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}T${dos(fecha.getHours())}:${dos(fecha.getMinutes())}`
}

// Devuelve { campo: mensaje } con los errores; vacío si el reclamo es válido. El incidente no puede ser futuro
// (restricción ck_reclamo_incidente de la base).
export function validarReclamo(datos, reclamante, ahora = new Date()) {
  const errores = {}
  if (!datos.tipoReclamo) errores.tipoReclamo = 'Selecciona el tipo de reclamo.'
  else if (!tiposPara(reclamante).some((t) => t.valor === datos.tipoReclamo)) {
    errores.tipoReclamo = 'Ese tipo de reclamo no está disponible para tu perfil.'
  }

  if (!datos.fechaHoraIncidente) {
    errores.fechaHoraIncidente = 'Indica la fecha y la hora del incidente.'
  } else {
    const momento = new Date(datos.fechaHoraIncidente)
    if (Number.isNaN(momento.getTime())) errores.fechaHoraIncidente = 'La fecha y la hora no son válidas.'
    else if (momento > ahora) errores.fechaHoraIncidente = 'El incidente no puede ser posterior a la fecha y hora actual.'
  }

  if ((datos.referencia ?? '').trim().length > MAXIMO_REFERENCIA) {
    errores.referencia = `Usa como máximo ${MAXIMO_REFERENCIA} caracteres.`
  }

  const comentario = (datos.comentario ?? '').trim()
  if (!comentario) errores.comentario = 'Describe lo ocurrido en el comentario.'
  else if (comentario.length > MAXIMO_COMENTARIO) errores.comentario = `Usa como máximo ${MAXIMO_COMENTARIO} caracteres.`
  return errores
}
