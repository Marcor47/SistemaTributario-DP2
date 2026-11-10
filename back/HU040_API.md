# HU040 — Auditar las decisiones de homologación

## Alcance y Requisitos (HU040 / RF-053)

Como **Responsable de datos**, consultar el detalle de cada decisión de homologación, para explicar por qué un registro quedó asociado a determinada entidad maestra (RF-053).

Complementa las decisiones de homologación del motor (RF-039) y de la bandeja de supervisión (RF-040).

### Criterios de Aceptación

1. **Criterio 1 — Detalle de resolución**: Dado un registro maestro, cuando consulto su decisión de homologación, entonces veo el nivel que la resolvió (`N0_CORRESPONDENCIA_PREVIA`, `N1_EXACTA_NORMALIZADA`, `N2_SIMILITUD`, `N3_ALTA_NUEVA`), el puntaje alcanzado (0 a 1), los atributos que coincidieron y si fue automática (motor) o humana. En las decisiones humanas se incluye además el usuario responsable, la fecha/hora y el motivo registrado (aprobación, rechazo u override).
2. **Criterio 2 — Registro inalterable**: Dado un registro de auditoría, cuando intento modificarlo desde la aplicación, entonces no es posible (no se exponen operaciones de modificación HTTP, la entidad JPA posee `@Immutable` y la tabla PostgreSQL cuenta con el trigger `tg_decision_homologacion_inalterable`).

---

## Acceso y Seguridad

Requiere sesión de usuario interno con rol **`RESPONSABLE_DATOS`** o **`ADMINISTRADOR`** y permiso **`HOMOLOGACION:AUDITAR`**.

1. Iniciar sesión interna en `POST /api/auth/interno/login` para obtener la cookie de sesión.
2. Realizar las peticiones incluyendo las credenciales (`credentials: 'include'`).

| Situación | Código HTTP | Código Error |
|---|---|---|
| Sin sesión interna activa | `401 Unauthorized` | `SESION_REQUERIDA` |
| Sesión con otro rol (ej. `VENTANILLA`, `ANALISTA_RENTAS`) | `403 Forbidden` | — |
| Entidad maestra no existe | `404 Not Found` | `ENTIDAD_MAESTRA_NO_ENCONTRADA` |
| Decisión no existe | `404 Not Found` | `DECISION_NO_ENCONTRADA` |
| Intento de `PUT`, `POST` o `DELETE` | `405 Method Not Allowed` | `METODO_NO_PERMITIDO` |

---

## Endpoints

### 1. Listar decisiones de un registro maestro

```http
GET /api/homologacion/maestros/{idMaestro}/decisiones
```

#### Parámetros de ruta
- `idMaestro` (Long, obligatorio): Identificador numérico de la entidad maestra (`maestros.entidad_maestra`).

#### Respuesta `200 OK`
Devuelve la lista cronológica de decisiones aplicadas sobre la entidad maestra.

```json
[
  {
    "idDecision": 104,
    "idMaestro": 15,
    "tipoEntidad": "CONTRIBUYENTE",
    "seudonimoMaestro": "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0",
    "idCaso": null,
    "nivelResolutor": "N1_EXACTA_NORMALIZADA",
    "puntaje": 1.0000,
    "atributosCoincidentes": "{\"tipoDocumento\":\"DNI\",\"numeroDocumento\":\"41028765\"}",
    "tipoDecision": "AUTOMATICA",
    "idUsuario": null,
    "nombreUsuario": null,
    "nombreCompletoUsuario": null,
    "fechaHora": "2026-10-19T02:15:30",
    "motivo": null
  }
]
```

---

### 2. Listar decisiones por seudónimo maestro

Permite auditar la decisión directamente desde el seudónimo con el que se identifica la entidad en el modelo dimensional analítico (RNF-018).

```http
GET /api/homologacion/maestros/seudonimo/{seudonimo}/decisiones
```

#### Parámetros de ruta
- `seudonimo` (String, obligatorio): Hash HMAC-SHA-256 de 64 caracteres de la entidad maestra.

#### Respuesta `200 OK` (Ejemplo de decisión humana / override)

```json
[
  {
    "idDecision": 210,
    "idMaestro": 32,
    "tipoEntidad": "PREDIO",
    "seudonimoMaestro": "9876543210abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "idCaso": 45,
    "nivelResolutor": "N2_SIMILITUD",
    "puntaje": 0.8750,
    "atributosCoincidentes": "{\"direccion\":\"Av. La Marina 1230\",\"codigoCatastral\":\"SM-04-12\"}",
    "tipoDecision": "HUMANA",
    "idUsuario": 3,
    "nombreUsuario": "jgarcia",
    "nombreCompletoUsuario": "Juan García (Responsable de Datos)",
    "fechaHora": "2026-10-19T11:20:00",
    "motivo": "Coincidencia validada presencialmente con documentación física de catastro."
  }
]
```

---

### 3. Consultar detalle de una decisión por ID

```http
GET /api/homologacion/decisiones/{idDecision}
```

#### Parámetros de ruta
- `idDecision` (Long, obligatorio): Identificador de la decisión auditada (`analitica.decision_homologacion`).

#### Respuesta `200 OK`
Devuelve el objeto individual de la decisión de homologación.

---

## Garantía de Inalterabilidad (RF-053 / Criterio 2)

Para asegurar que ningún registro de decisión de homologación pueda ser manipulado o eliminado:
1. **API REST**: No se proporcionan rutas `POST`, `PUT`, `PATCH` ni `DELETE` sobre `/api/homologacion/**`. Las solicitudes que utilicen dichos métodos HTTP son rechazadas con código `405 Method Not Allowed`.
2. **Capa ORM**: La entidad `DecisionHomologacion` está anotada con `@Immutable` de Hibernate, ignorando y previniendo actualizaciones en el contexto de persistencia.
3. **Capa Base de Datos**: La tabla `analitica.decision_homologacion` está protegida por el trigger `tg_decision_homologacion_inalterable` con la función `seguridad.fn_registro_inalterable()`, que rechaza a nivel transaccional cualquier sentencia `UPDATE` o `DELETE`.

