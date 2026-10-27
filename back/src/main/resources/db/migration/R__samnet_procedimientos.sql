-- Procedimientos de SAMNET emulados sobre la BD sintética. Conservan el nombre y el propósito de los SP
-- reales (ps_ctacte, ps_resumenpagos, ps_verificarBeneficio, ps_login); si algún día se obtiene acceso
-- a SAMNET, solo cambia la implementación de SamnetGateway, no sus llamadas.
-- Las únicas escrituras permitidas son las de RF-048 y RF-056 (ps_registrarpredio), según RNF-001.
-- Es una migración repetible: se vuelve a ejecutar cada vez que cambia este archivo.

-- RF-001: contribuyente por código o por número de documento.
CREATE OR REPLACE FUNCTION samnet.ps_contribuyente(p_codigo_o_documento VARCHAR)
RETURNS TABLE (
    codigo VARCHAR, tipo_documento VARCHAR, numero_documento VARCHAR, nombre VARCHAR, regimen VARCHAR,
    tipo_persona VARCHAR, condicion VARCHAR, correo VARCHAR, telefonos VARCHAR, estado VARCHAR,
    domicilio_fiscal VARCHAR, dom_departamento VARCHAR, dom_provincia VARCHAR, dom_distrito VARCHAR,
    dom_via VARCHAR, dom_numero VARCHAR, dom_interior VARCHAR, dom_manzana VARCHAR, dom_lote VARCHAR)
LANGUAGE sql STABLE AS $$
    SELECT c.codigo, c.tipo_documento, c.numero_documento, c.nombre, c.regimen, c.tipo_persona, c.condicion,
           c.correo, c.telefonos, c.estado, c.domicilio_fiscal, c.dom_departamento, c.dom_provincia,
           c.dom_distrito, c.dom_via, c.dom_numero, c.dom_interior, c.dom_manzana, c.dom_lote
    FROM samnet.contribuyente c
    WHERE c.codigo = p_codigo_o_documento OR c.numero_documento = p_codigo_o_documento
    ORDER BY c.codigo;
$$;

-- HU004: búsqueda parcial por código, nombre o documento para el selector de la pantalla.
CREATE OR REPLACE FUNCTION samnet.ps_buscarcontribuyentes(p_texto VARCHAR)
RETURNS SETOF samnet.contribuyente
LANGUAGE sql STABLE AS $$
    SELECT c.*
    FROM samnet.contribuyente c
    WHERE upper(c.codigo) LIKE '%' || upper(p_texto) || '%'
       OR upper(c.nombre) LIKE '%' || upper(p_texto) || '%'
       OR upper(c.numero_documento) LIKE '%' || upper(p_texto) || '%'
    ORDER BY c.nombre;
$$;

