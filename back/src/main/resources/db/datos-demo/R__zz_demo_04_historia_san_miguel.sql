-- Solo perfil local. Historia tributaria ficticia de San Miguel (Lima) para que los indicadores se puedan consultar
-- por año y mes (HU008, HU011, HU039):
--   1. UIT y tarifas de arbitrios de 2018 a 2024 (las de 2025 y 2026 están en R__datos_prueba_03).
--   2. 32 contribuyentes nuevos repartidos en los cinco sectores del distrito (Costanera, Maranga, Pando,
--      Las Leyendas y Faucett - Libertad), con su predio, declaración jurada y perfil de pago.
--   3. Cuotas de 2018 a 2026 de los nuevos y de 2018 a 2024 de los contribuyentes de R__datos_prueba_01, calculadas
--      igual que R__datos_prueba_03 (predial por escala de la UIT en 4 cuotas; arbitrios por predio y mes).
--   4. Pagos según el perfil, con el efecto del estado de emergencia de 2020, y cobranza de las cuotas impagas.
--   5. Una carga del ETL por cierre de mes desde enero de 2019 y otra el 2026-11-04: cada una ejecuta el ETL real
--      (etl.ejecutar_lote: SAMNET → bronce → plata → oro) con esa fecha de corte, así que es la foto de la cuenta
--      corriente a ese día (lo pagado hasta entonces) con todas las cuotas hasta el año de corte.
-- Nombres, documentos, direcciones y montos son ficticios. Los sectores agrupan urbanizaciones reales del distrito,
-- pero no son la zonificación de la ordenanza de arbitrios. Es repetible: rehace todo lo suyo cada vez que cambia.

-- 1. UIT (D. S. de cada año) y tarifas históricas: las de 2025 deflactadas por la variación de precios acumulada.
INSERT INTO samnet.uit (anio, valor)
VALUES (2018, 4150.00), (2019, 4200.00), (2020, 4300.00), (2021, 4400.00), (2022, 4600.00), (2023, 4950.00),
       (2024, 5150.00)
ON CONFLICT (anio) DO NOTHING;

DELETE FROM samnet.tasa_arbitrio WHERE anio BETWEEN 2018 AND 2024;
INSERT INTO samnet.tasa_arbitrio (anio, servicio, categoria_uso, zona, monto_mensual)
SELECT f.anio, t.servicio, t.categoria_uso, t.zona, round(t.monto_mensual * f.factor, 2)
FROM samnet.tasa_arbitrio t
CROSS JOIN (VALUES (2018, 0.78), (2019, 0.795), (2020, 0.81), (2021, 0.83), (2022, 0.88), (2023, 0.945),
                   (2024, 0.975)) AS f (anio, factor)
WHERE t.anio = 2025;

