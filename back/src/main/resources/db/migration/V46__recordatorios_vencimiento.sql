-- HU016: contrato provisional de SAMNET para localizar cuotas próximas a vencer.
CREATE OR REPLACE FUNCTION samnet.ps_recordatorios_vencimiento(desde DATE, hasta DATE)
RETURNS TABLE(id_obligacion BIGINT, codigo_contribuyente VARCHAR, numero_documento VARCHAR,
              nombre VARCHAR, correo VARCHAR, tributo VARCHAR, fecha_vencimiento DATE, saldo NUMERIC)
LANGUAGE SQL STABLE AS $$
    SELECT o.id, c.codigo, c.numero_documento, c.nombre, c.correo, o.tributo,
           o.fecha_vencimiento, o.monto_emitido - o.monto_pagado - o.monto_descontado
    FROM samnet.obligacion o JOIN samnet.contribuyente c ON c.codigo = o.contribuyente_codigo
    WHERE o.fecha_vencimiento BETWEEN desde AND hasta
      AND o.monto_emitido - o.monto_pagado - o.monto_descontado > 0;
$$;
