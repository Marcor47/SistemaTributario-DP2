ALTER TABLE operativo.aviso_correo DROP CONSTRAINT aviso_correo_estado_check;
ALTER TABLE operativo.aviso_correo ADD CONSTRAINT aviso_correo_estado_check
    CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO', 'SIMULADO'));

-- HU016 solo ha tenido un proveedor simulado hasta esta versión. No debe impedir el envío real futuro.
UPDATE operativo.aviso_correo SET estado = 'SIMULADO'
WHERE tipo = 'RECORDATORIO_VENCIMIENTO' AND estado = 'ENVIADO';
