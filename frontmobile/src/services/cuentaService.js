import { pedir } from './api'
import { normalizarCuenta } from '../utils/cuenta'

export async function obtenerCuenta({ signal } = {}) {
  return normalizarCuenta(await pedir('/portal/estado-cuenta', { signal, cache: 'no-store' }))
}
