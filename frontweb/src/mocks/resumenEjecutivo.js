/*
 * HU017 – Indicadores ficticios para la demostración del resumen ejecutivo. Los de octubre coinciden con los de
 * R__datos_prueba_06_alertas_desvio.sql; septiembre es solo para poder comparar dos periodos.
 *
 * La pantalla usa estos datos únicamente cuando el servicio de resúmenes no responde (ver resumenEjecutivoService.js).
 */
export const INDICADORES_DEMO = {
  '2026-09': [
    { codigo: 'INDICE_MOROSIDAD', nombre: 'Índice de morosidad', valor: 13.2, unidad: 'PORCENTAJE' },
    { codigo: 'PORC_RECAUDACION_EMISION', nombre: 'Recaudación sobre emisión', valor: 66.5, unidad: 'PORCENTAJE' },
    { codigo: 'RECUPERACION_DEUDA', nombre: 'Recuperación de deuda', valor: 28, unidad: 'PORCENTAJE' },
  ],
  '2026-10': [
    { codigo: 'INDICE_MOROSIDAD', nombre: 'Índice de morosidad', valor: 14.8, unidad: 'PORCENTAJE' },
    { codigo: 'PORC_RECAUDACION_EMISION', nombre: 'Recaudación sobre emisión', valor: 72, unidad: 'PORCENTAJE' },
    { codigo: 'RECUPERACION_DEUDA', nombre: 'Recuperación de deuda', valor: 33, unidad: 'PORCENTAJE' },
  ],
}

const decimal = (valor) => valor.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// Redacta el texto solo con las cifras de los indicadores, como lo haría el modelo local (RF-023).
export function redactarResumenDemo(periodoLegible, indicadores) {
  const porCodigo = Object.fromEntries(indicadores.map((indicador) => [indicador.codigo, indicador.valor]))
  return [
    `Resumen ejecutivo de ${periodoLegible}.`,
    `El índice de morosidad del periodo fue de ${decimal(porCodigo.INDICE_MOROSIDAD)} %, es decir, esa proporción de la emisión exigible sigue vencida y sin pagar.`,
    `La recaudación alcanzó el ${decimal(porCodigo.PORC_RECAUDACION_EMISION)} % de lo emitido, mientras que la recuperación de deuda llegó al ${decimal(porCodigo.RECUPERACION_DEUDA)} %.`,
    'Se recomienda revisar este texto con los indicadores calculados antes de descargarlo.',
  ].join(' ')
}
