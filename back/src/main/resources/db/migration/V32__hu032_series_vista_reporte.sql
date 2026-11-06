ALTER TABLE operativo.vista_reporte ADD COLUMN IF NOT EXISTS series text;

UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'REPORTES:PERSONALIZAR')
WHERE nombre = 'GERENCIA'
  AND NOT ('REPORTES:PERSONALIZAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
