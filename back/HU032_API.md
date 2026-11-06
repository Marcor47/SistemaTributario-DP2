# HU032 - Personalizar la visualización de un reporte

Como Gerencia de Rentas, ajustar rangos, tipos de gráfica, filtros y series de un reporte dentro de un conjunto
predefinido de opciones, para analizar la información desde distintas perspectivas sin intervención de TI
(RF-008, RF-044).

Depende de la [HU006](HU006_API.md) (catálogo de reportes) y extiende las vistas de la [HU007](HU007_API.md): la
personalización se guarda en la misma vista (`tipoGrafica`, `series`, `filtros`). Las gráficas se dibujan en
frontweb con las filas del reporte; el backend define qué opciones existen y valida que la vista guardada las respete.

## Acceso y seguridad

Solo usuarios internos con rol `GERENCIA` o `ANALISTA_RENTAS`, con la cookie de sesión interna.

- **Permiso `REPORTES:PERSONALIZAR`** (migración `V32`, asignado a `GERENCIA`): necesario para guardar una vista con
  una gráfica distinta de `TABLA`. Sin él, las opciones solo ofrecen `TABLA`.
- **Restricciones por rol (RF-060):** las zonas ofrecidas y la zona de los filtros guardados respetan
  `operativo.restriccion_reporte`.

| Situación | Código HTTP | Código error |
| --- | --- | --- |
| Sin sesión interna | `401` | `SESION_REQUERIDA` |
| Rol sin acceso a reportes (ej. `VENTANILLA`) | `403` | `ACCESO_DENEGADO` |
| Reporte restringido por rol | `403` | `REPORTE_RESTRINGIDO` |
| Gráfica distinta de `TABLA` sin `REPORTES:PERSONALIZAR` | `403` | `PERSONALIZACION_NO_PERMITIDA` |
| Zona del filtro restringida para el rol | `403` | `ZONA_RESTRINGIDA` |
| Gráfica no disponible para el reporte | `400` | `GRAFICA_NO_PERMITIDA` |
| Serie no disponible para el reporte | `400` | `SERIE_NO_PERMITIDA` |
| Filtro que no aplica al reporte | `400` | `FILTRO_NO_APLICABLE` |
| Año fuera del rango permitido | `400` | `ANIO_FUERA_DE_RANGO` |
| Monto mínimo negativo | `400` | `MONTO_INVALIDO` |

---

## 1. Consultar las opciones de visualización

```
GET /api/reportes/{tipo}/opciones-visualizacion
```

`tipo`: `MOROSOS`, `PRICOS` o `BUENOS_PAGADORES`.

### Respuesta `200` (ejemplo para `MOROSOS`, abreviado)

```json
{
  "tipoReporte": "MOROSOS",
  "puedePersonalizar": true,
  "tiposGrafica": ["TABLA", "BARRAS", "LINEAS", "CIRCULAR"],
  "series": [
    { "id": "deudaVencida", "etiqueta": "Deuda vencida", "formato": "MONTO" },
    { "id": "montoSinBeneficio", "etiqueta": "Deuda sin beneficio", "formato": "MONTO" },
    { "id": "montoConBeneficio", "etiqueta": "Deuda con beneficio", "formato": "MONTO" }
  ],
  "filtros": [
    { "nombre": "anioDesde", "tipo": "ANIO", "valores": null, "minimo": 1900, "maximo": 2026 },
    { "nombre": "anioHasta", "tipo": "ANIO", "valores": null, "minimo": 1900, "maximo": 2026 },
    { "nombre": "zona", "tipo": "LISTA", "valores": ["Z1", "Z2", "Z3"], "minimo": null, "maximo": null },
    { "nombre": "tributo", "tipo": "LISTA", "valores": ["IMPUESTO_PREDIAL", "ARBITRIOS"], "minimo": null, "maximo": null },
    { "nombre": "regimen", "tipo": "TEXTO", "valores": null, "minimo": null, "maximo": null },
    { "nombre": "beneficio", "tipo": "LISTA", "valores": ["AMBOS", "CON_BENEFICIO", "SIN_BENEFICIO"], "minimo": null, "maximo": null },
    { "nombre": "montoMinimo", "tipo": "MONTO", "valores": null, "minimo": 0, "maximo": null }
  ],
  "porDefecto": { "tipoGrafica": "TABLA", "serie": "deudaVencida" }
}
```

