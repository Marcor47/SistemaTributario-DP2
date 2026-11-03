UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'REPORTES:EXPORTAR')
WHERE nombre IN ('GERENCIA', 'ANALISTA_RENTAS')
  AND NOT ('REPORTES:EXPORTAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));

