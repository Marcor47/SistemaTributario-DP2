import { FORMULARIOS } from '../mocks/formularios.js'
import { normalizar } from '../utils/formato.js'

const esperar = () => new Promise((resolve) => setTimeout(resolve, 250))
const versionVigente = (documento) => documento.versiones.reduce((vigente, version) => version.numero > vigente.numero ? version : vigente)

// Al integrar la API se sustituyen las respuestas de este servicio, sin cambiar
// la pantalla. Las fechas y versiones provienen del catálogo, no del navegador.
export async function obtenerFormularios(consulta = '') {
  await esperar()
  const texto = normalizar(consulta)
  return FORMULARIOS
    .filter((documento) => normalizar(`${documento.codigo} ${documento.nombre} ${documento.tipo} ${documento.descripcion}`).includes(texto))
    .map((documento) => {
      const vigente = versionVigente(documento)
      return {
        codigo: documento.codigo,
        nombre: documento.nombre,
        tipo: documento.tipo,
        descripcion: documento.descripcion,
        version: vigente.numero,
        fechaPublicacion: vigente.fechaPublicacion,
        formato: 'PDF',
      }
    })
}

export async function descargarFormulario(codigo, version) {
  await esperar()
  const documento = FORMULARIOS.find((item) => item.codigo === codigo)
  if (!documento) throw new Error('El documento no está disponible.')
  const vigente = versionVigente(documento)
  if (vigente.numero !== version) throw new Error('La versión cambió. Actualiza el catálogo para descargar la vigente.')

  const respuesta = await fetch(`${import.meta.env.BASE_URL}formularios/${vigente.archivo}`)
  if (!respuesta.ok || !respuesta.headers.get('content-type')?.includes('application/pdf')) {
    throw new Error('No se pudo descargar el documento. Intenta nuevamente.')
  }
  return { archivo: await respuesta.blob(), nombre: vigente.archivo }
}
