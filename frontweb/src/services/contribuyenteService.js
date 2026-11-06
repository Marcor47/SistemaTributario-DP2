import { pedir } from './api.js'
import { MODO_DEMO } from './authService.js'
import { CONTRIBUYENTES } from '../mocks/predios.js'

// TEMPORAL: desactiva el fallback de mocks para la API de contribuyentes.
// Cambiar a true para volver a usar los datos ficticios cuando la API no esté disponible.
const USAR_MOCKS_CONTRIBUYENTES = false

// HU001/HU004/HU048: coincidencias por documento o código para el selector.
// El backend busca de forma parcial; se filtra por el criterio elegido para no
// mezclar coincidencias de códigos, documentos o nombres con el mismo texto.
export async function buscarCoincidencias(tipoDocumento, numeroDocumento) {
  const numero = String(numeroDocumento ?? '').replace(/\s/g, '').toUpperCase()
  if (!numero) return []
  const coincide = (c) => tipoDocumento === 'CODIGO'
    ? String(c.codigo).toUpperCase().includes(numero)
    : c.tipoDoc === tipoDocumento && String(c.doc).toUpperCase().includes(numero)

  try {
    const encontrados = await pedir(`/contribuyentes/buscar?q=${encodeURIComponent(numero)}`)
    return encontrados
      .filter(coincide)
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
  } catch (err) {
    // Fallback demo: si el backend no tiene sesión o no está disponible, buscar en datos ficticios
    if (USAR_MOCKS_CONTRIBUYENTES && (MODO_DEMO || err.estado === 401 || err.codigo === 'SIN_CONEXION')) {
      return CONTRIBUYENTES
        .filter(coincide)
        .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
    }
    throw err
  }
}

// HU001: ficha oficial por tipo y número de documento.
export async function obtenerContribuyentePorDocumento(tipoDocumento, numeroDocumento) {
  const parametros = new URLSearchParams({ tipoDocumento, numeroDocumento })
  try {
    return await pedir(`/contribuyentes?${parametros.toString()}`)
  } catch (err) {
    // Fallback demo: buscar en el padrón ficticio
    if (USAR_MOCKS_CONTRIBUYENTES && (MODO_DEMO || err.estado === 401 || err.codigo === 'SIN_CONEXION')) {
      const numero = String(numeroDocumento ?? '').toUpperCase()
      const encontrado = CONTRIBUYENTES.find(
        (c) => c.tipoDoc === tipoDocumento && String(c.doc).toUpperCase() === numero,
      )
      if (!encontrado) throw new Error('No se encontró el contribuyente en el padrón.', { cause: err })
      return {
        ...encontrado,
        estado: 'ACTIVO',
        direccion: 'Av. La Marina 1450, San Miguel',
        email: 'contribuyente@demo.muni',
        telefono: '(01) 555-0000',
      }
    }
    throw err
  }
}
