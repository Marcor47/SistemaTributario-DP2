# Guía de la API para el frontend: HU001, HU009 y HU013

Backend: `red-013` · Issues de backend: #56 (HU001), #62 (HU009), #66 (HU013)
Pantallas que la consumen: #57 (HU001), #63 (HU009), #67 (HU013)

> Todos los nombres, documentos y montos de los ejemplos son **ficticios** y coinciden con los mocks de `frontweb/src/mocks`.

## 1. Cómo levantar el backend

1. PostgreSQL con la BD y el usuario del README (`municipalidad` / `municipalidad`).
2. `cd back` y `mvnw.cmd spring-boot:run`.
3. Comprobar con `http://localhost:8080/api/health`.

Al arrancar, Flyway crea las tablas y carga los datos ficticios de demostración. No hay que ejecutar ningún SQL a mano.

- **URL base:** `${import.meta.env.VITE_API_URL}`, que en desarrollo es `http://localhost:8080/api`.
- **CORS:** ya permite `http://localhost:5173` con GET, POST, PUT, DELETE y OPTIONS.
- **Formatos:** todas las respuestas son JSON. Las fechas van como `"2026-10-19"` y las fechas con hora como `"2026-10-19T12:00:00"`. Los montos son números con hasta 2 decimales.

## 2. Formato único de error

Cualquier error de cualquier endpoint llega con esta forma:

```json
{
  "codigo": "CONTRIBUYENTE_NO_ENCONTRADO",
  "mensaje": "No existe un contribuyente registrado con DNI 99999999.",
  "detalles": []
}
```

| Campo | Para qué sirve |
|---|---|
| `codigo` | Es estable: decide la lógica de la pantalla con este campo, nunca con el mensaje. |
| `mensaje` | Ya está redactado en español para el usuario final; se puede mostrar tal cual. |
| `detalles` | Solo trae datos en errores de validación (400): un texto por campo, con la forma `"campo: problema"`. |

| HTTP | Cuándo ocurre |
|---|---|
| 400 | Datos con formato inválido (`SOLICITUD_INVALIDA`, `DOCUMENTO_INVALIDO`, `PERIODO_INVALIDO`, `VALOR_META_FUERA_DE_RANGO`) |
| 404 | El recurso no existe |
| 409 | Ya existe (`META_DUPLICADA`) |
| 422 | Una regla de negocio lo impide (`PERIODO_CERRADO`, `INDICADOR_INACTIVO`, `OBLIGACION_NO_VENCIDA`) |
| 500 | `ERROR_INTERNO`: error inesperado; mostrar el mensaje y permitir reintentar |

Ejemplo de servicio que ya maneja el error. Se puede copiar en `services/` y reemplazar el mock:

```js
const API = import.meta.env.VITE_API_URL

async function pedir(ruta, opciones = {}) {
  const respuesta = await fetch(`${API}${ruta}`, {
    headers: { 'Content-Type': 'application/json' },
    ...opciones,
  })
  const cuerpo = await respuesta.json()
  if (!respuesta.ok) {
    // cuerpo = { codigo, mensaje, detalles }
    throw Object.assign(new Error(cuerpo.mensaje), cuerpo, { estado: respuesta.status })
  }
  return cuerpo
}
```

## 3. HU001: consultar contribuyente por documento (#57)

### `GET /api/contribuyentes?tipoDocumento={tipo}&numeroDocumento={numero}`

| Parámetro | Obligatorio | Valores |
|---|---|---|
| `tipoDocumento` | Sí | `DNI` (8 dígitos), `RUC` (11 dígitos que empiezan con 10, 15, 17 o 20), `CE` (9 a 12 caracteres alfanuméricos) |
| `numeroDocumento` | Sí | El backend quita espacios y pasa a mayúsculas, así que `" 0012 34567 "` funciona igual. |

La búsqueda es **exacta**: tipo más número. El mismo número con otro tipo de documento no encuentra al contribuyente.

**200**: `GET /api/contribuyentes?tipoDocumento=DNI&numeroDocumento=41028765`

```json
{
  "codigo": "014502",
  "nombre": "GARCÍA TORRES, MARÍA ELENA",
  "tipoPersona": "NATURAL",
  "tipoDocumento": "DNI",
  "numeroDocumento": "41028765",
  "domicilioFiscal": "Av. La Marina 1450, San Miguel",
  "estado": "ACTIVO"
}
```

| Campo | Valores |
|---|---|
| `tipoPersona` | `NATURAL` · `JURIDICA` |
| `estado` | `ACTIVO` · `INACTIVO`. A un contribuyente inactivo también se le devuelve 200; conviene mostrar el estado en la pantalla. |

