import { pedir } from './api.js'
import { MODO_DEMO } from './authService.js'
import { RECLAMOS_DEMO } from '../mocks/reclamos.js'
import { aFechaHoraLocal, validarReclamo } from '../utils/reclamos.js'

// HU050 / RF-064: registro y seguimiento de reclamos. El API del backend (HU050-Backend) aún no está publicado;
// mientras no responda se usan datos ficticios. Contrato previsto:
//   POST /api/reclamos        { tipoReclamo, referencia, fechaHoraIncidente, comentario } -> reclamo creado
//   GET  /api/reclamos/mios   -> reclamos del usuario autenticado, con su estado y sus derivaciones
// El tipo de reclamante (CONTRIBUYENTE o TRABAJADOR) lo toma el servidor de la sesión, no del cuerpo.
let reclamosDemo = RECLAMOS_DEMO.map(copiar)

function copiar(reclamo) {
  return { ...reclamo, derivaciones: reclamo.derivaciones.map((d) => ({ ...d })) }
}

const esperar = () => new Promise((resolve) => setTimeout(resolve, 250))
const usarDatosDemo = (err) =>
  MODO_DEMO || err.estado === 401 || err.estado === 404 || err.codigo === 'SIN_CONEXION'

export function reiniciarReclamosDemo() {
  reclamosDemo = RECLAMOS_DEMO.map(copiar)
}

// Devuelve { reclamos, demo }; demo indica que los datos son ficticios y no se guardan en el servidor.
export async function listarMisReclamos() {
  try {
    return { reclamos: await pedir('/reclamos/mios'), demo: false }
  } catch (err) {
    if (!usarDatosDemo(err)) throw err
    await esperar()
    return { reclamos: reclamosDemo.map(copiar), demo: true }
  }
}

// Orden descendente por fecha de registro, como se espera ver "lo último primero".
export const ordenarReclamos = (reclamos) =>
  [...reclamos].sort((a, b) => String(b.fechaRegistro).localeCompare(String(a.fechaRegistro)))

export async function registrarReclamo(datos, demo, reclamante = 'TRABAJADOR', ahora = new Date()) {
  const errores = validarReclamo(datos, reclamante, ahora)
  if (Object.keys(errores).length > 0) {
    throw Object.assign(new Error('Revisa los datos del reclamo.'), { codigo: 'RECLAMO_INVALIDO', errores })
  }

  const cuerpo = {
    tipoReclamo: datos.tipoReclamo,
    referencia: (datos.referencia ?? '').trim() || null,
    // datetime-local entrega "AAAA-MM-DDTHH:mm"; el backend espera segundos (LocalDateTime).
    fechaHoraIncidente: datos.fechaHoraIncidente.length === 16 ? `${datos.fechaHoraIncidente}:00` : datos.fechaHoraIncidente,
    comentario: datos.comentario.trim(),
  }

  if (!demo) return pedir('/reclamos', { method: 'POST', body: JSON.stringify(cuerpo) })

  await esperar()
  const siguiente = reclamosDemo.reduce((mayor, r) => Math.max(mayor, r.idReclamo), 0) + 1
  const creado = {
    idReclamo: siguiente,
    numeroReclamo: `REC-${ahora.getFullYear()}-${String(siguiente).padStart(6, '0')}`,
    tipoReclamante: reclamante,
    ...cuerpo,
    estado: 'ENVIADO',
    fechaRegistro: `${aFechaHoraLocal(ahora)}:00`,
    respuesta: null,
    fechaRespuesta: null,
    derivaciones: [],
  }
  reclamosDemo = [creado, ...reclamosDemo]
  return copiar(creado)
}
