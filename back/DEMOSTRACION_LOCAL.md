# Datos para probar HU011 y HU019

Al iniciar `mvnw.cmd spring-boot:run`, el perfil `local` ejecuta Flyway con las carpetas
`db/migration`, `db/datos-prueba` y `db/datos-demo`. El resto del equipo recibe las mismas
semillas al actualizar la rama y reiniciar el backend. Las bases nuevas también las cargan.

No se necesitan credenciales Niubiz para ver el dashboard ni el buzón. El perfil normal
sin `local` no incorpora estas semillas de demostración.

| Pantalla | Usuario | Contraseña de desarrollo |
| --- | --- | --- |
| Web: dashboard de Gerencia | `gerencia` | `gerencia123` |
| App móvil: Mi buzón | `061177` | `Portal061177!` |
| App móvil: prueba Niubiz de S/ 1.00 | `099024` | `Portal099024!` |

El buzón contiene tres ejemplos: una constancia descargable marcada DOCUMENTO DE PRUEBA,
una respuesta de solicitud de predio y un estado de cuenta registrado sin archivo. Dos
empiezan sin leer y uno ya leído. No se restablecen las lecturas ni se duplican los documentos
en los reinicios. El PDF no acredita ausencia de deuda, una firma ni un trámite real.

El dashboard utiliza un lote `DEMO_HU011_SAMNET_SINTETICO` con corte **04/11/2026**. Es una
foto de las obligaciones de los seis contribuyentes ficticios conocidos, extraída mediante
los procedimientos `ps_etl_*`, con identificadores seudonimizados. Las cuotas de Predial
sin predio asociado se conservan en `SIN_ZONA`; no se les asigna una zona inventada.
Se incluyen metas de ejemplo y segmentos rotulados Demo. Esos segmentos son categorías
fijas de la semilla, no resultados de un modelo de IA.

Esta carga permite probar el dashboard mientras se incorpora el proceso ETL de HU025.
No modifica las obligaciones ni los pagos de SAMNET. No se actualiza automáticamente al
pagar: es una foto de demostración, independiente de las siguientes cargas del ETL.

ETL significa extraer, transformar y cargar: toma los datos operativos de SAMNET, valida y
prepara sus dimensiones y hechos, y publica una carga analítica para consultas y reportes.

| Endpoint HU011 | Resultado |
| --- | --- |
| `GET /api/dashboard` | Importes, morosidad, avance de metas, resultados por tributo, zona y segmento y fechas de corte. |
| `GET /api/dashboard/filtros` | Opciones disponibles de años, zonas, tributos y segmentos. |

Ambas rutas requieren una sesión interna con `DASHBOARD:CONSULTAR`. HU011 consulta la carga
publicada; no ejecuta ni reemplaza el ETL. La app consulta el buzón propio por `GET /api/buzon`.

Para activar el pago de pruebas, sigue [NIUBIZ_SANDBOX.md](NIUBIZ_SANDBOX.md). Las semillas
no habilitan Niubiz ni generan autorizaciones o cargos externos.

Las cuentas `099023` y `099024` son adicionales y no forman parte de la foto analítica del
dashboard. Cada una nace con una cuota de S/ 1.00. En este equipo ambas cuotas iniciales se
pagaron; por solicitud del usuario, `099024` recibe otra cuota de S/ 1.00 de 2027-T1 para
seguir probando la app. La semilla no reabre cuotas pagadas.
