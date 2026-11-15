import { pedir } from './api.js'

export const PREDIOS_POR_PAGINA = 10

// HU002: búsqueda en SAMNET por código predial, catastro, dirección o titular (código, nombre o documento). La
// respuesta viene paginada: { contenido, pagina, tamano, totalElementos, totalPaginas }.
export async function buscarPredios(consulta, { pagina = 0, tamano = PREDIOS_POR_PAGINA } = {}) {
  const texto = String(consulta ?? '').trim()
  if (!texto) return { contenido: [], pagina: 0, tamano, totalElementos: 0, totalPaginas: 0 }
  const parametros = new URLSearchParams({ consulta: texto, pagina: String(pagina), tamano: String(tamano) })
  return pedir(`/predios?${parametros}`)
}

// HU036: zonas y usos registrados para el formulario de registro.
export const obtenerCatalogosPredio = (opciones = {}) => pedir('/predios/catalogos', opciones)

// HU036: registra un predio nuevo con su titular. Los campos opcionales vacíos se envían como nulos.
export function registrarPredio(datos) {
  const texto = (valor) => (String(valor ?? '').trim() || null)
  return pedir('/predios', {
    method: 'POST',
    body: JSON.stringify({
      codigoContribuyente: datos.codigoContribuyente.trim(),
      codigoPredio: datos.codigoPredio.trim().toUpperCase(),
      numeroCatastral: texto(datos.numeroCatastral),
      ubicacion: datos.ubicacion.trim(),
      usoPredio: texto(datos.usoPredio),
      tipoPredio: datos.tipoPredio,
      areaTerreno: Number(datos.areaTerreno),
      baseImponible: Number(datos.baseImponible),
      fechaAdquisicion: texto(datos.fechaAdquisicion),
      zona: datos.zona,
      condicionPropiedad: texto(datos.condicionPropiedad),
      porcentajePropiedad: Number(datos.porcentajePropiedad),
    }),
  })
}
