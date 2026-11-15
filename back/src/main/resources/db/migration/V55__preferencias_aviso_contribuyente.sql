-- HU016 (RF-022): preferencia del contribuyente para recibir recordatorios de vencimiento (P12).
-- La ausencia de fila equivale a recordatorio activo, para no cambiar el comportamiento de los datos existentes.
CREATE TABLE IF NOT EXISTS operativo.preferencia_aviso (
    codigo_contribuyente     VARCHAR(10) PRIMARY KEY,
    recordatorio_vencimiento BOOLEAN     NOT NULL DEFAULT TRUE,
    actualizado_en           TIMESTAMP   NOT NULL DEFAULT now()
);
