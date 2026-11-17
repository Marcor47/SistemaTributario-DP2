-- HU016 (RF-022): datos de prueba de recordatorios de vencimiento.
--
-- Uso:
--   psql -h localhost -U municipalidad -d municipalidad -f back/scripts/hu016_datos_prueba.sql
--
-- Requisitos y precauciones:
--   * El perfil local ya debe haber aplicado las migraciones (Flyway) y las semillas.
--   * El script es idempotente: solo borra y recrea los contribuyentes 970001 a 970007.
--   * La ventana del proceso es [CURRENT_DATE + 1, CURRENT_DATE + DIAS_RECORDATORIO].
--   * Antes de procesar comprueba que solo aparecen estas cuotas:
--       SELECT * FROM samnet.ps_recordatorios_vencimiento(CURRENT_DATE + 1, CURRENT_DATE + 5);
--     Cerca de fin de mes el padrón local también tiene cuotas en la ventana.
--   * La tarea programada corre a las 08:00 (America/Lima). Arranca el backend con
--     tributario.recordatorios.programacion=- mientras preparas los datos.
--   * Todos los buzones del fixture apuntan a adrian.arista2002@gmail.com para validar la
--     entrega real por SMTP. El contribuyente 970003 se mantiene sin correo a propósito.

SET TIME ZONE 'America/Lima';

BEGIN;

-- 1. Limpieza de la corrida anterior (solo este fixture).
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

-- 2. Contribuyentes de prueba. Ajusta el correo si quieres recibir los mensajes.
INSERT INTO samnet.contribuyente
    (codigo, nombre, tipo_persona, tipo_documento, numero_documento, domicilio_fiscal,
     estado, regimen, condicion, correo)
VALUES
    ('970001', 'Prueba HU016 Envio',            'NATURAL', 'DNI', '99000001',
     'Av. Prueba 100, San Miguel', 'ACTIVO', 'GENERAL', 'HABIDO', 'adrian.arista2002@gmail.com'),
    ('970002', 'Prueba HU016 Desactivado',      'NATURAL', 'DNI', '99000002',
     'Av. Prueba 102, San Miguel', 'ACTIVO', 'GENERAL', 'HABIDO', 'adrian.arista2002@gmail.com'),
    ('970003', 'Prueba HU016 Sin Correo',       'NATURAL', 'DNI', '99000003',
     'Av. Prueba 104, San Miguel', 'ACTIVO', 'GENERAL', 'HABIDO', NULL),
    ('970004', 'Prueba HU016 Ya Notificado',    'NATURAL', 'DNI', '99000004',
     'Av. Prueba 106, San Miguel', 'ACTIVO', 'GENERAL', 'HABIDO', 'adrian.arista2002@gmail.com'),
    ('970005', 'Prueba HU016 Registrado Previo', 'NATURAL', 'DNI', '99000005',
     'Av. Prueba 108, San Miguel', 'ACTIVO', 'GENERAL', 'HABIDO', 'adrian.arista2002@gmail.com'),
    ('970006', 'Prueba HU016 Saldo Cero',       'NATURAL', 'DNI', '99000006',
     'Av. Prueba 110, San Miguel', 'ACTIVO', 'GENERAL', 'HABIDO', 'adrian.arista2002@gmail.com'),
    ('970007', 'Prueba HU016 Fuera Ventana',    'NATURAL', 'DNI', '99000007',
     'Av. Prueba 112, San Miguel', 'ACTIVO', 'GENERAL', 'HABIDO', 'adrian.arista2002@gmail.com');

-- 3. Contribuyente que desactivó el recordatorio (P12).
INSERT INTO operativo.preferencia_aviso (codigo_contribuyente, recordatorio_vencimiento, actualizado_en)
VALUES ('970002', FALSE, NOW());

-- 4. Cuotas con vencimiento relativo a hoy.
INSERT INTO samnet.obligacion
    (contribuyente_codigo, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido, monto_pagado)
VALUES
    -- Se envían (2 cuotas del mismo contribuyente).
    ('970001', 'IMPUESTO_PREDIAL', 2099, 1, CURRENT_DATE + 1, 120.00, 0),
    ('970001', 'ARBITRIOS',        2099, 1, CURRENT_DATE + 3,  80.00, 0),
    -- Desactivado por preferencia.
    ('970002', 'IMPUESTO_PREDIAL', 2099, 2, CURRENT_DATE + 2, 150.00, 0),
    -- Sin correo registrado.
    ('970003', 'IMPUESTO_PREDIAL', 2099, 3, CURRENT_DATE + 2, 200.00, 0),
    -- Con aviso ENVIADO previo: se cuenta como duplicado.
    ('970004', 'IMPUESTO_PREDIAL', 2099, 4, CURRENT_DATE + 2, 250.00, 0),
    -- Con aviso REGISTRADO previo: duplicado en modo registro y se reenvía en modo smtp.
    ('970005', 'ARBITRIOS',        2099, 2, CURRENT_DATE + 2, 300.00, 0),
    -- Saldo cero: la función de SAMNET la excluye.
    ('970006', 'IMPUESTO_PREDIAL', 2099, 1, CURRENT_DATE + 2, 100.00, 100.00),
    -- Fuera de la ventana (más allá de la anticipación y hoy mismo).
    ('970007', 'IMPUESTO_PREDIAL', 2099, 2, CURRENT_DATE + 6, 180.00, 0),
    ('970007', 'IMPUESTO_PREDIAL', 2099, 3, CURRENT_DATE,     180.00, 0);

-- 5. Avisos previos para probar la deduplicación.
INSERT INTO operativo.aviso_correo
    (tipo, documento_contribuyente, correo_destino, fecha_programada, fecha_envio, estado, id_obligacion)
SELECT 'RECORDATORIO_VENCIMIENTO', '99000004', 'adrian.arista2002@gmail.com',
       NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', 'ENVIADO', o.id::text
FROM samnet.obligacion o
WHERE o.contribuyente_codigo = '970004';

INSERT INTO operativo.aviso_correo
    (tipo, documento_contribuyente, correo_destino, fecha_programada, fecha_envio, estado, id_obligacion)
SELECT 'RECORDATORIO_VENCIMIENTO', '99000005', 'adrian.arista2002@gmail.com',
       NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', 'REGISTRADO', o.id::text
FROM samnet.obligacion o
WHERE o.contribuyente_codigo = '970005';

COMMIT;

-- 6. Comprobación: con 5 días de anticipación deben aparecer 6 filas de los códigos 9700xx.
SELECT * FROM samnet.ps_recordatorios_vencimiento(CURRENT_DATE + 1, CURRENT_DATE + 5)
ORDER BY id_obligacion;
