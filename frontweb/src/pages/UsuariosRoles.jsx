import { useEffect, useRef, useState } from 'react'
import BarraBusqueda from '../components/BarraBusqueda.jsx'
import { cambiarRol, cargarUsuariosYRoles } from '../services/usuarioAdminService.js'
import { etiquetaRol } from '../utils/roles.js'
import { normalizar } from '../utils/formato.js'
import './UsuariosRoles.css'

const MAXIMO_MOTIVO = 200

const formatoFechaHora = (iso) =>
  new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })

export default function UsuariosRoles() {
  const [usuarios, setUsuarios] = useState([])
  const [roles, setRoles] = useState([])
  const [demo, setDemo] = useState(false)
  const [consulta, setConsulta] = useState('')
  const [ocupado, setOcupado] = useState(true)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [seleccionado, setSeleccionado] = useState(null)
  const [nuevoRol, setNuevoRol] = useState('')
  const [motivo, setMotivo] = useState('')
  const [errorRol, setErrorRol] = useState('')
  const [confirmando, setConfirmando] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [errorGuardado, setErrorGuardado] = useState('')
  const [historial, setHistorial] = useState([])
  const selectRol = useRef(null)
  const botonConfirmar = useRef(null)
  const botonOrigen = useRef(null)
  const bloqueado = ocupado || guardando

  useEffect(() => {
    let activo = true
    cargarUsuariosYRoles()
      .then((datos) => {
        if (!activo) return
        setUsuarios(datos.usuarios)
        setRoles(datos.roles)
        setDemo(datos.demo)
      })
      .catch((err) => {
        if (activo) setError(err.message || 'No se pudo cargar la lista de usuarios. Recarga la página para intentarlo nuevamente.')
      })
      .finally(() => { if (activo) setOcupado(false) })
    return () => { activo = false }
  }, [])

  useEffect(() => {
    if (seleccionado) selectRol.current?.focus()
  }, [seleccionado])

  useEffect(() => {
    if (confirmando) botonConfirmar.current?.focus()
  }, [confirmando])

  const texto = normalizar(consulta)
  const visibles = usuarios.filter((u) =>
    normalizar(`${u.nombreUsuario} ${u.nombreCompleto} ${u.correo} ${etiquetaRol(u.rol)}`).includes(texto))
  const rolesDisponibles = roles.filter((r) => r.nombre !== seleccionado?.rol)

  function abrirCambio(usuario, evento) {
    botonOrigen.current = evento.currentTarget
    setSeleccionado(usuario)
    setNuevoRol('')
    setMotivo('')
    setErrorRol('')
    setErrorGuardado('')
    setConfirmando(false)
    setAviso('')
  }

  function cerrarCambio() {
    setSeleccionado(null)
    setConfirmando(false)
    setErrorRol('')
    setErrorGuardado('')
    requestAnimationFrame(() => botonOrigen.current?.focus())
  }

  function revisar(evento) {
    evento.preventDefault()
    if (!nuevoRol) {
      setErrorRol('Selecciona el nuevo rol del usuario.')
      selectRol.current?.focus()
      return
    }
    setErrorRol('')
    setConfirmando(true)
  }

  async function aplicar() {
    setGuardando(true)
    setErrorGuardado('')
    try {
      const { usuario, registro } = await cambiarRol(seleccionado, nuevoRol, motivo, demo)
      setUsuarios((actuales) => actuales.map((u) => (u.idUsuario === usuario.idUsuario ? { ...u, rol: usuario.rol } : u)))
      setHistorial((actual) => [registro, ...actual])
      setAviso(`Se cambió el rol de ${registro.nombreCompleto} de ${etiquetaRol(registro.rolAnterior)} a ${etiquetaRol(registro.rolNuevo)}. El cambio quedó registrado.`)
      cerrarCambio()
    } catch (err) {
      setConfirmando(false)
      setErrorGuardado(err.message || 'No se pudo cambiar el rol. Intenta nuevamente.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div aria-busy={ocupado || guardando}>
      <section className="pcard">
        <h2>Usuarios internos y roles</h2>
        <p className="hint">
          Cambia el rol de un usuario interno. Cada cambio se registra con el usuario, la fecha y el rol anterior.
        </p>
      </section>

      <BarraBusqueda
        etiqueta="Buscar usuario"
        placeholder="Nombre, usuario, correo o rol"
        onBuscar={(valor) => setConsulta(valor.trim())}
        onLimpiar={() => setConsulta('')}
        ocupado={bloqueado}
      />

      {demo && (
        <div className="alert" role="note">
          <strong>Datos ficticios de demostración.</strong> No hay sesión de Administrador con el servidor: los cambios de rol
          se aplican solo en esta pantalla y no se guardan.
        </div>
      )}
      {error && <div className="alert" role="alert">{error}</div>}
      {errorGuardado && <div className="alert" role="alert">{errorGuardado}</div>}
      {aviso && <div className="info" role="status">{aviso}</div>}
      {ocupado && <p className="hint mensaje-carga" role="status">Cargando usuarios…</p>}

      {seleccionado && (
        <section className="pcard" id="cambio-rol" aria-labelledby="titulo-cambio-rol">
          <h2 id="titulo-cambio-rol">Cambiar rol de {seleccionado.nombreCompleto}</h2>
          <p className="hint">
            Usuario <strong>{seleccionado.nombreUsuario}</strong> · Rol actual: <strong>{etiquetaRol(seleccionado.rol)}</strong>
          </p>
          <form className="formulario-rol" onSubmit={revisar} noValidate>
            <fieldset className="pfields" disabled={bloqueado || confirmando}>
              <legend className="sr-only">Datos del cambio de rol</legend>
              <div className={`fld${errorRol ? ' fld-error' : ''}`}>
                <label htmlFor="rol-nuevo">Nuevo rol</label>
                <select
                  id="rol-nuevo"
                  ref={selectRol}
                  value={nuevoRol}
                  onChange={(e) => { setNuevoRol(e.target.value); setErrorRol('') }}
                  aria-invalid={errorRol ? 'true' : undefined}
                  aria-describedby={errorRol ? 'rol-nuevo-error' : 'rol-nuevo-ayuda'}
                >
                  <option value="">Selecciona un rol</option>
                  {rolesDisponibles.map((r) => (
                    <option key={r.nombre} value={r.nombre}>{etiquetaRol(r.nombre)}</option>
                  ))}
                </select>
                {errorRol
                  ? <span id="rol-nuevo-error" className="error-campo" role="alert">{errorRol}</span>
                  : <span id="rol-nuevo-ayuda" className="hint">El usuario solo accederá a los módulos permitidos por el nuevo rol.</span>}
              </div>
              <div className="fld campo-completo">
                <label htmlFor="rol-motivo">Motivo (opcional)</label>
                <textarea
                  id="rol-motivo"
                  rows={2}
                  maxLength={MAXIMO_MOTIVO}
                  value={motivo}
                  onChange={(e) => setMotivo(e.target.value)}
                  aria-describedby="rol-motivo-ayuda"
                />
                <span id="rol-motivo-ayuda" className="hint">{motivo.length}/{MAXIMO_MOTIVO} caracteres</span>
              </div>
            </fieldset>
            {!confirmando && (
              <div className="acciones-documento">
                <button className="btn primary" type="submit" disabled={bloqueado}>Revisar cambio</button>
                <button className="btn" type="button" onClick={cerrarCambio} disabled={bloqueado}>Cancelar</button>
              </div>
            )}
          </form>

          {confirmando && (
            <div className="alert" role="group" aria-labelledby="confirmar-cambio-rol">
              <p id="confirmar-cambio-rol">
                ¿Cambiar el rol de <strong>{seleccionado.nombreCompleto}</strong> de <strong>{etiquetaRol(seleccionado.rol)}</strong> a{' '}
                <strong>{etiquetaRol(nuevoRol)}</strong>? Perderá el acceso a los módulos del rol anterior y el cambio quedará en la auditoría.
              </p>
              <div className="acciones-documento">
                <button className="btn primary" type="button" ref={botonConfirmar} onClick={aplicar} disabled={guardando}>
                  {guardando ? 'Guardando…' : 'Sí, cambiar rol'}
                </button>
                <button className="btn" type="button" onClick={() => setConfirmando(false)} disabled={guardando}>Volver</button>
              </div>
            </div>
          )}
        </section>
      )}

      <section className="pcard" aria-label="Usuarios internos">
        <h2>Usuarios</h2>
        <p className="hint" role="status">
          {ocupado
            ? 'Consultando usuarios…'
            : `${visibles.length} ${visibles.length === 1 ? 'usuario' : 'usuarios'}${consulta ? ` para «${consulta}»` : ''}.`}
        </p>
        {!ocupado && !error && visibles.length === 0 && (
          <div className="info">
            {consulta ? `No se encontraron usuarios para «${consulta}». Usa Limpiar para ver todos.` : 'No hay usuarios internos registrados.'}
          </div>
        )}
        {!ocupado && visibles.length > 0 && (
          <div className="tabla-scroll" role="region" aria-label="Lista de usuarios internos" tabIndex={0}>
            <table className="tabla tabla-usuarios">
              <caption className="sr-only">Usuarios internos con su rol actual y estado</caption>
              <thead>
                <tr><th scope="col">Usuario</th><th scope="col">Rol actual</th><th scope="col">Estado</th><th scope="col">Acciones</th></tr>
              </thead>
              <tbody>
                {visibles.map((u) => (
                  <tr key={u.idUsuario}>
                    <td>
                      <strong>{u.nombreCompleto}</strong>
                      <span className="usuario-detalle">{u.nombreUsuario} · {u.correo}</span>
                    </td>
                    <td><span className="estado neutro">{etiquetaRol(u.rol)}</span></td>
                    <td>{u.activo ? <span className="estado ok">Activo</span> : <span className="estado critico">Inactivo</span>}</td>
                    <td>
                      <button
                        className="btn btn-compacto"
                        type="button"
                        aria-label={`Cambiar rol de ${u.nombreCompleto}`}
                        aria-expanded={seleccionado?.idUsuario === u.idUsuario}
                        aria-controls="cambio-rol"
                        disabled={bloqueado}
                        onClick={(e) => abrirCambio(u, e)}
                      >
                        Cambiar rol
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="pcard" aria-labelledby="titulo-historial-roles">
        <h2 id="titulo-historial-roles">Cambios registrados en esta sesión</h2>
        {historial.length === 0 ? (
          <p className="hint">Los cambios de rol que realices aparecerán aquí con la fecha, el usuario y el rol anterior.</p>
        ) : (
          <div className="tabla-scroll" role="region" aria-label="Cambios de rol registrados" tabIndex={0}>
            <table className="tabla tabla-historial-roles">
              <caption className="sr-only">Cambios de rol con fecha, usuario, rol anterior y rol nuevo</caption>
              <thead>
                <tr><th scope="col">Fecha</th><th scope="col">Usuario</th><th scope="col">Rol anterior</th><th scope="col">Rol nuevo</th><th scope="col">Motivo</th></tr>
              </thead>
              <tbody>
                {historial.map((r, i) => (
                  <tr key={`${r.idUsuario}-${r.fecha}-${i}`}>
                    <td className="fecha-documento">{formatoFechaHora(r.fecha)}</td>
                    <td><strong>{r.nombreCompleto}</strong><span className="usuario-detalle">{r.nombreUsuario}</span></td>
                    <td>{etiquetaRol(r.rolAnterior)}</td>
                    <td><span className="estado ok">{etiquetaRol(r.rolNuevo)}</span></td>
                    <td>{r.motivo || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
