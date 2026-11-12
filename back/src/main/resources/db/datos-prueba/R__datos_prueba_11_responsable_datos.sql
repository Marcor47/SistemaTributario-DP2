-- Usuario interno de desarrollo con el rol Responsable de datos: ejecuta el ETL a demanda (ETL:GESTIONAR) y resuelve
-- la bandeja de homologación (RF-040). La clave es pública; esta carpeta no se carga en producción.
-- No sobrescribe el usuario si ya existe, ni su clave ni su rol.
WITH nuevo AS (
    INSERT INTO seguridad.usuario (nombre_usuario, nombre_completo, correo, clave_hash, activo, fecha_creacion)
    VALUES ('datos', 'Responsable de Datos de Pruebas', 'datos.local@example.org',
            public.crypt('datos123', public.gen_salt('bf', 12)), TRUE, now())
    ON CONFLICT DO NOTHING
    RETURNING id_usuario
)
INSERT INTO seguridad.usuario_rol (id_usuario, id_rol)
SELECT n.id_usuario, r.id_rol
FROM nuevo n
JOIN seguridad.rol r ON r.nombre = 'RESPONSABLE_DATOS'
ON CONFLICT DO NOTHING;
