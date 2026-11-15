-- Código de la Constancia de No Adeudo: deja de ser un correlativo (CNA-2026-000123, fácil de adivinar) y pasa a ser
-- único y no predecible, con formato profesional: CND-2026-7F3K9Q (prefijo, año de emisión y seis caracteres al azar).
-- El alfabeto omite 0/O, 1/I/L para que el código se pueda dictar y transcribir sin ambigüedad. La unicidad la
-- garantiza la clave primaria; la función reintenta si el azar repite un código ya emitido.
CREATE OR REPLACE FUNCTION operativo.fn_siguiente_numero_constancia(p_fecha DATE) RETURNS VARCHAR
LANGUAGE plpgsql VOLATILE AS $$
DECLARE
    v_alfabeto CONSTANT TEXT := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
    v_codigo   VARCHAR(20);
    v_bytes    BYTEA;
BEGIN
    LOOP
        v_bytes := public.gen_random_bytes(6);
        v_codigo := 'CND-' || to_char(p_fecha, 'YYYY') || '-';
        FOR i IN 0..5 LOOP
            v_codigo := v_codigo || substr(v_alfabeto, (get_byte(v_bytes, i) % length(v_alfabeto)) + 1, 1);
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM operativo.constancia WHERE numero_constancia = v_codigo);
    END LOOP;
    RETURN v_codigo;
END;
$$;

-- Las constancias ya emitidas reciben su código nuevo; el buzón sigue apuntando a ellas.
ALTER TABLE operativo.documento_buzon DROP CONSTRAINT documento_buzon_numero_constancia_fkey;
ALTER TABLE operativo.documento_buzon
    ADD CONSTRAINT documento_buzon_numero_constancia_fkey FOREIGN KEY (numero_constancia)
        REFERENCES operativo.constancia (numero_constancia) ON UPDATE CASCADE;

DO $$
DECLARE
    v_constancia RECORD;
    v_nuevo      VARCHAR(20);
BEGIN
    FOR v_constancia IN
        SELECT numero_constancia, fecha_emision FROM operativo.constancia
        WHERE numero_constancia NOT LIKE 'CND-____-______'
    LOOP
        v_nuevo := operativo.fn_siguiente_numero_constancia(v_constancia.fecha_emision::DATE);
        UPDATE operativo.constancia
        SET numero_constancia = v_nuevo,
            archivo_pdf = replace(archivo_pdf, v_constancia.numero_constancia, v_nuevo)
        WHERE numero_constancia = v_constancia.numero_constancia;
        UPDATE operativo.documento_buzon
        SET titulo = replace(titulo, v_constancia.numero_constancia, v_nuevo),
            archivo = replace(archivo, v_constancia.numero_constancia, v_nuevo)
        WHERE numero_constancia = v_nuevo;
    END LOOP;
END;
$$;

DROP SEQUENCE IF EXISTS operativo.seq_numero_constancia;
