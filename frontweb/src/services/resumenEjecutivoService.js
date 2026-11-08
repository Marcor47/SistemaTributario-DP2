import { pedir } from './api.js'
import { esPeriodoValido, nombreArchivo } from '../utils/resumenEjecutivo.js'

// HU017 / RF-023: resumen ejecutivo del periodo con un modelo de lenguaje local, generado por el backend:
//   POST /api/resumenes-ejecutivos                  { periodo: "AAAA-MM" } -> resumen en estado GENERADO
//   POST /api/resumenes-ejecutivos/{id}/revision    -> el mismo resumen en estado REVISADO
//   GET  /api/resumenes-ejecutivos/{id}/descarga    -> { texto }; 422 RESUMEN_SIN_REVISAR si no se revisó
// Un resumen trae { idResumen, periodo, texto, indicadoresUsados: [{ codigo, nombre, valor, unidad }], estado,
// fechaGeneracion }. No hay datos de respaldo: si el servicio no responde, la pantalla muestra el error.

function errorDeNegocio(codigo, mensaje, estado = 422) {
  return Object.assign(new Error(mensaje), { codigo, estado, detalles: [] })
}

function servicioNoDisponible(err) {
  if (err.estado === 404 && (!err.codigo || err.codigo === 'RUTA_NO_ENCONTRADA' || err.codigo === 'ERROR_INTERNO')) {
    return errorDeNegocio('SERVICIO_NO_DISPONIBLE',
      'El servicio de resúmenes ejecutivos todavía no está disponible en el servidor.', 404)
  }
  return err
}

export async function generarResumen(periodo) {
  if (!esPeriodoValido(periodo)) {
    throw errorDeNegocio('PERIODO_INVALIDO', 'Elige el año y el mes del periodo.', 400)
  }
  try {
    return await pedir('/resumenes-ejecutivos', { method: 'POST', body: JSON.stringify({ periodo }) })
  } catch (err) {
    throw servicioNoDisponible(err)
  }
}

// El usuario confirma que revisó el texto contra los indicadores; sin esto no se puede descargar.
export async function marcarRevisado(resumen) {
  return pedir(`/resumenes-ejecutivos/${resumen.idResumen}/revision`, { method: 'POST' })
}

// Devuelve { nombreArchivo, contenido }. Un resumen sin revisar no se descarga, igual que en el backend.
export async function descargarResumen(resumen) {
  if (resumen.estado !== 'REVISADO') {
    throw errorDeNegocio('RESUMEN_SIN_REVISAR', 'El resumen debe revisarse antes de descargarlo.')
  }
  const { texto } = await pedir(`/resumenes-ejecutivos/${resumen.idResumen}/descarga`)
  return { nombreArchivo: nombreArchivo(resumen.periodo), contenido: texto }
}
