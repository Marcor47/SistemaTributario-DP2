# HU018 — API de Generación de Reporte de Morosos por Zona y Periodo

## Alcance y Requisitos (HU018 / RF-024 / RF-008)

Como **Gerencia de Rentas**, generar el reporte de contribuyentes morosos por **zona** y **periodo**, para orientar campañas de cobranza institucional.

### Criterios de Aceptación
1. **Control con SAMNET / ETL**: Dado que existe una carga del ETL del periodo, cuando se genera el reporte, coincide con el reporte de Contribuyentes Morosos de SAMNET en una muestra de control.
2. **Exportación (RF-008)**: Dado el reporte generado, cuando se exporta, se descarga en formatos **Excel (`.xlsx`)** o **PDF (`.pdf`)**.
3. **Columnas requeridas por RF-024**:
   - Código de contribuyente (`codigo`)
   - Razón social (`razonSocial` / `nombre`)
   - Régimen tributario (`regimen`)
   - Documento de identidad: tipo (`tipoDocumento`) y número (`numeroDocumento` DNI o RUC)
   - Domicilio fiscal: desglosado con vía, número, departamento, interior, manzana, lote y distrito (`domicilioFiscalDesglosado`) y consolidado (`domicilioFiscal`)
   - Deuda sin beneficio (`deudaSinBeneficio`)

---

## Fuente de los datos

El reporte se calcula sobre la última carga `PUBLICADA` de `ps_etl_cobranza`:

- Montos, cuotas y antigüedad: `analitica.hecho_cobranza`.
- Identidad y etapa de cobranza: SAMNET, a través de `maestros.correspondencia_origen`; cada reidentificación queda registrada en la auditoría (RNF-018).
- Sin carga publicada con hechos: `422` `SIN_CARGA_ETL`.
- Sin correspondencia con SAMNET: `422` `ETL_SIN_CORRESPONDENCIA`.

**Filtro de zona:** cuando se envía `zona`, solo se suman los arbitrios de los predios de esa zona. El Impuesto Predial es del contribuyente y no se atribuye a una zona, por lo que no se incluye en los totales filtrados. En el detalle, `zona` solo viene para las cuotas de arbitrios; en predial es `null`.

## Seguridad y Acceso

- **Roles autorizados**: `GERENCIA` y `ANALISTA_RENTAS`.
- Requiere cookie de sesión interna iniciada mediante `POST /api/auth/interno/login`.
- Respuestas de error de acceso:
  - Sin autenticación: `401 Unauthorized` (`SESION_REQUERIDA`).
  - Rol no autorizado (ej. `VENTANILLA`, `CONTRIBUYENTE`): `403 Forbidden`.

---

## Endpoints

### 1. Generar Reporte de Morosos por Zona y Periodo

```http
GET /api/reportes/morosos-zona?anioDesde={anioDesde}&anioHasta={anioHasta}&zona={zona}&tributo={tributo}&regimen={regimen}&tipoDocumento={tipoDocumento}&etapa={etapa}&beneficio={beneficio}&montoMinimo={montoMinimo}
```

#### Parámetros de consulta (Query Params)

| Parámetro | Tipo | Requerido | Descripción / Validación |
| :--- | :--- | :--- | :--- |
| `anioDesde` | `Integer` | Opcional | Año inicial del rango de deuda vencida (1900 a 2100). Por defecto `1900`. |
| `anioHasta` | `Integer` | Opcional | Año final del rango de deuda vencida (1900 a 2100). Por defecto el año de la fecha de corte. |
| `beneficio` | `String` | Opcional | `AMBOS`, `CON_BENEFICIO` o `SIN_BENEFICIO`. Por defecto `AMBOS`. |
| `zona` | `String` | Opcional | Código de zona (ej. `Z1`, `Z2`, etc. Máx. 20 caracteres). |
| `tributo` | `String` | Opcional | `IMPUESTO_PREDIAL` o `ARBITRIOS`. Si se omite, incluye ambos tributos. |
| `regimen` | `String` | Opcional | Régimen del contribuyente (máx. 40 caracteres). |
| `tipoDocumento` | `String` | Opcional | `DNI`, `RUC` o `CE`. |
| `etapa` | `String` | Opcional | Etapa más avanzada de la deuda vencida: `MORA_TEMPRANA`, `CAMPANA`, `VALOR_EMITIDO`, `ESPERA_LEGAL` o `COACTIVA`. |
| `montoMinimo` | `BigDecimal`| Opcional | Monto mínimo de deuda vencida (mayor o igual a 0). |

