/*
 * HU008 – Datos ficticios de demostración para el índice de morosidad.
 * Reproducen la muestra de control de IndiceMorosidadControllerTest y los datos
 * de R__datos_prueba_06_alertas_desvio.sql (lote ficticio de octubre de 2026).
 *
 * La pantalla usa estos datos únicamente con VITE_USAR_MOCK=true.
 * Ambos desgloses suman 1 050 000 emitidos y 155 400 vencidos no pagados.
 */

export const LOTE_DEMO = {
  idLote: 1,
  fechaCorte: '2026-10-18',
  anio: 2026,
  numeroPeriodo: 10,
  periodo: 'Octubre 2026',
}

export const META_DEMO = 12.5 // % (RF-010: meta configurada para el índice de morosidad)
export const UMBRAL_DESVIO = 10 // % de desvío permitido antes de activar alerta (RF-052)

/* Tabla plana: todas las dimensiones juntas para la tabla de detalle. */
export const DATOS_DEMO = {
  lote: LOTE_DEMO,
  total: 14.8,
  montoEmitidoTotal: 1_050_000,
  montoVencidoTotal: 155_400,
  meta: META_DEMO,
  umbral: UMBRAL_DESVIO,
  porTributo: [
    { valorAlcance: 'PREDIAL',             indice: 18.75, montoEmitido: 800_000, montoVencido: 150_000 },
    { valorAlcance: 'ARBITRIOS',           indice:  2.16, montoEmitido: 250_000, montoVencido:   5_400 },
  ],
  porZona: [
    { valorAlcance: 'Zona Norte', indice: 25, montoEmitido: 400_000, montoVencido: 100_000 },
    { valorAlcance: 'Zona Sur', indice: 13.3333, montoEmitido: 300_000, montoVencido: 40_000 },
    { valorAlcance: 'Zona Este', indice: 5, montoEmitido: 200_000, montoVencido: 10_000 },
    { valorAlcance: 'Zona Oeste', indice: 3.6, montoEmitido: 150_000, montoVencido: 5_400 },
  ],
  alertas: [
    {
      indicadorCodigo: 'INDICE_MOROSIDAD',
      alcance: 'TOTAL',
      valorObservado: 14.8,
      valorMeta: 12.5,
      desvioPorcentual: 18.4,
      activa: true,
    },
  ],
}

/* Cálculo auxiliar de morosidad para los tests (HU008 RF-009). */
export function calcularIndice(montoVencido, montoEmitido) {
  if (!montoEmitido || montoEmitido === 0) return null
  return (montoVencido / montoEmitido) * 100
}

/* Devuelve true si hay alerta de desvío activa para el TOTAL. */
export function tieneAlertaTotal(datos) {
  return datos.alertas.some(
    (a) => a.indicadorCodigo === 'INDICE_MOROSIDAD' && a.alcance === 'TOTAL' && a.activa,
  )
}

/* Etiqueta amigable para el nombre de un tributo. */
export function etiquetaTributo(clave) {
  return (
    {
      PREDIAL: 'Impuesto Predial',
      ARBITRIOS: 'Arbitrios Municipales',
    }[clave] ?? clave
  )
}
