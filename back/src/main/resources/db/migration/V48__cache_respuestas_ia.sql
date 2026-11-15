-- Caché de las respuestas del modelo de lenguaje (RF-023, RF-058). El modelo corre en un equipo con cupo limitado de
-- consultas: un resumen o una respuesta ya generada para la misma carga publicada se reutiliza en vez de volver a
-- consultarlo.

-- Un resumen se genera para un periodo y una carga del ETL; con la misma carga se devuelve el ya guardado.
ALTER TABLE analitica.resumen_ejecutivo
    ADD COLUMN id_lote BIGINT REFERENCES analitica.lote_etl (id_lote);

CREATE INDEX ix_resumen_ejecutivo_periodo_lote
    ON analitica.resumen_ejecutivo (periodo, id_lote, fecha_generacion DESC);

-- Respuesta del asistente para una pregunta normalizada, sus filtros y la versión de la carga analítica.
CREATE TABLE analitica.respuesta_asistente (
    clave          VARCHAR(64)  PRIMARY KEY,
    id_lote        BIGINT       NOT NULL REFERENCES analitica.lote_etl (id_lote),
    pregunta       VARCHAR(500) NOT NULL,
    respuesta      TEXT         NOT NULL,
    fecha_registro TIMESTAMP    NOT NULL,
    veces_usada    INTEGER      NOT NULL DEFAULT 0 CHECK (veces_usada >= 0)
);

CREATE INDEX ix_respuesta_asistente_lote ON analitica.respuesta_asistente (id_lote);
