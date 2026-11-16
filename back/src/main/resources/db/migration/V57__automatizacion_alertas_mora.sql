-- La automatización es optativa al instalarse: nunca despacha lotes históricos por sorpresa.
CREATE TABLE operativo.configuracion_alertas_mora (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    ultimo_lote_detectado BIGINT NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMP NOT NULL DEFAULT now(),
    actualizado_por VARCHAR(120)
);
INSERT INTO operativo.configuracion_alertas_mora (id, ultimo_lote_detectado)
SELECT 1, COALESCE(MAX(id_lote), 0) FROM analitica.lote_etl;

CREATE TABLE operativo.proceso_alertas_mora (
    id_lote BIGINT PRIMARY KEY REFERENCES analitica.lote_etl (id_lote),
    ultima_fila INTEGER NOT NULL DEFAULT 0,
    estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'CON_ERRORES', 'COMPLETADO')),
    proximo_reintento TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX ix_proceso_alertas_mora_estado ON operativo.proceso_alertas_mora (estado, id_lote);
