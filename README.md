# Sistema Integral de Gestión Tributaria y Seguimiento de Recaudación
Proyecto de Implementación de Software - Proyecto 1INF47 - Grupo 10

## Estructura
- `back`: Java + Spring Boot (API REST)
- `frontweb`: React + Vite (plataforma interna)
- `frontmobile`: React Native + Expo (portal del ciudadano)

## Requisitos
- JDK 21 o superior, Node.js LTS y PostgreSQL con pgAdmin

## Base de datos (una sola vez por equipo)
En pgAdmin, Query Tool, ejecuta por separado:
`CREATE USER municipalidad WITH PASSWORD 'municipalidad';`
`CREATE DATABASE municipalidad OWNER municipalidad;`

## Cómo levantar el proyecto (CMD)
1. Back: configura `DB_PASSWORD` con la contraseña local de PostgreSQL; luego `cd back` y `mvnw.cmd spring-boot:run` (http://localhost:8080/api/health)
2. Front web: `cd frontweb`, `npm install` y `npm run dev` (http://localhost:5173)
3. Front móvil: `cd frontmobile`, `npm install` y `npx expo start`

Para probar las APIs de predios, sesión y formularios con datos ficticios, consulta `back/README.md`.
