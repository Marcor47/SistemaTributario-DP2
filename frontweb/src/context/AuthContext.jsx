import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { esSesionDemo, obtenerSesionDemo } from '../services/authService.js'
import { obtenerSesionInterna } from '../services/authInternoService.js'
import { pedir } from '../services/api.js'

const ANONIMO = { estado: 'listo', tipo: null, usuario: null }
const AuthContext = createContext(null)

// Averigua qué sesión tiene el navegador. El backend mantiene una sola sesión por navegador.
async function consultarSesion() {
  if (esSesionDemo()) return { estado: 'listo', tipo: 'contribuyente', usuario: obtenerSesionDemo() }
  try {
    return { estado: 'listo', tipo: 'interno', usuario: await obtenerSesionInterna() }
  } catch {
    // Sin sesión interna; se prueba con la del contribuyente.
  }
  try {
    return { estado: 'listo', tipo: 'contribuyente', usuario: await pedir('/auth/me') }
  } catch {
    return ANONIMO
  }
}

export function AuthProvider({ children }) {
  const [sesion, setSesion] = useState({ estado: 'cargando', tipo: null, usuario: null })

  useEffect(() => {
    let activo = true
    consultarSesion().then((s) => {
      if (activo) setSesion(s)
    })
    return () => {
      activo = false
    }
  }, [])

  const establecerSesion = useCallback(({ tipo, usuario }) => setSesion({ estado: 'listo', tipo, usuario }), [])
  const limpiarSesion = useCallback(() => setSesion(ANONIMO), [])

  const valor = useMemo(() => ({ sesion, establecerSesion, limpiarSesion }), [sesion, establecerSesion, limpiarSesion])
  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const contexto = useContext(AuthContext)
  if (!contexto) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return contexto
}
