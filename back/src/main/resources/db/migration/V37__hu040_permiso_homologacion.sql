-- HU040: auditar las decisiones de homologación (RF-053).
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'HOMOLOGACION:AUDITAR')
WHERE nombre IN ('RESPONSABLE_DATOS', 'ADMINISTRADOR')
  AND NOT ('HOMOLOGACION:AUDITAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));

