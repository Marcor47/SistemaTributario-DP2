# HU022 — API de pago total de obligaciones

## Alcance

El backend toma el contribuyente exclusivamente de la cookie de sesión y cotiza **todas** sus
obligaciones con saldo pendiente. El cliente no elige deudas ni importes. La cotización conserva
por obligación el saldo base, el descuento y el identificador de beneficio reconocido por SAMNET.
Antes de registrar el pago, el servidor vuelve a verificar el conjunto completo, los saldos y
los beneficios. Las órdenes anteriores a esta corrección, sin desglose, no se pueden confirmar.

La regla de pago total y la condición de pago con beneficios proceden de RF-036 del catálogo v11.
No se presume elegibilidad a partir del tipo de persona, empleo ni del mayor porcentaje disponible.

## Rutas y contrato

Todas las rutas exigen una sesión creada por `POST /api/auth/login`. Sin sesión responden HTTP 401.
No se envía código de contribuyente en estas peticiones.

| Operación | Petición | Respuesta |
| --- | --- | --- |
| Crear orden | `POST /api/portal/pagos`, `Content-Type: application/json`, body `{}` | HTTP 201, cabecera `Location: /api/portal/pagos/{id}` |
| Consultar | `GET /api/portal/pagos/{id}`, sin body | HTTP 200 |
| Confirmar | `POST /api/portal/pagos/{id}/confirmacion`; en sandbox body `{ "transactionToken": "..." }` | HTTP 200 |

Por compatibilidad, el body de creación puede ser `{"obligaciones":[1,2,...]}`. Esa lista debe
coincidir exactamente con **todas** las obligaciones pendientes de la cuenta; el servidor siempre
obtiene los IDs y montos desde SAMNET. Se puede omitir el body por completo. Las listas parciales,
duplicadas, ajenas o con obligaciones canceladas se rechazan con `PAGO_DEBE_CUBRIR_TODO`.

Respuesta ilustrativa de creación (los IDs y montos reales dependen de la cuenta):

```json
{
  "idTransaccion": 42,
  "estado": "INICIADA",
  "montoBase": 300.00,
  "montoDescuento": 25.00,
  "montoTotal": 275.00,
  "obligaciones": ["101", "102"],
  "urlPasarela": "/pasarela-simulada/SIM-00000000-0000-0000-0000-000000000000",
  "comprobante": null,
  "dePrueba": true,
  "checkout": null
}
```

Al consultar, `urlPasarela` es `null`. Tras confirmar, `estado` es `CONFIRMADA` y `comprobante`
es `WEB-{idTransaccion}`; la confirmación repetida devuelve la misma transacción sin otro cargo.
El `montoBase` es el saldo antes de beneficios, `montoDescuento` no es dinero cobrado y
`montoTotal` es el importe de la orden de pasarela. La base de datos conserva esos tres importes
y el desglose por obligación. `dePrueba: true` indica un entorno sin cobro de producción
(simulador o sandbox).

Errores de negocio usan `{ "codigo": "...", "mensaje": "...", "detalles": [] }`. Ejemplo:

```json
{
  "codigo": "PAGO_DEBE_CUBRIR_TODO",
  "mensaje": "El pago virtual debe cubrir todas las obligaciones pendientes. Consulte su cuenta.",
  "detalles": []
}
```

| HTTP | Código | Significado |
| --- | --- | --- |
| 401 | `SESION_REQUERIDA` | Falta sesión. |
| 404 | `PAGO_NO_ENCONTRADO` | Orden inexistente o ajena. |
| 404 | `ORDEN_NO_ENCONTRADA` | El simulador ya no conoce la orden, por ejemplo tras reiniciar el backend. |
| 422 | `PAGO_DEBE_CUBRIR_TODO` | Lista opcional no coincide con toda la deuda pendiente. |
| 422 | `SIN_DEUDA` | No hay saldo pendiente; no se crea orden. |
| 422 | `BENEFICIO_INDETERMINADO` | La fuente no asignó un beneficio verificable a cada obligación; acudir a ventanilla. |
| 422 | `SIN_COBRO_VIRTUAL` | El beneficio cubre toda la deuda; no se crea un cobro de cero. Acudir a ventanilla para aplicar el beneficio. |
| 422 | `PAGO_NO_AUTORIZADO` | La pasarela no autorizó la orden. |
| 400 | `TOKEN_NIUBIZ_REQUERIDO` | Falta el token que devuelve el checkout en modo sandbox. |
| 502 | `NIUBIZ_NO_DISPONIBLE` | Niubiz no respondió; conciliar la orden si ocurrió durante la autorización. |
| 409 | `SALDO_CAMBIO` | Cambió la deuda o el beneficio; se requiere nueva cotización. |
| 503 | `SAMNET_NO_DISPONIBLE` | No se pudo consultar la fuente; no se cobra con datos de copia. |

## Flujo para frontend

