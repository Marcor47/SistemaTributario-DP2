# Backend tributario

## APIs de demostración

Las rutas nuevas se activan **solo** con el perfil `demo`. Este perfil usa predios ficticios,
autenticación simulada y una base H2 local para el catálogo de formularios. No consulta SAMNET.
El servidor de demostración escucha únicamente en `127.0.0.1`.

En PowerShell, define valores de prueba propios y arranca el servidor:

```powershell
$env:DEMO_CITIZEN_CODE = 'USUARIO_DE_PRUEBA'
$env:DEMO_CITIZEN_PASSWORD = '<clave-de-prueba>'
$env:DEMO_INTERNAL_READ_KEY = '<clave-lectura-distinta>'
$env:DEMO_INTERNAL_ADMIN_KEY = '<clave-admin-distinta>'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

El perfil `demo` guarda las versiones de formularios en `tributario-demo.mv.db`, que Git ignora.
Las claves de prueba se configuran fuera del repositorio. Para el perfil normal, configura
`DB_URL`, `DB_USER` y `DB_PASSWORD` para PostgreSQL.

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
  oficiales. La base H2 de demostración no representa ese almacenamiento.

Ejecuta `mvnw.cmd test` para verificar consultas, sesión, control de acceso de demostración y
versionado de formularios sin necesitar PostgreSQL.
