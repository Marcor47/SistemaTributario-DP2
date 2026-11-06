-- Simula una carga publicada de ps_etl_cobranza para desarrollo y pruebas (HU006, HU018, HU026).
-- La carga real la hará el proceso ETL de la otra rama; este script solo llena el modelo estrella y el
-- puente de reidentificación con los mismos datos sintéticos de SAMNET. No forma parte de db/migration:
-- solo se ejecuta con el perfil local y en los tests.
--
-- Es una migración repetible: se puede volver a ejecutar sin duplicar hechos ni correspondencias.
-- Fecha de corte fija 2026-10-19, la misma del reloj de los tests y de los mocks del frontend.

-- 1. Lote publicado de cobranza.
INSERT INTO analitica.lote_etl
    (fuente, fecha_corte, tipo_ejecucion, estado, fecha_inicio, fecha_fin,
     registros_leidos, registros_aceptados, registros_rechazados)
SELECT 'ps_etl_cobranza', DATE '2026-10-19', 'PROGRAMADA', 'PUBLICADO',
       TIMESTAMP '2026-10-19 02:00', TIMESTAMP '2026-10-19 02:10', 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM analitica.lote_etl
    WHERE fuente = 'ps_etl_cobranza' AND fecha_corte = DATE '2026-10-19');

-- 2. Dimensiones con seudónimos deterministas (RNF-018). La clave es solo para los datos de demo.
INSERT INTO analitica.dim_contribuyente (seudonimo_contribuyente, tipo_persona, regimen, condicion)
SELECT encode(hmac(c.codigo, 'samnet-demo-contribuyente', 'sha256'), 'hex'),
       c.tipo_persona, c.regimen, c.condicion
FROM samnet.contribuyente c
ON CONFLICT (seudonimo_contribuyente) DO UPDATE
SET tipo_persona = EXCLUDED.tipo_persona,
    regimen = EXCLUDED.regimen,
    condicion = EXCLUDED.condicion;

INSERT INTO analitica.dim_predio (seudonimo_predio, zona, uso_predio, tipo_predio, base_imponible)
SELECT encode(hmac(p.codigo_predio, 'samnet-demo-predio', 'sha256'), 'hex'),
       p.zona, p.uso_predio, p.tipo_predio, p.base_imponible
FROM samnet.predio p
ON CONFLICT (seudonimo_predio) DO UPDATE
SET zona = EXCLUDED.zona,
    uso_predio = EXCLUDED.uso_predio,
    tipo_predio = EXCLUDED.tipo_predio,
    base_imponible = EXCLUDED.base_imponible;

-- 3. Correspondencia seudónimo -> clave de SAMNET, para la reidentificación auditada.
INSERT INTO maestros.entidad_maestra (tipo_entidad, seudonimo, estado)
SELECT 'CONTRIBUYENTE', encode(hmac(c.codigo, 'samnet-demo-contribuyente', 'sha256'), 'hex'), 'ACTIVA'
FROM samnet.contribuyente c
ON CONFLICT (seudonimo) DO NOTHING;

INSERT INTO maestros.entidad_maestra (tipo_entidad, seudonimo, estado)
SELECT 'PREDIO', encode(hmac(p.codigo_predio, 'samnet-demo-predio', 'sha256'), 'hex'), 'ACTIVA'
FROM samnet.predio p
ON CONFLICT (seudonimo) DO NOTHING;

INSERT INTO maestros.correspondencia_origen (id_maestro, sistema_origen, clave_origen, fecha_vinculo)
SELECT e.id_maestro, 'SAMNET', c.codigo, TIMESTAMP '2026-10-19 02:10'
FROM samnet.contribuyente c
JOIN maestros.entidad_maestra e
  ON e.seudonimo = encode(hmac(c.codigo, 'samnet-demo-contribuyente', 'sha256'), 'hex')
ON CONFLICT (sistema_origen, clave_origen) DO NOTHING;

