-- Datos ficticios de SAMNET sintético: cuotas de 2025 y 2026, pagos y etapas de cobranza, calculados como los
-- emite la Municipalidad de San Miguel (ver V31):
--   * Impuesto Predial por contribuyente: escala de la Ley de Tributación Municipal sobre la suma de sus predios al
--     1 de enero (por su porcentaje de propiedad), mínimo 0,6 % UIT, en 4 cuotas (feb, may, ago, nov). El pensionista
--     deduce 50 UIT de la base (art. 19) y no paga mínimo.
--   * Arbitrios por predio y mes, con el detalle de los 4 servicios; en copropiedad cada titular paga su porcentaje.
--     Un predio adquirido en el año paga arbitrios desde el mes siguiente.
-- Los pagos siguen el perfil de cada contribuyente a la fecha de los datos (2026-10-19): puntual anual, puntual con
-- atrasos, moroso, dejó de pagar, pago parcial y mixto. Lo impago y vencido genera interés moratorio (TIM) y entra
-- a la cobranza inductiva.
-- Es repetible: rehace la cuenta corriente de los contribuyentes de R__datos_prueba_01 cada vez que cambia este
-- archivo. Solo borra la de ellos: las semillas de datos-demo (p. ej. 099023 y 099024 de Niubiz) no se vuelven a
-- ejecutar al cambiar este archivo y perderían sus obligaciones y pagos.
CREATE TEMPORARY TABLE contribuyente_sintetico ON COMMIT DROP AS
SELECT codigo FROM (VALUES ('014502'), ('021390'), ('033218'), ('045067'), ('058934'), ('061177'), ('072405'),
                           ('080245')) AS c (codigo);

DELETE FROM samnet.seguimiento_cobranza s
USING samnet.obligacion o
WHERE o.id = s.obligacion_id AND o.contribuyente_codigo IN (SELECT codigo FROM contribuyente_sintetico);
DELETE FROM samnet.pago_obligacion po
USING samnet.obligacion o
WHERE o.id = po.id_obligacion AND o.contribuyente_codigo IN (SELECT codigo FROM contribuyente_sintetico);
DELETE FROM samnet.pago WHERE contribuyente_codigo IN (SELECT codigo FROM contribuyente_sintetico);
-- obligacion_servicio se borra en cascada con su obligación (V31).
DELETE FROM samnet.obligacion WHERE contribuyente_codigo IN (SELECT codigo FROM contribuyente_sintetico);

-- Tasas mensuales de arbitrios 2025 por servicio, categoría de uso y zona; 2026 se reajusta 3 %.
DELETE FROM samnet.tasa_arbitrio;
INSERT INTO samnet.tasa_arbitrio (anio, servicio, categoria_uso, zona, monto_mensual)
SELECT a.anio, t.servicio, t.categoria_uso, t.zona,
       round(t.monto * CASE a.anio WHEN 2026 THEN 1.03 ELSE 1 END, 2)
