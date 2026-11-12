import { useSesionInterna } from '../context/sesionInterna.js'
import { useAuth } from '../context/AuthContext.jsx'
import { etiquetaRol } from '../utils/roles.js'
import BuzonNotificaciones from './BuzonNotificaciones.jsx'

/**
 * Barra superior con identidad del usuario y acceso al buzón del contribuyente.
 *
 * @param {object} props
 * @param {'portal'|'gestion'} props.seccion
 */
export default function BarraSuperior({ seccion }) {
  const { sesion: sesionInterna } = useSesionInterna()
  const { sesion: sesionAuth } = useAuth()

  /* ── Datos de identidad según sección ─────────────────────────── */
  const esGestion = seccion === 'gestion'

  const nombreMostrado = esGestion
    ? (sesionInterna?.nombreCompleto || sesionInterna?.nombreUsuario || '')
    : (sesionAuth?.usuario?.nombre || sesionAuth?.usuario?.codigoContribuyente || sesionAuth?.usuario?.codigo || '')

  const rolMostrado = esGestion
    ? etiquetaRol(sesionInterna?.rol)
    : 'Contribuyente'

  const inicialAvatar = nombreMostrado ? nombreMostrado.charAt(0).toUpperCase() : '?'

  return (
    <header className="barra-superior" role="banner">
      {/* Nombre del sistema */}
      <span className="barra-superior__sistema">
        {esGestion ? 'Sistema de Gestión Tributaria' : 'Portal del Ciudadano'}
      </span>

      {/* Espacio flexible */}
      <div className="barra-superior__espaciador" aria-hidden="true" />

      {/* Identidad del usuario */}
      {nombreMostrado && (
        <div className="barra-superior__usuario" role="status" aria-label={`Usuario: ${nombreMostrado}`}>
          <div className="barra-superior__avatar" aria-hidden="true">
            {esGestion ? inicialAvatar : (
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="8" r="4" />
                <path d="M4 21a8 8 0 0 1 16 0" />
              </svg>
            )}
          </div>
          <div className="barra-superior__info">
            <span className="barra-superior__nombre">{nombreMostrado}</span>
            <span className="barra-superior__rol">{rolMostrado}</span>
          </div>
        </div>
      )}

      {!esGestion && <BuzonNotificaciones />}
    </header>
  )
}

