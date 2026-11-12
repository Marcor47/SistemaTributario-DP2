import { pedir } from './api.js'

const ruta = '/gestion/constancias'
export const listarSolicitudesConstancia = () => pedir(ruta, { cache: 'no-store' })
export const obtenerSolicitudConstancia = (id) => pedir(`${ruta}/${encodeURIComponent(id)}`, { cache: 'no-store' })

async function enviar(id, accion, cuerpo) {
  const token = await pedir('/auth/interno/csrf')
  return pedir(`${ruta}/${encodeURIComponent(id)}/${accion}`, {
    method: 'POST',
    headers: { [token.headerName]: token.token, 'Content-Type': 'application/json' },
    body: cuerpo == null ? undefined : JSON.stringify(cuerpo),
  })
}

export const verificarSolicitudConstancia = (id) => enviar(id, 'verificacion')
export const emitirSolicitudConstancia = (id) => enviar(id, 'emision')
export const rechazarSolicitudConstancia = (id, motivo) => enviar(id, 'rechazo', { motivo })
