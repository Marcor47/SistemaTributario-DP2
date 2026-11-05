import { useEffect, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { MODULOS } from '../utils/modulos.jsx'
import { useSesionInterna } from '../context/sesionInterna.js'
import { useAuth } from '../context/AuthContext.jsx'
import { obtenerEstadoCuentaPortal } from '../services/portalCuentaService.js'
import { cerrarSesion } from '../services/authService.js'

/**
 * Panel de navegación lateral.
 *
 * @param {object} props
 * @param {'portal'|'gestion'|undefined} props.solo
 *   - 'portal'  → muestra solo los módulos de Servicios en Línea del Contribuyente.
 *   - 'gestion' → muestra solo los módulos de Gestión Municipal (funcionarios).
 *   - undefined  → muestra ambas secciones (comportamiento original).
 * @param {boolean} [props.visible=true]  Controla si el menú está expandido.
 * @param {function} [props.onToggle]     Callback para colapsar/expandir.
 */
export default function MenuLateral({ solo, visible = true, onToggle }) {
  const navegar = useNavigate()
  const { sesion, salir: salirInterno } = useSesionInterna()
  const { sesion: sesionAuth, limpiarSesion } = useAuth()
  const [perfilPortal, setPerfilPortal] = useState(null)
  const [errorSalida, setErrorSalida] = useState('')
  const rutaInicio = solo === 'portal' ? '/portal/inicio' : (solo === 'gestion' ? '/gestion/inicio' : '/')
  const codigoPortal = sesionAuth.tipo === 'contribuyente'
    ? sesionAuth.usuario?.codigoContribuyente || sesionAuth.usuario?.codigo
    : null

  useEffect(() => {
    if (solo !== 'portal' || !codigoPortal) return undefined
    const controlador = new AbortController()
    obtenerEstadoCuentaPortal({ signal: controlador.signal })
      .then((cuenta) => {
        if (!controlador.signal.aborted && cuenta.contribuyente?.codigo === codigoPortal) {
          setPerfilPortal({ codigo: codigoPortal, nombre: cuenta.contribuyente.nombre })
        }
      })
      .catch(() => {}) // El código de la sesión sigue identificando al contribuyente.
    return () => controlador.abort()
  }, [solo, codigoPortal])

  const modulosPortal  = MODULOS.filter((m) => m.seccion === 'portal')
  const modulosGestion = MODULOS.filter((m) => m.seccion !== 'portal'
    && (!m.permiso || sesion?.permisos?.includes(m.permiso)))

  const mostrarPortal  = !solo || solo === 'portal'
  const mostrarGestion = !solo || solo === 'gestion'

  const nombreUsuario = solo === 'gestion' ? sesion?.nombreCompleto
    : solo === 'portal' && codigoPortal
      ? (perfilPortal?.codigo === codigoPortal && perfilPortal.nombre) || codigoPortal
      : null

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
      setErrorSalida(err.message || 'Error al cerrar sesión')
    }
  }

  /* ── Estado colapsado: solo muestra una tira angosta con botón ── */
  if (!visible) {
    return (
      <aside className="menu menu--colapsado" aria-label="Menú lateral (colapsado)">
        <button
          className="menu__btn-toggle"
          onClick={onToggle}
          aria-label="Mostrar menú lateral"
          title="Mostrar menú"
          type="button"
        >
          {/* Chevron derecha */}
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </aside>
    )
  }

  return (
    <aside className="menu">
      <Link to={rutaInicio} className="menu-marca" aria-label="Municipalidad de San Miguel, ir al inicio">
        <img src="/logo-san-miguel-circulo.png" alt="" width="342" height="342" />
        <div>
          <span>Municipalidad</span>
          <strong>San Miguel</strong>
        </div>
      </Link>

      {nombreUsuario && (
        <div className="menu-perfil" role="status" aria-label={`Usuario activo: ${nombreUsuario}`}>
          <div className="menu-perfil-avatar" aria-hidden="true">
            {solo === 'portal'
              ? <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg>
              : nombreUsuario.charAt(0).toUpperCase()}
          </div>
          <div className="menu-perfil-info">
            <span className="menu-perfil-etiqueta">{solo === 'portal' ? 'Contribuyente' : 'Usuario activo'}</span>
            <strong className="menu-perfil-nombre">{nombreUsuario}</strong>
            {solo === 'portal' && nombreUsuario !== codigoPortal && <span className="menu-perfil-codigo">Código {codigoPortal}</span>}
          </div>
        </div>
      )}

      <nav aria-label="Módulos del sistema" className="menu-secciones">
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

      {onToggle && (
        <button
          className="menu__btn-toggle menu__btn-toggle--expandido"
          onClick={onToggle}
          aria-label="Ocultar menú lateral"
          title="Ocultar menú"
          type="button"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
      )}
      <div className="menu-pie">
        <p className="menu-demo" role="note">Datos ficticios de demostración</p>
        {errorSalida && <p role="alert">{errorSalida}</p>}
        <button className="menu-salir" onClick={salir} type="button" title="Cerrar sesión">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M9 21H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h4" />
            <path d="M16 17l5-5-5-5M21 12H9" />
          </svg>
          Salir
        </button>
      </div>
    </aside>
  )
}