**Diferencia de nombres con el mock actual (`mocks/predios.js`):**

| Mock | API |
|---|---|
| `tipoDoc` | `tipoDocumento` |
| `doc` | `numeroDocumento` |

**Errores:**

| HTTP | `codigo` | Caso | Sugerencia de UI |
|---|---|---|---|
| 400 | `DOCUMENTO_INVALIDO` | El número no cumple el formato del tipo. Ej.: `"El DNI debe tener 8 dígitos."` | Mostrar el mensaje bajo el campo |
| 400 | `SOLICITUD_INVALIDA` | Falta un parámetro o `tipoDocumento` no existe | Validar en el formulario antes de enviar |
| 404 | `CONTRIBUYENTE_NO_ENCONTRADO` | No hay contribuyente con ese documento | Estado vacío: «No se encontró…» |

**Datos ficticios para probar:**

| Tipo | Número | Contribuyente |
|---|---|---|
| DNI | 41028765 | GARCÍA TORRES, MARÍA ELENA (014502) |
| RUC | 20456781239 | INVERSIONES COSTA AZUL S.A.C. (021390) |
| DNI | 09215430 | RAMOS QUISPE, VÍCTOR HUGO (033218) |
| RUC | 20512309876 | CENTRO EDUCATIVO SOL DE LIMA E.I.R.L. (045067) |
| DNI | 45871203 | MEDINA CRUZ, PATRICIA (058934) |
| DNI | 45871777 | MEDINA CRUZ, ROBERTO (061177) |
| CE | 001234567 | LÓPEZ VARGAS, ANDREA (072405, **INACTIVO**) |

## 4. HU009: configurar una meta por indicador (#63)

El flujo de pantalla sugerido es este:
1. Cargar el catálogo de indicadores para el selector.
2. Según la `periodicidad` del indicador elegido, mostrar mes (1 a 12), trimestre (1 a 4) o nada (anual, que siempre usa periodo 1).
3. Listar las metas existentes y permitir crear o editar.

### `GET /api/indicadores`: catálogo para el selector

Devuelve solo los indicadores activos, ordenados por nombre.

```json
[
  {
    "codigo": "INDICE_MOROSIDAD",
    "nombre": "Índice de morosidad",
    "definicion": "Porcentaje de la deuda emitida que se encuentra vencida y sin pagar al cierre del periodo.",
    "formula": "(Saldo vencido al cierre / Monto emitido acumulado) x 100",
    "fuente": "Cuenta corriente SAMNET: obligaciones vencidas",
    "unidadMedida": "PORCENTAJE",
    "periodicidad": "MENSUAL",
    "periodosPorAnio": 12,
    "sentido": "DESCENDENTE"
  }
]
```

| Campo | Valores | Uso en la UI |
|---|---|---|
| `unidadMedida` | `PORCENTAJE` (máximo 100) · `SOLES` · `CANTIDAD` | Sufijo "%" o prefijo "S/" en el input |
| `periodicidad` | `MENSUAL` · `TRIMESTRAL` · `ANUAL` | Qué selector de periodo mostrar |
| `periodosPorAnio` | 12 · 4 · 1 | Rango válido de `numeroPeriodo` |
| `sentido` | `ASCENDENTE` (más es mejor) · `DESCENDENTE` (menos es mejor) | Texto de ayuda: «la meta es un máximo» en el caso de morosidad |

Indicadores del catálogo:

| Código | Indicador | Unidad | Periodicidad |
|---|---|---|---|
| `ACTUALIZACION_PADRON` | Actualización del padrón | % | Anual |
| `INDICE_MOROSIDAD` | Índice de morosidad | % | Mensual |
| `MONTO_RECAUDADO` | Monto recaudado | S/ | Mensual |
| `PORC_RECAUDACION_EMISION` | Recaudación respecto de la emisión | % | Mensual |
| `RECUPERACION_DEUDA` | Recuperación de deuda vencida | % | Trimestral |

### `GET /api/metas?indicadorCodigo={codigo}&anio={anio}`: listado

Los dos filtros son opcionales. El orden es: año descendente, nombre del indicador y número de periodo.

```json
[
  {
    "id": 2,
    "indicadorCodigo": "PORC_RECAUDACION_EMISION",
    "indicadorNombre": "Recaudación respecto de la emisión",
    "unidadMedida": "PORCENTAJE",
    "sentido": "ASCENDENTE",
    "periodicidad": "MENSUAL",
    "anio": 2026,
    "numeroPeriodo": 10,
    "periodo": "2026-10",
    "fechaFinPeriodo": "2026-10-31",
    "valorMeta": 85.00,
    "observacion": null,
    "editable": true,
    "fechaRegistro": "2026-09-20T10:30:00",
    "fechaActualizacion": "2026-09-20T10:30:00"
  }
]
```

