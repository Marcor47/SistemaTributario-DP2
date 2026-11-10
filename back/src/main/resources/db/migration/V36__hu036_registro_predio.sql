-- HU036 / RF-048: registro de un predio NUEVO con su titular. A diferencia de ps_registrarpredio (que también edita,
-- RF-056, y por eso sobrescribe), esta función NO actualiza: si el código predial ya existe, falla con
-- unique_violation y no cambia nada. Predio y titular se insertan en la misma transacción.
CREATE OR REPLACE FUNCTION samnet.ps_registrarpredionuevo(
    p_codigo_predio VARCHAR, p_numero_catastral VARCHAR, p_ubicacion VARCHAR, p_uso_predio VARCHAR,
    p_tipo_predio VARCHAR, p_area_terreno NUMERIC, p_base_imponible NUMERIC, p_fecha_adquisicion DATE,
    p_zona VARCHAR, p_contribuyente_codigo VARCHAR, p_condicion_propiedad VARCHAR,
    p_porcentaje_propiedad NUMERIC)
RETURNS VARCHAR
LANGUAGE plpgsql VOLATILE AS $$
BEGIN
    INSERT INTO samnet.predio (codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno,
                               base_imponible, fecha_adquisicion, zona)
    VALUES (p_codigo_predio, p_numero_catastral, p_ubicacion, p_uso_predio, p_tipo_predio, p_area_terreno,
            p_base_imponible, p_fecha_adquisicion, p_zona);

    INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad,
                                       porcentaje_propiedad)
    VALUES (p_codigo_predio, p_contribuyente_codigo, p_condicion_propiedad, p_porcentaje_propiedad);

    RETURN p_codigo_predio;
END;
$$;

-- El Analista de Rentas registra predios (HU036); el permiso se exige en POST /api/predios.
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'PREDIOS:REGISTRAR')
WHERE nombre = 'ANALISTA_RENTAS'
  AND NOT ('PREDIOS:REGISTRAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
