# HU038 — API de Generación de Reporte de Buenos Pagadores por Zona

## Alcance y Requisitos (HU038 / RF-051 / RF-008)

Como **Gerencia de Rentas**, generar el reporte de contribuyentes puntuales (buenos pagadores) por **zona** y **periodo**, para reconocer y dar seguimiento a los buenos pagadores institucionales.

**Implementación temporal sin HU025:** esta API consulta `samnet.ps_reportecartera` mediante `SamnetGateway`. Si existe un lote de cobranza publicado, usa su fecha de corte; si no existe, usa la fecha actual. No lee todavía los hechos del modelo estrella ni ejecuta el ETL. La coincidencia automatizada se comprueba contra datos sintéticos de SAMNET; la comparación con una muestra real del reporte de SAMNET queda pendiente de disponer de HU025 y de esa muestra de control.

### Criterios de Aceptación
1. **Control con SAMNET / ETL**: Dado que existe una carga del ETL del periodo, cuando se genera el reporte, coincide con el reporte de Contribuyentes Puntuales de SAMNET en una muestra de control.
2. **Exportación (RF-008)**: Dado el reporte generado, cuando se exporta, se descarga en formatos **Excel (`.xlsx`)** o **PDF (`.pdf`)**.
3. **Columnas requeridas por RF-051**:
   - Código de contribuyente (`codigo`)
   - Razón social (`razonSocial` / `nombre`)
   - Régimen tributario (`regimen`)
   - Documento de identidad: tipo (`tipoDocumento`) y número (`numeroDocumento` DNI o RUC)
   - Domicilio fiscal: consolidado (`domicilioFiscal`) y desglosado con vía, número, departamento, interior, manzana, lote y distrito (`domicilioFiscalDesglosado`)
   - Monto emitido de predial (`montoEmitidoPredial`)
   - Monto emitido de arbitrios (`montoEmitidoArbitrios`)
   - Monto pagado de predial (`montoPagadoPredial`)
   - Monto pagado de arbitrios (`montoPagadoArbitrios`)
   - Total emitido (`totalEmitido`)
   - Total pagado (`totalPagado` / `pagadoAnio`)
   - Cuotas pagadas a tiempo (`cuotasPagadasATiempo`)
   - Zonas de los predios (`zonas`)

---

## Seguridad y Acceso

- **Roles autorizados**: `GERENCIA` y `ANALISTA_RENTAS`.
- Requiere cookie de sesión interna iniciada mediante `POST /api/auth/interno/login`.
- Respuestas de error de acceso:
  - Sin autenticación: `401 Unauthorized` (`SESION_REQUERIDA`).
  - Rol no autorizado (ej. `VENTANILLA`, `CONTRIBUYENTE`): `403 Forbidden`.

---

## Endpoints

### 1. Generar Reporte de Buenos Pagadores por Zona y Periodo

```http
GET /api/reportes/buenos-pagadores-zona?periodo={periodo}&zona={zona}&tributo={tributo}&montoMinimo={montoMinimo}
```

#### Parámetros de consulta (Query Params)

| Parámetro | Tipo | Requerido | Descripción / Validación |
| :--- | :--- | :--- | :--- |
| `periodo` | `Integer` | Opcional | Año o periodo gravable evaluado (2000 a 2100). También se acepta como alias `anio`. Si se omite, toma por defecto el año de la fecha de corte del último lote ETL publicado de cobranza (o el año actual). |
| `zona` | `String` | Opcional | Código de zona (ej. `Z1`, `Z2`, `Z3`, etc. Máx. 20 caracteres). |
| `tributo` | `String` | Opcional | `IMPUESTO_PREDIAL` o `ARBITRIOS`. La puntualidad y ausencia de deuda se evalúan siempre considerando ambos tributos; el filtro limita los montos mostrados. |
| `montoMinimo` | `BigDecimal`| Opcional | Monto mínimo pagado acumulado (mayor o igual a 0). |

#### Respuesta Exitosa (`200 OK`)

