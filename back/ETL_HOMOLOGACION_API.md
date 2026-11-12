# ETL por capas y bandeja de homologación (RF-038 a RF-041, RF-053)

El ETL de Rentas pasa los datos de SAMNET al modelo analítico en tres capas, todas en la misma base PostgreSQL:

```
SAMNET (samnet.ps_etl_*)
   │  extracción: contribuyentes, predios (una fila por titular), cuotas y pagos a la fecha de corte
   ▼
bronce.*   copia cruda de cada extracción, por lote; solo se agrega (bronce.extraccion es el manifiesto)
   │  reglas R1 a R5 · normalización · homologación N0 a N3
   ▼
plata.*    registros validados del último lote publicado, con su entidad maestra; plata.cuarentena guarda lo
   │       rechazado de todos los lotes con su regla y el registro original
   ▼
oro        el modelo estrella de siempre: analitica.lote_etl, resultado_regla, dim_contribuyente, dim_predio,
           hecho_cobranza, hecho_pago, y maestros.entidad_maestra / correspondencia_origen
```

**El oro no cambia de forma.** Las pantallas (reportes, dashboard, plataforma analítica, indicadores, alertas)
leen las mismas tablas y columnas, con el mismo seudónimo (HMAC-SHA-256 del código de SAMNET). La carga de prueba
publica los mismos 1218 hechos que la carga anterior, fila por fila (lo comprueba `EtlPipelineTest`).

| Pieza | Dónde |
| --- | --- |
| Tablas de bronce y plata | `db/migration/V39__etl_capas_bronce_plata.sql` |
| Lógica del ETL (`etl.ejecutar_lote`) | `db/migration/R__etl_pipeline.sql` |
| Orquestación, a demanda y programada | `etl/EtlService.java`, `etl/EtlController.java` |
| Bandeja de homologación | `etl/HomologacionCasoService.java`, `etl/HomologacionCasoController.java` |
| Pruebas | `etl/EtlPipelineTest.java` |

`bronce` y `plata` tienen DNI, RUC y nombres en claro: igual que `maestros`, no tienen acceso público (RNF-018). El
oro solo tiene seudónimos.

## Un lote

`etl.ejecutar_lote(fecha_corte, tipo, clave_contribuyente, clave_predio, fecha_inicio, max_rechazo, umbral_revision)`
es una sola transacción: o se publica todo o no queda nada a medias. Solo corre un lote a la vez.

1. **Bronce.** Llama a `ps_etl_contribuyentes()`, `ps_etl_predios()`, `ps_etl_cobranza(corte)` y `ps_etl_pagos(corte)`
   y guarda lo que devuelven, sin validar. `ps_etl_cobranza` ahora devuelve lo pagado y el insoluto **a la fecha de
   corte** (sin los pagos posteriores): con la fecha de hoy el resultado es el de siempre; con una fecha pasada
   reproduce la cuenta corriente de ese día.
2. **Reglas** (`analitica.regla_calidad`). Un registro con error va a `plata.cuarentena` y no pasa al oro:

   | Regla | Qué rechaza |
   | --- | --- |
   | R1 | datos obligatorios vacíos o fuera de dominio: código, documento, nombre, tipo de persona; zona y tipo de predio; tributo, cuota 1 a 12, montos y canal |
   | R2 | documento mal formado: DNI de 8 dígitos, RUC de 11, CE u otro de 9 a 12 alfanuméricos (después de quitar espacios, puntos y guiones) |
   | R3 | la cuota, el pago o la titularidad apuntan a un contribuyente o predio rechazado o inexistente. **Un predio sin titular no es error:** se publica marcado para revisión (su entidad maestra queda `PENDIENTE_REVISION`) |
   | R4 | claves repetidas en el lote (se rechazan todas las copias) |
   | R5 | fecha de corte posterior a la ejecución (rechaza el lote); cuota de un año o pago posteriores al corte |

   Cada regla queda en `analitica.resultado_regla` con `CUMPLE`, `OBSERVA` (hubo rechazos o marcas) o `FALLA`. **El lote
   se rechaza** (y no toca plata ni oro) si una regla deja fuera más de `max-rechazo-porcentaje` (10 %) de los
   registros que evalúa, o si la fecha de corte es futura. Así un registro malo no detiene los reportes del día, pero
   una extracción rota sí se detiene.
