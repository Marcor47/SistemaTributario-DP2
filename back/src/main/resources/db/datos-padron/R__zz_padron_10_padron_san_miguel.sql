-- Solo perfil local. Padrón tributario de San Miguel (Lima) con volumen de producción para la presentación: miles de
-- contribuyentes con sus predios, cuotas de Impuesto Predial y arbitrios de 2022 a 2026, pagos, cobranza, trámites
-- (constancias, solicitudes de predio y reclamos en todos sus estados), buzón y avisos, y las cargas del ETL que
-- alimentan la plataforma analítica.
--
-- Reproducible: no usa random(); cada valor sale de un hash estable de su clave, así que dos bases generadas con el
-- mismo archivo tienen los mismos datos. El volumen es el marcador ${volumen_padron} (application.yml; las pruebas usan
-- uno pequeño). Al volver a ejecutarse rehace solo lo suyo: los contribuyentes 2NNNNN y lo que depende de ellos.
--
-- Reglas que respeta: arbitrio como monto único por predio y mes (V49), interés moratorio calculado por el sistema
-- sobre las cuotas vencidas (no se guarda), fechas en hora de Lima (V51) y códigos de constancia CND-AAAA-XXXXXX (V52).
-- Los nombres, documentos y direcciones son ficticios aunque con formato real (DNI de 8 dígitos y RUC con dígito
-- verificador válido); las vías y urbanizaciones sí son del distrito.

-- 0. Funciones auxiliares de esta ejecución (pg_temp desaparece al terminar la sesión de Flyway).
CREATE OR REPLACE FUNCTION pg_temp.azar(p_clave TEXT) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE AS $$
    SELECT ('x' || substr(md5(p_clave), 1, 12))::BIT(48)::BIGINT / 281474976710656.0;
$$;

