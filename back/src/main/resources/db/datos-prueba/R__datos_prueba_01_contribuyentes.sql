-- Datos ficticios de SAMNET sintético: contribuyentes con sus predios, titulares y declaraciones juradas.
-- Es una migración repetible: se vuelve a ejecutar si cambia su contenido, por eso usa ON CONFLICT.
-- Casos que cubre (auditoría 2026-11): un predio, varios predios, copropiedad al 50 %, predio adquirido a mitad
-- de año, pensionista con un solo predio de vivienda, contribuyente sin predios, una inmobiliaria con 31 predios
-- y una empresa (021390) con 11 predios de usos y zonas distintos, para probar los filtros de la tabla de predios.
-- Zonas: sectores del distrito de San Miguel (Lima) que agrupan urbanizaciones con la misma tarifa de arbitrios:
-- Costanera, Maranga y Pando aquí; Las Leyendas y Faucett - Libertad con los contribuyentes de datos-demo. Son
-- ilustrativas: reemplazarlas por la zonificación de la ordenanza de arbitrios vigente cuando se tenga.
INSERT INTO samnet.contribuyente
    (codigo, nombre, tipo_persona, tipo_documento, numero_documento, domicilio_fiscal, estado, regimen, condicion,
     correo, telefonos, dom_departamento, dom_provincia, dom_distrito, dom_via, dom_numero)
VALUES
    ('014502', 'GARCÍA TORRES, MARÍA ELENA', 'NATURAL', 'DNI', '41028765', 'Av. La Marina 1450, San Miguel', 'ACTIVO',
     'GENERAL', 'HABIDO', 'maria.garcia@correo.test', '987000101', 'LIMA', 'LIMA', 'SAN MIGUEL', 'Av. La Marina', '1450'),
    ('021390', 'INVERSIONES COSTA AZUL S.A.C.', 'JURIDICA', 'RUC', '20456781239', 'Jr. Los Precursores 220, San Miguel', 'ACTIVO',
     'GENERAL', 'HABIDO', 'cobranzas@costaazul.test', '014500202', 'LIMA', 'LIMA', 'SAN MIGUEL', 'Jr. Los Precursores', '220'),
    ('033218', 'RAMOS QUISPE, VÍCTOR HUGO', 'NATURAL', 'DNI', '09215430', 'Calle Bahía 88, San Miguel', 'ACTIVO',
     'PENSIONISTA', 'HABIDO', NULL, '987000303', 'LIMA', 'LIMA', 'SAN MIGUEL', 'Calle Bahía', '88'),
    ('045067', 'CENTRO EDUCATIVO SOL DE LIMA E.I.R.L.', 'JURIDICA', 'RUC', '20512309876', 'Av. Faro Azul 640, San Miguel', 'ACTIVO',
     'GENERAL', 'HABIDO', 'administracion@soldelima.test', '014500404', 'LIMA', 'LIMA', 'SAN MIGUEL', 'Av. Faro Azul', '640'),
    ('058934', 'MEDINA CRUZ, PATRICIA', 'NATURAL', 'DNI', '45871203', 'Jr. Mirador 340, San Miguel', 'ACTIVO',
     'GENERAL', 'HABIDO', 'patricia.medina@correo.test', '987000505', 'LIMA', 'LIMA', 'SAN MIGUEL', 'Jr. Mirador', '340'),
    ('061177', 'MEDINA CRUZ, ROBERTO', 'NATURAL', 'DNI', '45871777', 'Jr. Mirador 340, San Miguel', 'ACTIVO',
     'GENERAL', 'HABIDO', NULL, NULL, 'LIMA', 'LIMA', 'SAN MIGUEL', 'Jr. Mirador', '340'),
    ('072405', 'LÓPEZ VARGAS, ANDREA', 'NATURAL', 'CE', '001234567', 'Av. Costanera 1180, San Miguel', 'INACTIVO',
     'GENERAL', 'NO HABIDO', NULL, NULL, 'LIMA', 'LIMA', 'SAN MIGUEL', 'Av. Costanera', '1180'),
    ('080245', 'INMOBILIARIA MIRAMAR S.A.C.', 'JURIDICA', 'RUC', '20601234567', 'Av. Costanera 2450, San Miguel', 'ACTIVO',
     'GENERAL', 'HABIDO', 'tributos@miramar.test', '014500808', 'LIMA', 'LIMA', 'SAN MIGUEL', 'Av. Costanera', '2450')