-- 2. Contribuyentes nuevos: uno o dos predios cada uno. perfil: PUNTUAL, ANUAL (todo el año en febrero),
-- ATRASADO, RECUPERA (deja el último tramo del año y lo regulariza en marzo siguiente), MOROSO (deja de pagar desde
-- desde_anio) y PARCIAL (paga el predial y solo el primer semestre de arbitrios).
CREATE TEMPORARY TABLE demo_sm_nuevo ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('110101', 'SALAZAR RÍOS, CARMEN ROSA', 'NATURAL', 'DNI', '40612387', 'Costanera', 'Av. Costanera 1640 Dpto. 502', 'Vivienda', 85.00, 265000.00, DATE '2014-03-12', 'PUNTUAL', NULL),
    ('110102', 'CHÁVEZ NÚÑEZ, LUIS ALBERTO', 'NATURAL', 'DNI', '09876521', 'Costanera', 'Av. Bertolotto 455', 'Vivienda', 140.00, 310000.00, DATE '2009-07-01', 'ANUAL', NULL),
    ('110103', 'RESTAURANTE EL MIRADOR DEL PACÍFICO S.A.C.', 'JURIDICA', 'RUC', '20545671238', 'Costanera', 'Av. Costanera 2110', 'Comercio', 320.00, 690000.00, DATE '2016-05-20', 'ATRASADO', NULL),
    ('110104', 'PAREDES LUNA, JORGE', 'NATURAL', 'DNI', '41558820', 'Costanera', 'Av. Rafael Escardó 1015', 'Vivienda', 120.00, 240000.00, DATE '2018-10-02', 'PUNTUAL', NULL),
    ('110105', 'HOSTAL BRISAS MARINAS E.I.R.L.', 'JURIDICA', 'RUC', '20601987452', 'Costanera', 'Av. Costanera 1890', 'Servicios', 260.00, 520000.00, DATE '2015-01-15', 'RECUPERA', NULL),
    ('110106', 'QUISPE MAMANI, ROSA ELVIRA', 'NATURAL', 'DNI', '10293847', 'Costanera', 'Av. Bertolotto 780', 'Vivienda', 110.00, 198000.00, DATE '2006-11-30', 'MOROSO', 2023),
    ('110201', 'FERNÁNDEZ GUTIÉRREZ, ANA LUCÍA', 'NATURAL', 'DNI', '42178935', 'Maranga', 'Av. Precursores 451, Urb. Maranga', 'Vivienda', 160.00, 285000.00, DATE '2011-04-18', 'PUNTUAL', NULL),
    ('110202', 'TORRES VEGA, MIGUEL ÁNGEL', 'NATURAL', 'DNI', '07845123', 'Maranga', 'Av. Universitaria 1245, Urb. Maranga', 'Vivienda', 150.00, 270000.00, DATE '2005-08-09', 'PUNTUAL', NULL),
    ('110203', 'BOTICA SANTA ROSA DE MARANGA S.A.C.', 'JURIDICA', 'RUC', '20534567812', 'Maranga', 'Av. La Marina 2580, Urb. Maranga', 'Comercio', 95.00, 330000.00, DATE '2017-02-14', 'ATRASADO', NULL),
    ('110204', 'ROJAS HUAMÁN, ELENA', 'NATURAL', 'DNI', '43901276', 'Maranga', 'Calle Los Tulipanes 368, Urb. Maranga', 'Vivienda', 130.00, 230000.00, DATE '2012-06-22', 'ANUAL', NULL),
    ('110205', 'CASTRO DÍAZ, PEDRO PABLO', 'NATURAL', 'DNI', '40011223', 'Maranga', 'Av. Precursores 870, Urb. Maranga', 'Vivienda', 145.00, 250000.00, DATE '2019-11-05', 'ATRASADO', NULL),
    ('110206', 'INSTITUTO DE IDIOMAS MARANGA S.A.C.', 'JURIDICA', 'RUC', '20512398761', 'Maranga', 'Av. Universitaria 1580, Urb. Maranga', 'Educación', 400.00, 780000.00, DATE '2013-09-01', 'RECUPERA', NULL),
    ('110207', 'MENDOZA PRADO, SILVIA', 'NATURAL', 'DNI', '44556677', 'Maranga', 'Calle Las Azucenas 214, Urb. Maranga', 'Vivienda', 120.00, 215000.00, DATE '2016-12-12', 'MOROSO', 2024),
    ('110301', 'HUAMANÍ CCORI, JULIO CÉSAR', 'NATURAL', 'DNI', '41239876', 'Pando', 'Calle Los Geranios 132, Urb. Pando', 'Vivienda', 120.00, 165000.00, DATE '2008-02-28', 'PUNTUAL', NULL),
    ('110302', 'VILCHEZ SOTO, MARTHA', 'NATURAL', 'DNI', '09543218', 'Pando', 'Av. Universitaria 650, Urb. Pando', 'Vivienda', 100.00, 150000.00, DATE '2010-05-17', 'ATRASADO', NULL),
    ('110303', 'FERRETERÍA EL CONSTRUCTOR DE PANDO E.I.R.L.', 'JURIDICA', 'RUC', '20607654321', 'Pando', 'Av. Venezuela 3420, Urb. Pando', 'Comercio', 210.00, 380000.00, DATE '2014-08-04', 'ATRASADO', NULL),
    ('110304', 'LEÓN CÁRDENAS, RAÚL', 'NATURAL', 'DNI', '42387451', 'Pando', 'Calle Los Claveles 287, Urb. Pando', 'Vivienda', 110.00, 158000.00, DATE '2015-10-10', 'RECUPERA', NULL),
    ('110305', 'ÑAHUI QUISPE, FLOR DE MARÍA', 'NATURAL', 'DNI', '46712309', 'Pando', 'Calle Las Magnolias 95, Urb. Pando', 'Vivienda', 90.00, 132000.00, DATE '2017-03-03', 'RECUPERA', NULL),
    ('110306', 'TALLER MECÁNICO HERMANOS ALVA S.R.L.', 'JURIDICA', 'RUC', '20498765432', 'Pando', 'Av. Venezuela 3780, Urb. Pando', 'Industria', 450.00, 520000.00, DATE '2012-01-20', 'MOROSO', 2021),
    ('110307', 'CHUQUIMIA APAZA, BENITO', 'NATURAL', 'DNI', '08812345', 'Pando', 'Calle Los Lirios 410, Urb. Pando', 'Vivienda', 105.00, 140000.00, DATE '2011-07-07', 'PARCIAL', NULL),
    ('110401', 'VALDIVIA REYES, CARLOS ENRIQUE', 'NATURAL', 'DNI', '10456789', 'Las Leyendas', 'Calle Las Palmeras 156, Urb. Las Leyendas', 'Vivienda', 130.00, 205000.00, DATE '2010-09-15', 'PUNTUAL', NULL),
    ('110402', 'GÓMEZ ARANA, PATRICIA', 'NATURAL', 'DNI', '43215678', 'Las Leyendas', 'Calle Los Robles 278, Urb. Las Leyendas', 'Vivienda', 120.00, 190000.00, DATE '2013-04-04', 'PUNTUAL', NULL),
    ('110403', 'MINIMARKET LAS LEYENDAS S.A.C.', 'JURIDICA', 'RUC', '20556781234', 'Las Leyendas', 'Calle Los Olivos 512, Urb. Las Leyendas', 'Comercio', 140.00, 260000.00, DATE '2018-06-18', 'ATRASADO', NULL),
    ('110404', 'RAMÍREZ ESPINOZA, VÍCTOR', 'NATURAL', 'DNI', '07123456', 'Las Leyendas', 'Calle Los Sauces 89, Urb. Las Leyendas', 'Vivienda', 115.00, 175000.00, DATE '2007-12-01', 'RECUPERA', NULL),
    ('110405', 'HUERTA BALLÓN, DORIS', 'NATURAL', 'DNI', '40987123', 'Las Leyendas', 'Calle Los Cipreses 340, Urb. Las Leyendas', 'Vivienda', 125.00, 182000.00, DATE '2014-02-11', 'MOROSO', 2022),
    ('110406', 'INVERSIONES NAVARRO & HIJOS S.A.C.', 'JURIDICA', 'RUC', '20523456789', 'Las Leyendas', 'Calle Los Álamos 201, Urb. Las Leyendas', 'Oficina', 180.00, 300000.00, DATE '2016-08-30', 'PARCIAL', NULL),
    ('110501', 'DISTRIBUIDORA FAUCETT S.A.C.', 'JURIDICA', 'RUC', '20511223344', 'Faucett - Libertad', 'Av. Elmer Faucett 1820', 'Industria', 800.00, 1150000.00, DATE '2010-03-01', 'PUNTUAL', NULL),
    ('110502', 'PALOMINO SÁNCHEZ, HÉCTOR', 'NATURAL', 'DNI', '09654321', 'Faucett - Libertad', 'Av. Libertad 1450', 'Vivienda', 120.00, 160000.00, DATE '2009-05-25', 'ATRASADO', NULL),
    ('110503', 'GRIFO SERVICENTRO LIBERTAD E.I.R.L.', 'JURIDICA', 'RUC', '20600112233', 'Faucett - Libertad', 'Av. Libertad 2010', 'Comercio', 650.00, 980000.00, DATE '2012-11-11', 'MOROSO', 2020),
    ('110504', 'CAMPOS ORTIZ, ISABEL', 'NATURAL', 'DNI', '41122334', 'Faucett - Libertad', 'Av. Libertad 1688 Dpto. 301', 'Vivienda', 80.00, 128000.00, DATE '2019-04-15', 'PARCIAL', NULL),
    ('110505', 'ALMACENES DEL OESTE S.A.', 'JURIDICA', 'RUC', '20100223344', 'Faucett - Libertad', 'Av. Elmer Faucett 2560', 'Industria', 1200.00, 1650000.00, DATE '2008-07-01', 'RECUPERA', NULL),
    ('110506', 'SOTO LÓPEZ, ERNESTO', 'NATURAL', 'DNI', '08234567', 'Faucett - Libertad', 'Av. Elmer Faucett 1355', 'Vivienda', 95.00, 135000.00, DATE '2006-10-10', 'ANUAL', NULL)
) AS n (codigo, nombre, tipo_persona, tipo_documento, numero_documento, zona, via, uso_predio, area_terreno,
        base_imponible, fecha_adquisicion, perfil, desde_anio);