CREATE OR REPLACE FUNCTION pg_temp.elegir(p_opciones TEXT[], p_clave TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT p_opciones[1 + floor(pg_temp.azar(p_clave) * array_length(p_opciones, 1))::INTEGER];
$$;

-- RUC de persona jurídica: 20 + 8 dígitos + dígito verificador (módulo 11 con pesos 5432765432).
CREATE OR REPLACE FUNCTION pg_temp.ruc(p_cuerpo TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT p_cuerpo || CASE r WHEN 10 THEN '0' WHEN 11 THEN '1' ELSE r::TEXT END
    FROM (SELECT 11 - (sum(substr(p_cuerpo, i, 1)::INTEGER * (ARRAY[5, 4, 3, 2, 7, 6, 5, 4, 3, 2])[i]) % 11) AS r
          FROM generate_series(1, 10) AS i) s;
$$;

-- 1. Limpieza: lo que generó una versión anterior de este archivo y las muestras que este padrón reemplaza (la
-- historia de 32 contribuyentes y las cuentas de la pasarela local).
CREATE TEMPORARY TABLE padron_viejo ON COMMIT DROP AS
SELECT codigo FROM samnet.contribuyente
WHERE codigo ~ '^2[0-9]{5}$' OR codigo ~ '^110[1-5][0-9]{2}$' OR codigo IN ('099023', '099024');

CREATE TEMPORARY TABLE documento_viejo ON COMMIT DROP AS
SELECT numero_documento FROM samnet.contribuyente WHERE codigo IN (SELECT codigo FROM padron_viejo);

-- Los reclamos y sus derivaciones son un registro inalterable (no se borran): se conservan con sus avisos del buzón
-- y el paso 9.3 solo agrega los de quienes aún no tienen.
DELETE FROM operativo.documento_buzon
WHERE documento_contribuyente IN (SELECT numero_documento FROM documento_viejo) AND id_reclamo IS NULL;
DELETE FROM operativo.documento_adjunto a USING operativo.solicitud_predio s
WHERE a.id_solicitud = s.id_solicitud AND s.documento_contribuyente IN (SELECT numero_documento FROM documento_viejo);
DELETE FROM operativo.solicitud_predio WHERE documento_contribuyente IN (SELECT numero_documento FROM documento_viejo);
DELETE FROM operativo.constancia c USING operativo.solicitud_constancia s
WHERE c.id_solicitud = s.id_solicitud AND s.documento_contribuyente IN (SELECT numero_documento FROM documento_viejo);
DELETE FROM operativo.firma_digital f USING operativo.solicitud_constancia s
WHERE f.id_solicitud = s.id_solicitud AND s.documento_contribuyente IN (SELECT numero_documento FROM documento_viejo);
DELETE FROM operativo.transaccion_pago WHERE documento_contribuyente IN (SELECT numero_documento FROM documento_viejo);
DELETE FROM operativo.solicitud_constancia WHERE documento_contribuyente IN (SELECT numero_documento FROM documento_viejo);
DELETE FROM operativo.aviso_correo WHERE documento_contribuyente IN (SELECT numero_documento FROM documento_viejo);
DELETE FROM operativo.estado_cuenta_copia WHERE codigo_contribuyente IN (SELECT codigo FROM padron_viejo);

DELETE FROM samnet.seguimiento_cobranza s USING samnet.obligacion o
WHERE s.obligacion_id = o.id AND (o.contribuyente_codigo IN (SELECT codigo FROM padron_viejo) OR o.anio < 2025);
DELETE FROM samnet.pago_obligacion po USING samnet.obligacion o
WHERE po.id_obligacion = o.id AND (o.contribuyente_codigo IN (SELECT codigo FROM padron_viejo) OR o.anio < 2025);
DELETE FROM samnet.obligacion WHERE contribuyente_codigo IN (SELECT codigo FROM padron_viejo) OR anio < 2025;
DELETE FROM samnet.pago p
WHERE (p.contribuyente_codigo IN (SELECT codigo FROM padron_viejo) OR p.id_pago LIKE 'RH-%' OR p.id_pago LIKE 'RV-%')
  AND NOT EXISTS (SELECT 1 FROM samnet.pago_obligacion po WHERE po.id_pago = p.id_pago);
DELETE FROM samnet.beneficio_tributario WHERE contribuyente_codigo IN (SELECT codigo FROM padron_viejo);
DELETE FROM samnet.credencial_web WHERE contribuyente_codigo IN (SELECT codigo FROM padron_viejo);
DELETE FROM samnet.declaracion_jurada d USING samnet.predio_titular t
WHERE d.codigo_predio = t.codigo_predio AND t.contribuyente_codigo IN (SELECT codigo FROM padron_viejo);
CREATE TEMPORARY TABLE predio_viejo ON COMMIT DROP AS
SELECT codigo_predio FROM samnet.predio_titular WHERE contribuyente_codigo IN (SELECT codigo FROM padron_viejo);
DELETE FROM samnet.predio_titular WHERE contribuyente_codigo IN (SELECT codigo FROM padron_viejo);
DELETE FROM samnet.predio p
WHERE p.codigo_predio IN (SELECT codigo_predio FROM predio_viejo)
  AND NOT EXISTS (SELECT 1 FROM samnet.predio_titular t WHERE t.codigo_predio = p.codigo_predio);
DELETE FROM samnet.contribuyente WHERE codigo IN (SELECT codigo FROM padron_viejo);

-- Cargas analíticas de las muestras anteriores (y de una versión anterior de este archivo): se rehacen en el paso 9.
CREATE TEMPORARY TABLE lote_viejo ON COMMIT DROP AS
SELECT l.id_lote FROM analitica.lote_etl l
WHERE l.fuente = 'ps_etl_cobranza' AND l.tipo_ejecucion = 'PROGRAMADA' AND l.fecha_inicio::DATE = l.fecha_corte + 1;

DELETE FROM analitica.alerta_desvio a USING analitica.valor_indicador v
WHERE a.id_valor = v.id_valor AND v.id_lote IN (SELECT id_lote FROM lote_viejo);
DELETE FROM analitica.valor_indicador WHERE id_lote IN (SELECT id_lote FROM lote_viejo);
DELETE FROM analitica.hecho_cobranza WHERE id_lote IN (SELECT id_lote FROM lote_viejo);
DELETE FROM analitica.hecho_pago WHERE id_lote IN (SELECT id_lote FROM lote_viejo);
UPDATE operativo.aviso_correo SET id_lote = NULL WHERE id_lote IN (SELECT id_lote FROM lote_viejo);
DELETE FROM analitica.respuesta_asistente WHERE id_lote IN (SELECT id_lote FROM lote_viejo);
DELETE FROM analitica.resumen_ejecutivo WHERE id_lote IN (SELECT id_lote FROM lote_viejo);
DELETE FROM analitica.lote_etl l
WHERE l.id_lote IN (SELECT id_lote FROM lote_viejo)
  AND NOT EXISTS (SELECT 1 FROM analitica.caso_homologacion c WHERE c.id_lote = l.id_lote);

-- Las constancias guardadas con el formato anterior se regeneran con la plantilla institucional al descargarlas.
UPDATE operativo.constancia SET contenido_pdf = NULL WHERE contenido_pdf IS NOT NULL AND firma_local;

-- Correos de ejemplo de las fichas de R__datos_prueba_01: correos con formato real.
UPDATE samnet.contribuyente c
SET correo = n.correo
FROM (VALUES ('014502', 'maria.garcia.torres@gmail.com'), ('021390', 'cobranzas@costaazul.com.pe'),
             ('045067', 'administracion@soldelima.edu.pe'), ('058934', 'patricia.medina.cruz@hotmail.com'),
             ('080245', 'tributos@miramar.com.pe'))
     AS n (codigo, correo)
WHERE c.codigo = n.codigo AND (c.correo LIKE '%.test' OR c.correo LIKE '%example%');

-- 2. UIT y tarifas de arbitrios de 2022 a 2024: las de 2025 ajustadas por la variación de precios acumulada.
INSERT INTO samnet.uit (anio, valor)
VALUES (2022, 4600.00), (2023, 4950.00), (2024, 5150.00)
ON CONFLICT (anio) DO UPDATE SET valor = EXCLUDED.valor;

DELETE FROM samnet.tasa_arbitrio WHERE anio < 2025;
INSERT INTO samnet.tasa_arbitrio (anio, categoria_uso, zona, monto_mensual)
SELECT f.anio, t.categoria_uso, t.zona, round(t.monto_mensual * f.factor, 2)
FROM samnet.tasa_arbitrio t
CROSS JOIN (VALUES (2022, 0.88), (2023, 0.945), (2024, 0.975)) AS f (anio, factor)
WHERE t.anio = 2025;

-- 3. Contribuyentes. Uno de cada nueve es persona jurídica (RUC); los demás, personas naturales con DNI.
CREATE TEMPORARY TABLE padron ON COMMIT DROP AS
WITH base AS (
    SELECT i, '2' || lpad(i::TEXT, 5, '0') AS codigo,
           pg_temp.azar('persona' || i) < 0.11 AS juridica,
           pg_temp.azar('sexo' || i) < 0.52 AS mujer,
           pg_temp.elegir(ARRAY['Costanera', 'Maranga', 'Maranga', 'Pando', 'Pando', 'Las Leyendas',
                                'Faucett - Libertad'], 'zona' || i) AS zona
    FROM generate_series(1, ${volumen_padron}) AS i
),
nombres AS (
    SELECT b.*,
           pg_temp.elegir(ARRAY['QUISPE', 'FLORES', 'SÁNCHEZ', 'RODRÍGUEZ', 'GARCÍA', 'ROJAS', 'HUAMÁN', 'RAMÍREZ',
               'TORRES', 'MENDOZA', 'CHÁVEZ', 'VÁSQUEZ', 'CASTILLO', 'DÍAZ', 'ESPINOZA', 'LÓPEZ', 'GONZALES',
               'PÉREZ', 'MAMANI', 'GUTIÉRREZ', 'FERNÁNDEZ', 'SALAZAR', 'RUIZ', 'VARGAS', 'RAMOS', 'REYES', 'CRUZ',
               'MORALES', 'HERRERA', 'MEDINA', 'AGUILAR', 'ROMERO', 'PAREDES', 'CASTRO', 'SILVA', 'CÓRDOVA', 'RÍOS',
               'VALVERDE', 'CÁRDENAS', 'ZAPATA', 'ALVARADO', 'NAVARRO', 'PALACIOS', 'BENITES', 'VILLANUEVA',
               'SOTO', 'CAMPOS', 'DELGADO', 'CÁCERES', 'AYALA', 'CCORI', 'HUANCA', 'CONDORI', 'APAZA', 'TICONA',
               'INGA', 'PACHECO', 'MONTOYA', 'ARIAS', 'BUSTAMANTE', 'VALDIVIA', 'VELÁSQUEZ', 'AGUIRRE', 'RIVERA',
               'LEÓN', 'VEGA', 'CARRASCO', 'ORTIZ', 'GUERRERO', 'PEÑA', 'NÚÑEZ', 'LUNA', 'SALINAS', 'OLIVERA',
               'TAPIA', 'ZEVALLOS', 'PORTOCARRERO', 'BARRIENTOS', 'HUERTA', 'CHUQUIMIA'], 'ap1' || b.i) AS ap1,
           pg_temp.elegir(ARRAY['QUISPE', 'FLORES', 'SÁNCHEZ', 'RODRÍGUEZ', 'GARCÍA', 'ROJAS', 'HUAMÁN', 'RAMÍREZ',
               'TORRES', 'MENDOZA', 'CHÁVEZ', 'VÁSQUEZ', 'CASTILLO', 'DÍAZ', 'ESPINOZA', 'LÓPEZ', 'GONZALES',
               'PÉREZ', 'MAMANI', 'GUTIÉRREZ', 'FERNÁNDEZ', 'SALAZAR', 'RUIZ', 'VARGAS', 'RAMOS', 'REYES', 'CRUZ',
               'MORALES', 'HERRERA', 'MEDINA', 'AGUILAR', 'ROMERO', 'PAREDES', 'CASTRO', 'SILVA', 'CÓRDOVA', 'RÍOS',
               'VALVERDE', 'CÁRDENAS', 'ZAPATA', 'ALVARADO', 'NAVARRO', 'PALACIOS', 'BENITES', 'VILLANUEVA',
               'SOTO', 'CAMPOS', 'DELGADO', 'CÁCERES', 'AYALA', 'CCORI', 'HUANCA', 'CONDORI', 'APAZA', 'TICONA',
               'INGA', 'PACHECO', 'MONTOYA', 'ARIAS', 'BUSTAMANTE', 'VALDIVIA', 'VELÁSQUEZ', 'AGUIRRE', 'RIVERA',
               'LEÓN', 'VEGA', 'CARRASCO', 'ORTIZ', 'GUERRERO', 'PEÑA', 'NÚÑEZ', 'LUNA', 'SALINAS', 'OLIVERA',
               'TAPIA', 'ZEVALLOS', 'PORTOCARRERO', 'BARRIENTOS', 'HUERTA', 'CHUQUIMIA'], 'ap2' || b.i) AS ap2,
           CASE WHEN b.mujer THEN pg_temp.elegir(ARRAY['MARÍA', 'ROSA', 'CARMEN', 'ANA', 'LUCÍA', 'PATRICIA', 'ELENA',
                   'SILVIA', 'GLORIA', 'JULIA', 'TERESA', 'MILAGROS', 'KAREN', 'CLAUDIA', 'SANDRA', 'VERÓNICA',
                   'GABRIELA', 'ANDREA', 'DIANA', 'MÓNICA', 'LILIANA', 'FIORELLA', 'ROCÍO', 'JESSICA', 'YOLANDA',
                   'NANCY', 'MARTHA', 'ELIZABETH', 'SOFÍA', 'VALERIA', 'CAMILA', 'XIMENA', 'FLOR', 'DORIS',
                   'BEATRIZ', 'CECILIA', 'NATALY', 'KATHERINE', 'MARIBEL', 'LOURDES'], 'n1' || b.i)
                ELSE pg_temp.elegir(ARRAY['JOSÉ', 'JUAN', 'LUIS', 'CARLOS', 'JORGE', 'MIGUEL', 'VÍCTOR', 'CÉSAR',
                   'PEDRO', 'RICARDO', 'FERNANDO', 'JULIO', 'RAÚL', 'ÓSCAR', 'MANUEL', 'ALBERTO', 'EDUARDO',
                   'ROBERTO', 'JAVIER', 'DIEGO', 'ANDRÉS', 'ALEJANDRO', 'RENZO', 'GIANCARLO', 'WALTER', 'HUGO',
                   'MARCO', 'PABLO', 'SERGIO', 'ÁNGEL', 'FRANCISCO', 'ENRIQUE', 'GUSTAVO', 'DANIEL', 'SANTIAGO',
                   'SEBASTIÁN', 'MATEO', 'ADRIÁN', 'HERNÁN', 'FÉLIX'], 'n1' || b.i) END AS nombre1,
           CASE WHEN pg_temp.azar('n2' || b.i) < 0.55 THEN
                CASE WHEN b.mujer THEN pg_temp.elegir(ARRAY['ISABEL', 'DEL PILAR', 'ELENA', 'FERNANDA', 'ALEJANDRA',
                         'VICTORIA', 'ROSA', 'LUISA', 'ANTONIETA', 'MERCEDES', 'GRACIELA', 'INÉS'], 'n2' || b.i)
                     ELSE pg_temp.elegir(ARRAY['ANTONIO', 'ALBERTO', 'ENRIQUE', 'MANUEL', 'ÁNGEL', 'FRANCISCO',
                         'JAVIER', 'RAFAEL', 'ALFONSO', 'GUILLERMO', 'ERNESTO', 'MARTÍN'], 'n2' || b.i) END
           END AS nombre2,
           pg_temp.elegir(ARRAY['INVERSIONES', 'COMERCIAL', 'DISTRIBUIDORA', 'CORPORACIÓN', 'GRUPO',
               'SERVICIOS GENERALES', 'CONSTRUCTORA', 'INMOBILIARIA', 'FARMACIA', 'CLÍNICA DENTAL', 'INSTITUTO',
               'RESTAURANTE', 'FERRETERÍA', 'MINIMARKET', 'TALLER MECÁNICO', 'IMPORTACIONES', 'LIBRERÍA',
               'PANADERÍA', 'CONSULTORA', 'TRANSPORTES', 'POLLERÍA', 'BOTICA', 'LAVANDERÍA', 'ÓPTICA',
               'CENTRO DE IDIOMAS', 'GIMNASIO', 'HOSTAL'], 'giro' || b.i) AS giro,
           pg_temp.elegir(ARRAY['SAN MIGUEL', 'MARANGA', 'PANDO', 'LA MARINA', 'DEL PACÍFICO', 'LOS PRECURSORES',
               'COSTA AZUL', 'LAS LEYENDAS', 'EL MIRADOR', 'SANTA ROSA', 'LOS ANDES', 'LIMA OESTE', 'BAHÍA',
               'DEL CALLAO', 'FAUCETT', 'MIRAMAR', 'SAN JUDAS', 'LA LIBERTAD', 'PERÚ NORTE', 'EL SOL'],
               'razon' || b.i) AS razon,
           pg_temp.elegir(ARRAY['S.A.C.', 'S.A.C.', 'S.A.C.', 'E.I.R.L.', 'E.I.R.L.', 'S.R.L.', 'S.A.'],
               'forma' || b.i) AS forma
    FROM base b
)
SELECT n.i, n.codigo, n.juridica, n.mujer, n.zona,
       CASE WHEN n.juridica THEN n.giro || ' ' || CASE WHEN pg_temp.azar('apell' || n.i) < 0.35 THEN n.ap1 || ' '
                ELSE '' END || n.razon || ' ' || n.forma
            ELSE n.ap1 || ' ' || n.ap2 || ', ' || n.nombre1 || coalesce(' ' || n.nombre2, '') END AS nombre,
       CASE WHEN n.juridica THEN pg_temp.ruc('20' || lpad((50000000 + (n.i::BIGINT * 104729) % 49999999)::TEXT, 8, '0'))
            ELSE lpad((10000000 + (n.i::BIGINT * 7919 + 1234567) % 79999999)::TEXT, 8, '0') END AS documento,
       n.ap1, n.ap2, n.nombre1, n.giro,
       -- Perfil de pago: cómo cumple sus cuotas a lo largo de los años.
       CASE WHEN pg_temp.azar('perfil' || n.i) < 0.40 THEN 'PUNTUAL'
            WHEN pg_temp.azar('perfil' || n.i) < 0.50 THEN 'ANUAL'
            WHEN pg_temp.azar('perfil' || n.i) < 0.64 THEN 'ATRASADO'
            WHEN pg_temp.azar('perfil' || n.i) < 0.72 THEN 'RECUPERA'
            WHEN pg_temp.azar('perfil' || n.i) < 0.79 THEN 'PARCIAL'
            WHEN pg_temp.azar('perfil' || n.i) < 0.90 THEN 'IRREGULAR'
            ELSE 'MOROSO' END AS perfil,
       2022 + floor(pg_temp.azar('desde' || n.i) * 5)::INTEGER AS moroso_desde
FROM nombres n;

CREATE TEMPORARY TABLE via ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('Costanera', 'Av. Costanera', 'Urb. Costa Azul'), ('Costanera', 'Av. Rafael Escardó', 'Urb. Miramar'),
    ('Costanera', 'Av. Bertolotto', 'Urb. Costa Azul'), ('Costanera', 'Jr. Mantaro', 'Urb. Miramar'),
    ('Costanera', 'Calle Bahía', 'Urb. Costa Azul'), ('Costanera', 'Av. Libertad', 'Urb. Miramar'),
    ('Maranga', 'Av. La Marina', 'Urb. Maranga'), ('Maranga', 'Av. Universitaria', 'Urb. Maranga'),
    ('Maranga', 'Av. Los Precursores', 'Urb. Maranga'), ('Maranga', 'Calle Los Tulipanes', 'Urb. Maranga'),
    ('Maranga', 'Calle Las Azucenas', 'Urb. Los Jardines de Maranga'), ('Maranga', 'Av. Federico Gallese', 'Urb. Maranga'),
    ('Maranga', 'Jr. Los Mirtos', 'Urb. Los Jardines de Maranga'),
    ('Pando', 'Av. Venezuela', 'Urb. Pando'), ('Pando', 'Calle Los Geranios', 'Urb. Pando'),
    ('Pando', 'Calle Los Claveles', 'Urb. Pando'), ('Pando', 'Calle Las Magnolias', 'Urb. Pando'),
    ('Pando', 'Av. Universitaria', 'Urb. Pando'), ('Pando', 'Calle Los Lirios', 'Urb. Pando'),
    ('Las Leyendas', 'Calle Las Palmeras', 'Urb. Las Leyendas'), ('Las Leyendas', 'Calle Los Robles', 'Urb. Las Leyendas'),
    ('Las Leyendas', 'Calle Los Olivos', 'Urb. Las Leyendas'), ('Las Leyendas', 'Calle Los Cipreses', 'Urb. Brígida Silva de Ochoa'),
    ('Las Leyendas', 'Calle Los Álamos', 'Urb. Brígida Silva de Ochoa'),
    ('Faucett - Libertad', 'Av. Elmer Faucett', 'Urb. Elmer Faucett'), ('Faucett - Libertad', 'Av. La Libertad', 'Urb. Libertad'),
    ('Faucett - Libertad', 'Jr. Ayacucho', 'Urb. Libertad'), ('Faucett - Libertad', 'Calle Huancayo', 'Urb. Elmer Faucett')
) AS v (zona, via, urbanizacion);

INSERT INTO samnet.contribuyente
    (codigo, nombre, tipo_persona, tipo_documento, numero_documento, domicilio_fiscal, estado, regimen, condicion,
     correo, telefonos, dom_departamento, dom_provincia, dom_distrito, dom_via, dom_numero, dom_interior)
SELECT p.codigo, p.nombre, CASE WHEN p.juridica THEN 'JURIDICA' ELSE 'NATURAL' END,
       CASE WHEN p.juridica THEN 'RUC' ELSE 'DNI' END, p.documento,
       d.via || ' ' || d.numero || coalesce(' Dpto. ' || d.interior, '') || ', ' || d.urbanizacion || ', San Miguel',
       CASE WHEN pg_temp.azar('baja' || p.i) < 0.015 THEN 'INACTIVO' ELSE 'ACTIVO' END,
       CASE WHEN NOT p.juridica AND pg_temp.azar('pension' || p.i) < 0.04 THEN 'PENSIONISTA' ELSE 'GENERAL' END,
       CASE WHEN pg_temp.azar('habido' || p.i) < 0.02 THEN 'NO HABIDO' ELSE 'HABIDO' END,
       CASE WHEN pg_temp.azar('correo' || p.i) < 0.78 THEN
            CASE WHEN p.juridica THEN pg_temp.elegir(ARRAY['contabilidad', 'administracion', 'gerencia', 'ventas'],
                     'buzon' || p.i) || '@' || lower(translate(split_part(p.nombre, ' ', 1),
                     'ÁÉÍÓÚÑ', 'AEIOUN')) || (p.i % 97) || '.com.pe'
                 ELSE lower(translate(left(p.nombre1, 1) || p.ap1 || '.' || p.ap2, 'ÁÉÍÓÚÑ ', 'AEIOUN'))
                     || CASE WHEN p.i % 3 = 0 THEN (p.i % 89)::TEXT ELSE '' END || '@'
                     || pg_temp.elegir(ARRAY['gmail.com', 'gmail.com', 'hotmail.com', 'outlook.com', 'yahoo.es'],
                                       'dominio' || p.i) END END,
       CASE WHEN p.juridica THEN '01 ' || (4500000 + (p.i * 37) % 499999)::TEXT
            ELSE '9' || lpad(((p.i::BIGINT * 7393) % 99999999)::TEXT, 8, '0') END,
       'LIMA', 'LIMA', 'SAN MIGUEL', d.via, d.numero, d.interior
FROM padron p
CROSS JOIN LATERAL (
    SELECT v.via, v.urbanizacion, (100 + floor(pg_temp.azar('num' || p.i) * 2900))::INTEGER::TEXT AS numero,
           CASE WHEN NOT p.juridica AND pg_temp.azar('dpto' || p.i) < 0.35
                THEN (100 * (1 + floor(pg_temp.azar('piso' || p.i) * 9)) + 1 + floor(pg_temp.azar('dp' || p.i) * 4))::TEXT
           END AS interior
    FROM via v WHERE v.zona = p.zona
    ORDER BY pg_temp.azar('via' || p.i || v.via) LIMIT 1
) d
ON CONFLICT DO NOTHING;

-- 4. Predios: uno a tres por contribuyente, en su zona o en otra del distrito; uso según el tipo de contribuyente.
CREATE TEMPORARY TABLE predio_nuevo ON COMMIT DROP AS
SELECT p.i, p.codigo, k, p.codigo || '-0' || k AS codigo_predio,
       CASE WHEN k = 1 THEN p.zona ELSE pg_temp.elegir(ARRAY['Costanera', 'Maranga', 'Pando', 'Las Leyendas',
            'Faucett - Libertad'], 'zp' || p.i || k) END AS zona,
       CASE WHEN p.juridica THEN pg_temp.elegir(ARRAY['Comercio', 'Comercio', 'Oficina', 'Oficina', 'Servicios',
                'Educación', 'Industria', 'Almacén'], 'uso' || p.i || k)
            WHEN k = 1 THEN CASE WHEN pg_temp.azar('uso' || p.i) < 0.93 THEN 'Vivienda' ELSE 'Comercio' END
            ELSE pg_temp.elegir(ARRAY['Vivienda', 'Vivienda', 'Estacionamiento', 'Comercio', 'Depósito',
                'Terreno sin construir'], 'uso' || p.i || k) END AS uso,
       make_date(1978 + floor(pg_temp.azar('adq' || p.i || k) * 43)::INTEGER,
                 1 + floor(pg_temp.azar('mes' || p.i || k) * 12)::INTEGER,
                 1 + floor(pg_temp.azar('dia' || p.i || k) * 27)::INTEGER) AS fecha_adquisicion,
       p.juridica
FROM padron p
CROSS JOIN LATERAL generate_series(1, CASE WHEN pg_temp.azar('predios' || p.i) < 0.68 THEN 1
                                           WHEN pg_temp.azar('predios' || p.i) < 0.93 THEN 2 ELSE 3 END) AS k
WHERE EXISTS (SELECT 1 FROM samnet.contribuyente c WHERE c.codigo = p.codigo);

INSERT INTO samnet.predio
    (codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno, base_imponible,
     fecha_adquisicion, zona)
SELECT n.codigo_predio,
       '150136' || lpad((n.i * 3 + n.k)::TEXT, 6, '0') || lpad((n.k * 7)::TEXT, 3, '0'),
       d.via || ' ' || d.numero || CASE WHEN n.uso = 'Estacionamiento' THEN ' Est. ' || (n.k * 11)
                                        WHEN n.uso = 'Vivienda' AND pg_temp.azar('dpto' || n.i || n.k) < 0.4
                                        THEN ' Dpto. ' || (100 * (1 + floor(pg_temp.azar('pp' || n.i) * 8))::INTEGER + n.k)
                                        ELSE '' END || ', ' || d.urbanizacion,
       n.uso, CASE WHEN n.uso = 'Terreno sin construir' AND pg_temp.azar('rus' || n.i) < 0.3 THEN 'OTRO' ELSE 'URBANO' END,
       a.area,
       round(a.area * CASE n.zona WHEN 'Costanera' THEN 2650 WHEN 'Maranga' THEN 2350 WHEN 'Las Leyendas' THEN 2150
                                  WHEN 'Pando' THEN 1950 ELSE 1750 END
                    * CASE n.uso WHEN 'Comercio' THEN 1.35 WHEN 'Oficina' THEN 1.30 WHEN 'Industria' THEN 0.85
                                 WHEN 'Terreno sin construir' THEN 0.55 WHEN 'Estacionamiento' THEN 0.60 ELSE 1 END
                    * (0.8 + pg_temp.azar('valor' || n.codigo_predio) * 0.45), 2),
       n.fecha_adquisicion, n.zona
FROM predio_nuevo n
CROSS JOIN LATERAL (
    SELECT v.via, v.urbanizacion, (100 + floor(pg_temp.azar('pn' || n.codigo_predio) * 2900))::INTEGER::TEXT AS numero
    FROM via v WHERE v.zona = n.zona
    ORDER BY pg_temp.azar('pv' || n.codigo_predio || v.via) LIMIT 1
) d
CROSS JOIN LATERAL (
    SELECT round((CASE n.uso WHEN 'Vivienda' THEN 75 + pg_temp.azar('ar' || n.codigo_predio) * 185
                             WHEN 'Estacionamiento' THEN 12 + pg_temp.azar('ar' || n.codigo_predio) * 6
                             WHEN 'Industria' THEN 300 + pg_temp.azar('ar' || n.codigo_predio) * 900
                             WHEN 'Almacén' THEN 200 + pg_temp.azar('ar' || n.codigo_predio) * 500
                             WHEN 'Terreno sin construir' THEN 120 + pg_temp.azar('ar' || n.codigo_predio) * 380
                             ELSE 60 + pg_temp.azar('ar' || n.codigo_predio) * 340 END)::NUMERIC, 2) AS area
) a
ON CONFLICT DO NOTHING;

INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad, porcentaje_propiedad)
SELECT codigo_predio, codigo, 'PROPIETARIO', 100 FROM predio_nuevo
ON CONFLICT DO NOTHING;

