-- Solo perfil local. Snapshot de los contribuyentes FICTICIOS conocidos, para probar HU011.
-- No es el orquestador ETL de HU025 ni ejecuta IA. No modifica deudas ni pagos de SAMNET.
-- Se ejecuta después de R__samnet_procedimientos. La clave HMAC es pública y SOLO sirve para estas semillas.
-- La foto se conserva en los reinicios; no se mezcla con las futuras cargas del ETL.
DO $demo$
DECLARE
    v_lote BIGINT;
    v_modelo BIGINT;
    v_gerencia BIGINT;
    v_corte DATE := DATE '2026-11-04';
    v_clave TEXT := 'CLAVE_PUBLICA_SOLO_SEMILLA_FICTICIA_HU011';
    v_registros INTEGER;
BEGIN
    IF EXISTS (SELECT 1 FROM analitica.lote_etl WHERE fuente = 'DEMO_HU011_SAMNET_SINTETICO') THEN
        RETURN;
    END IF;

    CREATE TEMP TABLE demo_hu011_contribuyentes ON COMMIT DROP AS
    SELECT c.* FROM samnet.ps_etl_contribuyentes() c
    WHERE (c.codigo, c.numero_documento) IN (
        ('014502', '41028765'), ('021390', '20456781239'), ('033218', '09215430'),
        ('045067', '20512309876'), ('058934', '45871203'), ('061177', '45871777'));

    CREATE TEMP TABLE demo_hu011_cobranza ON COMMIT DROP AS
    SELECT h.* FROM samnet.ps_etl_cobranza(v_corte) h
    JOIN demo_hu011_contribuyentes c ON c.codigo = h.contribuyente_codigo;
    SELECT count(*) INTO v_registros FROM demo_hu011_cobranza;
    IF v_registros = 0 THEN RETURN; END IF;

    INSERT INTO analitica.lote_etl (fuente, fecha_corte, tipo_ejecucion, estado, fecha_inicio,
            fecha_fin, registros_leidos, registros_aceptados, registros_rechazados)
    VALUES ('DEMO_HU011_SAMNET_SINTETICO', v_corte, 'A_DEMANDA', 'EN_CURSO', v_corte + TIME '08:00',
            NULL, v_registros, v_registros, 0)
    RETURNING id_lote INTO v_lote;

    INSERT INTO analitica.dim_contribuyente (seudonimo_contribuyente, tipo_persona, regimen, condicion)
    SELECT encode(public.hmac('CONTRIBUYENTE:' || c.codigo, v_clave, 'sha256'), 'hex'),
           c.tipo_persona, c.regimen, c.condicion
    FROM demo_hu011_contribuyentes c
    ON CONFLICT (seudonimo_contribuyente) DO NOTHING;

    INSERT INTO analitica.dim_predio (seudonimo_predio, zona, uso_predio, tipo_predio, base_imponible)
    SELECT DISTINCT encode(public.hmac('PREDIO:' || p.codigo_predio, v_clave, 'sha256'), 'hex'),
           COALESCE(p.zona, 'SIN_ZONA'), p.uso_predio, p.tipo_predio, p.base_imponible
    FROM samnet.ps_etl_predios() p
    WHERE p.codigo_predio IN (SELECT codigo_predio FROM demo_hu011_cobranza)
    ON CONFLICT (seudonimo_predio) DO NOTHING;

    -- El predial de la muestra no tiene predio asociado: se conserva SIN_ZONA, sin asignarle una zona inventada.
    INSERT INTO analitica.dim_predio (seudonimo_predio, zona, uso_predio, tipo_predio)
    SELECT DISTINCT encode(public.hmac('SIN_PREDIO:' || h.contribuyente_codigo, v_clave, 'sha256'), 'hex'),
           'SIN_ZONA', 'Obligacion sin predio asociado (demo)', 'OTRO'
    FROM demo_hu011_cobranza h WHERE h.codigo_predio IS NULL
    ON CONFLICT (seudonimo_predio) DO NOTHING;

    INSERT INTO analitica.hecho_cobranza (id_lote, seudonimo_contribuyente, id_predio, tributo,
            anio, cuota, fecha_vencimiento, monto_emitido, monto_insoluto, monto_pagado, monto_descuento, estado_pago)
    SELECT v_lote, c.seudonimo_contribuyente, p.id_predio, h.tributo, h.anio, h.cuota,
           h.fecha_vencimiento, h.monto_emitido, h.insoluto, h.monto_pagado,
           GREATEST(h.monto_emitido - h.monto_pagado - h.insoluto, 0),
           CASE WHEN h.insoluto = 0 THEN 'PAGADA'
                WHEN h.fecha_vencimiento < v_corte THEN 'VENCIDA' ELSE 'PENDIENTE' END
    FROM demo_hu011_cobranza h
    JOIN analitica.dim_contribuyente c ON c.seudonimo_contribuyente =
         encode(public.hmac('CONTRIBUYENTE:' || h.contribuyente_codigo, v_clave, 'sha256'), 'hex')
    JOIN analitica.dim_predio p ON p.seudonimo_predio = encode(public.hmac(
         CASE WHEN h.codigo_predio IS NULL THEN 'SIN_PREDIO:' || h.contribuyente_codigo
              ELSE 'PREDIO:' || h.codigo_predio END, v_clave, 'sha256'), 'hex');

    IF (SELECT count(*) FROM analitica.hecho_cobranza WHERE id_lote = v_lote) <> v_registros THEN
        RAISE EXCEPTION 'La muestra HU011 no puede publicarse: faltan dimensiones de cobranza.';
    END IF;

    SELECT id_usuario INTO v_gerencia FROM seguridad.usuario WHERE nombre_usuario = 'gerencia';
    INSERT INTO analitica.modelo_ia (nombre, tipo, version, hash_integridad, formato, fecha_registro)
    VALUES ('DEMO_HU011_NO_ES_MODELO_IA', 'SEGMENTACION', 'demo-1',
            encode(public.digest('Categorias fijas para demostrar HU011, sin inferencia IA', 'sha256'), 'hex'),
            'JSON_DEMO', v_corte + TIME '08:00')
    ON CONFLICT (nombre, version) DO NOTHING;
    SELECT id_modelo INTO v_modelo FROM analitica.modelo_ia WHERE nombre = 'DEMO_HU011_NO_ES_MODELO_IA' AND version = 'demo-1';

    INSERT INTO analitica.segmento (id_modelo, nombre, descripcion, tamano, variables_caracteristicas,
            estado, id_aprobador, fecha_aprobacion)
    SELECT v_modelo, s.nombre, 'Categoria de demostracion asignada por la semilla. No es resultado de IA.',
           0, '{"origen":"SEMILLA_DEMO","inferenciaIA":false}', 'PUBLICADO', v_gerencia, v_corte + TIME '08:00'
    FROM (VALUES ('Demo: al dia'), ('Demo: pago parcial'), ('Demo: deuda pendiente')) s(nombre)
    WHERE NOT EXISTS (SELECT 1 FROM analitica.segmento x WHERE x.id_modelo = v_modelo AND x.nombre = s.nombre);

    INSERT INTO analitica.asignacion_segmento (id_segmento, seudonimo_contribuyente, fecha_asignacion)
    SELECT s.id_segmento, t.seudonimo_contribuyente, v_corte + TIME '08:00'
    FROM (SELECT seudonimo_contribuyente, sum(monto_insoluto) AS deuda, sum(monto_pagado) AS pagado
          FROM analitica.hecho_cobranza WHERE id_lote = v_lote GROUP BY seudonimo_contribuyente) t
    JOIN analitica.segmento s ON s.id_modelo = v_modelo AND s.nombre =
         CASE WHEN t.deuda = 0 THEN 'Demo: al dia' WHEN t.pagado > 0 THEN 'Demo: pago parcial'
              ELSE 'Demo: deuda pendiente' END
    ON CONFLICT (id_segmento, seudonimo_contribuyente) DO NOTHING;
    UPDATE analitica.segmento s SET tamano =
           (SELECT count(*) FROM analitica.asignacion_segmento a WHERE a.id_segmento = s.id_segmento)
    WHERE s.id_modelo = v_modelo;

    INSERT INTO analitica.meta_indicador (indicador_codigo, anio, numero_periodo, valor_meta, observacion,
            fecha_registro, fecha_actualizacion)
    VALUES ('MONTO_RECAUDADO', 2026, 11, 12000, 'Meta de demostracion HU011', v_corte, v_corte),
           ('INDICE_MOROSIDAD', 2026, 11, 12.5, 'Meta de demostracion HU011', v_corte, v_corte)
    ON CONFLICT (indicador_codigo, anio, numero_periodo) DO NOTHING;

    UPDATE analitica.lote_etl SET estado = 'PUBLICADO', fecha_fin = v_corte + TIME '08:05' WHERE id_lote = v_lote;
END;
$demo$;
