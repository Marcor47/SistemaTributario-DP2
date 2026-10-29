# App móvil (Expo SDK 58 + Expo Router)

App del contribuyente de la Municipalidad de San Miguel. Usa la misma identidad visual que `frontweb`
(colores marino `#35497D` / `#17406B`, acento cian `#19C3F3`, fuente Atkinson Hyperlegible). Los tokens
están en `src/theme/tokens.js`; úsalos en lugar de escribir colores en cada pantalla.

## Ejecutar

```bash
npm install
npx expo start          # a: emulador Android · i: simulador iOS · w: navegador
npx expo lint
```

El backend debe estar levantado (`back/`). `EXPO_PUBLIC_API_URL`, si está definida, tiene prioridad.
En Expo Go por LAN, la app detecta la IP privada de la PC desde `Constants.expoConfig.hostUri` y usa
`http://<IP-de-la-PC>:8080/api`. El teléfono y la PC deben estar en la misma red Wi-Fi. En web se usa
`http://localhost:8080/api`; sin una dirección LAN detectada, Android usa `10.0.2.2` para el emulador
e iOS usa `localhost` para el simulador. Para otro servidor o puerto, configura la URL en `.env.local`
usando `.env.example` como referencia. El túnel de Expo solo publica Metro, no el backend.

El perfil `local` del
backend permite por defecto `http://localhost:5173` (portal web) y `http://localhost:8081` (Expo web).
Si defines `CORS_ORIGINS`, incluye ambos orígenes separados por coma. Reinicia el backend tras
cambiar esta configuración.

Si el teléfono muestra un error de conexión, abre `http://<IP-de-la-PC>:8080/api/health` en su navegador.
Debe responder `{"servicio":"tributario-back","estado":"ok"}`. Si no responde, revisa que el backend esté
encendido y que la red permita acceder al puerto 8080. Tras cambiar la URL, haz **Reload** en Expo Go.

## Estructura

| Ruta | Contenido |
| --- | --- |
| `src/app/` | Pantallas (Expo Router). `_layout.jsx` protege las rutas según la sesión. |
| `src/components/ui/` | Componentes base: `Boton`, `CampoTexto`, `Aviso`, `Cabecera`, `Icono`. |
| `src/context/SesionContext.jsx` | Sesión del contribuyente (`useSesion`). |
| `src/services/api.js` | `pedir()`: mismo contrato de error que la web (`{ codigo, mensaje, detalles }`). |

Para agregar un trámite al inicio, crea su ruta en `src/app/` y añade una entrada en `SERVICIOS`
de `src/app/index.jsx`. Las rutas nuevas que requieran sesión van dentro de
`<Stack.Protected guard={autenticado}>` en `src/app/_layout.jsx`.

## Inicio de sesión

`/login` llama a `POST /api/auth/login`; el backend responde con una cookie de sesión que la app
reenvía en cada petición. Al abrir la app se consulta `GET /api/auth/me` para recuperar la sesión.
Si una petición responde 401 (la sesión vence a los 10 minutos de inactividad), la app vuelve al
login y lo avisa. En desarrollo el login muestra el acceso de prueba `021390` / `Portal021390!`
con el botón **Usar estos datos** (no inicia sesión por sí solo).

## Registro o traspaso de predio (HU046)

Desde el inicio, **Registro o traspaso de predio** abre un asistente de cuatro pasos:

1. **Trámite**: registro o traspaso.
2. **Predio**: urbano, rústico u otro, y el código del predio (obligatorio para traspaso, máx. 12 caracteres).
3. **Documentos**: la lista sale de `GET /api/portal/solicitudes-predio/requisitos`. Cada documento se
   adjunta con la cámara, la galería o un archivo. Se validan en el celular los mismos límites que el
   backend: PDF, JPG o PNG, hasta 10 MB, todos los obligatorios y un máximo de 5 archivos.
4. **Revisar**: resumen con opción de editar cada parte y envío con `POST /api/portal/solicitudes-predio`.

Al enviar se muestra el número de solicitud, su estado (**En revisión**) y los documentos recibidos
(`GET /api/portal/solicitudes-predio/{id}`). Los errores del backend (`PREDIO_REQUERIDO`,
`REQUISITOS_FALTANTES`, `TIPO_ARCHIVO_INVALIDO`, etc.) llevan al paso que hay que corregir.

El backend necesita `PREDIO_FILES_KEY` (32 bytes aleatorios en Base64) para guardar los adjuntos;
sin ella el envío falla. Contrato completo en `back/README.md`.

## Estado de cuenta y pago de obligaciones (HU022)

Desde el inicio, **Estado de cuenta y pagos** abre `/estado-cuenta`. Consulta
`GET /api/portal/estado-cuenta` con la cookie del contribuyente y presenta el titular, la fecha de corte,
el saldo completo, los pagos aplicados y los periodos con sus conceptos desplegables. Los filtros por
año, saldo o pagos solo cambian la vista. Al volver a esta pantalla se actualiza la cuenta desde el backend.

**Pagar obligaciones** crea una orden mediante `POST /api/portal/pagos` con `{}`. El servidor determina
todas las obligaciones pendientes y calcula base, descuentos e importe final. `/pagos/[id]` consulta
esa misma orden y muestra la pasarela simulada; no solicita datos de tarjeta ni abre una URL externa.
**Confirmar pago** llama a `POST /api/portal/pagos/{id}/confirmacion`. La autorización es simulada,
pero el backend sí registra el pago y actualiza los saldos de PostgreSQL. La app solo muestra éxito
cuando la API devuelve `CONFIRMADA` y su comprobante.

El resultado abre un modal con el importe y comprobante. Desde la cuenta se puede volver al último
comprobante de la sesión y consultar los periodos con pagos. La última operación se mantiene en memoria
durante la sesión, se elimina al salir o cambiar de contribuyente y siempre se consulta en el servidor al
abrirla. Si se pierde una confirmación, se consulta la misma orden; no se crea ni confirma otra automáticamente.

Una copia desactualizada no habilita el pago. Los errores de beneficios indeterminados o de cobertura del
100 % derivan a ventanilla. Un cambio de saldo requiere volver a la cuenta y generar una cotización nueva.
El contrato completo y las limitaciones del simulador están en `back/HU022_API.md`.
