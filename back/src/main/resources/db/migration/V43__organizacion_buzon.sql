ALTER TABLE operativo.documento_buzon
    ADD COLUMN leido_actualmente BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN destacado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN archivado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fecha_archivado TIMESTAMP;

UPDATE operativo.documento_buzon
SET leido_actualmente = fecha_primera_lectura IS NOT NULL;

ALTER TABLE operativo.documento_buzon
    ADD CONSTRAINT ck_documento_buzon_archivado
        CHECK ((archivado AND fecha_archivado IS NOT NULL) OR (NOT archivado AND fecha_archivado IS NULL));

CREATE INDEX ix_documento_buzon_bandeja
    ON operativo.documento_buzon (documento_contribuyente, archivado, fecha_envio DESC);

CREATE INDEX ix_documento_buzon_destacados
    ON operativo.documento_buzon (documento_contribuyente, destacado, fecha_envio DESC);