ON CONFLICT (codigo) DO UPDATE SET
    regimen = EXCLUDED.regimen, condicion = EXCLUDED.condicion, correo = EXCLUDED.correo,
    telefonos = EXCLUDED.telefonos, dom_departamento = EXCLUDED.dom_departamento,
    dom_provincia = EXCLUDED.dom_provincia, dom_distrito = EXCLUDED.dom_distrito, dom_via = EXCLUDED.dom_via,
    dom_numero = EXCLUDED.dom_numero;

-- El 099001-01 no tiene titular a propósito (regla R3 del ETL). El 021390-03 se compró en junio de 2025: paga
-- arbitrios desde julio de 2025 e Impuesto Predial desde 2026 (la condición de contribuyente es al 1 de enero).
INSERT INTO samnet.predio
    (codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno, base_imponible,
     fecha_adquisicion, zona)
VALUES
    ('014502-01', '150136-001-014', 'Av. La Marina 1450, San Miguel', 'Vivienda', 'URBANO', 160.00, 185000.00, DATE '2012-05-14', 'Costanera'),
    ('014502-02', '150136-006-155', 'Calle Los Cedros 155, San Miguel', 'Vivienda', 'URBANO', 90.00, 98000.00, DATE '2021-07-15', 'Maranga'),
    ('021390-01', '150136-004-220', 'Jr. Los Precursores 220, San Miguel', 'Comercio', 'URBANO', 420.00, 960000.00, DATE '2016-09-01', 'Maranga'),
    ('021390-02', '150136-004-224', 'Jr. Los Precursores 224 (depósito), San Miguel', 'Industria', 'URBANO', 180.00, 120000.00, DATE '2019-09-01', 'Maranga'),
    ('021390-03', '150136-003-380', 'Av. Elmer Faucett 380 Of. 501, San Miguel', 'Oficina', 'URBANO', 85.00, 210000.00, DATE '2025-06-10', 'Costanera'),
    ('021390-04', '150136-016-118', 'Calle Los Nogales 245, San Miguel', 'Depósito', 'URBANO', 210.00, 190000.00, DATE '2017-10-03', 'Maranga'),
    ('021390-05', '150136-017-073', 'Av. Rafael Escardó 880, San Miguel', 'Almacén', 'URBANO', 280.00, 350000.00, DATE '2013-05-27', 'Costanera'),
    ('021390-06', '150136-017-074', 'Av. Rafael Escardó 884, San Miguel', 'Estacionamiento', 'URBANO', 160.00, 110000.00, DATE '2021-01-18', 'Costanera'),
    ('021390-07', '150136-018-202', 'Av. El Pacífico 1520, San Miguel', 'Comercio', 'URBANO', 360.00, 540000.00, DATE '2016-03-09', 'Costanera'),
    ('021390-08', '150136-019-331', 'Calle Las Brisas 410, San Miguel', 'Vivienda', 'URBANO', 130.00, 175000.00, DATE '2018-08-21', 'Pando'),
    ('021390-09', '150136-020-055', 'Av. Universitaria 2270, San Miguel', 'Industria', 'URBANO', 600.00, 820000.00, DATE '2011-12-12', 'Pando'),
    ('021390-10', '150136-020-056', 'Av. Universitaria 2274, San Miguel', 'Terreno sin construir', 'URBANO', 115.00, 75000.00, DATE '2022-04-06', 'Pando'),
    ('021390-11', '150136-021-144', 'Psje. Los Jazmines 92, San Miguel', 'Comercio', 'URBANO', 200.00, 310000.00, DATE '2015-11-11', 'Maranga'),
    ('033218-01', '150136-007-088', 'Calle Bahía 88, San Miguel', 'Vivienda', 'URBANO', 120.00, 142000.00, DATE '1998-03-20', 'Pando'),
    ('045067-01', '150136-002-640', 'Av. Faro Azul 640, San Miguel', 'Educación', 'URBANO', 850.00, 1250000.00, DATE '2009-01-10', 'Costanera'),
    ('058934-01', '150136-005-340', 'Jr. Mirador 340, San Miguel', 'Vivienda', 'URBANO', 140.00, 210000.00, DATE '2018-11-30', 'Maranga'),
    ('058934-02', '150136-005-342', 'Jr. Mirador 342 Dpto. 201, San Miguel', 'Vivienda', 'URBANO', 75.00, 115000.00, DATE '2022-03-01', 'Maranga'),
    ('099001-01', '150136-009-001', 'Psje. Las Gaviotas 15, San Miguel', 'Terreno sin construir', 'OTRO', 95.00, 60000.00, NULL, 'Pando')
