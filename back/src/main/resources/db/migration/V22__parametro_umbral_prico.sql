-- HU006 (RF-050): monto emitido en el año a partir del cual un contribuyente es PRICO (principal contribuyente).
-- Es configurable sin tocar código; el valor inicial es una propuesta sobre la BD sintética.
INSERT INTO seguridad.parametro_configuracion (clave, valor, descripcion) VALUES
    ('UMBRAL_PRICO', '2000', 'Monto emitido en el año (S/) desde el cual un contribuyente es PRICO.')
ON CONFLICT (clave) DO NOTHING;
