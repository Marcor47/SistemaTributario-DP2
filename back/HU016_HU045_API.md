# HU016 y HU045: recordatorios y restricciones de reportes

## HU016: recordatorios antes del vencimiento

El proceso consulta obligaciones pendientes en SAMNET desde mañana hasta el número de días de
anticipación de `seguridad.parametro_configuracion.DIAS_RECORDATORIO` (1 a 10; valor inicial: 5).
Solo considera contribuyentes con documento y correo registrado. Guarda el resultado en
`operativo.aviso_correo` y no vuelve a enviar un recordatorio que figure como enviado para la
misma obligación. Los intentos fallidos pueden procesarse nuevamente.

La tarea corre diariamente a las 08:00, hora de Lima. La propiedad
`tributario.recordatorios.programacion` permite cambiar el cron o desactivarlo con `-`.
Un usuario interno con `ALERTAS:EVALUAR` o `ETL:GESTIONAR` también puede ejecutar
`POST /api/recordatorios-vencimiento/procesar` con CSRF y consultar el resumen.

El contribuyente consulta sus datos en la app móvil (P12) con `GET /api/portal/preferencias-avisos`.
La respuesta incluye nombre, tipo y número de documento enmascarado, correo, `recordatorioVencimiento`
y `diasAnticipacion`. `PUT /api/portal/preferencias-avisos` con `{ "recordatorioVencimiento": false }`
guarda la preferencia y devuelve el mismo objeto. Sin correo registrado, la activación se rechaza con
`CORREO_NO_REGISTRADO`; la ausencia de fila equivale a recordatorio activo (migración V55). El procesamiento
omite a quienes lo desactivaron y los cuenta en `desactivados`. La consulta de estado de una obligación sin
aviso devuelve `DESACTIVADO` cuando el contribuyente apagó el recordatorio.

**Mientras no se configure un servidor de correo**, `CorreoRegistroLocalGateway` registra los mensajes y no los
entrega por Internet. Es necesario integrar un proveedor real antes de prometer envíos reales.
La respuesta distingue `enviados` de `registrados`, suma `desactivados` y expone `sinServidorCorreo`. El
historial guarda esos avisos como `REGISTRADO`; no bloquean el envío posterior con un proveedor real
(migraciones V47 y V54).
La función SAMNET de esta versión usa la base sintética; el contrato debe adaptarse cuando se
integre la fuente municipal. La deduplicación de envío externo ante una caída entre el envío y
la confirmación requerirá soporte de idempotencia o conciliación del proveedor real.

## HU045: restricciones del catálogo de reportes

Los endpoints siguientes requieren sesión de administrador y CSRF para cambios:

- `GET /api/admin/restricciones-reportes`: lista las reglas.
- `GET /api/admin/restricciones-reportes/opciones`: zonas, roles, reportes y dimensiones admitidas.
- `POST /api/admin/restricciones-reportes`: crea una denegación de reporte completo o de zona.
- `DELETE /api/admin/restricciones-reportes/{id}`: elimina una regla.

Ejemplo: `{ "rol": "GERENCIA", "tipoReporte": "MOROSOS", "dimension": "ZONA", "valor": "Maranga" }`.
Para restringir el reporte completo, usa `dimension: "REPORTE"` sin `valor`. La generación y
exportación del catálogo ya consultan estas reglas. El PRICOS especializado bloquea la consulta
si hay una restricción de zona que no puede aplicar, en vez de devolver datos sin filtrar.

Las zonas se validan contra SAMNET y se guardan con su nombre canónico. Una denegación explícita
prevalece sobre los permisos de los otros roles del mismo usuario. La configuración nueva por
segmento se rechaza: el catálogo aún no dispone de una asociación fiable por contribuyente.

La plataforma `/api/reporteria` aplica las restricciones antes de calcular cifras, rankings y
respuestas del asistente. Excluye el contribuyente completo si alguna de sus cuotas exigibles
pertenece a una zona restringida de un reporte al que corresponde (morosos, PRICOS o buenos
pagadores); un filtro de pantalla no puede reclasificarlo para eludir la regla. La carga compartida
en memoria conserva los datos originales, y las reglas se consultan de nuevo en cada petición.

Para usuarios con restricciones, la exportación exige `consulta: { desde, hasta, zona?, tributo?, segmento? }`
y reconstruye indicadores y ranking desde los datos autorizados del servidor. Se omiten imágenes,
comparaciones y cifras aportadas por el navegador en ese caso. Los usuarios sin restricciones
conservan la exportación visual completa. La pantalla de administración corresponde al frontend del equipo.
