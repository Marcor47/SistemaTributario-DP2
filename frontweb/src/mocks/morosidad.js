/*
 * HU008 – Datos ficticios de demostración para el índice de morosidad.
 * Reproducen la muestra de control de IndiceMorosidadControllerTest y los datos
 * de R__datos_prueba_06_alertas_desvio.sql (lote ficticio de octubre de 2026).
 *
 * La pantalla usa estos datos si el backend no está disponible (VITE_USAR_MOCK=true
 * o si la petición falla). Al integrar el backend real sólo se reemplaza morosidadService.js.
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
    { valorAlcance: 'LIMPIEZA_PUBLICA',    indice:  5.00, montoEmitido: 100_000, montoVencido:   5_000 },
    { valorAlcance: 'PARQUES_JARDINES',    indice:  2.00, montoEmitido:  50_000, montoVencido:   1_000 },
    { valorAlcance: 'SEGURIDAD_CIUDADANA', indice:  1.60, montoEmitido:  62_500, montoVencido:   1_000 },
  ],
  porZona: [
    { valorAlcance: 'Zona Norte',  indice: 20.40, montoEmitido: 280_000, montoVencido:  57_120 },
    { valorAlcance: 'Zona Sur',    indice: 15.80, montoEmitido: 310_000, montoVencido:  48_980 },
    { valorAlcance: 'Zona Este',   indice:  9.20, montoEmitido: 260_000, montoVencido:  23_920 },
    { valorAlcance: 'Zona Oeste',  indice:  5.00, montoEmitido: 130_000, montoVencido:   6_500 },
    { valorAlcance: 'Zona Centro', indice:  3.25, montoEmitido:  70_000, montoVencido:   2_275 },
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
      LIMPIEZA_PUBLICA: 'Limpieza Pública',
      PARQUES_JARDINES: 'Parques y Jardines',
      SEGURIDAD_CIUDADANA: 'Seguridad Ciudadana',
    }[clave] ?? clave
  )
}
