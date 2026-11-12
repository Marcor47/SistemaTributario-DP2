ALTER TABLE operativo.documento_buzon
    ADD COLUMN contenido TEXT;

ALTER TABLE operativo.documento_buzon
    DROP CONSTRAINT IF EXISTS documento_buzon_id_reclamo_key;

ALTER TABLE operativo.documento_buzon
    DROP CONSTRAINT ck_documento_buzon_tipo;

ALTER TABLE operativo.documento_buzon
    ADD CONSTRAINT ck_documento_buzon_tipo CHECK (tipo IN
        ('CONSTANCIA', 'ESTADO_CUENTA', 'RESPUESTA_SOLICITUD', 'ASIGNACION_RECLAMO', 'RESPUESTA_RECLAMO'));

CREATE UNIQUE INDEX ux_documento_buzon_reclamo_tipo
    ON operativo.documento_buzon (id_reclamo, tipo)
    WHERE id_reclamo IS NOT NULL;
