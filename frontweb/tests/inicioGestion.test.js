import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

test('Inicio Gestión: existe el módulo de inicio para la sección de gestión en MODULOS', () => {
  const modulos = readFileSync(new URL('../src/utils/modulos.jsx', import.meta.url), 'utf8')
  assert.match(modulos, /ruta:\s*['"]\/gestion\/inicio['"]/)
  assert.match(modulos, /texto:\s*['"]Inicio['"]/)
  assert.match(modulos, /seccion:\s*['"]gestion['"]/)
  assert.match(modulos, /export function rutaInicialGestion/)
})

test('Inicio Gestión: rutaInicialGestion redirige a /gestion/inicio cuando hay sesión', () => {
  const modulos = readFileSync(new URL('../src/utils/modulos.jsx', import.meta.url), 'utf8')
  assert.match(modulos, /if \(!sesion\) return '\/gestion\/login'/)
  assert.match(modulos, /return '\/gestion\/inicio'/)
})

test('Inicio Gestión: App.jsx registra la ruta /gestion/inicio y el layout de gestión', () => {
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8')
  assert.match(app, /path="\/gestion\/inicio"/)
  assert.match(app, /InicioGestion/)
  assert.match(app, /path="\/gestion" element={<Navigate to="\/gestion\/inicio" replace \/>}/)
})

test('Inicio Gestión: MenuLateral incluye rutaInicio hacia /gestion/inicio para gestión', () => {
  const menu = readFileSync(new URL('../src/components/MenuLateral.jsx', import.meta.url), 'utf8')
  assert.match(menu, /solo === 'gestion' \? '\/gestion\/inicio'/)
})
