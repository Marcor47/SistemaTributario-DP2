-- HU020: el operador de Rentas gestiona las solicitudes de Constancia de No Adeudo (RF-034). En ventanilla el
-- trabajador ya actúa como operador (RF-055), así que el permiso se da al rol VENTANILLA.
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'CONSTANCIAS:GESTIONAR')
WHERE nombre = 'VENTANILLA'
  AND NOT ('CONSTANCIAS:GESTIONAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));

-- Plantilla con que se registran las constancias emitidas (RF-046). El PDF lo arma el backend; cuando la
-- Municipalidad entregue el formato oficial se publica como una versión nueva.
INSERT INTO operativo.plantilla_documento (codigo, nombre, tipo, descripcion)
VALUES ('CONSTANCIA_NO_ADEUDO', 'Constancia de No Adeudo', 'DOCUMENTO_GENERADO',
        'Constancia que emite el operador tras verificar la deuda y el pago del derecho de trámite')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO operativo.version_plantilla
    (id_plantilla, numero_version, archivo, fecha_publicacion, vigente, tipo_contenido, contenido)
SELECT p.id_plantilla, 1, 'constancia-no-adeudo.pdf', CURRENT_TIMESTAMP, TRUE, 'application/pdf', ''::BYTEA
FROM operativo.plantilla_documento p
WHERE p.codigo = 'CONSTANCIA_NO_ADEUDO'
  AND NOT EXISTS (SELECT 1 FROM operativo.version_plantilla v WHERE v.id_plantilla = p.id_plantilla);
