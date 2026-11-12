import { Platform } from 'react-native'
import { File, Paths } from 'expo-file-system'
import { API, pedir } from './api'

async function csrf() {
  const token = await pedir('/auth/interno/csrf')
  return { 'Content-Type': 'application/json', [token.headerName]: token.token }
}

export function listarBuzon({ vista = 'RECIBIDOS', busqueda = '', tipo = '', leido = '', pagina = 0, tamanio = 10, signal } = {}) {
  const parametros = new URLSearchParams({ vista, pagina: String(pagina), tamanio: String(tamanio) })
  if (busqueda.trim()) parametros.set('busqueda', busqueda.trim())
  if (tipo) parametros.set('tipo', tipo)
  if (leido !== '') parametros.set('leido', String(leido))
  return pedir(`/buzon?${parametros}`, { signal, cache: 'no-store' })
}
export const abrirDocumento = (id, { signal } = {}) => pedir(`/buzon/${encodeURIComponent(id)}`, { signal, cache: 'no-store' })

export const marcarNoLeido = async (id) => pedir(`/buzon/${encodeURIComponent(id)}/no-leido`, {
  method: 'POST', headers: await csrf(),
})

async function cambiarEstado(id, accion, valor) {
  return pedir(`/buzon/${encodeURIComponent(id)}/${accion}`, {
    method: 'PATCH', headers: await csrf(), body: JSON.stringify({ valor }),
  })
}

export const cambiarDestacado = (id, valor) => cambiarEstado(id, 'destacado', valor)
export const cambiarArchivado = (id, valor) => cambiarEstado(id, 'archivado', valor)

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
