-- Carga del ETL para desarrollo y pruebas (HU006, HU018, HU025, HU026), con fecha de corte fija 2026-10-19, la misma
-- del reloj de los tests y de los mocks del frontend.
-- Ejecuta el ETL real (R__etl_pipeline.sql): extrae SAMNET sintético con sus ps_etl_*, lo copia a bronce, lo valida y
-- homologa en plata y lo publica en el modelo estrella y los maestros. Reemplaza a R__datos_prueba_10, que escribía
-- el modelo estrella a mano; el resultado es el mismo (mismos hechos, montos y seudónimos), más la cuarentena, los
-- casos de homologación y los hechos de pago.
-- Va después de R__samnet_procedimientos (por eso empieza con zz): necesita las funciones de extracción.
-- La clave HMAC es pública y solo sirve para los datos ficticios. Flyway la vuelve a ejecutar cuando cambia este
-- archivo; cada ejecución es un lote nuevo y los reportes usan el más reciente.
SELECT etl.ejecutar_lote(DATE '2026-10-19', 'PROGRAMADA', 'samnet-demo-contribuyente', 'samnet-demo-predio',
                         TIMESTAMP '2026-10-19 02:00');
