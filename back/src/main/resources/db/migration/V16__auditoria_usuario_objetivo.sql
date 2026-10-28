-- Permite atribuir cada alta y cambio de rol al usuario afectado (HU035).
ALTER TABLE seguridad.registro_auditoria
    ADD COLUMN id_usuario_objetivo BIGINT REFERENCES seguridad.usuario (id_usuario);
CREATE INDEX ix_registro_auditoria_objetivo ON seguridad.registro_auditoria (id_usuario_objetivo);
