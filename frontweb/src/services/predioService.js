import { CONTRIBUYENTES, PREDIOS } from '../mocks/predios.js'
import { normalizar } from '../utils/formato.js'

// Hoy responde con datos ficticios. Cuando el back (#58) esté listo, solo cambia
// el cuerpo de esta función por un fetch a `${import.meta.env.VITE_API_URL}/...`;
// las pantallas no se tocan.
const RETRASO_MS = 250

function conTitulares(predio) {
  return {
    ...predio,
    contribuyentes: predio.titulares.map((t) => ({
      ...CONTRIBUYENTES.find((c) => c.codigo === t.codigo),
      pct: t.pct,
    })),
  }
}

// Busca por código predial o por contribuyente asociado (código, nombre o documento).
export async function buscarPredios(consulta) {
  await new Promise((r) => setTimeout(r, RETRASO_MS))

  const texto = normalizar(consulta)
  if (!texto) return []
  const digitos = consulta.replace(/\D/g, '')

  return PREDIOS.map(conTitulares).filter(
    (p) =>
      normalizar(p.codPredio).includes(texto) ||
      p.contribuyentes.some(
        (c) =>
          c.codigo.includes(texto) ||
          normalizar(c.nombre).includes(texto) ||
          (digitos && c.doc.includes(digitos)),
      ),
  )
}
