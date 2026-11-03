import { pedir } from './api.js'
import { MODO_DEMO } from './authService.js'
import { RECLAMOS_DEMO } from '../mocks/reclamos.js'
import { aFechaHoraLocal, validarReclamo } from '../utils/reclamos.js'

// HU050 / RF-064: registro y seguimiento de reclamos.
//   POST /api/reclamos        { tipoReclamo, referencia, fechaHoraIncidente, comentario } -> reclamo creado
//   GET  /api/reclamos/mios   -> reclamos del usuario autenticado, con su estado y sus derivaciones
// El tipo de reclamante (CONTRIBUYENTE o TRABAJADOR) lo toma el servidor de la sesión, no del cuerpo.
let reclamosDemo = RECLAMOS_DEMO.map(copiar)

function copiar(reclamo) {
  return { ...reclamo, derivaciones: reclamo.derivaciones.map((d) => ({ ...d })) }
}

const esperar = () => new Promise((resolve) => setTimeout(resolve, 250))
const usarDatosDemo = () => MODO_DEMO

async function obtenerCsrf() {
  const token = await pedir('/auth/interno/csrf')
  return { [token.headerName]: token.token, 'Content-Type': 'application/json' }
}

async function enviarProtegido(ruta, cuerpo = null) {
  return pedir(ruta, {
    method: 'POST',
    headers: await obtenerCsrf(),
    body: cuerpo == null ? undefined : JSON.stringify(cuerpo),
  })
}

export function reiniciarReclamosDemo() {
  reclamosDemo = RECLAMOS_DEMO.map(copiar)
}

// Devuelve { reclamos, demo }; demo indica que los datos son ficticios y no se guardan en el servidor.
export async function listarMisReclamos(demo = usarDatosDemo()) {
  if (!demo) return { reclamos: await pedir('/reclamos/mios'), demo: false }
  await esperar()
  return { reclamos: reclamosDemo.map(copiar), demo: true }
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

  if (!demo) return pedir('/reclamos', { method: 'POST', headers: await obtenerCsrf(), body: JSON.stringify(cuerpo) })

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

export const cancelarReclamo = (idReclamo) => enviarProtegido(`/reclamos/mios/${idReclamo}/cancelar`)

export async function listarReclamosGestion() {
  const datos = await pedir('/reclamos/gestion')
  return datos.map(normalizarGestion)
}

export const cargarCatalogosReclamos = () => pedir('/reclamos/catalogos')

export async function derivarReclamo(idReclamo, datos) {
  return normalizarGestion(await enviarProtegido(`/reclamos/gestion/${idReclamo}/derivaciones`, datos))
}

export async function listarReclamosAsignados() {
  const datos = await pedir('/reclamos/asignados')
  return datos.map(normalizarGestion)
}

export async function responderReclamo(idReclamo, respuesta) {
  return normalizarGestion(await enviarProtegido(`/reclamos/asignados/${idReclamo}/respuesta`, { respuesta }))
}

function normalizarGestion(datos) {
  return {
    ...datos.reclamo,
    reclamante: datos.reclamante,
    idAreaActual: datos.idAreaActual,
    areaActual: datos.areaActual,
    idResponsableActual: datos.idResponsableActual,
    responsableActual: datos.responsableActual,
  }
}
