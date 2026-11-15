-- Los arbitrios municipales se manejan como un solo concepto con su monto total: se elimina el desglose por
-- barrido de calles, recolección de residuos, parques y jardines y serenazgo (V31).

-- Cada cuota conserva como monto emitido la suma de sus componentes.
UPDATE samnet.obligacion o
SET monto_emitido = s.total
FROM (SELECT id_obligacion, sum(monto) AS total
      FROM samnet.obligacion_servicio
      GROUP BY id_obligacion) s
WHERE s.id_obligacion = o.id
  AND o.monto_emitido <> s.total
  AND s.total >= o.monto_pagado + o.monto_descontado;

DROP TABLE samnet.obligacion_servicio;

-- Una tarifa mensual de arbitrios por año, categoría de uso y zona: la suma de las tasas de los cuatro servicios.
CREATE TABLE samnet.tarifa_arbitrio (
    anio          INTEGER        NOT NULL,
    categoria_uso VARCHAR(25)    NOT NULL CHECK (categoria_uso IN
                      ('CASA_HABITACION', 'COMERCIO', 'SERVICIOS', 'INDUSTRIA', 'TERRENO_SIN_CONSTRUIR')),
    zona          VARCHAR(20)    NOT NULL,
    monto_mensual NUMERIC(10, 2) NOT NULL CHECK (monto_mensual >= 0),
    PRIMARY KEY (anio, categoria_uso, zona)
);

INSERT INTO samnet.tarifa_arbitrio (anio, categoria_uso, zona, monto_mensual)
SELECT anio, categoria_uso, zona, sum(monto_mensual)
FROM samnet.tasa_arbitrio
GROUP BY anio, categoria_uso, zona;

DROP TABLE samnet.tasa_arbitrio;
ALTER TABLE samnet.tarifa_arbitrio RENAME TO tasa_arbitrio;
