# Backend tributario

## APIs de demostración

Las rutas nuevas se activan **solo** con el perfil `demo`. Este perfil usa predios ficticios y
autenticación simulada. El catálogo de formularios utiliza PostgreSQL, igual que el resto del
backend. No consulta SAMNET.
El servidor de demostración escucha únicamente en `127.0.0.1`.

En PowerShell, configura la conexión PostgreSQL y valores de prueba propios antes de arrancar:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/municipalidad'
$env:DB_USER = 'municipalidad'
$env:DB_PASSWORD = '<contraseña-local>'
$env:DEMO_CITIZEN_CODE = 'USUARIO_DE_PRUEBA'
$env:DEMO_CITIZEN_PASSWORD = '<clave-de-prueba>'
$env:DEMO_INTERNAL_READ_KEY = '<clave-lectura-distinta>'
$env:DEMO_INTERNAL_ADMIN_KEY = '<clave-admin-distinta>'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

Las versiones de formularios se guardan en la base indicada por `DB_URL`. No se crea una base
local de demostración. Sin PostgreSQL disponible, el backend no inicia. Las claves de prueba se
configuran fuera del repositorio. El perfil normal usa las mismas variables de conexión.

| Ruta | Acción | Acceso demo |
| --- | --- | --- |
| `GET /api/predios?consulta=...` | Busca por código, titular o documento; `[]` indica sin resultados. | Cabecera `X-Demo-Internal-Key` de lectura o administración |
| `POST /api/auth/login` | Recibe `codigoContribuyente` y `password`; crea sesión de 10 minutos de inactividad. | Sin cabecera interna |
| `GET /api/auth/me` | Devuelve el código del contribuyente de la sesión. | Cookie de sesión |
| `POST /api/auth/logout` | Cierra la sesión. | Cookie de sesión |
| `GET /api/formularios` | Lista solo las versiones vigentes. | Cabecera interna de lectura o administración |
| `GET /api/formularios/{codigo}/descarga` | Descarga el PDF o DOCX vigente. | Cabecera interna de lectura o administración |
| `POST /api/formularios` | Publica una nueva versión con campos multipart `codigo`, `nombre`, `archivo`. | Cabecera interna de administración |

El login devuelve `401` con un mensaje genérico cuando las credenciales fallan y `429` tras
cinco fallos en quince minutos por usuario o IP. El backend no devuelve la contraseña ni un
token municipal. Las versiones anteriores de un formulario permanecen registradas, pero
dejan de aparecer en las consultas vigentes.

## Integraciones pendientes

- Sustituir `DemoPredioGateway` por un cliente de la API municipal que consulta SAMNET.
- Sustituir `DemoMunicipalAuthClient` por `/api/Autenticacion/ps_login` y validar su contrato real.
- Integrar el acceso de los usuarios internos por rol. Las claves de `demo` no son el esquema
  de autenticación definitivo.
- Acordar el almacenamiento definitivo de plantillas en el lakehouse y cargar los archivos
  oficiales. PostgreSQL conserva el catálogo actual mientras se define ese almacenamiento.

Ejecuta `mvnw.cmd test` para verificar consultas, sesión, control de acceso de demostración y
versionado de formularios. Las pruebas levantan un PostgreSQL temporal; no requieren uno instalado.
