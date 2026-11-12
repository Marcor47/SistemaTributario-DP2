-- ETL por capas en la misma base (RF-038 a RF-041, RNF-018). No es un lakehouse: para el piloto (unos 10 usuarios
-- internos) PostgreSQL alcanza; el diseño por capas permite mover bronce y plata a un lakehouse si el volumen crece.
--
--   SAMNET (samnet.ps_etl_*) ──► bronce  copia cruda de cada extracción, por lote, solo se agrega
--                              ──► plata   registros validados (R1 a R5), normalizados y homologados; cuarentena
--                              ──► oro     el modelo estrella de siempre (analitica.dim_*, hecho_*) y maestros.*
--
-- El oro no cambia: las pantallas siguen leyendo analitica y maestros igual que antes. La lógica está en
-- R__etl_pipeline.sql (función etl.ejecutar_lote) y la orquesta EtlService.
-- bronce y plata guardan identificadores en claro (DNI, RUC, nombres): igual que maestros, acceso restringido.

-- Similitud de textos para la homologación (N2). Ambas son extensiones de confianza: las crea el dueño de la base.
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch WITH SCHEMA public;

CREATE SCHEMA IF NOT EXISTS bronce;
CREATE SCHEMA IF NOT EXISTS plata;
CREATE SCHEMA IF NOT EXISTS etl;

COMMENT ON SCHEMA bronce IS 'ETL, capa bronce: extracciones crudas de SAMNET por lote (solo se agregan). Datos en claro.';
COMMENT ON SCHEMA plata IS 'ETL, capa plata: registros validados, normalizados y homologados del último lote publicado, y cuarentena.';
COMMENT ON SCHEMA etl IS 'Funciones del ETL (bronce, plata, homologación y paso al modelo estrella).';

REVOKE ALL ON SCHEMA bronce FROM PUBLIC;
REVOKE ALL ON SCHEMA plata FROM PUBLIC;
REVOKE ALL ON SCHEMA etl FROM PUBLIC;

-- ===== Bronce: una tabla por procedimiento de extracción, con las columnas que devuelve =====
-- Sin longitudes ni CHECK: la copia cruda no debe fallar por un dato fuera de formato; eso lo decide la plata.

-- Manifiesto: qué se extrajo en cada lote, con qué parámetro y cuántas filas.
CREATE TABLE bronce.extraccion (
    id_lote          BIGINT      NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    procedimiento    TEXT NOT NULL,
    fecha_parametro  DATE,
    filas            INTEGER     NOT NULL CHECK (filas >= 0),
    fecha_extraccion TIMESTAMP   NOT NULL,
    PRIMARY KEY (id_lote, procedimiento)
);

-- samnet.ps_etl_contribuyentes()
CREATE TABLE bronce.contribuyente (
    id_lote          BIGINT  NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    fila             INTEGER NOT NULL,
    codigo           TEXT,
    nombre           TEXT,
    tipo_persona     TEXT,
    tipo_documento   TEXT,
    numero_documento TEXT,
    domicilio_fiscal TEXT,
    estado           TEXT,
    regimen          TEXT,
    condicion        TEXT,
    correo           TEXT,
    telefonos        TEXT,
    dom_departamento TEXT,
    dom_provincia    TEXT,
    dom_distrito     TEXT,
    dom_via          TEXT,
    dom_numero       TEXT,
    dom_interior     TEXT,
    dom_manzana      TEXT,
    dom_lote         TEXT,
    PRIMARY KEY (id_lote, fila)
);

-- samnet.ps_etl_predios(): una fila por titular.
CREATE TABLE bronce.predio (
    id_lote              BIGINT  NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    fila                 INTEGER NOT NULL,
    codigo_predio        TEXT,
    numero_catastral     TEXT,
    ubicacion            TEXT,
    uso_predio           TEXT,
    tipo_predio          TEXT,
    area_terreno         NUMERIC,
    base_imponible       NUMERIC,
    fecha_adquisicion    DATE,
    fecha_baja           DATE,
    zona                 TEXT,
    contribuyente_codigo TEXT,
    condicion_propiedad  TEXT,
    porcentaje_propiedad NUMERIC,
    PRIMARY KEY (id_lote, fila)
);

