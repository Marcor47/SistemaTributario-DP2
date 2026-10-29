import { Navigate, useLocation } from 'react-router-dom'
import { useSesionInterna } from '../context/sesionInterna.js'
import { rutaInicialGestion } from '../utils/modulos.jsx'

export default function RutaInternaProtegida({ children, permiso, permitirCambioClave = false }) {
  const { sesion, cargando } = useSesionInterna()
  const ubicacion = useLocation()
  if (cargando) return <main className="pmain"><p className="mensaje-carga" role="status">Comprobando sesión…</p></main>
  if (!sesion) return <Navigate to="/gestion/login" state={{ from: ubicacion.pathname }} replace />
  if (sesion.cambioClavePendiente && !permitirCambioClave) return <Navigate to="/gestion/cambiar-clave" replace />
  if (!sesion.cambioClavePendiente && permitirCambioClave) return <Navigate to={rutaInicialGestion(sesion)} replace />
  if (permiso && !sesion.permisos.includes(permiso)) return <Navigate to="/gestion/sin-acceso" replace />
  return children
}
