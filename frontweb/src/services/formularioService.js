import { pedir } from './api.js'
import { normalizar } from '../utils/formato.js'

const API = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api'
const TAMANO_MAXIMO = 10 * 1024 * 1024

// HU049: catálogo de formularios y plantillas publicados en la BD (operativo.plantilla_documento).
// El código indica el tipo: PL-### son plantillas y FO-### formularios.
export const tipoDeCodigo = (codigo) => (codigo?.startsWith('PL-') ? 'Plantilla' : 'Formulario')

async function cabecerasCsrf() {
  const token = await pedir('/auth/interno/csrf')
  return { [token.headerName]: token.token }
}

function normalizarFormulario(resumen) {
  return {
    codigo: resumen.codigo,
    nombre: resumen.nombre,
    tipo: tipoDeCodigo(resumen.codigo),
    descripcion: resumen.descripcion || '',
    version: resumen.version,
    fechaPublicacion: resumen.fechaPublicacion?.slice(0, 10),
    formato: resumen.formato,
    archivo: resumen.archivo,
  }
}

export async function obtenerFormularios(consulta = '') {
  const texto = normalizar(consulta)
  const formularios = (await pedir('/formularios')).map(normalizarFormulario)
  return formularios.filter((documento) =>
    normalizar(`${documento.codigo} ${documento.nombre} ${documento.tipo} ${documento.descripcion}`).includes(texto))
}

export async function descargarFormulario(codigo, version) {
  const vigentes = await obtenerFormularios()
  const documento = vigentes.find((item) => item.codigo === codigo)
  if (!documento) throw new Error('El documento no está disponible.')
  if (documento.version !== version) throw new Error('La versión cambió. Actualiza el catálogo para descargar la vigente.')
  let respuesta
  try {
    respuesta = await fetch(`${API}/formularios/${encodeURIComponent(codigo)}/descarga`, { credentials: 'include' })
  } catch {
    throw new Error('No se pudo conectar con el servicio. Intenta nuevamente.')
  }
  if (!respuesta.ok) throw new Error('No se pudo descargar el documento. Intenta nuevamente.')
  return { archivo: await respuesta.blob(), nombre: documento.archivo || `${codigo}-v${version}` }
}

function siguienteCodigo(tipo, existentes) {
  const prefijo = tipo === 'Plantilla' ? 'PL' : 'FO'
  const numeros = existentes.map(({ codigo }) => {
    const coincidencia = codigo.match(new RegExp(`^${prefijo}-([0-9]+)$`))
    return coincidencia ? Number(coincidencia[1]) : 0
  })
  return `${prefijo}-${String(Math.max(0, ...numeros) + 1).padStart(3, '0')}`
}

export async function agregarFormulario({ nombre, tipo, descripcion, archivo }) {
  const titulo = (nombre || '').trim()
  const detalle = (descripcion || '').trim()
  if (!titulo || titulo.length > 120) throw new Error('Ingresa un nombre de hasta 120 caracteres.')
  if (!['Plantilla', 'Formulario'].includes(tipo)) throw new Error('Selecciona un tipo de documento válido.')
  if (detalle.length > 300) throw new Error('La descripción debe tener como máximo 300 caracteres.')
  if (!archivo || !/\.(docx|pdf)$/i.test(archivo.name)) throw new Error('Selecciona un archivo Word (.docx) o PDF.')
  if (archivo.size > TAMANO_MAXIMO) throw new Error('El archivo debe pesar como máximo 10 MB.')

  const codigo = siguienteCodigo(tipo, await obtenerFormularios())
  const datos = new FormData()
  datos.append('codigo', codigo)
  datos.append('nombre', titulo)
  if (detalle) datos.append('descripcion', detalle)
  datos.append('archivo', archivo)
  let respuesta
  try {
    respuesta = await fetch(`${API}/formularios`, {
      method: 'POST', credentials: 'include', headers: await cabecerasCsrf(), body: datos,
    })
  } catch {
    throw new Error('No se pudo conectar con el servicio. Intenta nuevamente.')
  }
  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => null)
    throw new Error(cuerpo?.mensaje || 'No se pudo publicar el documento. Verifica que sea un .docx o PDF válido.')
  }
  return normalizarFormulario(await respuesta.json())
}

export async function eliminarFormulario(codigo) {
  await pedir(`/formularios/${encodeURIComponent(codigo)}`, { method: 'DELETE', headers: await cabecerasCsrf() })
}
