-- Datos ficticios de demostración para la alerta de desvío de meta en el dashboard (HU039).
-- Una carga publicada del ETL con valores de octubre de 2026 comparados con las metas de R__datos_prueba_02:
--   * Índice de morosidad 14,80 % frente a una meta de 12,50 % (sentido descendente): desvío de +18,40 %.
--   * Recaudación sobre la emisión 72,00 % frente a una meta de 85,00 % (sentido ascendente): desvío de -15,29 %.
--   * Recuperación de deuda del 4.° trimestre 33,00 % frente a 35,00 %: desvío de -5,71 %, dentro del umbral.
INSERT INTO analitica.lote_etl
    (fuente, fecha_corte, tipo_ejecucion, estado, fecha_inicio, fecha_fin, registros_leidos, registros_aceptados,
     registros_rechazados)
SELECT 'ps_etl_indicadores_demo', DATE '2026-10-18', 'PROGRAMADA', 'PUBLICADO', TIMESTAMP '2026-10-18 02:00',
       TIMESTAMP '2026-10-18 02:08', 120, 120, 0
WHERE NOT EXISTS (SELECT 1 FROM analitica.lote_etl
                  WHERE fuente = 'ps_etl_indicadores_demo' AND fecha_corte = DATE '2026-10-18');

INSERT INTO analitica.valor_indicador
    (indicador_codigo, id_lote, anio, numero_periodo, alcance, valor_alcance, valor, fecha_corte)
SELECT v.indicador_codigo, l.id_lote, 2026, v.numero_periodo, 'TOTAL', NULL, v.valor, l.fecha_corte
FROM analitica.lote_etl l
CROSS JOIN (VALUES ('INDICE_MOROSIDAD', 10, 14.8000),
                   ('PORC_RECAUDACION_EMISION', 10, 72.0000),
                   ('RECUPERACION_DEUDA', 4, 33.0000)) AS v (indicador_codigo, numero_periodo, valor)
WHERE l.fuente = 'ps_etl_indicadores_demo' AND l.fecha_corte = DATE '2026-10-18'
ON CONFLICT ON CONSTRAINT uq_valor_indicador DO NOTHING;

-- Alertas que dejó esa carga, con el mismo cálculo que AlertaDesvio.evaluar().
INSERT INTO analitica.alerta_desvio
    (id_meta, id_valor, valor_observado, valor_meta, desvio_porcentual, fecha_generacion, activa)
SELECT m.id, v.id_valor, v.valor, m.valor_meta,
       round((v.valor - m.valor_meta) * 100 / m.valor_meta, 2), TIMESTAMP '2026-10-18 02:08', TRUE
FROM analitica.valor_indicador v
JOIN analitica.lote_etl l ON l.id_lote = v.id_lote
JOIN analitica.meta_indicador m ON m.indicador_codigo = v.indicador_codigo AND m.anio = v.anio
                               AND m.numero_periodo = v.numero_periodo AND m.alcance = v.alcance
WHERE l.fuente = 'ps_etl_indicadores_demo' AND l.fecha_corte = DATE '2026-10-18'
  AND v.indicador_codigo IN ('INDICE_MOROSIDAD', 'PORC_RECAUDACION_EMISION')
  AND NOT EXISTS (SELECT 1 FROM analitica.alerta_desvio a WHERE a.id_valor = v.id_valor);