El componente actual `frontweb/src/components/PagoCuentaPortal.jsx` todavía permite escoger
periodos y calcula el importe en el navegador. Debe adaptarse por el equipo de frontend antes
de usar este contrato: enviar `{}`, mostrar el total de la respuesta del servidor y manejar
`PAGO_DEBE_CUBRIR_TODO`, `BENEFICIO_INDETERMINADO` y `SIN_COBRO_VIRTUAL`.

1. Iniciar sesión. Consultar `GET /api/portal/estado-cuenta` para mostrar la deuda informativa.
2. Crear la orden con `{}` y mostrar `montoBase`, `montoDescuento`, `montoTotal` y `dePrueba`.
3. En modo simulado, abrir `urlPasarela`. En sandbox, presentar el checkout de Niubiz con el objeto `checkout`.
4. En sandbox, enviar el `transactionToken` devuelto por Niubiz a la ruta de confirmación.
   El backend autoriza servidor a servidor; el retorno del navegador por sí solo no confirma el pago.
5. Si hay `SALDO_CAMBIO`, consultar de nuevo la cuenta y crear una orden nueva. Si hay
   `BENEFICIO_INDETERMINADO` o `SIN_COBRO_VIRTUAL`, indicar que acuda a ventanilla.
6. Consultar la orden por ID para recuperar su estado y comprobante.

## Pruebas y datos

Las pruebas de integración usan PostgreSQL temporal y la fecha fija **19/10/2026**. Los datos
ficticios de `db/datos-prueba` incluyen `021390` con varias obligaciones pendientes,
`033218` sin deuda y `058934` con un beneficio vigente sin asignación suficiente, que bloquea
el pago virtual. Las pruebas crean una asignación explícita de beneficio para verificar descuento,
dinero registrado y saldo final. También cubren selección parcial, obligaciones ajenas y pagadas,
cambio de deuda o beneficio, confirmación repetida y concurrente. No se usan tarjetas ni pagos reales.

## Sandbox de Niubiz

Para activar el proveedor, configure `NIUBIZ_MODE=sandbox`, `NIUBIZ_USERNAME`,
`NIUBIZ_PASSWORD`, `NIUBIZ_MERCHANT_ID`, `NIUBIZ_CALLBACK_URL` y `NIUBIZ_TIMEOUT_URL`.
Las dos URLs deben ser HTTPS y accesibles por el checkout. Las credenciales son exclusivas del
backend. El contribuyente debe tener correo registrado; el backend obtiene su identidad de la
sesión y usa la IP de la conexión. El comercio debe confirmar con Niubiz los MDD antifraude
requeridos para su afiliación antes de la homologación.

La creación devuelve `urlPasarela: null` y un objeto `checkout` con `sessionKey`,
`merchantId`, `purchaseNumber`, `checkoutUrl`, `callbackUrl` y `timeoutUrl`.
`checkoutUrl` apunta al script oficial del sandbox. El cliente debe presentar el formulario
responsivo de Niubiz con esos datos y el importe `montoTotal`, recibir `transactionToken`
en la URL de retorno y enviarlo a `/confirmacion` con la misma sesión autenticada. El backend
vuelve a verificar la deuda, autoriza con Niubiz servidor a servidor y solo registra en SAMNET
si la respuesta indica `Authorized`, código `000` o `010` e importe exacto. Nunca se aceptan
datos de tarjeta en esta API. Si expira el token de checkout, se crea una orden nueva.

El frontend móvil de pagos conserva su flujo actual hasta que su responsable conecte el
checkout y envíe el token. En modo sandbox, su confirmación sin token se rechaza con
HTTP 400 `TOKEN_NIUBIZ_REQUERIDO`; no se crea un pago por omitir la pasarela.
Si Niubiz agota el tiempo de autorización, el resultado puede ser incierto: no se reintenta
automáticamente y se debe conciliar la orden con Niubiz antes de repetirla. También se requiere
conciliación si Niubiz autoriza pero la escritura local falla después. Faltan los procedimientos
operativos de conciliación y extorno para producción.

Referencia: [Botón de Pago Web de Niubiz](https://desarrolladores.niubiz.com.pe/docs/bot%C3%B3n-de-pago-1).

## Simulador y reglas pendientes

`NiubizSimuladoGateway` autoriza órdenes de prueba, conserva su importe en memoria y devuelve
una URL local. Una orden de prueba pendiente no se puede confirmar tras reiniciar el backend.
No representa el sandbox real de Niubiz ni mueve dinero. Es el modo por defecto para desarrollo
y pruebas (`NIUBIZ_MODE=simulado`).

La fuente municipal debe definir y proporcionar la **asignación explícita de cada beneficio a
cada obligación** (`samnet.obligacion.id_beneficio` en la base sintética), junto con la tasa
resultante. El backend verifica que el beneficio esté vigente y pertenezca al contribuyente.
Si la Municipalidad todavía no determina elegibilidad, ámbito o acumulación de beneficios,
la API bloquea el pago virtual y deriva a ventanilla; no infiere esas reglas. También queda
pendiente el procedimiento municipal para aplicar una condonación del 100 % sin cobro.
