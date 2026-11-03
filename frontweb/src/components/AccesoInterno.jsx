import { Link } from 'react-router-dom'
import { etiquetaRol } from '../utils/roles.js'

/*
 * Pantallas internas de predios (HU003 y HU005) comparten el control de acceso:
 * sin sesión interna se pide iniciar sesión; con sesión sin rol se informa el bloqueo.
 */
export function VerificandoAcceso({ mensaje }) {
  return <p className="hint mensaje-carga" role="status">{mensaje || 'Verificando acceso…'}</p>
}

export function AccesoRestringido({ titulo, descripcion, mensajeExpirada, expirada }) {
  return (
    <section className="pcard acceso-interno" aria-label="Acceso restringido">
      <h2>{titulo}</h2>
      <p className="hint">{descripcion}</p>
      {expirada && (
        <div className="alert acceso-interno__aviso" role="alert">
          {mensajeExpirada || 'Tu sesión interna venció. Vuelve a ingresar para continuar.'}
        </div>
      )}
      <Link className="btn primary acceso-interno__btn" to="/interno/login" state={{ from: '/predios' }}>
        Iniciar sesión interna
      </Link>
    </section>
  )
}

export function AccesoDenegado({ titulo, usuario, descripcion, rolesPermitidos }) {
  return (
    <section className="pcard acceso-interno" role="alert" aria-label="Acceso denegado">
      <h2>{titulo}</h2>
      <p>
        El usuario <strong>{usuario?.nombreUsuario || 'interno'}</strong>{' '}
        {usuario?.nombreCompleto ? <span className="hint">({usuario.nombreCompleto}) </span> : null}
        no tiene permiso para esta consulta.
      </p>
      {descripcion && <p className="hint">{descripcion}</p>}
      <p className="hint">
        Se requiere el rol {rolesPermitidos.map((rol, indice) => (indice === 0 ? '' : ' o ') + etiquetaRol(rol)).join('')}.
      </p>
    </section>
  )
}
