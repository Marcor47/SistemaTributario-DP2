-- Datos ficticios de demostración: las mismas cuotas que genera frontweb/src/mocks/cuentas.js
-- (Impuesto Predial y arbitrios x 6 trimestres por contribuyente). El Impuesto Predial no tiene predio; los arbitrios sí.
INSERT INTO samnet.obligacion
    (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido, monto_pagado)
SELECT d.contribuyente_codigo, d.codigo_predio, d.tributo, d.anio, d.numero_cuota, d.fecha_vencimiento,
       d.monto_emitido, d.monto_pagado
FROM (VALUES
    ('014502', NULL, 'IMPUESTO_PREDIAL', 2025, 1, DATE '2025-03-31', 180.00, 180.00),
    ('014502', NULL, 'IMPUESTO_PREDIAL', 2025, 2, DATE '2025-06-30', 183.60, 183.60),
    ('014502', NULL, 'IMPUESTO_PREDIAL', 2025, 3, DATE '2025-09-30', 187.20, 187.20),
    ('014502', NULL, 'IMPUESTO_PREDIAL', 2025, 4, DATE '2025-12-31', 190.80, 190.80),
    ('014502', NULL, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-03-31', 194.40, 194.40),
    ('014502', NULL, 'IMPUESTO_PREDIAL', 2026, 2, DATE '2026-06-30', 198.00, 198.00),
    ('014502', '014502-01', 'ARBITRIOS', 2025, 1, DATE '2025-03-31', 230.00, 230.00),
    ('014502', '014502-01', 'ARBITRIOS', 2025, 2, DATE '2025-06-30', 234.60, 234.60),
    ('014502', '014502-01', 'ARBITRIOS', 2025, 3, DATE '2025-09-30', 239.20, 239.20),
    ('014502', '014502-01', 'ARBITRIOS', 2025, 4, DATE '2025-12-31', 243.80, 243.80),
    ('014502', '014502-01', 'ARBITRIOS', 2026, 1, DATE '2026-03-31', 248.40, 248.40),
    ('014502', '014502-01', 'ARBITRIOS', 2026, 2, DATE '2026-06-30', 253.00, 253.00),
    ('021390', NULL, 'IMPUESTO_PREDIAL', 2025, 1, DATE '2025-03-31', 936.00, 936.00),
    ('021390', NULL, 'IMPUESTO_PREDIAL', 2025, 2, DATE '2025-06-30', 954.72, 954.72),
    ('021390', NULL, 'IMPUESTO_PREDIAL', 2025, 3, DATE '2025-09-30', 973.44, 973.44),
    ('021390', NULL, 'IMPUESTO_PREDIAL', 2025, 4, DATE '2025-12-31', 992.16, 0.00),
    ('021390', NULL, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-03-31', 1010.88, 0.00),
    ('021390', NULL, 'IMPUESTO_PREDIAL', 2026, 2, DATE '2026-06-30', 1029.60, 0.00),
    ('021390', '021390-01', 'ARBITRIOS', 2025, 1, DATE '2025-03-31', 1196.00, 1196.00),
    ('021390', '021390-01', 'ARBITRIOS', 2025, 2, DATE '2025-06-30', 1219.92, 1219.92),
    ('021390', '021390-01', 'ARBITRIOS', 2025, 3, DATE '2025-09-30', 1243.84, 1243.84),
    ('021390', '021390-01', 'ARBITRIOS', 2025, 4, DATE '2025-12-31', 1267.76, 0.00),
    ('021390', '021390-01', 'ARBITRIOS', 2026, 1, DATE '2026-03-31', 1291.68, 0.00),
    ('021390', '021390-01', 'ARBITRIOS', 2026, 2, DATE '2026-06-30', 1315.60, 0.00),
    ('033218', NULL, 'IMPUESTO_PREDIAL', 2025, 1, DATE '2025-03-31', 99.00, 99.00),
    ('033218', NULL, 'IMPUESTO_PREDIAL', 2025, 2, DATE '2025-06-30', 100.98, 100.98),
    ('033218', NULL, 'IMPUESTO_PREDIAL', 2025, 3, DATE '2025-09-30', 102.96, 102.96),
    ('033218', NULL, 'IMPUESTO_PREDIAL', 2025, 4, DATE '2025-12-31', 104.94, 104.94),
    ('033218', NULL, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-03-31', 106.92, 106.92),
    ('033218', NULL, 'IMPUESTO_PREDIAL', 2026, 2, DATE '2026-06-30', 108.90, 108.90),
    ('033218', '033218-01', 'ARBITRIOS', 2025, 1, DATE '2025-03-31', 126.50, 126.50),
    ('033218', '033218-01', 'ARBITRIOS', 2025, 2, DATE '2025-06-30', 129.04, 129.04),
    ('033218', '033218-01', 'ARBITRIOS', 2025, 3, DATE '2025-09-30', 131.56, 131.56),
    ('033218', '033218-01', 'ARBITRIOS', 2025, 4, DATE '2025-12-31', 134.10, 134.10),
    ('033218', '033218-01', 'ARBITRIOS', 2026, 1, DATE '2026-03-31', 136.62, 136.62),
    ('033218', '033218-01', 'ARBITRIOS', 2026, 2, DATE '2026-06-30', 139.16, 139.16),
    ('045067', NULL, 'IMPUESTO_PREDIAL', 2025, 1, DATE '2025-03-31', 432.00, 432.00),
    ('045067', NULL, 'IMPUESTO_PREDIAL', 2025, 2, DATE '2025-06-30', 440.64, 440.64),
    ('045067', NULL, 'IMPUESTO_PREDIAL', 2025, 3, DATE '2025-09-30', 449.28, 449.28),
    ('045067', NULL, 'IMPUESTO_PREDIAL', 2025, 4, DATE '2025-12-31', 457.92, 457.92),
    ('045067', NULL, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-03-31', 466.56, 466.56),
    ('045067', NULL, 'IMPUESTO_PREDIAL', 2026, 2, DATE '2026-06-30', 475.20, 0.00),
    ('045067', '045067-01', 'ARBITRIOS', 2025, 1, DATE '2025-03-31', 552.00, 552.00),
    ('045067', '045067-01', 'ARBITRIOS', 2025, 2, DATE '2025-06-30', 563.04, 563.04),
    ('045067', '045067-01', 'ARBITRIOS', 2025, 3, DATE '2025-09-30', 574.08, 574.08),
    ('045067', '045067-01', 'ARBITRIOS', 2025, 4, DATE '2025-12-31', 585.12, 585.12),
    ('045067', '045067-01', 'ARBITRIOS', 2026, 1, DATE '2026-03-31', 596.16, 596.16),
    ('045067', '045067-01', 'ARBITRIOS', 2026, 2, DATE '2026-06-30', 607.20, 0.00),
    ('058934', NULL, 'IMPUESTO_PREDIAL', 2025, 1, DATE '2025-03-31', 144.00, 144.00),
    ('058934', NULL, 'IMPUESTO_PREDIAL', 2025, 2, DATE '2025-06-30', 146.88, 146.88),
    ('058934', NULL, 'IMPUESTO_PREDIAL', 2025, 3, DATE '2025-09-30', 149.76, 149.76),
    ('058934', NULL, 'IMPUESTO_PREDIAL', 2025, 4, DATE '2025-12-31', 152.64, 152.64),
    ('058934', NULL, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-03-31', 155.52, 0.00),
    ('058934', NULL, 'IMPUESTO_PREDIAL', 2026, 2, DATE '2026-06-30', 158.40, 0.00),
    ('058934', '058934-01', 'ARBITRIOS', 2025, 1, DATE '2025-03-31', 184.00, 184.00),
    ('058934', '058934-01', 'ARBITRIOS', 2025, 2, DATE '2025-06-30', 187.68, 187.68),
    ('058934', '058934-01', 'ARBITRIOS', 2025, 3, DATE '2025-09-30', 191.36, 191.36),
    ('058934', '058934-01', 'ARBITRIOS', 2025, 4, DATE '2025-12-31', 195.04, 195.04),
    ('058934', '058934-01', 'ARBITRIOS', 2026, 1, DATE '2026-03-31', 198.72, 0.00),
    ('058934', '058934-01', 'ARBITRIOS', 2026, 2, DATE '2026-06-30', 202.40, 0.00),
    ('061177', NULL, 'IMPUESTO_PREDIAL', 2025, 1, DATE '2025-03-31', 144.00, 144.00),
    ('061177', NULL, 'IMPUESTO_PREDIAL', 2025, 2, DATE '2025-06-30', 146.88, 146.88),
    ('061177', NULL, 'IMPUESTO_PREDIAL', 2025, 3, DATE '2025-09-30', 149.76, 149.76),
    ('061177', NULL, 'IMPUESTO_PREDIAL', 2025, 4, DATE '2025-12-31', 152.64, 152.64),
    ('061177', NULL, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-03-31', 155.52, 0.00),
    ('061177', NULL, 'IMPUESTO_PREDIAL', 2026, 2, DATE '2026-06-30', 158.40, 0.00),
    ('061177', '058934-01', 'ARBITRIOS', 2025, 1, DATE '2025-03-31', 184.00, 184.00),
    ('061177', '058934-01', 'ARBITRIOS', 2025, 2, DATE '2025-06-30', 187.68, 187.68),
    ('061177', '058934-01', 'ARBITRIOS', 2025, 3, DATE '2025-09-30', 191.36, 191.36),
    ('061177', '058934-01', 'ARBITRIOS', 2025, 4, DATE '2025-12-31', 195.04, 195.04),
    ('061177', '058934-01', 'ARBITRIOS', 2026, 1, DATE '2026-03-31', 198.72, 0.00),
    ('061177', '058934-01', 'ARBITRIOS', 2026, 2, DATE '2026-06-30', 202.40, 0.00)
) AS d (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido, monto_pagado)
WHERE NOT EXISTS (
    SELECT 1 FROM samnet.obligacion o
    WHERE o.contribuyente_codigo = d.contribuyente_codigo
      AND o.tributo = d.tributo
      AND o.anio = d.anio
      AND o.numero_cuota = d.numero_cuota
)
ORDER BY d.contribuyente_codigo, d.anio, d.numero_cuota, d.tributo;

-- Etapas de la cobranza inductiva registradas para algunas deudas vencidas; las demás están en mora temprana
-- desde el día siguiente a su vencimiento.
INSERT INTO samnet.seguimiento_cobranza
    (obligacion_id, etapa, fecha_inicio, documento_referencia, observacion, fecha_registro)
SELECT o.id, s.etapa, s.fecha_inicio, s.documento_referencia, s.observacion, s.fecha_registro
FROM (VALUES
    ('021390', 'IMPUESTO_PREDIAL', 2025, 4, 'CAMPANA', DATE '2026-01-12', NULL, 'Incluida en la campaña de cobranza de enero', TIMESTAMP '2026-01-12 08:30'),
    ('021390', 'IMPUESTO_PREDIAL', 2025, 4, 'VALOR_EMITIDO', DATE '2026-01-20', 'OP-2026-000101', NULL, TIMESTAMP '2026-01-20 09:15'),
    ('021390', 'IMPUESTO_PREDIAL', 2025, 4, 'ESPERA_LEGAL', DATE '2026-02-03', 'OP-2026-000101', 'Notificado en el domicilio fiscal', TIMESTAMP '2026-02-03 16:40'),
    ('021390', 'IMPUESTO_PREDIAL', 2025, 4, 'COACTIVA', DATE '2026-05-12', 'OP-2026-000101', 'Venció la espera legal sin pago ni reclamación', TIMESTAMP '2026-05-12 11:05'),
    ('021390', 'ARBITRIOS', 2025, 4, 'VALOR_EMITIDO', DATE '2026-02-10', 'RD-2026-000310', NULL, TIMESTAMP '2026-02-10 10:00'),
    ('021390', 'ARBITRIOS', 2025, 4, 'ESPERA_LEGAL', DATE '2026-02-24', 'RD-2026-000310', NULL, TIMESTAMP '2026-02-24 15:20'),
    ('021390', 'IMPUESTO_PREDIAL', 2026, 1, 'VALOR_EMITIDO', DATE '2026-05-05', 'OP-2026-000388', NULL, TIMESTAMP '2026-05-05 08:50'),
    ('058934', 'IMPUESTO_PREDIAL', 2026, 1, 'MORA_TEMPRANA', DATE '2026-04-10', NULL, 'Aviso de cobranza enviado por correo', TIMESTAMP '2026-04-10 12:00'),
    ('058934', 'IMPUESTO_PREDIAL', 2026, 1, 'VALOR_EMITIDO', DATE '2026-06-01', 'OP-2026-000502', NULL, TIMESTAMP '2026-06-01 09:30')
) AS s (contribuyente_codigo, tributo, anio, numero_cuota, etapa, fecha_inicio, documento_referencia, observacion, fecha_registro)
JOIN samnet.obligacion o
  ON o.contribuyente_codigo = s.contribuyente_codigo
 AND o.tributo = s.tributo
 AND o.anio = s.anio
 AND o.numero_cuota = s.numero_cuota
ORDER BY o.id, s.fecha_inicio
ON CONFLICT (obligacion_id, etapa) DO NOTHING;