INSERT INTO samnet.declaracion_jurada
    (id_declaracion, codigo_predio, anio, autoavaluo, motivo, submotivo, tipo_declarante, estado, es_vigente)
SELECT 'DJ' || EXTRACT(YEAR FROM n.fecha_adquisicion) || '-' || replace(n.codigo_predio, '-', ''), n.codigo_predio,
       EXTRACT(YEAR FROM n.fecha_adquisicion)::INTEGER, p.base_imponible, 'INSCRIPCION',
       pg_temp.elegir(ARRAY['COMPRA-VENTA', 'COMPRA-VENTA', 'COMPRA-VENTA', 'HERENCIA', 'DONACIÓN'], 'sub' || n.codigo_predio),
       CASE WHEN n.juridica THEN 'REPRESENTANTE LEGAL' ELSE 'PROPIETARIO' END, 'PROCESADA', TRUE
FROM predio_nuevo n
JOIN samnet.predio p ON p.codigo_predio = n.codigo_predio
ON CONFLICT DO NOTHING;

-- 5. Cuotas de 2022 a 2026. Impuesto Predial por contribuyente (escala de la Ley de Tributación Municipal en 4 cuotas)
-- y arbitrios por predio y mes como monto único: tarifa de la categoría y zona; fuera de vivienda crece con el área.
-- Los contribuyentes de R__datos_prueba_01 reciben aquí su historia de 2022 a 2024 (2025 y 2026 son de ese archivo).
CREATE TEMPORARY TABLE contribuyente_historia ON COMMIT DROP AS
SELECT p.codigo, p.perfil, p.moroso_desde, 2022 AS desde, 2026 AS hasta FROM padron p
UNION ALL
SELECT v.codigo, v.perfil, NULL, 2022, 2024
FROM (VALUES ('014502', 'ANUAL'), ('033218', 'ANUAL'), ('058934', 'PUNTUAL'), ('061177', 'PUNTUAL'),
             ('080245', 'PUNTUAL'), ('045067', 'ATRASADO'), ('021390', 'ATRASADO'), ('072405', 'IRREGULAR'))
     AS v (codigo, perfil)
