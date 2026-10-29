import { useEffect, useId, useRef, useState } from 'react'
import { crearUsuarioInterno, listarRolesInternos, listarUsuariosInternos } from '../services/authInternaService.js'
import './GestionInterna.css'

const ETIQUETAS_ROL = {
  ADMINISTRADOR: 'Administrador del Sistema', GERENCIA: 'Gerencia de Rentas',
  ANALISTA_RENTAS: 'Analista de Rentas', RESPONSABLE_DATOS: 'Responsable de Datos',
  VENTANILLA: 'Ventanilla', TERMINALISTA: 'Terminalista',
}

const VACIO = { nombreUsuario: '', nombreCompleto: '', correo: '', rol: '' }

export default function AdministracionUsuarios() {
  const usuarioId = useId()
  const nombreId = useId()
  const correoId = useId()
  const rolId = useId()
  const resumenRef = useRef(null)
  const [usuarios, setUsuarios] = useState([])
  const [roles, setRoles] = useState([])
  const [formulario, setFormulario] = useState(VACIO)
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')
  const [errores, setErrores] = useState({})
  const [creado, setCreado] = useState(null)
  const [copiado, setCopiado] = useState(false)

  useEffect(() => {
    Promise.all([listarUsuariosInternos(), listarRolesInternos()])
      .then(([listaUsuarios, listaRoles]) => { setUsuarios(listaUsuarios); setRoles(listaRoles) })
      .catch((err) => setError(err.message)).finally(() => setCargando(false))
  }, [])

  function validar() {
    const nuevos = {}
    if (!/^[A-Za-z0-9._-]{3,40}$/.test(formulario.nombreUsuario.trim())) nuevos.nombreUsuario = 'Usa entre 3 y 40 letras, números, puntos, guiones o guion bajo.'
    if (!formulario.nombreCompleto.trim() || formulario.nombreCompleto.trim().length > 200) nuevos.nombreCompleto = 'Ingresa el nombre completo, con máximo 200 caracteres.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formulario.correo.trim())) nuevos.correo = 'Ingresa un correo válido.'
    if (!formulario.rol) nuevos.rol = 'Selecciona un rol.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length) setTimeout(() => resumenRef.current?.focus(), 0)
    return Object.keys(nuevos).length === 0
  }

  async function enviar(evento) {
    evento.preventDefault()
    if (!validar()) return
    setError('')
    setCreado(null)
    setGuardando(true)
    try {
      const respuesta = await crearUsuarioInterno({
        nombreUsuario: formulario.nombreUsuario.trim(), nombreCompleto: formulario.nombreCompleto.trim(),
        correo: formulario.correo.trim(), rol: formulario.rol,
      })
      setUsuarios((actuales) => [...actuales, respuesta.usuario].sort((a, b) => a.nombreCompleto.localeCompare(b.nombreCompleto)))
      setCreado(respuesta)
      setFormulario(VACIO)
      setErrores({})
    } catch (err) { setError(err.message) } finally { setGuardando(false) }
  }

  async function copiarClave() {
    await navigator.clipboard.writeText(creado.passwordTemporal)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2500)
  }

  return (
    <>
      <section className="pcard" aria-labelledby="titulo-crear-usuario">
        <h2 id="titulo-crear-usuario">Crear usuario interno</h2>
        <p className="hint">La contraseña temporal se mostrará una sola vez y deberá cambiarse en el primer ingreso.</p>
        {Object.keys(errores).length > 0 && <div className="gi-mensaje gi-mensaje--error" role="alert" tabIndex="-1" ref={resumenRef}><strong>Revisa los campos señalados.</strong></div>}
        {error && <div className="gi-mensaje gi-mensaje--error" role="alert">{error}</div>}
        {creado && (
          <div className="gi-temporal" role="status">
            <div><strong>Usuario creado: {creado.usuario.nombreUsuario}</strong><p>Entrega esta contraseña temporal de forma segura. No volverá a mostrarse.</p></div>
            <code>{creado.passwordTemporal}</code>
            <button type="button" className="btn" onClick={copiarClave}>{copiado ? 'Copiada' : 'Copiar contraseña'}</button>
            <button type="button" className="gi-cerrar" aria-label="Cerrar confirmación" onClick={() => setCreado(null)}>×</button>
          </div>
        )}
        <form className="gi-form-grid" onSubmit={enviar} noValidate>
          <Campo id={usuarioId} label="Usuario institucional" error={errores.nombreUsuario}>
            <input id={usuarioId} value={formulario.nombreUsuario} onChange={(e) => setFormulario({ ...formulario, nombreUsuario: e.target.value })} maxLength="40" autoComplete="off" aria-invalid={Boolean(errores.nombreUsuario)} aria-describedby={errores.nombreUsuario ? `${usuarioId}-error` : undefined} />
          </Campo>
          <Campo id={nombreId} label="Nombre completo" error={errores.nombreCompleto}>
            <input id={nombreId} value={formulario.nombreCompleto} onChange={(e) => setFormulario({ ...formulario, nombreCompleto: e.target.value })} maxLength="200" autoComplete="name" aria-invalid={Boolean(errores.nombreCompleto)} aria-describedby={errores.nombreCompleto ? `${nombreId}-error` : undefined} />
          </Campo>
          <Campo id={correoId} label="Correo institucional" error={errores.correo}>
            <input id={correoId} type="email" value={formulario.correo} onChange={(e) => setFormulario({ ...formulario, correo: e.target.value })} maxLength="120" autoComplete="email" aria-invalid={Boolean(errores.correo)} aria-describedby={errores.correo ? `${correoId}-error` : undefined} />
          </Campo>
          <Campo id={rolId} label="Rol" error={errores.rol}>
            <select id={rolId} value={formulario.rol} onChange={(e) => setFormulario({ ...formulario, rol: e.target.value })} aria-invalid={Boolean(errores.rol)} aria-describedby={errores.rol ? `${rolId}-error` : undefined}>
              <option value="">Selecciona un rol</option>
              {roles.map((rol) => <option key={rol.nombre} value={rol.nombre}>{ETIQUETAS_ROL[rol.nombre] || rol.nombre}</option>)}
            </select>
          </Campo>
          <div className="gi-form-accion"><button className="btn primary" disabled={guardando}>{guardando ? 'Creando…' : 'Crear usuario'}</button></div>
        </form>
      </section>

      <section className="pcard" aria-labelledby="titulo-usuarios">
        <h2 id="titulo-usuarios">Usuarios internos</h2>
        <p className="hint">Consulta las cuentas registradas y el rol que tienen asignado.</p>
        {cargando ? <p role="status">Cargando usuarios…</p> : usuarios.length === 0 ? <p className="info">Todavía no existen usuarios internos.</p> : (
          <div className="tabla-scroll" tabIndex="0" aria-label="Listado de usuarios internos">
            <table className="tabla">
              <thead><tr><th>Usuario</th><th>Nombre completo</th><th>Correo</th><th>Rol</th><th>Estado</th></tr></thead>
              <tbody>{usuarios.map((usuario) => (
                <tr key={usuario.idUsuario}><td><strong>{usuario.nombreUsuario}</strong></td><td>{usuario.nombreCompleto}</td><td>{usuario.correo}</td><td>{ETIQUETAS_ROL[usuario.rol] || usuario.rol}</td><td><span className={`estado ${usuario.activo ? 'ok' : 'critico'}`}>{usuario.activo ? usuario.cambioClavePendiente ? 'Cambio de clave pendiente' : 'Activo' : 'Inactivo'}</span></td></tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}

function Campo({ id, label, error, children }) {
  return <div className="fld"><label htmlFor={id}>{label}</label>{children}{error && <p id={`${id}-error`} className="gi-error-campo">{error}</p>}</div>
}
