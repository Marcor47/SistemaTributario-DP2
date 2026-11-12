# Maestro de documentos (RF-063, HU049)

## Cómo está hoy

| Qué | Dónde |
| --- | --- |
| Plantillas y formularios: catálogo | `operativo.plantilla_documento` (código, nombre, tipo, descripción) |
| Cada versión publicada, con el archivo | `operativo.version_plantilla.contenido` (`bytea`), su nombre de archivo, tipo, fecha y quién publicó. Solo una vigente por plantilla; las anteriores quedan como historial. |
| Constancias emitidas | el PDF en `operativo.constancia.contenido_pdf`; lo arma `GeneradorConstanciaPdf` |
| Adjuntos de los trámites de predio | archivos cifrados (AES-GCM) en la carpeta `storage/predios` (`PREDIO_FILES_DIR`); la base guarda la ruta |

Las plantillas no están en una carpeta: están en PostgreSQL, versionadas. La única carpeta del backend es la de los
adjuntos de predios. Los formularios se publican, retiran y descargan con `/api/formularios` (pantalla Formularios).

## Decisión: se queda así para el piloto

Para la demostración y un piloto de unos 10 usuarios internos, guardar los archivos en la misma base es suficiente: los
formularios son pocos y pequeños (decenas de KB), se publican pocas veces y un solo respaldo (`pg_dump`) cubre datos y
documentos. No hay otro servicio que operar.

Un lakehouse u almacenamiento de objetos (S3 con el catálogo en PostgreSQL, como en la prueba de concepto de DuckLake)
es mejor **cuando crece**:

- **Volumen.** Cada constancia emitida guarda su PDF. Con miles al mes, la base crece con binarios que no se consultan;
  en S3 la base guarda solo la ruta y el hash.
- **Respaldo y restauración.** Una base con gigas de PDF tarda más en respaldar y restaurar; los objetos tienen
  versionado y ciclo de vida propios.
- **Varias instancias del backend** comparten el almacenamiento sin pasar los archivos por la base.
- **Edición en línea** (p. ej. ONLYOFFICE) trabaja con URL de archivos, no con columnas `bytea`.

Señales para migrar: más de unos pocos GB en `version_plantilla` y `constancia`, respaldos lentos, o más de una
instancia del backend. La migración es acotada: un servicio de almacenamiento que guarde en S3 y deje en la tabla la
ruta y el SHA-256; la API `/api/formularios` no cambia.

## Editar un formulario

`back/scripts/editar_formulario.py` (requiere Python 3.10+; con [uv](https://docs.astral.sh/uv/) no hay que instalar
nada más). Usa la misma API que la pantalla, así que la versión editada se publica como una versión nueva y la anterior
queda en el historial: no cambia nada de lo que el front usa.

```bash
# Ver el texto del formulario vigente (o de un .docx local) para ubicar qué cambiar
uv run back/scripts/editar_formulario.py descargar --codigo PL-001 PL-001.docx
uv run back/scripts/editar_formulario.py texto PL-001.docx

# Editar y publicar la versión nueva en un paso (pide la clave de admin, o FORMULARIO_CLAVE)
uv run back/scripts/editar_formulario.py publicar --codigo PL-001 \
    --reemplazar "Se deja constancia de que" "Se hace constar que" \
    --al-final "Texto agregado al final"

# O editar a mano en Word / LibreOffice y publicar el archivo
uv run back/scripts/editar_formulario.py publicar --codigo PL-001 --archivo PL-001-editado.docx

# Solo una copia local, sin publicar
uv run back/scripts/editar_formulario.py editar PL-001.docx PL-001-v3.docx --reemplazar "viejo" "nuevo"
```

- Sin uv: `pip install python-docx` y `python back/scripts/editar_formulario.py ...`.
- Conexión: `--api` (por defecto `http://localhost:8080`), `--usuario` (por defecto `admin`; publicar requiere
  `FORMULARIOS:PUBLICAR`).
- Si un texto a reemplazar no aparece, no se guarda ni se publica nada.
- El reemplazo funciona aunque Word haya partido el texto en varios tramos con formato distinto, y también en tablas,
  encabezados y pies. Conserva el formato del primer tramo.
- Solo edita formularios `.docx`. Las constancias de No Adeudo las genera el backend con su propio formato; cuando la
  Municipalidad entregue el oficial se publica como versión nueva de `CONSTANCIA_NO_ADEUDO`.
- Para revisar cómo se ve antes de publicar: `soffice --headless --convert-to pdf PL-001-v3.docx`.
