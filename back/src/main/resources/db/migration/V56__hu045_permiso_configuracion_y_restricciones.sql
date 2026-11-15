-- HU045: permiso dedicado para configurar el acceso a reportes y unicidad de reglas.
UPDATE seguridad.rol
SET permisos = CASE
        WHEN permisos LIKE '%REPORTES:CONFIGURAR%' THEN permisos
        ELSE permisos || ',REPORTES:CONFIGURAR'
    END
WHERE nombre = 'ADMINISTRADOR';

-- Evita duplicados por concurrencia. Las reglas de reporte completo tienen valor nulo.
DELETE FROM operativo.restriccion_reporte a
USING operativo.restriccion_reporte b
WHERE a.id_restriccion > b.id_restriccion
  AND a.id_rol = b.id_rol
  AND a.tipo_reporte = b.tipo_reporte
  AND a.dimension = b.dimension
  AND COALESCE(a.valor_permitido, '') = COALESCE(b.valor_permitido, '');

CREATE UNIQUE INDEX uq_restriccion_reporte_regla
    ON operativo.restriccion_reporte (id_rol, tipo_reporte, dimension, COALESCE(valor_permitido, ''));
