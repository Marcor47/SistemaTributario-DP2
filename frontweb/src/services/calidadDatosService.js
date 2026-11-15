import { pedir } from './api.js'

// HU025, HU026, HU028 y HU029: cargas del ETL, su evaluación de calidad y la bandeja de homologación.
export const listarCargas = (opciones = {}) => pedir('/etl/lotes?limite=60', opciones)
export const obtenerCarga = (idLote) => pedir(`/etl/lotes/${encodeURIComponent(idLote)}`)
export const obtenerCuarentena = (idLote) => pedir(`/etl/lotes/${encodeURIComponent(idLote)}/cuarentena`)
export const ejecutarCarga = (fechaCorte) => pedir('/etl/lotes', {
  method: 'POST',
  body: JSON.stringify(fechaCorte ? { fechaCorte } : {}),
})

export const listarCasos = (estado = 'PENDIENTE', opciones = {}) =>
  pedir(`/homologacion/casos?estado=${encodeURIComponent(estado)}`, opciones)
export const obtenerCaso = (idCaso) => pedir(`/homologacion/casos/${encodeURIComponent(idCaso)}`)
export const aprobarCaso = (idCaso, motivo) => pedir(`/homologacion/casos/${encodeURIComponent(idCaso)}/aprobacion`, {
  method: 'POST', body: JSON.stringify({ motivo }),
})
export const rechazarCaso = (idCaso, motivo) => pedir(`/homologacion/casos/${encodeURIComponent(idCaso)}/rechazo`, {
  method: 'POST', body: JSON.stringify({ motivo }),
})
export const reasignarCaso = (idCaso, idMaestro, motivo) => pedir(`/homologacion/casos/${encodeURIComponent(idCaso)}/reasignacion`, {
  method: 'POST', body: JSON.stringify({ idMaestro, motivo }),
})

// HU040: decisiones de homologación del registro maestro al que quedó asociado un código de SAMNET (solo lectura).
export const decisionesPorCodigo = (tipoEntidad, codigo) =>
  pedir(`/homologacion/decisiones?${new URLSearchParams({ tipoEntidad, codigo: String(codigo).trim() })}`)

// HU030: ejecuta el K-means sobre la última carga publicada; los segmentos quedan en borrador hasta que la jefatura
// los apruebe. Repetirlo con la misma carga devuelve el modelo ya calculado.
export const calcularSegmentacion = () => pedir('/segmentos/calculos', { method: 'POST' })
export const listarSegmentosBorrador = (opciones = {}) => pedir('/segmentos?estado=BORRADOR', opciones)
