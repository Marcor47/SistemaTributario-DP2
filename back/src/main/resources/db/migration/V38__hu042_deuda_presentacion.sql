-- HU042: la condición de no adeudo se evalúa y congela al presentar la solicitud en ventanilla.
-- Las solicitudes anteriores quedan en NULL y se evalúan una única vez al llegar a la verificación.
ALTER TABLE operativo.solicitud_constancia
    ADD COLUMN deuda_presentacion NUMERIC(12,2),
    ADD COLUMN fecha_deuda_presentacion TIMESTAMP;

ALTER TABLE operativo.solicitud_constancia
    ADD CONSTRAINT ck_solicitud_deuda_presentacion
    CHECK (deuda_presentacion IS NULL OR deuda_presentacion >= 0);
