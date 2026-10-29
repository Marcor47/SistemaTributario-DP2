import { createContext, useCallback, useContext, useMemo, useState } from 'react'

const PagosContext = createContext(null)

// Solo conserva la última operación durante la sesión. La cuenta y las confirmaciones vienen del backend.
export function PagosProvider({ children }) {
  const [ultimoPago, setUltimoPago] = useState(null)
  const guardarPago = useCallback((pago) => {
    setUltimoPago((anterior) => ({
      ...pago,
      descartado: String(anterior?.idTransaccion) === String(pago.idTransaccion) && Boolean(anterior?.descartado),
    }))
  }, [])
  const descartarPago = useCallback((id) => {
    setUltimoPago((anterior) => String(anterior?.idTransaccion) === String(id) ? { ...anterior, descartado: true } : anterior)
  }, [])
  const valor = useMemo(() => ({ ultimoPago, guardarPago, descartarPago }), [ultimoPago, guardarPago, descartarPago])
  return <PagosContext.Provider value={valor}>{children}</PagosContext.Provider>
}

export function usePagos() {
  const contexto = useContext(PagosContext)
  if (!contexto) throw new Error('PagosProvider no está disponible.')
  return contexto
}
