import { Platform } from 'react-native'
import { File, Paths } from 'expo-file-system'
import { API, pedir } from './api'

export const listarConstancias = () => pedir('/constancias')
export const consultarDeudaConstancia = () => pedir('/constancias/deuda')
export const iniciarConstancia = () => pedir('/constancias', { method: 'POST' })
export const obtenerConstancia = (id) => pedir(`/constancias/${encodeURIComponent(id)}`)
export const obtenerDocumentoFirma = (id) => pedir(`/constancias/${encodeURIComponent(id)}/documento-firma`)
export const enviarFirmaConstancia = (id, firmaBase64, certificado) => pedir(`/constancias/${encodeURIComponent(id)}/firma`, {
  method: 'POST', body: JSON.stringify({ firmaBase64, certificado }),
})
export const crearOrdenConstancia = (id) => pedir(`/constancias/${encodeURIComponent(id)}/orden`, { method: 'POST' })
export const obtenerOrdenConstancia = (id) => pedir(`/constancias/${encodeURIComponent(id)}/orden`)
export const confirmarPagoConstancia = (id) => pedir(`/constancias/${encodeURIComponent(id)}/pago`, { method: 'POST' })
export const emitirConstancia = (id) => pedir(`/constancias/${encodeURIComponent(id)}/emitir`, { method: 'POST' })
export const listarBuzonConstancias = () => pedir('/constancias/buzon')

export async function descargarConstancia(id) {
  const respuesta = await fetch(`${API}/constancias/buzon/${encodeURIComponent(id)}/descarga`, { credentials: 'include' })
  if (!respuesta.ok) throw new Error('No se pudo descargar la constancia. Inténtalo nuevamente.')
  if (Platform.OS === 'web') {
    const url = URL.createObjectURL(await respuesta.blob())
    const enlace = document.createElement('a')
    enlace.href = url
    enlace.download = `constancia-${id}.pdf`
    enlace.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } else {
    const archivo = new File(Paths.document, `constancia-${id}-${Date.now()}.pdf`)
    archivo.create()
    archivo.write(new Uint8Array(await respuesta.arrayBuffer()))
    await archivo.preview()
  }
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
