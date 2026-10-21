-- HU001: padrón de contribuyentes (copia local de los datos maestros de SAMNET)
CREATE SCHEMA IF NOT EXISTS tributario;

CREATE TABLE tributario.contribuyente (
    codigo           VARCHAR(10)  PRIMARY KEY,
    nombre           VARCHAR(200) NOT NULL,
    tipo_persona     VARCHAR(10)  NOT NULL CHECK (tipo_persona IN ('NATURAL', 'JURIDICA')),
    tipo_documento   VARCHAR(5)   NOT NULL CHECK (tipo_documento IN ('DNI', 'RUC', 'CE')),
    numero_documento VARCHAR(12)  NOT NULL,
    domicilio_fiscal VARCHAR(250),
    estado           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT uq_contribuyente_documento UNIQUE (tipo_documento, numero_documento)
);
