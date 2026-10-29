import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { registrarExpiracionSesion } from '../services/api'
import { cerrarSesion as cerrarSesionApi, iniciarSesion, obtenerSesion } from '../services/authService'

const SesionContext = createContext(null)

// La autorización real la valida siempre el backend con la cookie; esto solo decide qué pantallas mostrar.
export function SesionProvider({ children }) {
  const [sesion, setSesion] = useState({ estado: 'cargando', usuario: null, vencida: false })

  useEffect(() => {
    let activo = true
    obtenerSesion()
      .then((usuario) => activo && setSesion({ estado: 'listo', usuario, vencida: false }))
      .catch(() => activo && setSesion({ estado: 'listo', usuario: null, vencida: false }))
    registrarExpiracionSesion(() => setSesion((s) => ({ estado: 'listo', usuario: null, vencida: Boolean(s.usuario) })))
    return () => {
      activo = false
      registrarExpiracionSesion(null)
    }
  }, [])

  const ingresar = useCallback(async (codigo, clave) => {
    const usuario = await iniciarSesion(codigo, clave)
    setSesion({ estado: 'listo', usuario, vencida: false })
  }, [])

  const salir = useCallback(async () => {
    try {
      await cerrarSesionApi()
    } finally {
      setSesion({ estado: 'listo', usuario: null, vencida: false })
    }
  }, [])

  const valor = useMemo(() => ({ sesion, ingresar, salir }), [sesion, ingresar, salir])
  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>
}

export function useSesion() {
  const contexto = useContext(SesionContext)
  if (!contexto) throw new Error('useSesion debe usarse dentro de SesionProvider')
  return contexto
}