-- samnet.ps_etl_cobranza(fecha_corte): una fila por cuota.
CREATE TABLE bronce.cobranza (
    id_lote              BIGINT  NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    fila                 INTEGER NOT NULL,
    id_obligacion        BIGINT,
    contribuyente_codigo TEXT,
    codigo_predio        TEXT,
    zona                 TEXT,
    tributo              TEXT,
    anio                 INTEGER,
    cuota                INTEGER,
    fecha_vencimiento    DATE,
    monto_emitido        NUMERIC,
    monto_pagado         NUMERIC,
    insoluto             NUMERIC,
    porcentaje_descuento NUMERIC,
    etapa_cobranza       TEXT,
    fecha_etapa          DATE,
    fecha_pago           DATE,
    canal_pago           TEXT,
    PRIMARY KEY (id_lote, fila)
);

-- samnet.ps_etl_pagos(fecha_corte): una fila por pago.
CREATE TABLE bronce.pago (
    id_lote               BIGINT  NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    fila                  INTEGER NOT NULL,
    id_pago               TEXT,
    contribuyente_codigo  TEXT,
    fecha_pago            DATE,
    canal                 TEXT,
    monto_pagado          NUMERIC,
    monto_correspondiente NUMERIC,
    beneficio_aplicado    TEXT,
    PRIMARY KEY (id_lote, fila)
);

-- ===== Plata: estado vigente (último lote publicado) =====

CREATE TABLE plata.contribuyente (
    codigo            VARCHAR(10)  PRIMARY KEY,
    id_lote           BIGINT       NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    tipo_persona      VARCHAR(10)  NOT NULL,
    tipo_documento    VARCHAR(5)   NOT NULL,
    -- Documento sin espacios, puntos ni guiones.
    numero_documento  VARCHAR(12)  NOT NULL,
    -- Clave de identidad para la homologación exacta (N1): el RUC 10 de una persona natural lleva su DNI.
    clave_identidad   VARCHAR(20)  NOT NULL,
    nombre            VARCHAR(200) NOT NULL,
    nombre_comparable VARCHAR(200) NOT NULL,
    domicilio_fiscal  VARCHAR(250),
    domicilio_comparable VARCHAR(250),
    estado            VARCHAR(10),
    regimen           VARCHAR(40),
    condicion         VARCHAR(40),
    id_maestro        BIGINT       NOT NULL REFERENCES maestros.entidad_maestra (id_maestro),
    nivel_homologacion VARCHAR(30) NOT NULL
);

CREATE INDEX ix_plata_contribuyente_maestro ON plata.contribuyente (id_maestro);

CREATE TABLE plata.predio (
    codigo_predio     VARCHAR(12)    PRIMARY KEY,
    id_lote           BIGINT         NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    numero_catastral  VARCHAR(30),
    ubicacion         VARCHAR(250),
    uso_predio        VARCHAR(60),
    tipo_predio       VARCHAR(10)    NOT NULL,
    area_terreno      NUMERIC(10, 2),
    base_imponible    NUMERIC(14, 2),
    zona              VARCHAR(20)    NOT NULL,
    -- R3: predio sin titular en SAMNET; se publica marcado para revisión.
    marcado_revision  BOOLEAN        NOT NULL DEFAULT FALSE,
    id_maestro        BIGINT         NOT NULL REFERENCES maestros.entidad_maestra (id_maestro),
    nivel_homologacion VARCHAR(30)   NOT NULL
);

CREATE INDEX ix_plata_predio_maestro ON plata.predio (id_maestro);

CREATE TABLE plata.predio_titular (
    codigo_predio        VARCHAR(12)   NOT NULL REFERENCES plata.predio (codigo_predio) ON DELETE CASCADE,
    contribuyente_codigo VARCHAR(10)   NOT NULL REFERENCES plata.contribuyente (codigo) ON DELETE CASCADE,
    condicion_propiedad  VARCHAR(30),
    porcentaje_propiedad NUMERIC(5, 2),
    PRIMARY KEY (codigo_predio, contribuyente_codigo)
);

