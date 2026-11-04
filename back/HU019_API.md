# HU019 - Recibir documentos en el buzón

Como Contribuyente, ver en un solo lugar las constancias, los estados de cuenta y las respuestas a mis
solicitudes de registro o traspaso de predio y a mis reclamos (RF-030). Cada documento muestra su fecha de envío
y, cuando lo abro por primera vez, queda registrada la fecha y hora de esa lectura. La validez legal de la
notificación no es un requisito.

## Acceso

Solo contribuyentes con sesión propia del sistema. El destinatario sale de la sesión, nunca de la petición
(RNF-023): cada contribuyente ve únicamente su buzón.

1. Iniciar sesión (HU041) deja la cookie de sesión; el front debe enviar `credentials: 'include'`.
2. Con esa cookie se llama a los endpoints.

| Situación | Respuesta |
| --- | --- |
| Sin sesión de contribuyente | `401` `SESION_REQUERIDA` |
| El documento no existe o es de otro contribuyente | `404` `DOCUMENTO_NO_ENCONTRADO` |
| SAMNET no responde | `503` `SAMNET_NO_DISPONIBLE` |

Los errores usan el contrato del backend: `{ "codigo": "...", "mensaje": "...", "detalles": [] }`.
Un documento ajeno se responde igual que uno inexistente, sin revelar que existe.

## Endpoints

### Listar el buzón

```
GET /api/buzon
```

No registra lecturas. Los documentos vienen del más reciente al más antiguo.

```json
{
  "total": 2,
  "sinLeer": 1,
  "documentos": [
    {
      "idDocumento": 12,
      "tipo": "ESTADO_CUENTA",
      "titulo": "Estado de cuenta 2026-10",
      "archivo": null,
      "fechaEnvio": "2026-10-15T16:30:00",
      "fechaPrimeraLectura": null,
      "leido": false
    },
    {
      "idDocumento": 9,
      "tipo": "CONSTANCIA",
      "titulo": "Constancia de No Adeudo CNA-2026-000004",
      "archivo": "constancias/CNA-2026-000004.pdf",
      "fechaEnvio": "2026-10-10T09:00:00",
      "fechaPrimeraLectura": "2026-10-11T08:15:00",
      "leido": true
    }
  ]
}
```

Un buzón sin documentos devuelve `total` y `sinLeer` en `0` y `documentos` vacío.

### Abrir un documento

```
GET /api/buzon/{idDocumento}
```

Devuelve el mismo objeto de un elemento de `documentos`. **Registra la primera lectura**: si
`fechaPrimeraLectura` era `null`, queda con la fecha y hora actuales; las aperturas siguientes no la cambian.
Es la llamada que debe hacer la pantalla cuando el contribuyente abre el documento, no al mostrar la lista.

### Campos

| Campo | Significado |
| --- | --- |
| `tipo` | `CONSTANCIA`, `ESTADO_CUENTA`, `RESPUESTA_SOLICITUD` o `RESPUESTA_RECLAMO`. |
| `titulo` | Texto a mostrar en la lista. |
| `archivo` | Ruta del archivo del documento, o `null` si todavía no tiene archivo adjunto. |
| `fechaEnvio` | Fecha y hora en que se depositó el documento en el buzón. |
| `fechaPrimeraLectura` | Fecha y hora de la primera apertura; `null` si no se abrió. |
| `leido` | `true` si ya tiene `fechaPrimeraLectura`. |
| `sinLeer` | Cantidad de documentos con `leido` en `false`; sirve para la insignia del buzón. |

## Datos de prueba

La app móvil incorpora la lista y el detalle del buzón. Las respuestas incluyen además
`contenido` (texto de la respuesta de solicitud o reclamo, cuando existe) y `urlDescarga`
(ruta relativa de descarga protegida, solo si la constancia tiene PDF persistido).

`GET /api/buzon/{idDocumento}/descarga` entrega ese PDF con `Cache-Control: no-store`.
Exige la misma sesión y propiedad que la apertura; un archivo no disponible devuelve
`404 ARCHIVO_NO_DISPONIBLE`. Nunca resuelve directamente rutas guardadas en `archivo`.
La primera lectura se protege con bloqueo de fila para conservarla ante aperturas simultáneas.

La base sintética no trae documentos en el buzón: se crean al emitir constancias o responder solicitudes. Para
probar a mano hay que insertar filas en `operativo.documento_buzon` con `documento_contribuyente` igual al
DNI/RUC del contribuyente (por ejemplo `41028765` para el código `014502`).
