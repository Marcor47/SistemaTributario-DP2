# HU037 - Generar el reporte de PRICOS

Como Gerencia de Rentas, generar el reporte de Principales Contribuyentes (PRICOS) para dar seguimiento a los
contribuyentes de mayor impacto en la recaudación (RF-050). Es una consulta de **solo lectura** sobre la última
carga publicada del ETL; lo único que se escribe es el registro de la solicitud del reporte (RF-059).

## Acceso

Solo usuarios internos con el permiso `REPORTES:CONSULTAR`, que tienen los roles `GERENCIA` y `ANALISTA_RENTAS`.
Se usa la sesión interna:

1. `POST /api/auth/interno/login` con `{"nombreUsuario": "...", "clave": "..."}` deja la cookie de sesión (el
   front debe enviar `credentials: 'include'`).
2. Con esa cookie se llama al endpoint.

| Situación | Respuesta |
| --- | --- |
| Sin sesión interna (o con sesión de contribuyente) | `401` `SESION_REQUERIDA` |
| Sesión con otro rol (por ejemplo `TERMINALISTA` o `ADMINISTRADOR`) | `403` |
| El rol tiene el reporte PRICOS restringido (RF-060) | `403` |
| `limite` fuera de 1 a 200, o `criterio` desconocido | `400` `SOLICITUD_INVALIDA` |
| No hay ninguna carga publicada del ETL con hechos | `422` `SIN_CARGA_ETL` |
| La carga no tiene correspondencia con SAMNET | `422` `ETL_SIN_CORRESPONDENCIA` |
| SAMNET no responde | `503` `SAMNET_NO_DISPONIBLE` |

Los errores usan el contrato del backend: `{ "codigo": "...", "mensaje": "...", "detalles": [] }`.

## Fuente de los datos

El reporte se calcula siempre sobre la última carga `PUBLICADA` de `ps_etl_cobranza` que tenga hechos:

- La emisión, la deuda y la base imponible salen de `analitica.hecho_cobranza` y `analitica.dim_predio`.
- `baseImponible` es la suma de la base de los predios del contribuyente en la carga. No descuenta copropiedad.
- La identidad, el domicilio y el contacto se completan desde SAMNET mediante `maestros.correspondencia_origen`;
  cada reidentificación queda auditada (RNF-018).
- Si no hay una carga publicada con hechos: `422` `SIN_CARGA_ETL`.
- Si la carga no tiene correspondencia con SAMNET: `422` `ETL_SIN_CORRESPONDENCIA`.
- `totalContribuyentes` cuenta los contribuyentes con hechos en la carga, antes de aplicar `limite`.

## Endpoint

```
GET /api/reportes/pricos?criterio={BASE_IMPONIBLE|DEUDA_TOTAL}&limite={1 a 200}
```

| Parámetro | Por defecto | Significado |
| --- | --- | --- |
| `criterio` | `BASE_IMPONIBLE` | Cómo se ordena y se elige a los principales. `BASE_IMPONIBLE`: mayor base imponible de sus predios. `DEUDA_TOTAL`: mayor deuda (años anteriores más año de corte). |
| `limite` | `10` | Cantidad de contribuyentes que devuelve el reporte. |

**El criterio para considerar a un contribuyente como principal sigue pendiente de la Gerencia de Rentas.** Los
dos criterios son provisionales; cuando la Gerencia lo defina se ajusta solo `CriterioPricos` y el orden del
servicio, sin cambiar el contrato.

### Respuesta 200

