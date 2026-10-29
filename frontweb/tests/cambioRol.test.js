import assert from 'node:assert/strict'
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

test('HU035: sin sesión ni backend se cargan usuarios y los siete roles ficticios', async () => {
  reiniciarUsuariosDemo()
  const datos = await cargarUsuariosYRoles()
  assert.equal(datos.demo, true)
  assert.equal(datos.usuarios.length, USUARIOS_DEMO.length)
  assert.equal(datos.roles.length, 7)
  assert.ok(datos.roles.some((r) => r.nombre === 'GESTOR_RECLAMOS'))
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
