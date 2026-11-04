# Configurar Niubiz sandbox

El backend contiene la integración de seguridad, sesión de checkout y autorización. La app móvil
presenta el formulario oficial, envía únicamente el token de transacción y muestra el comprobante
cuando el pago está registrado. El sandbox es el modo por defecto; no hay sustitución automática
por el simulador. No se admiten pagos productivos. Si faltan las credenciales o las URL HTTPS,
el backend arranca y permite usar los otros módulos; intentar un pago devuelve
`503 NIUBIZ_NO_CONFIGURADO`. La configuración se valida antes de contactar a Niubiz.

En este equipo las credenciales públicas de pruebas están guardadas en
`back/.niubiz-sandbox-local.properties`. El perfil `local` importa ese archivo al iniciar
desde `back/`. Git lo ignora; las variables de entorno tienen prioridad. No contiene
credenciales productivas ni se envía al móvil. Reinicia el backend para aplicar los cambios.

Para una demostración, la guía oficial de Niubiz publica credenciales, códigos de comercio y
tarjetas en la sección **Datos de prueba**. El comercio público `456879852` corresponde a soles.
No se requieren credenciales productivas para esta prueba. Si el grupo tiene un comercio sandbox
propio, usa sus credenciales correspondientes.

Para otro equipo, copia `.niubiz-sandbox-local.properties.example` como
`.niubiz-sandbox-local.properties` y completa usuario y contraseña con los datos sandbox de
la guía oficial. También puedes configurar las variables en la terminal de inicio:

```powershell
$env:NIUBIZ_MODE = 'sandbox'
$env:NIUBIZ_USERNAME = '<usuario sandbox>'
$env:NIUBIZ_PASSWORD = '<contraseña sandbox>'
$env:NIUBIZ_MERCHANT_ID = '<código de comercio de 9 dígitos>'
$env:NIUBIZ_CALLBACK_URL = 'https://<dominio-de-pruebas>/niubiz/retorno'
$env:NIUBIZ_TIMEOUT_URL = 'https://<dominio-de-pruebas>/niubiz/timeout'
.\mvnw.cmd spring-boot:run
```

En la app con tarjeta/canal `web`, `complete` entrega el token al WebView y este lo lleva a la
API con la sesión del ciudadano. Para desarrollo se usa el origen virtual `https://localhost`
del ejemplo: no necesita un servidor HTTPS local ni un túnel porque no se hace el POST de
retorno. La app intercepta la URL de timeout. Estas direcciones no son endpoints publicados.
Otros métodos de pago o un flujo que navegue al retorno sí requieren páginas HTTPS reales
del despliegue. Recibir un token o visitar una URL nunca se considera prueba de pago:
el backend comprueba la autorización y el importe antes de registrar el pago.

El móvil debe apuntar al mismo backend mediante `EXPO_PUBLIC_API_URL`. El contribuyente debe
tener correo registrado. Usa únicamente las tarjetas y el comercio de prueba habilitados para
la integración del grupo. No incluyas credenciales Niubiz en variables `EXPO_PUBLIC_*`.

Prueba manual:

1. Inicia el backend y abre la app móvil. La semilla local crea `099024` / `Portal099024!`,
   una cuenta ficticia con una cuota de S/ 1.00 y correo de prueba. No restablece una cuota
   pagada al reiniciar. Los demás usuarios ficticios con correo y deuda también sirven.
2. Abre Estado de cuenta y pagos, luego Pagar obligaciones. El importe debe venir del backend.
3. Pulsa **Pagar** en el resumen. El SDK inicial se precarga al entrar a la sesión y el formulario
   se prepara al recibir una orden válida. Se muestra directamente, sin un segundo botón para abrirlo.
   Usa una tarjeta de prueba.
4. Tras autorización y registro, comprueba el número de comprobante. Al volver a la cuenta se consulta
   nuevamente SAMNET y se muestran los saldos actualizados.
5. Si cancelas el formulario o Niubiz no autoriza, las obligaciones siguen pendientes.

