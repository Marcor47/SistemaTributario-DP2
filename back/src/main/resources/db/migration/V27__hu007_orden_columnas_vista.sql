-- HU007: conservar el orden completo de las columnas, incluidas las que no están visibles.
ALTER TABLE operativo.vista_reporte
    ADD COLUMN orden_columnas TEXT,
    ADD COLUMN orden_columnas_detalle TEXT;
