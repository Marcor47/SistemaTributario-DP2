# React + Vite

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

Para una demostración local sin backend, copia `.env.example` a `.env.local`, configura
`VITE_USAR_MOCK=true` y `VITE_AUTH_DEMO=true`, y reinicia Vite. HU008 usa una muestra
consistente de octubre de 2026. Para HU014, inicia sesión con `DEMO-0001` / `demo1234`.
Ambas pantallas identifican explícitamente los datos de demostración.

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
