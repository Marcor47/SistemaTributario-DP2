-- Catálogos de demostración pendientes de validar con la Municipalidad: documentos requeridos por tipo de
-- trámite y de predio (RF-061) y áreas a las que se derivan los reclamos (RF-064). Las dos primeras áreas son
-- las que la Municipalidad nombró para la cobranza inductiva (24/10/2026); las demás son provisionales.
INSERT INTO operativo.area_municipal (codigo, nombre)
VALUES
    ('REGISTRO_TRIBUTARIO', 'Unidad de Registro Tributario y Control de la Deuda'),
    ('TIC', 'Oficina de Tecnologías de la Información y Comunicaciones'),
    ('RENTAS', 'Gerencia de Rentas'),
    ('ATENCION_CIUDADANO', 'Atención al Ciudadano y Ventanilla'),
    ('RECURSOS_HUMANOS', 'Recursos Humanos'),
    ('ADMINISTRACION', 'Gerencia de Administración')
ON CONFLICT (codigo) DO NOTHING;

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
