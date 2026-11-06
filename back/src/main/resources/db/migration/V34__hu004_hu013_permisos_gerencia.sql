-- HU004 / HU013: la Gerencia de Rentas consulta el estado de cuenta y el detalle de cobranza
-- de cualquier contribuyente desde la ficha interna (/contribuyentes).
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'CUENTAS:CONSULTAR')
WHERE nombre = 'GERENCIA'
  AND NOT ('CUENTAS:CONSULTAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));

UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'COBRANZA:CONSULTAR')
WHERE nombre = 'GERENCIA'
  AND NOT ('COBRANZA:CONSULTAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
