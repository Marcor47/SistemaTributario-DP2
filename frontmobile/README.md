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

El backend debe estar levantado (`back/`). La URL se toma de `EXPO_PUBLIC_API_URL`; sin ella se usa
`http://10.0.2.2:8080/api` en el emulador de Android y `http://localhost:8080/api` en iOS y web.
En un celular físico copia `.env.example` a `.env.local` y pon la IP de tu PC. Para probar en el
navegador agrega `http://localhost:8081` a `CORS_ORIGINS` del backend.

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
