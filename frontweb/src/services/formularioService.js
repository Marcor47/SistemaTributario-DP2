import { FORMULARIOS } from '../mocks/formularios.js'
import { normalizar } from '../utils/formato.js'

const esperar = () => new Promise((resolve) => setTimeout(resolve, 250))
const versionVigente = (documento) => documento.versiones.reduce((vigente, version) => version.numero > vigente.numero ? version : vigente)
const TIPO_WORD = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
const TAMANO_MAXIMO = 10 * 1024 * 1024

// IndexedDB conserva los archivos y las bajas de esta demostración en el navegador.
// Al integrar la API, estas operaciones se sustituyen dentro del servicio.
async function operarAlmacen(modo, operacion) {
  if (typeof indexedDB === 'undefined') {
    if (modo === 'readonly') return []
    throw new Error('El navegador no permite guardar los cambios localmente.')
  }

  return new Promise((resolve, reject) => {
    const apertura = indexedDB.open('samnet-formularios-demo', 1)
    apertura.onupgradeneeded = () => apertura.result.createObjectStore('cambios', { keyPath: 'codigo' })
    apertura.onerror = () => reject(new Error('No se pudo acceder al catálogo local.'))
    apertura.onblocked = () => reject(new Error('Cierra las otras pestañas del sistema e intenta nuevamente.'))
    apertura.onsuccess = () => {
      const db = apertura.result
      try {
        const transaccion = db.transaction('cambios', modo)
        const peticion = operacion(transaccion.objectStore('cambios'))
        transaccion.oncomplete = () => { db.close(); resolve(peticion.result) }
        transaccion.onabort = () => {
          db.close()
          reject(new Error('No se pudo guardar el cambio. Revisa el espacio disponible y el almacenamiento del navegador.'))
        }
      } catch {
        db.close()
        reject(new Error('No se pudo acceder al catálogo local. Intenta nuevamente.'))
      }
    }
  })
}

const leerCambios = () => operarAlmacen('readonly', (almacen) => almacen.getAll())
const guardarCambio = (cambio) => operarAlmacen('readwrite', (almacen) => almacen.put(cambio))

async function obtenerDocumentos() {
  const documentos = new Map(FORMULARIOS.map((documento) => [documento.codigo, documento]))
  for (const cambio of await leerCambios()) {
    if (cambio.eliminado) documentos.delete(cambio.codigo)
    else documentos.set(cambio.codigo, { ...cambio.documento, archivoLocal: cambio.archivoLocal })
  }
  return [...documentos.values()]
}

async function validarArchivoWord(archivo, nombre = archivo?.name) {
  if (!archivo || !/\.docx$/i.test(nombre)) throw new Error('Selecciona un archivo de Word con extensión .docx.')
  if (archivo.size > TAMANO_MAXIMO) throw new Error('El archivo debe pesar como máximo 10 MB.')
  const contenido = await archivo.arrayBuffer()
  const firma = new Uint8Array(contenido)
  const entradas = new TextDecoder().decode(contenido)
  if (firma[0] !== 0x50 || firma[1] !== 0x4b || firma[2] !== 0x03 || firma[3] !== 0x04
    || !entradas.includes('[Content_Types].xml') || !entradas.includes('word/document.xml')) {
    throw new Error('El archivo no corresponde a un documento Word válido (.docx).')
  }
}

export async function obtenerFormularios(consulta = '') {
  await esperar()
  const texto = normalizar(consulta)
  return (await obtenerDocumentos())
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
        formato: 'Word (.docx)',
      }
    })
}

export async function descargarFormulario(codigo, version) {
  await esperar()
  const documento = (await obtenerDocumentos()).find((item) => item.codigo === codigo)
  if (!documento) throw new Error('El documento no está disponible.')
  const vigente = versionVigente(documento)
  if (vigente.numero !== version) throw new Error('La versión cambió. Actualiza el catálogo para descargar la vigente.')

  let archivo = documento.archivoLocal
  if (!archivo) {
    const respuesta = await fetch(`${import.meta.env.BASE_URL}formularios/${vigente.archivo}`)
    if (!respuesta.ok) {
      throw new Error('No se pudo descargar el documento Word. Intenta nuevamente.')
    }
    archivo = await respuesta.blob()
  }
  // Algunos servidores no identifican el MIME de .docx; validamos el contenido.
  await validarArchivoWord(archivo, vigente.archivo)
  return { archivo: archivo.slice(0, archivo.size, TIPO_WORD), nombre: vigente.archivo }
}

export async function agregarFormulario({ nombre, tipo, descripcion, archivo }) {
  const titulo = nombre.trim()
  const detalle = descripcion.trim()
  if (!titulo || titulo.length > 120) throw new Error('Ingresa un nombre de hasta 120 caracteres.')
  if (!['Plantilla', 'Formulario'].includes(tipo)) throw new Error('Selecciona un tipo de documento válido.')
  if (detalle.length > 300) throw new Error('La descripción debe tener como máximo 300 caracteres.')
  await validarArchivoWord(archivo)

  const cambios = await leerCambios()
  const prefijo = tipo === 'Plantilla' ? 'PL' : 'FO'
  const numeros = [...FORMULARIOS, ...cambios].map(({ codigo }) => {
    const coincidencia = codigo.match(new RegExp(`^${prefijo}-(\\d+)$`))
    return coincidencia ? Number(coincidencia[1]) : 0
  })
  const codigo = `${prefijo}-${String(Math.max(0, ...numeros) + 1).padStart(3, '0')}`
  const hoy = new Date()
  const fechaPublicacion = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`
  const documento = {
    codigo, nombre: titulo, tipo, descripcion: detalle,
    versiones: [{ numero: 1, fechaPublicacion, archivo: archivo.name }],
  }
  await guardarCambio({ codigo, documento, archivoLocal: archivo })
  return documento
}

export async function eliminarFormulario(codigo) {
  if (!(await obtenerDocumentos()).some((documento) => documento.codigo === codigo)) {
    throw new Error('El documento ya no está disponible. Actualiza el catálogo.')
  }
  await guardarCambio({ codigo, eliminado: true })
}