FROM (VALUES (2025), (2026)) AS a (anio)
CROSS JOIN (VALUES
    ('BARRIDO_CALLES', 'CASA_HABITACION', 'Z1', 9.80), ('BARRIDO_CALLES', 'CASA_HABITACION', 'Z2', 8.60), ('BARRIDO_CALLES', 'CASA_HABITACION', 'Z3', 7.40),
    ('RECOLECCION_RESIDUOS', 'CASA_HABITACION', 'Z1', 14.50), ('RECOLECCION_RESIDUOS', 'CASA_HABITACION', 'Z2', 13.20), ('RECOLECCION_RESIDUOS', 'CASA_HABITACION', 'Z3', 12.10),
    ('PARQUES_JARDINES', 'CASA_HABITACION', 'Z1', 8.90), ('PARQUES_JARDINES', 'CASA_HABITACION', 'Z2', 7.60), ('PARQUES_JARDINES', 'CASA_HABITACION', 'Z3', 6.30),
    ('SERENAZGO', 'CASA_HABITACION', 'Z1', 15.20), ('SERENAZGO', 'CASA_HABITACION', 'Z2', 13.80), ('SERENAZGO', 'CASA_HABITACION', 'Z3', 12.40),
    ('BARRIDO_CALLES', 'COMERCIO', 'Z1', 18.40), ('BARRIDO_CALLES', 'COMERCIO', 'Z2', 16.20), ('BARRIDO_CALLES', 'COMERCIO', 'Z3', 14.00),
    ('RECOLECCION_RESIDUOS', 'COMERCIO', 'Z1', 46.80), ('RECOLECCION_RESIDUOS', 'COMERCIO', 'Z2', 41.50), ('RECOLECCION_RESIDUOS', 'COMERCIO', 'Z3', 36.90),
    ('PARQUES_JARDINES', 'COMERCIO', 'Z1', 12.60), ('PARQUES_JARDINES', 'COMERCIO', 'Z2', 10.80), ('PARQUES_JARDINES', 'COMERCIO', 'Z3', 9.20),
    ('SERENAZGO', 'COMERCIO', 'Z1', 38.50), ('SERENAZGO', 'COMERCIO', 'Z2', 34.20), ('SERENAZGO', 'COMERCIO', 'Z3', 30.10),
    ('BARRIDO_CALLES', 'SERVICIOS', 'Z1', 16.20), ('BARRIDO_CALLES', 'SERVICIOS', 'Z2', 14.30), ('BARRIDO_CALLES', 'SERVICIOS', 'Z3', 12.50),
    ('RECOLECCION_RESIDUOS', 'SERVICIOS', 'Z1', 35.40), ('RECOLECCION_RESIDUOS', 'SERVICIOS', 'Z2', 31.60), ('RECOLECCION_RESIDUOS', 'SERVICIOS', 'Z3', 28.20),
    ('PARQUES_JARDINES', 'SERVICIOS', 'Z1', 11.40), ('PARQUES_JARDINES', 'SERVICIOS', 'Z2', 9.80), ('PARQUES_JARDINES', 'SERVICIOS', 'Z3', 8.30),
    ('SERENAZGO', 'SERVICIOS', 'Z1', 30.20), ('SERENAZGO', 'SERVICIOS', 'Z2', 26.90), ('SERENAZGO', 'SERVICIOS', 'Z3', 23.80),
    ('BARRIDO_CALLES', 'INDUSTRIA', 'Z1', 17.10), ('BARRIDO_CALLES', 'INDUSTRIA', 'Z2', 15.10), ('BARRIDO_CALLES', 'INDUSTRIA', 'Z3', 13.20),
    ('RECOLECCION_RESIDUOS', 'INDUSTRIA', 'Z1', 52.30), ('RECOLECCION_RESIDUOS', 'INDUSTRIA', 'Z2', 46.70), ('RECOLECCION_RESIDUOS', 'INDUSTRIA', 'Z3', 41.40),
    ('PARQUES_JARDINES', 'INDUSTRIA', 'Z1', 10.20), ('PARQUES_JARDINES', 'INDUSTRIA', 'Z2', 8.80), ('PARQUES_JARDINES', 'INDUSTRIA', 'Z3', 7.40),
    ('SERENAZGO', 'INDUSTRIA', 'Z1', 34.60), ('SERENAZGO', 'INDUSTRIA', 'Z2', 30.80), ('SERENAZGO', 'INDUSTRIA', 'Z3', 27.20),
    ('BARRIDO_CALLES', 'TERRENO_SIN_CONSTRUIR', 'Z1', 6.20), ('BARRIDO_CALLES', 'TERRENO_SIN_CONSTRUIR', 'Z2', 5.40), ('BARRIDO_CALLES', 'TERRENO_SIN_CONSTRUIR', 'Z3', 4.70),
    ('RECOLECCION_RESIDUOS', 'TERRENO_SIN_CONSTRUIR', 'Z1', 3.10), ('RECOLECCION_RESIDUOS', 'TERRENO_SIN_CONSTRUIR', 'Z2', 2.80), ('RECOLECCION_RESIDUOS', 'TERRENO_SIN_CONSTRUIR', 'Z3', 2.50),
    ('PARQUES_JARDINES', 'TERRENO_SIN_CONSTRUIR', 'Z1', 5.60), ('PARQUES_JARDINES', 'TERRENO_SIN_CONSTRUIR', 'Z2', 4.80), ('PARQUES_JARDINES', 'TERRENO_SIN_CONSTRUIR', 'Z3', 4.10),
    ('SERENAZGO', 'TERRENO_SIN_CONSTRUIR', 'Z1', 9.40), ('SERENAZGO', 'TERRENO_SIN_CONSTRUIR', 'Z2', 8.50), ('SERENAZGO', 'TERRENO_SIN_CONSTRUIR', 'Z3', 7.60)
) AS t (servicio, categoria_uso, zona, monto);

