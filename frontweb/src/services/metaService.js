import { pedir } from './api.js'

// HU009: contrato de la pantalla de configuración de metas.
export function listarIndicadores() {
  return pedir('/indicadores')
}

export function buscarMetas(indicadorCodigo, anio) {
  const parametros = new URLSearchParams()
  if (indicadorCodigo) parametros.set('indicadorCodigo', indicadorCodigo)
  if (anio) parametros.set('anio', anio)
  return pedir(`/metas?${parametros.toString()}`)
}

export function registrarMeta(meta) {
  return pedir('/metas', { method: 'POST', body: JSON.stringify(meta) })
}

export function actualizarMeta(id, meta) {
  return pedir(`/metas/${id}`, { method: 'PUT', body: JSON.stringify(meta) })
}

export function eliminarMeta(id) {
  return pedir(`/metas/${id}`, { method: 'DELETE' })
}
