# HU042 — API de Emisión de Constancia de No Adeudo en Ventanilla

Como trabajador de **Ventanilla**, emitir la Constancia de No Adeudo con el DNI del contribuyente, para atenderlo presencialmente (**HU042**, **RF-055**).

---

## 1. Alcance y Requisitos del Negocio

### Criterios de Aceptación (HU042)
1. **Flujo integrado del portal (RF-055, RF-034):** Dado el DNI de un contribuyente, cuando se genera la constancia desde ventanilla, se sigue el mismo flujo del portal: consulta de deuda en el SP de estado de cuenta de SAMNET, cobro del derecho de trámite (S/ 10.00) y emisión. Si la deuda es mayor que 0, se entrega el detalle de la deuda y no se emite la constancia.
2. **Registro del trabajador (RF-055, HU042):** Dado que se emite la constancia, cuando se registra, queda registrado qué trabajador de ventanilla la emitió (en la solicitud, en la constancia emitida y en el texto del documento).
3. **Asignación correlativa automática (RF-055):** El sistema asigna automáticamente el número o ID oficial de la constancia (`CNA-AAAA-NNNNNN`), sin digitación ni copiado a mano.
4. **Elección de predios y fechas (RF-055):** Para contribuyentes con muchos predios o trámites específicos, el trabajador puede indicar los predios comprendidos y el rango de fechas que cubre la constancia.
5. **Firma digital municipal automática (RF-037):** Tras la confirmación del pago del derecho de trámite, el sistema incorpora de forma automática la firma digital oficial de la Municipalidad y su hash criptográfico en la plantilla oficial.
6. **Envío al buzón del contribuyente (RF-030):** La constancia emitida se deposita automáticamente en el buzón de documentos en línea del contribuyente (`operativo.documento_buzon`).
7. **Impresión y entrega física (RF-055):** La constancia se entrega lista para previsualización e impresión directa desde ventanilla mediante descarga en formato PDF binario.

---

## 2. Seguridad y Control de Acceso

- **Roles autorizados:** Usuarios internos con rol `VENTANILLA` (o usuarios con la autoridad `CONSTANCIAS:GESTIONAR`).
- **Autenticación requerida:** Sesión interna iniciada mediante `POST /api/auth/interno/login` con credenciales de personal municipal. El frontend debe enviar `credentials: 'include'`.
- **Identificación del trabajador:** El trabajador emisor se toma automáticamente del usuario autenticado en la sesión (`Principal`), garantizando que no pueda ser alterado ni suplantado desde el cliente.

### Respuestas de Acceso y Errores

| Situación | Código HTTP | Código del Backend |
| :--- | :--- | :--- |
| Sin sesión interna | `401 Unauthorized` | `SESION_REQUERIDA` / `TRABAJADOR_NO_IDENTIFICADO` |
| Usuario sin autoridad `CONSTANCIAS:GESTIONAR` | `403 Forbidden` | Acceso denegado |
| Documento o DNI no proporcionado o inválido | `400 Bad Request` | `DOCUMENTO_REQUERIDO` / `SOLICITUD_INVALIDA` |
| Contribuyente no existe en padrón / SAMNET | `404 Not Found` | `CONTRIBUYENTE_NO_ENCONTRADO` |
| Contribuyente registra deuda pendiente | `422 Unprocessable Content` | `DEUDA_PENDIENTE` |
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

La API de ventanilla expone cuatro operaciones bajo la ruta `/api/ventanilla/constancias`:

```
1. GET  /api/ventanilla/constancias/consulta?documento={documento}
2. POST /api/ventanilla/constancias/emitir
3. GET  /api/ventanilla/constancias/{numeroConstancia}/descarga
4. GET  /api/ventanilla/constancias[?documento={documento}]
```

---

### Endpoint 1: Consulta Previa del Contribuyente

Permite al trabajador de ventanilla ingresar el DNI, RUC o código del contribuyente para validar su identidad, visualizar sus predios registrados para selección y comprobar su estado de deuda en SAMNET antes de proceder a la emisión.

```http
GET /api/ventanilla/constancias/consulta?documento={documento}
```

#### Parámetros de consulta (Query Params)

| Parámetro | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `documento` | `String` | **Sí** | DNI (8 dígitos), RUC (11 dígitos) o código de contribuyente (ej. `014502`). |

