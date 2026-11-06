-- Auditoría de tributos (2026-11): cómo cobra la Municipalidad de San Miguel.
--   * Impuesto Predial (TUO de la Ley de Tributación Municipal, D. S. 156-2004-EF, arts. 8 a 15): se determina por
--     contribuyente sobre el valor total de sus predios en el distrito al 1 de enero, con escala progresiva
--     acumulativa (0,2 % hasta 15 UIT, 0,6 % de 15 a 60 UIT, 1 % más de 60 UIT) y un mínimo del 0,6 % de la UIT. Se
--     paga al contado o en cuatro cuotas trimestrales que vencen el último día hábil de febrero, mayo, agosto y
--     noviembre (calendario tributario de San Miguel, p. ej. Ordenanza 509/MDSM para 2025).
--   * Arbitrios: se determinan por predio y por mes (12 periodos; enero y febrero vencen a fin de febrero) y suman
--     cuatro servicios: barrido de calles, recolección de residuos sólidos, parques y jardines, y serenazgo.
-- Así, cada cuota sabe exactamente cuánto es Impuesto Predial y cuánto arbitrios, y de estos cuánto es cada servicio.

-- UIT de cada ejercicio (D. S. 260-2024-EF para 2025; D. S. 301-2025-EF para 2026).
CREATE TABLE samnet.uit (
    anio  INTEGER        PRIMARY KEY CHECK (anio BETWEEN 2000 AND 2100),
    valor NUMERIC(10, 2) NOT NULL CHECK (valor > 0)
);

INSERT INTO samnet.uit (anio, valor) VALUES (2025, 5350.00), (2026, 5500.00);

-- Tasa mensual de cada servicio por categoría de uso y zona, como la fija la ordenanza de arbitrios del año.
CREATE TABLE samnet.tasa_arbitrio (
    anio          INTEGER        NOT NULL,
    servicio      VARCHAR(25)    NOT NULL CHECK (servicio IN
                      ('BARRIDO_CALLES', 'RECOLECCION_RESIDUOS', 'PARQUES_JARDINES', 'SERENAZGO')),
    categoria_uso VARCHAR(25)    NOT NULL CHECK (categoria_uso IN
                      ('CASA_HABITACION', 'COMERCIO', 'SERVICIOS', 'INDUSTRIA', 'TERRENO_SIN_CONSTRUIR')),
    zona          VARCHAR(20)    NOT NULL,
    monto_mensual NUMERIC(10, 2) NOT NULL CHECK (monto_mensual >= 0),
    PRIMARY KEY (anio, servicio, categoria_uso, zona)
);

-- Obligacion 1 ◆ 0..4 ObligacionServicio: el detalle de una cuota de arbitrios por servicio. Suma el monto emitido.
CREATE TABLE samnet.obligacion_servicio (
    id_obligacion BIGINT         NOT NULL REFERENCES samnet.obligacion (id) ON DELETE CASCADE,
    servicio      VARCHAR(25)    NOT NULL CHECK (servicio IN
                      ('BARRIDO_CALLES', 'RECOLECCION_RESIDUOS', 'PARQUES_JARDINES', 'SERENAZGO')),
    monto         NUMERIC(14, 2) NOT NULL CHECK (monto >= 0),
    PRIMARY KEY (id_obligacion, servicio)
);

-- El Impuesto Predial tiene a lo más cuatro cuotas; los arbitrios, una por mes.
ALTER TABLE samnet.obligacion
    ADD CONSTRAINT ck_obligacion_cuota_por_tributo
        CHECK (tributo <> 'IMPUESTO_PREDIAL' OR numero_cuota BETWEEN 1 AND 4);

-- Último día hábil (lunes a viernes) del mes. No descuenta feriados: ninguno cae en los cierres de mes que se usan.
CREATE FUNCTION samnet.fn_ultimo_dia_habil(p_anio INTEGER, p_mes INTEGER) RETURNS DATE
LANGUAGE sql IMMUTABLE AS $$
    SELECT d - CASE EXTRACT(ISODOW FROM d) WHEN 6 THEN 1 WHEN 7 THEN 2 ELSE 0 END
    FROM (SELECT (make_date(p_anio, p_mes, 1) + INTERVAL '1 month' - INTERVAL '1 day')::DATE AS d) f;
$$;
