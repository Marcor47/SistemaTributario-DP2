import { pedir } from './api.js'

// HU001/HU004/HU048: coincidencias por documento o código para el selector.
// El backend busca de forma parcial; se filtra por el criterio elegido para no
// mezclar coincidencias de códigos, documentos o nombres con el mismo texto.
export async function buscarCoincidencias(tipoDocumento, numeroDocumento) {
  const numero = String(numeroDocumento ?? '').replace(/\s/g, '').toUpperCase()
  if (!numero) return []
  const coincide = (c) => tipoDocumento === 'CODIGO'
    ? String(c.codigo).toUpperCase().includes(numero)
    : c.tipoDoc === tipoDocumento && String(c.doc).toUpperCase().includes(numero)

  const encontrados = await pedir(`/contribuyentes/buscar?q=${encodeURIComponent(numero)}`)
  return encontrados
    .filter(coincide)
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
}

// HU001: ficha oficial por tipo y número de documento.
export async function obtenerContribuyentePorDocumento(tipoDocumento, numeroDocumento) {
  const parametros = new URLSearchParams({ tipoDocumento, numeroDocumento })
  return pedir(`/contribuyentes?${parametros.toString()}`)
}
