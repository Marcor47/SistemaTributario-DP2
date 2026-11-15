import { API, pedir } from './api.js'

// Cada cambio en el buzón (leer, marcar no leído, archivar, destacar) avisa a la campana de la barra superior para
// que vuelva a contar los mensajes sin leer desde el servidor.
export const EVENTO_BUZON_ACTUALIZADO = 'buzon-actualizado'

function avisarCambio(resultado) {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(EVENTO_BUZON_ACTUALIZADO))
  return resultado
}

async function csrf() {
  const token = await pedir('/auth/interno/csrf')
  return { 'Content-Type': 'application/json', [token.headerName]: token.token }
}

export function listarBuzon({ vista = 'RECIBIDOS', busqueda = '', tipo = '', leido = '', pagina = 0, tamanio = 10, signal } = {}) {
  const parametros = new URLSearchParams({ vista, pagina: String(pagina), tamanio: String(tamanio) })
  if (busqueda.trim()) parametros.set('busqueda', busqueda.trim())
  if (tipo) parametros.set('tipo', tipo)
  if (leido !== '') parametros.set('leido', String(leido))
  return pedir(`/buzon?${parametros}`, { signal })
}

export const abrirMensajeBuzon = (idDocumento) => pedir(`/buzon/${idDocumento}`).then(avisarCambio)

export const marcarMensajeNoLeido = async (idDocumento) => pedir(`/buzon/${idDocumento}/no-leido`, {
  method: 'POST',
  headers: await csrf(),
}).then(avisarCambio)

async function cambiarEstado(idDocumento, accion, valor) {
  return pedir(`/buzon/${idDocumento}/${accion}`, {
    method: 'PATCH',
    headers: await csrf(),
    body: JSON.stringify({ valor }),
  }).then(avisarCambio)
}

export const cambiarDestacadoBuzon = (idDocumento, valor) => cambiarEstado(idDocumento, 'destacado', valor)

export const cambiarArchivadoBuzon = (idDocumento, valor) => cambiarEstado(idDocumento, 'archivado', valor)

export async function descargarDocumentoBuzon(idDocumento) {
  const respuesta = await fetch(`${API}/buzon/${idDocumento}/descarga`, { credentials: 'include' })
  if (!respuesta.ok) throw new Error('No se pudo descargar el documento.')
  return respuesta.blob()
}