INSERT INTO samnet.contribuyente
    (codigo, nombre, tipo_persona, tipo_documento, numero_documento, domicilio_fiscal, estado, regimen, condicion,
     correo, telefonos, dom_departamento, dom_provincia, dom_distrito, dom_via, dom_numero)
SELECT n.codigo, n.nombre, n.tipo_persona, n.tipo_documento, n.numero_documento, n.via || ', San Miguel', 'ACTIVO',
       'GENERAL', 'HABIDO', lower(n.codigo) || '@correo.test', '9870' || n.codigo, 'LIMA', 'LIMA', 'SAN MIGUEL',
       regexp_replace(split_part(n.via, ',', 1), ' [0-9].*$', ''),
       substring(split_part(n.via, ',', 1) FROM '([0-9]+)')
FROM demo_sm_nuevo n
ON CONFLICT (codigo) DO UPDATE SET nombre = EXCLUDED.nombre, domicilio_fiscal = EXCLUDED.domicilio_fiscal,
    dom_via = EXCLUDED.dom_via, dom_numero = EXCLUDED.dom_numero;

INSERT INTO samnet.predio
    (codigo_predio, numero_catastral, ubicacion, uso_predio, tipo_predio, area_terreno, base_imponible,
     fecha_adquisicion, zona)
