-- HU011: dashboard ejecutivo de Rentas (RF-012).
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'DASHBOARD:CONSULTAR')
WHERE nombre IN ('GERENCIA', 'ANALISTA_RENTAS')
  AND NOT ('DASHBOARD:CONSULTAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
