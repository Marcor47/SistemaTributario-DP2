-- Cuentas FICTICIAS para Niubiz sandbox: una para verificar y otra para probar la app.
-- No restablece deudas pagadas ni credenciales existentes al reiniciar.
DO $$
DECLARE
    v_c RECORD;
BEGIN
    FOR v_c IN SELECT * FROM (VALUES
        ('099023', '99099023', 'DEMO HU023 - PAGO NIUBIZ SANDBOX'),
        ('099024', '99099024', 'DEMO HU023 - PAGO APP MOVIL')
    ) AS c(codigo, documento, nombre)
    LOOP
        INSERT INTO samnet.contribuyente
            (codigo, nombre, tipo_persona, tipo_documento, numero_documento, domicilio_fiscal, estado,
             regimen, condicion, correo, telefonos, dom_departamento, dom_provincia, dom_distrito)
        VALUES (v_c.codigo, v_c.nombre, 'NATURAL', 'DNI', v_c.documento,
                'Direccion ficticia de pruebas, San Miguel', 'ACTIVO', 'GENERAL', 'HABIDO',
                'demo.hu' || right(v_c.codigo, 3) || '@example.com', '999000' || right(v_c.codigo, 3),
                'LIMA', 'LIMA', 'SAN MIGUEL')
        ON CONFLICT (codigo) DO NOTHING;

        IF NOT EXISTS (SELECT 1 FROM samnet.contribuyente WHERE codigo = v_c.codigo
                       AND numero_documento = v_c.documento AND nombre = v_c.nombre) THEN
            CONTINUE;
        END IF;

        INSERT INTO samnet.credencial_web (contribuyente_codigo, clave_hash, habilitada)
        VALUES (v_c.codigo, public.crypt('Portal' || v_c.codigo || '!', public.gen_salt('bf', 12)), TRUE)
        ON CONFLICT (contribuyente_codigo) DO NOTHING;

        INSERT INTO samnet.obligacion
            (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota, fecha_vencimiento,
             monto_emitido, monto_pagado)
        SELECT v_c.codigo, NULL, 'IMPUESTO_PREDIAL', 2026, 4, DATE '2026-12-31', 1.00, 0.00
        WHERE NOT EXISTS (SELECT 1 FROM samnet.obligacion WHERE contribuyente_codigo = v_c.codigo
                          AND tributo = 'IMPUESTO_PREDIAL' AND anio = 2026 AND numero_cuota = 4);
    END LOOP;
END $$;
