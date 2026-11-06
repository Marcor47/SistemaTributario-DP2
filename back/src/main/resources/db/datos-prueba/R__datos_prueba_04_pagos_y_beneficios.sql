-- Datos ficticios de SAMNET sintético: beneficios tributarios. Los pagos se generan junto con las cuotas en
-- R__datos_prueba_03, con el perfil de pago de cada contribuyente.

INSERT INTO samnet.beneficio_tributario
    (id_beneficio, contribuyente_codigo, tipo, porcentaje_descuento, vigencia_desde, vigencia_hasta)
VALUES
    ('BEN-2026-0001', '033218', 'PARTICULAR', 50.00, DATE '2026-01-01', DATE '2026-12-31'),
    ('BEN-2026-0002', '058934', 'AMNISTIA', 100.00, DATE '2026-10-01', DATE '2026-11-30')
ON CONFLICT (id_beneficio) DO NOTHING;
