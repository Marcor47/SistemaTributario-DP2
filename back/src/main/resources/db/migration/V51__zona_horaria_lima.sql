-- Zona horaria del sistema: America/Lima (UTC-5). El backend genera sus fechas con el reloj de Lima y cada conexión
-- fija SET TIME ZONE 'America/Lima'; aquí la base también la adopta por defecto (consultas manuales, now()).
-- Registros existentes: las columnas TIMESTAMP sin zona se llenaron siempre con el reloj de Lima del backend (o con
-- fechas relativas a él en el ETL) y la única fecha que generó la base (V26) es TIMESTAMP WITH TIME ZONE, que guarda
-- el instante absoluto; por eso no hay datos que desplazar.
-- Solo el dueño de la base puede cambiar su configuración: si quien migra no lo es, la conexión la sigue fijando.
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', current_database(), 'America/Lima');
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'Sin permiso para fijar la zona horaria de la base; la conexión usa America/Lima.';
END;
$$;