ON CONFLICT (codigo_predio) DO UPDATE SET ubicacion = EXCLUDED.ubicacion, zona = EXCLUDED.zona;

-- Inmobiliaria Miramar: 30 departamentos y un local comercial en el mismo edificio (lista larga de predios).
INSERT INTO samnet.predio
    (codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno, base_imponible,
     fecha_adquisicion, zona)
SELECT '080245-' || lpad(n::TEXT, 2, '0'),
       '150136-010-' || lpad((100 + n)::TEXT, 3, '0'),
       CASE WHEN n = 31 THEN 'Av. Costanera 2450 Local 1, San Miguel'
            ELSE 'Av. Costanera 2450 Dpto. ' || ((n - 1) / 6 + 1) || lpad((((n - 1) % 6) + 1)::TEXT, 2, '0') || ', San Miguel'
       END,
       CASE WHEN n = 31 THEN 'Comercio' ELSE 'Vivienda' END,
       'URBANO',
       CASE WHEN n = 31 THEN 140.00 ELSE 60.00 + (n % 6) * 10 END,
       CASE WHEN n = 31 THEN 420000.00 ELSE 180000.00 + (n % 6) * 25000 + ((n - 1) / 6) * 4000 END,
       DATE '2020-02-01',
       'Costanera'
FROM generate_series(1, 31) AS n
ON CONFLICT (codigo_predio) DO UPDATE SET zona = EXCLUDED.zona;

INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad, porcentaje_propiedad)
VALUES
    ('014502-01', '014502', 'PROPIETARIO', 100.00),
    ('014502-02', '014502', 'PROPIETARIO', 100.00),
    ('021390-01', '021390', 'PROPIETARIO', 100.00),
    ('021390-02', '021390', 'PROPIETARIO', 100.00),
    ('021390-03', '021390', 'PROPIETARIO', 100.00),
    ('021390-04', '021390', 'PROPIETARIO', 100.00),
    ('021390-05', '021390', 'PROPIETARIO', 100.00),
    ('021390-06', '021390', 'PROPIETARIO', 100.00),
    ('021390-07', '021390', 'PROPIETARIO', 100.00),
    ('021390-08', '021390', 'PROPIETARIO', 100.00),
    ('021390-09', '021390', 'PROPIETARIO', 100.00),
    ('021390-10', '021390', 'PROPIETARIO', 100.00),
    ('021390-11', '021390', 'PROPIETARIO', 100.00),
    ('033218-01', '033218', 'PROPIETARIO', 100.00),
    ('045067-01', '045067', 'PROPIETARIO', 100.00),
    ('058934-01', '058934', 'COPROPIETARIO', 50.00),
    ('058934-01', '061177', 'COPROPIETARIO', 50.00),
    ('058934-02', '058934', 'PROPIETARIO', 100.00)
