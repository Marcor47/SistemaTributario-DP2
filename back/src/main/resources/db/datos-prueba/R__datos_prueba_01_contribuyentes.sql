-- Datos ficticios de SAMNET sintético: los mismos contribuyentes que usan los mocks de frontweb,
-- con sus predios, titulares y declaraciones juradas.
-- Es una migración repetible: se vuelve a ejecutar si cambia su contenido, por eso usa ON CONFLICT.
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
     'GENERAL', 'NO HABIDO', NULL, NULL, 'LIMA', 'LIMA', 'SAN MIGUEL', 'Av. Costanera', '1180')
ON CONFLICT (codigo) DO UPDATE SET
    regimen = EXCLUDED.regimen, condicion = EXCLUDED.condicion, correo = EXCLUDED.correo,
    telefonos = EXCLUDED.telefonos, dom_departamento = EXCLUDED.dom_departamento,
    dom_provincia = EXCLUDED.dom_provincia, dom_distrito = EXCLUDED.dom_distrito, dom_via = EXCLUDED.dom_via,
    dom_numero = EXCLUDED.dom_numero;

-- Predios de los arbitrios de R__datos_prueba_03. El 099001-01 no tiene titular a propósito (regla R3 del ETL).
INSERT INTO samnet.predio
    (codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno, base_imponible,
     fecha_adquisicion, zona)
VALUES
    ('014502-01', '150136-001-014', 'Av. La Marina 1450, San Miguel', 'Vivienda', 'URBANO', 160.00, 185000.00, DATE '2012-05-14', 'Z1'),
    ('021390-01', '150136-004-220', 'Jr. Los Precursores 220, San Miguel', 'Comercio', 'URBANO', 420.00, 960000.00, DATE '2016-09-01', 'Z2'),
    ('033218-01', '150136-007-088', 'Calle Bahía 88, San Miguel', 'Vivienda', 'URBANO', 120.00, 142000.00, DATE '1998-03-20', 'Z3'),
    ('045067-01', '150136-002-640', 'Av. Faro Azul 640, San Miguel', 'Educación', 'URBANO', 850.00, 1250000.00, DATE '2009-01-10', 'Z1'),
    ('058934-01', '150136-005-340', 'Jr. Mirador 340, San Miguel', 'Vivienda', 'URBANO', 140.00, 210000.00, DATE '2018-11-30', 'Z2'),
    ('099001-01', '150136-009-001', 'Psje. Las Gaviotas 15, San Miguel', 'Terreno sin construir', 'OTRO', 95.00, 60000.00, NULL, 'Z3')
ON CONFLICT (codigo_predio) DO NOTHING;

INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad, porcentaje_propiedad)
VALUES
    ('014502-01', '014502', 'PROPIETARIO', 100.00),
    ('021390-01', '021390', 'PROPIETARIO', 100.00),
    ('033218-01', '033218', 'PROPIETARIO', 100.00),
    ('045067-01', '045067', 'PROPIETARIO', 100.00),
    ('058934-01', '058934', 'COPROPIETARIO', 50.00),
    ('058934-01', '061177', 'COPROPIETARIO', 50.00)
ON CONFLICT (codigo_predio, contribuyente_codigo) DO NOTHING;

INSERT INTO samnet.declaracion_jurada
    (id_declaracion, codigo_predio, anio, autoavaluo, motivo, submotivo, tipo_declarante, estado, es_vigente)
VALUES
    ('DJ-2012-000141', '014502-01', 2012, 150000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'PROPIETARIO', 'PROCESADA', FALSE),
    ('DJ-2024-001450', '014502-01', 2024, 185000.00, 'ACTUALIZACION', 'AMPLIACION', 'PROPIETARIO', 'PROCESADA', TRUE),
    ('DJ-2016-000220', '021390-01', 2016, 960000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-1998-000088', '033218-01', 1998, 142000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'PROPIETARIO', 'PROCESADA', TRUE),
    ('DJ-2009-000640', '045067-01', 2009, 1250000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'REPRESENTANTE LEGAL', 'PROCESADA', TRUE),
    ('DJ-2018-000340', '058934-01', 2018, 210000.00, 'INSCRIPCION', 'COMPRA-VENTA', 'COPROPIETARIO', 'PROCESADA', TRUE)
ON CONFLICT (id_declaracion) DO NOTHING;
