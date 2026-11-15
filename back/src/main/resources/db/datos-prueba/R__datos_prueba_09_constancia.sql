-- Actor técnico sin acceso interactivo para el proceso automático de constancias.
INSERT INTO seguridad.usuario (nombre_usuario, nombre_completo, correo, clave_hash, activo, fecha_creacion)
VALUES ('sistema.constancia', 'Proceso automático de constancias', 'constancias.sistema@munisanmiguel.gob.pe', 'SIN_ACCESO', FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (nombre_usuario) DO NOTHING;
