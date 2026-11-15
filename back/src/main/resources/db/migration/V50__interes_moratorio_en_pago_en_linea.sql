-- Interés moratorio en el pago en línea (Código Tributario, art. 33). Hasta ahora la orden cobraba solo el insoluto:
-- el contribuyente pagaba menos de lo que su estado de cuenta mostraba como total a pagar. La orden guarda el interés
-- de cada cuota a la fecha de la orden y SAMNET registra cuánto del pago fue interés.
ALTER TABLE operativo.transaccion_pago
    ADD COLUMN monto_interes NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (monto_interes >= 0);

ALTER TABLE operativo.transaccion_pago DROP CONSTRAINT ck_transaccion_monto;
ALTER TABLE operativo.transaccion_pago
    ADD CONSTRAINT ck_transaccion_monto CHECK (monto_total = monto_base - monto_descuento + monto_interes);

ALTER TABLE operativo.transaccion_pago_detalle
    ADD COLUMN monto_interes NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (monto_interes >= 0);

ALTER TABLE samnet.pago_obligacion
    ADD COLUMN monto_interes NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (monto_interes >= 0);

CREATE OR REPLACE FUNCTION samnet.ps_registrarpagoweb(p_codigo VARCHAR, p_transaccion BIGINT, p_fecha DATE)
RETURNS VARCHAR LANGUAGE plpgsql AS $$
DECLARE
    v_id VARCHAR(20) := 'WEB-' || p_transaccion;
    v_transaccion operativo.transaccion_pago%ROWTYPE;
    v_detalle RECORD;
    v_base NUMERIC(14,2) := 0;
    v_descuento NUMERIC(14,2) := 0;
    v_interes NUMERIC(14,2) := 0;
    v_cantidad INTEGER := 0;
    v_beneficio VARCHAR(20);
BEGIN
    -- Serializa confirmaciones del mismo contribuyente y bloquea inserciones con FK a esta fila.
    PERFORM 1 FROM samnet.contribuyente WHERE codigo = p_codigo FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Contribuyente inexistente'; END IF;

    SELECT * INTO v_transaccion FROM operativo.transaccion_pago
    WHERE id_transaccion = p_transaccion AND motivo = 'OBLIGACIONES' AND estado = 'INICIADA'
    FOR UPDATE;
    IF NOT FOUND OR v_transaccion.documento_contribuyente <>
            (SELECT numero_documento FROM samnet.contribuyente WHERE codigo = p_codigo) THEN
        RAISE EXCEPTION 'Orden no vigente o de otro contribuyente';
    END IF;
    IF v_transaccion.monto_total <= 0 OR v_transaccion.monto_total <>
            v_transaccion.monto_base - v_transaccion.monto_descuento + v_transaccion.monto_interes THEN
        RAISE EXCEPTION 'Importes de orden incoherentes';
    END IF;

    FOR v_detalle IN
        SELECT d.id_obligacion, d.monto_base, d.monto_descuento, d.monto_interes, d.id_beneficio,
               o.monto_emitido - o.monto_pagado - o.monto_descontado AS saldo,
               o.porcentaje_descuento, o.id_beneficio AS beneficio_actual
        FROM operativo.transaccion_pago_detalle d
        JOIN samnet.obligacion o ON o.id = d.id_obligacion AND o.contribuyente_codigo = p_codigo
        WHERE d.id_transaccion = p_transaccion
        ORDER BY o.id FOR UPDATE OF o
    LOOP
        IF v_detalle.saldo <= 0 OR v_detalle.monto_base <> v_detalle.saldo OR
                v_detalle.monto_descuento <> round(v_detalle.saldo * v_detalle.porcentaje_descuento / 100, 2) THEN
            RAISE EXCEPTION 'Saldo o descuento cambió';
        END IF;
        IF v_detalle.id_beneficio IS DISTINCT FROM v_detalle.beneficio_actual THEN
            RAISE EXCEPTION 'Asignación de beneficio cambió';
        END IF;
        IF v_detalle.monto_descuento > 0 THEN
            IF NOT EXISTS (
                SELECT 1 FROM samnet.beneficio_tributario b
                WHERE b.id_beneficio = v_detalle.id_beneficio
                  AND b.contribuyente_codigo = p_codigo
                  AND b.porcentaje_descuento = v_detalle.porcentaje_descuento
                  AND p_fecha BETWEEN b.vigencia_desde AND b.vigencia_hasta
            ) THEN
                RAISE EXCEPTION 'Beneficio no verificable';
            END IF;
        ELSIF v_detalle.id_beneficio IS NOT NULL OR v_detalle.porcentaje_descuento <> 0 THEN
            RAISE EXCEPTION 'Beneficio no verificable';
        END IF;
        v_cantidad := v_cantidad + 1;
        v_base := v_base + v_detalle.monto_base;
        v_descuento := v_descuento + v_detalle.monto_descuento;
        v_interes := v_interes + v_detalle.monto_interes;
    END LOOP;

    IF v_cantidad = 0 OR v_cantidad <>
            (SELECT count(*) FROM operativo.transaccion_pago_detalle WHERE id_transaccion = p_transaccion)
       OR v_cantidad <>
            (SELECT count(*) FROM samnet.obligacion WHERE contribuyente_codigo = p_codigo
             AND monto_emitido - monto_pagado - monto_descontado > 0)
       OR v_base <> v_transaccion.monto_base OR v_descuento <> v_transaccion.monto_descuento
       OR v_interes <> v_transaccion.monto_interes THEN
        RAISE EXCEPTION 'Conjunto de deudas o importes cambió';
    END IF;
    IF EXISTS (SELECT 1 FROM samnet.beneficio_tributario b WHERE b.contribuyente_codigo = p_codigo
               AND p_fecha BETWEEN b.vigencia_desde AND b.vigencia_hasta)
       AND NOT EXISTS (SELECT 1 FROM operativo.transaccion_pago_detalle d
                       WHERE d.id_transaccion = p_transaccion AND d.id_beneficio IS NOT NULL) THEN
        RAISE EXCEPTION 'Beneficio no verificable';
    END IF;

    SELECT CASE WHEN count(DISTINCT id_beneficio) = 1 THEN min(id_beneficio) END INTO v_beneficio
    FROM operativo.transaccion_pago_detalle WHERE id_transaccion = p_transaccion;
    INSERT INTO samnet.pago (id_pago, contribuyente_codigo, fecha_pago, canal, monto_pagado,
                             monto_correspondiente, id_beneficio)
    VALUES (v_id, p_codigo, p_fecha, 'EN_LINEA', v_transaccion.monto_total, v_base + v_interes, v_beneficio);
    INSERT INTO samnet.pago_obligacion
        (id_pago, id_obligacion, monto_aplicado, monto_descuento, monto_interes, id_beneficio)
    SELECT v_id, id_obligacion, monto_base - monto_descuento, monto_descuento, monto_interes, id_beneficio
    FROM operativo.transaccion_pago_detalle WHERE id_transaccion = p_transaccion;
    UPDATE samnet.obligacion o
    SET monto_pagado = o.monto_pagado + d.monto_base - d.monto_descuento,
        monto_descontado = o.monto_descontado + d.monto_descuento
    FROM operativo.transaccion_pago_detalle d
    WHERE d.id_transaccion = p_transaccion AND d.id_obligacion = o.id;
    RETURN v_id;
END;
$$;