ON CONFLICT (codigo_predio, contribuyente_codigo) DO NOTHING;

INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad, porcentaje_propiedad)
SELECT p.codigo_predio, '080245', 'PROPIETARIO', 100.00
FROM samnet.predio p
WHERE p.codigo_predio LIKE '080245-%'
ON CONFLICT (codigo_predio, contribuyente_codigo) DO NOTHING;

INSERT INTO samnet.declaracion_jurada
    (id_declaracion, codigo_predio, anio, autoavaluo, motivo, submotivo, tipo_declarante, estado, es_vigente)
SELECT s.id_declaracion, s.codigo_predio, s.anio, s.autoavaluo, s.motivo, s.submotivo,
       s.tipo_declarante, s.estado, s.es_vigente
FROM (VALUES
    ('DJ-2012-000141', '014502-01', 2012, 150000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'PROPIETARIO', 'PROCESADA', FALSE),
    ('DJ-2024-001450', '014502-01', 2024, 185000.00, 'ACTUALIZACION', 'AMPLIACION', 'PROPIETARIO', 'PROCESADA', TRUE),
    ('DJ-2021-000155', '014502-02', 2021, 98000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'PROPIETARIO', 'PROCESADA', TRUE),
    ('DJ-2016-000220', '021390-01', 2016, 960000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2019-000224', '021390-02', 2019, 120000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2025-000380', '021390-03', 2025, 210000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2026-006203', '021390-04', 2026, 190000.00, 'ACTUALIZACION', 'REVALUO', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2026-006204', '021390-05', 2026, 350000.00, 'ACTUALIZACION', 'REVALUO', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2026-006205', '021390-06', 2026, 110000.00, 'ACTUALIZACION', 'REVALUO', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2026-006206', '021390-07', 2026, 540000.00, 'ACTUALIZACION', 'REVALUO', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2026-006207', '021390-08', 2026, 175000.00, 'ACTUALIZACION', 'REVALUO', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2026-006208', '021390-09', 2026, 820000.00, 'ACTUALIZACION', 'REVALUO', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2026-006209', '021390-10', 2026, 75000.00, 'ACTUALIZACION', 'REVALUO', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2026-006210', '021390-11', 2026, 310000.00, 'ACTUALIZACION', 'REVALUO', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-1998-000088', '033218-01', 1998, 142000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'PROPIETARIO', 'PROCESADA', TRUE),
    ('DJ-2009-000640', '045067-01', 2009, 1250000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2018-000340', '058934-01', 2018, 210000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'COPROPIETARIO', 'PROCESADA', TRUE),
    ('DJ-2022-000342', '058934-02', 2022, 115000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'PROPIETARIO', 'PROCESADA', TRUE)
) AS s(id_declaracion, codigo_predio, anio, autoavaluo, motivo, submotivo, tipo_declarante, estado, es_vigente)
WHERE NOT (s.es_vigente AND EXISTS (
    SELECT 1 FROM samnet.declaracion_jurada d
    WHERE d.codigo_predio = s.codigo_predio AND d.es_vigente
))
ON CONFLICT (id_declaracion) DO NOTHING;

INSERT INTO samnet.declaracion_jurada
    (id_declaracion, codigo_predio, anio, autoavaluo, motivo, submotivo, tipo_declarante, estado, es_vigente)
SELECT 'DJ-2020-' || replace(p.codigo_predio, '-', ''), p.codigo_predio, 2020, p.base_imponible, 'INSCRIPCION',
       'COMPRA-VENTA', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE
FROM samnet.predio p
WHERE p.codigo_predio LIKE '080245-%'
  AND NOT EXISTS (
      SELECT 1 FROM samnet.declaracion_jurada d
      WHERE d.codigo_predio = p.codigo_predio AND d.es_vigente
  )
ON CONFLICT (id_declaracion) DO NOTHING;