WHERE EXISTS (SELECT 1 FROM samnet.contribuyente c WHERE c.codigo = v.codigo);

WITH base AS (
    SELECT t.contribuyente_codigo, a.anio, u.valor AS uit, c.regimen,
           sum(p.base_imponible * t.porcentaje_propiedad / 100) AS base_imponible
    FROM samnet.predio_titular t
    JOIN samnet.predio p ON p.codigo_predio = t.codigo_predio
    JOIN samnet.contribuyente c ON c.codigo = t.contribuyente_codigo
    JOIN contribuyente_historia h ON h.codigo = t.contribuyente_codigo
    CROSS JOIN LATERAL generate_series(h.desde, h.hasta) AS a (anio)
    JOIN samnet.uit u ON u.anio = a.anio
    WHERE coalesce(p.fecha_adquisicion, DATE '1900-01-01') < make_date(a.anio, 1, 1) AND p.fecha_baja IS NULL
    GROUP BY t.contribuyente_codigo, a.anio, u.valor, c.regimen
),
afecta AS (
    SELECT b.*, CASE WHEN b.regimen = 'PENSIONISTA' THEN greatest(b.base_imponible - 50 * b.uit, 0)
                     ELSE b.base_imponible END AS base_afecta
    FROM base b
),
impuesto AS (
    SELECT f.contribuyente_codigo, f.anio,
           CASE WHEN f.regimen = 'PENSIONISTA' AND f.base_afecta = 0 THEN 0
                ELSE greatest(round(least(f.base_afecta, 15 * f.uit) * 0.002
                                    + least(greatest(f.base_afecta - 15 * f.uit, 0), 45 * f.uit) * 0.006
                                    + greatest(f.base_afecta - 60 * f.uit, 0) * 0.010, 2),
                              CASE WHEN f.regimen = 'PENSIONISTA' THEN 0 ELSE round(f.uit * 0.006, 2) END)
           END AS anual
    FROM afecta f
)
INSERT INTO samnet.obligacion
    (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido, monto_pagado)
