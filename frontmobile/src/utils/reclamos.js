export const TIPOS_RECLAMO = {
  COBRANZA: 'Cobranza', SERVICIO: 'Servicio', ATENCION: 'Atención', OTRO: 'Otro',
}

export const ESTADOS_RECLAMO = {
  ENVIADO: { etiqueta: 'Enviado', tono: 'neutro' },
  EN_REVISION: { etiqueta: 'En revisión', tono: 'aviso' },
  APROBADO_CON_RESPUESTA: { etiqueta: 'Atendido', tono: 'exito' },
  CANCELADO: { etiqueta: 'Cancelado', tono: 'critico' },
}

export function fechaHoraLocal(fecha = new Date()) {
  const dos = (valor) => String(valor).padStart(2, '0')
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}T${dos(fecha.getHours())}:${dos(fecha.getMinutes())}`
}

export function fechaHoraVisible(valor) {
  const partes = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(valor || '')
  return partes ? `${partes[3]}/${partes[2]}/${partes[1]} ${partes[4]}:${partes[5]}` : '—'
}

export function validarReclamo(datos, ahora = new Date()) {
  const errores = {}
  if (!TIPOS_RECLAMO[datos.tipoReclamo]) errores.tipoReclamo = 'Selecciona el tipo de reclamo.'
  if (!datos.fechaHoraIncidente) errores.fechaHoraIncidente = 'Indica la fecha y hora del incidente.'
  else if (Number.isNaN(new Date(datos.fechaHoraIncidente).getTime())) errores.fechaHoraIncidente = 'La fecha y hora no son válidas.'
  else if (new Date(datos.fechaHoraIncidente) > ahora) errores.fechaHoraIncidente = 'El incidente no puede ser futuro.'
  if (datos.referencia.trim().length > 150) errores.referencia = 'Usa como máximo 150 caracteres.'
  if (!datos.comentario.trim()) errores.comentario = 'Describe lo ocurrido.'
  else if (datos.comentario.trim().length > 2000) errores.comentario = 'Usa como máximo 2000 caracteres.'
  return errores
}

export const ordenarReclamos = (reclamos) => [...reclamos].sort((a, b) => String(b.fechaRegistro).localeCompare(String(a.fechaRegistro)))