La guía oficial publica, por ejemplo, Visa `4474118355632240`, vencimiento `03/2028`, CVV
`111` para un resultado autorizado sin cuotas. Usa nombres y correo ficticios para la cuenta
de demostración. Estos datos sirven únicamente en el sandbox. La primera carga del SDK
externo puede tardar; espera el formulario antes de volver a tocar el botón.

La carga lenta no se trata como pago rechazado ni como pasarela no disponible. Se conserva
el formulario y se ofrece **Reintentar carga**, sin enviar una autorización automática.
En web el HTML propio carga únicamente el SDK permitido y no añade restricciones `sandbox`
a los marcos del proveedor; estas restricciones heredadas convertían su origen en `null`.
Un token inválido sigue bloqueado y el éxito depende de la comprobación del backend.

Diagnóstico de la descarga del 04/11/2026: `visanet.min.js` tiene 830087 bytes. Las mediciones
directas desde este equipo recibieron solo 229127–294663 bytes en 35 segundos (6–8 KB/s),
y la prueba con compresión también excedió 30 segundos. Los recursos del SDK siguieron
pendientes tras 180 segundos en pruebas reales de Chrome y Edge. Se comprobó también la
ruta oficial `/v2/`, sin resolver la lentitud; se conserva `/env/sandbox/`. Esta limitación
de descarga no se puede presentar como resuelta por cambiar un indicador o un temporizador.
La prueba completa posterior en la app real de Expo abrió el formulario de tarjeta en
266.5 segundos desde **Pagar**, conservando la vista abierta mientras se descargaba el SDK.
No usó respuestas simuladas, cachés locales del SDK ni cambios de HTTP/2 y no efectuó el pago.
El origen observado dentro del formulario fue `https://static-content-qas.vnforapps.com`.
El cierre por tiempo está corregido; la velocidad de esa descarga externa depende del proveedor y la red.

Para evitar repetir esa descarga, `PagosProvider` conserva un único visor durante la sesión del
ciudadano, fuera de las pantallas de navegación. Al entrar se descarga el SDK inicial y se adelantan
las conexiones a los dominios oficiales. El formulario completo empieza a prepararse cuando el
backend entrega una orden válida; no se generan compras ficticias para calentarlo. El WebView usa
la caché normal del sistema, respetando las cabeceras del proveedor. No se distribuyen copias del SDK.

Cerrar el visor, volver al inicio y retomar la misma operación mantiene su documento en memoria.
Después de un pago confirmado cuyo token entregó este visor, el siguiente pago puede reutilizar
el motor con su propia sesión, número de compra e importe. Los mensajes incluyen el identificador
de operación y no se reutilizan tokens de autorización. Una operación diferente que sustituya a una
pendiente, una renovación de sesión o un pago confirmado fuera del visor requieren una instancia
limpia para aislar respuestas anteriores. Cerrar sesión o cambiar de ciudadano destruye el visor.
**Reintentar carga** también crea una instancia nueva; no conviene pulsarlo durante una descarga
que sigue avanzando. La primera carga, una recarga de la app o la recuperación tras falta de memoria
siguen dependiendo de la descarga externa.

Las pruebas de interfaz con respuestas controladas verificaron a 390 y 1220 píxeles dos compras
con sesiones e importes diferentes usando una sola descarga del SDK, navegación sin recrear los
marcos, rechazo de tokens repetidos y eliminación del visor al salir. Esas pruebas no midieron
la velocidad del servicio de Niubiz ni efectuaron pagos reales.

Medición posterior con esta mejora, Chrome sin modificaciones de red, Expo y backend reales:
la primera apertura tardó 288.8 segundos. Después de cerrar el visor, volver al inicio y retomar
la misma operación, el formulario apareció en 0.054 segundos, sin peticiones adicionales de scripts
de Niubiz. Se comprobó que el marco y su documento seguían siendo los mismos. Es una medición
de reapertura en este equipo, no una garantía de tiempo en otros dispositivos ni en una carga inicial.
No se ingresaron datos de tarjeta ni se efectuó un pago; `099024` conservó S/ 1.00 de saldo.

