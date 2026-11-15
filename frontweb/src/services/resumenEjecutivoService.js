import { pedir, pedirArchivo } from './api.js'
import { esPeriodoValido, nombreArchivo } from '../utils/resumenEjecutivo.js'

// HU017 / RF-023: resumen ejecutivo del periodo redactado por el modelo de lenguaje con los indicadores de la carga:
//   POST /api/resumenes-ejecutivos                  { periodo: "AAAA-MM" } -> resumen (el guardado si ya existe)
//   POST /api/resumenes-ejecutivos/{id}/revision    -> el mismo resumen en estado REVISADO
//   GET  /api/resumenes-ejecutivos/{id}/pdf         -> PDF institucional; 422 RESUMEN_SIN_REVISAR si no se revisó
// Si el servicio de IA no está disponible, el backend responde 503 IA_NO_DISPONIBLE y la pantalla lo informa.

function errorDeNegocio(codigo, mensaje, estado = 422) {
  return Object.assign(new Error(mensaje), { codigo, estado, detalles: [] })
}

export async function generarResumen(periodo) {
  if (!esPeriodoValido(periodo)) {
    throw errorDeNegocio('PERIODO_INVALIDO', 'Elige el año y el mes del periodo.', 400)
  }
  return pedir('/resumenes-ejecutivos', { method: 'POST', body: JSON.stringify({ periodo }) })
}

// El usuario confirma que revisó el texto contra los indicadores; sin esto no se puede descargar.
export async function marcarRevisado(resumen) {
  return pedir(`/resumenes-ejecutivos/${resumen.idResumen}/revision`, { method: 'POST' })
}

// Devuelve { blob, nombre } del PDF. Un resumen sin revisar no se descarga, igual que en el backend.
export async function descargarResumen(resumen) {
  if (resumen.estado !== 'REVISADO') {
    throw errorDeNegocio('RESUMEN_SIN_REVISAR', 'El resumen debe revisarse antes de descargarlo.')
  }
  return pedirArchivo(`/resumenes-ejecutivos/${resumen.idResumen}/pdf`, nombreArchivo(resumen.periodo))
}
