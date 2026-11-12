-- Duplicados y errores de calidad en SAMNET sintético, para probar el ETL y la bandeja de homologación (RF-039 a
-- RF-041). Son datos ficticios, como el resto de esta carpeta, y emulan lo que trae un padrón real: la misma persona
-- registrada dos veces, un homónimo y un documento mal digitado.
-- Ninguno tiene cuotas ni pagos: no cambian montos, reportes ni indicadores. Solo ejercitan la cascada.
--
--   903218  RUC 10 de 033218 (persona natural con negocio): el RUC lleva su DNI            → N1: se une solo
--   914502  la misma persona que 014502, con el DNI mal digitado (dos cifras invertidas)   → N2: caso para aprobar
--   921390  la misma empresa que 021390, sin puntos y con dos cifras del RUC invertidas      → N2: caso para aprobar
--   933219  homónimo de 033218 en el mismo domicilio, con otro DNI (padre e hijo)          → N2: caso para rechazar
--   990001  DNI de 7 dígitos                                                              → R2: cuarentena
--   predio 914502-01: el mismo código catastral que 014502-01, registrado por el duplicado → N1: se une solo
--
-- Va antes de R__zz_carga_etl_prueba para que la carga de prueba ya los encuentre. Los nombres y documentos evitan
-- las búsquedas que usan los tests de SAMNET ("medina", "4102"), para no cambiar lo que esas pantallas muestran.

INSERT INTO samnet.contribuyente (codigo, nombre, tipo_persona, tipo_documento, numero_documento, domicilio_fiscal,
        estado, regimen, condicion, correo, dom_departamento, dom_provincia, dom_distrito, dom_via, dom_numero)
VALUES
    ('903218', 'RAMOS QUISPE VICTOR HUGO', 'NATURAL', 'RUC', '10092154303', 'Calle Bahía 88, San Miguel',
     'ACTIVO', 'PENSIONISTA', 'HABIDO', NULL, 'LIMA', 'LIMA', 'SAN MIGUEL', 'Calle Bahía', '88'),
    ('914502', 'Maria Elena Garcia Torres', 'NATURAL', 'DNI', '41208765', 'AV LA MARINA 1450 SAN MIGUEL',
     'ACTIVO', 'GENERAL', 'HABIDO', NULL, 'LIMA', 'LIMA', 'SAN MIGUEL', 'AV LA MARINA', '1450'),
    ('921390', 'INVERSIONES COSTA AZUL SAC', 'JURIDICA', 'RUC', '20456781293', 'Jr. Los Precursores 220 - San Miguel',
     'ACTIVO', 'GENERAL', 'HABIDO', NULL, 'LIMA', 'LIMA', 'SAN MIGUEL', 'Jr. Los Precursores', '220'),
    ('933219', 'RAMOS QUISPE, VÍCTOR HUGO', 'NATURAL', 'DNI', '70451288', 'Calle Bahía 88, San Miguel',
     'ACTIVO', 'GENERAL', 'HABIDO', NULL, 'LIMA', 'LIMA', 'SAN MIGUEL', 'Calle Bahía', '88'),
    ('990001', 'TORRES VEGA, LUIS', 'NATURAL', 'DNI', '4560987', 'Av. Libertad 900, San Miguel',
     'ACTIVO', 'GENERAL', 'HABIDO', NULL, 'LIMA', 'LIMA', 'SAN MIGUEL', 'Av. Libertad', '900')
ON CONFLICT DO NOTHING;

INSERT INTO samnet.predio (codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno,
        base_imponible, fecha_adquisicion, zona)
VALUES ('914502-01', '150136-001-014', 'AV LA MARINA 1450 SAN MIGUEL', 'Vivienda', 'URBANO', 160.00, 185000.00,
        DATE '2012-05-14', 'Costanera')
ON CONFLICT DO NOTHING;

INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad, porcentaje_propiedad)
VALUES ('914502-01', '914502', 'PROPIETARIO', 100.00)
ON CONFLICT DO NOTHING;