Comparación adicional del 04/11/2026, desde el mismo equipo y sin abrir la app: una petición
HTTP Range `0-131071` a `/env/sandbox/js/visanet.min.js` descargó 131072 bytes en 14.165 segundos
(9253 bytes/s). La misma cantidad de `/v2/js/visanet.min.js` en el host público de producción
se descargó en 0.155 segundos (845549 bytes/s). Ambos respondieron `206`. El sandbox respondió
con `Server: nginx`; producción mostró AmazonS3 y CloudFront, con punto de presencia `LIM50-P4`.
Esto apunta a la distribución del entorno QAS o la ruta de red hacia él. No permite atribuir
la causa exclusivamente al proveedor, ni medir todo el checkout de producción. No se usaron
credenciales productivas ni se cambió la integración. Adelantar una descarga puede ocultar
parte de la espera, pero no acelera esa transferencia; corregirla requiere revisar conectividad
o que Niubiz revise la entrega de sus recursos sandbox. Cambiar al SDK productivo de forma
aislada no es una solución compatible con una integración que sigue usando sesiones sandbox.

Revisión adicional de la precarga completa sin orden: `visanet.html` construye la URL de
`visanet.min.js` con `Math.random()` y el framework hace lo mismo para varios scripts secundarios.
Precargar otra instancia de esa página no asegura aprovechar la caché al abrir el checkout real:
sus URL serían diferentes. La prueba aislada se detuvo al confirmar este comportamiento en el
HTML oficial. La ruta alternativa `static-content-qas-nlb.vnforapps.com`, referida por el indicador
`static=true` del SDK, no entregó una respuesta HTTP utilizable desde este equipo. No se integró
esa ruta ni se modificó el SDK. Una posibilidad adicional sería generar una cotización real antes
de la pulsación de pago, por ejemplo al entrar al estado de cuenta, y configurar anticipadamente
el visor con esa orden válida. Esto crea una orden pendiente aunque el ciudadano solo consulte
su cuenta; adelanta la espera y la caducidad de la sesión, pero no acelera la transferencia externa.
La app actual conserva la creación de la orden al pulsar **Pagar obligaciones**.

En móvil, el panel se superpone al resumen usando `StyleSheet.absoluteFill`, disponible en
React Native 0.88. `absoluteFillObject` ya no existe en esa versión: usarlo dejaba la cabecera
debajo del resumen y el WebView sin altura. La cabecera gestiona el margen superior seguro
y el contenedor reserva únicamente el inferior, sin sumar dos veces el margen superior.

Verificación del 04/11/2026 en la BD local: la cuenta ficticia `099023` completó el formulario,
obtuvo un token real y el backend recibió autorización del sandbox por S/ 1.00. La operación
18 quedó `CONFIRMADA`, comprobante `WEB-18`. La prueba usó Chrome con HTTP/1.1 y los dos JS
oficiales descargados sin modificar como caché temporal; las APIs de sesión, validación del BIN,
tokenización y autorización fueron las reales del sandbox, sin respuestas HTTP simuladas.
No sustituye la comprobación visual en un teléfono: recarga Expo y prueba allí el WebView.
En este equipo `099023` ya no tiene esa deuda y `099024` también pagó su cuota inicial
(`WEB-20`). A petición del usuario se repone S/ 1.00 con una cuota adicional de 2027-T1 para
`099024` / `Portal099024!`, conservando el pago anterior. Una BD nueva recibe las cuotas
iniciales pendientes; las semillas no reabren pagos realizados.

Si hay un timeout después de enviar el token, consulta la misma operación y solicita conciliación
con Niubiz antes de iniciar otro pago. La recuperación de red no reenvía la autorización.

Para desarrollo sin Niubiz se permite explícitamente `$env:NIUBIZ_MODE = 'simulado'`; la app lo
identifica como Simulador de contingencia. Los tests automáticos usan esa configuración o respuestas
HTTP de prueba y no realizan cargos externos.

Referencia: [Botón de Pago Web de Niubiz](https://desarrolladores.niubiz.com.pe/v1.1/docs/bot%C3%B3n-de-pago-1).
