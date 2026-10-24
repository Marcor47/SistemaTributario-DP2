-- Datos ficticios de SAMNET sintético: pagos coherentes con el monto pagado de cada obligación de
-- R__datos_prueba_03 (un recibo por contribuyente y cuota) y beneficios tributarios.

INSERT INTO samnet.beneficio_tributario
    (id_beneficio, contribuyente_codigo, tipo, porcentaje_descuento, vigencia_desde, vigencia_hasta)
VALUES
    ('BEN-2026-0001', '033218', 'PARTICULAR', 50.00, DATE '2026-01-01', DATE '2026-12-31'),
    ('BEN-2026-0002', '058934', 'AMNISTIA', 100.00, DATE '2026-10-01', DATE '2026-11-30')
ON CONFLICT (id_beneficio) DO NOTHING;

-- Se paga a tiempo cuando la cuota quedó cancelada; un pago parcial se registra 20 días después del vencimiento.
INSERT INTO samnet.pago
    (id_pago, contribuyente_codigo, fecha_pago, canal, monto_pagado, monto_correspondiente, id_beneficio)
SELECT 'REC-' || o.contribuyente_codigo || '-' || o.anio || '-' || o.numero_cuota,
       o.contribuyente_codigo,
       CASE WHEN bool_and(o.monto_pagado = o.monto_emitido) THEN min(o.fecha_vencimiento) - 5
            ELSE min(o.fecha_vencimiento) + 20 END,
       CASE WHEN o.numero_cuota % 2 = 0 THEN 'EN_LINEA' ELSE 'VENTANILLA' END,
       sum(o.monto_pagado),
       sum(o.monto_pagado),
       NULL
FROM samnet.obligacion o
WHERE o.monto_pagado > 0
GROUP BY o.contribuyente_codigo, o.anio, o.numero_cuota
ON CONFLICT (id_pago) DO NOTHING;

INSERT INTO samnet.pago_obligacion (id_pago, id_obligacion, monto_aplicado)
SELECT 'REC-' || o.contribuyente_codigo || '-' || o.anio || '-' || o.numero_cuota, o.id, o.monto_pagado
FROM samnet.obligacion o
JOIN samnet.pago p ON p.id_pago = 'REC-' || o.contribuyente_codigo || '-' || o.anio || '-' || o.numero_cuota
WHERE o.monto_pagado > 0
ON CONFLICT (id_pago, id_obligacion) DO NOTHING;
