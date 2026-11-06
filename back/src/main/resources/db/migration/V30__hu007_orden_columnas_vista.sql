-- HU007: conservar el orden completo de las columnas, incluidas las que no están visibles.
ALTER TABLE operativo.vista_reporte
    ADD COLUMN IF NOT EXISTS orden_columnas TEXT,
    ADD COLUMN IF NOT EXISTS orden_columnas_detalle TEXT;
