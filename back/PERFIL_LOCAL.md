# Datos del perfil local

Al iniciar con el perfil `local` (`mvnw.cmd spring-boot:run`), Flyway aplica tres carpetas:

| Carpeta | Contenido |
| --- | --- |
| `db/migration` | Esquemas, procedimientos `ps_*` de SAMNET y migraciones versionadas. |
| `db/datos-prueba` | Contribuyentes de referencia (los de los tests), personal municipal y la carga del ETL al 19/10/2026. |
| `db/datos-padron` | Padrón de San Miguel con volumen de producción, personal adicional y formularios oficiales. |

El perfil normal (sin `local`) no incorpora `db/datos-prueba` ni `db/datos-padron`.

## Padrón de San Miguel

`db/datos-padron/R__zz_padron_10_padron_san_miguel.sql` genera, de forma reproducible (sin `random()`), miles de
contribuyentes con sus predios, cuotas de Impuesto Predial y arbitrios de 2022 a 2026, pagos, cobranza, trámites en
todos sus estados, buzón, avisos y las cargas del ETL que alimentan la plataforma analítica.

- **Volumen:** variable `VOLUMEN_PADRON` (5200 por defecto; `spring.flyway.placeholders.volumen_padron`).
- **Cuándo corre:** la primera vez y cada vez que cambia el archivo; no en cada arranque. En una base vacía el primer
  arranque tarda unos 5 minutos.
- **Repetible:** al volver a ejecutarse rehace solo lo suyo (contribuyentes `2NNNNN`). Los reclamos y sus derivaciones
  son inalterables y se conservan.

Las credenciales de cada rol están en el informe de entrega del curso; no se publican en el repositorio ni en las
pantallas.

## Servicios externos en local

| Servicio | Implementación local | Configuración |
| --- | --- | --- |
| Pasarela de pagos | `NiubizLocalGateway` (no mueve dinero) | `NIUBIZ_MODE=local`; por defecto `sandbox` ([NIUBIZ_SANDBOX.md](NIUBIZ_SANDBOX.md)) |
| Firma digital | `FirmadorServiciosEnLineaGateway` | Se usa mientras no se integre el firmador digital del Estado |
| Correo | `CorreoRegistroLocalGateway` (registra el aviso como `REGISTRADO`) | Se usa mientras no se configure un servidor de correo |

## Segmentación inicial

`SegmentacionInicialLocal` calcula al iniciar la segmentación K-means de la última carga y, si queda en borrador, la
publica como aprobada por `gerencia` (`tributario.segmentacion.aprobador-inicial`). Se apaga con
`tributario.segmentacion.publicar-al-iniciar: false`.
