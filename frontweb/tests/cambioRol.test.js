import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { USUARIOS_DEMO } from '../src/mocks/usuarios.js'
import {
  cambiarRol,
  cargarUsuariosYRoles,
  construirRegistroCambio,
  reiniciarUsuariosDemo,
} from '../src/services/usuarioAdminService.js'
import { etiquetaRol } from '../src/utils/roles.js'

test('HU035: el registro del cambio guarda usuario, fecha y rol anterior', () => {
  const ahora = new Date('2026-10-28T15:30:00Z')
  const registro = construirRegistroCambio(USUARIOS_DEMO[4], 'TERMINALISTA', '  Rotación de puesto  ', ahora)
  assert.equal(registro.nombreUsuario, 'ventanilla1')
  assert.equal(registro.rolAnterior, 'VENTANILLA')
  assert.equal(registro.rolNuevo, 'TERMINALISTA')
  assert.equal(registro.motivo, 'Rotación de puesto')
  assert.equal(registro.fecha, '2026-10-28T15:30:00.000Z')
})

test('HU035: sin backend se cargan usuarios y los siete roles ficticios', async () => {
  reiniciarUsuariosDemo()
  const datos = await cargarUsuariosYRoles()
  assert.equal(datos.demo, true)
  assert.equal(datos.usuarios.length, USUARIOS_DEMO.length)
  assert.equal(datos.roles.length, 7)
  assert.ok(datos.roles.some((r) => r.nombre === 'GESTOR_RECLAMOS'))
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
    return Response.json({ ...USUARIOS_DEMO[4], rol: 'ANALISTA_RENTAS' })
  }
  try {
    const { usuario } = await cambiarRol(USUARIOS_DEMO[4], 'ANALISTA_RENTAS', 'Ascenso', false)
    assert.equal(usuario.rol, 'ANALISTA_RENTAS')
    assert.equal(solicitudes[1].opciones.method, 'PUT')
    assert.equal(solicitudes[1].opciones.credentials, 'include')
    assert.equal(solicitudes[1].opciones.headers.get('X-XSRF-TOKEN'), 'csrf-prueba')
  } finally {
    globalThis.fetch = fetchOriginal
  }
})

test('HU035: cambiar el rol en demostración actualiza al usuario y devuelve el registro', async () => {
  reiniciarUsuariosDemo()
  const { usuarios } = await cargarUsuariosYRoles()
  const objetivo = usuarios.find((u) => u.nombreUsuario === 'ventanilla1')
  const { usuario, registro } = await cambiarRol(objetivo, 'ANALISTA_RENTAS', 'Ascenso', true)
  assert.equal(usuario.rol, 'ANALISTA_RENTAS')
  assert.equal(registro.rolAnterior, 'VENTANILLA')
  const otraVez = await cargarUsuariosYRoles()
  assert.equal(otraVez.usuarios.find((u) => u.idUsuario === objetivo.idUsuario).rol, 'ANALISTA_RENTAS')
  assert.equal(objetivo.rol, 'VENTANILLA', 'El usuario de origen no se modifica')
  reiniciarUsuariosDemo()
})

test('HU035: rechaza el mismo rol y la falta de rol', async () => {
  await assert.rejects(() => cambiarRol(USUARIOS_DEMO[0], 'ADMINISTRADOR', '', true), { codigo: 'ROL_IDENTICO' })
  await assert.rejects(() => cambiarRol(USUARIOS_DEMO[0], '', '', true), { codigo: 'ROL_REQUERIDO' })
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