Para el selector de zona, la pantalla puede obtener las zonas disponibles con `GET /api/reportes/zonas?tipo=MOROSOS`
(ver [HU006](HU006_API.md)); ese catálogo ya excluye las zonas restringidas para el rol.

#### Respuesta Exitosa (`200 OK`)

Se muestra solo la primera fila de las tres que devuelve `periodo=2026&zona=Z2`:

```json
{
  "tipoReporte": "MOROSOS",
  "titulo": "Reporte de contribuyentes morosos por zona",
  "fechaCorte": "2026-10-19",
  "filtrosAplicados": {
    "anio": 2026,
    "periodo": 2026,
    "zona": "Z2",
    "tributo": null,
    "montoMinimo": null
  },
  "resumen": {
    "cantidad": 3,
    "conceptoMonto": "Deuda vencida periodo 2026",
    "montoTotal": 7732.32,
    "umbralPrico": null
  },
  "filas": [
    {
      "codigo": "021390",
      "nombre": "INVERSIONES COSTA AZUL S.A.C.",
      "razonSocial": "INVERSIONES COSTA AZUL S.A.C.",
      "regimen": "GENERAL",
      "tipoDocumento": "RUC",
      "numeroDocumento": "20456781239",
      "domicilioFiscal": "Jr. Los Precursores 220, San Miguel",
      "domicilioFiscalDesglosado": {
        "via": "Jr. Los Precursores",
        "numero": "220",
        "departamento": null,
        "interior": null,
        "manzana": null,
        "lote": null,
        "distrito": "San Miguel"
      },
      "zonas": ["Z1", "Z2", "Z3"],
      "deudaVencida": 6930.08,
      "deudaSinBeneficio": 6930.08,
      "cuotasVencidas": 10,
      "vencimientoMasAntiguo": "2025-12-31",
      "diasVencido": 292,
      "etapaCobranza": "ESPERA_LEGAL",
      "nombreEtapa": "Espera legal",
      "deudaPorTributo": [
        { "tributo": "ARBITRIOS", "monto": 6930.08 }
      ]
    }
  ]
}
```

---

### 2. Exportar Reporte a Excel o PDF (RF-008)

```http
GET /api/reportes/morosos-zona/exportar?formato={formato}&anioDesde={anioDesde}&anioHasta={anioHasta}&zona={zona}&tributo={tributo}&regimen={regimen}&tipoDocumento={tipoDocumento}&etapa={etapa}&beneficio={beneficio}&montoMinimo={montoMinimo}
```

#### Parámetros adicionales

| Parámetro | Tipo | Requerido | Valores Permitidos |
| :--- | :--- | :--- | :--- |
| `formato` | `String` | **Obligatorio** | `EXCEL` o `PDF` |

#### Encabezados de Respuesta

- Para `EXCEL`:
  - `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - `Content-Disposition: attachment; filename="reporte_morosos_zona_YYYYMMDD.xlsx"`
- Para `PDF`:
  - `Content-Type: application/pdf`
  - `Content-Disposition: attachment; filename="reporte_morosos_zona_YYYYMMDD.pdf"`

#### Columnas generadas en el archivo exportado (RF-024)
1. **Código** (código de contribuyente)
2. **Razón Social / Nombre**
3. **Régimen**
4. **Tipo Doc.**
5. **N° Doc.**
6. **Vía**
7. **Número**
8. **Dpto.**
9. **Interior**
10. **Mz.**
11. **Lote**
12. **Distrito**
13. **Zonas**
14. **Deuda Sin Beneficio**
15. **Deuda Vencida**
16. **Cuotas Vencidas**
17. **Etapa Cobranza**
18. **Días Vencido**

---

### 3. Códigos de Error Comunes

| Código HTTP | Código Interno | Motivo |
| :--- | :--- | :--- |
| `400 Bad Request` | `SOLICITUD_INVALIDA` | Parámetros inválidos (ej. periodo fuera de 2000-2100, monto mínimo negativo, formato no soportado). |
| `401 Unauthorized` | `SESION_REQUERIDA` | No se envió cookie de sesión o la sesión interna ha expirado. |
| `403 Forbidden` | `ACCESO_DENEGADO` | El usuario autenticado no posee el rol `GERENCIA` o `ANALISTA_RENTAS`. |
| `503 Service Unavailable` | `SAMNET_NO_DISPONIBLE` | Falla de conexión con la base de datos o procedimiento almacenado de SAMNET. |

