import { pedir } from './api'
import { cuerpoPreferenciaRecordatorio, RUTA_PREFERENCIAS_AVISOS } from '../utils/preferenciasAvisos'

// HU016: preferencias del contribuyente autenticado. La ruta /api/portal/** usa la cookie de sesión y no CSRF.
export function obtenerPreferenciaAviso({ signal } = {}) {
  return pedir(RUTA_PREFERENCIAS_AVISOS, { signal, cache: 'no-store' })
}

export function guardarRecordatorioVencimiento(recordatorioVencimiento) {
  return pedir(RUTA_PREFERENCIAS_AVISOS, {
    method: 'PUT',
    body: cuerpoPreferenciaRecordatorio(recordatorioVencimiento),
  })
}
