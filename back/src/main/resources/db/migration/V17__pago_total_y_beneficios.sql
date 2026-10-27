-- El dinero y el descuento se registran por separado; la cotización queda ligada a cada obligación.
ALTER TABLE samnet.obligacion
    ADD COLUMN monto_descontado NUMERIC(14,2) NOT NULL DEFAULT 0
        CHECK (monto_descontado >= 0);
ALTER TABLE samnet.obligacion
    ADD COLUMN id_beneficio VARCHAR(20) REFERENCES samnet.beneficio_tributario (id_beneficio);
ALTER TABLE samnet.obligacion
    ADD CONSTRAINT ck_obligacion_cobertura
        CHECK (monto_pagado + monto_descontado <= monto_emitido);

ALTER TABLE samnet.pago_obligacion DROP CONSTRAINT pago_obligacion_monto_aplicado_check;
ALTER TABLE samnet.pago_obligacion
    ADD CONSTRAINT ck_pago_obligacion_dinero CHECK (monto_aplicado >= 0),
    ADD COLUMN monto_descuento NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (monto_descuento >= 0),
    ADD COLUMN id_beneficio VARCHAR(20) REFERENCES samnet.beneficio_tributario (id_beneficio);

CREATE TABLE operativo.transaccion_pago_detalle (
    id_transaccion BIGINT NOT NULL REFERENCES operativo.transaccion_pago (id_transaccion) ON DELETE CASCADE,
    id_obligacion BIGINT NOT NULL,
    monto_base NUMERIC(14,2) NOT NULL CHECK (monto_base > 0),
    monto_descuento NUMERIC(14,2) NOT NULL CHECK (monto_descuento BETWEEN 0 AND monto_base),
    id_beneficio VARCHAR(20),
    PRIMARY KEY (id_transaccion, id_obligacion),
    CONSTRAINT ck_detalle_beneficio CHECK ((monto_descuento > 0) = (id_beneficio IS NOT NULL))
);

-- Solo devuelve la asignación explícita que reconoció el origen municipal; una tasa coincidente no basta.
CREATE FUNCTION samnet.ps_beneficios_obligacion(p_codigo VARCHAR)
RETURNS TABLE (id_obligacion BIGINT, id_beneficio VARCHAR)
LANGUAGE sql STABLE AS $$
    SELECT o.id, o.id_beneficio FROM samnet.obligacion o WHERE o.contribuyente_codigo = p_codigo;
$$;

-- La firma antigua aceptaba un subconjunto arbitrario enviado por el cliente.
DROP FUNCTION samnet.ps_registrarpagoweb(VARCHAR, BIGINT, BIGINT[], NUMERIC, DATE);

CREATE FUNCTION samnet.ps_registrarpagoweb(p_codigo VARCHAR, p_transaccion BIGINT, p_fecha DATE)
RETURNS VARCHAR LANGUAGE plpgsql AS $$
DECLARE
    v_id VARCHAR(20) := 'WEB-' || p_transaccion;
    v_transaccion operativo.transaccion_pago%ROWTYPE;
    v_detalle RECORD;
    v_base NUMERIC(14,2) := 0;
    v_descuento NUMERIC(14,2) := 0;
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
            v_transaccion.monto_base - v_transaccion.monto_descuento THEN
        RAISE EXCEPTION 'Importes de orden incoherentes';
    END IF;

    FOR v_detalle IN
        SELECT d.id_obligacion, d.monto_base, d.monto_descuento, d.id_beneficio,
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
    END LOOP;

    IF v_cantidad = 0 OR v_cantidad <>
            (SELECT count(*) FROM operativo.transaccion_pago_detalle WHERE id_transaccion = p_transaccion)
       OR v_cantidad <>
            (SELECT count(*) FROM samnet.obligacion WHERE contribuyente_codigo = p_codigo
             AND monto_emitido - monto_pagado - monto_descontado > 0)
       OR v_base <> v_transaccion.monto_base OR v_descuento <> v_transaccion.monto_descuento THEN
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
    VALUES (v_id, p_codigo, p_fecha, 'EN_LINEA', v_transaccion.monto_total, v_base, v_beneficio);
    INSERT INTO samnet.pago_obligacion
        (id_pago, id_obligacion, monto_aplicado, monto_descuento, id_beneficio)
    SELECT v_id, id_obligacion, monto_base - monto_descuento, monto_descuento, id_beneficio
    FROM operativo.transaccion_pago_detalle WHERE id_transaccion = p_transaccion;
    UPDATE samnet.obligacion o
    SET monto_pagado = o.monto_pagado + d.monto_base - d.monto_descuento,
        monto_descontado = o.monto_descontado + d.monto_descuento
    FROM operativo.transaccion_pago_detalle d
    WHERE d.id_transaccion = p_transaccion AND d.id_obligacion = o.id;
    RETURN v_id;
END;
$$;
