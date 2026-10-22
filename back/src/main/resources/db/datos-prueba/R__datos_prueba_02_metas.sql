-- Datos ficticios de demostración para la pantalla de metas (HU009).
-- Indicador inactivo solo para mostrar el caso en que no se admiten nuevas metas.
INSERT INTO analitica.indicador
    (codigo, nombre, definicion, formula, fuente, unidad_medida, periodicidad, sentido, activo)
VALUES
    ('CUPONERAS_ENTREGADAS', 'Cuponeras entregadas',
     'Cantidad de cuponeras físicas entregadas a domicilio. Reemplazado por la notificación digital.',
     'Conteo de cuponeras con cargo de entrega', 'Registro de distribución', 'CANTIDAD', 'ANUAL', 'ASCENDENTE', FALSE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO analitica.meta_indicador
    (indicador_codigo, anio, numero_periodo, valor_meta, observacion, fecha_registro, fecha_actualizacion)
VALUES
    ('RECUPERACION_DEUDA', 2026, 3, 30.00, 'Meta del plan de recaudación 2026', '2026-06-15 09:00', '2026-06-15 09:00'),
    ('PORC_RECAUDACION_EMISION', 2026, 10, 85.00, NULL, '2026-09-20 10:30', '2026-09-20 10:30'),
    ('PORC_RECAUDACION_EMISION', 2026, 11, 87.50, NULL, '2026-09-20 10:31', '2026-09-20 10:31'),
    ('PORC_RECAUDACION_EMISION', 2026, 12, 90.00, 'Cierre de año: campaña de pronto pago', '2026-09-20 10:32', '2026-09-20 10:32'),
    ('INDICE_MOROSIDAD', 2026, 10, 12.50, NULL, '2026-09-20 10:40', '2026-09-20 10:40'),
    ('RECUPERACION_DEUDA', 2026, 4, 35.00, NULL, '2026-09-20 10:45', '2026-09-20 10:45')
ON CONFLICT (indicador_codigo, anio, numero_periodo) DO NOTHING;
