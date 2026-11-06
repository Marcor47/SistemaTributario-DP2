-- Solo perfil local. Completa la fecha y el canal del último pago en las cargas de demostración, a partir de los pagos
-- sintéticos de SAMNET. Es idempotente y se ejecuta en cada inicio (${flyway:timestamp}) porque
-- R__datos_prueba_10_etl_cobranza vuelve a crear los hechos de su carga cuando cambia.
WITH ultimo_pago AS (
    SELECT DISTINCT ON (po.id_obligacion) po.id_obligacion, p.fecha_pago, p.canal
    FROM samnet.pago_obligacion po
    JOIN samnet.pago p ON p.id_pago = po.id_pago
    ORDER BY po.id_obligacion, p.fecha_pago DESC, p.id_pago DESC
),
cuotas AS (
    SELECT o.contribuyente_codigo, o.codigo_predio, o.tributo, o.anio, o.numero_cuota, u.fecha_pago, u.canal,
           encode(public.hmac(o.contribuyente_codigo, 'samnet-demo-contribuyente', 'sha256'), 'hex') AS seudonimo_carga,
           encode(public.hmac('CONTRIBUYENTE:' || o.contribuyente_codigo,
                              'CLAVE_PUBLICA_SOLO_SEMILLA_FICTICIA_HU011', 'sha256'), 'hex') AS seudonimo_demo,
           encode(public.hmac(o.codigo_predio, 'samnet-demo-predio', 'sha256'), 'hex') AS predio_carga,
           encode(public.hmac('PREDIO:' || o.codigo_predio,
                              'CLAVE_PUBLICA_SOLO_SEMILLA_FICTICIA_HU011', 'sha256'), 'hex') AS predio_demo
    FROM samnet.obligacion o
    JOIN ultimo_pago u ON u.id_obligacion = o.id
)
UPDATE analitica.hecho_cobranza h
SET fecha_pago = c.fecha_pago,
    canal_pago = c.canal
FROM cuotas c, analitica.dim_predio p
WHERE p.id_predio = h.id_predio
  AND h.seudonimo_contribuyente IN (c.seudonimo_carga, c.seudonimo_demo)
  AND h.tributo = c.tributo
  AND h.anio = c.anio
  AND h.cuota = c.numero_cuota
  AND (c.codigo_predio IS NULL OR p.seudonimo_predio IN (c.predio_carga, c.predio_demo))
  AND (h.fecha_pago IS DISTINCT FROM c.fecha_pago OR h.canal_pago IS DISTINCT FROM c.canal);
