-- ETL de Rentas por capas (RF-038 a RF-041, RF-053, RNF-018): SAMNET → bronce → plata (calidad y homologación) → oro.
-- Las tablas están en V39__etl_capas_bronce_plata.sql. La ejecuta EtlService (a demanda y programada) y, en
-- desarrollo, las semillas que antes escribían el modelo estrella a mano.
--
-- Contrato con el oro: escribe analitica.lote_etl (fuente ps_etl_cobranza), resultado_regla, dim_contribuyente,
-- dim_predio, hecho_cobranza, hecho_pago y maestros.entidad_maestra / correspondencia_origen con la misma forma y el
-- mismo seudónimo (HMAC-SHA-256 del código de SAMNET) que usaba la carga anterior. Las pantallas no cambian.
--
-- Reglas (analitica.regla_calidad):
--   R1  datos obligatorios vacíos o fuera de dominio (código, documento, tributo, cuota, montos, canal, zona, tipo)
--   R2  formato del documento: DNI 8 dígitos, RUC 11, CE u otro de 9 a 12 alfanuméricos
--   R3  integridad: la cuota, el pago o la titularidad apuntan a un registro rechazado o inexistente. Un predio sin
--       titular no es error: se publica marcado para revisión (OBSERVA)
--   R4  claves repetidas en el lote (se rechazan todas las copias: no se sabe cuál es la buena)
--   R5  fecha de corte posterior a la ejecución (rechaza el lote); cuota de un año o pago posteriores al corte
-- Un registro con error va a plata.cuarentena y no pasa al oro. El lote se rechaza (y no toca plata ni oro) si una
-- regla supera p_max_rechazo % de registros con error o si la fecha de corte es futura.
--
-- Homologación de contribuyentes y predios (cascada determinista, RF-039 y RF-040):
--   N0  la clave de SAMNET ya tiene correspondencia: misma entidad maestra
--   N1  misma clave de identidad (contribuyente: tipo y número de documento normalizados, y el RUC 10 lleva el DNI;
--       predio: código catastral): se une sola, con decisión AUTOMATICA
--   N2  solo contribuyentes: nombre parecido (similitud >= 0,6) y puntaje 0,5·nombre + 0,3·documento + 0,2·domicilio
--       >= p_umbral_revision (documento: 1 si difiere en una cifra, 0,75 si en dos). No se une sola:
--       queda con una entidad provisional (PENDIENTE_REVISION, con su propio seudónimo) y un caso PENDIENTE para el
--       Responsable de datos (HomologacionCasoService). Su decisión se aplica a los maestros al resolver y al oro en
--       el siguiente lote
--   N3  sin coincidencias: entidad nueva, con decisión AUTOMATICA

CREATE OR REPLACE FUNCTION etl.texto_comparable(p_texto TEXT)
RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT nullif(btrim(regexp_replace(regexp_replace(
               upper(translate(replace(coalesce(p_texto, ''), '.', ''),
                               'áéíóúüñàèìòùÁÉÍÓÚÜÑÀÈÌÒÙ', 'aeiouunaeiouAEIOUUNAEIOU')),
               '[^A-Z0-9 ]', ' ', 'g'), '\s+', ' ', 'g')), '');
$$;

-- Nombre con las palabras ordenadas: "GARCÍA TORRES, MARÍA" y "Maria Garcia Torres" quedan iguales.
CREATE OR REPLACE FUNCTION etl.nombre_comparable(p_nombre TEXT)
RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT string_agg(t, ' ' ORDER BY t)
    FROM unnest(string_to_array(etl.texto_comparable(p_nombre), ' ')) AS t
    WHERE t <> '';
$$;

CREATE OR REPLACE FUNCTION etl.documento_normalizado(p_documento TEXT)
RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT upper(regexp_replace(coalesce(p_documento, ''), '[\s.\-/]', '', 'g'));
$$;

-- Persona natural con RUC 10: el RUC es 10 + DNI + dígito verificador, así que es la misma persona que el DNI.
CREATE OR REPLACE FUNCTION etl.clave_identidad(p_tipo_documento TEXT, p_documento TEXT)
RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
               WHEN p_tipo_documento = 'RUC' AND p_documento ~ '^10\d{9}$' THEN 'DNI:' || substr(p_documento, 3, 8)
               ELSE coalesce(p_tipo_documento, '?') || ':' || p_documento
           END;
$$;

CREATE OR REPLACE FUNCTION etl.ejecutar_lote(
        p_fecha_corte DATE,
        p_tipo_ejecucion VARCHAR DEFAULT 'A_DEMANDA',
        p_clave_contribuyente TEXT DEFAULT NULL,
        p_clave_predio TEXT DEFAULT NULL,
        p_fecha_inicio TIMESTAMP DEFAULT NULL,
        p_max_rechazo NUMERIC DEFAULT 10,
        p_umbral_revision NUMERIC DEFAULT 0.65)
RETURNS BIGINT
LANGUAGE plpgsql
SET search_path = pg_catalog, public
-- Sin los avisos de DROP TABLE IF EXISTS de las tablas de trabajo.
SET client_min_messages = warning AS $$
DECLARE
    v_inicio      TIMESTAMP := coalesce(p_fecha_inicio, localtimestamp);
    v_reloj       TIMESTAMP := clock_timestamp();
    v_lote        BIGINT;
    v_filas       INTEGER;
    v_corte_futuro BOOLEAN;
    v_rechazar    BOOLEAN;
    v_leidos      INTEGER;
    v_rechazados  INTEGER;
    v_maestro     BIGINT;
    -- Mejor candidato de N2 y sus puntajes.
    v_candidato   BIGINT;
    v_s_nombre    NUMERIC;
    v_distancia   INTEGER;
    v_s_documento NUMERIC;
    v_s_domicilio NUMERIC;
    v_puntaje     NUMERIC;
    v_caso        BIGINT;
    v_fila        RECORD;
