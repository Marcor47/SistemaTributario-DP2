import * as DocumentPicker from 'expo-document-picker'
import { File } from 'expo-file-system'
import { pedir } from './api'

export const iniciarConstancia = () => pedir('/constancias', { method: 'POST' })
export const obtenerConstancia = (id) => pedir(`/constancias/${encodeURIComponent(id)}`)
export const obtenerDocumentoFirma = (id) => pedir(`/constancias/${encodeURIComponent(id)}/documento-firma`)
export const enviarFirmaConstancia = (id, firmaBase64, certificado) => pedir(`/constancias/${encodeURIComponent(id)}/firma`, {
  method: 'POST', body: JSON.stringify({ firmaBase64, certificado }),
})

export async function elegirFirma() {
  const resultado = await DocumentPicker.getDocumentAsync({ type: '*/*', copyToCacheDirectory: true })
  if (resultado.canceled || !resultado.assets?.length) return null
  const archivo = resultado.assets[0]
  const tamano = archivo.size ?? archivo.file?.size ?? new File(archivo.uri).size
  if (!tamano || tamano > 48 * 1024) throw new Error('Selecciona una firma digital de hasta 48 KB.')
  if (!/\.(p7s|sig)$/i.test(archivo.name)) throw new Error('Selecciona un archivo de firma .p7s o .sig.')
  const firmaBase64 = archivo.base64 || await new File(archivo.uri).base64()
  if (!firmaBase64) throw new Error('No se pudo leer el archivo de firma.')
  return { nombre: archivo.name, firmaBase64 }
}

const ALFABETO = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

export function firmaDePrueba(hash) {
  const bytes = Array.from(`FIRMA-DE-PRUEBA:${hash}`, (c) => c.charCodeAt(0))
  let salida = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i], b = bytes[i + 1], c = bytes[i + 2]
    salida += ALFABETO[a >> 2] + ALFABETO[((a & 3) << 4) | ((b ?? 0) >> 4)]
      + (b === undefined ? '=' : ALFABETO[((b & 15) << 2) | ((c ?? 0) >> 6)])
      + (c === undefined ? '=' : ALFABETO[c & 63])
  }
  return salida
}

export function textoDocumento(base64) {
  const limpio = base64.replace(/=+$/, '')
  const bytes = []
  for (let i = 0; i < limpio.length; i += 4) {
    const a = ALFABETO.indexOf(limpio[i]), b = ALFABETO.indexOf(limpio[i + 1])
    const c = limpio[i + 2] ? ALFABETO.indexOf(limpio[i + 2]) : 0
    const d = limpio[i + 3] ? ALFABETO.indexOf(limpio[i + 3]) : 0
    bytes.push((a << 2) | (b >> 4))
    if (limpio[i + 2]) bytes.push(((b & 15) << 4) | (c >> 2))
    if (limpio[i + 3]) bytes.push(((c & 3) << 6) | d)
  }
  return decodeURIComponent(bytes.map((n) => `%${n.toString(16).padStart(2, '0')}`).join(''))
}
