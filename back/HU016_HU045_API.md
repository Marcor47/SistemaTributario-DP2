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

El envío real se activa con `tributario.correo.modo=smtp` (`CORREO_MODO=smtp`) y las variables
`MAIL_*` / `CORREO_*`: Gmail con contraseña de aplicación u otro proveedor SMTP. En `registro`, que es
el valor por defecto, `CorreoRegistroLocalGateway` registra los mensajes sin enviarlos. Las alertas de
mora (HU012) usan el mismo gateway: en modo `smtp` también se envían por correo real.
La respuesta distingue `enviados` de `registrados`, suma `desactivados` y expone `sinServidorCorreo`,
que es `false` cuando el modo es `smtp`. El historial guarda como `REGISTRADO` los avisos del modo
local; esos avisos no bloquean el envío posterior con el proveedor real (migraciones V47 y V54). Los
envíos fallidos quedan `FALLIDO` y se reintentan en el siguiente procesamiento; el motivo se registra
en el log sin guardar el cuerpo del correo.

Configuración mínima para Gmail:

```env
CORREO_MODO=smtp
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=cuenta@gmail.com
MAIL_PASSWORD=contraseña-de-aplicación
MAIL_AUTH=true
MAIL_STARTTLS=true
MAIL_SSL=false
CORREO_REMITENTE=cuenta@gmail.com
CORREO_NOMBRE_REMITENTE=Municipalidad Distrital de San Miguel
PORTAL_URL=https://portal.munisanmiguel.gob.pe
```

La contraseña de aplicación se genera en la cuenta de Google con verificación en dos pasos. No debe
versionarse: se configura por variable de entorno o en el `.env` del despliegue.

La función SAMNET de esta versión usa la base sintética; el contrato debe adaptarse cuando se
integre la fuente municipal. La deduplicación de envío externo ante una caída entre el envío y
la confirmación requerirá soporte de idempotencia o conciliación del proveedor real.

## HU045: restricciones del catálogo de reportes

Los endpoints siguientes requieren el permiso `REPORTES:CONFIGURAR` (el rol Administrador lo recibe
por la migración V56) y CSRF para cambios:

- `GET /api/admin/restricciones-reportes`: lista las reglas.
- `GET /api/admin/restricciones-reportes/opciones`: zonas, segmentos publicados, roles con
  `REPORTES:CONSULTAR`, reportes y dimensiones admitidas. Los demás roles no pueden consultar
  reportes y no aparecen en la configuración.
- `POST /api/admin/restricciones-reportes`: crea una denegación.
- `PUT /api/admin/restricciones-reportes`: reemplaza de forma transaccional las reglas de los roles
  incluidas en `{ "roles": { "GERENCIA": [ ... ] } }`; un rol con lista vacía queda sin restricciones.
- `DELETE /api/admin/restricciones-reportes/{id}`: elimina una regla.

La configuración es una **denylist**: `permitido=false` oculta el reporte, la zona o el segmento; la
ausencia de regla permite el acceso. Las dimensiones son `REPORTE`, `ZONA` y `SEGMENTO`; no hay
restricciones por columnas. Ejemplo:

```json
{ "rol": "GERENCIA", "tipoReporte": "MOROSOS", "dimension": "SEGMENTO", "valor": "Riesgo alto" }
```

Para ocultar el reporte completo se usa `dimension: "REPORTE"` sin `valor`. Las zonas se validan
contra SAMNET y los segmentos contra la **última segmentación publicada**
(`analitica.segmento` + `asignacion_segmento`); ambos se guardan con su nombre canónico. Si un
contribuyente no tiene segmento publicado, se excluye cuando existe una regla de segmento, para no
revelar datos que no se pueden clasificar. Una denegación prevalece sobre los permisos de los otros
roles del usuario. El rol Administrador no se puede restringir desde esta configuración.

La generación y exportación del catálogo aplican estas reglas. El PRICOS especializado bloquea la
consulta si hay una restricción de zona o segmento que no puede aplicar antes de reidentificar el
ranking, en vez de devolver datos sin filtrar. La plataforma `/api/reporteria` aplica las
restricciones antes de calcular cifras, rankings y respuestas del asistente; su catálogo expone
`zonasPermitidas`, `segmentosPermitidos` y `reportesPermitidos` sin alterar las listas completas que
alinean los arreglos de datos. La caché del asistente incluye una huella de las reglas del usuario
para no reutilizar una respuesta entre usuarios con distinto acceso.

Para usuarios con restricciones, la exportación exige `consulta: { desde, hasta, zona?, tributo?, segmento? }`
y reconstruye indicadores y ranking desde los datos autorizados del servidor. Se omiten imágenes,
comparaciones y cifras aportadas por el navegador en ese caso. Los usuarios sin restricciones
conservan la exportación visual completa. La pantalla de administración es
`frontweb/src/pages/ConfiguracionAccesoReportes.jsx`.