```json
{
  "tipoReporte": "BUENOS_PAGADORES",
  "titulo": "Reporte de buenos pagadores",
  "fechaCorte": "2026-10-19",
  "filtrosAplicados": {
    "anio": 2026,
    "zona": "Z1",
    "tributo": null,
    "montoMinimo": null,
    "periodo": 2026
  },
  "resumen": {
    "cantidad": 1,
    "conceptoMonto": "Monto pagado en 2026",
    "montoTotal": 893.80,
    "umbralPrico": null
  },
  "filas": [
    {
      "codigo": "014502",
      "nombre": "GARCÍA TORRES, MARÍA ELENA",
      "razonSocial": "GARCÍA TORRES, MARÍA ELENA",
      "regimen": "GENERAL",
      "tipoDocumento": "DNI",
      "numeroDocumento": "41028765",
      "domicilioFiscal": "Av. La Marina 1450, San Miguel",
      "domicilioFiscalDesglosado": {
        "direccion": "Av. La Marina 1450, San Miguel",
        "departamento": "LIMA",
        "provincia": null,
        "distrito": "SAN MIGUEL",
        "via": "Av. La Marina",
        "numero": "1450",
        "interior": null,
        "manzana": null,
        "lote": null
      },
      "zonas": ["Z1"],
      "montoEmitidoPredial": 392.40,
      "montoEmitidoArbitrios": 501.40,
      "montoPagadoPredial": 392.40,
      "montoPagadoArbitrios": 501.40,
      "totalEmitido": 893.80,
      "totalPagado": 893.80,
      "cuotasPagadasATiempo": 4,
      "pagadoAnio": 893.80
    }
  ]
}
```

---

### 2. Exportar Reporte a Excel o PDF (RF-008)

```http
GET /api/reportes/buenos-pagadores-zona/exportar?formato={formato}&periodo={periodo}&zona={zona}&tributo={tributo}&montoMinimo={montoMinimo}
```

#### Parámetros adicionales

| Parámetro | Tipo | Requerido | Valores Permitidos |
| :--- | :--- | :--- | :--- |
| `formato` | `String` | **Obligatorio** | `EXCEL` o `PDF` |
| `columnas` | `List<String>` | Opcional | Lista de identificadores de columna deseados. Si se omite, genera las columnas por defecto. |

#### Encabezados de Respuesta

- Para `EXCEL`:
  - `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - `Content-Disposition: attachment; filename="reporte_buenos_pagadores_zona_YYYYMMDD.xlsx"`
- Para `PDF`:
  - `Content-Type: application/pdf`
  - `Content-Disposition: attachment; filename="reporte_buenos_pagadores_zona_YYYYMMDD.pdf"`

#### Columnas generadas en el archivo exportado (RF-051)
1. **Código** (código de contribuyente)
2. **Razón Social / Nombre**
3. **Régimen**
4. **Tipo Doc.**
5. **N° Documento**
6. **Domicilio Fiscal**
7. **Zonas**
8. **Emitido Predial (S/)**
9. **Emitido Arbitrios (S/)**
10. **Pagado Predial (S/)**
11. **Pagado Arbitrios (S/)**
12. **Cuotas a Tiempo**
13. **Total Pagado (S/)**

El PDF usa página A3 apaisada para dar espacio a las columnas obligatorias. También se permiten las columnas opcionales `totalEmitido`, `nombre` y `pagadoAnio` al solicitar una exportación personalizada.

---

### 3. Códigos de Error Comunes

| Código HTTP | Código Interno | Motivo |
| :--- | :--- | :--- |
| `400 Bad Request` | `SOLICITUD_INVALIDA` | Parámetros inválidos (ej. periodo fuera de 2000-2100, monto mínimo negativo, formato no soportado). |
| `401 Unauthorized` | `SESION_REQUERIDA` | No se envió cookie de sesión o la sesión interna ha expirado. |
| `403 Forbidden` | `ACCESO_DENEGADO` | El usuario autenticado no posee el rol `GERENCIA` o `ANALISTA_RENTAS`. |
| `503 Service Unavailable` | `SAMNET_NO_DISPONIBLE` | Falla de conexión con la base de datos o procedimiento almacenado de SAMNET. |

