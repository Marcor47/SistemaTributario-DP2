import { Platform } from 'react-native'
import { File, Paths } from 'expo-file-system'
import { API, pedir } from './api'

export const listarBuzon = ({ signal } = {}) => pedir('/buzon', { signal, cache: 'no-store' })
export const abrirDocumento = (id, { signal } = {}) => pedir(`/buzon/${encodeURIComponent(id)}`, { signal, cache: 'no-store' })

export async function descargarDocumento(id) {
  const respuesta = await fetch(`${API}/buzon/${encodeURIComponent(id)}/descarga`, { credentials: 'include' })
  if (!respuesta.ok) throw new Error('No se pudo descargar el documento. Vuelve a abrirlo e intenta nuevamente.')
  if (Platform.OS === 'web') {
    const url = URL.createObjectURL(await respuesta.blob())
    const enlace = document.createElement('a')
    enlace.href = url
    enlace.download = `documento-${id}.pdf`
    enlace.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } else {
    const archivo = new File(Paths.cache, `documento-${id}-${Date.now()}.pdf`)
    archivo.create()
    await archivo.write(new Uint8Array(await respuesta.arrayBuffer()))
    await archivo.preview()
  }
}