SELECT i.contribuyente_codigo, NULL, 'IMPUESTO_PREDIAL', i.anio, c.cuota, samnet.fn_ultimo_dia_habil(i.anio, c.cuota * 3 - 1),
       CASE WHEN c.cuota = 1 THEN i.anual - 3 * round(i.anual / 4, 2) ELSE round(i.anual / 4, 2) END, 0
FROM impuesto i
CROSS JOIN generate_series(1, 4) AS c (cuota)
WHERE i.anual > 0
ORDER BY i.contribuyente_codigo, i.anio, c.cuota;

INSERT INTO samnet.obligacion
    (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido, monto_pagado)
SELECT t.contribuyente_codigo, p.codigo_predio, 'ARBITRIOS', a.anio, m.mes,
       samnet.fn_ultimo_dia_habil(a.anio, greatest(m.mes, 2)),
       round(ta.monto_mensual * CASE WHEN cat.categoria <> 'CASA_HABITACION'
                                     THEN 1 + 0.4 * (greatest(1, p.area_terreno / 200) - 1) ELSE 1 END
             * t.porcentaje_propiedad / 100, 2),
       0
FROM samnet.predio_titular t
JOIN samnet.predio p ON p.codigo_predio = t.codigo_predio
JOIN contribuyente_historia h ON h.codigo = t.contribuyente_codigo
CROSS JOIN LATERAL (SELECT CASE WHEN p.uso_predio = 'Vivienda' THEN 'CASA_HABITACION'
                                WHEN p.uso_predio IN ('Comercio', 'Estacionamiento') THEN 'COMERCIO'
                                WHEN p.uso_predio IN ('Industria', 'Almacén', 'Depósito') THEN 'INDUSTRIA'
                                WHEN p.uso_predio = 'Terreno sin construir' THEN 'TERRENO_SIN_CONSTRUIR'
                                ELSE 'SERVICIOS' END AS categoria) cat
CROSS JOIN LATERAL generate_series(h.desde, h.hasta) AS a (anio)
CROSS JOIN generate_series(1, 12) AS m (mes)
JOIN samnet.tasa_arbitrio ta ON ta.anio = a.anio AND ta.categoria_uso = cat.categoria AND ta.zona = p.zona
WHERE make_date(a.anio, m.mes, 1) > coalesce(p.fecha_adquisicion, DATE '1900-01-01') AND p.fecha_baja IS NULL
ORDER BY t.contribuyente_codigo, a.anio, m.mes, p.codigo_predio;

-- 6. Pagos según el perfil, hasta la fecha de los datos (13/11/2026). h varía cada cuota de forma estable.
CREATE TEMPORARY TABLE plan_pago ON COMMIT DROP AS
WITH cuota AS (
    SELECT o.id, o.contribuyente_codigo, o.tributo, o.anio, o.numero_cuota, o.fecha_vencimiento, o.monto_emitido,
           h.perfil, h.moroso_desde,
           abs(hashtext(o.contribuyente_codigo || coalesce(o.codigo_predio, '') || o.tributo || o.anio || '-'
                        || o.numero_cuota)::BIGINT) AS h
    FROM samnet.obligacion o
    JOIN contribuyente_historia h ON h.codigo = o.contribuyente_codigo AND o.anio BETWEEN h.desde AND h.hasta
),
fecha AS (
    SELECT c.*,
           CASE c.perfil
               WHEN 'ANUAL' THEN make_date(c.anio, 2, 10 + (c.h % 15)::INTEGER)
               WHEN 'PUNTUAL' THEN CASE WHEN c.h % 100 < 4 THEN c.fecha_vencimiento + 25 + (c.h % 50)::INTEGER
                                        ELSE c.fecha_vencimiento - (c.h % 9)::INTEGER END
               WHEN 'ATRASADO' THEN CASE WHEN c.h % 100 < 14 THEN c.fecha_vencimiento + 120 + (c.h % 220)::INTEGER
                                         ELSE c.fecha_vencimiento + 6 + (c.h % 45)::INTEGER END
               WHEN 'RECUPERA' THEN CASE WHEN (c.tributo = 'IMPUESTO_PREDIAL' AND c.numero_cuota >= 3)
                                              OR (c.tributo = 'ARBITRIOS' AND c.numero_cuota >= 8)
                                         THEN make_date(c.anio + 1, 3, 5) + (c.h % 40)::INTEGER
                                         ELSE c.fecha_vencimiento - (c.h % 5)::INTEGER END
               WHEN 'PARCIAL' THEN CASE WHEN c.tributo = 'IMPUESTO_PREDIAL' THEN c.fecha_vencimiento - (c.h % 4)::INTEGER
                                        WHEN c.numero_cuota <= 6 THEN c.fecha_vencimiento + (c.h % 12)::INTEGER END
               WHEN 'IRREGULAR' THEN CASE WHEN c.h % 100 < 62 THEN c.fecha_vencimiento + 15 + (c.h % 260)::INTEGER END
               WHEN 'MOROSO' THEN CASE WHEN c.anio < c.moroso_desde AND c.h % 10 <> 0
                                       THEN c.fecha_vencimiento + 5 + (c.h % 35)::INTEGER END
           END AS fecha_pago
    FROM cuota c
)
SELECT f.id, f.contribuyente_codigo, f.fecha_pago, f.monto_emitido AS monto, f.h
FROM fecha f
WHERE f.fecha_pago IS NOT NULL AND f.fecha_pago <= DATE '2026-11-13';

UPDATE samnet.obligacion o SET monto_pagado = p.monto FROM plan_pago p WHERE p.id = o.id;

-- Un recibo por contribuyente y día; el pago en línea crece año a año.
INSERT INTO samnet.pago (id_pago, contribuyente_codigo, fecha_pago, canal, monto_pagado, monto_correspondiente)
SELECT 'RV-' || p.contribuyente_codigo || '-' || to_char(p.fecha_pago, 'YYYYMMDD'), p.contribuyente_codigo, p.fecha_pago,
       CASE WHEN pg_temp.azar('canal' || p.contribuyente_codigo || p.fecha_pago)
                 < 0.22 + 0.09 * (EXTRACT(YEAR FROM p.fecha_pago) - 2022) THEN 'EN_LINEA' ELSE 'VENTANILLA' END,
       sum(p.monto), sum(p.monto)
