INSERT INTO seguridad.usuario
    (nombre_usuario, nombre_completo, correo, clave_hash, activo, fecha_creacion,
     cambio_clave_pendiente, fecha_cambio_clave)
SELECT 'admin.local', 'Administrador local', 'admin.local@munisanmiguel.gob.pe',
       public.crypt('AdminLocalSM#2026', public.gen_salt('bf', 12)), TRUE, CURRENT_TIMESTAMP,
       FALSE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM seguridad.usuario WHERE LOWER(nombre_usuario) = 'admin.local'
);

INSERT INTO seguridad.usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM seguridad.usuario u
JOIN seguridad.rol r ON r.nombre = 'ADMINISTRADOR'
WHERE LOWER(u.nombre_usuario) = 'admin.local'
ON CONFLICT DO NOTHING;
