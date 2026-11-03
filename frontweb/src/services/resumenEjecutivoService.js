import { pedir } from './api.js'
import { MODO_DEMO } from './authService.js'
import { INDICADORES_DEMO, redactarResumenDemo } from '../mocks/resumenEjecutivo.js'
import { esPeriodoValido, formatoPeriodo, nombreArchivo } from '../utils/resumenEjecutivo.js'

// HU017 / RF-023: resumen ejecutivo del periodo con un modelo de lenguaje local. El API del backend aún no está
// publicado; mientras no responda se usan datos ficticios. Contrato previsto:
//   POST /api/resumenes-ejecutivos                  { periodo: "AAAA-MM" } -> resumen en estado GENERADO
//   POST /api/resumenes-ejecutivos/{id}/revision    -> el mismo resumen en estado REVISADO
//   GET  /api/resumenes-ejecutivos/{id}/descarga    -> { texto }; 422 RESUMEN_SIN_REVISAR si no se revisó
// Un resumen trae { idResumen, periodo, texto, indicadoresUsados: [{ codigo, nombre, valor, unidad }], estado,
// fechaGeneracion }. El texto tiene hasta 300 palabras y solo usa indicadores calculados del periodo.
let siguienteIdDemo = 1

const esperar = () => new Promise((resolve) => setTimeout(resolve, 250))
const usarDatosDemo = (err) =>
  MODO_DEMO || err.estado === 401 || err.estado === 404 || err.codigo === 'SIN_CONEXION'

function errorDeNegocio(codigo, mensaje, estado = 422) {
  return Object.assign(new Error(mensaje), { codigo, estado, detalles: [] })
}

export function reiniciarResumenesDemo() {
  siguienteIdDemo = 1
}

// Devuelve el resumen con demo=true cuando es ficticio y no se guarda en el servidor.
export async function generarResumen(periodo, ahora = new Date()) {
  if (!esPeriodoValido(periodo)) {
    throw errorDeNegocio('PERIODO_INVALIDO', 'Elige el año y el mes del periodo.', 400)
  }
  try {
    const resumen = await pedir('/resumenes-ejecutivos', { method: 'POST', body: JSON.stringify({ periodo }) })
    return { ...resumen, demo: false }
  } catch (err) {
    if (!usarDatosDemo(err)) throw err
    await esperar()
    return generarDemo(periodo, ahora)
  }
}

function generarDemo(periodo, ahora) {
  const indicadores = INDICADORES_DEMO[periodo]
  if (!indicadores) {
    throw errorDeNegocio('PERIODO_SIN_INDICADORES',
      `${formatoPeriodo(periodo)} no tiene indicadores calculados. Prueba con septiembre u octubre de 2026.`)
  }
  return {
    idResumen: siguienteIdDemo++,
    periodo,
    texto: redactarResumenDemo(formatoPeriodo(periodo), indicadores),
    indicadoresUsados: indicadores.map((indicador) => ({ ...indicador })),
    estado: 'GENERADO',
    fechaGeneracion: ahora.toISOString().slice(0, 19),
    demo: true,
  }
}

// El usuario confirma que revisó el texto contra los indicadores; sin esto no se puede descargar.
export async function marcarRevisado(resumen) {
  if (resumen.demo) {
    await esperar()
    return { ...resumen, estado: 'REVISADO' }
  }
  const revisado = await pedir(`/resumenes-ejecutivos/${resumen.idResumen}/revision`, { method: 'POST' })
  return { ...revisado, demo: false }
}

// Devuelve { nombreArchivo, contenido }. Un resumen sin revisar no se descarga, igual que en el backend.
export async function descargarResumen(resumen) {
  if (resumen.estado !== 'REVISADO') {
    throw errorDeNegocio('RESUMEN_SIN_REVISAR', 'El resumen debe revisarse antes de descargarlo.')
  }
  if (resumen.demo) return { nombreArchivo: nombreArchivo(resumen.periodo), contenido: resumen.texto }
  const { texto } = await pedir(`/resumenes-ejecutivos/${resumen.idResumen}/descarga`)
  return { nombreArchivo: nombreArchivo(resumen.periodo), contenido: texto }
}