FROM plan_pago p
GROUP BY p.contribuyente_codigo, p.fecha_pago
ON CONFLICT (id_pago) DO NOTHING;

INSERT INTO samnet.pago_obligacion (id_pago, id_obligacion, monto_aplicado)
SELECT 'RV-' || p.contribuyente_codigo || '-' || to_char(p.fecha_pago, 'YYYYMMDD'), p.id, p.monto
FROM plan_pago p
ON CONFLICT DO NOTHING;

-- 7. Cobranza de lo impago: carta de la campaña a los 40 días, valor (OP del predial, RD de arbitrios) a los 80,
-- espera legal a los 94 y vía coactiva a los 125, hasta la fecha de los datos. Los arbitrios se cobran juntos por
-- año, así que la etapa se registra en la cuota de diciembre del predio (o la última emitida).
WITH impaga AS (
    SELECT o.id, o.tributo, o.fecha_vencimiento, row_number() OVER (ORDER BY o.fecha_vencimiento, o.id) AS correlativo
    FROM samnet.obligacion o
    JOIN contribuyente_historia h ON h.codigo = o.contribuyente_codigo AND o.anio BETWEEN h.desde AND h.hasta
    WHERE o.monto_pagado < o.monto_emitido AND o.fecha_vencimiento + 40 <= DATE '2026-11-13'
      AND (o.tributo = 'IMPUESTO_PREDIAL' OR o.numero_cuota IN (6, 12))
)
INSERT INTO samnet.seguimiento_cobranza (obligacion_id, etapa, fecha_inicio, documento_referencia, observacion, fecha_registro)
SELECT i.id, e.etapa, i.fecha_vencimiento + e.dias,
       CASE WHEN e.etapa = 'CAMPANA' THEN NULL
            ELSE CASE i.tributo WHEN 'IMPUESTO_PREDIAL' THEN 'OP-' ELSE 'RD-' END
                 || EXTRACT(YEAR FROM i.fecha_vencimiento + 80) || '-' || lpad((20000 + i.correlativo)::TEXT, 7, '0') END,
       e.observacion, (i.fecha_vencimiento + e.dias) + TIME '09:30'
FROM impaga i
CROSS JOIN (VALUES ('CAMPANA', 40, 'Carta inductiva de la campaña de cobranza'),
                   ('VALOR_EMITIDO', 80, 'Valor notificado en el domicilio fiscal'),
                   ('ESPERA_LEGAL', 94, 'Plazo de pago o reclamación en curso'),
                   ('COACTIVA', 125, 'Venció la espera legal sin pago ni reclamación')) AS e (etapa, dias, observacion)
WHERE i.fecha_vencimiento + e.dias <= DATE '2026-11-13'
ON CONFLICT DO NOTHING;

-- 8. Accesos a Servicios en Línea de una muestra del padrón: clave inicial SanMiguel + código (p. ej. SanMiguel200001).
INSERT INTO samnet.credencial_web (contribuyente_codigo, clave_hash, habilitada)
SELECT p.codigo, public.crypt('SanMiguel' || p.codigo, public.gen_salt('bf', 8)), TRUE
FROM padron p
JOIN samnet.contribuyente c ON c.codigo = p.codigo AND c.estado = 'ACTIVO'
WHERE p.i <= 60
ON CONFLICT (contribuyente_codigo) DO NOTHING;

-- 9. Trámites del último año y medio en todos sus estados. Quienes los atienden son el personal de
-- R__zz_padron_09_personal_municipal (ventanilla, gestión de reclamos y áreas que responden).
CREATE TEMPORARY TABLE personal ON COMMIT DROP AS
SELECT r.nombre AS rol, u.id_usuario, row_number() OVER (PARTITION BY r.nombre ORDER BY u.id_usuario) AS n
FROM seguridad.usuario u
JOIN seguridad.usuario_rol ur ON ur.id_usuario = u.id_usuario
JOIN seguridad.rol r ON r.id_rol = ur.id_rol
WHERE u.activo;

CREATE OR REPLACE FUNCTION pg_temp.personal(p_rol TEXT, p_clave TEXT) RETURNS BIGINT
LANGUAGE sql STABLE AS $$
    SELECT id_usuario FROM personal
    WHERE rol = p_rol
      AND n = 1 + floor(pg_temp.azar(p_clave) * (SELECT count(*) FROM personal WHERE rol = p_rol))::INTEGER;
$$;

-- Deuda vencida de cada contribuyente del padrón a la fecha de los datos (decide el resultado de su trámite).
CREATE TEMPORARY TABLE deuda_padron ON COMMIT DROP AS
SELECT p.i, p.codigo, p.documento, p.perfil, p.nombre,
       coalesce(sum(o.monto_emitido - o.monto_pagado - o.monto_descontado)
                FILTER (WHERE o.fecha_vencimiento < DATE '2026-11-13'), 0) AS deuda
FROM padron p
JOIN samnet.contribuyente c ON c.codigo = p.codigo
LEFT JOIN samnet.obligacion o ON o.contribuyente_codigo = p.codigo
GROUP BY p.i, p.codigo, p.documento, p.perfil, p.nombre;

DO $tramites$
DECLARE
    v_version   BIGINT;
    v_fila      RECORD;
    v_id        BIGINT;
    v_numero    VARCHAR(20);
    v_fecha     TIMESTAMP;
    v_estado    TEXT;
    v_operador  BIGINT;
    v_area      BIGINT;
    v_resp      BIGINT;
    v_gestor    BIGINT;
    v_texto     TEXT;
