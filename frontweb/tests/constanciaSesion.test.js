import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import test from 'node:test'
import { webcrypto } from 'node:crypto'
import {
  documentoConstanciaDemo, firmarConstanciaDemo, iniciarConstanciaDemo, obtenerConstanciaDemo,
} from '../src/mocks/constancia.js'
import { obtenerSesion } from '../src/services/constanciaService.js'

function almacenamiento() {
  const datos = new Map()
  return {
    getItem: (clave) => datos.get(clave) ?? null,
    setItem: (clave, valor) => datos.set(clave, valor),
  }
}

test('la constancia demo pertenece al contribuyente de la sesión y conserva su firma de prueba', async (t) => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto')
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: webcrypto })
  t.after(() => Object.defineProperty(globalThis, 'crypto', descriptor))
  const datos = almacenamiento()
  const solicitud = iniciarConstanciaDemo('DEMO-0001', datos)
  assert.equal(solicitud.estado, 'INICIADA')
  assert.deepEqual(obtenerConstanciaDemo('DEMO-0001', solicitud.idSolicitud, datos), solicitud)
  assert.throws(() => obtenerConstanciaDemo('OTRO', solicitud.idSolicitud, datos), { estado: 404 })
  const documento = await documentoConstanciaDemo('DEMO-0001', solicitud.idSolicitud, datos)
  assert.equal(documento.documentoDePrueba, true)
  assert.match(Buffer.from(documento.contenidoBase64, 'base64').toString(), /DOCUMENTO DE PRUEBA/)
  const firma = btoa(`FIRMA-DE-PRUEBA:${documento.hashSha256}`)
  const firmada = await firmarConstanciaDemo('DEMO-0001', solicitud.idSolicitud, firma, 'CERTIFICADO DE PRUEBA', datos)
  assert.equal(firmada.estado, 'FIRMADA')
  assert.equal(obtenerConstanciaDemo('DEMO-0001', solicitud.idSolicitud, datos).firmaContribuyente.hashDocumento,
    documento.hashSha256)
})

test('sin modo demo la constancia consulta la sesión real con cookie', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    llamadas.push({ url, opciones })
    return new Response(JSON.stringify({ codigoContribuyente: '014502' }), { status: 200 })
  })
  assert.deepEqual(await obtenerSesion(), { codigoContribuyente: '014502' })
  assert.equal(llamadas.length, 1)
  assert.ok(llamadas[0].url.endsWith('/auth/me'))
  assert.equal(llamadas[0].opciones.credentials, 'include')
})
