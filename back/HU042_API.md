# HU042 — API de Emisión de Constancia de No Adeudo en Ventanilla

Como trabajador de **Ventanilla**, emitir la Constancia de No Adeudo con el DNI del contribuyente, para atenderlo presencialmente (**HU042**, **RF-055**). El flujo se ejecuta por pasos, igual que en el portal (**HU020**): el trabajador crea la solicitud, el contribuyente firma en el dispositivo presencial, se verifica la deuda, se genera la orden de pago, se confirma el cobro presencial y se emite la constancia.

---

## 1. Alcance y Requisitos del Negocio

### Criterios de Aceptación (HU042)
1. **Flujo integrado del portal (RF-055, RF-034):** Dado el DNI de un contribuyente, cuando se genera la constancia desde ventanilla, se sigue el mismo flujo del portal: firma de la solicitud, consulta de deuda en el SP de estado de cuenta de SAMNET, cobro del derecho de trámite (S/ 10.00) y emisión. Si la deuda es mayor que 0, se entrega el detalle de la deuda y no se emite la constancia.
2. **Registro del trabajador (RF-055, HU042):** Dado que se emite la constancia, cuando se registra, queda registrado qué trabajador de ventanilla la emitió (en la solicitud, en la constancia emitida y en el texto del documento).
3. **Asignación correlativa automática (RF-055):** El sistema asigna automáticamente el número o ID oficial de la constancia (`CNA-AAAA-NNNNNN`), sin digitación ni copiado a mano.
4. **Elección de predios y fechas (RF-055):** Para contribuyentes con muchos predios o trámites específicos, el trabajador puede indicar los predios comprendidos y el rango de fechas que cubre la constancia.
5. **Firma presencial del contribuyente (RF-021):** La firma del contribuyente se captura con el dispositivo presencial de ventanilla y llega al sistema; el servidor la verifica contra el documento que él mismo generó antes de avanzar.
6. **Firma digital municipal automática (RF-037):** Tras la confirmación del pago del derecho de trámite, el sistema incorpora de forma automática la firma digital oficial de la Municipalidad y su hash criptográfico en la plantilla oficial.
7. **Envío al buzón del contribuyente (RF-030):** La constancia emitida se deposita automáticamente en el buzón de documentos en línea del contribuyente (`operativo.documento_buzon`).
8. **Impresión y entrega física (RF-055):** La constancia se entrega lista para previsualización e impresión directa desde ventanilla mediante descarga en formato PDF binario.

### Secuencia de estados

```text
INICIADA → FIRMADA → DEUDA_VERIFICADA → ORDEN_PAGO_EMITIDA → PAGADA → EMITIDA (o RECHAZADA)
```

Cada paso exige el estado anterior (`SECUENCIA_INVALIDA` si no).

---

## 2. Seguridad y Control de Acceso

- **Roles autorizados:** Usuarios internos con rol `VENTANILLA` (o usuarios con la autoridad `CONSTANCIAS:GESTIONAR`).
- **Autenticación requerida:** Sesión interna iniciada mediante `POST /api/auth/interno/login` con credenciales de personal municipal. El frontend debe enviar `credentials: 'include'` y el token CSRF en las peticiones de escritura.
- **Identificación del trabajador:** El trabajador emisor se toma automáticamente del usuario autenticado en la sesión (`Principal`), garantizando que no pueda ser alterado ni suplantado desde el cliente.

### Respuestas de Acceso y Errores

| Situación | Código HTTP | Código del Backend |
| :--- | :--- | :--- |
| Sin sesión interna | `401 Unauthorized` | `SESION_REQUERIDA` / `TRABAJADOR_NO_IDENTIFICADO` |
| Usuario sin autoridad `CONSTANCIAS:GESTIONAR` | `403 Forbidden` | Acceso denegado |
| Documento o DNI no proporcionado o inválido | `400 Bad Request` | `DOCUMENTO_REQUERIDO` / `SOLICITUD_INVALIDA` |
| Contribuyente no existe en padrón / SAMNET | `404 Not Found` | `CONTRIBUYENTE_NO_ENCONTRADO` |
| Paso fuera de secuencia | `422 Unprocessable Content` | `SECUENCIA_INVALIDA` |
| Firma rechazada | `422 Unprocessable Content` | `FIRMA_INVALIDA` |
| Contribuyente registra deuda pendiente (verificación) | `200 OK` con estado `RECHAZADA` y deuda | — |
| Deuda pendiente al emitir | `422 Unprocessable Content` | `DEUDA_PENDIENTE` |
| Orden no pendiente / pago no confirmado | `422 Unprocessable Content` | `ORDEN_NO_PENDIENTE` / `PAGO_REQUERIDO` |
| Plantilla de constancia no vigente | `422 Unprocessable Content` | `PLANTILLA_NO_DISPONIBLE` |

