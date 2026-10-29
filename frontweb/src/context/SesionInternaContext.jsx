import { useCallback, useEffect, useMemo, useState } from 'react'
import { cerrarSesionInterna, obtenerSesionInterna } from '../services/authInternaService.js'
import { SesionInternaContext } from './sesionInterna.js'

export function SesionInternaProvider({ children }) {
  const [sesion, setSesion] = useState(null)
  const [cargando, setCargando] = useState(true)

  const recargar = useCallback(async () => {
    setCargando(true)
    try { setSesion(await obtenerSesionInterna()) } catch { setSesion(null) } finally { setCargando(false) }
  }, [])

  useEffect(() => {
    let activo = true
    obtenerSesionInterna().then((datos) => { if (activo) setSesion(datos) })
      .catch(() => { if (activo) setSesion(null) })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
  }, [])

  const salir = useCallback(async () => {
    await cerrarSesionInterna()
    setSesion(null)
  }, [])

  const valor = useMemo(() => ({ sesion, cargando, setSesion, recargar, salir }), [sesion, cargando, recargar, salir])
  return <SesionInternaContext.Provider value={valor}>{children}</SesionInternaContext.Provider>
}
