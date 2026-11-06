-- HU007: el orden y la visibilidad de las columnas del detalle también forman parte de la vista guardada.
ALTER TABLE operativo.vista_reporte
    ADD COLUMN IF NOT EXISTS columnas_detalle_visibles TEXT;
