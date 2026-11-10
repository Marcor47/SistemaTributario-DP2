# HU036 - Registrar un predio nuevo (RF-048)

Como Analista de Rentas, registro un predio nuevo con su contribuyente asociado para que quede reflejado en SAMNET.
La escritura se hace solo por la capa de servicios municipal (`SamnetGateway`), nunca directo a SAMNET.

## `POST /api/predios`

- **Sesión:** usuario interno (cookie de sesión + cabecera CSRF `X-XSRF-TOKEN`, igual que el resto de POST internos).
- **Permiso:** `PREDIOS:REGISTRAR` (lo recibe el rol `ANALISTA_RENTAS` con la migración `V36`).
  El front debe mostrar la opción solo si el usuario tiene ese permiso.
- **Content-Type:** `application/json`

### Request

| Campo | Tipo | Obligatorio | Regla |
| --- | --- | --- | --- |
| `codigoContribuyente` | string | sí | Código de un contribuyente existente y `ACTIVO` (máx. 10). |
| `codigoPredio` | string | sí | Letras, números y guion, hasta 12 caracteres (ej. `014502-90`). |
| `numeroCatastral` | string | no | Hasta 30 caracteres. |
| `ubicacion` | string | sí | Hasta 250 caracteres. |
| `usoPredio` | string | no | Hasta 60 caracteres (ej. `Vivienda`). |
| `tipoPredio` | string | sí | `URBANO`, `RUSTICO` u `OTRO`. |
| `areaTerreno` | número | sí | Mayor que 0, hasta 2 decimales. |
| `baseImponible` | número | sí | Mayor o igual que 0 (autovalúo vigente). |
| `fechaAdquisicion` | `yyyy-MM-dd` | no | No puede ser futura. |
| `zona` | string | sí | Hasta 20 caracteres (ej. `Costanera`). |
| `condicionPropiedad` | string | no | Por defecto `PROPIETARIO`. Se guarda en mayúsculas. |
| `porcentajePropiedad` | número | no | Entre 0.01 y 100. Por defecto `100`. |

```json
{
  "codigoContribuyente": "014502",
  "codigoPredio": "014502-90",
  "numeroCatastral": "150136-010-009",
  "ubicacion": "Av. Libertad 500, San Miguel",
  "usoPredio": "Vivienda",
  "tipoPredio": "URBANO",
  "areaTerreno": 120.50,
  "baseImponible": 150000.00,
  "fechaAdquisicion": "2026-09-01",
  "zona": "Costanera"
}
```

### Respuestas

**201 Created** con cabecera `Location: /api/predios?consulta=014502-90` y el mismo formato de predio que
`GET /api/predios` (HU002):

```json
{
  "codigoPredio": "014502-90",
  "numeroCatastral": "150136-010-009",
  "ubicacion": "Av. Libertad 500, San Miguel",
  "usoPredio": "Vivienda",
  "tipoPredio": "URBANO",
  "areaTerreno": 120.50,
  "baseImponible": 150000.00,
  "fechaAdquisicion": "2026-09-01",
  "zona": "Costanera",
  "titulares": [
    { "codigo": "014502", "nombre": "GARCÍA TORRES, MARÍA ELENA", "tipoDocumento": "DNI",
      "numeroDocumento": "41028765", "condicionPropiedad": "PROPIETARIO", "porcentajePropiedad": 100 }
  ]
}
```

Los errores usan el contrato común `{ "codigo", "mensaje", "detalles" }`:

| HTTP | `codigo` | Cuándo |
| --- | --- | --- |
| 400 | `SOLICITUD_INVALIDA` | Falta un campo obligatorio o un valor no cumple su regla. `detalles` trae `campo: mensaje`. |
| 400 | `CONTRIBUYENTE_INACTIVO` | El contribuyente existe pero está inactivo. |
| 400 | `PREDIO_INVALIDO` | Falla una regla de dominio de RF-048 (por ejemplo, la suma de titulares supera 100). |
| 401 | `SESION_REQUERIDA` | No hay sesión interna. |
| 403 | `ACCESO_DENEGADO` | El usuario no tiene `PREDIOS:REGISTRAR`. |
| 404 | `CONTRIBUYENTE_NO_ENCONTRADO` | No existe el contribuyente. |
| 409 | `PREDIO_YA_REGISTRADO` | El `codigoPredio` ya existe en SAMNET. **No se modifica el predio existente.** |
| 503 | `SAMNET_NO_DISPONIBLE` | La capa municipal no responde; el registro no se hizo, se puede reintentar. |

## Notas de diseño

- El registro **nunca sobrescribe**: usa `samnet.ps_registrarpredionuevo` (INSERT sin upsert). La función existente
  `ps_registrarpredio` sigue siendo la de edición (RF-056). Un registro simultáneo del mismo código devuelve 409 al
  segundo, sin dejar datos a medias (predio y titular se insertan en una sola transacción).
- Después de registrar, el predio se puede consultar con `GET /api/predios?consulta={codigoPredio}` (HU002).
