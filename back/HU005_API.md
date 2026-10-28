# HU005 - Ver detalle de arbitrios de un predio

Como Analista de Rentas, ver los arbitrios de un predio con su monto y estado de pago para explicarle
su situación al contribuyente.

## Importante: un solo tributo `ARBITRIOS`

La Municipalidad no ha entregado la fórmula con que reparte los arbitrios entre limpieza pública, parques y
jardines, y seguridad ciudadana. Por eso (migración `V18__agrupar_arbitrios.sql`) el sistema los maneja como
**un solo tributo por periodo**. Esta API devuelve esas cuotas tal cual; **no separa los tres arbitrios**, y la
pantalla no debe mostrar tres montos ni inventarlos. Si más adelante se conoce la fórmula, el desglose se
agregaría como un campo nuevo sin romper este contrato.

## Acceso

Solo usuarios internos con rol `ANALISTA_RENTAS` o `ADMINISTRADOR`. Se usa la sesión interna:

1. `POST /api/auth/interno/login` con `{"nombreUsuario": "...", "clave": "..."}` devuelve `200` y deja la
   cookie de sesión (el front debe enviar `credentials: 'include'`).
2. Con esa cookie se llama al endpoint.

Un contribuyente con sesión del portal **no** puede usarlo.

## Endpoint

```
GET /api/predios/{codigoPredio}/arbitrios
```

`codigoPredio`: por ejemplo `021390-01` (máximo 20 caracteres).

### Respuesta 200

```json
{
  "predio": {
    "codigoPredio": "021390-01",
    "ubicacion": "Jr. Los Precursores 220, San Miguel",
    "usoPredio": "Comercio",
    "zona": "Z2",
    "titulares": [
      {
        "codigoContribuyente": "021390",
        "nombre": "INVERSIONES COSTA AZUL S.A.C.",
        "condicionPropiedad": "PROPIETARIO",
        "porcentajePropiedad": 100.00
      }
    ]
  },
  "fechaCorte": "2026-10-19",
  "resumen": {
    "totalEmitido": 7534.80,
    "totalPagado": 3659.76,
    "totalDescontado": 0.00,
    "totalSaldo": 3875.04,
    "cuotasPagadas": 3,
    "cuotasPendientes": 0,
    "cuotasVencidas": 3
  },
  "cuotas": [
    {
      "idObligacion": 13,
      "codigoContribuyente": "021390",
      "anio": 2025,
      "cuota": 1,
      "periodo": "2025-T1",
      "fechaVencimiento": "2025-03-31",
      "emitido": 1196.00,
      "pagado": 1196.00,
      "descontado": 0.00,
      "saldo": 0.00,
      "estado": "PAGADA",
      "diasVencida": 0
    },
    {
      "idObligacion": 19,
      "codigoContribuyente": "021390",
      "anio": 2025,
      "cuota": 4,
      "periodo": "2025-T4",
      "fechaVencimiento": "2025-12-31",
      "emitido": 1267.76,
      "pagado": 0.00,
      "descontado": 0.00,
      "saldo": 1267.76,
      "estado": "VENCIDA",
      "diasVencida": 292
    }
  ]
}
```

(El ejemplo muestra solo 2 de las 6 cuotas.)

### Campos

| Campo | Descripción |
| --- | --- |
| `fechaCorte` | Fecha a la que se calcula el estado de pago (hoy). |
| `resumen.*` | Totales de todas las cuotas y conteo por estado. |
| `cuotas` | Ordenadas por año, cuota y titular. Montos en soles con 2 decimales. |
| `cuotas[].estado` | `PAGADA` (saldo 0), `PENDIENTE` (con saldo, aún no vence) o `VENCIDA` (con saldo y vencida). |
| `cuotas[].saldo` | `emitido - pagado - descontado`. |
| `cuotas[].diasVencida` | Días de atraso. Es `0` si la cuota no está `VENCIDA`. |
| `cuotas[].codigoContribuyente` | Titular al que se emitió la cuota. |

**Copropiedad:** si el predio tiene varios titulares (por ejemplo `058934-01`, con 2), cada titular tiene
sus propias cuotas del mismo periodo. Para mostrarlas por persona, agrupar por `codigoContribuyente` y
tomar el nombre de `predio.titulares`.

**Predio sin arbitrios:** devuelve `200` con `cuotas: []` y el resumen en cero.

### Errores

Todos con el formato `{ "codigo", "mensaje", "detalles" }`.

| HTTP | `codigo` | Cuándo |
| --- | --- | --- |
| 401 | `SESION_REQUERIDA` | No hay sesión interna (o expiró). |
| 403 | `ERROR_HTTP` | Sesión interna sin rol `ANALISTA_RENTAS` ni `ADMINISTRADOR`. |
| 404 | `PREDIO_NO_ENCONTRADO` | No existe el predio. |
| 503 | `SAMNET_NO_DISPONIBLE` | SAMNET no responde. Esta consulta no usa copia de respaldo. |

## Datos de prueba

| Predio | Caso |
| --- | --- |
| `014502-01` | 6 cuotas, todas pagadas. |
| `021390-01` | 3 pagadas y 3 vencidas. |
| `045067-01` | 5 pagadas y 1 vencida. |
| `058934-01` | Copropiedad: 2 titulares, 12 cuotas. |
| `XXX` | Predio inexistente (404). |

Usuario interno de prueba (solo desarrollo local): `admin` / `admin123`, rol `ADMINISTRADOR`. Todavía no hay
pantalla de login interno en `frontweb`; hasta que exista, el front debe llamar primero a
`/api/auth/interno/login`.
