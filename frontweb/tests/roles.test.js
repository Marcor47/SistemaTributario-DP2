import assert from 'node:assert/strict'
import test from 'node:test'
import { puedeAccederModulo, tieneRolInterno } from '../src/utils/roles.js'

test('HU016: el módulo de recordatorios solo se muestra al rol Administrador', () => {
  const modulo = { roles: ['ADMINISTRADOR'] }

  assert.equal(puedeAccederModulo(modulo, { roles: ['ADMINISTRADOR'], permisos: [] }), true)
  assert.equal(puedeAccederModulo(modulo, { rol: 'ADMINISTRADOR', roles: [], permisos: [] }), true)
  assert.equal(
    puedeAccederModulo(modulo, { roles: ['GERENCIA'], permisos: ['METAS:GESTIONAR'] }),
    false,
  )
  assert.equal(puedeAccederModulo(modulo, null), false)
})

test('los módulos con permiso siguen controlados por sus permisos', () => {
  const modulo = { permiso: 'METAS:GESTIONAR' }

  assert.equal(puedeAccederModulo(modulo, { permisos: ['METAS:GESTIONAR'] }), true)
  assert.equal(puedeAccederModulo(modulo, { roles: ['ADMINISTRADOR'], permisos: [] }), false)
})

test('un módulo sin restricciones está disponible para cualquier sesión', () => {
  assert.equal(puedeAccederModulo({}, {}), true)
  assert.equal(puedeAccederModulo({}, null), true)
})

test('tieneRolInterno acepta el rol principal cuando no llega la lista de roles', () => {
  assert.equal(tieneRolInterno({ rol: 'ADMINISTRADOR' }, ['ADMINISTRADOR']), true)
  assert.equal(tieneRolInterno({ rol: 'GERENCIA' }, ['ADMINISTRADOR']), false)
})
