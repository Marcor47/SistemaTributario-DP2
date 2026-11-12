-- HU045: el Administrador configura el acceso a reportes y necesita listar
-- los segmentos publicados para restringir la información por segmento.
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'SEGMENTOS:CONSULTAR')
WHERE nombre = 'ADMINISTRADOR'
  AND NOT ('SEGMENTOS:CONSULTAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
