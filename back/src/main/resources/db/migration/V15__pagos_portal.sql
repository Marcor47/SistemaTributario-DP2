-- La operación de cobro es atómica: valida titularidad y saldos bajo bloqueo antes de registrar el pago.
CREATE FUNCTION samnet.ps_registrarpagoweb(p_codigo VARCHAR, p_transaccion BIGINT,
        p_obligaciones BIGINT[], p_monto NUMERIC, p_fecha DATE)
RETURNS VARCHAR LANGUAGE plpgsql AS $$
DECLARE
    v_id VARCHAR(20) := 'WEB-' || p_transaccion;
    v_obligacion RECORD;
    v_suma NUMERIC(14,2) := 0;
BEGIN
    IF p_obligaciones IS NULL OR cardinality(p_obligaciones) = 0 OR p_monto <= 0 THEN
        RAISE EXCEPTION 'Pago sin obligaciones o monto válido';
    END IF;
    IF (SELECT COUNT(*) FROM unnest(p_obligaciones) x) <>
       (SELECT COUNT(DISTINCT x) FROM unnest(p_obligaciones) x) THEN
        RAISE EXCEPTION 'Obligaciones duplicadas';
    END IF;
    FOR v_obligacion IN
        SELECT o.id, o.monto_emitido - o.monto_pagado AS saldo
        FROM samnet.obligacion o
        WHERE o.id = ANY(p_obligaciones) AND o.contribuyente_codigo = p_codigo
        ORDER BY o.id FOR UPDATE
    LOOP
        IF v_obligacion.saldo <= 0 THEN
            RAISE EXCEPTION 'Obligación ya pagada';
        END IF;
        v_suma := v_suma + v_obligacion.saldo;
    END LOOP;
    IF (SELECT COUNT(*) FROM samnet.obligacion
        WHERE id = ANY(p_obligaciones) AND contribuyente_codigo = p_codigo) <> cardinality(p_obligaciones)
       OR v_suma <> p_monto THEN
        RAISE EXCEPTION 'Titularidad o saldo de obligaciones cambió';
    END IF;

    INSERT INTO samnet.pago (id_pago, contribuyente_codigo, fecha_pago, canal, monto_pagado,
                             monto_correspondiente)
    VALUES (v_id, p_codigo, p_fecha, 'EN_LINEA', p_monto, p_monto);
    INSERT INTO samnet.pago_obligacion (id_pago, id_obligacion, monto_aplicado)
    SELECT v_id, id, monto_emitido - monto_pagado
    FROM samnet.obligacion WHERE id = ANY(p_obligaciones);
    UPDATE samnet.obligacion SET monto_pagado = monto_emitido WHERE id = ANY(p_obligaciones);
    RETURN v_id;
END;
$$;
