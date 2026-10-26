import { pedir } from './api.js'

export const obtenerSesion = () => pedir('/auth/me')
export const iniciarSolicitud = () => pedir('/constancias', { method: 'POST' })
export const obtenerSolicitud = (id) => pedir(`/constancias/${encodeURIComponent(id)}`)
export const obtenerDocumento = (id) => pedir(`/constancias/${encodeURIComponent(id)}/documento-firma`)
export const firmarSolicitud = (id, firmaBase64, certificado) => pedir(`/constancias/${encodeURIComponent(id)}/firma`, {
  method: 'POST',
  body: JSON.stringify({ firmaBase64, certificado }),
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
