-- Datos ficticios de demostración, los mismos contribuyentes que usan los mocks de frontweb.
-- Es una migración repetible: se vuelve a ejecutar si cambia su contenido, por eso usa ON CONFLICT.
INSERT INTO tributario.contribuyente
    (codigo, nombre, tipo_persona, tipo_documento, numero_documento, domicilio_fiscal, estado)
VALUES
    ('014502', 'GARCÍA TORRES, MARÍA ELENA', 'NATURAL', 'DNI', '41028765', 'Av. La Marina 1450, San Miguel', 'ACTIVO'),
    ('021390', 'INVERSIONES COSTA AZUL S.A.C.', 'JURIDICA', 'RUC', '20456781239', 'Jr. Los Precursores 220, San Miguel', 'ACTIVO'),
    ('033218', 'RAMOS QUISPE, VÍCTOR HUGO', 'NATURAL', 'DNI', '09215430', 'Calle Bahía 88, San Miguel', 'ACTIVO'),
    ('045067', 'CENTRO EDUCATIVO SOL DE LIMA E.I.R.L.', 'JURIDICA', 'RUC', '20512309876', 'Av. Faro Azul 640, San Miguel', 'ACTIVO'),
    ('058934', 'MEDINA CRUZ, PATRICIA', 'NATURAL', 'DNI', '45871203', 'Jr. Mirador 340, San Miguel', 'ACTIVO'),
    ('061177', 'MEDINA CRUZ, ROBERTO', 'NATURAL', 'DNI', '45871777', 'Jr. Mirador 340, San Miguel', 'ACTIVO'),
    ('072405', 'LÓPEZ VARGAS, ANDREA', 'NATURAL', 'CE', '001234567', 'Av. Costanera 1180, San Miguel', 'INACTIVO')
ON CONFLICT (codigo) DO NOTHING;
