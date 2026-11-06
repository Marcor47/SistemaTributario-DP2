# HU007 - Guardar y exportar una vista de reporte

Como Gerencia de Rentas, guardar y exportar la vista personalizada de un reporte (morosos, PRICOS, buenos pagadores)
para reutilizarla sin repetir la configuración (RF-008, RF-044, RF-059, RF-060, RNF-023).

Complementa a la [HU006](HU006_API.md): aplica las mismas restricciones de rol y zonas de SAMNET, y registra cada
exportación en `operativo.solicitud_reporte` con formato `EXCEL` o `PDF`.

## Acceso y seguridad

Solo usuarios internos con rol `GERENCIA` o `ANALISTA_RENTAS`. Se utiliza la cookie de sesión interna obtenida en
`POST /api/auth/interno/login`.

- **Privacidad estricta (RNF-023):** Cada vista pertenece exclusivamente al usuario que la guardó. Ningún otro usuario
  (incluso con el mismo rol) puede verla, abrirla, modificarla o eliminarla (devuelve `404` `VISTA_NO_ENCONTRADA`).
- **Unicidad de nombre:** Un mismo usuario no puede tener dos vistas con el mismo nombre para el mismo tipo de reporte
  (`409` `NOMBRE_VISTA_DUPLICADO`).

| Situación | Código HTTP | Código error |
| --- | --- | --- |
| Sin sesión interna | `401` | `SESION_REQUERIDA` |
| Rol sin permisos (ej. `VENTANILLA`, `ADMINISTRADOR`) | `403` | `ACCESO_DENEGADO` |
| Reporte restringido por rol | `403` | `REPORTE_RESTRINGIDO` |
| Vista no existe o pertenece a otro usuario | `404` | `VISTA_NO_ENCONTRADA` |
| Nombre de vista duplicado para ese usuario y reporte | `409` | `NOMBRE_VISTA_DUPLICADO` |
| Formato de exportación no soportado (distinto a EXCEL o PDF) | `400` | `FORMATO_NO_SOPORTADO` |
| Columna inválida para el reporte | `400` | `COLUMNA_NO_VALIDA` |
| Filtro de año en morosos | `400` | `FILTRO_NO_APLICABLE` |
| SAMNET no disponible | `503` | `SAMNET_NO_DISPONIBLE` |

---

## 1. Guardar una vista personalizada

```
POST /api/reportes/{tipo}/vistas
```

`tipo`: `MOROSOS`, `PRICOS` o `BUENOS_PAGADORES`.

### Petición (Body JSON)

```json
{
  "nombre": "Morosos Zona 2 con Arbitrios",
  "tipoGrafica": "BARRAS",
  "filtros": {
    "zona": "Z2",
    "tributo": "ARBITRIOS",
    "montoMinimo": 1000
  },
  "columnasVisibles": ["codigo", "nombre", "deudaVencida", "cuotasVencidas", "nombreEtapa"],
  "orden": "deudaVencida:desc"
}
```

- `nombre` (obligatorio, 1-80 caracteres): nombre asignado por el usuario.
- `tipoGrafica` (opcional, por defecto `TABLA`): `TABLA`, `BARRAS`, `LINEAS`, `CIRCULAR`.
- `filtros` (opcional): los mismos filtros de la HU006 (`anio`, `zona`, `tributo`, `montoMinimo`).
- `columnasVisibles` (obligatorio, no vacío): lista de columnas a mostrar. Deben ser columnas válidas del reporte.
- `orden` (opcional, máx. 250 caracteres): criterio de ordenación guardado (ej. `deudaVencida:desc`).

### Respuesta `201 Created`

```json
{
  "idVista": 1,
  "tipoReporte": "MOROSOS",
  "nombre": "Morosos Zona 2 con Arbitrios",
  "tipoGrafica": "BARRAS",
  "filtros": {
    "anio": null,
    "zona": "Z2",
    "tributo": "ARBITRIOS",
    "montoMinimo": 1000.00
  },
  "columnasVisibles": ["codigo", "nombre", "deudaVencida", "cuotasVencidas", "nombreEtapa"],
  "orden": "deudaVencida:desc",
  "fechaCreacion": "2026-11-02T00:25:00"
}
```

