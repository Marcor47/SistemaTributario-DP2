-- Catálogos de demostración pendientes de validar con la Municipalidad (tipos de predio y documentos
-- requeridos de RF-061).
INSERT INTO operativo.requisito_documental (tipo_tramite, tipo_predio, nombre_documento, obligatorio)
VALUES
    ('REGISTRO', 'URBANO', 'Documento que acredita la propiedad (minuta, escritura o título)', TRUE),
    ('REGISTRO', 'URBANO', 'Declaración jurada de autoavalúo firmada', TRUE),
    ('REGISTRO', 'URBANO', 'Plano de ubicación o fotografía de la fachada', FALSE),
    ('REGISTRO', 'RUSTICO', 'Documento que acredita la propiedad (minuta, escritura o título)', TRUE),
    ('REGISTRO', 'RUSTICO', 'Constancia de posesión o certificado catastral', TRUE),
    ('REGISTRO', 'OTRO', 'Documento que acredita la propiedad o posesión', TRUE),
    ('TRASPASO', 'URBANO', 'Documento de transferencia (minuta o escritura de compraventa)', TRUE),
    ('TRASPASO', 'URBANO', 'Copia del documento de identidad del adquirente', TRUE),
    ('TRASPASO', 'RUSTICO', 'Documento de transferencia (minuta o escritura de compraventa)', TRUE),
    ('TRASPASO', 'OTRO', 'Documento de transferencia', TRUE)
ON CONFLICT (tipo_tramite, tipo_predio, nombre_documento) DO NOTHING;
