# /// script
# requires-python = ">=3.10"
# dependencies = ["python-docx>=1.1"]
# ///
"""Edita los formularios Word (.docx) del maestro de documentos (HU049, RF-063) y publica la versión nueva.

El maestro guarda cada versión en PostgreSQL (operativo.version_plantilla) y solo la vigente se ofrece. Este script
usa la misma API que la pantalla Formularios, así que no cambia nada de lo que el front usa: la versión anterior queda
en el historial y la nueva aparece como vigente.

Uso (con uv, que instala python-docx solo; sin uv: pip install python-docx y python en vez de uv run):

  uv run back/scripts/editar_formulario.py texto ARCHIVO.docx
      Muestra el texto (párrafos, tablas, encabezados y pies) para ubicar lo que se quiere cambiar.

  uv run back/scripts/editar_formulario.py editar ENTRADA.docx SALIDA.docx --reemplazar "viejo" "nuevo" ...
      Edita una copia local. Opciones repetibles: --reemplazar VIEJO NUEVO, --al-inicio TEXTO, --al-final TEXTO.

  uv run back/scripts/editar_formulario.py descargar --codigo PL-001 SALIDA.docx
      Baja la versión vigente (para editarla a mano en Word o LibreOffice).

  uv run back/scripts/editar_formulario.py publicar --codigo PL-001 --reemplazar "viejo" "nuevo" ...
  uv run back/scripts/editar_formulario.py publicar --codigo PL-001 --archivo EDITADO.docx
      Baja la vigente, aplica las ediciones (o toma el archivo ya editado) y la publica como versión nueva.

Conexión: --api (por defecto http://localhost:8080), --usuario (por defecto admin) y la clave en la variable
FORMULARIO_CLAVE o, si no está, se pide por teclado. Publicar requiere el permiso FORMULARIOS:PUBLICAR (Administrador).

Un --reemplazar cuyo texto no aparece es un error: así no se publica una versión que no tiene el cambio. Word parte el
texto en tramos (runs) al editar; el reemplazo funciona aunque el texto esté partido y conserva el formato del primer
tramo. Las marcas {{campo}} de una plantilla se pueden reemplazar igual que cualquier texto.
"""
from __future__ import annotations

import argparse
import getpass
import http.cookiejar
import io
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

from docx import Document
from docx.document import Document as DocumentoWord
from docx.table import Table
from docx.text.paragraph import Paragraph

TIPO_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"


# ----------------------------------------------------------------------------------------------- edición del .docx

def parrafos(doc: DocumentoWord):
    """Todos los párrafos editables: cuerpo, tablas (también anidadas), encabezados y pies de cada sección."""
    def de_bloque(contenedor):
        for p in contenedor.paragraphs:
            yield p
        for tabla in contenedor.tables:
            yield from de_tabla(tabla)

    def de_tabla(tabla: Table):
        for fila in tabla.rows:
            for celda in fila.cells:
                yield from de_bloque(celda)

    yield from de_bloque(doc)
    for seccion in doc.sections:
        for parte in (seccion.header, seccion.first_page_header, seccion.even_page_header,
                      seccion.footer, seccion.first_page_footer, seccion.even_page_footer):
            if parte is not None and not parte.is_linked_to_previous:
                yield from de_bloque(parte)


def reemplazar_en_parrafo(p: Paragraph, viejo: str, nuevo: str) -> int:
    """Reemplaza viejo por nuevo aunque esté repartido en varios runs; el texto nuevo toma el formato del primero."""
    veces, desde = 0, 0
    while True:
        runs = p.runs
        texto = "".join(r.text for r in runs)
        i = texto.find(viejo, desde)
        if i < 0:
            return veces
        fin = i + len(viejo)
        pos, primero, ultimo = 0, None, None
        for k, r in enumerate(runs):
            largo = len(r.text)
            if primero is None and i < pos + largo:
                primero, corte_ini = k, i - pos
            if primero is not None and fin <= pos + largo:
                ultimo, corte_fin = k, fin - pos
                break
            pos += largo
        if primero == ultimo:
            r = runs[primero]
            r.text = r.text[:corte_ini] + nuevo + r.text[corte_fin:]
        else:
            runs[primero].text = runs[primero].text[:corte_ini] + nuevo
            for k in range(primero + 1, ultimo):
                runs[k].text = ""
            runs[ultimo].text = runs[ultimo].text[corte_fin:]
        veces += 1
        desde = i + len(nuevo)


