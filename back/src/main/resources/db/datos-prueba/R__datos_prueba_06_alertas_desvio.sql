-- HU039: las alertas de desvío de meta ya no se siembran con valores escritos a mano. Salen de los indicadores
-- calculados sobre los hechos de cada carga publicada (HU008, CalculoMorosidadInicial en el perfil local).
-- Este script retira la carga de demostración 'ps_etl_indicadores_demo' (2026-10-18) que versiones anteriores
-- insertaban con 14,80 % de morosidad, 72 % de recaudación y 33 % de recuperación, junto con sus alertas.
DELETE FROM analitica.alerta_desvio a
USING analitica.valor_indicador v, analitica.lote_etl l
WHERE a.id_valor = v.id_valor
  AND v.id_lote = l.id_lote
  AND l.fuente = 'ps_etl_indicadores_demo';

DELETE FROM analitica.valor_indicador v
USING analitica.lote_etl l
WHERE v.id_lote = l.id_lote
  AND l.fuente = 'ps_etl_indicadores_demo';

-- La carga solo se borra si nada más la referencia (por ejemplo, un aviso de correo registrado en otra base).
DELETE FROM analitica.lote_etl l
WHERE l.fuente = 'ps_etl_indicadores_demo'
  AND NOT EXISTS (SELECT 1 FROM operativo.aviso_correo c WHERE c.id_lote = l.id_lote)
  AND NOT EXISTS (SELECT 1 FROM analitica.caso_homologacion c WHERE c.id_lote = l.id_lote);