SELECT n.codigo || '-01', '150136-' || substring(n.codigo FROM 3 FOR 2) || '0-' || substring(n.codigo FROM 5 FOR 2) || '1',
       n.via || ', San Miguel', n.uso_predio, 'URBANO', n.area_terreno, n.base_imponible, n.fecha_adquisicion, n.zona
FROM demo_sm_nuevo n
ON CONFLICT (codigo_predio) DO UPDATE SET ubicacion = EXCLUDED.ubicacion, zona = EXCLUDED.zona,
    uso_predio = EXCLUDED.uso_predio, area_terreno = EXCLUDED.area_terreno,
    base_imponible = EXCLUDED.base_imponible, fecha_adquisicion = EXCLUDED.fecha_adquisicion;

INSERT INTO samnet.predio_titular (codigo_predio, contribuyente_codigo, condicion_propiedad, porcentaje_propiedad)
SELECT n.codigo || '-01', n.codigo, 'PROPIETARIO', 100.00
FROM demo_sm_nuevo n
ON CONFLICT (codigo_predio, contribuyente_codigo) DO NOTHING;

INSERT INTO samnet.declaracion_jurada
    (id_declaracion, codigo_predio, anio, autoavaluo, motivo, submotivo, tipo_declarante, estado, es_vigente)
SELECT 'DJ-' || EXTRACT(YEAR FROM n.fecha_adquisicion) || '-' || n.codigo, n.codigo || '-01',
       EXTRACT(YEAR FROM n.fecha_adquisicion)::INTEGER, n.base_imponible, 'INSCRIPCION', 'COMPRA-VENTA',
       CASE n.tipo_persona WHEN 'JURIDICA' THEN 'REPRESENTANTE LEGAL' ELSE 'PROPIETARIO' END, 'PROCESADA', TRUE
FROM demo_sm_nuevo n
ON CONFLICT (id_declaracion) DO NOTHING;

-- Perfil de pago de todos los contribuyentes con historia. Los de R__datos_prueba_01 pagaron todo hasta 2024.
CREATE TEMPORARY TABLE demo_sm_perfil ON COMMIT DROP AS
SELECT codigo, perfil, desde_anio, 2018 AS anio_desde, 2026 AS anio_hasta FROM demo_sm_nuevo
UNION ALL
SELECT v.codigo, v.perfil, NULL, 2018, 2024
FROM (VALUES ('014502', 'ANUAL'), ('033218', 'ANUAL'), ('058934', 'PUNTUAL'), ('061177', 'PUNTUAL'),
             ('080245', 'PUNTUAL'), ('045067', 'ATRASADO_LEVE'), ('021390', 'ATRASADO_LEVE')) AS v (codigo, perfil);

-- Rehace lo que administra este archivo: todas las cuotas de los nuevos y las de 2018 a 2024 de los demás.
CREATE TEMPORARY TABLE demo_sm_borrar ON COMMIT DROP AS
SELECT o.id FROM samnet.obligacion o
JOIN demo_sm_perfil p ON p.codigo = o.contribuyente_codigo AND o.anio BETWEEN p.anio_desde AND p.anio_hasta;