def editar(contenido: bytes, reemplazos, al_inicio, al_final) -> tuple[bytes, list[str]]:
    doc = Document(io.BytesIO(contenido))
    informe = []
    for viejo, nuevo in reemplazos:
        if not viejo:
            raise SystemExit("--reemplazar necesita un texto a buscar")
        veces = sum(reemplazar_en_parrafo(p, viejo, nuevo) for p in parrafos(doc))
        if veces == 0:
            raise SystemExit(f"No se encontró el texto a reemplazar: {viejo!r}. No se guardó nada.")
        informe.append(f"reemplazado {veces} vez/veces: {viejo!r} -> {nuevo!r}")
    for texto in reversed(al_inicio):
        primero = doc.paragraphs[0] if doc.paragraphs else doc.add_paragraph()
        primero.insert_paragraph_before(texto, style=primero.style)
        informe.append(f"párrafo agregado al inicio: {texto!r}")
    for texto in al_final:
        doc.add_paragraph(texto)
        informe.append(f"párrafo agregado al final: {texto!r}")
    salida = io.BytesIO()
    doc.save(salida)
    datos = salida.getvalue()
    Document(io.BytesIO(datos))  # el resultado se vuelve a abrir: si no es un .docx válido, falla aquí
    return datos, informe


def texto_de(contenido: bytes) -> str:
    return "\n".join(p.text for p in parrafos(Document(io.BytesIO(contenido))) if p.text.strip())


# ------------------------------------------------------------------------------------------------ API del backend

