import { API, pedir } from './api.js'

// HU042 por pasos: emisión presencial de la Constancia de No Adeudo. El trabajador de ventanilla se toma del
// Principal de la sesión en el backend; el frontend crea la solicitud, recibe la firma del dispositivo,
// verifica la deuda y genera la orden; al confirmar el cobro presencial, el backend emite la constancia.
export const MAX_DOCUMENTO = 12
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

/* Firma registrada en ventanilla con la firma electrónica de Servicios en Línea. */
export function firmaEnVentanilla(hash) {
  return btoa(`FIRMA-VENTANILLA:${hash}`)
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

export async function confirmarPagoSolicitud(idSolicitud, datos = {}) {
  const cuerpo = construirParametrosEmision(datos)
  return pedir(`/ventanilla/constancias/${encodeURIComponent(idSolicitud)}/pago`, {
    method: 'POST',
    headers: await cabecerasProtegidas(),
    body: JSON.stringify(cuerpo),
  })
}

function fechaIsoValida(valor) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(valor)) return false
  const fecha = new Date(`${valor}T00:00:00Z`)
  return !Number.isNaN(fecha.getTime()) && fecha.toISOString().slice(0, 10) === valor
}

export function construirParametrosEmision({ predios = [], fechaDesde = '', fechaHasta = '' } = {}) {
  const seleccionados = [...new Set(
    (predios ?? []).map((predio) => String(predio ?? '').trim()).filter(Boolean),
  )]
  const desde = String(fechaDesde ?? '').trim()
  const hasta = String(fechaHasta ?? '').trim()

  if (seleccionados.length > 100) {
    throw errorValidacion('SOLICITUD_INVALIDA', 'No se pueden seleccionar más de 100 predios.')
  }
  if (seleccionados.some((codigo) => codigo.length > 50) || seleccionados.join(', ').length > 400) {
    throw errorValidacion('PREDIOS_EXCEDEN_DOCUMENTO', 'La selección de predios excede el espacio permitido en la constancia.')
  }
  if ((desde && !hasta) || (!desde && hasta)) {
    throw errorValidacion('RANGO_FECHAS_INVALIDO', 'Indica la fecha inicial y la fecha final.')
  }
  if (desde && (!fechaIsoValida(desde) || !fechaIsoValida(hasta) || hasta < desde)) {
    throw errorValidacion('RANGO_FECHAS_INVALIDO', 'El rango de fechas no es válido.')
  }

  return {
    predios: seleccionados,
    fechaDesde: desde || null,
    fechaHasta: hasta || null,
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