-- Impuesto Predial: base de cada contribuyente al 1 de enero de cada año y su impuesto anual en 4 cuotas.
WITH base AS (
    SELECT t.contribuyente_codigo, a.anio, u.valor AS uit, c.regimen,
           sum(p.base_imponible * t.porcentaje_propiedad / 100) AS base_imponible
    FROM samnet.predio_titular t
    JOIN samnet.predio p ON p.codigo_predio = t.codigo_predio
    JOIN samnet.contribuyente c ON c.codigo = t.contribuyente_codigo
    CROSS JOIN (VALUES (2025), (2026)) AS a (anio)
    JOIN samnet.uit u ON u.anio = a.anio
    WHERE coalesce(p.fecha_adquisicion, DATE '1900-01-01') < make_date(a.anio, 1, 1)
      AND p.fecha_baja IS NULL
    GROUP BY t.contribuyente_codigo, a.anio, u.valor, c.regimen
),
afecta AS (
    SELECT b.*,
           CASE WHEN b.regimen = 'PENSIONISTA' THEN greatest(b.base_imponible - 50 * b.uit, 0)
                ELSE b.base_imponible END AS base_afecta
    FROM base b
),
impuesto AS (
    SELECT f.contribuyente_codigo, f.anio,
           CASE WHEN f.regimen = 'PENSIONISTA' AND f.base_afecta = 0 THEN 0
                ELSE greatest(round(
                       least(f.base_afecta, 15 * f.uit) * 0.002
                     + least(greatest(f.base_afecta - 15 * f.uit, 0), 45 * f.uit) * 0.006
                     + greatest(f.base_afecta - 60 * f.uit, 0) * 0.010, 2),
                     CASE WHEN f.regimen = 'PENSIONISTA' THEN 0 ELSE round(f.uit * 0.006, 2) END)
           END AS anual
    FROM afecta f
)
INSERT INTO samnet.obligacion
    (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido, monto_pagado)
SELECT i.contribuyente_codigo, NULL, 'IMPUESTO_PREDIAL', i.anio, c.cuota,
       samnet.fn_ultimo_dia_habil(i.anio, c.cuota * 3 - 1),
       -- La primera cuota absorbe el redondeo para que las cuatro sumen el impuesto anual.
       CASE WHEN c.cuota = 1 THEN i.anual - 3 * round(i.anual / 4, 2) ELSE round(i.anual / 4, 2) END,
       0
FROM impuesto i
CROSS JOIN generate_series(1, 4) AS c (cuota)
WHERE i.anual > 0
ORDER BY i.contribuyente_codigo, i.anio, c.cuota;

-- Arbitrios: monto de cada servicio por titular, predio y mes. Fuera de vivienda, la recolección crece con el área.
CREATE TEMPORARY TABLE arbitrio_calculado ON COMMIT DROP AS
SELECT t.contribuyente_codigo, p.codigo_predio, a.anio, m.mes, ta.servicio,
       round(ta.monto_mensual
             * CASE WHEN ta.servicio = 'RECOLECCION_RESIDUOS' AND cat.categoria <> 'CASA_HABITACION'
                    THEN greatest(1, round(p.area_terreno / 200, 2)) ELSE 1 END
             * t.porcentaje_propiedad / 100, 2) AS monto
FROM samnet.predio_titular t
JOIN samnet.predio p ON p.codigo_predio = t.codigo_predio
CROSS JOIN LATERAL (SELECT CASE
        WHEN p.uso_predio = 'Vivienda' THEN 'CASA_HABITACION'
        WHEN p.uso_predio = 'Comercio' THEN 'COMERCIO'
        WHEN p.uso_predio = 'Industria' THEN 'INDUSTRIA'
        WHEN p.uso_predio = 'Terreno sin construir' THEN 'TERRENO_SIN_CONSTRUIR'
        ELSE 'SERVICIOS' END AS categoria) cat
CROSS JOIN (VALUES (2025), (2026)) AS a (anio)
CROSS JOIN generate_series(1, 12) AS m (mes)
JOIN samnet.tasa_arbitrio ta
  ON ta.anio = a.anio AND ta.categoria_uso = cat.categoria AND ta.zona = p.zona
WHERE make_date(a.anio, m.mes, 1) > coalesce(p.fecha_adquisicion, DATE '1900-01-01')
  AND p.fecha_baja IS NULL;

