import assert from 'node:assert/strict'
import test from 'node:test'
import {
  derivarReclamo,
  listarMisReclamos,
  listarReclamosAsignados,
  listarReclamosGestion,
  ordenarReclamos,
  registrarReclamo,
  reiniciarReclamosDemo,
  responderReclamo,
} from '../src/services/reclamoService.js'
import { aFechaHoraLocal, estadoReclamo, tiposPara, validarReclamo } from '../src/utils/reclamos.js'

const AHORA = new Date(2026, 9, 28, 15, 30)
const valido = {
  tipoReclamo: 'DOCUMENTO',
  fechaHoraIncidente: '2026-10-28T09:00',
  referencia: 'Memorándum 050-2026',
  comentario: 'El documento tiene un dato incorrecto.',
}

test('HU050: el formulario es el mismo y solo cambian los tipos según quién reclama', () => {
  const trabajador = tiposPara('TRABAJADOR').map((t) => t.valor)
  const contribuyente = tiposPara('CONTRIBUYENTE').map((t) => t.valor)
  assert.deepEqual(trabajador.sort(), ['AREA_RELACIONADA', 'COBRANZA', 'DOCUMENTO', 'OTRO'])
  assert.deepEqual(contribuyente.sort(), ['ATENCION', 'COBRANZA', 'OTRO', 'SERVICIO'])
})

test('HU050: un reclamo completo es válido y no devuelve errores', () => {
  assert.deepEqual(validarReclamo(valido, 'TRABAJADOR', AHORA), {})
})

test('HU050: exige tipo, fecha y comentario', () => {
  const errores = validarReclamo({ tipoReclamo: '', fechaHoraIncidente: '', referencia: '', comentario: '   ' }, 'TRABAJADOR', AHORA)
  assert.deepEqual(Object.keys(errores).sort(), ['comentario', 'fechaHoraIncidente', 'tipoReclamo'])
})

test('HU050: rechaza un tipo que no corresponde al reclamante', () => {
  const errores = validarReclamo({ ...valido, tipoReclamo: 'SERVICIO' }, 'TRABAJADOR', AHORA)
  assert.ok(errores.tipoReclamo)
})

test('HU050: el incidente no puede ser posterior a la fecha y hora actual', () => {
  assert.ok(validarReclamo({ ...valido, fechaHoraIncidente: '2026-10-28T16:00' }, 'TRABAJADOR', AHORA).fechaHoraIncidente)
  assert.deepEqual(validarReclamo({ ...valido, fechaHoraIncidente: '2026-10-28T15:30' }, 'TRABAJADOR', AHORA), {})
})

test('HU050: respeta los máximos de comentario (2000) y referencia (150)', () => {
  assert.ok(validarReclamo({ ...valido, comentario: 'x'.repeat(2001) }, 'TRABAJADOR', AHORA).comentario)
  assert.deepEqual(validarReclamo({ ...valido, comentario: 'x'.repeat(2000) }, 'TRABAJADOR', AHORA), {})
  assert.ok(validarReclamo({ ...valido, referencia: 'r'.repeat(151) }, 'TRABAJADOR', AHORA).referencia)
})

test('HU050: los cuatro estados del reclamo tienen etiqueta legible', () => {
  assert.equal(estadoReclamo('ENVIADO').etiqueta, 'Enviado')
  assert.equal(estadoReclamo('EN_REVISION').etiqueta, 'En revisión')
  assert.equal(estadoReclamo('APROBADO_CON_RESPUESTA').etiqueta, 'Aprobado con respuesta')
  assert.equal(estadoReclamo('CANCELADO').etiqueta, 'Cancelado')
})

test('HU050: aFechaHoraLocal da el formato del campo datetime-local', () => {
  assert.equal(aFechaHoraLocal(new Date(2026, 0, 5, 7, 4)), '2026-01-05T07:04')
})

test('HU050: el modo demostración explícito lista reclamos ficticios con estado y derivaciones', async () => {
  reiniciarReclamosDemo()
  const { reclamos, demo } = await listarMisReclamos(true)
  assert.equal(demo, true)
  assert.equal(reclamos.length, 2)
  assert.ok(reclamos.some((r) => r.estado === 'EN_REVISION' && r.derivaciones.length === 1))
  assert.ok(reclamos.some((r) => r.estado === 'APROBADO_CON_RESPUESTA' && r.respuesta))
})

