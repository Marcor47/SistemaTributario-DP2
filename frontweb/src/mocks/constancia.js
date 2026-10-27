// Flujo local exclusivo de la cuenta DEMO-0001. Nunca representa una solicitud municipal real.
const prefijo = 'constancia-demo-'

function clave(codigo) {
  return `${prefijo}${codigo}`
}

function noEncontrada() {
  return Object.assign(new Error('No tienes una solicitud de constancia con ese número.'), { estado: 404 })
}

export function iniciarConstanciaDemo(codigo, almacenamiento = sessionStorage) {
  const actual = leerConstanciaDemo(codigo, almacenamiento)
  if (actual && !['EMITIDA', 'RECHAZADA'].includes(actual.estado)) {
    throw new Error(`Ya tienes la solicitud de constancia N.º ${actual.idSolicitud} en curso.`)
  }
  const solicitud = {
    idSolicitud: Date.now(), canal: 'APP', estado: 'INICIADA',
    fechaSolicitud: new Date().toISOString(), motivoRechazo: null, firmaContribuyente: null,
  }
  almacenamiento.setItem(clave(codigo), JSON.stringify(solicitud))
  return solicitud
}

export function leerConstanciaDemo(codigo, almacenamiento = sessionStorage) {
  try {
    const dato = almacenamiento.getItem(clave(codigo))
    return dato ? JSON.parse(dato) : null
  } catch {
    return null
  }
}

export function obtenerConstanciaDemo(codigo, id, almacenamiento = sessionStorage) {
  const solicitud = leerConstanciaDemo(codigo, almacenamiento)
  if (!solicitud || String(solicitud.idSolicitud) !== String(id)) throw noEncontrada()
  return solicitud
}

export async function documentoConstanciaDemo(codigo, id, almacenamiento = sessionStorage) {
  const solicitud = obtenerConstanciaDemo(codigo, id, almacenamiento)
  if (solicitud.estado !== 'INICIADA') throw new Error('Esta solicitud ya no requiere firma.')
  const texto = `DOCUMENTO DE PRUEBA\nSOLICITUD DE CONSTANCIA DE NO ADEUDO\nSolicitud N.º: ${id}\nContribuyente: ${codigo}\nFecha: ${solicitud.fechaSolicitud}\n`
  const bytes = new TextEncoder().encode(texto)
  const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes)
  const hashSha256 = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
  return {
    idSolicitud: solicitud.idSolicitud,
    nombreArchivo: `solicitud-constancia-${id}-DEMO.txt`,
    tipoContenido: 'text/plain; charset=UTF-8',
    contenidoBase64: btoa(String.fromCharCode(...bytes)),
    hashSha256,
    documentoDePrueba: true,
  }
}

export async function firmarConstanciaDemo(codigo, id, firmaBase64, certificado, almacenamiento = sessionStorage) {
  const documento = await documentoConstanciaDemo(codigo, id, almacenamiento)
  if (certificado !== 'CERTIFICADO DE PRUEBA'
      || firmaBase64 !== btoa(`FIRMA-DE-PRUEBA:${documento.hashSha256}`)) {
    throw new Error('En la demostración solo se acepta la firma de prueba generada en esta pantalla.')
  }
  const solicitud = obtenerConstanciaDemo(codigo, id, almacenamiento)
  const firmada = {
    ...solicitud, estado: 'FIRMADA',
    firmaContribuyente: {
      entidadCertificadora: 'CERTIFICADO DE PRUEBA',
      fechaFirma: new Date().toISOString(), hashDocumento: documento.hashSha256,
    },
  }
  almacenamiento.setItem(clave(codigo), JSON.stringify(firmada))
  return firmada
}
