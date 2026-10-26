# Backend tributario

## Portal del contribuyente (HU014, HU022 y HU046)

El perfil normal usa PostgreSQL para autenticar y consultar datos. El login recibe en el campo
`codigoContribuyente` el código o el número de documento y la clave guardada con bcrypt en
`samnet.credencial_web`. Devuelve una cookie de sesión; las rutas `/api/portal/**` toman la
identidad de esa sesión. Configura `SESSION_COOKIE_SECURE=true` cuando sirvas el backend por HTTPS.

| Ruta | Operación |
| --- | --- |
| `POST /api/auth/login` | Iniciar sesión con `codigoContribuyente` y `password`. |
| `GET /api/auth/me`, `POST /api/auth/logout` | Consultar o cerrar la sesión. |
| `GET /api/portal/estado-cuenta` | Ver únicamente el estado de cuenta propio. |
| `GET /api/estado-cuenta/{codigo}` y `/contribuyentes?q=...` | Rutas anteriores: ahora requieren sesión y solo devuelven la cuenta propia. |
| `POST /api/portal/pagos` | Crear una orden con `{"obligaciones":[123,456]}`. |
| `GET /api/portal/pagos/{id}` | Consultar el estado del pago propio. |
| `POST /api/portal/pagos/{id}/confirmacion` | Consultar la pasarela servidor a servidor y registrar el pago autorizado en PostgreSQL. Repetirlo no crea otro pago. |
| `GET /api/portal/solicitudes-predio/requisitos?tipoTramite=REGISTRO&tipoPredio=URBANO` | Consultar los documentos requeridos. |
| `POST /api/portal/solicitudes-predio` | Enviar formulario multipart con `tipoTramite`, `tipoPredio`, `codigoPredio` opcional, `requisitoIds` y `archivos` en el mismo orden. |
| `GET /api/portal/solicitudes-predio`, `GET /api/portal/solicitudes-predio/{id}` | Consultar solicitudes propias. |
| `GET /api/portal/solicitudes-predio/{id}/adjuntos/{idAdjunto}` | Descargar un adjunto de una solicitud propia. |

Los pagos usan `NiubizSimuladoGateway`: quedan identificados como `dePrueba`, no mueven dinero
real, pero sí actualizan las obligaciones sintéticas de PostgreSQL tras la confirmación. Se cobra
el saldo completo de las obligaciones seleccionadas; los beneficios o descuentos no se aplican
automáticamente en este flujo. Si la deuda cambia después de crear la orden, la confirmación
devuelve `409 SALDO_CAMBIO` y el contribuyente debe crear un pago nuevo.

Los adjuntos se guardan cifrados con AES-GCM en `back/storage/predios` al iniciar el backend desde
`back/`. Esa carpeta está excluida de Git. La BD solo guarda metadatos y ruta. Configura
`PREDIO_FILES_DIR` para otra ubicación y `PREDIO_FILES_KEY` con 32 bytes aleatorios en Base64;
sin esa clave no se aceptan archivos. Genera una clave de desarrollo en PowerShell:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:PREDIO_FILES_KEY = [Convert]::ToBase64String($bytes)
```

Conserva la misma clave para poder leer los archivos ya guardados. No la subas al repositorio.
Los requisitos de `db/datos-prueba` son provisionales y deben validarse antes de un despliegue
municipal.

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

## Base de datos y SAMNET sintético

Hay **una sola base** (`municipalidad`). Flyway la organiza en esquemas, según el Modelo de Análisis
y Diseño:

| Esquema | Contenido |
| --- | --- |
| `samnet` | BD sintética que emula SAMNET (contribuyentes, predios, DJ, obligaciones, pagos, beneficios) y sus SP `ps_*`. |
| `seguridad` | Usuarios internos, roles, sesiones, auditoría, eventos de seguridad y parámetros. |
| `operativo` | Constancia y pago en línea, solicitudes de predio, plantillas, buzón, avisos, copia del estado de cuenta y reportes. |
| `maestros` | Entidades maestras y correspondencia con el origen (datos reidentificables, acceso restringido). |
| `analitica` | Lotes del ETL, homologación, modelo estrella, indicadores, metas, alertas e IA. |

No hay acceso a los SP reales de la Municipalidad. Por eso los datos de Rentas salen de funciones
propias con el nombre y el propósito de los SP reales (`samnet.ps_ctacte`, `ps_resumenpagos`,
`ps_verificarbeneficio`, `ps_login`, etc., en `db/migration/R__samnet_procedimientos.sql`). El código
**nunca** lee las tablas de `samnet` directamente: usa `SamnetGateway`. Si algún día hay acceso a
SAMNET, solo cambia `SamnetApiGateway`.

Los demás servicios externos tienen implementación simulada con el mismo contrato (`NiubizSimuladoGateway`,
`FirmadorSimuladoGateway`, `CorreoSimuladoGateway`). La autenticación del contribuyente usa
`samnet.ps_login`. Para probarla en local, crea una credencial (solo se guarda el hash bcrypt):

```sql
INSERT INTO samnet.credencial_web (contribuyente_codigo, clave_hash)
VALUES ('014502', public.crypt('<clave-local>', public.gen_salt('bf', 12)));
```

Con `tributario.integracion.autenticacion=simulado` se usa en cambio la credencial de
`tributario.integracion.autenticacion-simulada.documento` y `.clave`.

## Integraciones pendientes

- Sustituir `DemoPredioGateway` por `SamnetGateway.buscarPredio` (SP `ps_predio`).
- Sustituir `DemoMunicipalAuthClient` por `AutenticacionMunicipalGateway` (SP `ps_login`).
- Integrar el acceso de los usuarios internos por rol. Las claves de `demo` no son el esquema
  de autenticación definitivo.
- Acordar el almacenamiento definitivo de plantillas en el lakehouse y cargar los archivos
  oficiales. PostgreSQL conserva el catálogo actual mientras se define ese almacenamiento.

Ejecuta `mvnw.cmd test` para verificar consultas, sesión, control de acceso de demostración y
versionado de formularios. Las pruebas levantan un PostgreSQL temporal; no requieren uno instalado.