CREATE TABLE plata.cuota (
    id_obligacion        BIGINT         PRIMARY KEY,
    id_lote              BIGINT         NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    contribuyente_codigo VARCHAR(10)    NOT NULL REFERENCES plata.contribuyente (codigo) ON DELETE CASCADE,
    codigo_predio        VARCHAR(12),
    -- Predio con que se publica el hecho. El Impuesto Predial no tiene predio en SAMNET: se usa el primer predio
    -- del contribuyente, como hacía la carga anterior (la zona del predial no se usa en los reportes).
    codigo_predio_hecho  VARCHAR(12)    NOT NULL REFERENCES plata.predio (codigo_predio) ON DELETE CASCADE,
    tributo              VARCHAR(25)    NOT NULL,
    anio                 INTEGER        NOT NULL,
    cuota                INTEGER        NOT NULL,
    fecha_vencimiento    DATE           NOT NULL,
    monto_emitido        NUMERIC(14, 2) NOT NULL,
    monto_pagado         NUMERIC(14, 2) NOT NULL,
    monto_insoluto       NUMERIC(14, 2) NOT NULL,
    monto_descuento      NUMERIC(14, 2) NOT NULL,
    estado_pago          VARCHAR(10)    NOT NULL,
    fecha_pago           DATE,
    canal_pago           VARCHAR(10)
);

CREATE INDEX ix_plata_cuota_contribuyente ON plata.cuota (contribuyente_codigo);

CREATE TABLE plata.pago (
    id_pago               VARCHAR(20)    PRIMARY KEY,
    id_lote               BIGINT         NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    contribuyente_codigo  VARCHAR(10)    NOT NULL REFERENCES plata.contribuyente (codigo) ON DELETE CASCADE,
    fecha_pago            DATE           NOT NULL,
    canal                 VARCHAR(10)    NOT NULL,
    monto_pagado          NUMERIC(14, 2) NOT NULL,
    monto_correspondiente NUMERIC(14, 2) NOT NULL,
    beneficio_aplicado    VARCHAR(20)
);

-- Registros que no pasaron una regla, con el registro original. Se conserva la de todos los lotes.
CREATE TABLE plata.cuarentena (
    id_cuarentena BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_lote       BIGINT       NOT NULL REFERENCES analitica.lote_etl (id_lote) ON DELETE CASCADE,
    entidad       VARCHAR(20)  NOT NULL CHECK (entidad IN ('CONTRIBUYENTE', 'PREDIO', 'TITULAR', 'CUOTA', 'PAGO')),
    clave_origen  VARCHAR(60),
    regla         VARCHAR(10)  NOT NULL,
    motivo        VARCHAR(250) NOT NULL,
    registro      JSONB        NOT NULL
);

CREATE INDEX ix_plata_cuarentena_lote ON plata.cuarentena (id_lote, regla);

-- ===== Homologación con revisión humana (RF-040) =====

-- Un registro que la cascada no puede resolver sola (N2) entra con una entidad maestra provisional, así sus datos se
-- siguen publicando con su propio seudónimo mientras el Responsable de datos decide. El caso guarda esa entidad.
ALTER TABLE analitica.caso_homologacion
    ADD COLUMN id_maestro_provisional BIGINT REFERENCES maestros.entidad_maestra (id_maestro),
    ADD COLUMN fecha_creacion         TIMESTAMP;

CREATE INDEX ix_caso_homologacion_provisional ON analitica.caso_homologacion (id_maestro_provisional);

-- FUSIONADA: la entidad provisional se unió a otra al aprobar o reasignar el caso; ya no tiene correspondencias.
ALTER TABLE maestros.entidad_maestra DROP CONSTRAINT entidad_maestra_estado_check;
ALTER TABLE maestros.entidad_maestra
    ADD CONSTRAINT ck_entidad_maestra_estado CHECK (estado IN ('ACTIVA', 'PENDIENTE_REVISION', 'FUSIONADA'));
