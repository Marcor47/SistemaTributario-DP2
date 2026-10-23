# Backend tributario

## Preparación local

Cada integrante debe crear una sola vez el usuario y la base local en PostgreSQL:

```sql
CREATE USER municipalidad WITH PASSWORD 'municipalidad';
CREATE DATABASE municipalidad OWNER municipalidad;
```

Luego el backend se inicia sin variables ni parámetros adicionales:

```powershell
cd back
.\mvnw.cmd spring-boot:run
```

El comando `spring-boot:run` activa el perfil `local`. Este perfil se conecta por defecto a
`jdbc:postgresql://localhost:5432/municipalidad` con el usuario y la contraseña
`municipalidad`. Si una laptop usa otra configuración, puede definir `DB_URL`, `DB_USER` o
`DB_PASSWORD` antes de iniciar el backend.

Flyway crea la estructura y carga los contribuyentes, obligaciones, metas, cobranza y
credenciales ficticias que falten. Las migraciones locales no se cargan al ejecutar una
aplicación empaquetada sin el perfil `local`.

## Credenciales ficticias

Estas cuentas son exclusivas del entorno local. La base solo almacena hashes BCrypt con coste
12; las contraseñas originales se documentan aquí para las pruebas del equipo.

| Código de contribuyente | Contraseña |
| --- | --- |
| `014502` | `DemoSM#014502` |
| `021390` | `DemoSM#021390` |
| `033218` | `DemoSM#033218` |
| `045067` | `DemoSM#045067` |
| `058934` | `DemoSM#058934` |
| `061177` | `DemoSM#061177` |
| `072405` | `DemoSM#072405` |

El contribuyente `072405` figura como inactivo en el padrón, pero su credencial local está
habilitada. El estado tributario y la habilitación para iniciar sesión son datos distintos.

## Autenticación

| Ruta | Acción |
| --- | --- |
| `GET /api/auth/csrf` | Entrega el token CSRF requerido por las operaciones que modifican datos. |
| `POST /api/auth/login` | Recibe `codigoContribuyente` y `password`; crea una sesión de 15 minutos de inactividad. |
| `GET /api/auth/me` | Devuelve el código del contribuyente de la sesión. |
| `POST /api/auth/logout` | Cierra la sesión y devuelve `204`. |

El login devuelve `401` con un mensaje genérico cuando las credenciales fallan y `429` tras
cinco fallos en quince minutos por código o IP. El backend nunca devuelve la contraseña ni su
hash.

La aplicación web conserva la sesión en una cookie `HttpOnly` y `SameSite=Strict`. La
aplicación móvil usa la misma API con la cabecera `X-Client-Platform: mobile`; en ese canal el
identificador opaco de sesión se transporta en `X-Auth-Token`. Ambos canales deben enviar el
token CSRF en `X-CSRF-TOKEN` para `POST`, `PUT`, `PATCH` y `DELETE`. La aplicación móvil debe
guardar el identificador de sesión en el almacén seguro del dispositivo.

Los orígenes web admitidos se configuran mediante `CORS_ALLOWED_ORIGINS`. En producción debe
usarse `SESSION_COOKIE_SECURE=true` para que la cookie viaje únicamente por HTTPS.

## APIs locales pendientes de integración municipal

| Ruta | Acción | Acceso local |
| --- | --- | --- |
| `GET /api/predios?consulta=...` | Busca por código, titular o documento. | `X-Demo-Internal-Key: lectura-local` o `administracion-local` |
| `GET /api/formularios` | Lista las versiones vigentes. | Clave local de lectura o administración |
| `GET /api/formularios/{codigo}/descarga` | Descarga el PDF o DOCX vigente. | Clave local de lectura o administración |
| `POST /api/formularios` | Publica una versión con `codigo`, `nombre` y `archivo`. | `X-Demo-Internal-Key: administracion-local` |

Estas claves solo funcionan en el perfil `local`, cuyo servidor escucha en `127.0.0.1`.

## Integraciones pendientes

- Sustituir `DemoPredioGateway` por un cliente de la API municipal que consulta SAMNET.
- Sustituir `LocalMunicipalAuthClient` por `/api/Autenticacion/ps_login` cuando la
  Municipalidad entregue el contrato real. La interfaz `MunicipalAuthClient` permite cambiar
  el adaptador sin modificar el contrato que consumen las aplicaciones web y móvil.
- Integrar el acceso de los usuarios internos por rol. Las cuentas descritas aquí son solo de
  contribuyentes ficticios.
- Acordar el almacenamiento definitivo de plantillas en el lakehouse y cargar los archivos
  oficiales.

Ejecuta `.\mvnw.cmd test` para verificar las migraciones, consultas, sesiones, bloqueo de
intentos, acceso local y versionado de formularios contra PostgreSQL embebido.
