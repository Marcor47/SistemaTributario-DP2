-- Revisión del Sprint 1: no se conoce la fórmula con que la Municipalidad reparte los arbitrios entre limpieza
-- pública, parques y jardines y seguridad ciudadana, así que se manejan como un solo tributo (ARBITRIOS) separado
-- por periodo. Las cuotas de un mismo predio y periodo se fusionan en una que suma sus montos. El Impuesto Predial
-- no cambia: su cálculo (escala sobre el autovalúo) sí está definido en la Ley de Tributación Municipal.

ALTER TABLE samnet.obligacion DROP CONSTRAINT obligacion_tributo_check;
ALTER TABLE analitica.hecho_cobranza DROP CONSTRAINT hecho_cobranza_tributo_check;

-- Por cada predio y periodo se conserva la cuota de menor id; las otras se fusionan en ella.
CREATE TEMPORARY TABLE arbitrio_fusion ON COMMIT DROP AS
SELECT o.id AS id_origen,
       min(o.id) OVER (PARTITION BY o.contribuyente_codigo, o.codigo_predio, o.anio, o.numero_cuota) AS id_destino
FROM samnet.obligacion o
WHERE o.tributo IN ('LIMPIEZA_PUBLICA', 'PARQUES_JARDINES', 'SEGURIDAD_CIUDADANA');

UPDATE samnet.obligacion d
SET tributo           = 'ARBITRIOS',
    fecha_vencimiento = s.fecha_vencimiento,
    monto_emitido     = s.monto_emitido,
    monto_pagado      = s.monto_pagado,
    monto_descontado  = s.monto_descontado,
    id_beneficio      = s.id_beneficio
FROM (SELECT f.id_destino,
             min(o.fecha_vencimiento) AS fecha_vencimiento,
             sum(o.monto_emitido)     AS monto_emitido,
             sum(o.monto_pagado)      AS monto_pagado,
             sum(o.monto_descontado)  AS monto_descontado,
             max(o.id_beneficio)      AS id_beneficio
      FROM arbitrio_fusion f
      JOIN samnet.obligacion o ON o.id = f.id_origen
      GROUP BY f.id_destino) s
WHERE d.id = s.id_destino;

-- Seguimiento de cobranza: si dos cuotas fusionadas estaban en la misma etapa, queda la que empezó primero.
DELETE FROM samnet.seguimiento_cobranza s
USING (SELECT sc.id,
              row_number() OVER (PARTITION BY f.id_destino, sc.etapa ORDER BY sc.fecha_inicio, sc.id) AS orden
       FROM samnet.seguimiento_cobranza sc
       JOIN arbitrio_fusion f ON f.id_origen = sc.obligacion_id) r
WHERE s.id = r.id AND r.orden > 1;

UPDATE samnet.seguimiento_cobranza s
SET obligacion_id = f.id_destino
FROM arbitrio_fusion f
WHERE s.obligacion_id = f.id_origen AND f.id_origen <> f.id_destino;

-- Pagos y transacciones que cancelaban varias cuotas fusionadas pasan a aplicarse a una sola.
CREATE TEMPORARY TABLE pago_obligacion_fusion ON COMMIT DROP AS
SELECT p.id_pago, f.id_destino AS id_obligacion, sum(p.monto_aplicado) AS monto_aplicado,
       sum(p.monto_descuento) AS monto_descuento, max(p.id_beneficio) AS id_beneficio
FROM samnet.pago_obligacion p
JOIN arbitrio_fusion f ON f.id_origen = p.id_obligacion
GROUP BY p.id_pago, f.id_destino;

DELETE FROM samnet.pago_obligacion p USING arbitrio_fusion f WHERE p.id_obligacion = f.id_origen;

INSERT INTO samnet.pago_obligacion (id_pago, id_obligacion, monto_aplicado, monto_descuento, id_beneficio)
SELECT id_pago, id_obligacion, monto_aplicado, monto_descuento, id_beneficio FROM pago_obligacion_fusion;

CREATE TEMPORARY TABLE transaccion_detalle_fusion ON COMMIT DROP AS
SELECT t.id_transaccion, f.id_destino AS id_obligacion, sum(t.monto_base) AS monto_base,
       sum(t.monto_descuento) AS monto_descuento, max(t.id_beneficio) AS id_beneficio
FROM operativo.transaccion_pago_detalle t
JOIN arbitrio_fusion f ON f.id_origen = t.id_obligacion
GROUP BY t.id_transaccion, f.id_destino;

DELETE FROM operativo.transaccion_pago_detalle t USING arbitrio_fusion f WHERE t.id_obligacion = f.id_origen;

INSERT INTO operativo.transaccion_pago_detalle
    (id_transaccion, id_obligacion, monto_base, monto_descuento, id_beneficio)
