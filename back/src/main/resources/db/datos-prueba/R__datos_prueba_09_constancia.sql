-- Actor técnico sin acceso interactivo para el recorrido simulado de constancias.
INSERT INTO seguridad.usuario (nombre_usuario, nombre_completo, correo, clave_hash, activo, fecha_creacion)
VALUES ('sistema.constancia', 'Proceso simulado de constancias', 'sistema.constancia@example.org', 'SIN_ACCESO', FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (nombre_usuario) DO NOTHING;

INSERT INTO operativo.plantilla_documento (codigo, nombre, tipo, descripcion)
VALUES ('CONSTANCIA_DEMO', 'Constancia de No Adeudo de prueba', 'DOCUMENTO_GENERADO', 'Plantilla para demostración')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO operativo.version_plantilla (id_plantilla, numero_version, archivo, fecha_publicacion, vigente, tipo_contenido, contenido)
SELECT id_plantilla, 1, 'constancia-demo.pdf', CURRENT_TIMESTAMP, TRUE, 'application/pdf', decode('', 'hex')
FROM operativo.plantilla_documento p
WHERE p.codigo = 'CONSTANCIA_DEMO' AND NOT EXISTS (
    SELECT 1 FROM operativo.version_plantilla v WHERE v.id_plantilla = p.id_plantilla AND v.vigente
);
