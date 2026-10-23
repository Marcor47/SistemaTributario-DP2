CREATE TABLE seguridad.credencial_contribuyente_local (
    codigo_contribuyente VARCHAR(10) PRIMARY KEY,
    password_hash        VARCHAR(60) NOT NULL,
    habilitada           BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credencial_local_contribuyente FOREIGN KEY (codigo_contribuyente)
        REFERENCES tributario.contribuyente (codigo),
    CONSTRAINT ck_credencial_local_hash_bcrypt CHECK (password_hash LIKE '$2_$12$%')
);