#### Respuesta Exitosa (`200 OK`)

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
      "codigoPredio": "PRD-014502-01",
      "numeroCatastral": "150136001004001001",
      "ubicacion": "Av. La Marina 1500 Dpto. 401, San Miguel",
      "tipoPredio": "URBANO",
      "zona": "ZONA 1"
    }
  ],
  "deuda": {
    "total": 0.00,
    "puedeSolicitar": true,
    "obligacionesPendientes": []
  }
}
```

*Nota para la pantalla:* Si `deuda.puedeSolicitar` es `false`, `deuda.total` contiene el importe adeudado y `deuda.obligacionesPendientes` lista las cuotas adeudadas, inhabilitando el botón de emisión presencial.

---

### Endpoint 2: Emisión Presencial de la Constancia

Ejecuta el flujo completo de emisión presencial: registra la solicitud en canal `VENTANILLA`, cobra y confirma el derecho de trámite (S/ 10.00), genera el número correlativo anual oficial, plasma los predios y fechas seleccionados, incorpora la firma digital de la Municipalidad, deposita el documento en el buzón del contribuyente y registra al trabajador emisor.

```http
POST /api/ventanilla/constancias/emitir
Content-Type: application/json
```

#### Cuerpo de la Petición (`EmitirConstanciaVentanillaSolicitud`)

```json
{
  "documentoContribuyente": "41028765",
  "predios": [
    "PRD-014502-01"
  ],
  "prediosTexto": null,
  "rangoFechas": "Periodo 2024 - 2026"
}
```

#### Campos del Request

| Campo | Tipo | Requerido | Descripción / Validación |
| :--- | :--- | :--- | :--- |
| `documentoContribuyente` | `String` | **Sí** | DNI o documento del contribuyente (máx. 12 caracteres). |
| `predios` | `List<String>` | Opcional | Lista de códigos de predios seleccionados por el trabajador. |
| `prediosTexto` | `String` | Opcional | Texto libre opcional descriptivo de predios (máx. 400 caracteres). Si se omiten ambos, aplica `"Todos los predios del contribuyente"`. |
| `rangoFechas` | `String` | Opcional | Rango de fechas cubierto (máx. 60 caracteres). Si se omite, aplica `"Hasta la fecha de emisión"`. |

#### Respuesta Exitosa (`201 Created`)

```json
{
  "numeroConstancia": "CNA-2026-000001",
  "idSolicitud": 15,
  "documentoContribuyente": "41028765",
  "nombreContribuyente": "GARCÍA TORRES, MARÍA ELENA",
  "codigoContribuyente": "014502",
  "fechaEmision": "2026-10-19T10:30:00",
  "prediosIncluidos": "PRD-014502-01",
  "rangoFechas": "Periodo 2024 - 2026",
  "trabajadorEmisor": "ventanilla",
  "nombreTrabajadorEmisor": "Trabajador de Ventanilla de Pruebas",
  "canal": "VENTANILLA",
  "estado": "EMITIDA",
  "comprobantePago": "CONST-VENT-15",
  "montoDerechoTramite": 10.00,
  "esDePrueba": true
}
```

---

### Endpoint 3: Descarga e Impresión del PDF Firmado

Entrega el documento oficial generado con la plantilla municipal vigente, conteniendo los predios, el rango de fechas, el trabajador que atendió en ventanilla y la firma digital municipal (o sello simulado si se ejecuta en ambiente de pruebas, RNF-015).

```http
GET /api/ventanilla/constancias/{numeroConstancia}/descarga
```

#### Parámetros de Ruta

| Parámetro | Tipo | Descripción |
| :--- | :--- | :--- |
| `numeroConstancia` | `String` | Número oficial de la constancia (ej. `CNA-2026-000001`). |

#### Respuesta Exitosa (`200 OK`)
- **Headers:**
  - `Content-Type: application/pdf`
  - `Content-Disposition: inline; filename="CNA-2026-000001.pdf"`
- **Body:** Binario del documento PDF listo para visualización e impresión en la impresora de ventanilla.

---

### Endpoint 4: Listado de Constancias Emitidas

Permite consultar el historial de constancias emitidas en ventanilla para fines de auditoría, seguimiento o reimpresión de comprobantes.

```http
GET /api/ventanilla/constancias[?documento={documento}]
```

#### Parámetros de Consulta (Query Params)

| Parámetro | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `documento` | `String` | Opcional | DNI o RUC del contribuyente. Si se omite, devuelve las últimas 50 constancias emitidas ordenadas descendentemente por fecha. |

#### Respuesta Exitosa (`200 OK`)

Retorna un arreglo JSON con los registros emitidos en la estructura de `ConstanciaVentanillaRespuesta`.

---

## 4. Datos de Prueba

Utilizar los siguientes datos en el entorno de desarrollo y pruebas:

| Usuario Interno | Rol | Clave | Propósito |
| :--- | :--- | :--- | :--- |
| `ventanilla` | `VENTANILLA` | `ventanilla123` | Atención presencial en ventanilla con autoridad `CONSTANCIAS:GESTIONAR`. |

| Documento de Prueba | Tipo | Situación en SAMNET | Comportamiento esperado en HU042 |
| :--- | :--- | :--- | :--- |
| `41028765` (Código `014502`) | DNI | Deuda S/ 0.00 | **Emisión exitosa**: consulta con `puedeSolicitar: true` y emisión con `201 Created`. |
| `20456781239` (Código `021390`) | RUC | Deuda pendiente S/ 6,907.68 | **Bloqueo por deuda**: consulta con `puedeSolicitar: false` y emisión rechazada con `422 DEUDA_PENDIENTE`. |
| `00000000` | DNI | No registrado | Retorna `404 CONTRIBUYENTE_NO_ENCONTRADO`. |

---

## 5. Trazabilidad con Tablas de Base de Datos

1. **`operativo.solicitud_constancia`:**
   - Registra el registro con `canal = 'VENTANILLA'`, `estado = 'EMITIDA'`, `documento_contribuyente` y la clave foránea `id_trabajador` apuntando al usuario de ventanilla autenticado.
2. **`operativo.constancia`:**
   - Almacena el número correlativo generado, la versión de plantilla, los predios y fechas parametrizados, el archivo PDF firmado en `contenido_pdf` y la clave foránea `id_trabajador_emisor`.
3. **`operativo.transaccion_pago`:**
   - Registra el cobro de S/ 10.00 por concepto de derecho de trámite (`motivo = 'DERECHO_TRAMITE'`), confirmado de inmediato con comprobante `CONST-VENT-{id}`.
4. **`operativo.documento_buzon`:**
   - Deposita una copia del documento vinculado al DNI del contribuyente con fecha y hora de emisión para su posterior consulta y descarga en el portal o app móvil (**RF-030**).

