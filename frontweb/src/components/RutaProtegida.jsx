import { useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { obtenerSesion } from '../services/authService.js'

export default function RutaProtegida({ children }) {
  const ubicacion = useLocation()
  const [estado, setEstado] = useState('cargando')

  useEffect(() => {
    let activa = true
    obtenerSesion()
      .then(() => { if (activa) setEstado('autenticado') })
      .catch(() => { if (activa) setEstado('sin-sesion') })
    return () => { activa = false }
  }, [])

  if (estado === 'cargando') return <main className="pmain"><p>Verificando sesión…</p></main>
  if (estado === 'sin-sesion') return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />
  return children
}
