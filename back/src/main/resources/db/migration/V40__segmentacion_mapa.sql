-- HU030 (RF-043): mapa de la segmentación. Cada asignación guarda su posición en el plano de componentes principales
-- y las variables con que el modelo la clasificó, para dibujar el mapa y la ficha sin recalcular la carga. Las
-- asignaciones anteriores (semillas o modelos sin mapa) quedan en NULL y no salen en el mapa.
ALTER TABLE analitica.asignacion_segmento
    ADD COLUMN coord_x   NUMERIC(10, 5),
    ADD COLUMN coord_y   NUMERIC(10, 5),
    ADD COLUMN variables TEXT;