---

## 2. Listar vistas guardadas

```
GET /api/reportes/vistas
GET /api/reportes/{tipo}/vistas
```

Devuelve la lista de vistas pertenecientes al usuario autenticado, ordenadas por fecha de creación descendente.

---

## 3. Obtener configuración de una vista

```
GET /api/reportes/vistas/{idVista}
```

Devuelve los metadatos de la vista guardada. Solo si pertenece al usuario en sesión.

---

## 4. Abrir y ejecutar una vista guardada

```
GET /api/reportes/vistas/{idVista}/abrir
```

Ejecuta el reporte en tiempo real contra SAMNET aplicando la configuración y filtros guardados en la vista.

### Respuesta `200 OK`

```json
{
  "vista": {
    "idVista": 1,
    "tipoReporte": "MOROSOS",
    "nombre": "Morosos Zona 2 con Arbitrios",
    "tipoGrafica": "BARRAS",
    "filtros": { "anio": null, "zona": "Z2", "tributo": "ARBITRIOS", "montoMinimo": 1000.00 },
    "columnasVisibles": ["codigo", "nombre", "deudaVencida", "cuotasVencidas", "nombreEtapa"],
    "orden": "deudaVencida:desc",
    "fechaCreacion": "2026-11-02T00:25:00"
  },
  "reporte": {
    "tipoReporte": "MOROSOS",
    "titulo": "Reporte de morosos",
    "fechaCorte": "2026-10-19",
    "filtrosAplicados": { "anio": null, "zona": "Z2", "tributo": "ARBITRIOS", "montoMinimo": 1000.00 },
    "resumen": {
      "cantidad": 2,
      "conceptoMonto": "Deuda vencida",
      "montoTotal": 7650.00,
      "umbralPrico": null
    },
    "filas": [ ... ]
  }
}
```

---

## 5. Actualizar una vista guardada

```
PUT /api/reportes/vistas/{idVista}
```

Body JSON con los campos a actualizar (`nombre`, `tipoGrafica`, `filtros`, `columnasVisibles`, `orden`).

---

## 6. Eliminar una vista guardada

```
DELETE /api/reportes/vistas/{idVista}
```

Respuesta `204 No Content`.

---

## 7. Exportar un reporte generado a Excel o PDF

Permite exportar directamente con filtros en línea:

```
GET /api/reportes/{tipo}/exportar?formato=EXCEL&anio=&periodo=&zona=&tributo=&regimen=&tipoDocumento=&etapa=&montoMinimo=&columnas=codigo,nombre,deudaVencida
GET /api/reportes/{tipo}/exportar?formato=PDF&anio=&periodo=&zona=&tributo=&regimen=&tipoDocumento=&etapa=&montoMinimo=
```

Parámetros:
- `formato` (obligatorio): `EXCEL` o `PDF`.
- `anio`, `zona`, `tributo`, `montoMinimo` (opcionales): filtros de reporte.
- `columnas` (opcional): lista separada por comas de columnas a incluir en el archivo. Si se omite, se exportan las columnas predeterminadas del reporte.

---

## 8. Exportar una vista guardada a Excel o PDF

```
GET /api/reportes/vistas/{idVista}/exportar?formato=EXCEL
GET /api/reportes/vistas/{idVista}/exportar?formato=PDF
```

Aplica automáticamente los filtros y columnas guardados en la vista.

### Archivos entregados
- **Excel (`EXCEL`):** `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, nombre `reporte-{tipo}-{fechaCorte}.xlsx`. Formato XLSX con cabecera institucional, metadata, formato de moneda (S/), porcentajes y estilos de celda.
- **PDF (`PDF`):** `application/pdf`, nombre `reporte-{tipo}-{fechaCorte}.pdf`. Formato PDF apaisado con membrete oficial, metadata, tabla con bordes y paginación.
- **Auditoría (RF-059):** Cada exportación guarda un registro en `operativo.solicitud_reporte` con `formato_exportacion = 'EXCEL'` o `'PDF'`.

