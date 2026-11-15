import { createContext, useContext } from 'react'

export const TransicionLoginContext = createContext(null)

export function useTransicionLogin() {
  return useContext(TransicionLoginContext)
}
