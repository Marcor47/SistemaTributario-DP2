import { pedir } from './api.js'
import { MODO_DEMO, obtenerSesionDemo } from './authService.js'
import {
  documentoConstanciaDemo, firmarConstanciaDemo, iniciarConstanciaDemo, obtenerConstanciaDemo,
} from '../mocks/constancia.js'

function sesionDemo() {
  const codigo = obtenerSesionDemo()?.codigo
  if (!codigo) {
    throw Object.assign(new Error('Inicia sesión con tu cuenta de contribuyente.'), { estado: 401 })
  }
  return codigo
}

export const obtenerSesion = () => MODO_DEMO
  ? Promise.resolve({ codigoContribuyente: sesionDemo(), esDemo: true }) : pedir('/auth/me')
export const iniciarSolicitud = () => MODO_DEMO
  ? Promise.resolve(iniciarConstanciaDemo(sesionDemo())) : pedir('/constancias', { method: 'POST' })
export const obtenerSolicitud = (id) => MODO_DEMO
  ? Promise.resolve(obtenerConstanciaDemo(sesionDemo(), id)) : pedir(`/constancias/${encodeURIComponent(id)}`)
export const obtenerDocumento = (id) => MODO_DEMO
  ? documentoConstanciaDemo(sesionDemo(), id) : pedir(`/constancias/${encodeURIComponent(id)}/documento-firma`)
export const firmarSolicitud = (id, firmaBase64, certificado) => MODO_DEMO
  ? firmarConstanciaDemo(sesionDemo(), id, firmaBase64, certificado)
  : pedir(`/constancias/${encodeURIComponent(id)}/firma`, {
    method: 'POST', body: JSON.stringify({ firmaBase64, certificado }),
  })

export function decodificarBase64(base64) {
  const binario = atob(base64)
  return Uint8Array.from(binario, (caracter) => caracter.charCodeAt(0))
}

export async function codificarArchivo(archivo) {
  if (!archivo || archivo.size === 0 || archivo.size > 48 * 1024) {
    throw new Error('Selecciona una firma de hasta 48 KB.')
  }
  const bytes = new Uint8Array(await archivo.arrayBuffer())
  let binario = ''
  for (let inicio = 0; inicio < bytes.length; inicio += 8192) {
    binario += String.fromCharCode(...bytes.subarray(inicio, inicio + 8192))
  }
  return btoa(binario)
}
