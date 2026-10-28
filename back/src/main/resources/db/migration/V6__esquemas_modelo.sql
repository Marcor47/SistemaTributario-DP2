-- Modelo de Análisis y Diseño v1: una sola base de datos (municipalidad) organizada en esquemas.
--
--   samnet     BD sintética que emula a SAMNET. No hay acceso a los SP reales de la Municipalidad,
--              así que se consultan funciones propias (samnet.ps_*) con el mismo contrato. El sistema
--              solo la lee y escribe a través de SamnetGateway; ningún otro esquema tiene FK hacia ella.
--   seguridad  usuarios internos, roles, sesiones, auditoría, eventos de seguridad y parámetros.
--   operativo  trámites del portal y ventanilla, plantillas, buzón, avisos, copia del estado de cuenta
--              y configuración de reportes.
--   maestros   entidades maestras y correspondencia con el origen (datos reidentificables, RNF-018).
--   analitica  lotes del ETL, homologación, modelo estrella, indicadores, metas, alertas e IA.

ALTER SCHEMA tributario RENAME TO samnet;

CREATE SCHEMA IF NOT EXISTS seguridad;
CREATE SCHEMA IF NOT EXISTS operativo;
CREATE SCHEMA IF NOT EXISTS maestros;
CREATE SCHEMA IF NOT EXISTS analitica;

COMMENT ON SCHEMA samnet IS 'BD sintética que emula SAMNET; acceso solo mediante las funciones ps_* (SamnetGateway).';
COMMENT ON SCHEMA seguridad IS 'Usuarios internos, roles, sesiones, auditoría, eventos de seguridad y parámetros.';
COMMENT ON SCHEMA operativo IS 'Trámites, plantillas, buzón, avisos, copia del estado de cuenta y reportes.';
COMMENT ON SCHEMA maestros IS 'Entidades maestras y correspondencia con el origen; acceso restringido (RNF-018).';
COMMENT ON SCHEMA analitica IS 'ETL, homologación, modelo estrella, indicadores, metas, alertas e IA.';

REVOKE ALL ON SCHEMA maestros FROM PUBLIC;

-- Hash bcrypt para las credenciales sintéticas del servicio de autenticación municipal (samnet.ps_login).
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

-- Impide modificar o borrar registros de auditoría desde la aplicación (RNF-008, RNF-014, RF-053).
CREATE FUNCTION seguridad.fn_registro_inalterable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El registro de % no se puede modificar ni eliminar', TG_TABLE_NAME
        USING ERRCODE = 'insufficient_privilege';
END;
$$;