```json
{
  "idLote": 2,
  "fechaCorte": "2026-10-19",
  "criterio": "BASE_IMPONIBLE",
  "limite": 10,
  "totalContribuyentes": 6,
  "filas": [
    {
      "posicion": 1,
      "codigo": "021390",
      "razonSocial": "INVERSIONES COSTA AZUL S.A.C.",
      "regimen": "GENERAL",
      "documento": { "tipo": "RUC", "numero": "20456781239" },
      "domicilioFiscal": {
        "direccion": "Jr. Los Precursores 220, San Miguel",
        "via": "Jr. Los Precursores",
        "numero": "220",
        "departamento": "LIMA",
        "interior": null,
        "manzana": null,
        "lote": null,
        "distrito": "SAN MIGUEL"
      },
      "baseImponible": 4270000.00,
      "emisionAnioCorte": { "predial": 2040.48, "arbitrios": 12707.28 },
      "deudaAniosAnteriores": { "predial": 992.16, "arbitrios": 1267.76 },
      "deudaAnioCorte": { "predial": 2040.48, "arbitrios": 12707.28, "porcentajeDescuentoArbitrios": 0.00 },
      "condicion": "HABIDO",
      "tipoPersona": "JURIDICA",
      "correo": "cobranzas@costaazul.test",
      "telefonos": "014500202"
    }
  ]
}
```

(`filas` se acorta aquí; la respuesta real trae hasta `limite` contribuyentes, con `posicion` desde 1.)

### Campos

| Campo | Significado |
| --- | --- |
| `fechaCorte` | Fecha de la última carga publicada del ETL. Debe mostrarse en pantalla (RF-008). |
| `totalContribuyentes` | Contribuyentes considerados antes de aplicar `limite`. |
| `baseImponible` | Suma de la base imponible de los predios del contribuyente en la carga; no descuenta copropiedad. |
| `emisionAnioCorte` | Monto emitido en el año de la fecha de corte, de predial y de arbitrios. |
| `deudaAniosAnteriores` | Insoluto de cuotas vencidas de años anteriores al de corte. |
| `deudaAnioCorte` | Insoluto de cuotas vencidas del año de corte, más el mayor porcentaje de descuento aplicado a los arbitrios. |
| `condicion`, `tipoPersona`, `regimen` | Datos del contribuyente en SAMNET. |

Los montos vienen en soles con dos decimales. Los campos de domicilio que SAMNET no tiene vienen `null`.
Los arbitrios se manejan como un solo tributo; no se separan limpieza, parques y seguridad (ver `HU005_API.md`).

### Qué incluye y qué no

- **Deuda** es el insoluto de las cuotas con vencimiento anterior a la fecha de corte. El insoluto de la carga ya
  descuenta los beneficios aplicados, por eso el porcentaje de descuento es informativo y no se vuelve a restar.
- **Intereses y reajuste** no se incluyen: el SP de SAMNET emulado no los entrega.
- **Usuario y clave de pagos en línea** quedan fuera hasta validarlos con la Gerencia de Rentas (RNF-018).
- **Exportación a Excel y PDF** (RF-008) no forma parte de este endpoint; la solicitud se registra con formato
  `NINGUNO`.
- **Restricciones por zona o segmento** (RF-060) no se aplican por fila, porque un contribuyente puede tener
  predios en varias zonas. Sí se respeta la restricción del reporte completo por rol.

## Datos de prueba

Con la base sintética y la fecha de corte `2026-10-19` (hay que haber publicado una carga del ETL con esa fecha):

| Por `BASE_IMPONIBLE` | Contribuyente | Base imponible |
| --- | --- | --- |
| 1.º | `021390` Inversiones Costa Azul | 4 270 000,00 |
| 2.º | `045067` Centro Educativo Sol de Lima | 1 250 000,00 |
| 3.º | `058934` Medina Cruz, Patricia | 210 000,00 |
| 4.º | `061177` Medina Cruz, Roberto | 210 000,00 |
| 5.º | `014502` García Torres, María Elena | 185 000,00 |
| 6.º | `033218` Ramos Quispe, Víctor Hugo | 142 000,00 |

Por `DEUDA_TOTAL`, `021390` es el primero, con 6 907,68 de predial más 13 975,04 de arbitrios.