SELECT id_transaccion, id_obligacion, monto_base, monto_descuento, id_beneficio FROM transaccion_detalle_fusion;

-- En el esquema operativo la obligación de SAMNET se referencia por texto, sin clave foránea.
INSERT INTO operativo.transaccion_obligacion (id_transaccion, id_obligacion)
SELECT DISTINCT t.id_transaccion, f.id_destino::TEXT
FROM operativo.transaccion_obligacion t
JOIN arbitrio_fusion f ON t.id_obligacion = f.id_origen::TEXT
ON CONFLICT (id_transaccion, id_obligacion) DO NOTHING;

DELETE FROM operativo.transaccion_obligacion t
USING arbitrio_fusion f
WHERE t.id_obligacion = f.id_origen::TEXT AND f.id_origen <> f.id_destino;

UPDATE operativo.aviso_correo a
SET id_obligacion = f.id_destino::TEXT
FROM arbitrio_fusion f
WHERE a.id_obligacion = f.id_origen::TEXT AND f.id_origen <> f.id_destino;

DELETE FROM samnet.obligacion o USING arbitrio_fusion f WHERE o.id = f.id_origen AND f.id_origen <> f.id_destino;

ALTER TABLE samnet.obligacion
    ADD CONSTRAINT ck_obligacion_tributo CHECK (tributo IN ('IMPUESTO_PREDIAL', 'ARBITRIOS'));

-- Hechos del ETL: misma fusión por lote, contribuyente, predio y cuota. La cuota fusionada queda vencida si alguna
-- parte lo estaba y pendiente si alguna seguía pendiente.
CREATE TEMPORARY TABLE hecho_fusion ON COMMIT DROP AS
SELECT h.id_hecho AS id_origen,
       min(h.id_hecho) OVER (PARTITION BY h.id_lote, h.seudonimo_contribuyente, h.id_predio, h.anio, h.cuota)
           AS id_destino
FROM analitica.hecho_cobranza h
WHERE h.tributo IN ('LIMPIEZA_PUBLICA', 'PARQUES_JARDINES', 'SEGURIDAD_CIUDADANA');

UPDATE analitica.hecho_cobranza d
SET tributo           = 'ARBITRIOS',
    fecha_vencimiento = s.fecha_vencimiento,
    monto_emitido     = s.monto_emitido,
    monto_insoluto    = s.monto_insoluto,
    monto_pagado      = s.monto_pagado,
    monto_descuento   = s.monto_descuento,
    estado_pago       = s.estado_pago
FROM (SELECT f.id_destino,
             min(h.fecha_vencimiento) AS fecha_vencimiento,
             sum(h.monto_emitido)     AS monto_emitido,
             sum(h.monto_insoluto)    AS monto_insoluto,
             sum(h.monto_pagado)      AS monto_pagado,
             sum(h.monto_descuento)   AS monto_descuento,
             CASE WHEN bool_or(h.estado_pago = 'VENCIDA') THEN 'VENCIDA'
                  WHEN bool_or(h.estado_pago = 'PENDIENTE') THEN 'PENDIENTE'
                  ELSE 'PAGADA' END   AS estado_pago
      FROM hecho_fusion f
      JOIN analitica.hecho_cobranza h ON h.id_hecho = f.id_origen
      GROUP BY f.id_destino) s
WHERE d.id_hecho = s.id_destino;

DELETE FROM analitica.hecho_cobranza h USING hecho_fusion f
WHERE h.id_hecho = f.id_origen AND f.id_origen <> f.id_destino;

ALTER TABLE analitica.hecho_cobranza
    ADD CONSTRAINT ck_hecho_cobranza_tributo CHECK (tributo IN ('IMPUESTO_PREDIAL', 'ARBITRIOS'));

-- Los índices por tributo ya calculados son porcentajes y no se pueden sumar: se borran con sus alertas y se
-- obtienen otra vez recalculando el lote (POST /api/indicadores/morosidad/calculos). Las metas sí se conservan.
DELETE FROM analitica.alerta_desvio a
USING analitica.valor_indicador v
WHERE a.id_valor = v.id_valor
  AND v.alcance = 'TRIBUTO'
  AND v.valor_alcance IN ('LIMPIEZA_PUBLICA', 'PARQUES_JARDINES', 'SEGURIDAD_CIUDADANA');

DELETE FROM analitica.valor_indicador
WHERE alcance = 'TRIBUTO'
  AND valor_alcance IN ('LIMPIEZA_PUBLICA', 'PARQUES_JARDINES', 'SEGURIDAD_CIUDADANA');

UPDATE analitica.meta_indicador
SET valor_alcance = 'ARBITRIOS'
WHERE alcance = 'TRIBUTO'
  AND valor_alcance IN ('LIMPIEZA_PUBLICA', 'PARQUES_JARDINES', 'SEGURIDAD_CIUDADANA');
