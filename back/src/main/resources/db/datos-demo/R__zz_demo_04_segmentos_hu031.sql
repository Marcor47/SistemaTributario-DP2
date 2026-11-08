-- Solo perfil local. Segmentación ficticia en borrador para demostrar la aprobación de HU031.
-- No es un modelo de IA real y no modifica los segmentos publicados de HU011 hasta que la jefatura la apruebe.
-- La semilla es idempotente: después de aprobarla, los segmentos quedan publicados y no se vuelven a crear.
DO $demo$
DECLARE
    v_modelo BIGINT;
    v_lote BIGINT;
    v_corte DATE := DATE '2026-11-04';
BEGIN
    INSERT INTO analitica.modelo_ia (nombre, tipo, version, hash_integridad, formato, fecha_registro)
    VALUES ('DEMO_HU031_NO_ES_MODELO_IA', 'SEGMENTACION', 'demo-1',
            encode(public.digest('Segmentacion fija para demostrar la aprobacion de HU031', 'sha256'), 'hex'),
            'JSON_DEMO', TIMESTAMP '2026-11-04 09:00')
    ON CONFLICT (nombre, version) DO NOTHING;

    SELECT id_modelo INTO v_modelo
    FROM analitica.modelo_ia
    WHERE nombre = 'DEMO_HU031_NO_ES_MODELO_IA' AND version = 'demo-1';

    IF v_modelo IS NULL
       OR EXISTS (SELECT 1 FROM analitica.segmento WHERE id_modelo = v_modelo) THEN
        RETURN;
    END IF;

    SELECT l.id_lote, l.fecha_corte INTO v_lote, v_corte
    FROM analitica.lote_etl l
    WHERE l.fuente = 'DEMO_HU011_SAMNET_SINTETICO'
    ORDER BY l.id_lote DESC
    LIMIT 1;

    IF v_lote IS NULL THEN
        v_corte := DATE '2026-11-04';
    END IF;

    INSERT INTO analitica.segmento (id_modelo, nombre, descripcion, tamano, variables_caracteristicas, estado)
    VALUES
        (v_modelo, 'Demo IA: pago puntual',
         'Contribuyentes con pagos al día y deuda baja. Categoría fija de la semilla, sin inferencia IA.',
         0, '{"regularidad":"alta","deuda":"baja","porcentajePagoATiempo":95}', 'BORRADOR'),
        (v_modelo, 'Demo IA: riesgo medio',
         'Contribuyentes con pagos parciales y deuda pendiente. Categoría fija de la semilla, sin inferencia IA.',
         0, '{"regularidad":"media","deuda":"media","porcentajePagoATiempo":60}', 'BORRADOR'),
        (v_modelo, 'Demo IA: riesgo alto',
         'Contribuyentes sin pagos y con deuda vencida. Categoría fija de la semilla, sin inferencia IA.',
         0, '{"regularidad":"baja","deuda":"alta","porcentajePagoATiempo":15}', 'BORRADOR');

    IF v_lote IS NOT NULL THEN
        INSERT INTO analitica.asignacion_segmento (id_segmento, seudonimo_contribuyente, fecha_asignacion)
        SELECT s.id_segmento, t.seudonimo_contribuyente, v_corte + TIME '09:00'
        FROM (
            SELECT seudonimo_contribuyente, sum(monto_insoluto) AS deuda, sum(monto_pagado) AS pagado
            FROM analitica.hecho_cobranza
            WHERE id_lote = v_lote
            GROUP BY seudonimo_contribuyente
        ) t
        JOIN analitica.segmento s
          ON s.id_modelo = v_modelo
         AND s.nombre = CASE WHEN t.deuda = 0 THEN 'Demo IA: pago puntual'
                             WHEN t.pagado > 0 THEN 'Demo IA: riesgo medio'
                             ELSE 'Demo IA: riesgo alto' END
        ON CONFLICT (id_segmento, seudonimo_contribuyente) DO NOTHING;
    END IF;

    UPDATE analitica.segmento s
    SET tamano = (SELECT count(*) FROM analitica.asignacion_segmento a WHERE a.id_segmento = s.id_segmento)
    WHERE s.id_modelo = v_modelo;
END;
$demo$;
