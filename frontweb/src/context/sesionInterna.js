import { createContext, useContext } from 'react'

export const SesionInternaContext = createContext(null)

export function useSesionInterna() {
  const contexto = useContext(SesionInternaContext)
  if (!contexto) throw new Error('SesionInternaProvider no está disponible.')
  return contexto
}
