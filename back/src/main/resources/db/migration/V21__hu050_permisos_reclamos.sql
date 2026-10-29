UPDATE seguridad.rol
SET permisos = concat_ws(',', nullif(permisos, ''), 'RECLAMOS:CREAR')
WHERE nombre <> 'ADMINISTRADOR' AND permisos NOT LIKE '%RECLAMOS:CREAR%';

UPDATE seguridad.rol
SET permisos = concat_ws(',', nullif(permisos, ''), 'RECLAMOS:GESTIONAR')
WHERE nombre = 'GESTOR_RECLAMOS' AND permisos NOT LIKE '%RECLAMOS:GESTIONAR%';

UPDATE seguridad.rol
SET permisos = concat_ws(',', nullif(permisos, ''), 'RECLAMOS:ATENDER')
WHERE nombre IN ('GERENCIA', 'ANALISTA_RENTAS', 'RESPONSABLE_DATOS', 'VENTANILLA', 'TERMINALISTA')
  AND permisos NOT LIKE '%RECLAMOS:ATENDER%';
