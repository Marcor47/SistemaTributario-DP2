import { CONTRIBUYENTES } from '../mocks/predios.js'
import { FECHA_CORTE, FECHA_CORTE_ANTERIOR, generarCuenta } from '../mocks/cuentas.js'
import { normalizar } from '../utils/formato.js'

// Hoy responde con datos ficticios. Cuando el back (#60) esté listo, solo cambian
// los cuerpos de estas funciones por fetch a `${import.meta.env.VITE_API_URL}/...`.
const RETRASO_MS = 250
const esperar = () => new Promise((r) => setTimeout(r, RETRASO_MS))

// Busca contribuyentes por código, documento (DNI / RUC) o nombre.
// Admite tanto búsqueda individualizada { codigo, documento, nombre } como búsqueda libre por texto.
export async function buscarContribuyentes(criterios) {
  await esperar()
  if (typeof criterios === 'object' && criterios !== null) {
    const { codigo = '', documento = '', nombre = '' } = criterios
    const cod = normalizar(codigo)
    const nom = normalizar(nombre)
    const docDigits = documento.replace(/\D/g, '')
    if (!cod && !nom && !docDigits) return []

    return CONTRIBUYENTES.filter((c) => {
      if (cod && !c.codigo.toLowerCase().includes(cod.toLowerCase())) return false
      if (nom && !normalizar(c.nombre).includes(nom)) return false
      if (docDigits && !c.doc.includes(docDigits)) return false
      return true
    })
  }

  const texto = normalizar(criterios)
  if (!texto) return []
  const digitos = String(criterios).replace(/\D/g, '')
  return CONTRIBUYENTES.filter(
    (c) => c.codigo.includes(texto) || normalizar(c.nombre).includes(texto) || (digitos && c.doc.includes(digitos)),
  )
}

// `servicioCaido` solo existe para la demostración del flujo alterno: cuando el
// servicio de cuenta corriente no responde se devuelve la última información
// disponible, con su fecha de corte y marcada como desactualizada.
export async function obtenerEstadoCuenta(codigo, { servicioCaido = false } = {}) {
  await esperar()
  const fechaCorte = servicioCaido ? FECHA_CORTE_ANTERIOR : FECHA_CORTE
  return {
    contribuyente: CONTRIBUYENTES.find((c) => c.codigo === codigo),
    fechaCorte,
    desactualizado: servicioCaido,
    filas: generarCuenta(codigo, fechaCorte),
  }
}
