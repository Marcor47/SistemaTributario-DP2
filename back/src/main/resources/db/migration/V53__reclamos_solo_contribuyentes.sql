-- Los reclamos solo los registra el contribuyente desde Servicios en Línea (web o app). Ningún rol interno los
-- registra desde la vista institucional: solo los consultan, derivan o atienden según su permiso (GESTIONAR, ATENDER).
UPDATE seguridad.rol
SET permisos = array_to_string(array_remove(string_to_array(permisos, ','), 'RECLAMOS:CREAR'), ',')
WHERE 'RECLAMOS:CREAR' = ANY (string_to_array(coalesce(permisos, ''), ','));