-- RF-002: predios por código predial o por contribuyente titular; una fila por titular.
CREATE OR REPLACE FUNCTION samnet.ps_predio(p_codigo_predio VARCHAR, p_contribuyente VARCHAR)
RETURNS TABLE (
    codigo_predio VARCHAR, numero_catastral VARCHAR, ubicacion VARCHAR, uso_predio VARCHAR,
    tipo_predio VARCHAR, area_terreno NUMERIC, base_imponible NUMERIC, fecha_adquisicion DATE,
    fecha_baja DATE, zona VARCHAR, contribuyente_codigo VARCHAR, condicion_propiedad VARCHAR,
    porcentaje_propiedad NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT p.codigo_predio, p.numero_catastral, p.ubicacion, p.uso_predio, p.tipo_predio, p.area_terreno,
           p.base_imponible, p.fecha_adquisicion, p.fecha_baja, p.zona, t.contribuyente_codigo,
           t.condicion_propiedad, t.porcentaje_propiedad
    FROM samnet.predio p
    LEFT JOIN samnet.predio_titular t ON t.codigo_predio = p.codigo_predio
    WHERE (p_codigo_predio IS NOT NULL AND p.codigo_predio = p_codigo_predio)
       OR (p_contribuyente IS NOT NULL AND t.contribuyente_codigo = p_contribuyente)
    ORDER BY p.codigo_predio, t.contribuyente_codigo;
$$;

-- RF-003: declaraciones juradas del predio, la vigente primero.
CREATE OR REPLACE FUNCTION samnet.ps_declaracionjurada(p_codigo_predio VARCHAR)
RETURNS TABLE (
    id_declaracion VARCHAR, codigo_predio VARCHAR, anio INTEGER, autoavaluo NUMERIC, motivo VARCHAR,
    submotivo VARCHAR, tipo_declarante VARCHAR, estado VARCHAR, es_vigente BOOLEAN)
LANGUAGE sql STABLE AS $$
    SELECT d.id_declaracion, d.codigo_predio, d.anio, d.autoavaluo, d.motivo, d.submotivo, d.tipo_declarante,
           d.estado, d.es_vigente
    FROM samnet.declaracion_jurada d
    WHERE d.codigo_predio = p_codigo_predio
    ORDER BY d.es_vigente DESC, d.anio DESC;
$$;

-- Columnas comunes de una obligación con su insoluto y la etapa de cobranza más reciente (RF-004, RF-014).
CREATE OR REPLACE FUNCTION samnet.fn_obligaciones(p_contribuyente VARCHAR, p_id_obligacion BIGINT)
RETURNS TABLE (
    id_obligacion BIGINT, contribuyente_codigo VARCHAR, codigo_predio VARCHAR, tributo VARCHAR,
    anio INTEGER, cuota INTEGER, fecha_vencimiento DATE, monto_emitido NUMERIC, monto_pagado NUMERIC,
    insoluto NUMERIC, porcentaje_descuento NUMERIC, etapa_cobranza VARCHAR, fecha_etapa DATE)
LANGUAGE sql STABLE AS $$
    SELECT o.id, o.contribuyente_codigo, o.codigo_predio, o.tributo, o.anio, o.numero_cuota,
           o.fecha_vencimiento, o.monto_emitido, o.monto_pagado,
           o.monto_emitido - o.monto_pagado - o.monto_descontado,
           o.porcentaje_descuento, s.etapa, s.fecha_inicio
    FROM samnet.obligacion o
    LEFT JOIN LATERAL (
        SELECT sc.etapa, sc.fecha_inicio
        FROM samnet.seguimiento_cobranza sc
        WHERE sc.obligacion_id = o.id
        ORDER BY sc.fecha_inicio DESC, sc.id DESC
        LIMIT 1
    ) s ON TRUE
    WHERE (p_contribuyente IS NULL OR o.contribuyente_codigo = p_contribuyente)
      AND (p_id_obligacion IS NULL OR o.id = p_id_obligacion)
    ORDER BY o.anio, o.numero_cuota, o.tributo;
$$;

-- RF-004 / RF-018: cuenta corriente del contribuyente (obligaciones por tributo y periodo).
CREATE OR REPLACE FUNCTION samnet.ps_ctacte(p_codigo VARCHAR)
RETURNS TABLE (
    id_obligacion BIGINT, contribuyente_codigo VARCHAR, codigo_predio VARCHAR, tributo VARCHAR,
    anio INTEGER, cuota INTEGER, fecha_vencimiento DATE, monto_emitido NUMERIC, monto_pagado NUMERIC,
    insoluto NUMERIC, porcentaje_descuento NUMERIC, etapa_cobranza VARCHAR, fecha_etapa DATE)
LANGUAGE sql STABLE AS $$
    SELECT * FROM samnet.fn_obligaciones(p_codigo, NULL);
$$;

-- RF-014: una obligación puntual (consulta de su etapa de cobranza).
CREATE OR REPLACE FUNCTION samnet.ps_obligacion(p_id_obligacion BIGINT)
RETURNS TABLE (
    id_obligacion BIGINT, contribuyente_codigo VARCHAR, codigo_predio VARCHAR, tributo VARCHAR,
    anio INTEGER, cuota INTEGER, fecha_vencimiento DATE, monto_emitido NUMERIC, monto_pagado NUMERIC,
    insoluto NUMERIC, porcentaje_descuento NUMERIC, etapa_cobranza VARCHAR, fecha_etapa DATE)
LANGUAGE sql STABLE AS $$
    SELECT * FROM samnet.fn_obligaciones(NULL, p_id_obligacion);
$$;

-- RF-014: historial de etapas de cobranza registradas para una obligación.
CREATE OR REPLACE FUNCTION samnet.ps_seguimientocobranza(p_id_obligacion BIGINT)
RETURNS TABLE (
    id BIGINT, id_obligacion BIGINT, etapa VARCHAR, fecha_inicio DATE, documento_referencia VARCHAR,
    observacion VARCHAR, fecha_registro TIMESTAMP)
LANGUAGE sql STABLE AS $$
    SELECT sc.id, sc.obligacion_id, sc.etapa, sc.fecha_inicio, sc.documento_referencia, sc.observacion,
           sc.fecha_registro
    FROM samnet.seguimiento_cobranza sc
    WHERE sc.obligacion_id = p_id_obligacion
    ORDER BY sc.fecha_inicio, sc.id;
$$;

-- RF-062: pagos del contribuyente con lo que correspondía pagar y el beneficio aplicado.
CREATE OR REPLACE FUNCTION samnet.ps_resumenpagos(p_codigo VARCHAR)
RETURNS TABLE (
    id_pago VARCHAR, contribuyente_codigo VARCHAR, fecha_pago DATE, canal VARCHAR, monto_pagado NUMERIC,
    monto_correspondiente NUMERIC, diferencia NUMERIC, beneficio_aplicado VARCHAR)
LANGUAGE sql STABLE AS $$
    SELECT p.id_pago, p.contribuyente_codigo, p.fecha_pago, p.canal, p.monto_pagado, p.monto_correspondiente,
           p.monto_pagado - p.monto_correspondiente, p.id_beneficio
    FROM samnet.pago p
    WHERE p.contribuyente_codigo = p_codigo
    ORDER BY p.fecha_pago DESC, p.id_pago;
$$;

-- RF-036: beneficios vigentes del contribuyente a la fecha indicada.
CREATE OR REPLACE FUNCTION samnet.ps_verificarbeneficio(p_codigo VARCHAR, p_fecha DATE)
RETURNS TABLE (
    id_beneficio VARCHAR, contribuyente_codigo VARCHAR, tipo VARCHAR, porcentaje_descuento NUMERIC,
    vigencia_desde DATE, vigencia_hasta DATE)
LANGUAGE sql STABLE AS $$
    SELECT b.id_beneficio, b.contribuyente_codigo, b.tipo, b.porcentaje_descuento, b.vigencia_desde,
           b.vigencia_hasta
    FROM samnet.beneficio_tributario b
    WHERE b.contribuyente_codigo = p_codigo
      AND p_fecha BETWEEN b.vigencia_desde AND b.vigencia_hasta
    ORDER BY b.porcentaje_descuento DESC;
$$;

-- RF-048 / RF-056: registra o edita un predio y su titular. Devuelve el código predial.
CREATE OR REPLACE FUNCTION samnet.ps_registrarpredio(
    p_codigo_predio VARCHAR, p_numero_catastral VARCHAR, p_ubicacion VARCHAR, p_uso_predio VARCHAR,
    p_tipo_predio VARCHAR, p_area_terreno NUMERIC, p_base_imponible NUMERIC, p_fecha_adquisicion DATE,
    p_zona VARCHAR, p_contribuyente_codigo VARCHAR, p_condicion_propiedad VARCHAR,
    p_porcentaje_propiedad NUMERIC)
RETURNS VARCHAR
LANGUAGE plpgsql VOLATILE AS $$
DECLARE
    v_codigo VARCHAR := p_codigo_predio;
BEGIN
    IF v_codigo IS NULL THEN
        SELECT 'P' || lpad((count(*) + 1)::TEXT, 7, '0') INTO v_codigo FROM samnet.predio;
    END IF;

    INSERT INTO samnet.predio (codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno,
                               base_imponible, fecha_adquisicion, zona)
    VALUES (v_codigo, p_numero_catastral, p_ubicacion, p_uso_predio, p_tipo_predio, p_area_terreno,
            p_base_imponible, p_fecha_adquisicion, p_zona)
    ON CONFLICT (codigo_predio) DO UPDATE
        SET numero_catastral = EXCLUDED.numero_catastral, ubicacion = EXCLUDED.ubicacion,
            uso_predio = EXCLUDED.uso_predio, tipo_predio = EXCLUDED.tipo_predio,
            area_terreno = EXCLUDED.area_terreno, base_imponible = EXCLUDED.base_imponible,
            fecha_adquisicion = EXCLUDED.fecha_adquisicion, zona = EXCLUDED.zona;

    IF p_contribuyente_codigo IS NOT NULL THEN
        INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad,
                                           porcentaje_propiedad)
        VALUES (v_codigo, p_contribuyente_codigo, p_condicion_propiedad, p_porcentaje_propiedad)
        ON CONFLICT (codigo_predio, contribuyente_codigo) DO UPDATE
            SET condicion_propiedad = EXCLUDED.condicion_propiedad,
                porcentaje_propiedad = EXCLUDED.porcentaje_propiedad;
    END IF;

    RETURN v_codigo;
