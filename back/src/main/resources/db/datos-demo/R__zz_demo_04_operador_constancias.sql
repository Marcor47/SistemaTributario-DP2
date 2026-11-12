-- Cuenta de desarrollo para probar la bandeja de HU020. Nunca modifica usuarios existentes.
WITH creado AS (
    INSERT INTO seguridad.usuario (nombre_usuario, nombre_completo, correo, clave_hash, activo, fecha_creacion)
    VALUES ('ventanilla.demo', 'Operador de constancias de prueba', 'ventanilla.demo@example.org',
            public.crypt('VentanillaDemo#2026', public.gen_salt('bf', 12)), TRUE, CURRENT_TIMESTAMP)
    ON CONFLICT DO NOTHING
    RETURNING id_usuario
)
INSERT INTO seguridad.usuario_rol (id_usuario, id_rol)
SELECT c.id_usuario, r.id_rol FROM creado c JOIN seguridad.rol r ON r.nombre = 'VENTANILLA'
ON CONFLICT DO NOTHING;
