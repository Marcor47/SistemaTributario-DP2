import { CONTRIBUYENTES } from '../mocks/predios.js'
import { ANIOS_REGULARIDAD, generarRegularidad } from '../mocks/regularidad.js'

// HU010 sigue con datos ficticios. La búsqueda real puede devolver contribuyentes
// que no existen en este catálogo, así que la pantalla debe mostrar "sin datos".
export function tieneDatosRegularidad(codigo) {
  return CONTRIBUYENTES.some((persona) => persona.codigo === codigo)
}

// Igual que cuentaService: al integrar la API, solo se reemplaza esta función.
export async function obtenerRegularidad(codigo, anio = ANIOS_REGULARIDAD[0]) {
  await new Promise((resolve) => setTimeout(resolve, 250))
  const contribuyente = CONTRIBUYENTES.find((persona) => persona.codigo === codigo)
  if (!contribuyente) throw new Error('No se encontró el contribuyente.')
  if (!ANIOS_REGULARIDAD.includes(anio)) throw new Error('El año solicitado no está disponible.')
  return { contribuyente, ...generarRegularidad(codigo, anio) }
}