WITH cuotas AS (
    SELECT contribuyente_codigo, codigo_predio, anio, mes, sum(monto) AS monto
    FROM arbitrio_calculado
    GROUP BY contribuyente_codigo, codigo_predio, anio, mes
),
insertadas AS (
    INSERT INTO samnet.obligacion
        (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido,
         monto_pagado)
    SELECT c.contribuyente_codigo, c.codigo_predio, 'ARBITRIOS', c.anio, c.mes,
           -- Enero y febrero vencen a fin de febrero; los demás meses, al cierre de su mes.
           samnet.fn_ultimo_dia_habil(c.anio, greatest(c.mes, 2)), c.monto, 0
    FROM cuotas c
    ORDER BY c.contribuyente_codigo, c.anio, c.mes, c.codigo_predio
    RETURNING id, contribuyente_codigo, codigo_predio, anio, numero_cuota
)
INSERT INTO samnet.obligacion_servicio (id_obligacion, servicio, monto)
SELECT i.id, a.servicio, a.monto
FROM insertadas i
JOIN arbitrio_calculado a
  ON a.contribuyente_codigo = i.contribuyente_codigo AND a.codigo_predio = i.codigo_predio
 AND a.anio = i.anio AND a.mes = i.numero_cuota;

-- Perfil de pago de cada contribuyente: qué se pagó, cuándo y cuánto. Lo no listado queda impago.
--   014502 y 033218: pagan todo el año por adelantado en febrero (sin deuda: pueden pedir constancia).
--   045067: paga cada cuota unos días antes; algunas con atraso. Desde setiembre de 2026 no paga.
--   021390: moroso. Pagó hasta julio de 2025 (con atrasos) y luego nada: deuda en cobranza coactiva.
--   058934: pagó todo 2025 a tiempo y dejó de pagar en 2026.
--   061177: pagó 2025; en 2026 abonó la mitad de la 1.ª cuota del predial y enero a marzo de arbitrios con atraso.
--   080245: paga a tiempo hasta junio de 2026; luego no paga la 3.ª cuota del predial ni los arbitrios de 7 predios.
CREATE TEMPORARY TABLE plan_pago ON COMMIT DROP AS
SELECT o.id, o.contribuyente_codigo,
       CASE
           WHEN o.contribuyente_codigo IN ('014502', '033218') THEN make_date(o.anio, 2, 20)
           WHEN o.contribuyente_codigo = '045067' AND o.fecha_vencimiento <= DATE '2026-08-31' THEN
               o.fecha_vencimiento + CASE WHEN o.numero_cuota IN (3, 7) THEN 12 ELSE -3 END
           WHEN o.contribuyente_codigo = '021390' AND o.fecha_vencimiento <= DATE '2025-07-31' THEN
               o.fecha_vencimiento + CASE WHEN o.numero_cuota % 2 = 0 THEN 18 ELSE -2 END
           WHEN o.contribuyente_codigo IN ('058934', '061177') AND o.anio = 2025 THEN o.fecha_vencimiento - 4
           WHEN o.contribuyente_codigo = '061177' AND o.anio = 2026 AND o.tributo = 'IMPUESTO_PREDIAL'
                AND o.numero_cuota = 1 THEN DATE '2026-03-16'
           WHEN o.contribuyente_codigo = '061177' AND o.anio = 2026 AND o.tributo = 'ARBITRIOS'
                AND o.numero_cuota <= 3 THEN o.fecha_vencimiento + 15
           WHEN o.contribuyente_codigo = '080245' AND o.fecha_vencimiento <= DATE '2026-06-30' THEN
               o.fecha_vencimiento - 5
           WHEN o.contribuyente_codigo = '080245' AND o.tributo = 'ARBITRIOS'
                AND o.codigo_predio < '080245-25' AND o.fecha_vencimiento <= DATE '2026-09-30' THEN
               o.fecha_vencimiento - 5
       END AS fecha_pago,
       CASE WHEN o.contribuyente_codigo = '061177' AND o.anio = 2026 AND o.tributo = 'IMPUESTO_PREDIAL'
                 AND o.numero_cuota = 1 THEN round(o.monto_emitido / 2, 2)
            ELSE o.monto_emitido END AS monto
FROM samnet.obligacion o;

DELETE FROM plan_pago WHERE fecha_pago IS NULL OR fecha_pago > DATE '2026-10-19';

UPDATE samnet.obligacion o SET monto_pagado = p.monto FROM plan_pago p WHERE p.id = o.id;

