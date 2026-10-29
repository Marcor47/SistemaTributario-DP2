# HU048 - Consultar pagos y beneficios del contribuyente

Como personal de Ventanilla o de Rentas, ver en una sola vista los pagos y beneficios aplicados de un
contribuyente para validar rápidamente su situación de pagos (RF-062). Es una consulta de **solo lectura**:
no modifica SAMNET.

## Acceso

Solo usuarios internos con rol `VENTANILLA`, `ANALISTA_RENTAS`, `GERENCIA` o `ADMINISTRADOR`. Se usa la sesión
interna:

1. `POST /api/auth/interno/login` con `{"nombreUsuario": "...", "clave": "..."}` devuelve `200` y deja la
   cookie de sesión (el front debe enviar `credentials: 'include'`).
2. Con esa cookie se llama al endpoint.

| Situación | Respuesta |
| --- | --- |
| Sin sesión interna (o con sesión de contribuyente) | `401` `SESION_REQUERIDA` |
| Sesión con otro rol (por ejemplo `TERMINALISTA`) | `403` |
| `consulta` ausente, vacía o de más de 20 caracteres | `400` `SOLICITUD_INVALIDA` |
| No existe el contribuyente | `404` `CONTRIBUYENTE_NO_ENCONTRADO` |
| SAMNET no responde | `503` `SAMNET_NO_DISPONIBLE` |

Los errores usan el contrato del backend: `{ "codigo": "...", "mensaje": "...", "detalles": [] }`.

## Endpoint

```
GET /api/contribuyentes/pagos-beneficios?consulta={codigo o documento}
```

`consulta`: código de contribuyente (`021390`), DNI (`41028765`) o RUC (`20456781239`). La búsqueda es exacta;
se ignoran los espacios y las mayúsculas. No busca por nombre.

### Respuesta 200

```json
{
  "contribuyente": {
    "codigo": "021390",
    "tipoDocumento": "RUC",
    "numeroDocumento": "20456781239",
    "nombre": "INVERSIONES COSTA AZUL S.A.C."
  },
  "fechaCorte": "2026-10-19",
  "resumen": {
    "cantidadPagos": 3,
    "pagosConBeneficio": 0,
    "totalPagado": 6523.92,
    "totalCorrespondiente": 6523.92,
    "totalDiferencia": 0.00
  },
  "estadoCuenta": {
    "totalEmitido": 13431.60,
    "totalPagado": 6523.92,
    "totalDeuda": 6907.68,
    "obligacionesVencidas": 6
  },
  "pagos": [
    {
      "idPago": "REC-021390-2025-3",
      "fechaPago": "2025-09-25",
      "canal": "VENTANILLA",
      "montoPagado": 2217.28,
      "montoCorrespondiente": 2217.28,
      "diferencia": 0.00,
      "beneficioAplicado": null
    }
  ],
  "beneficiosVigentes": []
}
```

(`pagos` se acorta aquí; la respuesta real trae todos, del más reciente al más antiguo.)

### Campos

| Campo | Significado |
| --- | --- |
| `fechaCorte` | Fecha a la que corresponde la información. Debe mostrarse en pantalla (RF-062). |
| `pagos[].canal` | `VENTANILLA` o `EN_LINEA`. |
| `pagos[].montoPagado` | Lo que se pagó. |
| `pagos[].montoCorrespondiente` | Lo que correspondía pagar según el estado de cuenta de SAMNET. |
| `pagos[].diferencia` | `montoPagado - montoCorrespondiente`. Negativa: se pagó menos de lo que correspondía. |
| `pagos[].beneficioAplicado` | Identificador del beneficio (`BEN-2026-0001`) o `null` si el pago no tuvo beneficio. |
| `beneficiosVigentes` | Beneficios del contribuyente vigentes a la fecha de corte: tipo (`AMNISTIA` o `PARTICULAR`), porcentaje de descuento y vigencia. |
| `estadoCuenta` | Totales de las obligaciones del contribuyente. Se devuelve siempre, también cuando no hay pagos. |

### Reglas para la pantalla

- **Sin pagos**: `pagos` viene vacío y `resumen.cantidadPagos` es `0`. La pantalla debe decirlo (por ejemplo
  «El contribuyente no tiene pagos registrados») **sin ocultar** el bloque `estadoCuenta`.
- **Detalle del beneficio de un pago**: el SP de SAMNET (`ps_resumenpagos`) solo entrega el identificador. El
  tipo y el porcentaje se pueden mostrar cruzando `beneficioAplicado` con `beneficiosVigentes`; si el beneficio
  ya venció, no aparece en esa lista y solo se puede mostrar el identificador.
- Los montos vienen en soles con dos decimales. La diferencia no se calcula en el front.
- Cada pago agrupa lo pagado por cuota (predial y arbitrios de un mismo trimestre). Los arbitrios se manejan como
  un solo tributo; no se separan limpieza, parques y seguridad (ver `HU005_API.md`).

## Datos de prueba

Con la base sintética y fecha de corte `2026-10-19`:

| `consulta` | Resultado |
| --- | --- |
| `021390` o `20456781239` | 3 pagos, deuda pendiente, sin beneficios |
| `014502` o `41028765` | 6 pagos y sin deuda |
| `033218` o `09215430` | Tiene un beneficio vigente `BEN-2026-0001` (`PARTICULAR`, 50 %) |
| `999999` | `404` |
