-- Solo perfil local. Personal de la Gerencia de Rentas y áreas de apoyo: al menos tres usuarios por rol, con nombres y
-- correos institucionales. Corrige los nombres genéricos de las cuentas creadas por versiones anteriores de las
-- semillas sin cambiar su usuario, su clave ni su rol. Los usuarios nuevos no se sobrescriben si ya existen.
-- Las credenciales de cada cuenta se entregan en el informe de la entrega, nunca en una pantalla.

-- 1. Nombres y correos de las cuentas existentes.
UPDATE seguridad.usuario u
SET nombre_completo = n.nombre_completo, correo = n.correo
FROM (VALUES
    ('admin', 'Carlos Alberto Mendoza Ríos', 'cmendoza@munisanmiguel.gob.pe'),
    ('admin.local', 'Lucía Fernanda Paredes Soto', 'lparedes@munisanmiguel.gob.pe'),
    ('gerencia', 'María Fernanda Quispe Rojas', 'mquispe@munisanmiguel.gob.pe'),
    ('responsable.local', 'Ricardo Andrés Salazar Núñez', 'rsalazar@munisanmiguel.gob.pe'),
    ('analista', 'Diego Alonso Huamán Torres', 'dhuaman@munisanmiguel.gob.pe'),
    ('datos', 'Andrea Sofía Ramírez León', 'aramirez@munisanmiguel.gob.pe'),
    ('ventanilla', 'José Antonio Vargas Medina', 'jvargas@munisanmiguel.gob.pe'),
    ('cromero', 'Claudia Patricia Romero Delgado', 'cromero@munisanmiguel.gob.pe'),
    ('gestor.local', 'Verónica Isabel Aguilar Montoya', 'vaguilar@munisanmiguel.gob.pe')
) AS n (nombre_usuario, nombre_completo, correo)
WHERE lower(u.nombre_usuario) = n.nombre_usuario
  AND (u.nombre_completo IS DISTINCT FROM n.nombre_completo OR u.correo IS DISTINCT FROM n.correo)
  AND NOT EXISTS (SELECT 1 FROM seguridad.usuario o
                  WHERE lower(o.correo) = lower(n.correo) AND o.id_usuario <> u.id_usuario);

-- 2. Cuentas nuevas hasta completar tres por rol. Clave inicial común del personal: SanMiguel#2026.
WITH personal (nombre_usuario, nombre_completo, correo, rol) AS (
    VALUES
        ('jvalverde', 'Jorge Luis Valverde Campos', 'jvalverde@munisanmiguel.gob.pe', 'ADMINISTRADOR'),
        ('pcardenas', 'Patricia Elena Cárdenas Villanueva', 'pcardenas@munisanmiguel.gob.pe', 'GERENCIA'),
        ('kflores', 'Karen Milagros Flores Benites', 'kflores@munisanmiguel.gob.pe', 'ANALISTA_RENTAS'),
        ('lcastillo', 'Luis Enrique Castillo Ayala', 'lcastillo@munisanmiguel.gob.pe', 'ANALISTA_RENTAS'),
        ('mchavez', 'Miguel Ángel Chávez Espinoza', 'mchavez@munisanmiguel.gob.pe', 'RESPONSABLE_DATOS'),
        ('rgutierrez', 'Rosa María Gutiérrez Palacios', 'rgutierrez@munisanmiguel.gob.pe', 'RESPONSABLE_DATOS'),
        ('fccori', 'Fernando Javier Ccori Huanca', 'fccori@munisanmiguel.gob.pe', 'VENTANILLA'),
        ('evillanueva', 'Elena Victoria Villanueva Soto', 'evillanueva@munisanmiguel.gob.pe', 'VENTANILLA'),
        ('srojas', 'Sandra Beatriz Rojas Mamani', 'srojas@munisanmiguel.gob.pe', 'TERMINALISTA'),
        ('opacheco', 'Óscar Raúl Pacheco Inga', 'opacheco@munisanmiguel.gob.pe', 'TERMINALISTA'),
        ('mortiz', 'Milagros del Pilar Ortiz Cáceres', 'mortiz@munisanmiguel.gob.pe', 'TERMINALISTA'),
        ('pnavarro', 'Pedro Pablo Navarro Zapata', 'pnavarro@munisanmiguel.gob.pe', 'GESTOR_RECLAMOS'),
        ('gleon', 'Gabriela Alejandra León Bustamante', 'gleon@munisanmiguel.gob.pe', 'GESTOR_RECLAMOS')
),
nuevos AS (
    INSERT INTO seguridad.usuario (nombre_usuario, nombre_completo, correo, clave_hash, activo, fecha_creacion)
    SELECT p.nombre_usuario, p.nombre_completo, p.correo, public.crypt('SanMiguel#2026', public.gen_salt('bf', 10)),
           TRUE, TIMESTAMP '2026-01-05 08:30'
    FROM personal p
    WHERE NOT EXISTS (SELECT 1 FROM seguridad.usuario u
                      WHERE lower(u.nombre_usuario) = p.nombre_usuario OR lower(u.correo) = lower(p.correo))
    RETURNING id_usuario, nombre_usuario
)
INSERT INTO seguridad.usuario_rol (id_usuario, id_rol)
SELECT n.id_usuario, r.id_rol
FROM nuevos n
JOIN personal p ON p.nombre_usuario = n.nombre_usuario
JOIN seguridad.rol r ON r.nombre = p.rol
ON CONFLICT DO NOTHING;

-- 3. La cuenta de ventanilla de versiones anteriores conserva su rol.
INSERT INTO seguridad.usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM seguridad.usuario u
JOIN seguridad.rol r ON r.nombre = 'VENTANILLA'
WHERE lower(u.nombre_usuario) = 'cromero'
ON CONFLICT DO NOTHING;

-- 4. Cuenta técnica y plantilla interna del proceso automático de constancias con textos institucionales.
UPDATE seguridad.usuario
SET nombre_completo = 'Proceso automático de constancias', correo = 'constancias.sistema@munisanmiguel.gob.pe'
WHERE lower(nombre_usuario) = 'sistema.constancia'
  AND (nombre_completo <> 'Proceso automático de constancias' OR correo ILIKE '%example%');