- `puedePersonalizar`: `false` cuando el usuario no tiene `REPORTES:PERSONALIZAR`; en ese caso `tiposGrafica` es
  `["TABLA"]`.
- `filtros[].tipo`: `ANIO`, `LISTA`, `TEXTO`, `MONTO` o `ENTERO`. `valores` aplica a `LISTA`; `minimo`/`maximo`
  acotan `ANIO`, `ENTERO` y `MONTO`. El año máximo es el año de la fecha actual.
- `zona` lista solo las zonas que el rol puede ver. En morosos, si aún no hay una carga publicada del ETL de
  cobranza, la lista viene vacía.

### Opciones por reporte

| Reporte | Gráficas | Series (la primera es la serie por defecto) | Filtros |
| --- | --- | --- | --- |
| `MOROSOS` | `TABLA`, `BARRAS`, `LINEAS`, `CIRCULAR` | `deudaVencida`, `montoSinBeneficio`, `montoConBeneficio` | `anioDesde`, `anioHasta` (1900–año actual), `zona`, `tributo`, `regimen`, `tipoDocumento`, `etapa`, `beneficio`, `montoMinimo` |
| `PRICOS` | `TABLA`, `BARRAS`, `CIRCULAR` | `baseImponible`, `emisionTotal`, `deudaAnterior`, `deudaActual` | `anio` (2000–año actual), `zona`, `tributo`, `montoMinimo`, `criterio`, `limite` (1–200) |
| `BUENOS_PAGADORES` | `TABLA`, `BARRAS`, `CIRCULAR` | `totalPagado`, `montoPagadoPredial`, `montoPagadoArbitrios`, `cuotasPagadasATiempo` (`CANTIDAD`) | `periodo`, `anio` (2000–año actual), `zona`, `tributo`, `montoMinimo` |

Los ids de las series coinciden con los que usa frontweb en `Reportes.jsx`, `ReportePricos.jsx` y
`ReporteBuenosPagadores.jsx`.

---

## 2. Guardar o actualizar una vista personalizada

Se usan los endpoints de la [HU007](HU007_API.md) (`POST /api/reportes/{tipo}/vistas` y
`PUT /api/reportes/vistas/{idVista}`). Además de las validaciones de la HU007, se aplican estas:

```json
{
  "nombre": "Morosos en líneas",
  "tipoGrafica": "LINEAS",
  "filtros": { "anioDesde": 2024, "anioHasta": 2026, "zona": "Z1", "beneficio": "SIN_BENEFICIO" },
  "columnasVisibles": ["codigo", "nombre", "deudaSinBeneficio"],
  "series": ["montoSinBeneficio"]
}
```

- `tipoGrafica` debe estar entre las gráficas del reporte. Una gráfica distinta de `TABLA` requiere
  `REPORTES:PERSONALIZAR`.
- `series`: cada id debe estar entre las series del reporte. Las repetidas se guardan una sola vez. Si la gráfica no
  es `TABLA` y no se envían series, se guarda la serie por defecto.
- `filtros`:
  - `regimen`, `tipoDocumento`, `etapa` y `beneficio` (distinto de `AMBOS`) solo aplican a `MOROSOS`;
  - `criterio` y `limite` solo aplican a `PRICOS`;
  - los años deben estar dentro del rango de la tabla anterior;
  - `montoMinimo` no puede ser negativo;
  - la `zona` debe ser visible para el rol.
- Al actualizar, si solo se envía `tipoGrafica`, se valida junto con las series ya guardadas, y viceversa.

### Vistas guardadas antes de la HU032

Al leer, abrir o actualizar una vista, se ignoran las series guardadas que ya no estén entre las opciones del reporte
(se registra un aviso en el log). La vista se sigue abriendo con el resto de su configuración.