- `periodo` ya viene formateado para mostrar: `2026-10` (mensual), `2026-T2` (trimestral) o `2026` (anual).
- `editable` es `false` cuando el periodo ya concluyó. En ese caso hay que deshabilitar el botón de editar, porque el backend respondería 422.

### `GET /api/metas/{id}`: detalle

Devuelve el mismo objeto que cada elemento del listado. Si el id no existe, responde 404 `META_NO_ENCONTRADA`.

### `POST /api/metas`: registrar

```json
{
  "indicadorCodigo": "MONTO_RECAUDADO",
  "anio": 2026,
  "numeroPeriodo": 11,
  "valorMeta": 1250000.50,
  "observacion": "Campaña de noviembre"
}
```

Si sale bien responde **201** con la meta creada (mismo objeto del listado) y la cabecera `Location: /api/metas/{id}`.

| Campo | Regla |
|---|---|
| `indicadorCodigo` | Obligatorio; debe existir y estar activo |
| `anio` | Obligatorio, entre 2000 y 2100 |
| `numeroPeriodo` | Obligatorio, de 1 a `periodosPorAnio` del indicador |
| `valorMeta` | Obligatorio, ≥ 0, máximo 2 decimales; si la unidad es `PORCENTAJE`, ≤ 100 |
| `observacion` | Opcional, hasta 250 caracteres |

Además, el periodo no puede haber concluido. El periodo en curso sí se permite: en octubre se puede registrar la meta de octubre.

### `PUT /api/metas/{id}`: modificar

```json
{ "valorMeta": 1300000, "observacion": "Ajuste" }
```

Solo se cambian `valorMeta` y `observacion`. Si el indicador, el año o el periodo están mal, se registra otra meta. Responde **200** con la meta actualizada.

**Errores de HU009:**

| HTTP | `codigo` | Caso |
|---|---|---|
| 400 | `SOLICITUD_INVALIDA` | Campos faltantes o fuera de rango; ver `detalles`. Ej.: `["anio: debe ser 2000 o posterior", "valorMeta: no puede ser negativo"]` |
| 400 | `PERIODO_INVALIDO` | `numeroPeriodo` fuera del rango de la periodicidad (p. ej., trimestre 5) |
| 400 | `VALOR_META_FUERA_DE_RANGO` | Porcentaje mayor a 100 |
| 404 | `INDICADOR_NO_ENCONTRADO` / `META_NO_ENCONTRADA` | No existe |
| 409 | `META_DUPLICADA` | Ya hay meta para ese indicador y periodo; ofrecer editar la existente |
| 422 | `PERIODO_CERRADO` | El periodo ya concluyó (al registrar o al editar) |
| 422 | `INDICADOR_INACTIVO` | El indicador ya no admite metas |

## 5. HU013: consultar la etapa de cobranza de una obligación vencida (#67)

El flujo de pantalla sugerido es este:
1. Ubicar al contribuyente, reutilizando la búsqueda de HU001.
2. Listar sus obligaciones vencidas con la etapa de cada una.
3. Al elegir una, mostrar el detalle con su línea de tiempo.

### `GET /api/contribuyentes/{codigo}/obligaciones-vencidas`

Ordenadas por fecha de vencimiento (las más antiguas primero) y luego por tributo. Si el contribuyente está al día devuelve `[]`, y si no existe devuelve 404 `CONTRIBUYENTE_NO_ENCONTRADO`.

```json
[
  {
    "id": 37,
    "tributo": "IMPUESTO_PREDIAL",
    "tributoDescripcion": "Impuesto Predial",
    "periodo": "2025-T4",
    "codigoPredio": null,
    "fechaVencimiento": "2025-12-31",
    "saldo": 992.16,
    "diasVencida": 292,
    "etapaActual": "COACTIVA",
    "etapaNombre": "Cobranza coactiva"
  },
  {
    "id": 38,
    "tributo": "LIMPIEZA_PUBLICA",
    "tributoDescripcion": "Limpieza Pública",
    "periodo": "2025-T4",
    "codigoPredio": "021390-01",
    "fechaVencimiento": "2025-12-31",
    "saldo": 523.64,
    "diasVencida": 292,
    "etapaActual": "VALOR_NOTIFICADO",
    "etapaNombre": "Valor notificado"
  }
]
```

