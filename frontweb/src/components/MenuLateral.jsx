import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { MODULOS, icono } from '../utils/modulos.jsx'
import { cerrarSesion } from '../services/authService.js'
import { useSesionInterna } from '../context/sesionInterna.js'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * Panel de navegación lateral.
 *
 * @param {object} props
 * @param {'portal'|'gestion'|undefined} props.solo
 *   - 'portal'  → muestra solo los módulos de Servicios en Línea del Contribuyente.
 *   - 'gestion' → muestra solo los módulos de Gestión Municipal (funcionarios).
 *   - undefined  → muestra ambas secciones (comportamiento original).
 */
export default function MenuLateral({ solo }) {
  const navegar = useNavigate()
  const { sesion, salir: salirInterno } = useSesionInterna()
  const { limpiarSesion } = useAuth()
  const [errorSalida, setErrorSalida] = useState('')
  const rutaLogin = solo === 'gestion' ? '/gestion/login' : '/login'

  async function salir(evento) {
    evento.preventDefault()
    setErrorSalida('')
    try {
      if (solo === 'gestion') {
        await salirInterno()
        navegar('/gestion/login', { replace: true })
      } else {
        await cerrarSesion()
        limpiarSesion()
        navegar('/login', { replace: true })
      }
    } catch (err) {
      setErrorSalida(err.message)
    }
  }

  const modulosPortal  = MODULOS.filter((m) => m.seccion === 'portal')
  const modulosGestion = MODULOS.filter((m) => m.seccion !== 'portal'
    && (!m.permiso || sesion?.permisos?.includes(m.permiso)))

  const mostrarPortal  = !solo || solo === 'portal'
  const mostrarGestion = !solo || solo === 'gestion'

  return (
    <aside className="menu">
      <Link to="/" className="menu-marca" aria-label="Municipalidad de San Miguel, ir al inicio">
        <img src="/logo-san-miguel-circulo.png" alt="" width="342" height="342" />
        <div>
          <span>Municipalidad</span>
          <strong>San Miguel</strong>
        </div>
      </Link>

      <nav aria-label="Módulos del sistema">
        {mostrarPortal && (
          <>
            <p className="menu-etiqueta">Servicios en Línea</p>
            <div className="menu-nav">
              {modulosPortal.map((m) => (
                <NavLink key={m.ruta} to={m.ruta} className="menu-enlace">
                  {m.icono}
                  <span>{m.texto}</span>
                </NavLink>
              ))}
            </div>
          </>
        )}

        {mostrarGestion && (
          <>
            <p className="menu-etiqueta" style={{ marginTop: mostrarPortal ? '16px' : undefined }}>
              Gestión Municipal
            </p>
            <div className="menu-nav">
              {modulosGestion.map((m) => (
                <NavLink key={m.ruta} to={m.ruta} className="menu-enlace">
                  {m.icono}
                  <span>{m.texto}</span>
                </NavLink>
              ))}
            </div>
          </>
        )}
      </nav>

      <div className="menu-pie">
        <p className="menu-demo" role="note">Datos ficticios de demostración</p>
        {errorSalida && <p role="alert">{errorSalida}</p>}
        {solo === 'gestion' && sesion && <p className="menu-demo" role="status">{sesion.nombreCompleto}</p>}
        <Link to={rutaLogin} className="menu-salir" onClick={salir}>
          {icono(<><path d="M9 21H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h4" /><path d="M16 17l5-5-5-5M21 12H9" /></>)}
          Salir
        </Link>
      </div>
    </aside>
  )
}