class Api:
    """Sesión interna del backend: login con cookie y token CSRF, como lo hace frontweb."""

    def __init__(self, base: str, usuario: str, clave: str):
        self.base = base.rstrip("/")
        self.cookies = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.pedir("POST", "/api/auth/interno/login", cuerpo=json.dumps({"nombreUsuario": usuario, "clave": clave}),
                   tipo="application/json")
        csrf = json.loads(self.pedir("GET", "/api/auth/interno/csrf"))
        self.csrf = {csrf["headerName"]: csrf["token"]}

    def pedir(self, metodo, ruta, cuerpo=None, tipo=None, cabeceras=None) -> bytes:
        datos = cuerpo.encode() if isinstance(cuerpo, str) else cuerpo
        req = urllib.request.Request(self.base + ruta, data=datos, method=metodo)
        if tipo:
            req.add_header("Content-Type", tipo)
        for k, v in (cabeceras or {}).items():
            req.add_header(k, v)
        try:
            with self.http.open(req, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            detalle = e.read().decode(errors="replace")
            raise SystemExit(f"{metodo} {ruta}: HTTP {e.code} {detalle}") from None

    def vigente(self, codigo: str) -> dict:
        for f in json.loads(self.pedir("GET", "/api/formularios")):
            if f["codigo"] == codigo:
                return f
        raise SystemExit(f"No hay una versión vigente del formulario {codigo}.")

    def descargar(self, codigo: str) -> bytes:
        return self.pedir("GET", f"/api/formularios/{urllib.parse.quote(codigo)}/descarga")

    def publicar(self, codigo, nombre, descripcion, archivo, contenido) -> dict:
        limite = uuid.uuid4().hex
        partes = []
        for campo, valor in (("codigo", codigo), ("nombre", nombre), ("descripcion", descripcion)):
            if valor is not None:
                partes.append(f'--{limite}\r\nContent-Disposition: form-data; name="{campo}"\r\n\r\n{valor}\r\n'
                              .encode())
        partes.append(f'--{limite}\r\nContent-Disposition: form-data; name="archivo"; filename="{archivo}"\r\n'
                      f"Content-Type: {TIPO_DOCX}\r\n\r\n".encode() + contenido + b"\r\n")
        partes.append(f"--{limite}--\r\n".encode())
        respuesta = self.pedir("POST", "/api/formularios", cuerpo=b"".join(partes),
                               tipo=f"multipart/form-data; boundary={limite}", cabeceras=self.csrf)
        return json.loads(respuesta)


def conectar(args) -> Api:
    clave = os.environ.get("FORMULARIO_CLAVE") or getpass.getpass(f"Clave de {args.usuario}: ")
    return Api(args.api, args.usuario, clave)


# ---------------------------------------------------------------------------------------------------- comandos

def agregar_ediciones(sub):
    sub.add_argument("--reemplazar", nargs=2, action="append", default=[], metavar=("VIEJO", "NUEVO"))
    sub.add_argument("--al-inicio", action="append", default=[], metavar="TEXTO")
    sub.add_argument("--al-final", action="append", default=[], metavar="TEXTO")


def agregar_conexion(sub):
    sub.add_argument("--api", default=os.environ.get("FORMULARIO_API", "http://localhost:8080"))
    sub.add_argument("--usuario", default=os.environ.get("FORMULARIO_USUARIO", "admin"))
    sub.add_argument("--codigo", required=True, help="código del formulario, p. ej. PL-001")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    comandos = parser.add_subparsers(dest="comando", required=True)

    c = comandos.add_parser("texto", help="muestra el texto de un .docx")
    c.add_argument("archivo", type=Path)

    c = comandos.add_parser("editar", help="edita una copia local")
    c.add_argument("entrada", type=Path)
    c.add_argument("salida", type=Path)
    agregar_ediciones(c)

    c = comandos.add_parser("descargar", help="baja la versión vigente de un formulario")
    agregar_conexion(c)
    c.add_argument("salida", type=Path)

    c = comandos.add_parser("publicar", help="edita la versión vigente y la publica como versión nueva")
    agregar_conexion(c)
    agregar_ediciones(c)
    c.add_argument("--archivo", type=Path, help="publicar este .docx ya editado en vez de editar la vigente")
    c.add_argument("--nombre", help="nombre nuevo (por defecto el de la versión vigente)")
    c.add_argument("--descripcion", help="descripción nueva (por defecto se conserva)")
    c.add_argument("--guardar-copia", type=Path, help="guardar también localmente lo que se publica")

    args = parser.parse_args()

    if args.comando == "texto":
        print(texto_de(args.archivo.read_bytes()))
    elif args.comando == "editar":
        datos, informe = editar(args.entrada.read_bytes(), args.reemplazar, args.al_inicio, args.al_final)
        args.salida.write_bytes(datos)
        print("\n".join(informe + [f"guardado: {args.salida}"]))
    elif args.comando == "descargar":
        api = conectar(args)
        args.salida.write_bytes(api.descargar(args.codigo))
        print(f"versión vigente de {args.codigo} guardada en {args.salida}")
    elif args.comando == "publicar":
        api = conectar(args)
        vigente = api.vigente(args.codigo)
        if args.archivo:
            if args.reemplazar or args.al_inicio or args.al_final:
                raise SystemExit("Use --archivo o las opciones de edición, no ambas.")
            datos, informe = args.archivo.read_bytes(), [f"archivo: {args.archivo}"]
            Document(io.BytesIO(datos))
        else:
            if not vigente["archivo"].lower().endswith(".docx"):
                raise SystemExit(f"{args.codigo} es {vigente['formato']}: solo se editan formularios .docx.")
            if not (args.reemplazar or args.al_inicio or args.al_final):
                raise SystemExit("Indique al menos una edición (--reemplazar, --al-inicio o --al-final) o --archivo.")
            datos, informe = editar(api.descargar(args.codigo), args.reemplazar, args.al_inicio, args.al_final)
        if args.guardar_copia:
            args.guardar_copia.write_bytes(datos)
        version_anterior = vigente["version"]
        nombre_archivo = f"{args.codigo}-v{version_anterior + 1}.docx"
        nueva = api.publicar(args.codigo, args.nombre or vigente["nombre"], args.descripcion, nombre_archivo, datos)
        print("\n".join(informe))
        print(f"{args.codigo}: publicada la versión {nueva['version']} (antes {version_anterior}), "
              f"archivo {nueva['archivo']}")


if __name__ == "__main__":
    sys.exit(main())