3. **Homologación** (cascada determinista, RF-039):

   | Nivel | Contribuyente | Predio | Resultado |
   | --- | --- | --- | --- |
   | N0 | la clave de SAMNET ya tiene correspondencia | igual | la misma entidad maestra |
   | N1 | misma clave de identidad: tipo y número de documento normalizados; el **RUC 10** de una persona natural lleva su DNI | mismo código catastral | se une sola, decisión `AUTOMATICA` |
   | N2 | nombre parecido (similitud ≥ 0,6) y puntaje ≥ `umbral-revision` (0,65) | — | **caso para el Responsable de datos** |
   | N3 | sin coincidencias | sin coincidencias | entidad nueva, decisión `AUTOMATICA` |

   Puntaje N2 = 0,5 · similitud del nombre (palabras en cualquier orden, sin tildes ni puntuación) + 0,3 · documento
   (1 si difiere en una cifra, 0,75 si en dos) + 0,2 · similitud del domicilio. Se compara solo con entidades activas
   del mismo tipo de persona. Un registro en N2 **no se une solo**: se publica con una entidad provisional
   (`PENDIENTE_REVISION`, con su propio seudónimo) y queda un caso `PENDIENTE` en `analitica.caso_homologacion`, que
   guarda solo puntajes y distancias, sin nombres ni documentos.
4. **Plata.** Reemplaza el estado vigente con lo aceptado.
5. **Oro.** Paso directo: actualiza las dimensiones e inserta los hechos de cobranza y de pago del lote. El Impuesto
   Predial no tiene predio en SAMNET: se publica con el primer predio del contribuyente, como hacía la carga anterior.

## Bandeja de homologación (revisión humana)

Solo API; la pantalla queda pendiente. Roles `RESPONSABLE_DATOS` o `ADMINISTRADOR`, con sesión interna y token CSRF en
los POST, igual que la auditoría de HU040.

| Ruta | Operación |
| --- | --- |
| `GET /api/homologacion/casos?estado=PENDIENTE` | Casos (`PENDIENTE` por defecto; `APROBADO`, `RECHAZADO`, `REASIGNADO` o `TODOS`). Sin datos personales: candidato, entidad provisional, puntaje y `atributosComparados`. |
| `GET /api/homologacion/casos/{id}` | Detalle para decidir: los registros de SAMNET de cada lado, en claro. Cada consulta queda en la auditoría como `REIDENTIFICAR`. |
| `POST /api/homologacion/casos/{id}/aprobacion` `{"motivo": "..."}` | Es la misma entidad que el candidato: las correspondencias de la provisional pasan al candidato y la provisional queda `FUSIONADA`. |
| `POST /api/homologacion/casos/{id}/rechazo` `{"motivo": "..."}` | Es otra entidad: la provisional queda `ACTIVA` con su seudónimo. |
| `POST /api/homologacion/casos/{id}/reasignacion` `{"idMaestro": 12, "motivo": "..."}` | Es la misma que otra entidad (no el candidato), del mismo tipo y no fusionada. |
| `GET /api/homologacion/maestros/{id}/decisiones` | (HU040, ya existía) auditoría de decisiones de una entidad, ahora también con las automáticas del ETL. |

Cada resolución registra una `DecisionHomologacion` `HUMANA` inalterable, con el usuario y el motivo (RF-053). Un caso
resuelto no se vuelve a resolver (`422 CASO_RESUELTO`); dos personas a la vez no lo resuelven dos veces (el caso se
bloquea). **Los maestros cambian en el acto; el modelo estrella, desde el siguiente lote**: los lotes publicados no se
reescriben. En el siguiente lote, el código del duplicado entra por N0 y sus cuotas salen con el seudónimo de la
entidad a la que se unió.

## Ejecutar el ETL