END;
$$;

-- RF-054: autenticación del contribuyente con su documento y clave. Sin filas = credenciales inválidas.
-- Nunca devuelve el hash ni un token: el sistema crea su propia sesión (RNF-028).
CREATE OR REPLACE FUNCTION samnet.ps_login(p_documento VARCHAR, p_clave VARCHAR)
RETURNS TABLE (contribuyente_codigo VARCHAR, nombre VARCHAR)
LANGUAGE sql STABLE AS $$
    SELECT c.codigo, c.nombre
    FROM samnet.contribuyente c
    JOIN samnet.credencial_web w ON w.contribuyente_codigo = c.codigo
    WHERE (c.numero_documento = p_documento OR c.codigo = p_documento)
      AND c.estado = 'ACTIVO'
      AND w.habilitada
      AND w.clave_hash = public.crypt(p_clave, w.clave_hash);
$$;

-- Validación de identidad por DNI (servicio de consulta de DNI de la capa municipal).
CREATE OR REPLACE FUNCTION samnet.ps_consultadni(p_dni VARCHAR)
RETURNS TABLE (numero_documento VARCHAR, nombre VARCHAR)
LANGUAGE sql STABLE AS $$
    SELECT c.numero_documento, c.nombre
    FROM samnet.contribuyente c
    WHERE c.tipo_documento = 'DNI' AND c.numero_documento = p_dni;
