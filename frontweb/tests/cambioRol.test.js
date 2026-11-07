import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import {
  cambiarRol,
  cargarUsuariosYRoles,
  construirRegistroCambio,
} from '../src/services/usuarioAdminService.js'

const OPERADOR = { idUsuario: 5, nombreUsuario: 'ventanilla1', nombreCompleto: 'Operador de Ventanilla', rol: 'VENTANILLA' }
const ADMINISTRADOR = { idUsuario: 1, nombreUsuario: 'admin', nombreCompleto: 'Administrador', rol: 'ADMINISTRADOR' }
import { etiquetaRol } from '../src/utils/roles.js'

test('HU035: el registro del cambio guarda usuario, fecha y rol anterior', () => {
  const ahora = new Date('2026-10-28T15:30:00Z')
  const registro = construirRegistroCambio(OPERADOR, 'TERMINALISTA', '  Rotación de puesto  ', ahora)
  assert.equal(registro.nombreUsuario, 'ventanilla1')
  assert.equal(registro.rolAnterior, 'VENTANILLA')
  assert.equal(registro.rolNuevo, 'TERMINALISTA')
  assert.equal(registro.motivo, 'Rotación de puesto')
  assert.equal(registro.fecha, '2026-10-28T15:30:00.000Z')
})

test('HU035: usuarios y roles se leen siempre del servidor', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => Response.json(String(url).endsWith('/roles')
    ? [{ nombre: 'ADMINISTRADOR' }, { nombre: 'GESTOR_RECLAMOS' }]
    : [OPERADOR, ADMINISTRADOR]))
  const datos = await cargarUsuariosYRoles()
  assert.equal(datos.usuarios.length, 2)
  assert.ok(datos.roles.some((r) => r.nombre === 'GESTOR_RECLAMOS'))
  assert.equal('demo' in datos, false)
})

test('HU035: sin conexión se informa el error en lugar de mostrar usuarios ficticios', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('fetch failed') })
  await assert.rejects(() => cargarUsuariosYRoles(), { codigo: 'SIN_CONEXION' })
})

test('HU035: una sesión vencida no se reemplaza con usuarios ficticios', async () => {
  const fetchOriginal = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify({ codigo: 'SESION_REQUERIDA', mensaje: 'Sesión vencida.' }), {
    status: 401, headers: { 'Content-Type': 'application/json' },
  })
  try {
    await assert.rejects(() => cargarUsuariosYRoles(), { estado: 401, codigo: 'SESION_REQUERIDA' })
  } finally {
    globalThis.fetch = fetchOriginal
  }
})

test('HU035: el cambio real de rol envía cookie y token CSRF', async () => {
  const fetchOriginal = globalThis.fetch
  const solicitudes = []
  globalThis.fetch = async (url, opciones = {}) => {
    solicitudes.push({ url: String(url), opciones })
    if (String(url).endsWith('/auth/interno/csrf')) {
      return Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-prueba' })
    }
    return Response.json({ ...OPERADOR, rol: 'ANALISTA_RENTAS' })
  }
  try {
    const { usuario } = await cambiarRol(OPERADOR, 'ANALISTA_RENTAS', 'Ascenso')
    assert.equal(usuario.rol, 'ANALISTA_RENTAS')
    assert.equal(solicitudes[1].opciones.method, 'PUT')
    assert.equal(solicitudes[1].opciones.credentials, 'include')
    assert.equal(solicitudes[1].opciones.headers.get('X-XSRF-TOKEN'), 'csrf-prueba')
  } finally {
    globalThis.fetch = fetchOriginal
  }
})

test('HU035: rechaza el mismo rol y la falta de rol', async () => {
  await assert.rejects(() => cambiarRol(ADMINISTRADOR, 'ADMINISTRADOR', ''), { codigo: 'ROL_IDENTICO' })
  await assert.rejects(() => cambiarRol(ADMINISTRADOR, '', ''), { codigo: 'ROL_REQUERIDO' })
})

test('HU035: las etiquetas de rol usan el nombre que conoce el usuario de Rentas', () => {
  assert.equal(etiquetaRol('GERENCIA'), 'Gerencia de Rentas')
  assert.equal(etiquetaRol('GESTOR_RECLAMOS'), 'Gestor de reclamos')
  assert.equal(etiquetaRol(undefined), 'Sin rol')
})

test('HU034/HU035: existe una sola entrada de usuarios y la ruta anterior redirige a la oficial', () => {
  const modulos = readFileSync(new URL('../src/utils/modulos.jsx', import.meta.url), 'utf8')
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8')
  assert.equal((modulos.match(/texto: 'Usuarios'/g) || []).length, 1)
  assert.match(app, /path="\/usuarios" element={<Navigate to="\/administracion\/usuarios" replace \/>}/)
  assert.doesNotMatch(app, /UsuariosRoles/)
})
