import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import { StyleSheet, View } from 'react-native'
import CheckoutNiubiz from '../components/pagos/CheckoutNiubiz'
import { configuracionNiubiz } from '../utils/niubiz'
import { actualizarPasarela, PASARELA_INICIAL } from '../utils/estadoPasarela'

const PagosContext = createContext(null)

// El SDK vive durante la sesión del ciudadano. Las operaciones, sesiones y confirmaciones son independientes.
export function PagosProvider({ children, habilitarPasarela = false }) {
  const [ultimoPago, setUltimoPago] = useState(null)
  const [pasarela, setPasarela] = useState(PASARELA_INICIAL)
  const receptor = useRef(null)

  const guardarPago = useCallback((pago) => {
    setUltimoPago((anterior) => ({
      ...pago,
      checkout: pago.estado === 'INICIADA' && String(anterior?.idTransaccion) === String(pago.idTransaccion)
        ? pago.checkout || anterior.checkout : pago.checkout,
      descartado: String(anterior?.idTransaccion) === String(pago.idTransaccion) && Boolean(anterior?.descartado),
    }))
    setPasarela((anterior) => actualizarPasarela(anterior, pago))
    if (pago.estado !== 'INICIADA' && receptor.current?.id === String(pago.idTransaccion)) receptor.current = null
  }, [])

  const descartarPago = useCallback((id) => {
    setUltimoPago((anterior) => String(anterior?.idTransaccion) === String(id) ? { ...anterior, descartado: true } : anterior)
    if (receptor.current?.id === String(id)) receptor.current = null
    setPasarela((anterior) => String(anterior.pago?.idTransaccion) === String(id)
      ? { ...PASARELA_INICIAL, generacion: anterior.generacion + 1 } : anterior)
  }, [])

  const cerrarPasarela = useCallback(() => {
    receptor.current = null
    setPasarela((anterior) => anterior.abierto ? { ...anterior, abierto: false } : anterior)
  }, [])

  const abrirPasarela = useCallback((pago, callbacks) => {
    configuracionNiubiz(pago) // Valida el importe y el checkout antes de mostrar el visor.
    const actual = { id: String(pago.idTransaccion), ...callbacks, enviado: false }
    receptor.current = actual
    setPasarela((anterior) => ({ ...actualizarPasarela(anterior, pago), abierto: true }))
    // El dueño de la pantalla solo puede liberar su propio receptor, nunca el de una pantalla posterior.
    return () => {
      if (receptor.current !== actual) return
      receptor.current = null
      setPasarela((anterior) => anterior.abierto ? { ...anterior, abierto: false } : anterior)
    }
  }, [])

  const recibirToken = useCallback(({ token, operacion }) => {
    const actual = receptor.current
    if (!actual || actual.enviado || actual.id !== operacion) return
    actual.enviado = true
    receptor.current = null
    setPasarela((anterior) => String(anterior.pago?.idTransaccion) === operacion
      ? { ...anterior, abierto: false, tokenEntregado: true } : anterior)
    actual.onToken(token)
  }, [])

  const recibirError = useCallback((operacion) => {
    const actual = receptor.current
    if (!actual || actual.id !== operacion || actual.enviado) return
    receptor.current = null
    setPasarela((anterior) => ({ ...anterior, abierto: false }))
    actual.onError()
  }, [])

  const valor = useMemo(() => ({ ultimoPago, guardarPago, descartarPago, abrirPasarela }),
    [ultimoPago, guardarPago, descartarPago, abrirPasarela])
  return <PagosContext.Provider value={valor}>
    <View style={estilos.raiz}>
      {children}
      {habilitarPasarela && <CheckoutNiubiz key={pasarela.generacion} pago={pasarela.pago} abierto={pasarela.abierto}
        onToken={recibirToken} onCerrar={cerrarPasarela} onError={recibirError} />}
    </View>
  </PagosContext.Provider>
}

export function usePagos() {
  const contexto = useContext(PagosContext)
  if (!contexto) throw new Error('PagosProvider no está disponible.')
  return contexto
}

const estilos = StyleSheet.create({ raiz: { flex: 1 } })