Los errores siguen el formato estándar:
```json
{
  "codigo": "DEUDA_PENDIENTE",
  "mensaje": "El contribuyente registra deuda pendiente de S/ 150.50. No se puede emitir la constancia de no adeudo; debe regularizar sus pagos.",
  "detalles": []
}
```

---

## 3. Endpoints

La API de ventanilla expone ocho operaciones bajo la ruta `/api/ventanilla/constancias`:

```
1. GET  /api/ventanilla/constancias/consulta?documento={documento}
2. POST /api/ventanilla/constancias/solicitudes
3. GET  /api/ventanilla/constancias/{idSolicitud}/documento-firma
4. POST /api/ventanilla/constancias/{idSolicitud}/firma
5. POST /api/ventanilla/constancias/{idSolicitud}/verificacion
6. GET  /api/ventanilla/constancias/{idSolicitud}/orden-pago
7. POST /api/ventanilla/constancias/{idSolicitud}/pago
8. POST /api/ventanilla/constancias/{idSolicitud}/emitir
9. GET  /api/ventanilla/constancias/{numeroConstancia}/descarga
10. GET /api/ventanilla/constancias[?documento={documento}]
```

---

### Endpoint 1: Consulta Previa del Contribuyente

Permite al trabajador de ventanilla ingresar el DNI, RUC o código del contribuyente para validar su identidad, visualizar sus predios registrados y comprobar su estado de deuda en SAMNET antes de crear la solicitud.

```http
GET /api/ventanilla/constancias/consulta?documento={documento}
```

| Parámetro | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `documento` | `String` | **Sí** | DNI (8 dígitos), RUC (11 dígitos) o código de contribuyente (ej. `014502`). |

Respuesta exitosa (`200 OK`):
```json
{
  "contribuyente": {
    "codigoContribuyente": "014502",
    "tipoDocumento": "DNI",
    "numeroDocumento": "41028765",
    "nombreRazonSocial": "GARCÍA TORRES, MARÍA ELENA"
  },
  "predios": [
    {
      "codigoPredio": "014502-01",
      "numeroCatastral": "150136-001-014",
      "ubicacion": "Av. La Marina 1450, San Miguel",
      "tipoPredio": "URBANO",
      "zona": "Costanera"
    }
  ],
  "deuda": {
    "total": 0.00,
    "obligaciones": [],
    "puedeSolicitar": true
  }
}
```

*Nota para la pantalla:* si `deuda.puedeSolicitar` es `false`, `deuda.total` contiene el importe adeudado y `deuda.obligaciones` lista las cuotas adeudadas. La creación de la solicitud no se bloquea; la solicitud se rechaza en la verificación (endpoint 5).

---

### Endpoint 2: Crear Solicitud

Crea la solicitud en canal `VENTANILLA` con el trabajador autenticado. Si el contribuyente ya tiene una solicitud abierta en ventanilla, se retoma en lugar de duplicar.

```http
POST /api/ventanilla/constancias/solicitudes
```

```json
{ "documentoContribuyente": "41028765" }
```

Respuesta exitosa (`201 Created`):
```json
{
  "idSolicitud": 15,
  "canal": "VENTANILLA",
  "estado": "INICIADA",
  "fechaSolicitud": "2026-10-19T10:30:00",
  "documentoContribuyente": "41028765",
  "tipoDocumento": "DNI",
  "codigoContribuyente": "014502",
  "nombreContribuyente": "GARCÍA TORRES, MARÍA ELENA",
  "motivoRechazo": null,
  "firmaContribuyente": null,
  "ordenPago": null,
  "deuda": null,
  "numeroConstancia": null
}
```

---

### Endpoint 3: Documento para Firmar

Documento de la solicitud generado por el servidor, que se muestra en el dispositivo de firma presencial.

