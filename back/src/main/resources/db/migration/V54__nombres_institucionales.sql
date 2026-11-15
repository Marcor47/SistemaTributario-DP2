-- Nombres institucionales para conceptos que quedaron con nombres de desarrollo.

-- La constancia firmada con la firma electrónica de Servicios en Línea (sin el firmador del Estado).
ALTER TABLE operativo.constancia RENAME COLUMN es_de_prueba TO firma_local;

-- Avisos registrados en el sistema mientras no se configure un servidor de correo.
ALTER TABLE operativo.aviso_correo DROP CONSTRAINT aviso_correo_estado_check;
UPDATE operativo.aviso_correo SET estado = 'REGISTRADO' WHERE estado = 'SIMULADO';
ALTER TABLE operativo.aviso_correo ADD CONSTRAINT aviso_correo_estado_check
    CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO', 'REGISTRADO'));

-- La plantilla interna del flujo de constancias de demostración ya no se usa. Si alguna constancia la referencia,
-- se conserva con un código institucional; si no, se retira.
UPDATE operativo.plantilla_documento p
SET codigo = 'CONSTANCIA_PROCESO_AUTOMATICO'
WHERE p.codigo = 'CONSTANCIA_DEMO'
  AND EXISTS (SELECT 1 FROM operativo.constancia c
              JOIN operativo.version_plantilla v ON v.id_version = c.id_version_plantilla
              WHERE v.id_plantilla = p.id_plantilla);

DELETE FROM operativo.version_plantilla v
USING operativo.plantilla_documento p
WHERE v.id_plantilla = p.id_plantilla AND p.codigo = 'CONSTANCIA_DEMO';

DELETE FROM operativo.plantilla_documento WHERE codigo = 'CONSTANCIA_DEMO';

-- Datos de muestras anteriores con códigos de desarrollo: lotes, segmentos, indicadores y metas. El padrón de la
-- carpeta datos-padron los reemplaza con cargas reales del ETL y la segmentación del modelo K-means.
CREATE TEMPORARY TABLE lote_muestra ON COMMIT DROP AS
SELECT id_lote FROM analitica.lote_etl
WHERE fuente IN ('DEMO_HU011_SAMNET_SINTETICO', 'ps_etl_indicadores_demo', 'ps_etl_indicadores_demo_t4');

DELETE FROM analitica.alerta_desvio a USING analitica.valor_indicador v
WHERE a.id_valor = v.id_valor
  AND (v.id_lote IN (SELECT id_lote FROM lote_muestra) OR v.indicador_codigo LIKE 'DEMO\_%');
DELETE FROM analitica.valor_indicador
WHERE id_lote IN (SELECT id_lote FROM lote_muestra) OR indicador_codigo LIKE 'DEMO\_%';
DELETE FROM analitica.hecho_cobranza WHERE id_lote IN (SELECT id_lote FROM lote_muestra);
DELETE FROM analitica.hecho_pago WHERE id_lote IN (SELECT id_lote FROM lote_muestra);
UPDATE operativo.aviso_correo SET id_lote = NULL WHERE id_lote IN (SELECT id_lote FROM lote_muestra);
DELETE FROM analitica.respuesta_asistente WHERE id_lote IN (SELECT id_lote FROM lote_muestra);
DELETE FROM analitica.resumen_ejecutivo WHERE id_lote IN (SELECT id_lote FROM lote_muestra);
DELETE FROM analitica.lote_etl l
WHERE l.id_lote IN (SELECT id_lote FROM lote_muestra)
  AND NOT EXISTS (SELECT 1 FROM analitica.caso_homologacion c WHERE c.id_lote = l.id_lote);

DELETE FROM analitica.segmento s USING analitica.modelo_ia m
WHERE s.id_modelo = m.id_modelo AND m.nombre LIKE 'DEMO\_%';
DELETE FROM analitica.modelo_ia m
WHERE m.nombre LIKE 'DEMO\_%'
  AND NOT EXISTS (SELECT 1 FROM analitica.traza_ia t WHERE t.id_modelo = m.id_modelo)
  AND NOT EXISTS (SELECT 1 FROM analitica.resumen_ejecutivo r WHERE r.id_modelo = m.id_modelo);
DELETE FROM analitica.alerta_desvio a USING analitica.meta_indicador m
WHERE a.id_meta = m.id AND (m.indicador_codigo LIKE 'DEMO\_%' OR m.observacion LIKE 'Demo%');
DELETE FROM analitica.meta_indicador WHERE indicador_codigo LIKE 'DEMO\_%' OR observacion LIKE 'Demo%';
DELETE FROM analitica.indicador WHERE codigo LIKE 'DEMO\_%';

-- La cuenta de ventanilla creada por una semilla anterior toma un usuario institucional.
UPDATE seguridad.usuario SET nombre_usuario = 'cromero'
WHERE lower(nombre_usuario) = 'ventanilla.demo'
  AND NOT EXISTS (SELECT 1 FROM seguridad.usuario WHERE lower(nombre_usuario) = 'cromero');

-- Órdenes de la pasarela local creadas con el prefijo anterior.
UPDATE operativo.transaccion_pago SET id_orden_pasarela = 'OPL-' || substr(id_orden_pasarela, 5)
WHERE id_orden_pasarela LIKE 'SIM-%';
