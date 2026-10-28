-- SAMNET sintético: tablas que emulan la información de Rentas (paquete Gestión tributaria).
-- Solo contiene datos ficticios. Las columnas son una propuesta y se ajustarán si la Municipalidad
-- entrega la lista de campos de SAMNET (RF-001, RF-002).
-- No se crean FK desde otros esquemas hacia samnet: para el sistema es un sistema externo.

-- Contribuyente: se agregan los atributos del modelo y el domicilio fiscal desglosado (value object).
ALTER TABLE samnet.contribuyente
    ADD COLUMN regimen          VARCHAR(40),
    ADD COLUMN condicion        VARCHAR(40),
    ADD COLUMN correo           VARCHAR(120),
    ADD COLUMN telefonos        VARCHAR(60),
    ADD COLUMN dom_departamento VARCHAR(40),
    ADD COLUMN dom_provincia    VARCHAR(40),
    ADD COLUMN dom_distrito     VARCHAR(40),
    ADD COLUMN dom_via          VARCHAR(150),
    ADD COLUMN dom_numero       VARCHAR(10),
    ADD COLUMN dom_interior     VARCHAR(10),
    ADD COLUMN dom_manzana      VARCHAR(10),
    ADD COLUMN dom_lote         VARCHAR(10);

CREATE TABLE samnet.predio (
    codigo_predio     VARCHAR(12)    PRIMARY KEY,
    numero_catastral  VARCHAR(30),
    ubicacion         VARCHAR(250)   NOT NULL,
    uso_predio        VARCHAR(60),
    tipo_predio       VARCHAR(10)    NOT NULL CHECK (tipo_predio IN ('URBANO', 'RUSTICO', 'OTRO')),
    area_terreno      NUMERIC(10, 2) CHECK (area_terreno >= 0),
    base_imponible    NUMERIC(14, 2) CHECK (base_imponible >= 0),
    fecha_adquisicion DATE,
    fecha_baja        DATE,
    zona              VARCHAR(20)    NOT NULL,
    CONSTRAINT ck_predio_fecha_baja CHECK (fecha_baja IS NULL OR fecha_baja > fecha_adquisicion)
);

-- Contribuyente 1..* titular de * Predio, con la condición y el porcentaje de cada titular.
CREATE TABLE samnet.predio_titular (
    codigo_predio        VARCHAR(12)   NOT NULL REFERENCES samnet.predio (codigo_predio),
    contribuyente_codigo VARCHAR(10)   NOT NULL REFERENCES samnet.contribuyente (codigo),
    condicion_propiedad  VARCHAR(30)   NOT NULL,
    porcentaje_propiedad NUMERIC(5, 2) NOT NULL CHECK (porcentaje_propiedad BETWEEN 0 AND 100),
    PRIMARY KEY (codigo_predio, contribuyente_codigo)
);

CREATE INDEX ix_predio_titular_contribuyente ON samnet.predio_titular (contribuyente_codigo);

CREATE TABLE samnet.declaracion_jurada (
    id_declaracion  VARCHAR(20)    PRIMARY KEY,
    codigo_predio   VARCHAR(12)    NOT NULL REFERENCES samnet.predio (codigo_predio),
    anio            INTEGER        NOT NULL,
    autoavaluo      NUMERIC(14, 2) NOT NULL CHECK (autoavaluo >= 0),
    motivo          VARCHAR(60),
    submotivo       VARCHAR(60),
    tipo_declarante VARCHAR(40),
    estado          VARCHAR(20)    NOT NULL,
    es_vigente      BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uq_declaracion_vigente_por_predio
    ON samnet.declaracion_jurada (codigo_predio) WHERE es_vigente;

-- Obligación: los arbitrios se emiten por mes (1 a 12) y pueden tener descuento por beneficio.
-- codigo_predio no tiene FK a propósito: los datos sintéticos incluyen inconsistencias que el ETL debe detectar.
ALTER TABLE samnet.obligacion DROP CONSTRAINT obligacion_numero_cuota_check;
ALTER TABLE samnet.obligacion
    ADD CONSTRAINT ck_obligacion_numero_cuota CHECK (numero_cuota BETWEEN 1 AND 12),
    ADD COLUMN porcentaje_descuento NUMERIC(5, 2) NOT NULL DEFAULT 0
        CHECK (porcentaje_descuento BETWEEN 0 AND 100);

CREATE TABLE samnet.beneficio_tributario (
    id_beneficio         VARCHAR(20)   PRIMARY KEY,
    contribuyente_codigo VARCHAR(10)   NOT NULL REFERENCES samnet.contribuyente (codigo),
    tipo                 VARCHAR(10)   NOT NULL CHECK (tipo IN ('AMNISTIA', 'PARTICULAR')),
    porcentaje_descuento NUMERIC(5, 2) NOT NULL CHECK (porcentaje_descuento BETWEEN 0 AND 100),
    vigencia_desde       DATE          NOT NULL,
    vigencia_hasta       DATE          NOT NULL,
    CONSTRAINT ck_beneficio_vigencia CHECK (vigencia_hasta >= vigencia_desde)
);

CREATE TABLE samnet.pago (
    id_pago               VARCHAR(20)    PRIMARY KEY,
    contribuyente_codigo  VARCHAR(10)    NOT NULL REFERENCES samnet.contribuyente (codigo),
    fecha_pago            DATE           NOT NULL,
    canal                 VARCHAR(10)    NOT NULL CHECK (canal IN ('VENTANILLA', 'EN_LINEA')),
    monto_pagado          NUMERIC(14, 2) NOT NULL CHECK (monto_pagado > 0),
    monto_correspondiente NUMERIC(14, 2) NOT NULL CHECK (monto_correspondiente >= 0),
    id_beneficio          VARCHAR(20)    REFERENCES samnet.beneficio_tributario (id_beneficio)
);

CREATE INDEX ix_pago_contribuyente ON samnet.pago (contribuyente_codigo, fecha_pago);

-- Pago * cancela 1..* Obligacion
CREATE TABLE samnet.pago_obligacion (
    id_pago        VARCHAR(20)    NOT NULL REFERENCES samnet.pago (id_pago),
    id_obligacion  BIGINT         NOT NULL REFERENCES samnet.obligacion (id),
    monto_aplicado NUMERIC(14, 2) NOT NULL CHECK (monto_aplicado > 0),
    PRIMARY KEY (id_pago, id_obligacion)
);

-- Credenciales del servicio de autenticación municipal (ps_login). Solo se guarda el hash bcrypt.
CREATE TABLE samnet.credencial_web (
    contribuyente_codigo VARCHAR(10) PRIMARY KEY REFERENCES samnet.contribuyente (codigo),
    clave_hash           VARCHAR(60) NOT NULL CHECK (clave_hash LIKE '$2_$%'),
    habilitada           BOOLEAN     NOT NULL DEFAULT TRUE
);