```http
GET /api/ventanilla/constancias/{idSolicitud}/documento-firma
```

Respuesta exitosa (`200 OK`):
```json
{
  "idSolicitud": 15,
  "nombreArchivo": "solicitud-constancia-15.txt",
  "tipoContenido": "text/plain; charset=UTF-8",
  "contenidoBase64": "...",
  "hashSha256": "…64 hex…",
  "documentoDePrueba": true,
  "datosContribuyente": {
    "codigo": "014502",
    "nombre": "GARCÍA TORRES, MARÍA ELENA",
    "tipoDocumento": "DNI",
    "numeroDocumento": "41028765"
  }
}
```

---

### Endpoint 4: Registrar Firma

La firma capturada en el dispositivo presencial llega al sistema; el servidor la verifica contra el documento que él mismo generó (`RNF-029`). Registra `FirmaDigital` de tipo `CONTRIBUYENTE` y pasa la solicitud a `FIRMADA`.

```http
POST /api/ventanilla/constancias/{idSolicitud}/firma
```

```json
{ "firmaBase64": "…", "certificado": "CERTIFICADO DE PRUEBA" }
```

Respuesta exitosa (`200 OK`): la solicitud con `estado = "FIRMADA"` y `firmaContribuyente` informado.

Con firma rechazada: `422 FIRMA_INVALIDA`.

---

### Endpoint 5: Verificar Deuda y Emitir Orden de Pago

Consulta la deuda en SAMNET. Con deuda 0 crea la orden de pago del derecho de trámite (S/ 10.00) y pasa a `ORDEN_PAGO_EMITIDA`; con deuda, la solicitud queda `RECHAZADA` y la respuesta lleva el detalle de lo pendiente.

```http
POST /api/ventanilla/constancias/{idSolicitud}/verificacion
```

Respuesta exitosa (`200 OK`):
```json
{
  "idSolicitud": 15,
  "canal": "VENTANILLA",
  "estado": "ORDEN_PAGO_EMITIDA",
  "fechaSolicitud": "2026-10-19T10:30:00",
  "documentoContribuyente": "41028765",
  "tipoDocumento": "DNI",
  "codigoContribuyente": "014502",
  "nombreContribuyente": "GARCÍA TORRES, MARÍA ELENA",
  "motivoRechazo": null,
  "firmaContribuyente": { "entidadCertificadora": "…", "fechaFirma": "…", "hashDocumento": "…" },
  "ordenPago": {
    "idTransaccion": 7,
    "monto": 10.00,
    "estado": "INICIADA",
    "fechaEmision": "2026-10-19T10:35:00",
    "comprobante": null,
    "urlPasarela": null,
    "checkout": null
  },
  "deuda": { "total": 0.00, "obligaciones": [], "puedeSolicitar": true },
  "numeroConstancia": null
}
```

Respuesta con deuda (`200 OK`): `estado = "RECHAZADA"`, `motivoRechazo` con el importe y `deuda.obligaciones` con el detalle.

---

### Endpoint 6: Consultar Orden de Pago

Datos de la orden para mostrarla o imprimirla en ventanilla.

```http
GET /api/ventanilla/constancias/{idSolicitud}/orden-pago
```

Respuesta exitosa (`200 OK`): el objeto `ordenPago` del endpoint 5. `404 ORDEN_NO_ENCONTRADA` si aún no se genera.

---

### Endpoint 7: Confirmar Pago Presencial

El trabajador confirma que recibió el pago del derecho de trámite en ventanilla; la orden queda `CONFIRMADA` con comprobante `CONST-VENT-{id}` y la solicitud pasa a `PAGADA`.

```http
POST /api/ventanilla/constancias/{idSolicitud}/pago
```

Respuesta exitosa (`200 OK`): la solicitud con `estado = "PAGADA"` y `ordenPago.estado = "CONFIRMADA"`.

---

### Endpoint 8: Emitir la Constancia

Con la solicitud `PAGADA`, vuelve a exigir deuda 0, genera el PDF con la plantilla vigente, lo firma como Municipalidad, asigna el correlativo, registra la constancia con el trabajador emisor y la deposita en el buzón.

```http
POST /api/ventanilla/constancias/{idSolicitud}/emitir
```

