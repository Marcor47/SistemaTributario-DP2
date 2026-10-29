-- Usuarios internos ficticios para desarrollo (roles de seguridad.usuario).
-- Flyway los carga al iniciar el backend junto con el resto de db/datos-prueba.
-- La clave es pública para desarrollo; esta carpeta no se carga en producción.
-- No sobrescribe la clave de un usuario existente ni reactiva usuarios deshabilitados.
INSERT INTO seguridad.usuario (nombre_usuario, nombre_completo, correo, clave_hash, activo, fecha_creacion)
VALUES ('admin', 'Administrador de prueba', 'admin@demo.muni',
        public.crypt('admin123', public.gen_salt('bf', 12)), TRUE, now())
ON CONFLICT (nombre_usuario) DO NOTHING;

INSERT INTO seguridad.usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM seguridad.usuario u, seguridad.rol r
WHERE u.nombre_usuario = 'admin'
  AND u.activo
  AND r.nombre = 'ADMINISTRADOR'
ON CONFLICT DO NOTHING;