| Ruta | Operación (permiso `ETL:GESTIONAR`, rol Responsable de datos) |
| --- | --- |
| `POST /api/etl/lotes` `{"fechaCorte": "2026-10-19"}` | Ejecuta un lote a demanda (sin fecha: hoy). Responde el lote con el resultado de cada regla. |
| `GET /api/etl/lotes/{id}` | Estado, volúmenes y reglas de un lote. |
| `GET /api/etl/lotes/{id}/cuarentena` | Registros rechazados: entidad, código de SAMNET, regla y motivo. |

Después de publicar, el backend calcula el índice de morosidad y las alertas de la carga nueva, igual que al iniciar.

Configuración (`application.yml`, `tributario.etl`):

| Propiedad | Variable | Por defecto |
| --- | --- | --- |
| `clave-seudonimo-contribuyente`, `clave-seudonimo-predio` | `ETL_CLAVE_SEUDONIMO_CONTRIBUYENTE`, `ETL_CLAVE_SEUDONIMO_PREDIO` | vacías: **sin ellas el ETL no corre**. En `local`, las claves públicas de las semillas. En producción son secretas y no se cambian una vez cargados datos. |
| `max-rechazo-porcentaje` | `ETL_MAX_RECHAZO` | 10 |
| `umbral-revision` | `ETL_UMBRAL_REVISION` | 0.65 |
| `programacion` | `ETL_PROGRAMACION` | `-` (apagada). Con un cron de Spring, p. ej. `0 0 2 * * *`, corre cada día a las 2:00 (Lima) con el corte del día anterior. |

## Datos de desarrollo

- `datos-prueba/R__zz_carga_etl_prueba.sql` ejecuta el ETL real con corte 2026-10-19 (va después de
  `R__samnet_procedimientos`; por eso empieza con `zz`). Reemplaza a `R__datos_prueba_10_etl_cobranza.sql`, que
  escribía el modelo estrella a mano y ahora está vacío para que Flyway siga reconociéndolo en las bases existentes.
- `datos-prueba/R__datos_prueba_09_duplicados_homologacion.sql` agrega a SAMNET casos para la cascada, sin cuotas ni
  pagos (no cambian montos ni reportes): `903218` (RUC 10 de `033218`, N1), `914502` (DNI con cifras invertidas de
  `014502`, N2, para aprobar), `921390` (razón social y RUC de `021390` mal digitados, N2, para aprobar), `933219`
  (homónimo de `033218` en el mismo domicilio, N2, para rechazar), `990001` (DNI de 7 dígitos, cuarentena R2) y el
  predio `914502-01` (mismo código catastral que `014502-01`, N1). El predio `099001-01`, que ya estaba sin titular,
  sale marcado para revisión.
- `datos-prueba/R__datos_prueba_11_responsable_datos.sql`: usuario `datos` / `datos123` con el rol Responsable de datos.
- En `local`, `datos-demo/R__zz_demo_04_historia_san_miguel.sql` ya no escribe dimensiones, maestros ni hechos: ejecuta
  el ETL una vez por cierre de mes desde enero de 2019 y otra el 2026-11-04 (95 lotes, unos 35 s la primera vez). Las cargas que leen las pantallas
  (2026-10-31 y 2026-11-04) son idénticas a las de antes; las mensuales anteriores traen además las cuotas ya pagadas
  de años previos, que la semilla omitía y que ningún indicador usa (filtran por año).
- Las cuentas de demostración de Niubiz `099023` y `099024` tienen Impuesto Predial sin predio: la carga anterior las
  omitía en silencio; el ETL las deja en cuarentena (R3), así que aparecen en la cuarentena de cada lote local.

## Por qué en PostgreSQL y no en un lakehouse

Se evaluó un lakehouse (DuckLake: bronce y plata como Parquet en S3, oro en PostgreSQL). Para el piloto, con unos 10
usuarios internos y un volumen de miles de contribuyentes, la misma base alcanza y no agrega servicios que operar. Las
capas están separadas por esquema y el contrato con el oro no depende de dónde vivan bronce y plata: si el volumen
crece (un distrito completo son ~45 mil contribuyentes y ~5 millones de cuotas), bronce y plata se pueden mover a un
lakehouse sin tocar las pantallas.