INSERT INTO maestros.correspondencia_origen (id_maestro, sistema_origen, clave_origen, fecha_vinculo)
SELECT e.id_maestro, 'SAMNET', p.codigo_predio, TIMESTAMP '2026-10-19 02:10'
FROM samnet.predio p
JOIN maestros.entidad_maestra e
  ON e.seudonimo = encode(hmac(p.codigo_predio, 'samnet-demo-predio', 'sha256'), 'hex')
ON CONFLICT (sistema_origen, clave_origen) DO NOTHING;

-- 4. Hechos por cuota. El predial no tiene predio en SAMNET; se enlaza al primer predio del
--    contribuyente solo para cumplir la FK. El reporte no usa esa zona para el predial.
DELETE FROM analitica.hecho_cobranza
WHERE id_lote = (
    SELECT id_lote FROM analitica.lote_etl
    WHERE fuente = 'ps_etl_cobranza' AND fecha_corte = DATE '2026-10-19'
    ORDER BY id_lote
    LIMIT 1);

WITH predio_tecnico AS (
    SELECT DISTINCT ON (t.contribuyente_codigo) t.contribuyente_codigo, t.codigo_predio
    FROM samnet.predio_titular t
    ORDER BY t.contribuyente_codigo, t.codigo_predio
),
cuotas AS (
    SELECT o.*,
           COALESCE(o.codigo_predio, pt.codigo_predio) AS predio_fact,
           o.monto_emitido - o.monto_pagado - o.monto_descontado AS saldo
    FROM samnet.obligacion o
    LEFT JOIN predio_tecnico pt ON pt.contribuyente_codigo = o.contribuyente_codigo
    WHERE o.anio <= EXTRACT(YEAR FROM DATE '2026-10-19')
)
INSERT INTO analitica.hecho_cobranza
    (id_lote, seudonimo_contribuyente, id_predio, tributo, anio, cuota, fecha_vencimiento,
     monto_emitido, monto_insoluto, monto_pagado, monto_descuento, estado_pago)
SELECT l.id_lote,
       dc.seudonimo_contribuyente,
       dp.id_predio,
       c.tributo,
       c.anio,
       c.numero_cuota,
       c.fecha_vencimiento,
       c.monto_emitido,
       c.saldo,
       c.monto_pagado,
       c.monto_descontado,
       CASE WHEN c.saldo <= 0 THEN 'PAGADA'
            WHEN c.fecha_vencimiento < DATE '2026-10-19' THEN 'VENCIDA'
            ELSE 'PENDIENTE' END
FROM cuotas c
JOIN analitica.lote_etl l
  ON l.id_lote = (SELECT id_lote FROM analitica.lote_etl
                  WHERE fuente = 'ps_etl_cobranza' AND fecha_corte = DATE '2026-10-19'
                  ORDER BY id_lote
                  LIMIT 1)
JOIN analitica.dim_contribuyente dc
  ON dc.seudonimo_contribuyente = encode(hmac(c.contribuyente_codigo,
                                              'samnet-demo-contribuyente', 'sha256'), 'hex')
JOIN analitica.dim_predio dp
  ON dp.seudonimo_predio = encode(hmac(c.predio_fact, 'samnet-demo-predio', 'sha256'), 'hex');

-- 5. Volumen del lote, para que la auditoría del ETL sea coherente.
UPDATE analitica.lote_etl l
SET registros_leidos = (SELECT count(*) FROM analitica.hecho_cobranza h WHERE h.id_lote = l.id_lote),
    registros_aceptados = (SELECT count(*) FROM analitica.hecho_cobranza h WHERE h.id_lote = l.id_lote),
    registros_rechazados = 0
WHERE l.id_lote = (SELECT id_lote FROM analitica.lote_etl
                   WHERE fuente = 'ps_etl_cobranza' AND fecha_corte = DATE '2026-10-19'
                   ORDER BY id_lote
                   LIMIT 1);