| Campo | Tipo | Requerido | Descripción / Validación |
| :--- | :--- | :--- | :--- |
| `predios` | `List<String>` | Opcional | Lista de códigos de predios seleccionados por el trabajador. |
| `prediosTexto` | `String` | Opcional | Texto libre opcional descriptivo de predios (máx. 400 caracteres). Si se omiten ambos, aplica `"Todos los predios del contribuyente"`. |
| `rangoFechas` | `String` | Opcional | Rango de fechas cubierto (máx. 60 caracteres). Si se omite, aplica `"Hasta la fecha de emisión"`. |

Respuesta exitosa (`201 Created`):
```json
{
  "numeroConstancia": "CNA-2026-000001",
  "idSolicitud": 15,
  "documentoContribuyente": "41028765",
  "nombreContribuyente": "GARCÍA TORRES, MARÍA ELENA",
  "codigoContribuyente": "014502",
  "fechaEmision": "2026-10-19T10:30:00",
  "prediosIncluidos": "Todos los predios del contribuyente",
  "rangoFechas": "Hasta la fecha de emisión",
  "trabajadorEmisor": "ventanilla",
  "nombreTrabajadorEmisor": "Trabajador de Ventanilla de Pruebas",
  "canal": "VENTANILLA",
  "estado": "EMITIDA",
  "comprobantePago": "CONST-VENT-7",
  "montoDerechoTramite": 10.00,
  "esDePrueba": true
}
```

---

### Endpoint 9: Descarga e Impresión del PDF Firmado

```http
GET /api/ventanilla/constancias/{numeroConstancia}/descarga
```

Respuesta exitosa (`200 OK`): `Content-Type: application/pdf`, `Content-Disposition: inline` con el PDF firmado.

---

### Endpoint 10: Listado de Constancias Emitidas

```http
GET /api/ventanilla/constancias[?documento={documento}]
```

| Parámetro | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `documento` | `String` | Opcional | DNI o RUC del contribuyente. Si se omite, devuelve las últimas 50 constancias emitidas ordenadas descendentemente por fecha. |

---

## 4. Datos de Prueba

| Usuario Interno | Rol | Clave | Propósito |
| :--- | :--- | :--- | :--- |
| `ventanilla` | `VENTANILLA` | `ventanilla123` | Atención presencial en ventanilla con autoridad `CONSTANCIAS:GESTIONAR`. |

| Documento de Prueba | Tipo | Situación en SAMNET | Comportamiento esperado en HU042 |
| :--- | :--- | :--- | :--- |
| `41028765` (Código `014502`) | DNI | Deuda S/ 0.00 | **Emisión exitosa**: consulta con `puedeSolicitar: true`, firma simulada y flujo por pasos hasta `EMITIDA`. |
| `20456781239` (Código `021390`) | RUC | Deuda pendiente | **Bloqueo por deuda**: la verificación rechaza la solicitud con el detalle de las obligaciones. |
| `00000000` | DNI | No registrado | Retorna `404 CONTRIBUYENTE_NO_ENCONTRADO`. |

---

## 5. Trazabilidad con Tablas de Base de Datos

1. **`operativo.solicitud_constancia`:**
   - Se crea con `canal = 'VENTANILLA'`, `estado = 'INICIADA'`, `documento_contribuyente` y `id_trabajador` apuntando al usuario de ventanilla autenticado; `id_operador` se registra en la verificación y en la emisión.
2. **`operativo.firma_digital`:**
   - Registra la firma del contribuyente capturada en el dispositivo presencial (`tipo_firmante = 'CONTRIBUYENTE'`) verificada contra el documento del servidor, y la firma municipal de la constancia.
3. **`operativo.transaccion_pago`:**
   - Registra el cobro de S/ 10.00 por concepto de derecho de trámite (`motivo = 'DERECHO_TRAMITE'`), confirmado en ventanilla con comprobante `CONST-VENT-{id}`.
4. **`operativo.constancia`:**
   - Almacena el número correlativo generado, la versión de plantilla, los predios y fechas parametrizados, el archivo PDF firmado en `contenido_pdf` y la clave foránea `id_trabajador_emisor`.
5. **`operativo.documento_buzon`:**
   - Deposita una copia del documento vinculado al DNI del contribuyente con fecha y hora de emisión para su posterior consulta y descarga en el portal o app móvil (**RF-030**).
