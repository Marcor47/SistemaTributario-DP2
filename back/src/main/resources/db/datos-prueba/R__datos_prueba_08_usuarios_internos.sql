-- Usuarios internos de desarrollo para el login de personal municipal (/api/auth/interno/login).
-- Las claves son públicas; esta carpeta no se carga en producción.
-- No sobrescribe usuarios existentes ni sus claves ni sus roles.
WITH accesos (nombre_usuario, nombre_completo, correo, clave, rol) AS (
    VALUES
        ('admin', 'Administrador de Pruebas', 'admin@example.org', 'admin123', 'ADMINISTRADOR'),
        ('analista', 'Analista de Rentas de Pruebas', 'analista@example.org', 'analista123', 'ANALISTA_RENTAS')
),
nuevos AS (
    INSERT INTO seguridad.usuario (nombre_usuario, nombre_completo, correo, clave_hash, activo, fecha_creacion)
    SELECT a.nombre_usuario, a.nombre_completo, a.correo,
           public.crypt(a.clave, public.gen_salt('bf', 12)), TRUE, now()
    FROM accesos a
    ON CONFLICT DO NOTHING
    RETURNING id_usuario, nombre_usuario
)
INSERT INTO seguridad.usuario_rol (id_usuario, id_rol)
SELECT n.id_usuario, r.id_rol
FROM nuevos n
JOIN accesos a ON a.nombre_usuario = n.nombre_usuario
JOIN seguridad.rol r ON r.nombre = a.rol
ON CONFLICT DO NOTHING;
