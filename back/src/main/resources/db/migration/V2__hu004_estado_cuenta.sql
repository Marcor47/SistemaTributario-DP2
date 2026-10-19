-- ====================================================================
-- HU004 (RF-004): Consultar estado de cuenta de un contribuyente
-- Esquema y tablas para gestión tributaria municipal (SAMNET)
-- ====================================================================

-- 1. Tabla de Contribuyentes
CREATE TABLE IF NOT EXISTS contribuyente (
    id BIGSERIAL PRIMARY KEY,
    codigo_contribuyente VARCHAR(20) NOT NULL UNIQUE,
    tipo_documento VARCHAR(10) NOT NULL, -- DNI, RUC, CE
    numero_documento VARCHAR(20) NOT NULL UNIQUE,
    nombres_razon_social VARCHAR(200) NOT NULL,
    direccion_fiscal VARCHAR(255) NOT NULL,
    telefono VARCHAR(20),
    email VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_contribuyente_doc ON contribuyente (numero_documento);
CREATE INDEX IF NOT EXISTS idx_contribuyente_cod ON contribuyente (codigo_contribuyente);

-- 2. Tabla de Catálogo de Tributos
CREATE TABLE IF NOT EXISTS tributo (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    categoria VARCHAR(50) NOT NULL, -- IMPUESTO, ARBITRIO
    color_hex VARCHAR(10)
);

-- Inserción inicial de catálogo oficial de tributos
INSERT INTO tributo (codigo, nombre, categoria, color_hex) VALUES
('PREDIAL', 'Impuesto Predial', 'IMPUESTO', '#2A78D6'),
('LIMPIEZA', 'Limpieza Pública', 'ARBITRIO', '#EB6834'),
('PARQUES', 'Parques y Jardines', 'ARBITRIO', '#1BAF7A'),
('SERENAZGO', 'Seguridad Ciudadana (Serenazgo)', 'ARBITRIO', '#EDA100')
ON CONFLICT (codigo) DO UPDATE 
SET nombre = EXCLUDED.nombre,
    categoria = EXCLUDED.categoria,
    color_hex = EXCLUDED.color_hex;

-- 3. Tabla de Obligaciones / Cuenta Corriente Tributaria
CREATE TABLE IF NOT EXISTS obligacion (
    id BIGSERIAL PRIMARY KEY,
    contribuyente_id BIGINT NOT NULL REFERENCES contribuyente(id) ON DELETE CASCADE,
    tributo_id BIGINT NOT NULL REFERENCES tributo(id),
    periodo INTEGER NOT NULL,
    cuota INTEGER NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    monto_emision NUMERIC(12, 2) NOT NULL,
    monto_pagado NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PAGADO', 'VENCIDO', 'POR_VENCER', 'PENDIENTE')),
    fecha_pago DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_contrib_trib_periodo_cuota UNIQUE (contribuyente_id, tributo_id, periodo, cuota)
);

CREATE INDEX IF NOT EXISTS idx_obligacion_contrib ON obligacion (contribuyente_id);
CREATE INDEX IF NOT EXISTS idx_obligacion_estado ON obligacion (estado);
CREATE INDEX IF NOT EXISTS idx_obligacion_periodo ON obligacion (periodo);

-- 4. Vista analítica para Estado de Cuenta (simulación SAMNET)
CREATE OR REPLACE VIEW analitica.vw_estado_cuenta_contribuyente AS
SELECT 
    c.id AS contribuyente_id,
    c.codigo_contribuyente,
    c.tipo_documento,
    c.numero_documento,
    c.nombres_razon_social,
    c.direccion_fiscal,
    o.id AS obligacion_id,
    t.id AS tributo_id,
    t.codigo AS tributo_codigo,
    t.nombre AS tributo_nombre,
    t.categoria AS tributo_categoria,
    t.color_hex AS tributo_color,
    o.periodo,
    o.cuota,
    o.fecha_vencimiento,
    o.monto_emision,
    o.monto_pagado,
    ROUND((o.monto_emision - o.monto_pagado)::numeric, 2) AS saldo_pendiente,
    CASE 
        WHEN o.estado = 'VENCIDO' THEN ROUND((o.monto_emision - o.monto_pagado)::numeric, 2)
        ELSE 0.00 
    END AS deuda_vencida,
    o.estado,
    o.fecha_pago
FROM contribuyente c
JOIN obligacion o ON c.id = o.contribuyente_id
JOIN tributo t ON o.tributo_id = t.id;

-- 5. Función / Stored Procedure en PostgreSQL para consulta de Estado de Cuenta
CREATE OR REPLACE FUNCTION analitica.sp_consultar_estado_cuenta(p_identificador VARCHAR)
RETURNS TABLE (
    contribuyente_id BIGINT,
    codigo_contribuyente VARCHAR,
    tipo_documento VARCHAR,
    numero_documento VARCHAR,
    nombres_razon_social VARCHAR,
    direccion_fiscal VARCHAR,
    obligacion_id BIGINT,
    tributo_id BIGINT,
    tributo_codigo VARCHAR,
    tributo_nombre VARCHAR,
    tributo_categoria VARCHAR,
    tributo_color VARCHAR,
    periodo INTEGER,
    cuota INTEGER,
    fecha_vencimiento DATE,
    monto_emision NUMERIC(12,2),
    monto_pagado NUMERIC(12,2),
    saldo_pendiente NUMERIC(12,2),
    deuda_vencida NUMERIC(12,2),
    estado VARCHAR,
    fecha_pago DATE
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        v.contribuyente_id,
        v.codigo_contribuyente,
        v.tipo_documento,
        v.numero_documento,
        v.nombres_razon_social,
        v.direccion_fiscal,
        v.obligacion_id,
        v.tributo_id,
        v.tributo_codigo,
        v.tributo_nombre,
        v.tributo_categoria,
        v.tributo_color,
        v.periodo,
        v.cuota,
        v.fecha_vencimiento,
        v.monto_emision,
        v.monto_pagado,
        v.saldo_pendiente,
        v.deuda_vencida,
        v.estado,
        v.fecha_pago
    FROM analitica.vw_estado_cuenta_contribuyente v
    WHERE UPPER(TRIM(v.codigo_contribuyente)) = UPPER(TRIM(p_identificador))
       OR UPPER(TRIM(v.numero_documento)) = UPPER(TRIM(p_identificador))
    ORDER BY v.periodo DESC, v.cuota ASC, v.tributo_id ASC;
END;
$$ LANGUAGE plpgsql;

-- 6. Datos de prueba: Contribuyentes y Obligaciones
-- Contribuyente 1: Persona Natural con deudas vencidas y saldo pendiente
INSERT INTO contribuyente (codigo_contribuyente, tipo_documento, numero_documento, nombres_razon_social, direccion_fiscal, telefono, email)
VALUES 
('C-100234', 'DNI', '10458923', 'Juan Carlos Mendoza Paredes', 'Av. Los Álamos 452, Urb. Las Flores', '987654321', 'jmendoza@gmail.com'),
('C-200891', 'RUC', '20556677881', 'Inversiones San Martín S.A.C.', 'Calle Los Pinos 180, Oficina 402, Centro Financiero', '014238990', 'tributos@inversiones-sm.pe'),
('C-300456', 'DNI', '45892014', 'María Elena Ríos Valdivia', 'Jr. Huallaga 310, Int. 5B', '976543210', 'm.rios@outlook.com')
ON CONFLICT (numero_documento) DO NOTHING;

-- Obtener IDs e insertar obligaciones para Contribuyente 1 (Juan Mendoza - Con Deuda Vencida)
DO $$
DECLARE
    v_contrib_id BIGINT;
    v_predial_id BIGINT;
    v_limpieza_id BIGINT;
    v_parques_id BIGINT;
    v_serenazgo_id BIGINT;
BEGIN
    SELECT id INTO v_contrib_id FROM contribuyente WHERE codigo_contribuyente = 'C-100234';
    SELECT id INTO v_predial_id FROM tributo WHERE codigo = 'PREDIAL';
    SELECT id INTO v_limpieza_id FROM tributo WHERE codigo = 'LIMPIEZA';
    SELECT id INTO v_parques_id FROM tributo WHERE codigo = 'PARQUES';
    SELECT id INTO v_serenazgo_id FROM tributo WHERE codigo = 'SERENAZGO';

    IF v_contrib_id IS NOT NULL THEN
        -- 2024: Cuotas 1 y 2 Pagadas, Cuotas 3 y 4 Vencidas
        INSERT INTO obligacion (contribuyente_id, tributo_id, periodo, cuota, fecha_vencimiento, monto_emision, monto_pagado, estado, fecha_pago)
        VALUES
        -- 2024 Cuota 1 (PAGADO)
        (v_contrib_id, v_predial_id, 2024, 1, '2024-02-28', 350.00, 350.00, 'PAGADO', '2024-02-25'),
        (v_contrib_id, v_limpieza_id, 2024, 1, '2024-02-28', 45.00, 45.00, 'PAGADO', '2024-02-25'),
        (v_contrib_id, v_parques_id, 2024, 1, '2024-02-28', 32.50, 32.50, 'PAGADO', '2024-02-25'),
        (v_contrib_id, v_serenazgo_id, 2024, 1, '2024-02-28', 55.00, 55.00, 'PAGADO', '2024-02-25'),
        -- 2024 Cuota 2 (PAGADO)
        (v_contrib_id, v_predial_id, 2024, 2, '2024-05-31', 350.00, 350.00, 'PAGADO', '2024-05-28'),
        (v_contrib_id, v_limpieza_id, 2024, 2, '2024-05-31', 45.00, 45.00, 'PAGADO', '2024-05-28'),
        (v_contrib_id, v_parques_id, 2024, 2, '2024-05-31', 32.50, 32.50, 'PAGADO', '2024-05-28'),
        (v_contrib_id, v_serenazgo_id, 2024, 2, '2024-05-31', 55.00, 55.00, 'PAGADO', '2024-05-28'),
        -- 2024 Cuota 3 (VENCIDO)
        (v_contrib_id, v_predial_id, 2024, 3, '2024-08-31', 350.00, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_limpieza_id, 2024, 3, '2024-08-31', 45.00, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_parques_id, 2024, 3, '2024-08-31', 32.50, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_serenazgo_id, 2024, 3, '2024-08-31', 55.00, 0.00, 'VENCIDO', NULL),
        -- 2024 Cuota 4 (VENCIDO)
        (v_contrib_id, v_predial_id, 2024, 4, '2024-11-30', 350.00, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_limpieza_id, 2024, 4, '2024-11-30', 45.00, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_parques_id, 2024, 4, '2024-11-30', 32.50, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_serenazgo_id, 2024, 4, '2024-11-30', 55.00, 0.00, 'VENCIDO', NULL),

        -- 2025: Cuota 1 Vencida, Cuota 2 Por vencer, Cuota 3 y 4 Pendientes
        (v_contrib_id, v_predial_id, 2025, 1, '2025-02-28', 380.00, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_limpieza_id, 2025, 1, '2025-02-28', 48.00, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_parques_id, 2025, 1, '2025-02-28', 35.00, 0.00, 'VENCIDO', NULL),
        (v_contrib_id, v_serenazgo_id, 2025, 1, '2025-02-28', 58.00, 0.00, 'VENCIDO', NULL),

        (v_contrib_id, v_predial_id, 2025, 2, '2025-05-31', 380.00, 0.00, 'POR_VENCER', NULL),
        (v_contrib_id, v_limpieza_id, 2025, 2, '2025-05-31', 48.00, 0.00, 'POR_VENCER', NULL),
        (v_contrib_id, v_parques_id, 2025, 2, '2025-05-31', 35.00, 0.00, 'POR_VENCER', NULL),
        (v_contrib_id, v_serenazgo_id, 2025, 2, '2025-05-31', 58.00, 0.00, 'POR_VENCER', NULL),

        (v_contrib_id, v_predial_id, 2025, 3, '2025-08-31', 380.00, 0.00, 'PENDIENTE', NULL),
        (v_contrib_id, v_predial_id, 2025, 4, '2025-11-30', 380.00, 0.00, 'PENDIENTE', NULL)
        ON CONFLICT (contribuyente_id, tributo_id, periodo, cuota) DO NOTHING;
    END IF;
END $$;

-- Obligaciones para Contribuyente 2 (Inversiones San Martín - Totalmente al día)
DO $$
DECLARE
    v_contrib_id BIGINT;
    v_predial_id BIGINT;
    v_limpieza_id BIGINT;
    v_parques_id BIGINT;
    v_serenazgo_id BIGINT;
BEGIN
    SELECT id INTO v_contrib_id FROM contribuyente WHERE codigo_contribuyente = 'C-200891';
    SELECT id INTO v_predial_id FROM tributo WHERE codigo = 'PREDIAL';
    SELECT id INTO v_limpieza_id FROM tributo WHERE codigo = 'LIMPIEZA';
    SELECT id INTO v_parques_id FROM tributo WHERE codigo = 'PARQUES';
    SELECT id INTO v_serenazgo_id FROM tributo WHERE codigo = 'SERENAZGO';

    IF v_contrib_id IS NOT NULL THEN
        INSERT INTO obligacion (contribuyente_id, tributo_id, periodo, cuota, fecha_vencimiento, monto_emision, monto_pagado, estado, fecha_pago)
        VALUES
        -- 2024 Todas pagadas
        (v_contrib_id, v_predial_id, 2024, 1, '2024-02-28', 1250.00, 1250.00, 'PAGADO', '2024-02-20'),
        (v_contrib_id, v_limpieza_id, 2024, 1, '2024-02-28', 180.00, 180.00, 'PAGADO', '2024-02-20'),
        (v_contrib_id, v_parques_id, 2024, 1, '2024-02-28', 120.00, 120.00, 'PAGADO', '2024-02-20'),
        (v_contrib_id, v_serenazgo_id, 2024, 1, '2024-02-28', 210.00, 210.00, 'PAGADO', '2024-02-20'),

        (v_contrib_id, v_predial_id, 2024, 2, '2024-05-31', 1250.00, 1250.00, 'PAGADO', '2024-05-15'),
        (v_contrib_id, v_limpieza_id, 2024, 2, '2024-05-31', 180.00, 180.00, 'PAGADO', '2024-05-15'),
        (v_contrib_id, v_parques_id, 2024, 2, '2024-05-31', 120.00, 120.00, 'PAGADO', '2024-05-15'),
        (v_contrib_id, v_serenazgo_id, 2024, 2, '2024-05-31', 210.00, 210.00, 'PAGADO', '2024-05-15'),

        (v_contrib_id, v_predial_id, 2024, 3, '2024-08-31', 1250.00, 1250.00, 'PAGADO', '2024-08-10'),
        (v_contrib_id, v_limpieza_id, 2024, 3, '2024-08-31', 180.00, 180.00, 'PAGADO', '2024-08-10'),
        (v_contrib_id, v_parques_id, 2024, 3, '2024-08-31', 120.00, 120.00, 'PAGADO', '2024-08-10'),
        (v_contrib_id, v_serenazgo_id, 2024, 3, '2024-08-31', 210.00, 210.00, 'PAGADO', '2024-08-10'),

        (v_contrib_id, v_predial_id, 2024, 4, '2024-11-30', 1250.00, 1250.00, 'PAGADO', '2024-11-12'),
        (v_contrib_id, v_limpieza_id, 2024, 4, '2024-11-30', 180.00, 180.00, 'PAGADO', '2024-11-12'),
        (v_contrib_id, v_parques_id, 2024, 4, '2024-11-30', 120.00, 120.00, 'PAGADO', '2024-11-12'),
        (v_contrib_id, v_serenazgo_id, 2024, 4, '2024-11-30', 210.00, 210.00, 'PAGADO', '2024-11-12'),

        -- 2025 Adelantado / al día
        (v_contrib_id, v_predial_id, 2025, 1, '2025-02-28', 1300.00, 1300.00, 'PAGADO', '2025-01-20'),
        (v_contrib_id, v_predial_id, 2025, 2, '2025-05-31', 1300.00, 1300.00, 'PAGADO', '2025-01-20'),
        (v_contrib_id, v_predial_id, 2025, 3, '2025-08-31', 1300.00, 0.00, 'POR_VENCER', NULL),
        (v_contrib_id, v_predial_id, 2025, 4, '2025-11-30', 1300.00, 0.00, 'PENDIENTE', NULL)
        ON CONFLICT (contribuyente_id, tributo_id, periodo, cuota) DO NOTHING;
    END IF;
END $$;