- `codigoPredio` es `null` en el Impuesto Predial, que se emite al contribuyente. Los arbitrios sí traen el predio.
- `periodo` usa el mismo formato que el mock de estado de cuenta: `2025-T4`.

### `GET /api/obligaciones/{id}/etapa-cobranza`

```json
{
  "obligacion": {
    "id": 37,
    "contribuyenteCodigo": "021390",
    "contribuyenteNombre": "INVERSIONES COSTA AZUL S.A.C.",
    "tributo": "IMPUESTO_PREDIAL",
    "tributoDescripcion": "Impuesto Predial",
    "periodo": "2025-T4",
    "codigoPredio": null,
    "fechaVencimiento": "2025-12-31",
    "montoEmitido": 992.16,
    "montoPagado": 0.00,
    "saldo": 992.16
  },
  "fechaConsulta": "2026-10-19",
  "diasVencida": 292,
  "etapaActual": {
    "etapa": "COACTIVA",
    "nombre": "Cobranza coactiva",
    "descripcion": "El expediente fue derivado al Ejecutor Coactivo.",
    "fechaInicio": "2026-05-12",
    "fechaFin": null,
    "documentoReferencia": "REC-2026-000045",
    "observacion": "Resolución de ejecución coactiva N.° 45",
    "registrada": true
  },
  "diasEnEtapaActual": 160,
  "historial": [
    { "etapa": "ORDINARIA", "nombre": "Cobranza ordinaria", "fechaInicio": "2026-01-01", "fechaFin": "2026-01-20", "documentoReferencia": null, "registrada": false, "...": "..." },
    { "etapa": "VALOR_EMITIDO", "nombre": "Valor emitido", "fechaInicio": "2026-01-20", "fechaFin": "2026-02-03", "documentoReferencia": "OP-2026-000101", "registrada": true, "...": "..." },
    { "etapa": "VALOR_NOTIFICADO", "nombre": "Valor notificado", "fechaInicio": "2026-02-03", "fechaFin": "2026-05-12", "documentoReferencia": "OP-2026-000101", "registrada": true, "...": "..." },
    { "etapa": "COACTIVA", "nombre": "Cobranza coactiva", "fechaInicio": "2026-05-12", "fechaFin": null, "documentoReferencia": "REC-2026-000045", "registrada": true, "...": "..." }
  ]
}
```

Cada elemento de `historial` tiene los mismos campos que `etapaActual`; arriba se abreviaron con `"..."`. El ejemplo completo está en `ejemplos-respuestas-api.txt`.

**Etapas, en orden de avance:**

| `etapa` | Nombre | Qué significa |
|---|---|---|
| `ORDINARIA` | Cobranza ordinaria | Vencida sin valor emitido: recordatorios y avisos |
| `VALOR_EMITIDO` | Valor emitido | Se emitió orden de pago o resolución de determinación |
| `VALOR_NOTIFICADO` | Valor notificado | Notificado; corre el plazo para pagar o reclamar |
| `COACTIVA` | Cobranza coactiva | Derivado al Ejecutor Coactivo |

**Para la línea de tiempo:**
- `historial` está en orden cronológico y el último elemento siempre es `etapaActual`.
- El tramo actual tiene `fechaFin: null`.
- `registrada: false` aparece solo en el primer tramo, cuando nadie registró la cobranza ordinaria. Toda deuda vencida entra en esa etapa al día siguiente de su vencimiento; se puede mostrar como «automática».
- `documentoReferencia` puede ser `null`.

**Errores:**

| HTTP | `codigo` | Caso |
|---|---|---|
| 404 | `OBLIGACION_NO_ENCONTRADA` | No existe el id |
| 422 | `OBLIGACION_NO_VENCIDA` | Está pagada o aún no vence. Ej.: `"La obligación Impuesto Predial 2025-T1 está pagada; solo las obligaciones vencidas tienen etapa de cobranza."` |
| 400 | `SOLICITUD_INVALIDA` | El id no es numérico |

**Datos ficticios para probar:**

| Contribuyente | Qué muestra |
|---|---|
| `021390` | 12 vencidas. El Predial 2025-T4 llega a **coactiva**, la Limpieza 2025-T4 está en **valor notificado** y el Predial 2026-T1 en **valor emitido**. |
| `058934` | Predial 2026-T1 con **ordinaria registrada** (aviso por correo) que luego pasa a valor emitido |
| `045067` | Vencidas sin etapas registradas, es decir, **ordinaria implícita** |
| `014502`, `033218` | Al día: listado vacío |

> Los ids de las obligaciones los asigna la BD. No hay que fijarlos en el front; se obtienen siempre del listado.
