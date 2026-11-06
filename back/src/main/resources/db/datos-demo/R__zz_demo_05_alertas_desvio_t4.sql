-- Solo perfil local. Alertas de desvío de meta de demostración para el periodo 2026-T4 (HU039).
-- La semilla es repetible e idempotente: se apoya en las restricciones únicas y en NOT EXISTS para no
-- duplicar indicadores, metas, valores ni alertas si se vuelve a ejecutar.
--
-- Se reutiliza RECUPERACION_DEUDA (el único indicador trimestral del catálogo) y se agregan tres
-- indicadores trimestrales de demostración. Las cuatro metas de 2026/4 quedan por debajo del umbral
-- desfavorable: -25 % en los ascendentes y +40 % en el descendente.
DO $demo$
DECLARE
    v_lote BIGINT;
    v_corte DATE := DATE '2026-11-05';
BEGIN
    -- 1. Catálogo: indicadores trimestrales nuevos.
    INSERT INTO analitica.indicador
        (codigo, nombre, definicion, formula, fuente, unidad_medida, periodicidad, sentido, activo)
    VALUES
        ('COBERTURA_PREDIAL', 'Cobertura predial',
         'Porcentaje de predios con datos verificados o actualizados durante el trimestre.',
         '(Predios actualizados en el trimestre / Total de predios activos) x 100',
         'Padrón de contribuyentes y predios', 'PORCENTAJE', 'TRIMESTRAL', 'ASCENDENTE', TRUE),
        ('EFICIENCIA_COBRANZA', 'Eficiencia de cobranza',
         'Porcentaje de la deuda exigible del trimestre efectivamente cobrada.',
         '(Monto cobrado en el trimestre / Monto exigible del trimestre) x 100',
         'Cuenta corriente SAMNET: obligaciones y pagos', 'PORCENTAJE', 'TRIMESTRAL', 'ASCENDENTE', TRUE),
        ('MOROSIDAD_TRIMESTRAL', 'Morosidad trimestral',
         'Porcentaje de la deuda emitida del trimestre que permanece vencida y sin pagar.',
         '(Saldo vencido al cierre del trimestre / Monto emitido del trimestre) x 100',
         'Cuenta corriente SAMNET: obligaciones vencidas', 'PORCENTAJE', 'TRIMESTRAL', 'DESCENDENTE', TRUE)
    ON CONFLICT (codigo) DO NOTHING;

    -- 2. Carga publicada que sustenta los valores de 2026-T4.
    SELECT l.id_lote INTO v_lote
    FROM analitica.lote_etl l
    WHERE l.fuente = 'ps_etl_indicadores_demo_t4' AND l.fecha_corte = v_corte
    ORDER BY l.id_lote DESC
    LIMIT 1;

    IF v_lote IS NULL THEN
        INSERT INTO analitica.lote_etl
            (fuente, fecha_corte, tipo_ejecucion, estado, fecha_inicio, fecha_fin,
             registros_leidos, registros_aceptados, registros_rechazados)
        VALUES
            ('ps_etl_indicadores_demo_t4', v_corte, 'PROGRAMADA', 'PUBLICADO',
             v_corte + TIME '02:00', v_corte + TIME '02:10', 4, 4, 0)
        RETURNING id_lote INTO v_lote;
    END IF;

    -- 3. Metas de 2026/4. RECUPERACION_DEUDA puede existir desde db/datos-prueba: no se sobrescribe.
    INSERT INTO analitica.meta_indicador
        (indicador_codigo, anio, numero_periodo, valor_meta, observacion,
         fecha_registro, fecha_actualizacion, alcance, umbral_desvio)
    VALUES
        ('RECUPERACION_DEUDA', 2026, 4, 35.00, 'Demo HU039 T4 2026',
         v_corte + TIME '09:00', v_corte + TIME '09:00', 'TOTAL', 10.00),
        ('COBERTURA_PREDIAL', 2026, 4, 80.00, 'Demo HU039 T4 2026',
         v_corte + TIME '09:00', v_corte + TIME '09:00', 'TOTAL', 10.00),
        ('EFICIENCIA_COBRANZA', 2026, 4, 70.00, 'Demo HU039 T4 2026',
         v_corte + TIME '09:00', v_corte + TIME '09:00', 'TOTAL', 10.00),
        ('MOROSIDAD_TRIMESTRAL', 2026, 4, 10.00, 'Demo HU039 T4 2026',
         v_corte + TIME '09:00', v_corte + TIME '09:00', 'TOTAL', 10.00)
    ON CONFLICT ON CONSTRAINT uq_meta_indicador_periodo DO NOTHING;

    -- 4. Valores observados. Se derivan de la meta vigente para garantizar un desvío desfavorable > 10 %.
    INSERT INTO analitica.valor_indicador
        (indicador_codigo, id_lote, anio, numero_periodo, alcance, valor_alcance, valor, fecha_corte)
    SELECT
        v.indicador_codigo,
        v_lote,
        2026,
        4,
        'TOTAL',
        NULL,
        CASE
            WHEN i.sentido = 'ASCENDENTE' THEN round(m.valor_meta * 0.75, 4)
            ELSE round(m.valor_meta * 1.40, 4)
        END,
        v_corte
    FROM (VALUES
        ('RECUPERACION_DEUDA'),
        ('COBERTURA_PREDIAL'),
        ('EFICIENCIA_COBRANZA'),
        ('MOROSIDAD_TRIMESTRAL')
    ) AS v(indicador_codigo)
    JOIN analitica.indicador i ON i.codigo = v.indicador_codigo
    JOIN analitica.meta_indicador m
      ON m.indicador_codigo = v.indicador_codigo
     AND m.anio = 2026
     AND m.numero_periodo = 4
     AND m.alcance = 'TOTAL'
    ON CONFLICT ON CONSTRAINT uq_valor_indicador DO NOTHING;

    -- 5. Alertas activas de la carga. Se resuelven id_meta e id_valor por sus claves de negocio.
    INSERT INTO analitica.alerta_desvio
        (id_meta, id_valor, valor_observado, valor_meta, desvio_porcentual, fecha_generacion, activa)
    SELECT
        m.id,
        v.id_valor,
        v.valor,
        m.valor_meta,
        round((v.valor - m.valor_meta) * 100 / m.valor_meta, 2),
        v_corte + TIME '02:15',
        TRUE
    FROM analitica.valor_indicador v
    JOIN analitica.lote_etl l ON l.id_lote = v.id_lote
    JOIN analitica.meta_indicador m
      ON m.indicador_codigo = v.indicador_codigo
     AND m.anio = v.anio
     AND m.numero_periodo = v.numero_periodo
     AND m.alcance = v.alcance
    WHERE l.id_lote = v_lote
      AND v.anio = 2026
      AND v.numero_periodo = 4
      AND v.indicador_codigo IN (
          'RECUPERACION_DEUDA',
          'COBERTURA_PREDIAL',
          'EFICIENCIA_COBRANZA',
          'MOROSIDAD_TRIMESTRAL'
      )
      AND NOT EXISTS (
          SELECT 1 FROM analitica.alerta_desvio a WHERE a.id_valor = v.id_valor
      )
      AND NOT EXISTS (
          SELECT 1 FROM analitica.alerta_desvio a WHERE a.id_meta = m.id AND a.activa
      );
END;
$demo$;