test('HU050: registrar un reclamo en demostración lo deja ENVIADO con número y lo agrega a la lista', async () => {
  reiniciarReclamosDemo()
  const creado = await registrarReclamo(valido, true, 'TRABAJADOR', AHORA)
  assert.equal(creado.estado, 'ENVIADO')
  assert.equal(creado.numeroReclamo, 'REC-2026-000003')
  assert.equal(creado.fechaHoraIncidente, '2026-10-28T09:00:00')
  assert.equal(creado.fechaRegistro, '2026-10-28T15:30:00')
  assert.deepEqual(creado.derivaciones, [])
  const { reclamos } = await listarMisReclamos(true)
  assert.equal(reclamos.length, 3)
  reiniciarReclamosDemo()
})

test('HU050: un reclamo inválido no se registra y devuelve los errores por campo', async () => {
  reiniciarReclamosDemo()
  await assert.rejects(
    () => registrarReclamo({ ...valido, comentario: '' }, true, 'TRABAJADOR', AHORA),
    (err) => err.codigo === 'RECLAMO_INVALIDO' && Boolean(err.errores.comentario),
  )
  assert.equal((await listarMisReclamos(true)).reclamos.length, 2)
})

test('HU050: la lista se ordena con el reclamo más reciente primero', () => {
  const orden = ordenarReclamos([
    { idReclamo: 1, fechaRegistro: '2026-10-02T16:20:00' },
    { idReclamo: 2, fechaRegistro: '2026-10-14T11:05:00' },
  ])
  assert.deepEqual(orden.map((r) => r.idReclamo), [2, 1])
})

test('HU050: gestor y responsable reciben reclamos normalizados desde sus bandejas', async (t) => {
  const original = globalThis.fetch
  t.after(() => { globalThis.fetch = original })
  const respuesta = [{ reclamo: { idReclamo: 10, numeroReclamo: 'REC-2026-000010', estado: 'EN_REVISION', derivaciones: [] }, reclamante: 'Trabajador Uno', idAreaActual: 2, areaActual: 'Rentas', idResponsableActual: 3, responsableActual: 'Responsable Uno' }]
  const rutas = []
  globalThis.fetch = async (url, opciones) => {
    rutas.push([String(url), opciones])
    return new Response(JSON.stringify(respuesta), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  const gestion = await listarReclamosGestion()
  const asignados = await listarReclamosAsignados()
  assert.equal(gestion[0].numeroReclamo, 'REC-2026-000010')
  assert.equal(asignados[0].responsableActual, 'Responsable Uno')
  assert.ok(rutas[0][0].endsWith('/api/reclamos/gestion'))
  assert.ok(rutas[1][0].endsWith('/api/reclamos/asignados'))
  assert.equal(rutas[0][1].credentials, 'include')
})

test('HU050: derivar y responder envían CSRF y el cuerpo esperado', async (t) => {
  const original = globalThis.fetch
  t.after(() => { globalThis.fetch = original })
  const llamadas = []
  globalThis.fetch = async (url, opciones = {}) => {
    llamadas.push([String(url), opciones])
    if (String(url).endsWith('/auth/interno/csrf')) {
      return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'token-hu050' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    }
    const cuerpo = JSON.parse(opciones.body)
    const estado = String(url).includes('/respuesta') ? 'APROBADO_CON_RESPUESTA' : 'EN_REVISION'
    return new Response(JSON.stringify({ reclamo: { idReclamo: 10, numeroReclamo: 'REC-2026-000010', estado, derivaciones: [] }, reclamante: 'Trabajador Uno', ...cuerpo }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  await derivarReclamo(10, { idArea: 2, idResponsable: 3, observacion: 'Revisar' })
  await responderReclamo(10, 'Respuesta oficial')
  const mutables = llamadas.filter(([url]) => !url.endsWith('/auth/interno/csrf'))
  assert.equal(new Headers(mutables[0][1].headers).get('X-CSRF-TOKEN'), 'token-hu050')
  assert.deepEqual(JSON.parse(mutables[0][1].body), { idArea: 2, idResponsable: 3, observacion: 'Revisar' })
  assert.deepEqual(JSON.parse(mutables[1][1].body), { respuesta: 'Respuesta oficial' })
})