BEGIN
    SELECT v.id_version INTO v_version
    FROM operativo.version_plantilla v
    JOIN operativo.plantilla_documento p ON p.id_plantilla = v.id_plantilla
    WHERE p.codigo = 'CONSTANCIA_NO_ADEUDO' AND v.vigente;

    -- 9.1 Constancias de No Adeudo: uno de cada siete contribuyentes la pidió alguna vez.
    FOR v_fila IN
        SELECT d.*, TIMESTAMP '2025-05-02 08:00'
                    + make_interval(days => floor(pg_temp.azar('fsc' || d.i) * 555)::INTEGER,
                                    mins => floor(pg_temp.azar('hsc' || d.i) * 540)::INTEGER) AS fecha,
               pg_temp.azar('esc' || d.i) AS r
        FROM deuda_padron d
        WHERE pg_temp.azar('pide' || d.i) < 0.14
        ORDER BY d.i
    LOOP
        v_fecha := least(v_fila.fecha, TIMESTAMP '2026-11-13 17:30');
        v_estado := CASE
            WHEN v_fila.deuda > 0 THEN CASE WHEN v_fila.r < 0.85 THEN 'RECHAZADA' ELSE 'INICIADA' END
            WHEN v_fecha > TIMESTAMP '2026-11-01' THEN pg_temp.elegir(ARRAY['INICIADA', 'FIRMADA', 'DEUDA_VERIFICADA',
                'ORDEN_PAGO_EMITIDA', 'PAGADA'], 'reciente' || v_fila.i)
            WHEN v_fila.r < 0.06 THEN 'FIRMADA'
            WHEN v_fila.r < 0.10 THEN 'ORDEN_PAGO_EMITIDA'
            ELSE 'EMITIDA' END;
        v_operador := pg_temp.personal('VENTANILLA', 'op' || v_fila.i);
        INSERT INTO operativo.solicitud_constancia
            (canal, estado, fecha_solicitud, documento_contribuyente, motivo_rechazo, id_trabajador, id_operador,
             fecha_revision, deuda_presentacion, fecha_deuda_presentacion)
        VALUES ('APP', v_estado, v_fecha, v_fila.documento,
                CASE WHEN v_estado = 'RECHAZADA'
                     THEN 'El contribuyente tiene deuda tributaria vencida de S/ '
                          || to_char(v_fila.deuda, 'FM999G990D00') || '. Regularice el pago para solicitar la constancia.'
                END,
                NULL,
                CASE WHEN v_estado NOT IN ('INICIADA', 'FIRMADA') THEN v_operador END,
                CASE WHEN v_estado NOT IN ('INICIADA', 'FIRMADA') THEN v_fecha + INTERVAL '3 hours' END,
                v_fila.deuda, v_fecha)
        RETURNING id_solicitud INTO v_id;
        -- Tres de cada diez se presentaron en ventanilla: llevan el trabajador que las registró.
        IF pg_temp.azar('canal' || v_fila.i) >= 0.7 THEN
            UPDATE operativo.solicitud_constancia SET canal = 'VENTANILLA', id_trabajador = v_operador
            WHERE id_solicitud = v_id;
        END IF;

        IF v_estado <> 'INICIADA' AND NOT (v_estado = 'RECHAZADA' AND v_fila.r < 0.3) THEN
            INSERT INTO operativo.firma_digital
                (id_solicitud, tipo_firmante, entidad_certificadora, fecha_firma, valida, hash_documento)
            VALUES (v_id, 'CONTRIBUYENTE', 'RENIEC - Documento Nacional de Identidad electrónico',
                    v_fecha + INTERVAL '4 minutes', TRUE, encode(sha256(convert_to('solicitud-' || v_id, 'UTF8')), 'hex'));
        END IF;
        IF v_estado IN ('ORDEN_PAGO_EMITIDA', 'PAGADA', 'EMITIDA') THEN
            INSERT INTO operativo.transaccion_pago
                (documento_contribuyente, motivo, monto_base, monto_descuento, monto_total, estado, id_orden_pasarela,
                 fecha_hora, comprobante, id_solicitud_constancia)
            VALUES (v_fila.documento, 'DERECHO_TRAMITE', 10.00, 0, 10.00,
                    CASE WHEN v_estado = 'ORDEN_PAGO_EMITIDA' THEN 'INICIADA' ELSE 'CONFIRMADA' END,
                    'NBZ-CND-' || v_id, v_fecha + INTERVAL '3 hours 10 minutes',
                    CASE WHEN v_estado <> 'ORDEN_PAGO_EMITIDA' THEN 'B001-' || lpad((40000 + v_id)::TEXT, 8, '0') END,
                    v_id);
        END IF;
        IF v_estado = 'EMITIDA' THEN
            v_numero := operativo.fn_siguiente_numero_constancia(v_fecha::DATE);
            INSERT INTO operativo.firma_digital
                (id_solicitud, tipo_firmante, entidad_certificadora, fecha_firma, valida, hash_documento)
            VALUES (v_id, 'MUNICIPALIDAD', 'Municipalidad Distrital de San Miguel - Firma digital institucional',
                    v_fecha + INTERVAL '3 hours 20 minutes', TRUE, encode(sha256(convert_to(v_numero, 'UTF8')), 'hex'));
            INSERT INTO operativo.constancia
                (numero_constancia, id_solicitud, id_version_plantilla, fecha_emision, predios_incluidos, rango_fechas,
                 archivo_pdf, firma_local, id_trabajador_emisor)
            VALUES (v_numero, v_id, v_version, v_fecha + INTERVAL '3 hours 20 minutes',
                    'Todos los predios del contribuyente', 'Hasta la fecha de emisión', v_numero || '.pdf', FALSE,
                    CASE WHEN pg_temp.azar('auto' || v_id) < 0.6 THEN NULL ELSE v_operador END);
            INSERT INTO operativo.documento_buzon
                (documento_contribuyente, tipo, titulo, archivo, fecha_envio, fecha_primera_lectura, numero_constancia,
                 leido_actualmente, contenido)
            VALUES (v_fila.documento, 'CONSTANCIA', 'Constancia de No Adeudo ' || v_numero, v_numero || '.pdf',
                    v_fecha + INTERVAL '3 hours 21 minutes',
                    CASE WHEN pg_temp.azar('leyo' || v_id) < 0.75 THEN v_fecha + INTERVAL '1 day' END, v_numero,
                    pg_temp.azar('leyo' || v_id) < 0.75,
                    'Su Constancia de No Adeudo fue emitida y firmada digitalmente por la Municipalidad.');
        END IF;
    END LOOP;

    -- 9.2 Solicitudes de registro y traspaso de predio.
    FOR v_fila IN
        SELECT d.*, TIMESTAMP '2025-06-02 09:00'
                    + make_interval(days => floor(pg_temp.azar('fsp' || d.i) * 525)::INTEGER,
                                    mins => floor(pg_temp.azar('hsp' || d.i) * 480)::INTEGER) AS fecha,
               pg_temp.azar('esp' || d.i) AS r,
               (SELECT t.codigo_predio FROM samnet.predio_titular t WHERE t.contribuyente_codigo = d.codigo
                ORDER BY t.codigo_predio LIMIT 1) AS predio
        FROM deuda_padron d
        WHERE pg_temp.azar('tramita' || d.i) < 0.08
        ORDER BY d.i
    LOOP
        v_fecha := least(v_fila.fecha, TIMESTAMP '2026-11-13 16:00');
        v_estado := CASE WHEN v_fecha > TIMESTAMP '2026-10-20' OR v_fila.r < 0.22 THEN 'EN_REVISION'
                         WHEN v_fila.r < 0.75 THEN 'ACEPTADA' ELSE 'RECHAZADA' END;
        INSERT INTO operativo.solicitud_predio
            (tipo_tramite, tipo_predio, documento_contribuyente, codigo_predio, estado, fecha_envio, firma_electronica,
             motivo_rechazo, id_revisor, fecha_revision, deuda_predio)
        VALUES (CASE WHEN pg_temp.azar('tt' || v_fila.i) < 0.55 THEN 'TRASPASO' ELSE 'REGISTRO' END, 'URBANO',
                v_fila.documento, v_fila.predio, v_estado, v_fecha, TRUE,
                CASE WHEN v_estado = 'RECHAZADA' THEN pg_temp.elegir(ARRAY[
                    'El documento de transferencia no está legalizado por notario.',
                    'La minuta adjunta no coincide con el predio indicado en la solicitud.',
                    'Falta la copia del documento de identidad del adquirente.',
                    'La declaración jurada de autoavalúo no está firmada.'], 'motivo' || v_fila.i) END,
                CASE WHEN v_estado <> 'EN_REVISION' THEN pg_temp.personal('VENTANILLA', 'rev' || v_fila.i) END,
                CASE WHEN v_estado <> 'EN_REVISION' THEN v_fecha + INTERVAL '2 days 3 hours' END,
                CASE WHEN v_fila.deuda > 0 THEN round(v_fila.deuda * 0.3, 2) ELSE 0 END)
        RETURNING id_solicitud INTO v_id;
        IF v_estado <> 'EN_REVISION' THEN
            INSERT INTO operativo.documento_buzon
                (documento_contribuyente, tipo, titulo, fecha_envio, fecha_primera_lectura, id_solicitud_predio,
                 leido_actualmente, contenido)
            VALUES (v_fila.documento, 'RESPUESTA_SOLICITUD', 'Respuesta a su solicitud de predio N.° ' || v_id,
                    v_fecha + INTERVAL '2 days 3 hours',
                    CASE WHEN pg_temp.azar('lsp' || v_id) < 0.7 THEN v_fecha + INTERVAL '3 days' END, v_id,
                    pg_temp.azar('lsp' || v_id) < 0.7,
                    CASE WHEN v_estado = 'ACEPTADA'
                         THEN 'Su solicitud fue aceptada y el predio quedó actualizado en el padrón.'
                         ELSE 'Su solicitud fue observada. Revise el motivo y preséntela de nuevo con los documentos corregidos.'
                    END);
        END IF;
    END LOOP;

    -- 9.3 Reclamos de contribuyentes, derivados al área que corresponde y respondidos.
    FOR v_fila IN
        SELECT d.*, TIMESTAMP '2025-04-01 08:30'
                    + make_interval(days => floor(pg_temp.azar('frc' || d.i) * 590)::INTEGER,
                                    mins => floor(pg_temp.azar('hrc' || d.i) * 560)::INTEGER) AS fecha,
               pg_temp.azar('erc' || d.i) AS r,
               pg_temp.elegir(ARRAY['COBRANZA', 'COBRANZA', 'SERVICIO', 'ATENCION', 'OTRO'], 'trc' || d.i) AS tipo
        FROM deuda_padron d
        WHERE pg_temp.azar('reclama' || d.i) < 0.12
          AND NOT EXISTS (SELECT 1 FROM operativo.reclamo r WHERE r.documento_contribuyente = d.documento)
        ORDER BY d.i
    LOOP
        v_fecha := least(v_fila.fecha, TIMESTAMP '2026-11-13 15:00');
        v_estado := CASE WHEN v_fecha > TIMESTAMP '2026-11-08' OR v_fila.r < 0.18 THEN 'ENVIADO'
                         WHEN v_fila.r < 0.42 THEN 'EN_REVISION'
                         WHEN v_fila.r < 0.92 THEN 'APROBADO_CON_RESPUESTA' ELSE 'CANCELADO' END;
        v_texto := CASE v_fila.tipo
            WHEN 'COBRANZA' THEN pg_temp.elegir(ARRAY[
                'Recibí una orden de pago por una cuota del Impuesto Predial que ya pagué en ventanilla. Solicito verificar el recibo.',
                'El estado de cuenta muestra arbitrios de un predio que vendí el año pasado. Solicito que se actualice el titular.',
                'Se me notificó una carta de cobranza con un monto mayor al de mi estado de cuenta en línea. Solicito la aclaración del interés moratorio.',
                'Pagué en línea y el pago no aparece aplicado a mis cuotas vencidas.'], 'crc' || v_fila.i)
            WHEN 'SERVICIO' THEN pg_temp.elegir(ARRAY[
                'El recojo de residuos en mi calle no se realiza en el horario publicado desde hace dos semanas.',
                'El parque frente a mi vivienda no recibe mantenimiento y el riego está suspendido.',
                'Solicito mayor presencia de serenazgo en la cuadra por robos recientes en la zona.'], 'crc' || v_fila.i)
            WHEN 'ATENCION' THEN pg_temp.elegir(ARRAY[
                'En ventanilla me indicaron requisitos distintos a los publicados para el traspaso de predio.',
                'Esperé más de una hora para ser atendido en la plataforma de Rentas.',
                'No recibí respuesta a mi solicitud presentada hace más de quince días.'], 'crc' || v_fila.i)
            ELSE pg_temp.elegir(ARRAY[
                'No puedo descargar mi constancia desde el buzón del portal.',
                'Solicito información sobre los beneficios tributarios vigentes para pensionistas.'], 'crc' || v_fila.i)
        END;
        v_area := (SELECT id_area FROM operativo.area_municipal
                   WHERE codigo = CASE v_fila.tipo WHEN 'COBRANZA' THEN 'REGISTRO_TRIBUTARIO' WHEN 'SERVICIO' THEN 'RENTAS'
                                       WHEN 'ATENCION' THEN 'ATENCION_CIUDADANO' ELSE 'TIC' END);
        v_resp := pg_temp.personal(CASE v_fila.tipo WHEN 'COBRANZA' THEN 'ANALISTA_RENTAS' WHEN 'ATENCION' THEN 'VENTANILLA'
                                        WHEN 'SERVICIO' THEN 'GERENCIA' ELSE 'TERMINALISTA' END, 'resp' || v_fila.i);
        v_gestor := pg_temp.personal('GESTOR_RECLAMOS', 'gest' || v_fila.i);
        INSERT INTO operativo.reclamo
            (numero_reclamo, tipo_reclamante, documento_contribuyente, tipo_reclamo, referencia, fecha_hora_incidente,
             comentario, estado, fecha_registro, id_area_actual, id_responsable_actual, respuesta, fecha_respuesta)
        VALUES (operativo.fn_siguiente_numero_reclamo(v_fecha::DATE), 'CONTRIBUYENTE', v_fila.documento, v_fila.tipo,
                CASE WHEN v_fila.tipo = 'COBRANZA' THEN 'Estado de cuenta ' || v_fila.codigo END,
                v_fecha - INTERVAL '2 days', v_texto, v_estado, v_fecha,
                CASE WHEN v_estado IN ('EN_REVISION', 'APROBADO_CON_RESPUESTA') THEN v_area END,
                CASE WHEN v_estado IN ('EN_REVISION', 'APROBADO_CON_RESPUESTA') THEN v_resp END,
                CASE WHEN v_estado = 'APROBADO_CON_RESPUESTA' THEN pg_temp.elegir(ARRAY[
                    'Se verificó su caso y se realizó la corrección solicitada. Puede consultar su estado de cuenta actualizado en Servicios en Línea.',
                    'Se coordinó con el área responsable y el servicio fue regularizado. Agradecemos su comunicación.',
                    'Se revisó la información y se confirma que el cobro es correcto; el detalle del cálculo figura en su estado de cuenta.'],
                    'rsp' || v_fila.i) END,
                CASE WHEN v_estado = 'APROBADO_CON_RESPUESTA' THEN v_fecha + INTERVAL '6 days 2 hours' END)
        RETURNING id_reclamo INTO v_id;
        IF v_estado IN ('EN_REVISION', 'APROBADO_CON_RESPUESTA') THEN
            INSERT INTO operativo.derivacion_reclamo
                (id_reclamo, id_area_destino, id_responsable, id_derivado_por, fecha_derivacion, observacion)
            VALUES (v_id, v_area, v_resp, v_gestor, v_fecha + INTERVAL '1 day',
                    'Derivado para su atención según el tipo de reclamo.');
            INSERT INTO operativo.documento_buzon
                (documento_contribuyente, tipo, titulo, fecha_envio, fecha_primera_lectura, id_reclamo, leido_actualmente,
                 contenido)
            VALUES (v_fila.documento, 'ASIGNACION_RECLAMO', 'Su reclamo fue derivado para su atención',
                    v_fecha + INTERVAL '1 day', v_fecha + INTERVAL '2 days', v_id, TRUE,
                    'Su reclamo fue derivado al área responsable para su atención.');
        END IF;
        IF v_estado = 'APROBADO_CON_RESPUESTA' THEN
            INSERT INTO operativo.documento_buzon
                (documento_contribuyente, tipo, titulo, fecha_envio, fecha_primera_lectura, id_reclamo, leido_actualmente,
                 contenido)
            VALUES (v_fila.documento, 'RESPUESTA_RECLAMO', 'Respuesta a su reclamo', v_fecha + INTERVAL '6 days 2 hours',
                    CASE WHEN pg_temp.azar('lrc' || v_id) < 0.6 THEN v_fecha + INTERVAL '7 days' END, v_id,
                    pg_temp.azar('lrc' || v_id) < 0.6,
                    'La Municipalidad respondió su reclamo. Revise el detalle en Mis reclamos.');
        END IF;
    END LOOP;
