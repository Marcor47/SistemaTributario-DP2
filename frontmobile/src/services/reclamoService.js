import { pedir } from './api'
import { validarReclamo } from '../utils/reclamos'

async function csrf() {
  const token = await pedir('/auth/interno/csrf')
  return { 'Content-Type': 'application/json', [token.headerName]: token.token }
}

export const listarMisReclamos = ({ signal } = {}) => pedir('/reclamos/mios', { signal, cache: 'no-store' })

export async function registrarReclamo(datos, ahora = new Date()) {
  const errores = validarReclamo(datos, ahora)
  if (Object.keys(errores).length) throw Object.assign(new Error('Revisa los datos del reclamo.'), { errores })
  return pedir('/reclamos', {
    method: 'POST', headers: await csrf(), body: JSON.stringify({
      tipoReclamo: datos.tipoReclamo,
      referencia: datos.referencia.trim() || null,
      fechaHoraIncidente: datos.fechaHoraIncidente.length === 16 ? `${datos.fechaHoraIncidente}:00` : datos.fechaHoraIncidente,
      comentario: datos.comentario.trim(),
    }),
  })
}

export const cancelarReclamo = async (id) => pedir(`/reclamos/mios/${encodeURIComponent(id)}/cancelar`, {
  method: 'POST', headers: await csrf(),
})
