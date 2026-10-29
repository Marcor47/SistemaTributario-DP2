# HU006 - Generar reporte del catálogo sin soporte de TI

Como Gerencia de Rentas, generar desde la plataforma los reportes de **morosos**, **PRICOS** y **buenos pagadores**
para no depender del área de TI (RF-024, RF-050, RF-051). Es una consulta de **solo lectura** sobre SAMNET a la
fecha de corte de hoy. Cada generación queda registrada (RF-059) y se aplican las restricciones del rol (RF-060).

## Acceso

Solo usuarios internos con rol `GERENCIA`, `ANALISTA_RENTAS` o `ADMINISTRADOR`. Se usa la sesión interna:

1. `POST /api/auth/interno/login` con `{"nombreUsuario": "...", "clave": "..."}` devuelve `200` y deja la
   cookie de sesión (el front debe enviar `credentials: 'include'`). En desarrollo: `analista` / `analista123`.
2. Con esa cookie se llama a los endpoints.

| Situación | Respuesta |
| --- | --- |
| Sin sesión interna (o con sesión de contribuyente) | `401` `SESION_REQUERIDA` |
| Sesión con otro rol (por ejemplo `VENTANILLA`) | `403` |
| El rol tiene restringido ese reporte | `403` `REPORTE_RESTRINGIDO` |
| `tipo` inexistente, `tributo` inválido, `anio` fuera de 2000-2100, `montoMinimo` negativo, `zona` de más de 20 caracteres | `400` `SOLICITUD_INVALIDA` |
| `anio` en el reporte de morosos | `400` `FILTRO_NO_APLICABLE` |
| SAMNET no responde | `503` `SAMNET_NO_DISPONIBLE` |

Los errores usan el contrato del backend: `{ "codigo": "...", "mensaje": "...", "detalles": [] }`.

## 1. Catálogo

```
GET /api/reportes
```

Devuelve los reportes que el rol puede generar (los restringidos por completo no aparecen) y los filtros que acepta
cada uno. La pantalla debe armar el selector y los filtros con esta respuesta.

```json
[
  {
    "tipo": "MOROSOS",
    "titulo": "Reporte de morosos",
    "descripcion": "Contribuyentes con cuotas vencidas impagas a la fecha de corte, con su deuda y etapa de cobranza.",
    "filtros": ["zona", "tributo", "montoMinimo"]
  },
  {
    "tipo": "PRICOS",
    "titulo": "Reporte de PRICOS",
    "descripcion": "Principales contribuyentes: monto emitido en el año igual o mayor al umbral configurado.",
    "filtros": ["anio", "zona", "tributo", "montoMinimo"]
  },
  {
    "tipo": "BUENOS_PAGADORES",
    "titulo": "Reporte de buenos pagadores",
    "descripcion": "Contribuyentes que pagaron a tiempo todas sus cuotas vencidas del año y no tienen deuda vencida.",
    "filtros": ["anio", "zona", "tributo", "montoMinimo"]
  }
]
```

## 2. Generar un reporte

```
GET /api/reportes/{tipo}?anio=&zona=&tributo=&montoMinimo=
```

`tipo`: `MOROSOS`, `PRICOS` o `BUENOS_PAGADORES` (el `tipo` del catálogo). Todos los filtros son opcionales:

| Filtro | Valores | Efecto |
| --- | --- | --- |
| `anio` | `2000`-`2100`. Por defecto, el año de la fecha de corte | Año de lo emitido, lo pagado y la puntualidad. **No aplica a morosos**: su deuda es de todos los años. |
| `zona` | `Z1`, `Z2`, ... (se ignoran espacios y mayúsculas) | Solo contribuyentes con algún predio en esa zona. |
| `tributo` | `IMPUESTO_PREDIAL` o `ARBITRIOS` | Los montos y cuotas se calculan solo con ese tributo. |
| `montoMinimo` | Soles, `>= 0` | Mínimo del monto principal: deuda vencida (morosos), monto emitido (PRICOS) o monto pagado (buenos pagadores). |

### Respuesta 200 (estructura común)

```json
{
  "tipoReporte": "MOROSOS",
  "titulo": "Reporte de morosos",
  "fechaCorte": "2026-10-19",
  "filtrosAplicados": { "anio": null, "zona": null, "tributo": null, "montoMinimo": null },
  "resumen": {
    "cantidad": 4,
    "conceptoMonto": "Deuda vencida",
    "montoTotal": 9420.16,
    "umbralPrico": null
  },
  "filas": [ ... ]
}
```

| Campo | Significado |
| --- | --- |
| `fechaCorte` | Fecha a la que corresponde la información. Debe mostrarse en pantalla. |
| `filtrosAplicados` | Filtros usados, ya normalizados. `anio` es el año efectivamente usado (`null` en morosos). |
| `resumen.conceptoMonto` | Etiqueta del monto que suma `montoTotal` (por ejemplo «Monto emitido en 2026»). |
| `resumen.umbralPrico` | Umbral vigente de PRICOS; solo viene en ese reporte. |
| `filas` | Su forma depende de `tipoReporte` (ver abajo). Llegan ya ordenadas; vacío si nadie cumple el criterio. |

