UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'REPORTES:SOLICITUDES')
WHERE nombre IN ('GERENCIA', 'ANALISTA_RENTAS')
  AND NOT ('REPORTES:SOLICITUDES' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
