import { ESTADOS } from '../components/solicitudPredio/catalogos.js'

// HU047: cómo se presenta cada estado de la solicitud de registro o traspaso (valores de EstadoSolicitudPredio).
// `tono` coincide con los tipos de <Aviso> y con los colores del tema.
const PRESENTACION = {
  EN_REVISION: {
    icono: 'reloj', tono: 'alerta',
    descripcion: 'Estamos revisando tu solicitud y tus documentos. Te avisaremos el resultado en tu buzón.',
  },
  ACEPTADA: {
    icono: 'check-circulo', tono: 'exito',
    descripcion: 'Tu solicitud fue aceptada. El resultado está disponible en tu buzón.',
  },
  RECHAZADA: {
    icono: 'alerta', tono: 'critico',
    descripcion: 'Tu solicitud fue rechazada. El resultado está disponible en tu buzón.',
  },
}

const DESCONOCIDO = { icono: 'info', tono: 'info', descripcion: '' }

/** Texto, icono y tono del estado. Un estado que el móvil aún no conoce se muestra tal cual, sin romper la pantalla. */
export function infoEstado(estado) {
  return {
    valor: estado ?? null,
    etiqueta: ESTADOS[estado] ?? (estado ? String(estado) : 'Sin estado'),
    ...(PRESENTACION[estado] ?? DESCONOCIDO),
  }
}

/**
 * Solo una solicitud rechazada muestra detalle; si el backend aún no envía el motivo devuelve null.
 * La entidad del backend guarda `motivoRechazo`; `observacion` se acepta por si la API la expone con ese nombre.
 */
export function observacionRechazo(solicitud) {
  if (solicitud?.estado !== 'RECHAZADA') return null
  for (const valor of [solicitud.motivoRechazo, solicitud.observacion]) {
    const texto = typeof valor === 'string' ? valor.trim() : ''
    if (texto) return texto
  }
  return null
}

/** Hay un resultado del trámite (aceptada o rechazada), que además se deposita en el buzón (RF-030). */
export function tieneResultado(solicitud) {
  return solicitud?.estado === 'ACEPTADA' || solicitud?.estado === 'RECHAZADA'
}

/** Las más recientes primero. fechaEnvio es un LocalDateTime ISO sin zona, comparable como texto. */
export function ordenarSolicitudes(solicitudes) {
  return [...(solicitudes || [])].sort((a, b) => {
    const porFecha = String(b.fechaEnvio ?? '').localeCompare(String(a.fechaEnvio ?? ''))
    return porFecha !== 0 ? porFecha : Number(b.idSolicitud) - Number(a.idSolicitud)
  })
}

export function textoAdjuntos(cantidad) {
  const n = Number(cantidad) || 0
  return `${n} ${n === 1 ? 'documento adjunto' : 'documentos adjuntos'}`
}
