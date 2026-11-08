-- HU012: Enviar alerta de mora temprana por correo (RF-013).
-- Índices para optimizar la consulta del historial de avisos del contribuyente y por lote/obligación.
CREATE INDEX IF NOT EXISTS ix_aviso_correo_contribuyente
    ON operativo.aviso_correo (documento_contribuyente, fecha_envio DESC);

CREATE INDEX IF NOT EXISTS ix_aviso_correo_lote
    ON operativo.aviso_correo (id_lote);

CREATE INDEX IF NOT EXISTS ix_aviso_correo_obligacion
    ON operativo.aviso_correo (id_obligacion);

-- Permisos para que Gerencia de Rentas pueda evaluar y disparar alertas de mora temprana.
UPDATE seguridad.rol
SET permisos = CONCAT_WS(',', NULLIF(permisos, ''), 'ALERTAS:EVALUAR')
WHERE nombre = 'GERENCIA'
  AND NOT ('ALERTAS:EVALUAR' = ANY(string_to_array(COALESCE(permisos, ''), ',')));

