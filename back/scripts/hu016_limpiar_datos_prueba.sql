-- HU016 (RF-022): elimina los datos de prueba creados por hu016_datos_prueba.sql.
--
-- Uso:
--   psql -h localhost -U municipalidad -d municipalidad -f back/scripts/hu016_limpiar_datos_prueba.sql

SET TIME ZONE 'America/Lima';

BEGIN;

DELETE FROM operativo.aviso_correo
WHERE id_obligacion IN (
    SELECT id::text FROM samnet.obligacion
    WHERE contribuyente_codigo IN ('970001', '970002', '970003', '970004', '970005', '970006', '970007')
);

DELETE FROM samnet.obligacion
WHERE contribuyente_codigo IN ('970001', '970002', '970003', '970004', '970005', '970006', '970007');

DELETE FROM operativo.preferencia_aviso
WHERE codigo_contribuyente IN ('970001', '970002', '970003', '970004', '970005', '970006', '970007');

DELETE FROM samnet.contribuyente
WHERE codigo IN ('970001', '970002', '970003', '970004', '970005', '970006', '970007');

COMMIT;
