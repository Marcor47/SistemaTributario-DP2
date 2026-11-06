import { Platform } from 'react-native'
import { pedir } from './api'

// Contrato de back/src/main/java/pe/municipalidad/tributario/tramitepredio/SolicitudPredioController.java (HU046).
const BASE = '/portal/solicitudes-predio'

export const TAMANO_MAXIMO_BYTES = 10 * 1024 * 1024
export const MAXIMO_ADJUNTOS = 5
export const TIPOS_PERMITIDOS = ['application/pdf', 'image/jpeg', 'image/png']

export function obtenerRequisitos(tipoTramite, tipoPredio) {
  const consulta = `tipoTramite=${encodeURIComponent(tipoTramite)}&tipoPredio=${encodeURIComponent(tipoPredio)}`
  return pedir(`${BASE}/requisitos?${consulta}`)
}

export function obtenerSolicitud(id) {
  return pedir(`${BASE}/${encodeURIComponent(id)}`)
}

/**
 * Envía la solicitud como multipart. El backend empareja `requisitoIds` y `archivos` por posición,
 * así que ambos se agregan en el mismo orden.
 * @param adjuntos lista de { idRequisito, archivo: { uri, nombre, tipo, file? } }
 */
export function enviarSolicitud({ tipoTramite, tipoPredio, codigoPredio, adjuntos }) {
  const datos = new FormData()
  datos.append('tipoTramite', tipoTramite)
  datos.append('tipoPredio', tipoPredio)
  if (codigoPredio?.trim()) datos.append('codigoPredio', codigoPredio.trim())
  for (const { idRequisito, archivo } of adjuntos) {
    datos.append('requisitoIds', String(idRequisito))
    if (Platform.OS === 'web' && archivo.file) {
      datos.append('archivos', archivo.file, archivo.nombre)
    } else {
      datos.append('archivos', { uri: archivo.uri, name: archivo.nombre, type: archivo.tipo })
    }
  }
  return pedir(BASE, { method: 'POST', body: datos })
}
