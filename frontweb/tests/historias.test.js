import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizarRegularidad, obtenerRegularidad } from '../src/services/regularidadService.js'
import {
  agregarFormulario, descargarFormulario, eliminarFormulario, obtenerFormularios, tipoDeCodigo,
} from '../src/services/formularioService.js'

const json = (cuerpo, estado = 200) => new Response(JSON.stringify(cuerpo), { status: estado })

/* HU010: la regularidad la calcula el backend; el front solo la presenta. */
const regularidad = {
  codigoContribuyente: '033218', nombreContribuyente: 'RAMOS QUISPE, VÍCTOR HUGO', anio: 2025,
  totalCuotasEmitidas: 3, cuotasExigibles: 2, cuotasPagadasATiempo: 1, cuotasPagadasConAtraso: 1,
  cuotasPendientesOVencidas: 0, porcentajeRegularidad: 50, segmentoSugerido: 'IRREGULAR', observacion: '1 de 2',
  fechaCorte: '2025-10-19', aniosDisponibles: [2026, 2025],
  cuotas: [
    { idObligacion: 1, periodo: '2025-01', tributo: 'ARBITRIOS', fechaVencimiento: '2025-02-28', fechaPago: '2025-02-20', emitido: 40, estado: 'A_TIEMPO' },
    { idObligacion: 2, periodo: '2025-03', tributo: 'ARBITRIOS', fechaVencimiento: '2025-03-31', fechaPago: '2025-04-10', emitido: 40, estado: 'CON_ATRASO' },
    { idObligacion: 3, periodo: '2025-12', tributo: 'ARBITRIOS', fechaVencimiento: '2025-12-31', fechaPago: null, emitido: 40, estado: 'POR_VENCER' },
  ],
}

test('HU010: la ficha usa los años, totales y cuotas que devuelve el servidor', () => {
  const ficha = normalizarRegularidad(regularidad, { codigo: '033218', tipoDoc: 'DNI', doc: '09215430' })
  assert.deepEqual(ficha.aniosDisponibles, [2026, 2025])
  assert.equal(ficha.porcentaje, 50)
  assert.equal(ficha.exigibles, 2)
  assert.equal(ficha.porVencer, 1)
  assert.deepEqual(ficha.cuotas.map((c) => c.estado), ['A tiempo', 'Con atraso', 'Por vencer'])
  assert.equal(ficha.cuotas[0].tributo, 'Arbitrios Municipales')
  assert.equal(ficha.contribuyente.doc, '09215430')
})

test('HU010: un año sin cuotas vencidas no se muestra como regularidad cero', () => {
  const ficha = normalizarRegularidad({ ...regularidad, cuotasExigibles: 0, porcentajeRegularidad: 0 }, { codigo: '033218' })
  assert.equal(ficha.porcentaje, null)
})

test('HU010: consulta el endpoint real con el año elegido', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => { urls.push(String(url)); return json(regularidad) })
  const ficha = await obtenerRegularidad({ codigo: '033218' }, 2025)
  assert.ok(urls[0].endsWith('/contribuyentes/033218/regularidad?anio=2025'))
  assert.equal(ficha.anio, 2025)
})

/* HU049: el catálogo de formularios vive en la BD. */
const catalogo = [
  { codigo: 'FO-002', nombre: 'Declaración Jurada de Predio', version: 3, fechaPublicacion: '2026-10-15T14:00:00Z',
    descripcion: 'Datos del predio.', formato: 'Word (.docx)', archivo: 'FO-002-v3.docx' },
  { codigo: 'PL-001', nombre: 'Constancia de No Adeudo', version: 2, fechaPublicacion: '2026-10-19T14:00:00Z',
    descripcion: null, formato: 'Word (.docx)', archivo: 'PL-001-v2.docx' },
]

test('HU049: el catálogo se lee del servidor y permite buscar sin tildes', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => json(catalogo))
  const documentos = await obtenerFormularios()
  assert.equal(documentos.length, 2)
  assert.equal(documentos.find((d) => d.codigo === 'PL-001').tipo, 'Plantilla')
  assert.equal(documentos.find((d) => d.codigo === 'FO-002').fechaPublicacion, '2026-10-15')
  assert.equal((await obtenerFormularios('declaracion jurada'))[0].codigo, 'FO-002')
  assert.deepEqual(await obtenerFormularios('documento inexistente'), [])
  assert.equal(tipoDeCodigo('FO-010'), 'Formulario')
})

test('HU049: solo se descarga la versión vigente', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => (String(url).endsWith('/descarga')
    ? new Response(new Blob(['PK']), { status: 200 })
    : json(catalogo)))
  const { nombre } = await descargarFormulario('PL-001', 2)
  assert.equal(nombre, 'PL-001-v2.docx')
  await assert.rejects(descargarFormulario('PL-001', 1), /La versión cambió/)
  await assert.rejects(descargarFormulario('inexistente', 1), /no está disponible/)
})

test('HU049: publicar asigna el siguiente código y envía el archivo con CSRF', async (t) => {
  const envios = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    const ruta = String(url)
    if (ruta.endsWith('/auth/interno/csrf')) return json({ headerName: 'X-XSRF-TOKEN', token: 't' })
    if (opciones.method === 'POST') {
      envios.push(opciones)
      return json({ ...catalogo[0], codigo: opciones.body.get('codigo'), nombre: opciones.body.get('nombre'), version: 1 }, 201)
    }
    if (opciones.method === 'DELETE') { envios.push(opciones); return new Response(null, { status: 204 }) }
    return json(catalogo)
  })
  const archivo = new File(['PK'], 'nueva.docx')
  const nuevo = await agregarFormulario({ nombre: 'Nueva solicitud', tipo: 'Formulario', descripcion: '', archivo })
  assert.equal(nuevo.codigo, 'FO-003')
  assert.equal(envios[0].headers['X-XSRF-TOKEN'], 't')
  assert.equal(envios[0].body.get('archivo').name, 'nueva.docx')
  await eliminarFormulario('FO-003')
  assert.equal(envios[1].method, 'DELETE')
  await assert.rejects(agregarFormulario({ nombre: 'X', tipo: 'Formulario', descripcion: '', archivo: new File(['x'], 'a.txt') }),
    /Word \(.docx\) o PDF/)
})