BEGIN
    IF p_fecha_corte IS NULL THEN
        RAISE EXCEPTION 'ETL: falta la fecha de corte' USING ERRCODE = '22023';
    END IF;
    IF p_tipo_ejecucion NOT IN ('PROGRAMADA', 'A_DEMANDA') THEN
        RAISE EXCEPTION 'ETL: tipo de ejecución no válido: %', p_tipo_ejecucion USING ERRCODE = '22023';
    END IF;
    IF coalesce(p_clave_contribuyente, '') = '' OR coalesce(p_clave_predio, '') = '' THEN
        RAISE EXCEPTION 'ETL: faltan las claves de seudonimización (RNF-018)' USING ERRCODE = '22023';
    END IF;

    -- Un lote a la vez: la ejecución programada y la de a demanda no se pisan.
    PERFORM pg_advisory_xact_lock(hashtext('etl.ejecutar_lote'));

    INSERT INTO analitica.lote_etl (fuente, fecha_corte, tipo_ejecucion, estado, fecha_inicio)
    VALUES ('ps_etl_cobranza', p_fecha_corte, p_tipo_ejecucion, 'EN_CURSO', v_inicio)
    RETURNING id_lote INTO v_lote;

    -- ===================== 1. Bronce: copia cruda de las cuatro extracciones =====================
    INSERT INTO bronce.contribuyente (id_lote, fila, codigo, nombre, tipo_persona, tipo_documento, numero_documento,
            domicilio_fiscal, estado, regimen, condicion, correo, telefonos, dom_departamento, dom_provincia,
            dom_distrito, dom_via, dom_numero, dom_interior, dom_manzana, dom_lote)
    SELECT v_lote, row_number() OVER (), c.codigo, c.nombre, c.tipo_persona, c.tipo_documento, c.numero_documento,
           c.domicilio_fiscal, c.estado, c.regimen, c.condicion, c.correo, c.telefonos, c.dom_departamento,
           c.dom_provincia, c.dom_distrito, c.dom_via, c.dom_numero, c.dom_interior, c.dom_manzana, c.dom_lote
    FROM samnet.ps_etl_contribuyentes() c;
    GET DIAGNOSTICS v_filas = ROW_COUNT;
    INSERT INTO bronce.extraccion VALUES (v_lote, 'ps_etl_contribuyentes', NULL, v_filas, v_inicio);

    INSERT INTO bronce.predio (id_lote, fila, codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio,
            area_terreno, base_imponible, fecha_adquisicion, fecha_baja, zona, contribuyente_codigo,
            condicion_propiedad, porcentaje_propiedad)
    SELECT v_lote, row_number() OVER (), p.codigo_predio, p.numero_catastral, p.ubicacion, p.uso_predio,
           p.tipo_predio, p.area_terreno, p.base_imponible, p.fecha_adquisicion, p.fecha_baja, p.zona,
           p.contribuyente_codigo, p.condicion_propiedad, p.porcentaje_propiedad
    FROM samnet.ps_etl_predios() p;
    GET DIAGNOSTICS v_filas = ROW_COUNT;
    INSERT INTO bronce.extraccion VALUES (v_lote, 'ps_etl_predios', NULL, v_filas, v_inicio);

    INSERT INTO bronce.cobranza (id_lote, fila, id_obligacion, contribuyente_codigo, codigo_predio, zona, tributo,
            anio, cuota, fecha_vencimiento, monto_emitido, monto_pagado, insoluto, porcentaje_descuento,
            etapa_cobranza, fecha_etapa, fecha_pago, canal_pago)
    SELECT v_lote, row_number() OVER (), o.id_obligacion, o.contribuyente_codigo, o.codigo_predio, o.zona,
           o.tributo, o.anio, o.cuota, o.fecha_vencimiento, o.monto_emitido, o.monto_pagado, o.insoluto,
           o.porcentaje_descuento, o.etapa_cobranza, o.fecha_etapa, o.fecha_pago, o.canal_pago
    FROM samnet.ps_etl_cobranza(p_fecha_corte) o;
    GET DIAGNOSTICS v_filas = ROW_COUNT;
    INSERT INTO bronce.extraccion VALUES (v_lote, 'ps_etl_cobranza', p_fecha_corte, v_filas, v_inicio);

    INSERT INTO bronce.pago (id_lote, fila, id_pago, contribuyente_codigo, fecha_pago, canal, monto_pagado,
            monto_correspondiente, beneficio_aplicado)
    SELECT v_lote, row_number() OVER (), p.id_pago, p.contribuyente_codigo, p.fecha_pago, p.canal, p.monto_pagado,
           p.monto_correspondiente, p.beneficio_aplicado
    FROM samnet.ps_etl_pagos(p_fecha_corte) p;
    GET DIAGNOSTICS v_filas = ROW_COUNT;
    INSERT INTO bronce.extraccion VALUES (v_lote, 'ps_etl_pagos', p_fecha_corte, v_filas, v_inicio);

    -- ===================== 2. Plata: normalización y reglas de calidad =====================
    -- Las semillas llaman a esta función varias veces en una misma transacción: las tablas de trabajo se recrean.
    DROP TABLE IF EXISTS etl_c, etl_p, etl_q, etl_pg, etl_err, etl_regla, etl_titular, etl_predio,
                         etl_predio_tecnico;

    CREATE TEMP TABLE etl_err (
        tabla   TEXT NOT NULL,   -- CONTRIBUYENTE, PREDIO, CUOTA o PAGO: la extracción de la fila
        entidad TEXT NOT NULL,   -- lo que se rechaza; TITULAR es una fila de predio
        fila    INTEGER NOT NULL,
        clave   TEXT,
        regla   TEXT NOT NULL,
        motivo  TEXT NOT NULL
    ) ON COMMIT DROP;

    CREATE TEMP TABLE etl_c ON COMMIT DROP AS
    SELECT b.fila, b.codigo, b.tipo_persona, b.tipo_documento,
           etl.documento_normalizado(b.numero_documento) AS documento,
           b.nombre, etl.nombre_comparable(b.nombre) AS nombre_cmp,
           b.domicilio_fiscal, etl.texto_comparable(b.domicilio_fiscal) AS dom_cmp,
           b.estado, b.regimen, b.condicion
    FROM bronce.contribuyente b
    WHERE b.id_lote = v_lote;

    INSERT INTO etl_err
    SELECT 'CONTRIBUYENTE', 'CONTRIBUYENTE', fila, codigo, 'R1',
           CASE WHEN coalesce(btrim(codigo), '') = '' THEN 'Código del contribuyente vacío'
                WHEN documento = '' THEN 'Documento de identidad vacío'
                WHEN coalesce(btrim(nombre), '') = '' THEN 'Nombre vacío'
                ELSE 'Tipo de persona fuera de dominio: ' || coalesce(tipo_persona, 'vacío') END
    FROM etl_c
    WHERE coalesce(btrim(codigo), '') = '' OR documento = '' OR coalesce(btrim(nombre), '') = ''
       OR tipo_persona IS NULL OR tipo_persona NOT IN ('NATURAL', 'JURIDICA');

    INSERT INTO etl_err
    SELECT 'CONTRIBUYENTE', 'CONTRIBUYENTE', fila, codigo, 'R2',
           CASE coalesce(tipo_documento, '')
               WHEN 'DNI' THEN 'El DNI debe tener 8 dígitos'
               WHEN 'RUC' THEN 'El RUC debe tener 11 dígitos'
               WHEN '' THEN 'Tipo de documento vacío'
               ELSE 'El ' || tipo_documento || ' debe tener de 9 a 12 caracteres alfanuméricos' END
    FROM etl_c
    WHERE documento <> ''
      AND CASE coalesce(tipo_documento, '')
              WHEN 'DNI' THEN documento !~ '^\d{8}$'
              WHEN 'RUC' THEN documento !~ '^\d{11}$'
              WHEN '' THEN TRUE
              ELSE documento !~ '^[A-Z0-9]{9,12}$' END;

    INSERT INTO etl_err
    SELECT 'CONTRIBUYENTE', 'CONTRIBUYENTE', fila, codigo, 'R4', 'Código de contribuyente repetido en el lote'
    FROM (SELECT fila, codigo, count(*) OVER (PARTITION BY codigo) AS veces FROM etl_c WHERE codigo IS NOT NULL) x
    WHERE veces > 1;

    DELETE FROM etl_c c USING etl_err e WHERE e.tabla = 'CONTRIBUYENTE' AND e.fila = c.fila;
    ALTER TABLE etl_c ADD PRIMARY KEY (codigo);

    -- Predios: una fila por titular.
    CREATE TEMP TABLE etl_p ON COMMIT DROP AS
    SELECT b.fila, b.codigo_predio, b.numero_catastral, etl.documento_normalizado(b.numero_catastral) AS cuc,
           b.ubicacion, b.uso_predio, b.tipo_predio, b.area_terreno, b.base_imponible, b.zona,
           b.contribuyente_codigo, b.condicion_propiedad, b.porcentaje_propiedad
    FROM bronce.predio b
    WHERE b.id_lote = v_lote;

    INSERT INTO etl_err
    SELECT 'PREDIO', 'PREDIO', fila, codigo_predio, 'R1',
           CASE WHEN coalesce(btrim(codigo_predio), '') = '' THEN 'Código del predio vacío'
                WHEN coalesce(btrim(zona), '') = '' THEN 'Zona del predio vacía'
                ELSE 'Tipo de predio fuera de dominio: ' || coalesce(tipo_predio, 'vacío') END
    FROM etl_p
    WHERE coalesce(btrim(codigo_predio), '') = '' OR coalesce(btrim(zona), '') = ''
       OR tipo_predio IS NULL OR tipo_predio NOT IN ('URBANO', 'RUSTICO', 'OTRO')
       OR base_imponible < 0;

    -- Un predio rechazado por R1 en una fila se rechaza en todas.
    INSERT INTO etl_err
    SELECT 'PREDIO', 'PREDIO', p.fila, p.codigo_predio, 'R1', 'Predio con datos obligatorios vacíos en otra fila'
    FROM etl_p p
    WHERE p.codigo_predio IN (SELECT clave FROM etl_err WHERE tabla = 'PREDIO' AND regla = 'R1')
      AND p.fila NOT IN (SELECT fila FROM etl_err WHERE tabla = 'PREDIO');

    INSERT INTO etl_err
    SELECT 'PREDIO', 'TITULAR', fila, codigo_predio || '/' || contribuyente_codigo, 'R4',
           'Titularidad repetida en el lote'
    FROM (SELECT fila, codigo_predio, contribuyente_codigo,
                 count(*) OVER (PARTITION BY codigo_predio, contribuyente_codigo) AS veces
          FROM etl_p WHERE contribuyente_codigo IS NOT NULL) x
    WHERE veces > 1;

    INSERT INTO etl_err
    SELECT 'PREDIO', 'TITULAR', p.fila, p.codigo_predio || '/' || p.contribuyente_codigo, 'R3',
           'El titular fue rechazado o no está en el lote'
    FROM etl_p p
    WHERE p.contribuyente_codigo IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM etl_c c WHERE c.codigo = p.contribuyente_codigo)
      AND NOT EXISTS (SELECT 1 FROM etl_err e WHERE e.tabla = 'PREDIO' AND e.fila = p.fila);

    -- Predios aceptados (sin fila con R1) y sus titulares aceptados.
    CREATE TEMP TABLE etl_titular ON COMMIT DROP AS
    SELECT p.codigo_predio, p.contribuyente_codigo, p.condicion_propiedad, p.porcentaje_propiedad
    FROM etl_p p
    WHERE p.contribuyente_codigo IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM etl_err e WHERE e.tabla = 'PREDIO' AND e.fila = p.fila);

    DELETE FROM etl_p p USING etl_err e WHERE e.tabla = 'PREDIO' AND e.regla = 'R1' AND e.fila = p.fila;
    CREATE TEMP TABLE etl_predio ON COMMIT DROP AS
    SELECT DISTINCT ON (p.codigo_predio) p.codigo_predio, p.numero_catastral, p.cuc, p.ubicacion, p.uso_predio,
           p.tipo_predio, p.area_terreno, p.base_imponible, p.zona,
           NOT EXISTS (SELECT 1 FROM etl_titular t WHERE t.codigo_predio = p.codigo_predio) AS marcado_revision
    FROM etl_p p
    ORDER BY p.codigo_predio, p.fila;
    ALTER TABLE etl_predio ADD PRIMARY KEY (codigo_predio);

    -- Predio con que se publica el Impuesto Predial: el primero del contribuyente, como en la carga anterior.
    CREATE TEMP TABLE etl_predio_tecnico ON COMMIT DROP AS
    SELECT DISTINCT ON (t.contribuyente_codigo) t.contribuyente_codigo, t.codigo_predio
    FROM etl_titular t
    ORDER BY t.contribuyente_codigo, t.codigo_predio;

    -- Cuotas.
    CREATE TEMP TABLE etl_q ON COMMIT DROP AS
    SELECT b.fila, b.id_obligacion, b.contribuyente_codigo, b.codigo_predio, b.tributo, b.anio, b.cuota,
           b.fecha_vencimiento, b.monto_emitido, b.monto_pagado, b.insoluto, b.fecha_pago, b.canal_pago,
           coalesce(b.codigo_predio, pt.codigo_predio) AS codigo_predio_hecho
    FROM bronce.cobranza b
    LEFT JOIN etl_predio_tecnico pt ON pt.contribuyente_codigo = b.contribuyente_codigo
    WHERE b.id_lote = v_lote;

    INSERT INTO etl_err
    SELECT 'CUOTA', 'CUOTA', fila, id_obligacion::TEXT, 'R1',
           CASE WHEN id_obligacion IS NULL OR contribuyente_codigo IS NULL THEN 'Cuota sin identificador o contribuyente'
                WHEN tributo IS NULL OR tributo NOT IN ('IMPUESTO_PREDIAL', 'ARBITRIOS')
                    THEN 'Tributo fuera de dominio: ' || coalesce(tributo, 'vacío')
                WHEN cuota IS NULL OR cuota NOT BETWEEN 1 AND 12 THEN 'Número de cuota fuera de 1 a 12'
                WHEN canal_pago IS NOT NULL AND canal_pago NOT IN ('VENTANILLA', 'EN_LINEA')
                    THEN 'Canal de pago fuera de dominio: ' || canal_pago
                ELSE 'Año, vencimiento o montos vacíos o negativos' END
    FROM etl_q
    WHERE id_obligacion IS NULL OR contribuyente_codigo IS NULL OR anio IS NULL OR fecha_vencimiento IS NULL
       OR tributo IS NULL OR tributo NOT IN ('IMPUESTO_PREDIAL', 'ARBITRIOS')
       OR cuota IS NULL OR cuota NOT BETWEEN 1 AND 12
       OR coalesce(monto_emitido, -1) < 0 OR coalesce(monto_pagado, -1) < 0 OR coalesce(insoluto, -1) < 0
       OR monto_pagado + insoluto > monto_emitido
       OR (canal_pago IS NOT NULL AND canal_pago NOT IN ('VENTANILLA', 'EN_LINEA'));

    INSERT INTO etl_err
    SELECT 'CUOTA', 'CUOTA', fila, id_obligacion::TEXT, 'R4', 'Obligación repetida en el lote'
    FROM (SELECT fila, id_obligacion, count(*) OVER (PARTITION BY id_obligacion) AS veces
          FROM etl_q WHERE id_obligacion IS NOT NULL) x
    WHERE veces > 1;

    INSERT INTO etl_err
    SELECT 'CUOTA', 'CUOTA', fila, id_obligacion::TEXT, 'R5', 'Cuota de un año posterior a la fecha de corte'
    FROM etl_q
    WHERE anio > EXTRACT(YEAR FROM p_fecha_corte);

    INSERT INTO etl_err
    SELECT 'CUOTA', 'CUOTA', q.fila, q.id_obligacion::TEXT, 'R3',
           CASE WHEN NOT EXISTS (SELECT 1 FROM etl_c c WHERE c.codigo = q.contribuyente_codigo)
                    THEN 'El contribuyente fue rechazado o no está en el lote'
                WHEN q.codigo_predio IS NOT NULL THEN 'El predio fue rechazado o no está en el lote'
                ELSE 'Impuesto Predial sin predio del contribuyente en el lote' END
    FROM etl_q q
    WHERE q.contribuyente_codigo IS NOT NULL
      AND (NOT EXISTS (SELECT 1 FROM etl_c c WHERE c.codigo = q.contribuyente_codigo)
           OR q.codigo_predio_hecho IS NULL
           OR NOT EXISTS (SELECT 1 FROM etl_predio p WHERE p.codigo_predio = q.codigo_predio_hecho));

    DELETE FROM etl_q q USING etl_err e WHERE e.tabla = 'CUOTA' AND e.fila = q.fila;

    -- Pagos.
    CREATE TEMP TABLE etl_pg ON COMMIT DROP AS
    SELECT b.fila, b.id_pago, b.contribuyente_codigo, b.fecha_pago, b.canal, b.monto_pagado,
           b.monto_correspondiente, b.beneficio_aplicado
    FROM bronce.pago b
    WHERE b.id_lote = v_lote;

    INSERT INTO etl_err
    SELECT 'PAGO', 'PAGO', fila, id_pago, 'R1',
           CASE WHEN id_pago IS NULL OR contribuyente_codigo IS NULL OR fecha_pago IS NULL
                    THEN 'Pago sin identificador, contribuyente o fecha'
                WHEN canal IS NULL OR canal NOT IN ('VENTANILLA', 'EN_LINEA')
                    THEN 'Canal fuera de dominio: ' || coalesce(canal, 'vacío')
                ELSE 'Montos del pago vacíos o fuera de rango' END
    FROM etl_pg
    WHERE id_pago IS NULL OR contribuyente_codigo IS NULL OR fecha_pago IS NULL
       OR canal IS NULL OR canal NOT IN ('VENTANILLA', 'EN_LINEA')
       OR coalesce(monto_pagado, 0) <= 0 OR coalesce(monto_correspondiente, -1) < 0;

    INSERT INTO etl_err
    SELECT 'PAGO', 'PAGO', fila, id_pago, 'R4', 'Pago repetido en el lote'
    FROM (SELECT fila, id_pago, count(*) OVER (PARTITION BY id_pago) AS veces
          FROM etl_pg WHERE id_pago IS NOT NULL) x
    WHERE veces > 1;

    INSERT INTO etl_err
    SELECT 'PAGO', 'PAGO', fila, id_pago, 'R5', 'Pago posterior a la fecha de corte'
    FROM etl_pg
    WHERE fecha_pago > p_fecha_corte;

    INSERT INTO etl_err
    SELECT 'PAGO', 'PAGO', p.fila, p.id_pago, 'R3', 'El contribuyente fue rechazado o no está en el lote'
    FROM etl_pg p
    WHERE p.contribuyente_codigo IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM etl_c c WHERE c.codigo = p.contribuyente_codigo);

    DELETE FROM etl_pg p USING etl_err e WHERE e.tabla = 'PAGO' AND e.fila = p.fila;

    -- Resultado por regla.
    v_corte_futuro := p_fecha_corte > v_inicio::DATE;

    CREATE TEMP TABLE etl_regla ON COMMIT DROP AS
    WITH filas AS (
        SELECT 'CONTRIBUYENTE' AS tabla, 'contribuyentes' AS etiqueta, 1 AS orden,
               (SELECT count(*) FROM bronce.contribuyente WHERE id_lote = v_lote) AS total
        UNION ALL SELECT 'PREDIO', 'filas de predios', 2, (SELECT count(*) FROM bronce.predio WHERE id_lote = v_lote)
        UNION ALL SELECT 'CUOTA', 'cuotas', 3, (SELECT count(*) FROM bronce.cobranza WHERE id_lote = v_lote)
        UNION ALL SELECT 'PAGO', 'pagos', 4, (SELECT count(*) FROM bronce.pago WHERE id_lote = v_lote)
    ),
    aplica (codigo, tabla) AS (
        VALUES ('R1', 'CONTRIBUYENTE'), ('R1', 'PREDIO'), ('R1', 'CUOTA'), ('R1', 'PAGO'),
               ('R2', 'CONTRIBUYENTE'),
               ('R3', 'PREDIO'), ('R3', 'CUOTA'), ('R3', 'PAGO'),
               ('R4', 'CONTRIBUYENTE'), ('R4', 'PREDIO'), ('R4', 'CUOTA'), ('R4', 'PAGO'),
               ('R5', 'CUOTA'), ('R5', 'PAGO')
    ),
    errores AS (
        SELECT regla, tabla, count(DISTINCT fila) AS n FROM etl_err GROUP BY regla, tabla
    ),
    por_tabla AS (
        SELECT a.codigo, f.orden, f.etiqueta, f.total, coalesce(e.n, 0) AS errores
        FROM aplica a
        JOIN filas f ON f.tabla = a.tabla
        LEFT JOIN errores e ON e.regla = a.codigo AND e.tabla = a.tabla
    )
    SELECT codigo,
           sum(total)::INTEGER AS evaluados,
           sum(errores)::INTEGER AS con_error,
           left('Con error: ' || string_agg(errores || ' de ' || total || ' ' || etiqueta, '; ' ORDER BY orden)
                || CASE WHEN codigo = 'R3' THEN (SELECT CASE WHEN count(*) > 0
                            THEN '. Marcados para revisión: ' || count(*) || ' predio(s) sin titular' ELSE '' END
                        FROM etl_predio WHERE marcado_revision) ELSE '' END
                || CASE WHEN codigo = 'R5' AND v_corte_futuro
                        THEN '. La fecha de corte ' || p_fecha_corte || ' es posterior a la ejecución' ELSE '' END
                || '.', 400) AS detalle
    FROM por_tabla
    GROUP BY codigo;

    ALTER TABLE etl_regla ADD COLUMN resultado TEXT;
    UPDATE etl_regla
    SET resultado = CASE
        WHEN (codigo = 'R5' AND v_corte_futuro) OR (evaluados > 0 AND con_error * 100.0 > p_max_rechazo * evaluados)
            THEN 'FALLA'
        WHEN con_error > 0 OR (codigo = 'R3' AND EXISTS (SELECT 1 FROM etl_predio WHERE marcado_revision))
            THEN 'OBSERVA'
        ELSE 'CUMPLE' END;

    INSERT INTO analitica.resultado_regla (id_lote, id_regla, registros_evaluados, registros_con_error, resultado, detalle)
    SELECT v_lote, rc.id_regla, rg.evaluados, rg.con_error, rg.resultado, rg.detalle
    FROM etl_regla rg
    JOIN analitica.regla_calidad rc ON rc.codigo = rg.codigo AND rc.activa;

    INSERT INTO plata.cuarentena (id_lote, entidad, clave_origen, regla, motivo, registro)
    SELECT v_lote, e.entidad, left(e.clave, 60), e.regla, left(e.motivo, 250),
           CASE e.tabla
               WHEN 'CONTRIBUYENTE' THEN (SELECT to_jsonb(b) - 'id_lote' FROM bronce.contribuyente b
                                          WHERE b.id_lote = v_lote AND b.fila = e.fila)
               WHEN 'PREDIO' THEN (SELECT to_jsonb(b) - 'id_lote' FROM bronce.predio b
                                   WHERE b.id_lote = v_lote AND b.fila = e.fila)
               WHEN 'CUOTA' THEN (SELECT to_jsonb(b) - 'id_lote' FROM bronce.cobranza b
                                  WHERE b.id_lote = v_lote AND b.fila = e.fila)
               ELSE (SELECT to_jsonb(b) - 'id_lote' FROM bronce.pago b WHERE b.id_lote = v_lote AND b.fila = e.fila)
           END
    FROM etl_err e;

    SELECT (SELECT sum(filas) FROM bronce.extraccion WHERE id_lote = v_lote)::INTEGER,
           (SELECT count(*) FROM (SELECT DISTINCT tabla, fila FROM etl_err) d)::INTEGER
    INTO v_leidos, v_rechazados;

    v_rechazar := EXISTS (SELECT 1 FROM etl_regla WHERE resultado = 'FALLA');
    IF v_rechazar THEN
        -- Un lote rechazado no toca plata ni oro (RF-041): queda el bronce y la cuarentena para revisarlo.
        UPDATE analitica.lote_etl
        SET estado = 'RECHAZADO', fecha_fin = v_inicio + (clock_timestamp() - v_reloj),
            registros_leidos = v_leidos, registros_aceptados = v_leidos - v_rechazados,
            registros_rechazados = v_rechazados
        WHERE id_lote = v_lote;
        DROP TABLE IF EXISTS etl_c, etl_p, etl_q, etl_pg, etl_err, etl_regla, etl_titular, etl_predio,
                             etl_predio_tecnico;
        RETURN v_lote;
    END IF;

    -- ===================== 3. Homologación de contribuyentes =====================
    ALTER TABLE etl_c ADD COLUMN clave_identidad TEXT, ADD COLUMN id_maestro BIGINT, ADD COLUMN nivel TEXT;
    UPDATE etl_c SET clave_identidad = etl.clave_identidad(tipo_documento, documento);

    -- N0: correspondencia previa.
    UPDATE etl_c c
    SET id_maestro = co.id_maestro, nivel = 'N0_CORRESPONDENCIA_PREVIA'
    FROM maestros.correspondencia_origen co
    JOIN maestros.entidad_maestra e ON e.id_maestro = co.id_maestro AND e.tipo_entidad = 'CONTRIBUYENTE'
    WHERE co.sistema_origen = 'SAMNET' AND co.clave_origen = c.codigo;

    FOR v_fila IN SELECT * FROM etl_c WHERE id_maestro IS NULL ORDER BY codigo LOOP
        -- N1: misma clave de identidad que un registro ya homologado.
        SELECT x.id_maestro INTO v_maestro
        FROM etl_c x
        WHERE x.id_maestro IS NOT NULL AND x.clave_identidad = v_fila.clave_identidad
        ORDER BY x.codigo
        LIMIT 1;

        IF v_maestro IS NOT NULL THEN
            INSERT INTO maestros.correspondencia_origen (id_maestro, sistema_origen, clave_origen, fecha_vinculo)
            VALUES (v_maestro, 'SAMNET', v_fila.codigo, v_inicio);
            INSERT INTO analitica.decision_homologacion (id_maestro, nivel_resolutor, puntaje, atributos_coincidentes,
                    tipo_decision, fecha_hora)
            VALUES (v_maestro, 'N1_EXACTA_NORMALIZADA', 1,
                    CASE WHEN v_fila.tipo_documento = 'RUC' AND v_fila.clave_identidad LIKE 'DNI:%'
                         THEN 'tipo_documento,numero_documento (RUC 10 con el DNI)'
                         ELSE 'tipo_documento,numero_documento' END,
                    'AUTOMATICA', v_inicio);
            UPDATE etl_c SET id_maestro = v_maestro, nivel = 'N1_EXACTA_NORMALIZADA' WHERE codigo = v_fila.codigo;
            CONTINUE;
        END IF;

        -- N2: el registro activo más parecido del mismo tipo de persona.
        v_candidato := NULL; v_s_nombre := NULL; v_distancia := NULL; v_s_documento := NULL;
        v_s_domicilio := NULL; v_puntaje := NULL;
        SELECT x.id_maestro, s.nombre, s.distancia, s.documento, s.domicilio,
               round(0.5 * s.nombre + 0.3 * s.documento + 0.2 * s.domicilio, 4) AS puntaje
        INTO v_candidato, v_s_nombre, v_distancia, v_s_documento, v_s_domicilio, v_puntaje
        FROM etl_c x
        JOIN maestros.entidad_maestra e ON e.id_maestro = x.id_maestro AND e.estado = 'ACTIVA'
        CROSS JOIN LATERAL (
            SELECT round(public.similarity(x.nombre_cmp, v_fila.nombre_cmp)::NUMERIC, 4) AS nombre,
                   CASE WHEN x.tipo_documento = v_fila.tipo_documento
                        THEN public.levenshtein(x.documento, v_fila.documento) END AS distancia,
                   CASE WHEN x.dom_cmp IS NULL OR v_fila.dom_cmp IS NULL THEN 0
                        ELSE round(public.similarity(x.dom_cmp, v_fila.dom_cmp)::NUMERIC, 4) END AS domicilio
        ) d
        CROSS JOIN LATERAL (
            SELECT d.nombre, d.distancia, d.domicilio,
                   CASE d.distancia WHEN 1 THEN 1 WHEN 2 THEN 0.75 ELSE 0 END AS documento
        ) s
        WHERE x.id_maestro IS NOT NULL AND x.tipo_persona = v_fila.tipo_persona AND x.codigo <> v_fila.codigo
          -- Sin un nombre parecido no hay caso: un DNI vecino en el mismo domicilio suele ser otra persona.
          AND d.nombre >= 0.6
        ORDER BY puntaje DESC, x.codigo
        LIMIT 1;

        INSERT INTO maestros.entidad_maestra (tipo_entidad, seudonimo, estado)
        VALUES ('CONTRIBUYENTE', encode(public.hmac(v_fila.codigo, p_clave_contribuyente, 'sha256'), 'hex'),
                CASE WHEN v_puntaje >= p_umbral_revision THEN 'PENDIENTE_REVISION' ELSE 'ACTIVA' END)
        ON CONFLICT (seudonimo) DO UPDATE SET estado = EXCLUDED.estado
        RETURNING id_maestro INTO v_maestro;
        INSERT INTO maestros.correspondencia_origen (id_maestro, sistema_origen, clave_origen, fecha_vinculo)
        VALUES (v_maestro, 'SAMNET', v_fila.codigo, v_inicio);

        IF v_puntaje >= p_umbral_revision THEN
            -- Solo puntajes y distancias: el caso no guarda nombres ni documentos (RNF-018).
            INSERT INTO analitica.caso_homologacion (id_lote, id_maestro_candidato, id_maestro_provisional,
                    nivel_alcanzado, puntaje, atributos_comparados, estado, fecha_creacion)
            VALUES (v_lote, v_candidato, v_maestro, 'N2_SIMILITUD', v_puntaje,
                    jsonb_build_object(
                        'entidad', 'CONTRIBUYENTE',
                        'nombre', jsonb_build_object('similitud', v_s_nombre, 'peso', 0.5),
                        'documento', jsonb_build_object('distancia', v_distancia,
                                                        'puntaje', v_s_documento, 'peso', 0.3),
                        'domicilio', jsonb_build_object('similitud', v_s_domicilio, 'peso', 0.2),
                        'umbralRevision', p_umbral_revision)::TEXT,
                    'PENDIENTE', v_inicio)
            RETURNING id_caso INTO v_caso;
            UPDATE etl_c SET id_maestro = v_maestro, nivel = 'N2_SIMILITUD' WHERE codigo = v_fila.codigo;
        ELSE
            INSERT INTO analitica.decision_homologacion (id_maestro, nivel_resolutor, puntaje, atributos_coincidentes,
                    tipo_decision, fecha_hora)
            VALUES (v_maestro, 'N3_ALTA_NUEVA', v_puntaje, 'sin coincidencias', 'AUTOMATICA', v_inicio);
            UPDATE etl_c SET id_maestro = v_maestro, nivel = 'N3_ALTA_NUEVA' WHERE codigo = v_fila.codigo;
        END IF;
    END LOOP;

    -- ===================== 4. Homologación de predios (N0, N1 por código catastral, N3) =====================
    ALTER TABLE etl_predio ADD COLUMN id_maestro BIGINT, ADD COLUMN nivel TEXT;

    UPDATE etl_predio p
    SET id_maestro = co.id_maestro, nivel = 'N0_CORRESPONDENCIA_PREVIA'
    FROM maestros.correspondencia_origen co
    JOIN maestros.entidad_maestra e ON e.id_maestro = co.id_maestro AND e.tipo_entidad = 'PREDIO'
    WHERE co.sistema_origen = 'SAMNET' AND co.clave_origen = p.codigo_predio;

    FOR v_fila IN SELECT * FROM etl_predio WHERE id_maestro IS NULL ORDER BY codigo_predio LOOP
        SELECT x.id_maestro INTO v_maestro
        FROM etl_predio x
        WHERE x.id_maestro IS NOT NULL AND v_fila.cuc <> '' AND x.cuc = v_fila.cuc
        ORDER BY x.codigo_predio
        LIMIT 1;

        IF v_maestro IS NOT NULL THEN
            INSERT INTO maestros.correspondencia_origen (id_maestro, sistema_origen, clave_origen, fecha_vinculo)
            VALUES (v_maestro, 'SAMNET', v_fila.codigo_predio, v_inicio);
            INSERT INTO analitica.decision_homologacion (id_maestro, nivel_resolutor, puntaje, atributos_coincidentes,
                    tipo_decision, fecha_hora)
            VALUES (v_maestro, 'N1_EXACTA_NORMALIZADA', 1, 'numero_catastral', 'AUTOMATICA', v_inicio);
            UPDATE etl_predio SET id_maestro = v_maestro, nivel = 'N1_EXACTA_NORMALIZADA'
            WHERE codigo_predio = v_fila.codigo_predio;
        ELSE
            INSERT INTO maestros.entidad_maestra (tipo_entidad, seudonimo, estado)
            VALUES ('PREDIO', encode(public.hmac(v_fila.codigo_predio, p_clave_predio, 'sha256'), 'hex'), 'ACTIVA')
            ON CONFLICT (seudonimo) DO UPDATE SET estado = EXCLUDED.estado
            RETURNING id_maestro INTO v_maestro;
            INSERT INTO maestros.correspondencia_origen (id_maestro, sistema_origen, clave_origen, fecha_vinculo)
            VALUES (v_maestro, 'SAMNET', v_fila.codigo_predio, v_inicio);
            INSERT INTO analitica.decision_homologacion (id_maestro, nivel_resolutor, atributos_coincidentes,
                    tipo_decision, fecha_hora)
            VALUES (v_maestro, 'N3_ALTA_NUEVA', 'sin coincidencias', 'AUTOMATICA', v_inicio);
            UPDATE etl_predio SET id_maestro = v_maestro, nivel = 'N3_ALTA_NUEVA' WHERE codigo_predio = v_fila.codigo_predio;
        END IF;
    END LOOP;

    -- R3: el predio sin titular queda pendiente de revisión hasta que SAMNET le asigne uno.
    UPDATE maestros.entidad_maestra e
    SET estado = CASE WHEN p.marcado_revision THEN 'PENDIENTE_REVISION' ELSE 'ACTIVA' END
    FROM etl_predio p
    WHERE e.id_maestro = p.id_maestro AND e.estado <> 'FUSIONADA'
      AND e.estado <> CASE WHEN p.marcado_revision THEN 'PENDIENTE_REVISION' ELSE 'ACTIVA' END;

    -- ===================== 5. Plata vigente =====================
    -- TRUNCATE y no DELETE: vacía las cinco de una vez sin revisar las claves foráneas fila por fila.
    TRUNCATE plata.cuota, plata.pago, plata.predio_titular, plata.predio, plata.contribuyente;

    INSERT INTO plata.contribuyente (codigo, id_lote, tipo_persona, tipo_documento, numero_documento, clave_identidad,
            nombre, nombre_comparable, domicilio_fiscal, domicilio_comparable, estado, regimen, condicion, id_maestro,
            nivel_homologacion)
    SELECT codigo, v_lote, tipo_persona, tipo_documento, documento, clave_identidad, nombre, nombre_cmp,
           domicilio_fiscal, dom_cmp, estado, regimen, condicion, id_maestro, nivel
    FROM etl_c;

    INSERT INTO plata.predio (codigo_predio, id_lote, numero_catastral, ubicacion, uso_predio, tipo_predio,
            area_terreno, base_imponible, zona, marcado_revision, id_maestro, nivel_homologacion)
    SELECT codigo_predio, v_lote, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno, base_imponible,
           zona, marcado_revision, id_maestro, nivel
    FROM etl_predio;

    INSERT INTO plata.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad, porcentaje_propiedad)
    SELECT t.codigo_predio, t.contribuyente_codigo, t.condicion_propiedad, t.porcentaje_propiedad
    FROM etl_titular t
    WHERE EXISTS (SELECT 1 FROM etl_predio p WHERE p.codigo_predio = t.codigo_predio);

    INSERT INTO plata.cuota (id_obligacion, id_lote, contribuyente_codigo, codigo_predio, codigo_predio_hecho, tributo,
            anio, cuota, fecha_vencimiento, monto_emitido, monto_pagado, monto_insoluto, monto_descuento, estado_pago,
            fecha_pago, canal_pago)
    SELECT q.id_obligacion, v_lote, q.contribuyente_codigo, q.codigo_predio, q.codigo_predio_hecho, q.tributo, q.anio,
           q.cuota, q.fecha_vencimiento, q.monto_emitido, q.monto_pagado, q.insoluto,
           q.monto_emitido - q.monto_pagado - q.insoluto,
           CASE WHEN q.insoluto <= 0 THEN 'PAGADA'
                WHEN q.fecha_vencimiento < p_fecha_corte THEN 'VENCIDA'
                ELSE 'PENDIENTE' END,
           q.fecha_pago, q.canal_pago
    FROM etl_q q;

    INSERT INTO plata.pago (id_pago, id_lote, contribuyente_codigo, fecha_pago, canal, monto_pagado,
            monto_correspondiente, beneficio_aplicado)
    SELECT id_pago, v_lote, contribuyente_codigo, fecha_pago, canal, monto_pagado, monto_correspondiente,
           beneficio_aplicado
    FROM etl_pg;

    -- ===================== 6. Oro: paso directo al modelo estrella =====================
    -- Atributos de la entidad maestra: los del registro que la originó (su seudónimo sale de ese código).
    INSERT INTO analitica.dim_contribuyente (seudonimo_contribuyente, tipo_persona, regimen, condicion)
    SELECT DISTINCT ON (c.id_maestro) e.seudonimo, c.tipo_persona, c.regimen, c.condicion
    FROM plata.contribuyente c
    JOIN maestros.entidad_maestra e ON e.id_maestro = c.id_maestro
    ORDER BY c.id_maestro, (encode(public.hmac(c.codigo, p_clave_contribuyente, 'sha256'), 'hex') = e.seudonimo) DESC,
             c.codigo
    ON CONFLICT (seudonimo_contribuyente) DO UPDATE
    SET tipo_persona = EXCLUDED.tipo_persona, regimen = EXCLUDED.regimen, condicion = EXCLUDED.condicion;

    INSERT INTO analitica.dim_predio (seudonimo_predio, zona, uso_predio, tipo_predio, base_imponible)
    SELECT DISTINCT ON (p.id_maestro) e.seudonimo, p.zona, p.uso_predio, p.tipo_predio, p.base_imponible
    FROM plata.predio p
    JOIN maestros.entidad_maestra e ON e.id_maestro = p.id_maestro
    ORDER BY p.id_maestro, (encode(public.hmac(p.codigo_predio, p_clave_predio, 'sha256'), 'hex') = e.seudonimo) DESC,
             p.codigo_predio
    ON CONFLICT (seudonimo_predio) DO UPDATE
    SET zona = EXCLUDED.zona, uso_predio = EXCLUDED.uso_predio, tipo_predio = EXCLUDED.tipo_predio,
        base_imponible = EXCLUDED.base_imponible;

    INSERT INTO analitica.hecho_cobranza (id_lote, seudonimo_contribuyente, id_predio, tributo, anio, cuota,
            fecha_vencimiento, monto_emitido, monto_insoluto, monto_pagado, monto_descuento, estado_pago, fecha_pago,
            canal_pago)
    SELECT v_lote, ec.seudonimo, dp.id_predio, q.tributo, q.anio, q.cuota, q.fecha_vencimiento, q.monto_emitido,
           q.monto_insoluto, q.monto_pagado, q.monto_descuento, q.estado_pago, q.fecha_pago, q.canal_pago
    FROM plata.cuota q
    JOIN plata.contribuyente c ON c.codigo = q.contribuyente_codigo
    JOIN maestros.entidad_maestra ec ON ec.id_maestro = c.id_maestro
    JOIN plata.predio p ON p.codigo_predio = q.codigo_predio_hecho
    JOIN maestros.entidad_maestra ep ON ep.id_maestro = p.id_maestro
    JOIN analitica.dim_predio dp ON dp.seudonimo_predio = ep.seudonimo
    -- En el orden de SAMNET, como la carga anterior: algunos reportes listan las cuotas del mismo periodo en
    -- distintos predios en el orden en que están guardadas.
    ORDER BY q.id_obligacion;

    INSERT INTO analitica.hecho_pago (id_lote, seudonimo_contribuyente, fecha_pago, canal, monto_pagado,
            beneficio_aplicado, monto_correspondiente)
    SELECT v_lote, e.seudonimo, p.fecha_pago, p.canal, p.monto_pagado, p.beneficio_aplicado, p.monto_correspondiente
    FROM plata.pago p
    JOIN plata.contribuyente c ON c.codigo = p.contribuyente_codigo
    JOIN maestros.entidad_maestra e ON e.id_maestro = c.id_maestro
    ORDER BY p.fecha_pago, p.id_pago;

    UPDATE analitica.lote_etl
    SET estado = 'PUBLICADO', fecha_fin = v_inicio + (clock_timestamp() - v_reloj),
        registros_leidos = v_leidos, registros_aceptados = v_leidos - v_rechazados,
        registros_rechazados = v_rechazados
    WHERE id_lote = v_lote;

    DROP TABLE IF EXISTS etl_c, etl_p, etl_q, etl_pg, etl_err, etl_regla, etl_titular, etl_predio, etl_predio_tecnico;
    RETURN v_lote;
END;
$$;

COMMENT ON FUNCTION etl.ejecutar_lote(DATE, VARCHAR, TEXT, TEXT, TIMESTAMP, NUMERIC, NUMERIC) IS
    'Ejecuta un lote del ETL (SAMNET → bronce → plata → oro) y devuelve su id. Ver R__etl_pipeline.sql.';
