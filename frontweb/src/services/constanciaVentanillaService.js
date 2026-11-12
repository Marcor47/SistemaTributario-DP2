import { API, pedir } from './api.js'

// HU042 por pasos: emisión presencial de la Constancia de No Adeudo. El trabajador de ventanilla se toma del
// Principal de la sesión en el backend; el frontend crea la solicitud, recibe la firma del dispositivo,
// verifica la deuda, genera la orden, confirma el cobro presencial y emite la constancia.
export const MAX_DOCUMENTO = 12
export const MAX_PREDIOS_TEXTO = 400
export const MAX_RANGO_FECHAS = 60
export const MINIMO_SUGERENCIAS = 2

function errorValidacion(codigo, mensaje) {
  return Object.assign(new Error(mensaje), { codigo, estado: 400, detalles: [] })
}

export function normalizarDocumento(valor) {
  return String(valor ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
}

function documentoValido(valor) {
  const documento = normalizarDocumento(valor)
  if (!documento) throw errorValidacion('DOCUMENTO_REQUERIDO', 'Ingresa el DNI, RUC o código del contribuyente.')
  if (documento.length > MAX_DOCUMENTO) {
    throw errorValidacion('DOCUMENTO_INVALIDO', `El documento no puede superar ${MAX_DOCUMENTO} caracteres.`)
  }
  return documento
}

export async function consultarContribuyente(documento) {
  const limpio = documentoValido(documento)
  return pedir(`/ventanilla/constancias/consulta?documento=${encodeURIComponent(limpio)}`)
}

// Sugerencias del padrón para el buscador de ventanilla. Se filtra por documento o código para no
// mezclar coincidencias por nombre con un campo que espera un documento.
export async function buscarCoincidenciasVentanilla(texto) {
  const limpio = normalizarDocumento(texto)
  if (limpio.length < MINIMO_SUGERENCIAS) return []
  const encontrados = await pedir(`/contribuyentes/buscar?q=${encodeURIComponent(limpio)}`)
  return encontrados.filter((contribuyente) => (
    String(contribuyente.doc ?? '').toUpperCase().includes(limpio)
    || String(contribuyente.codigo ?? '').toUpperCase().includes(limpio)
  ))
}

async function cabecerasProtegidas() {
  const token = await pedir('/auth/interno/csrf')
  return { [token.headerName]: token.token, 'Content-Type': 'application/json' }
}

/* Solicitudes del contribuyente (ambos canales y todos los estados) para retomar o revisar una cerrada. */
export async function listarSolicitudesContribuyente(documento) {
  const limpio = documentoValido(documento)
  return pedir(`/ventanilla/constancias/solicitudes?documento=${encodeURIComponent(limpio)}`)
}

/* Crear la solicitud; si el contribuyente ya tiene una abierta, el backend la retoma. */
export async function crearSolicitudVentanilla(documento) {
  const documentoContribuyente = documentoValido(documento)
  return pedir('/ventanilla/constancias/solicitudes', {
    method: 'POST',
    headers: await cabecerasProtegidas(),
    body: JSON.stringify({ documentoContribuyente }),
  })
}

export const obtenerDocumentoFirmaSolicitud = (idSolicitud) => (
  pedir(`/ventanilla/constancias/${encodeURIComponent(idSolicitud)}/documento-firma`)
)

/* La firma simulada usa el mismo patrón del firmador de prueba: sin validez legal. */
export function firmaDePrueba(hash) {
  return btoa(`FIRMA-DE-PRUEBA:${hash}`)
}

export async function registrarFirmaSolicitud(idSolicitud, firmaBase64, certificado) {
  return pedir(`/ventanilla/constancias/${encodeURIComponent(idSolicitud)}/firma`, {
    method: 'POST',
    headers: await cabecerasProtegidas(),
    body: JSON.stringify({ firmaBase64, certificado }),
  })
}

export async function verificarDeudaSolicitud(idSolicitud) {
  return pedir(`/ventanilla/constancias/${encodeURIComponent(idSolicitud)}/verificacion`, {
    method: 'POST',
    headers: await cabecerasProtegidas(),
  })
}

export const obtenerOrdenPagoSolicitud = (idSolicitud) => (
  pedir(`/ventanilla/constancias/${encodeURIComponent(idSolicitud)}/orden-pago`)
)

export async function confirmarPagoSolicitud(idSolicitud) {
  return pedir(`/ventanilla/constancias/${encodeURIComponent(idSolicitud)}/pago`, {
    method: 'POST',
    headers: await cabecerasProtegidas(),
  })
}

export function construirParametrosEmision({ predios = [], prediosTexto = '', rangoFechas = '' } = {}) {
  const seleccionados = [...new Set(
    (predios ?? []).map((predio) => String(predio ?? '').trim()).filter(Boolean),
  )]
  const texto = String(prediosTexto ?? '').trim()
  const rango = String(rangoFechas ?? '').trim()

  if (texto.length > MAX_PREDIOS_TEXTO) {
    throw errorValidacion('SOLICITUD_INVALIDA', `La descripción de predios no puede superar ${MAX_PREDIOS_TEXTO} caracteres.`)
  }
  if (rango.length > MAX_RANGO_FECHAS) {
    throw errorValidacion('SOLICITUD_INVALIDA', `El rango de fechas no puede superar ${MAX_RANGO_FECHAS} caracteres.`)
  }

  return {
    predios: seleccionados,
    // El backend prioriza los predios seleccionados; el texto libre solo aplica si no hay ninguno.
    prediosTexto: seleccionados.length === 0 && texto ? texto : null,
    rangoFechas: rango || null,
  }
}

export async function emitirSolicitud(idSolicitud, datos) {
  const cuerpo = construirParametrosEmision(datos)
  return pedir(`/ventanilla/constancias/${encodeURIComponent(idSolicitud)}/emitir`, {
    method: 'POST',
    headers: await cabecerasProtegidas(),
    body: JSON.stringify(cuerpo),
  })
}

export async function listarConstancias(documento) {
  const limpio = normalizarDocumento(documento)
  const consulta = limpio ? `?documento=${encodeURIComponent(limpio)}` : ''
  return pedir(`/ventanilla/constancias${consulta}`)
}

export const urlConstanciaPdf = (numeroConstancia) => (
  `${API}/ventanilla/constancias/${encodeURIComponent(numeroConstancia)}/descarga`
)

export function textoDocumento(base64) {
  return new TextDecoder().decode(Uint8Array.from(atob(base64), (caracter) => caracter.charCodeAt(0)))
}
