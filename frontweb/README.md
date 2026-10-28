# React + Vite

## Pago total en línea y pasarela simulada (HU022)

En `/estado-cuenta`, **Revisar pago total** crea una orden con
`POST /api/portal/pagos` y body `{}`. El backend determina todas las obligaciones
pendientes y sus beneficios; los filtros de la consulta no limitan el pago.
Con una sesión del backend requiere una cuenta actualizada. Las copias
desactualizadas no habilitan pagos.

El portal usa siempre la sesión y los datos del backend: no crea pagos ni saldos
en `sessionStorage`. `.env.development` tiene `VITE_AUTH_DEMO=false` y apunta a
`http://localhost:8080/api`. Inicia el backend con su perfil normal y PostgreSQL;
el login acepta el código o el documento de un contribuyente con credencial
habilitada en `samnet.credencial_web`. Flyway crea automáticamente los accesos de
los seis contribuyentes ficticios activos mediante `R__datos_prueba_07_credenciales_portal.sql`,
sin sobrescribir credenciales existentes. Por ejemplo, usa `021390` con
`Portal021390!`; los demás accesos están en `back/README.md`.
`DEMO-0001` es un acceso local antiguo y no sirve para realizar este flujo con la BD.
En desarrollo, el login muestra un recuadro amarillo con `021390` / `Portal021390!`
y el botón **Usar estos datos** para completar el formulario. El botón no inicia
sesión automáticamente; **Ingresar** autentica mediante el backend.

La URL local `/pasarela-simulada/SIM-…?transaccion=…` abre una pantalla de prueba
con saldo base, descuento y total recibidos del servidor. No pide tarjetas ni
conecta al sandbox real de Niubiz. Solo un clic en **Confirmar pago**
envía `POST /api/portal/pagos/{id}/confirmacion`. Volver a la cuenta no confirma
ni cancela la orden. La simulación sí registra pagos en los datos del backend.

El comprobante solo aparece tras una respuesta `CONFIRMADA`. La ruta `/pagos/{id}`
permite recuperar una orden por su ID y una recarga consulta su estado sin volver
a confirmar. Si se pierde la respuesta del POST, se hace un GET de esa misma
orden; si tampoco se puede verificar, se exige consultar su resultado antes de
volver a confirmar. No se crea otro pago automáticamente ni se inventan saldos.
Al volver a la cuenta, los saldos y el comprobante se consultan al servidor.
La confirmación abre un modal con un check, el comprobante y los importes; se
puede volver a abrir desde el detalle de la operación.

Los pagos se revisan en el mismo **Estado de cuenta**, en las columnas **Pagado**
y **Saldo pendiente**, el estado **Pagada** y la vista **Periodos con pagos**.
Tras volver de la pasarela, un aviso enlaza al comprobante de la operación recién
confirmada. Los importes aplicados por periodo no representan un historial de
recibos; la API actual no incluye los comprobantes en las filas de la cuenta.

`SALDO_CAMBIO` obliga a revisar la cuenta y crear una orden nueva.
`BENEFICIO_INDETERMINADO` y `SIN_COBRO_VIRTUAL` indican atención en ventanilla.
Una sesión vencida solicita iniciar sesión; una orden expirada o no reconocida
no se confirma. Contrato y datos: `back/HU022_API.md`. La orden simulada pendiente
puede perderse al reiniciar el backend porque la pasarela conserva órdenes en memoria.

## Declaración jurada del predio (HU003)

En `/predios`, busca y selecciona un predio. Las minipestañas **Resumen** y
**Declaración jurada** conservan el mismo predio seleccionado. La segunda consulta
`GET /api/predios/{codigoPredio}/declaracion-jurada` mediante `VITE_API_URL`
(por defecto `http://localhost:8080/api`). Requiere el backend disponible, incluso
si otras pantallas están en modo demostración; no sustituye errores por una DJ ficticia.

La pantalla muestra la DJ vigente y su autoavalúo, el historial no vigente, la fecha
de consulta y las advertencias de SAMNET. La vigencia la resuelve la API y no se
deduce por el año. Las consultas fallidas permiten reintentar; cambiar de predio
descarta la consulta anterior. Un importe ausente se muestra como no disponible.

Para probar la integración, usa `014502-01` (vigente 2024 e histórica 2012) o
`021390-01` (solo vigente). La búsqueda y el resumen de HU002 siguen usando los
datos de demostración existentes; conectarlos a su API queda fuera de HU003.

## Morosidad (HU008) y mi estado de cuenta (HU014)

- `/morosidad`: consulta de la última carga publicada o de un año/mes, índice total,
  gráficos y tablas por tributo y zona, ordenados por morosidad o nombre.
- `/estado-cuenta`: consulta propia por cookie de sesión, fecha de corte, resumen de
  deuda, pagos aplicados (incluidos los parciales) y filtros por año, tributo, estado y texto.
  `/portal/estado-cuenta` y `/mi-cuenta` redirigen a esta pantalla.
- No se muestran recibos, fechas de pago ni montos base que el contrato de la API no entregue.
  Una consulta fallida muestra su error; un 401 solicita iniciar sesión y elimina la cuenta de la vista.
- El cálculo ETL se mantiene en el backend. Se conserva el acceso existente a Pagos
  en Línea: tras una confirmación se vuelve a consultar el estado de cuenta al servidor,
  sin simular un pago exitoso por una caída de conexión.

Para mostrar HU008 sin backend, `VITE_USAR_MOCK=true` habilita una muestra
consistente de octubre de 2026. HU014 y HU022 requieren una sesión del backend y
consultan PostgreSQL. Después de cambiar variables de entorno, reinicia Vite.

En integración deja ambas opciones en `false` y configura `VITE_API_URL`. El estado
de cuenta utiliza `GET /api/portal/estado-cuenta`, sin enviar identificadores del contribuyente;
morosidad utiliza `GET /api/indicadores/morosidad` con año y mes opcionales.


## Constancia de No Adeudo (HU015)

La pantalla `/constancias` permite iniciar o recuperar una solicitud, revisar y descargar el
documento que genera el backend, y enviar la firma. Requiere una sesión iniciada con
`POST /api/auth/login`; el frontend envía la cookie en las peticiones. El botón **Salir** cierra
esa sesión en el backend.

Mientras el backend use `FirmadorSimuladoGateway`, la pantalla ofrece **Firmar en modo de prueba**
y muestra que la firma no tiene validez legal. También acepta un archivo de firma de hasta 48 KB
y el texto del certificado para enviarlos al contrato actual de la API. La integración con un
firmador real queda pendiente.

El frontend usa `VITE_API_URL` o `http://localhost:8080/api` por defecto. Para entrar con la base
PostgreSQL se necesita una credencial de contribuyente en `samnet.credencial_web`. El modo
`VITE_AUTH_DEMO=true` funciona sin backend y no permite completar este trámite.

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.
