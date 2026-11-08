# HU012 - Enviar alerta de mora temprana por correo

Como Gerencia de Rentas, quiero que el sistema envíe automáticamente un aviso por correo electrónico a los contribuyentes con cuotas vencidas sin pago, para notificarlos en un plazo máximo de 24 horas (RF-013).

## Reglas de negocio y alcance (RF-013)

1. **Exclusividad de canal (RF-013):** Solo correo electrónico. WhatsApp, SMS e IVR quedan completamente excluidos debido a las políticas tarifarias comerciales de WhatsApp Business API (vigentes desde el 1/10/2026) y restricciones presupuestarias del Plan de Adquisiciones.
2. **Detección en la carga del ETL:** Cada carga del ETL identifica las cuotas con fecha de vencimiento menor o igual a la fecha de corte que mantengan saldo insoluto pendiente.
3. **Filtro por correo registrado:** Los avisos se envían exclusivamente a los contribuyentes que cuentan con correo electrónico registrado en el padrón municipal. Quienes no tienen correo registrado quedan omitidos del envío sin recurrir a canales de mensajería de pago.
4. **Plazo de notificación:** El aviso se genera y programa para entrega inmediata dentro del plazo de 24 horas desde la detección.
5. **Historial del contribuyente:** Todo aviso enviado queda registrado en el historial de notificaciones del contribuyente (`operativo.aviso_correo`) y puede ser consultado por la Gerencia de Rentas o en la ficha del contribuyente.
6. **Dependencia de calidad (HU026 / RF-038):** Solo los lotes del ETL validados por las reglas de calidad (ISO/IEC 25012) y publicados (`PUBLICADO`) pueden generar alertas. Si un lote es rechazado por incumplimiento de reglas de calidad, no se generan alertas ni se afecta la información publicada.
7. **Prevención de duplicidad:** No se envían alertas duplicadas para la misma cuota/obligación en una misma ejecución o lote.

## Acceso y seguridad

Requiere sesión de usuario interno (`POST /api/auth/interno/login`).

- `POST /api/alertas-mora/evaluaciones`: autoridades `ALERTAS:EVALUAR` o `ETL:GESTIONAR` (asignadas a `GERENCIA` y `RESPONSABLE_DATOS`).
- `GET /api/alertas-mora/**`: autoridad `ALERTAS:CONSULTAR`, `CONTRIBUYENTES:CONSULTAR` o `ALERTAS:EVALUAR`.
- `GET /api/contribuyentes/{codigo}/avisos-mora`: autoridad `CONTRIBUYENTES:CONSULTAR`.

---

## 1. Procesar alertas de mora de un lote

```http
POST /api/alertas-mora/evaluaciones
Content-Type: application/json
```

### Cuerpo de la petición (JSON)

```json
{
  "idLote": 1,
  "registros": [
    {
      "id_obligacion": "OBL-7001",
      "contribuyente_codigo": "014502",
      "fecha_vencimiento": "2026-09-30",
      "monto_emitido": 200.00,
      "monto_pagado": 50.00,
      "insoluto": 150.00
    }
  ]
}
```

*Nota: El campo `registros` es opcional. Si se omite, el sistema extrae automáticamente las cuotas desde la función autorizada de SAMNET (`ps_etl_cobranza`) para la fecha de corte del lote.*

### Respuesta 200 OK

```json
{
  "idLote": 1,
  "fechaCorte": "2026-10-19",
  "cuotasEvaluadas": 1,
  "cuotasVencidasDetectadas": 1,
  "alertasEnviadas": 1,
  "sinCorreoRegistrado": 0,
  "alertasFallidas": 0,
  "avisos": [
    {
      "idAviso": 1,
      "tipo": "MORA_TEMPRANA",
      "documentoContribuyente": "41028765",
      "correoDestino": "maria.garcia@correo.test",
      "fechaProgramada": "2026-10-19T12:00:00",
      "fechaEnvio": "2026-10-19T12:00:00",
      "estado": "ENVIADO",
      "idLote": 1,
      "idObligacion": "OBL-7001"
    }
  ]
}
```

---

## 2. Consultar el historial de avisos de un contribuyente

```http
GET /api/contribuyentes/{codigoODocumento}/avisos-mora
```
O también:
```http
GET /api/alertas-mora/contribuyentes/{codigoODocumento}
```

Parámetro de ruta `codigoODocumento`: puede ser el código municipal (ej. `014502`) o el número de documento de identidad (DNI `41028765` o RUC).

### Respuesta 200 OK

```json
[
  {
    "idAviso": 1,
    "tipo": "MORA_TEMPRANA",
    "documentoContribuyente": "41028765",
    "correoDestino": "maria.garcia@correo.test",
    "fechaProgramada": "2026-10-19T12:00:00",
    "fechaEnvio": "2026-10-19T12:00:00",
    "estado": "ENVIADO",
    "idLote": 1,
    "idObligacion": "OBL-7001"
  }
]
```

---

## 3. Consultar avisos generados por lote

```http
GET /api/alertas-mora/lotes/{idLote}
```

Devuelve la lista de avisos por correo vinculados al lote del ETL.

---

## 4. Consultar aviso por ID

```http
GET /api/alertas-mora/{idAviso}
```

### Respuestas de error

| HTTP | Código | Causa |
| --- | --- | --- |
| 401 | `SESION_REQUERIDA` | Falta cookie de sesión interna. |
| 403 | `ACCESO_DENEGADO` | Rol sin permisos requeridos. |
| 404 | `LOTE_NO_ENCONTRADO` | El `idLote` especificado no existe en `analitica.lote_etl`. |
| 404 | `AVISO_NO_ENCONTRADO` | El `idAviso` consultado no existe. |
| 422 | `LOTE_NO_PUBLICADO` | El lote está en estado `RECHAZADO` o `EN_CURSO`; solo los lotes publicados y aprobados por calidad (HU026) generan alertas. |

