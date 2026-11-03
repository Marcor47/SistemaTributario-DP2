import { Link, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

function Cargando() {
  return <p role="status" style={{ padding: 24 }}>Verificando sesión…</p>
}

/** Exige sesión de personal municipal; con `roles` además exige alguno de ellos. */
export function RutaInterna({ roles }) {
  const { sesion } = useAuth()
  const ubicacion = useLocation()
  if (sesion.estado === 'cargando') return <Cargando />
  if (sesion.tipo !== 'interno') {
    return <Navigate to="/interno/login" replace state={{ from: ubicacion.pathname + ubicacion.search }} />
  }
  if (roles && !roles.some((rol) => sesion.usuario.roles?.includes(rol))) {
    return (
      <main className="pmain" role="alert">
        <h1>Acceso denegado</h1>
        <p>Tu usuario no tiene permiso para ver esta sección.</p>
        <Link to="/predios">Volver</Link>
      </main>
    )
  }
  return <Outlet />
}

/** Exige sesión de contribuyente (Servicios en Línea). */
export function RutaContribuyente() {
  const { sesion } = useAuth()
  const ubicacion = useLocation()
  if (sesion.estado === 'cargando') return <Cargando />
  if (sesion.tipo !== 'contribuyente') {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: ubicacion.pathname + ubicacion.search, motivo: 'servicios-en-linea' }}
      />
    )
  }
  return <Outlet />
}