DELETE FROM samnet.seguimiento_cobranza WHERE obligacion_id IN (SELECT id FROM demo_sm_borrar);
DELETE FROM samnet.pago_obligacion WHERE id_obligacion IN (SELECT id FROM demo_sm_borrar);
DELETE FROM samnet.pago p
WHERE p.id_pago LIKE 'RH-%'
  AND NOT EXISTS (SELECT 1 FROM samnet.pago_obligacion po WHERE po.id_pago = p.id_pago);
DELETE FROM samnet.obligacion WHERE id IN (SELECT id FROM demo_sm_borrar);

-- 3. Impuesto Predial (misma escala que R__datos_prueba_03).
WITH base AS (
    SELECT t.contribuyente_codigo, a.anio, u.valor AS uit, c.regimen,
           sum(p.base_imponible * t.porcentaje_propiedad / 100) AS base_imponible
    FROM samnet.predio_titular t
    JOIN samnet.predio p ON p.codigo_predio = t.codigo_predio
    JOIN samnet.contribuyente c ON c.codigo = t.contribuyente_codigo
    JOIN demo_sm_perfil pf ON pf.codigo = t.contribuyente_codigo
    CROSS JOIN LATERAL generate_series(pf.anio_desde, pf.anio_hasta) AS a (anio)
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
       CASE WHEN c.cuota = 1 THEN i.anual - 3 * round(i.anual / 4, 2) ELSE round(i.anual / 4, 2) END,
       0
FROM impuesto i
CROSS JOIN generate_series(1, 4) AS c (cuota)
WHERE i.anual > 0
ORDER BY i.contribuyente_codigo, i.anio, c.cuota;

-- Arbitrios por titular, predio, mes y servicio (misma regla que R__datos_prueba_03).
CREATE TEMPORARY TABLE demo_sm_arbitrio ON COMMIT DROP AS
SELECT t.contribuyente_codigo, p.codigo_predio, a.anio, m.mes, ta.servicio,
       round(ta.monto_mensual
             * CASE WHEN ta.servicio = 'RECOLECCION_RESIDUOS' AND cat.categoria <> 'CASA_HABITACION'
                    THEN greatest(1, round(p.area_terreno / 200, 2)) ELSE 1 END
             * t.porcentaje_propiedad / 100, 2) AS monto
FROM samnet.predio_titular t
JOIN samnet.predio p ON p.codigo_predio = t.codigo_predio
JOIN demo_sm_perfil pf ON pf.codigo = t.contribuyente_codigo
CROSS JOIN LATERAL (SELECT CASE
        WHEN p.uso_predio = 'Vivienda' THEN 'CASA_HABITACION'
        WHEN p.uso_predio = 'Comercio' THEN 'COMERCIO'
        WHEN p.uso_predio = 'Industria' THEN 'INDUSTRIA'
        WHEN p.uso_predio = 'Terreno sin construir' THEN 'TERRENO_SIN_CONSTRUIR'
        ELSE 'SERVICIOS' END AS categoria) cat
CROSS JOIN LATERAL generate_series(pf.anio_desde, pf.anio_hasta) AS a (anio)
CROSS JOIN generate_series(1, 12) AS m (mes)
JOIN samnet.tasa_arbitrio ta
  ON ta.anio = a.anio AND ta.categoria_uso = cat.categoria AND ta.zona = p.zona
WHERE make_date(a.anio, m.mes, 1) > coalesce(p.fecha_adquisicion, DATE '1900-01-01')
  AND p.fecha_baja IS NULL;

WITH cuotas AS (
    SELECT contribuyente_codigo, codigo_predio, anio, mes, sum(monto) AS monto
    FROM demo_sm_arbitrio
    GROUP BY contribuyente_codigo, codigo_predio, anio, mes
),
insertadas AS (
    INSERT INTO samnet.obligacion
        (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido,
         monto_pagado)
    SELECT c.contribuyente_codigo, c.codigo_predio, 'ARBITRIOS', c.anio, c.mes,
           samnet.fn_ultimo_dia_habil(c.anio, greatest(c.mes, 2)), c.monto, 0
    FROM cuotas c
    ORDER BY c.contribuyente_codigo, c.anio, c.mes, c.codigo_predio
    RETURNING id, contribuyente_codigo, codigo_predio, anio, numero_cuota
)
INSERT INTO samnet.obligacion_servicio (id_obligacion, servicio, monto)
SELECT i.id, a.servicio, a.monto
FROM insertadas i
JOIN demo_sm_arbitrio a
  ON a.contribuyente_codigo = i.contribuyente_codigo AND a.codigo_predio = i.codigo_predio
 AND a.anio = i.anio AND a.mes = i.numero_cuota;

-- 4. Pagos. h es una variación estable por cuota para que las fechas no sean todas iguales: el puntual se atrasa a
-- veces, el atrasado deja algunas cuotas meses impagas y el moroso nunca pagó algunas antes de dejar de pagar. En 2020,
-- durante el
-- estado de emergencia, quienes no pagaban por adelantado postergaron las cuotas de marzo a agosto hasta noviembre.
CREATE TEMPORARY TABLE demo_sm_plan ON COMMIT DROP AS
WITH cuota AS (
    SELECT o.id, o.contribuyente_codigo, o.tributo, o.anio, o.numero_cuota, o.fecha_vencimiento, o.monto_emitido,
           pf.perfil, pf.desde_anio,
           abs(hashtext(o.contribuyente_codigo || coalesce(o.codigo_predio, '') || o.tributo || o.anio || '-'
                        || o.numero_cuota)::BIGINT) AS h
    FROM samnet.obligacion o
    JOIN demo_sm_perfil pf ON pf.codigo = o.contribuyente_codigo AND o.anio BETWEEN pf.anio_desde AND pf.anio_hasta
),
fecha AS (
    SELECT c.*,
           CASE c.perfil
               WHEN 'ANUAL' THEN make_date(c.anio, 2, 20)
               WHEN 'PUNTUAL' THEN
                   CASE WHEN c.h % 100 < 3 THEN c.fecha_vencimiento + 30 + (c.h % 60)::INTEGER
                        ELSE c.fecha_vencimiento - (c.h % 6)::INTEGER END
               WHEN 'ATRASADO_LEVE' THEN c.fecha_vencimiento + (c.h % 19)::INTEGER - 3
               WHEN 'ATRASADO' THEN
                   CASE WHEN c.h % 100 < 12 THEN c.fecha_vencimiento + 150 + (c.h % 200)::INTEGER
                        ELSE c.fecha_vencimiento + 8 + (c.h % 40)::INTEGER END
               WHEN 'RECUPERA' THEN
                   CASE WHEN (c.tributo = 'IMPUESTO_PREDIAL' AND c.numero_cuota >= 3)
                             OR (c.tributo = 'ARBITRIOS' AND c.numero_cuota >= 8)
                        THEN make_date(c.anio + 1, 3, 10) + (c.h % 25)::INTEGER
                        ELSE c.fecha_vencimiento - (c.h % 4)::INTEGER END
               WHEN 'MOROSO' THEN
                   CASE WHEN c.anio < c.desde_anio AND c.h % 10 <> 0
                        THEN c.fecha_vencimiento + 5 + (c.h % 30)::INTEGER END
               WHEN 'PARCIAL' THEN
                   CASE WHEN c.tributo = 'IMPUESTO_PREDIAL' THEN c.fecha_vencimiento - 2
                        WHEN c.numero_cuota <= 6 THEN c.fecha_vencimiento + (c.h % 10)::INTEGER END
           END AS fecha_pago
    FROM cuota c
)
SELECT f.id, f.contribuyente_codigo, f.anio,
       CASE WHEN f.perfil NOT IN ('ANUAL', 'PUNTUAL') AND f.fecha_pago IS NOT NULL
                 AND f.fecha_vencimiento BETWEEN DATE '2020-03-15' AND DATE '2020-08-31'
            THEN greatest(f.fecha_pago, DATE '2020-11-16' + (f.h % 20)::INTEGER)
            ELSE f.fecha_pago END AS fecha_pago,
       f.monto_emitido AS monto
FROM fecha f;

-- Los datos llegan al 2026-10-19, como R__datos_prueba_03.
DELETE FROM demo_sm_plan WHERE fecha_pago IS NULL OR fecha_pago > DATE '2026-10-19';

UPDATE samnet.obligacion o SET monto_pagado = p.monto FROM demo_sm_plan p WHERE p.id = o.id;

INSERT INTO samnet.pago
    (id_pago, contribuyente_codigo, fecha_pago, canal, monto_pagado, monto_correspondiente, id_beneficio)
SELECT 'RH-' || p.contribuyente_codigo || '-' || to_char(p.fecha_pago, 'YYYYMMDD'), p.contribuyente_codigo,
       p.fecha_pago,
       CASE WHEN EXTRACT(YEAR FROM p.fecha_pago) < 2021
                 THEN CASE WHEN EXTRACT(MONTH FROM p.fecha_pago)::INTEGER % 4 = 0 THEN 'EN_LINEA' ELSE 'VENTANILLA' END
            WHEN EXTRACT(MONTH FROM p.fecha_pago)::INTEGER % 3 = 0 THEN 'VENTANILLA' ELSE 'EN_LINEA' END,
       sum(p.monto), sum(p.monto), NULL
FROM demo_sm_plan p
GROUP BY p.contribuyente_codigo, p.fecha_pago;

INSERT INTO samnet.pago_obligacion (id_pago, id_obligacion, monto_aplicado)
SELECT 'RH-' || p.contribuyente_codigo || '-' || to_char(p.fecha_pago, 'YYYYMMDD'), p.id, p.monto
FROM demo_sm_plan p;

-- Cobranza de las cuotas impagas de los nuevos: campaña a los 40 días, valor (OP para predial, RD para arbitrios)
-- a los 80, espera legal a los 94 y vía coactiva a los 125, hasta la fecha de los datos.
WITH impaga AS (
    SELECT o.id, o.tributo, o.fecha_vencimiento,
           row_number() OVER (ORDER BY o.fecha_vencimiento, o.id) AS correlativo
    FROM samnet.obligacion o
    JOIN demo_sm_nuevo n ON n.codigo = o.contribuyente_codigo
    WHERE o.monto_pagado < o.monto_emitido AND o.fecha_vencimiento + 40 <= DATE '2026-10-19'
),
etapa AS (
    SELECT i.*, e.etapa, i.fecha_vencimiento + e.dias AS fecha_inicio, e.observacion
    FROM impaga i
    CROSS JOIN (VALUES ('CAMPANA', 40, 'Carta inductiva de la campaña de cobranza'),
                       ('VALOR_EMITIDO', 80, NULL),
                       ('ESPERA_LEGAL', 94, 'Notificado en el domicilio fiscal'),
                       ('COACTIVA', 125, 'Venció la espera legal sin pago ni reclamación')) AS e (etapa, dias, observacion)
    WHERE i.fecha_vencimiento + e.dias <= DATE '2026-10-19'
)
INSERT INTO samnet.seguimiento_cobranza
    (obligacion_id, etapa, fecha_inicio, documento_referencia, observacion, fecha_registro)
SELECT e.id, e.etapa, e.fecha_inicio,
       CASE WHEN e.etapa = 'CAMPANA' THEN NULL
            ELSE CASE e.tributo WHEN 'IMPUESTO_PREDIAL' THEN 'OP-' ELSE 'RD-' END
                 || EXTRACT(YEAR FROM e.fecha_vencimiento + 80) || '-' || lpad((7000 + e.correlativo)::TEXT, 6, '0')
       END,
       e.observacion, e.fecha_inicio + TIME '09:00'
FROM etapa e
ORDER BY e.id, e.fecha_inicio;

-- 5. Las dimensiones, las entidades maestras y sus correspondencias las crea el ETL en cada carga (paso 6), con el
-- mismo seudónimo de antes. Aquí solo se corrigen las zonas de la muestra del dashboard (R__zz_demo_01), que usa otra
-- clave y no pasa por el ETL.
UPDATE analitica.dim_predio d SET zona = p.zona
FROM samnet.predio p
WHERE d.seudonimo_predio = encode(public.hmac('PREDIO:' || p.codigo_predio,
                                              'CLAVE_PUBLICA_SOLO_SEMILLA_FICTICIA_HU011', 'sha256'), 'hex')
  AND d.zona IS DISTINCT FROM p.zona;

-- 6. Cargas del ETL. Las de esta semilla se reconocen porque empezaron el día siguiente a su fecha de corte. Al volver a
-- ejecutarse se borran (con su bronce, su cuarentena y sus indicadores) y se cargan otra vez.
CREATE TEMPORARY TABLE demo_sm_lote_viejo ON COMMIT DROP AS
SELECT l.id_lote FROM analitica.lote_etl l
WHERE l.fuente = 'ps_etl_cobranza' AND l.tipo_ejecucion = 'PROGRAMADA'
  AND l.fecha_inicio::DATE = l.fecha_corte + 1;

DELETE FROM analitica.alerta_desvio a
USING analitica.valor_indicador v
WHERE a.id_valor = v.id_valor AND v.id_lote IN (SELECT id_lote FROM demo_sm_lote_viejo);
DELETE FROM analitica.valor_indicador WHERE id_lote IN (SELECT id_lote FROM demo_sm_lote_viejo);
DELETE FROM analitica.hecho_cobranza WHERE id_lote IN (SELECT id_lote FROM demo_sm_lote_viejo);
DELETE FROM analitica.hecho_pago WHERE id_lote IN (SELECT id_lote FROM demo_sm_lote_viejo);
DELETE FROM analitica.lote_etl l
WHERE l.id_lote IN (SELECT id_lote FROM demo_sm_lote_viejo)
  AND NOT EXISTS (SELECT 1 FROM operativo.aviso_correo c WHERE c.id_lote = l.id_lote)
  AND NOT EXISTS (SELECT 1 FROM analitica.caso_homologacion c WHERE c.id_lote = l.id_lote);

CREATE TEMPORARY TABLE demo_sm_corte ON COMMIT DROP AS
SELECT (date_trunc('month', m) + INTERVAL '1 month - 1 day')::DATE AS fecha_corte
FROM generate_series(DATE '2019-01-01', DATE '2026-10-01', INTERVAL '1 month') AS m
UNION ALL
SELECT DATE '2026-11-04';

-- Una ejecución del ETL por fecha de corte, en orden, como si hubiera corrido cada madrugada siguiente al cierre.
DO $etl$
DECLARE
    v_corte DATE;
BEGIN
    FOR v_corte IN SELECT fecha_corte FROM demo_sm_corte ORDER BY fecha_corte LOOP
        PERFORM etl.ejecutar_lote(v_corte, 'PROGRAMADA', 'samnet-demo-contribuyente', 'samnet-demo-predio',
                                  (v_corte + 1) + TIME '02:00');
    END LOOP;
END;
$etl$;

-- Estadísticas al día tras cargar casi medio millón de hechos: sin ellas, las semillas siguientes eligen planes lentos.
ANALYZE analitica.hecho_cobranza;
ANALYZE analitica.hecho_pago;
ANALYZE analitica.lote_etl;

-- 7. Segmentos de la muestra (R__zz_demo_01) para los contribuyentes de la carga del 2026-11-04, con la misma regla:
-- sin deuda, con pagos y deuda, o solo deuda. Así el dashboard mantiene la distribución por segmento.
INSERT INTO analitica.asignacion_segmento (id_segmento, seudonimo_contribuyente, fecha_asignacion)
SELECT s.id_segmento, t.seudonimo_contribuyente, TIMESTAMP '2026-11-05 08:00'
FROM (SELECT h.seudonimo_contribuyente, sum(h.monto_insoluto) FILTER (WHERE h.estado_pago = 'VENCIDA') AS deuda,
             sum(h.monto_pagado) FILTER (WHERE h.anio = 2026) AS pagado
      FROM analitica.hecho_cobranza h
      JOIN analitica.lote_etl l ON l.id_lote = h.id_lote
      WHERE l.fuente = 'ps_etl_cobranza' AND l.fecha_corte = DATE '2026-11-04'
        AND l.fecha_inicio::DATE = l.fecha_corte + 1
      GROUP BY h.seudonimo_contribuyente) t
JOIN analitica.modelo_ia m ON m.nombre = 'DEMO_HU011_NO_ES_MODELO_IA' AND m.version = 'demo-1'
JOIN analitica.segmento s ON s.id_modelo = m.id_modelo AND s.nombre =
     CASE WHEN coalesce(t.deuda, 0) = 0 THEN 'Demo: al dia' WHEN coalesce(t.pagado, 0) > 0 THEN 'Demo: pago parcial'
          ELSE 'Demo: deuda pendiente' END
ON CONFLICT (id_segmento, seudonimo_contribuyente) DO NOTHING;

UPDATE analitica.segmento s SET tamano = (
    SELECT count(DISTINCT a.seudonimo_contribuyente)
    FROM analitica.asignacion_segmento a
    JOIN analitica.hecho_cobranza h ON h.seudonimo_contribuyente = a.seudonimo_contribuyente
    JOIN analitica.lote_etl l ON l.id_lote = h.id_lote
    WHERE a.id_segmento = s.id_segmento AND l.fecha_corte = DATE '2026-11-04'
      AND l.fecha_inicio::DATE = l.fecha_corte + 1)
FROM analitica.modelo_ia m
WHERE s.id_modelo = m.id_modelo AND m.nombre = 'DEMO_HU011_NO_ES_MODELO_IA' AND m.version = 'demo-1';
