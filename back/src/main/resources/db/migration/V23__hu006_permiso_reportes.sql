UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'REPORTES:CONSULTAR')
WHERE nombre IN ('GERENCIA', 'ANALISTA_RENTAS')
  AND NOT ('REPORTES:CONSULTAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
