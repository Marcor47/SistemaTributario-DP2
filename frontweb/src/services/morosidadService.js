import { pedir } from './api.js'
import { DATOS_DEMO } from '../mocks/morosidad.js'

/*
 * HU008 (RF-009): Servicio de morosidad institucional.
 * Endpoint real: GET /api/indicadores/morosidad?anio=&numeroPeriodo=
 * Alertas activas: GET /api/alertas-desvio?indicadorCodigo=INDICE_MOROSIDAD
 *
 * Si VITE_USAR_MOCK=true (o la petición falla con 404/SIN_CONEXION)
 * se sirven los datos de demostración definidos en mocks/morosidad.js.
 */

const USAR_MOCK = import.meta.env.VITE_USAR_MOCK === 'true'

async function obtenerConFallback(fn) {
  if (USAR_MOCK) return fn()
  try {
    return await fn()
  } catch (error) {
    if (error.codigo === 'SIN_CONEXION' || error.estado === 404 || error.codigo === 'MOROSIDAD_NO_CALCULADA') {
      return fn()
    }
    throw error
  }
}

/* Transforma la respuesta del backend al formato interno que usa la pantalla. */
function transformarRespuestaBackend(respuesta, alertas) {
  const alerta = alertas.find(
    (a) => a.indicadorCodigo === 'INDICE_MOROSIDAD' && a.alcance === 'TOTAL',
  )
  // El backend devuelve el índice total directamente; los montos llegan en el cálculo.
  return {
    lote: {
      idLote: respuesta.idLote,
      fechaCorte: respuesta.fechaCorte,
      anio: respuesta.anio,
      numeroPeriodo: respuesta.numeroPeriodo,
      periodo: respuesta.periodo,
    },
    total: Number(respuesta.total ?? 0),
    // El endpoint /morosidad no devuelve montos; se omiten cuando no hay datos.
    montoEmitidoTotal: null,
    montoVencidoTotal: null,
    meta: alerta?.valorMeta ?? null,
    umbral: alerta ? Math.abs(((alerta.valorObservado - alerta.valorMeta) / alerta.valorMeta) * 100) : null,
    porTributo: (respuesta.porTributo ?? []).map((item) => ({
      valorAlcance: item.valorAlcance,
      indice: Number(item.indice),
      montoEmitido: null,
      montoVencido: null,
    })),
    porZona: (respuesta.porZona ?? []).map((item) => ({
      valorAlcance: item.valorAlcance,
      indice: Number(item.indice),
      montoEmitido: null,
      montoVencido: null,
    })),
    alertas: alertas,
  }
}

/*
 * Obtiene el índice de morosidad. Sin parámetros devuelve el último periodo publicado.
 * Con anio y numeroPeriodo filtra por el periodo indicado.
 */
export async function obtenerMorosidad(anio, numeroPeriodo) {
  return obtenerConFallback(async () => {
    if (USAR_MOCK) return { ...DATOS_DEMO }
    const params = new URLSearchParams()
    if (anio) params.set('anio', anio)
    if (numeroPeriodo) params.set('numeroPeriodo', numeroPeriodo)
    const [respuesta, alertas] = await Promise.all([
      pedir(`/indicadores/morosidad?${params.toString()}`),
      pedir('/alertas-desvio?indicadorCodigo=INDICE_MOROSIDAD').catch(() => []),
    ])
    return transformarRespuestaBackend(respuesta, alertas)
  })
}