### Filas de `MOROSOS`

Contribuyentes con al menos una cuota vencida e impaga a la fecha de corte, de **cualquier año**. Orden: mayor
deuda primero.

```json
{
  "codigo": "021390",
  "nombre": "INVERSIONES COSTA AZUL S.A.C.",
  "tipoDocumento": "RUC",
  "numeroDocumento": "20456781239",
  "domicilioFiscal": "Jr. Los Precursores 220, San Miguel",
  "zonas": ["Z2"],
  "deudaVencida": 6907.68,
  "cuotasVencidas": 6,
  "vencimientoMasAntiguo": "2025-12-31",
  "diasVencido": 292,
  "etapaCobranza": "COACTIVA",
  "nombreEtapa": "Exigible en vía coactiva",
  "deudaPorTributo": [
    { "tributo": "ARBITRIOS", "monto": 3875.04 },
    { "tributo": "IMPUESTO_PREDIAL", "monto": 3032.64 }
  ]
}
```

- `diasVencido`: días desde la cuota impaga más antigua.
- `etapaCobranza`: la etapa más avanzada de sus cuotas vencidas (`MORA_TEMPRANA`, `CAMPANA`, `VALOR_EMITIDO`,
  `ESPERA_LEGAL`, `COACTIVA`). Si SAMNET no registra ninguna, es `MORA_TEMPRANA`. `nombreEtapa` es el texto para
  mostrar.

### Filas de `PRICOS`

Principales contribuyentes: monto emitido en el año **mayor o igual al umbral** `UMBRAL_PRICO` (parámetro
configurable en `seguridad.parametro_configuracion`; hoy S/ 2,000). Orden: mayor emitido primero.

```json
{
  "codigo": "021390",
  "nombre": "INVERSIONES COSTA AZUL S.A.C.",
  "tipoPersona": "JURIDICA",
  "tipoDocumento": "RUC",
  "numeroDocumento": "20456781239",
  "domicilioFiscal": "Jr. Los Precursores 220, San Miguel",
  "zonas": ["Z2"],
  "emitidoAnio": 4647.76,
  "pagadoAnio": 0.00,
  "porcentajePagado": 0.00,
  "deudaVencida": 6907.68
}
```

- `porcentajePagado` = `pagadoAnio / emitidoAnio x 100`, con dos decimales.
- `deudaVencida` es de todos los años, para ver si el PRICO además es moroso.

### Filas de `BUENOS_PAGADORES`

Contribuyentes que, en el año, **pagaron a tiempo todas sus cuotas ya vencidas** (el pago es del día de vencimiento
o anterior) y **no tienen deuda vencida** de ningún año. Orden: mayor monto pagado primero.

```json
{
  "codigo": "014502",
  "nombre": "GARCÍA TORRES, MARÍA ELENA",
  "tipoDocumento": "DNI",
  "numeroDocumento": "41028765",
  "domicilioFiscal": "Av. La Marina 1450, San Miguel",
  "zonas": ["Z1"],
  "cuotasPagadasATiempo": 4,
  "pagadoAnio": 893.80
}
```

### Reglas para la pantalla

- Los montos vienen en soles con dos decimales; los totales y porcentajes no se calculan en el front.
- `zonas` es una lista: un contribuyente puede tener predios en varias zonas. Puede venir vacía si no tiene predios.
- Si el rol tiene restringidas algunas zonas, esas filas ya no llegan. La pantalla no tiene que filtrarlas.
- Exportar a Excel o PDF no está en esta HU: el registro de la solicitud se guarda con formato `NINGUNO`.
- Los arbitrios se manejan como un solo tributo (ver `HU005_API.md`).

## Datos de prueba

Con la base sintética y fecha de corte `2026-10-19`:

| Llamada | Resultado |
| --- | --- |
| `GET /api/reportes/MOROSOS` | `021390`, `045067`, `058934`, `061177`; total S/ 9,420.16 |
| `GET /api/reportes/MOROSOS?zona=Z2&tributo=ARBITRIOS` | `021390`, `058934`, `061177` |
| `GET /api/reportes/MOROSOS?montoMinimo=1000` | `021390`, `045067` |
| `GET /api/reportes/PRICOS` | `021390` (S/ 4,647.76), `045067` (S/ 2,145.12) |
| `GET /api/reportes/PRICOS?anio=2025` | `021390` (S/ 8,783.84, 74.27 % pagado), `045067` |
| `GET /api/reportes/BUENOS_PAGADORES` | `014502`, `033218` |
| `GET /api/reportes/MOROSOS?anio=2025` | `400` `FILTRO_NO_APLICABLE` |