$$;

-- Extracciones del ETL (RF-038). Devuelven los datos de Rentas con identificadores en claro:
-- el ETL los seudonimiza antes de cargarlos a la base analítica (RNF-018).
CREATE OR REPLACE FUNCTION samnet.ps_etl_contribuyentes()
RETURNS SETOF samnet.contribuyente
LANGUAGE sql STABLE AS $$
    SELECT * FROM samnet.contribuyente ORDER BY codigo;
$$;

CREATE OR REPLACE FUNCTION samnet.ps_etl_predios()
RETURNS TABLE (
    codigo_predio VARCHAR, numero_catastral VARCHAR, ubicacion VARCHAR, uso_predio VARCHAR,
    tipo_predio VARCHAR, area_terreno NUMERIC, base_imponible NUMERIC, fecha_adquisicion DATE,
    fecha_baja DATE, zona VARCHAR, contribuyente_codigo VARCHAR, condicion_propiedad VARCHAR,
    porcentaje_propiedad NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT p.codigo_predio, p.numero_catastral, p.ubicacion, p.uso_predio, p.tipo_predio, p.area_terreno,
           p.base_imponible, p.fecha_adquisicion, p.fecha_baja, p.zona, t.contribuyente_codigo,
           t.condicion_propiedad, t.porcentaje_propiedad
    FROM samnet.predio p
    LEFT JOIN samnet.predio_titular t ON t.codigo_predio = p.codigo_predio
    ORDER BY p.codigo_predio;
$$;

-- Cuotas emitidas hasta el año de la fecha de corte, con su zona para los reportes por zona.
CREATE OR REPLACE FUNCTION samnet.ps_etl_cobranza(p_fecha_corte DATE)
RETURNS TABLE (
    id_obligacion BIGINT, contribuyente_codigo VARCHAR, codigo_predio VARCHAR, zona VARCHAR, tributo VARCHAR,
    anio INTEGER, cuota INTEGER, fecha_vencimiento DATE, monto_emitido NUMERIC, monto_pagado NUMERIC,
    insoluto NUMERIC, porcentaje_descuento NUMERIC, etapa_cobranza VARCHAR, fecha_etapa DATE)
LANGUAGE sql STABLE AS $$
    SELECT o.id_obligacion, o.contribuyente_codigo, o.codigo_predio, p.zona, o.tributo, o.anio, o.cuota,
           o.fecha_vencimiento, o.monto_emitido, o.monto_pagado, o.insoluto, o.porcentaje_descuento,
           o.etapa_cobranza, o.fecha_etapa
    FROM samnet.fn_obligaciones(NULL, NULL) o
    LEFT JOIN samnet.predio p ON p.codigo_predio = o.codigo_predio
    WHERE o.anio <= EXTRACT(YEAR FROM p_fecha_corte);
$$;

CREATE OR REPLACE FUNCTION samnet.ps_etl_pagos(p_fecha_corte DATE)
RETURNS TABLE (
    id_pago VARCHAR, contribuyente_codigo VARCHAR, fecha_pago DATE, canal VARCHAR, monto_pagado NUMERIC,
    monto_correspondiente NUMERIC, beneficio_aplicado VARCHAR)
LANGUAGE sql STABLE AS $$
    SELECT p.id_pago, p.contribuyente_codigo, p.fecha_pago, p.canal, p.monto_pagado, p.monto_correspondiente,
           p.id_beneficio
    FROM samnet.pago p
    WHERE p.fecha_pago <= p_fecha_corte
    ORDER BY p.fecha_pago, p.id_pago;
$$;
