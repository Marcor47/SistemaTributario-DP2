import { useCallback, useState } from 'react'
import { useFocusEffect } from 'expo-router'
import { obtenerCuenta } from '../services/cuentaService'

export default function useEstadoCuenta() {
  const [revision, setRevision] = useState(0)
  const [consulta, setConsulta] = useState({ cuenta: null, ocupado: true, error: null })

  useFocusEffect(useCallback(() => {
    const controlador = new AbortController()
    setConsulta((anterior) => ({ ...anterior, ocupado: true, error: null }))
    obtenerCuenta({ signal: controlador.signal })
      .then((cuenta) => {
        if (!controlador.signal.aborted) setConsulta({ cuenta, ocupado: false, error: null })
      })
      .catch((error) => {
        if (!controlador.signal.aborted) setConsulta({ cuenta: null, ocupado: false, error })
      })
    return () => controlador.abort()
  }, [revision]))

  const actualizar = useCallback(() => setRevision((actual) => actual + 1), [])
  return { ...consulta, actualizar }
}
