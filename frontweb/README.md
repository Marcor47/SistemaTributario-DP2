# React + Vite

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
