-- HU043 / RF-056: Ventanilla revisa la solicitud de registro o traspaso de predio (HU046) y la acepta o la rechaza.

-- Deuda existente del predio al momento de la decisión (insoluto en SAMNET). Se guarda para que la respuesta del buzón
-- muestre la cifra que vio el revisor, aunque la deuda cambie después. Es nula si la solicitud no tiene predio en SAMNET.
ALTER TABLE operativo.solicitud_predio
    ADD COLUMN deuda_predio NUMERIC(14, 2) CHECK (deuda_predio >= 0);

-- RF-056 / RNF-001: traspaso de un predio existente al contribuyente adquirente. Los titulares anteriores dejan de
-- serlo y el adquirente queda como titular. Las obligaciones ya emitidas no cambian de deudor. Si el predio no existe,
-- falla con no_data_found y no cambia nada.
CREATE OR REPLACE FUNCTION samnet.ps_traspasarpredio(
    p_codigo_predio VARCHAR, p_contribuyente_codigo VARCHAR, p_condicion_propiedad VARCHAR,
    p_porcentaje_propiedad NUMERIC)
RETURNS VARCHAR
LANGUAGE plpgsql VOLATILE AS $$
BEGIN
    PERFORM 1 FROM samnet.predio WHERE codigo_predio = p_codigo_predio FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'El predio % no existe', p_codigo_predio USING ERRCODE = 'no_data_found';
    END IF;

    DELETE FROM samnet.predio_titular
    WHERE codigo_predio = p_codigo_predio AND contribuyente_codigo <> p_contribuyente_codigo;

    INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad,
                                       porcentaje_propiedad)
    VALUES (p_codigo_predio, p_contribuyente_codigo, p_condicion_propiedad, p_porcentaje_propiedad)
    ON CONFLICT (codigo_predio, contribuyente_codigo) DO UPDATE
        SET condicion_propiedad = EXCLUDED.condicion_propiedad,
            porcentaje_propiedad = EXCLUDED.porcentaje_propiedad;

    RETURN p_codigo_predio;
END;
$$;

-- Ventanilla revisa la bandeja de solicitudes de predio; el permiso se exige en /api/gestion/solicitudes-predio.
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'SOLICITUDES_PREDIO:REVISAR')
WHERE nombre = 'VENTANILLA'
  AND NOT ('SOLICITUDES_PREDIO:REVISAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