END;
$tramites$;

-- 9.4 Avisos de mora temprana enviados por correo a quienes tienen cuotas del predial 2026 vencidas.
INSERT INTO operativo.aviso_correo
    (tipo, documento_contribuyente, correo_destino, fecha_programada, fecha_envio, estado, id_obligacion)
SELECT 'MORA_TEMPRANA', c.numero_documento, c.correo, o.fecha_vencimiento + 10 + TIME '08:00',
       o.fecha_vencimiento + 10 + TIME '08:02', 'ENVIADO', o.id::TEXT
FROM samnet.obligacion o
JOIN samnet.contribuyente c ON c.codigo = o.contribuyente_codigo
JOIN padron p ON p.codigo = c.codigo
WHERE o.anio = 2026 AND o.tributo = 'IMPUESTO_PREDIAL' AND o.monto_pagado < o.monto_emitido
  AND o.fecha_vencimiento + 10 <= DATE '2026-11-13' AND c.correo IS NOT NULL;

-- 10. Cargas del ETL (SAMNET → bronce → plata → modelo analítico) como si hubieran corrido la madrugada siguiente a
-- cada cierre: el del año anterior, los dos últimos meses de 2026 y la foto a la fecha de los datos.
DO $etl$
DECLARE
    v_corte DATE;
BEGIN
    FOR v_corte IN SELECT unnest(ARRAY[DATE '2025-12-31', DATE '2026-09-30', DATE '2026-10-31',
                                      DATE '2026-11-13']) LOOP
        PERFORM etl.ejecutar_lote(v_corte, 'PROGRAMADA', 'samnet-demo-contribuyente', 'samnet-demo-predio',
                                  (v_corte + 1) + TIME '02:00');
    END LOOP;
END;
$etl$;

ANALYZE samnet.obligacion;
ANALYZE samnet.pago;
ANALYZE samnet.pago_obligacion;
ANALYZE analitica.hecho_cobranza;
ANALYZE analitica.hecho_pago;
ANALYZE analitica.lote_etl;
