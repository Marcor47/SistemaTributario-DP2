# Plataforma analítica de Rentas

Módulo de la web interna en `/reporteria` (RF-008, RF-012, RF-044, RF-058, RNF-003). Usa la última carga publicada del
ETL con hechos, la misma del dashboard, y aplica sus reglas: una cuota es exigible si vence antes de la fecha de corte,
lo cobrado es lo pagado de esas cuotas y la deuda es su insoluto. Los contribuyentes se muestran con su seudónimo
(RNF-018). Para ver los datos con identidad se usa el módulo Reportes, que pasa por la reidentificación auditada.

## Acceso

Requiere sesión interna con rol `GERENCIA` o `ANALISTA_RENTAS`. Consultar pide `REPORTES:CONSULTAR` y exportar,
`REPORTES:EXPORTAR`. Las operaciones `POST` y `DELETE` piden el token CSRF de `/api/auth/interno/csrf`.

| Ruta | Operación |
| --- | --- |
| `GET /api/reporteria/catalogo` | Fecha de corte, primer mes con datos, umbral de PRICOS, zonas, tributos, segmentos, usos y canales. |
| `GET /api/reporteria/datos?desde=2026-01&hasta=2026-11` | Agregados del periodo. Filtros opcionales: `zona`, `tributo`, `segmento`, `uso`, `contribuyente`. `destacado` devuelve además la fila y el puesto de ese contribuyente. |
| `GET`, `POST /api/reporteria/vistas`, `DELETE /api/reporteria/vistas/{id}` | Vistas guardadas del usuario. Guardar con un nombre que ya existe actualiza esa vista. |
| `POST /api/reporteria/exportar?formato=EXCEL` o `PDF` | Archivo de la vista en pantalla. Cada exportación queda en la auditoría. |
| `GET /api/reporteria/asistente/estado` | Disponibilidad del servicio de lenguaje. |
| `POST /api/reporteria/asistente/preguntas` | Respuesta del asistente: `{ pregunta, desde, hasta, zona?, tributo?, segmento? }`. Sin servicio devuelve `503 IA_NO_DISPONIBLE`. |

## Datos que necesita el ETL

- **Fecha y canal de pago por cuota.** `analitica.hecho_cobranza` agrega `fecha_pago` y `canal_pago`, que entrega
  `samnet.ps_etl_cobranza`. El ETL debe cargarlos. Si llegan vacíos, las cifras de cobranza no cambian, pero lo pagado
  se atribuye al mes de vencimiento y esas cuotas no cuentan como pagadas a tiempo.
- **Meta.** Se usa el indicador `PORC_RECAUDACION_EMISION` del último mes del periodo, con el alcance de los filtros.
  Sin meta registrada, el indicador lo informa y no se calcula el avance.
- **PRICOS.** Contribuyentes con emisión anual desde el parámetro `UMBRAL_PRICO`, en el año de la fecha de corte.
- **Segmentos.** Solo los publicados (HU031). Quien no tiene uno aparece como `Sin segmento`.

## Asistente IA

El servicio de lenguaje es externo al backend y usa una API compatible con OpenAI (`GET /salud` y
`POST /v1/chat/completions`). Se configura con dos variables, que no se suben al repositorio:

| Variable | Valor |
| --- | --- |
| `LLM_URL` | Dirección base del servicio. |
| `LLM_TOKEN` | Credencial del servicio. |

Se pueden definir como variables de entorno o, en el perfil `local`, en `back/.asistente-local.properties`, a partir de
`.asistente-local.properties.example`. Sin `LLM_URL`, o con el servicio apagado, el panel muestra «Servicio de IA no
disponible» y el resto de la plataforma funciona con normalidad.

Cómo conectarse al servicio de la máquina con GPU por Tailscale: [ASISTENTE_IA_TAILSCALE.md](ASISTENTE_IA_TAILSCALE.md).

El modelo no escribe SQL ni accede a la base de datos. Propone una consulta en JSON que se valida contra el catálogo y se
resuelve con los mismos agregados de las pantallas. Solo recibe la pregunta, los filtros, el catálogo y las filas del
resultado. Cada cifra de su respuesta se compara con esos datos: si inventa una, tiene un reintento y después se muestra
el resultado de la consulta.
