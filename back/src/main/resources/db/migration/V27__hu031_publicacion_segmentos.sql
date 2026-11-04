-- HU031: al publicar una segmentación nueva, la anterior pasa a RETIRADO; se conserva para la trazabilidad.
ALTER TABLE analitica.segmento DROP CONSTRAINT segmento_estado_check;
ALTER TABLE analitica.segmento
    ADD CONSTRAINT ck_segmento_estado CHECK (estado IN ('BORRADOR', 'APROBADO', 'PUBLICADO', 'RETIRADO'));

-- La jefatura (Gerencia de Rentas) aprueba; Gerencia y analistas consultan los segmentos.
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'SEGMENTOS:CONSULTAR')
WHERE nombre IN ('GERENCIA', 'ANALISTA_RENTAS')
  AND NOT ('SEGMENTOS:CONSULTAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));

UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'SEGMENTOS:APROBAR')
WHERE nombre = 'GERENCIA'
  AND NOT ('SEGMENTOS:APROBAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));
