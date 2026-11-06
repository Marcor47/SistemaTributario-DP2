import { normalizar } from './formato.js'
import { TAMANIOS_PAGINA, paginar } from './paginacion.js'

export { TAMANIOS_PAGINA }

export const TIPO_PREDIO = {
  URBANO: 'Urbano',
  RUSTICO: 'Rústico',
  OTRO: 'Otro',
}

export const CONDICION_PROPIEDAD = {
  PROPIETARIO: 'Propietario',
  COPROPIETARIO: 'Copropietario',
  POSESIONARIO: 'Posesionario',
}

export const FILTROS_PREDIOS_INICIALES = { tipo: '', uso: '', condicion: '' }

function opcionesUnicas(valores, etiquetaDe) {
  return [...new Set(valores.filter(Boolean))]
    .map((valor) => ({ valor, etiqueta: etiquetaDe ? etiquetaDe(valor) : valor }))
    .sort((a, b) => a.etiqueta.localeCompare(b.etiqueta, 'es'))
}

/* Opciones únicas de tipo, uso y condición presentes en los predios cargados. */
export function opcionesDePredios(predios = []) {
  return {
    tipos: opcionesUnicas(predios.map((predio) => predio.tipoPredio), (valor) => TIPO_PREDIO[valor] ?? valor),
    usos: opcionesUnicas(predios.map((predio) => predio.usoPredio)),
    condiciones: opcionesUnicas(
      predios.map((predio) => predio.condicionPropiedad),
      (valor) => CONDICION_PROPIEDAD[valor] ?? valor,
    ),
  }
}

/* Filtra por código/dirección y por tipo, uso y condición, todo con lógica AND. */
export function filtrarPredios(predios = [], texto = '', filtros = {}) {
  const consulta = normalizar(texto)
  return predios.filter((predio) => {
    const coincideTexto = !consulta
      || normalizar(predio?.codigoPredio).includes(consulta)
      || normalizar(predio?.ubicacion).includes(consulta)

    return coincideTexto
      && (!filtros.tipo || predio.tipoPredio === filtros.tipo)
      && (!filtros.uso || predio.usoPredio === filtros.uso)
      && (!filtros.condicion || predio.condicionPropiedad === filtros.condicion)
  })
}

export const paginarPredios = paginar
