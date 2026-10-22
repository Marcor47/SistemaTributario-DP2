import { pedir } from './api.js'

// HU001/HU004: coincidencias por documento para el selector de la pantalla.
// El backend busca de forma parcial; se filtra por tipo y número para no mezclar
// coincidencias de códigos o nombres que contengan el mismo texto.
export async function buscarCoincidencias(tipoDocumento, numeroDocumento) {
  const numero = String(numeroDocumento ?? '').replace(/\s/g, '').toUpperCase()
  if (!numero) return []

  const encontrados = await pedir(`/estado-cuenta/contribuyentes?q=${encodeURIComponent(numero)}`)
  return encontrados
    .filter((c) => c.tipoDoc === tipoDocumento && String(c.doc).toUpperCase().includes(numero))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
}

// HU001: ficha oficial por tipo y número de documento.
export async function obtenerContribuyentePorDocumento(tipoDocumento, numeroDocumento) {
  const parametros = new URLSearchParams({ tipoDocumento, numeroDocumento })
  return pedir(`/contribuyentes?${parametros.toString()}`)
}