-- Un recibo por contribuyente y día de pago; el canal alterna entre ventanilla y pago en línea.
INSERT INTO samnet.pago
    (id_pago, contribuyente_codigo, fecha_pago, canal, monto_pagado, monto_correspondiente, id_beneficio)
SELECT 'REC-' || p.contribuyente_codigo || '-' || to_char(p.fecha_pago, 'YYYYMMDD'), p.contribuyente_codigo,
       p.fecha_pago, CASE WHEN EXTRACT(MONTH FROM p.fecha_pago)::INTEGER % 2 = 0 THEN 'EN_LINEA' ELSE 'VENTANILLA' END,
       sum(p.monto), sum(p.monto), NULL
FROM plan_pago p
GROUP BY p.contribuyente_codigo, p.fecha_pago;

INSERT INTO samnet.pago_obligacion (id_pago, id_obligacion, monto_aplicado)
SELECT 'REC-' || p.contribuyente_codigo || '-' || to_char(p.fecha_pago, 'YYYYMMDD'), p.id, p.monto
FROM plan_pago p;

-- Cobranza inductiva de las deudas vencidas más antiguas (RF-014); las demás están en mora temprana desde el día
-- siguiente a su vencimiento. Los valores se emiten por tributo y periodo: OP para el Impuesto Predial y RD para
-- los arbitrios.
INSERT INTO samnet.seguimiento_cobranza
    (obligacion_id, etapa, fecha_inicio, documento_referencia, observacion, fecha_registro)
SELECT o.id, s.etapa, s.fecha_inicio, s.documento_referencia, s.observacion, s.fecha_inicio + TIME '09:00'
FROM (VALUES
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2025, 3, 'CAMPANA', DATE '2025-10-06', NULL, 'Carta inductiva de la campaña de octubre'),
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2025, 3, 'VALOR_EMITIDO', DATE '2025-10-20', 'OP-2025-000814', NULL),
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2025, 3, 'ESPERA_LEGAL', DATE '2025-11-03', 'OP-2025-000814', 'Notificado en el domicilio fiscal'),
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2025, 3, 'COACTIVA', DATE '2025-12-02', 'OP-2025-000814', 'Venció la espera legal sin pago ni reclamación'),
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2025, 4, 'CAMPANA', DATE '2026-01-12', NULL, 'Incluida en la campaña de cobranza de enero'),
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2025, 4, 'VALOR_EMITIDO', DATE '2026-01-20', 'OP-2026-000101', NULL),
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2025, 4, 'ESPERA_LEGAL', DATE '2026-02-03', 'OP-2026-000101', 'Notificado en el domicilio fiscal'),
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2025, 4, 'COACTIVA', DATE '2026-03-04', 'OP-2026-000101', 'Venció la espera legal sin pago ni reclamación'),
    ('021390', 'ARBITRIOS', '021390-01', 2025, 8, 'VALOR_EMITIDO', DATE '2026-02-10', 'RD-2026-000310', NULL),
    ('021390', 'ARBITRIOS', '021390-01', 2025, 8, 'ESPERA_LEGAL', DATE '2026-02-24', 'RD-2026-000310', 'Notificado en el domicilio fiscal'),
    ('021390', 'IMPUESTO_PREDIAL', NULL, 2026, 1, 'VALOR_EMITIDO', DATE '2026-05-05', 'OP-2026-000388', NULL),
    ('058934', 'IMPUESTO_PREDIAL', NULL, 2026, 1, 'MORA_TEMPRANA', DATE '2026-03-10', NULL, 'Aviso de cobranza enviado por correo'),
    ('058934', 'IMPUESTO_PREDIAL', NULL, 2026, 1, 'VALOR_EMITIDO', DATE '2026-06-01', 'OP-2026-000502', NULL),
    ('045067', 'ARBITRIOS', '045067-01', 2026, 9, 'MORA_TEMPRANA', DATE '2026-10-01', NULL, 'Recordatorio por correo y SMS')
) AS s (contribuyente_codigo, tributo, codigo_predio, anio, numero_cuota, etapa, fecha_inicio, documento_referencia,
        observacion)
JOIN samnet.obligacion o
  ON o.contribuyente_codigo = s.contribuyente_codigo
 AND o.tributo = s.tributo
 AND o.codigo_predio IS NOT DISTINCT FROM s.codigo_predio
 AND o.anio = s.anio
 AND o.numero_cuota = s.numero_cuota
ORDER BY o.id, s.fecha_inicio;
