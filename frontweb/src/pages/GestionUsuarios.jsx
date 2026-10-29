import { useEffect, useId, useRef, useState } from 'react'
import BarraBusqueda from '../components/BarraBusqueda.jsx'
import { useSesionInterna } from '../context/sesionInterna.js'
import { cambiarRol, cargarUsuariosYRoles, crearUsuario } from '../services/usuarioAdminService.js'
import { normalizar } from '../utils/formato.js'
import { etiquetaRol } from '../utils/roles.js'
import './GestionInterna.css'

const FORMULARIO_VACIO = { nombreUsuario: '', nombreCompleto: '', correo: '', rol: '' }
const MAXIMO_MOTIVO = 200
const formatoFechaHora = (iso) => new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })

export default function GestionUsuarios() {
  const { sesion } = useSesionInterna()
  const [usuarios, setUsuarios] = useState([])
  const [roles, setRoles] = useState([])
  const [demo, setDemo] = useState(false)
  const [consulta, setConsulta] = useState('')
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState('')
  const [aviso, setAviso] = useState('')
  const [dialogo, setDialogo] = useState(null)
  const [historial, setHistorial] = useState([])
  const botonOrigen = useRef(null)
  const puedeListarRoles = sesion?.permisos?.includes('ROLES:LISTAR')
  const puedeCrear = puedeListarRoles && sesion?.permisos?.includes('USUARIOS:CREAR')
  const puedeCambiarRol = puedeListarRoles && sesion?.permisos?.includes('ROLES:CAMBIAR')

  useEffect(() => {
    let activo = true
    cargarUsuariosYRoles(puedeCrear || puedeCambiarRol)
      .then((datos) => {
        if (!activo) return
        setUsuarios(datos.usuarios)
        setRoles(datos.roles)
        setDemo(datos.demo)
      })
      .catch((err) => { if (activo) setErrorCarga(err.message || 'No se pudo cargar la lista de usuarios.') })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
  }, [puedeCambiarRol, puedeCrear])

  const texto = normalizar(consulta)
  const visibles = usuarios.filter((usuario) => normalizar(`${usuario.nombreUsuario} ${usuario.nombreCompleto} ${usuario.correo} ${etiquetaRol(usuario.rol)}`).includes(texto))

  function abrir(tipo, usuario, evento) {
    botonOrigen.current = evento.currentTarget
    setAviso('')
    setDialogo({ tipo, usuario })
  }

  function cerrar() {
    setDialogo(null)
    requestAnimationFrame(() => botonOrigen.current?.focus())
  }

  function usuarioCreado(respuesta) {
    setUsuarios((actuales) => [...actuales, respuesta.usuario].sort((a, b) => a.nombreCompleto.localeCompare(b.nombreCompleto)))
  }

  function rolCambiado(usuario, registro) {
    setUsuarios((actuales) => actuales.map((actual) => actual.idUsuario === usuario.idUsuario ? usuario : actual))
    setHistorial((actual) => [registro, ...actual])
    setAviso(`Se cambió el rol de ${registro.nombreCompleto} de ${etiquetaRol(registro.rolAnterior)} a ${etiquetaRol(registro.rolNuevo)}. El cambio quedó registrado.`)
    cerrar()
  }

  return (
    <div aria-busy={cargando}>
      <section className="pcard usuarios-cabecera">
        <div><h2>Usuarios internos</h2><p className="hint">Crea cuentas institucionales, consulta su estado y administra el rol asignado.</p></div>
        {puedeCrear && <button className="btn primary" type="button" onClick={(evento) => abrir('crear', null, evento)}>Crear usuario</button>}
      </section>

      <BarraBusqueda etiqueta="Buscar usuario" placeholder="Nombre, usuario, correo o rol" onBuscar={(valor) => setConsulta(valor.trim())} onLimpiar={() => setConsulta('')} ocupado={cargando} />

      {demo && <div className="alert" role="note"><strong>Datos ficticios de demostración.</strong> Los cambios de rol se aplican solo en esta pantalla y se pierden al recargar.</div>}
      {errorCarga && <div className="alert" role="alert">{errorCarga}</div>}
      {aviso && <div className="info" role="status" aria-live="polite">{aviso}</div>}

      <section className="pcard" aria-labelledby="titulo-lista-usuarios">
        <h2 id="titulo-lista-usuarios">Usuarios</h2>
        <p className="hint" role="status">{cargando ? 'Consultando usuarios…' : `${visibles.length} ${visibles.length === 1 ? 'usuario' : 'usuarios'}${consulta ? ` para «${consulta}»` : ''}.`}</p>
        {!cargando && !errorCarga && visibles.length === 0 && <div className="info">{consulta ? `No se encontraron usuarios para «${consulta}». Usa Limpiar para ver todos.` : 'No hay usuarios internos registrados.'}</div>}
        {!cargando && visibles.length > 0 && (
          <div className="tabla-scroll" role="region" aria-label="Lista de usuarios internos" tabIndex={0}>
            <table className="tabla tabla-usuarios">
              <caption className="sr-only">Usuarios internos con correo, rol, estado y acciones disponibles</caption>
              <thead><tr><th scope="col">Usuario</th><th scope="col">Correo</th><th scope="col">Rol</th><th scope="col">Estado</th>{puedeCambiarRol && <th scope="col">Acciones</th>}</tr></thead>
              <tbody>{visibles.map((usuario) => (
                <tr key={usuario.idUsuario}>
                  <td><strong>{usuario.nombreCompleto}</strong><span className="usuario-detalle">{usuario.nombreUsuario}</span></td>
                  <td>{usuario.correo}</td>
                  <td><span className="estado neutro">{etiquetaRol(usuario.rol)}</span></td>
                  <td><EstadoUsuario usuario={usuario} /></td>
                  {puedeCambiarRol && <td><button className="btn btn-compacto" type="button" onClick={(evento) => abrir('rol', usuario, evento)}>Cambiar rol</button></td>}
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </section>

      {puedeCambiarRol && (
        <section className="pcard" aria-labelledby="titulo-historial-roles">
          <h2 id="titulo-historial-roles">Cambios registrados en esta sesión</h2>
          {historial.length === 0 ? <p className="hint">Los cambios de rol que realices aparecerán aquí con la fecha, el usuario y el rol anterior.</p> : (
            <div className="tabla-scroll" role="region" aria-label="Cambios de rol registrados" tabIndex={0}>
              <table className="tabla tabla-historial-roles">
                <caption className="sr-only">Cambios de rol realizados durante esta sesión</caption>
                <thead><tr><th scope="col">Fecha</th><th scope="col">Usuario</th><th scope="col">Rol anterior</th><th scope="col">Rol nuevo</th><th scope="col">Motivo</th></tr></thead>
                <tbody>{historial.map((registro, indice) => (
                  <tr key={`${registro.idUsuario}-${registro.fecha}-${indice}`}><td>{formatoFechaHora(registro.fecha)}</td><td><strong>{registro.nombreCompleto}</strong><span className="usuario-detalle">{registro.nombreUsuario}</span></td><td>{etiquetaRol(registro.rolAnterior)}</td><td><span className="estado ok">{etiquetaRol(registro.rolNuevo)}</span></td><td>{registro.motivo || '—'}</td></tr>
                ))}</tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {dialogo?.tipo === 'crear' && <Dialogo tituloId="titulo-crear-usuario" onCerrar={cerrar}><CrearUsuario roles={roles} onCreado={usuarioCreado} onCerrar={cerrar} /></Dialogo>}
      {dialogo?.tipo === 'rol' && <Dialogo tituloId="titulo-cambiar-rol" onCerrar={cerrar}><CambiarRol usuario={dialogo.usuario} roles={roles} demo={demo} onCambiado={rolCambiado} onCerrar={cerrar} /></Dialogo>}
    </div>
  )
}

function EstadoUsuario({ usuario }) {
  if (!usuario.activo) return <span className="estado critico">Inactivo</span>
  if (usuario.cambioClavePendiente) return <span className="estado aviso">Cambio de clave pendiente</span>
  return <span className="estado ok">Activo</span>
}

function CrearUsuario({ roles, onCreado, onCerrar }) {
  const usuarioId = useId()
  const nombreId = useId()
  const correoId = useId()
  const rolId = useId()
  const resumen = useRef(null)
  const confirmacion = useRef(null)
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [creado, setCreado] = useState(null)
  const [copiado, setCopiado] = useState(false)

  useEffect(() => { if (creado) confirmacion.current?.focus() }, [creado])

  function validar() {
    const nuevos = {}
    if (!/^[A-Za-z0-9._-]{3,40}$/.test(formulario.nombreUsuario.trim())) nuevos.nombreUsuario = 'Usa entre 3 y 40 letras, números, puntos, guiones o guion bajo.'
    if (!formulario.nombreCompleto.trim() || formulario.nombreCompleto.trim().length > 200) nuevos.nombreCompleto = 'Ingresa el nombre completo, con máximo 200 caracteres.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formulario.correo.trim())) nuevos.correo = 'Ingresa un correo válido.'
    if (!formulario.rol) nuevos.rol = 'Selecciona un rol.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length) requestAnimationFrame(() => resumen.current?.focus())
    return Object.keys(nuevos).length === 0
  }

  async function enviar(evento) {
    evento.preventDefault()
    if (!validar()) return
    setError('')
    setGuardando(true)
    try {
      const respuesta = await crearUsuario({ nombreUsuario: formulario.nombreUsuario.trim(), nombreCompleto: formulario.nombreCompleto.trim(), correo: formulario.correo.trim(), rol: formulario.rol })
      onCreado(respuesta)
      setCreado(respuesta)
    } catch (err) { setError(err.message) } finally { setGuardando(false) }
  }

  async function copiarClave() {
    try {
      await navigator.clipboard.writeText(creado.passwordTemporal)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2500)
    } catch { setError('No se pudo copiar la contraseña. Selecciónala y cópiala manualmente.') }
  }

  if (creado) return (
    <div className="gi-confirmacion" ref={confirmacion} tabIndex={-1} role="status">
      <p className="gi-etiqueta">Usuario creado correctamente</p><h2 id="titulo-crear-usuario">{creado.usuario.nombreUsuario}</h2><p>Entrega esta contraseña temporal de forma segura. No volverá a mostrarse.</p>
      {error && <div className="gi-mensaje gi-mensaje--error" role="alert">{error}</div>}
      <code className="usuarios-clave-temporal">{creado.passwordTemporal}</code>
      <div className="usuarios-dialogo__acciones"><button className="btn primary" type="button" onClick={copiarClave}>{copiado ? 'Contraseña copiada' : 'Copiar contraseña'}</button><button className="btn" type="button" onClick={onCerrar}>Cerrar</button></div>
    </div>
  )

  return (
    <>
      <div className="usuarios-dialogo__cabecera"><div><p className="gi-etiqueta">Nueva cuenta institucional</p><h2 id="titulo-crear-usuario">Crear usuario</h2></div><BotonCerrar onClick={onCerrar} /></div>
      <p className="hint">La contraseña temporal se mostrará una sola vez y deberá cambiarse en el primer ingreso.</p>
      {Object.keys(errores).length > 0 && <div className="gi-mensaje gi-mensaje--error" role="alert" tabIndex={-1} ref={resumen}><strong>Revisa los campos señalados.</strong></div>}
      {error && <div className="gi-mensaje gi-mensaje--error" role="alert">{error}</div>}
      <form className="gi-form-grid" onSubmit={enviar} noValidate>
        <Campo id={usuarioId} label="Usuario institucional" error={errores.nombreUsuario}><input data-autofocus id={usuarioId} value={formulario.nombreUsuario} onChange={(evento) => setFormulario({ ...formulario, nombreUsuario: evento.target.value })} maxLength="40" autoComplete="off" aria-invalid={Boolean(errores.nombreUsuario)} aria-describedby={errores.nombreUsuario ? `${usuarioId}-error` : undefined} /></Campo>
        <Campo id={nombreId} label="Nombre completo" error={errores.nombreCompleto}><input id={nombreId} value={formulario.nombreCompleto} onChange={(evento) => setFormulario({ ...formulario, nombreCompleto: evento.target.value })} maxLength="200" autoComplete="name" aria-invalid={Boolean(errores.nombreCompleto)} aria-describedby={errores.nombreCompleto ? `${nombreId}-error` : undefined} /></Campo>
        <Campo id={correoId} label="Correo institucional" error={errores.correo}><input id={correoId} type="email" value={formulario.correo} onChange={(evento) => setFormulario({ ...formulario, correo: evento.target.value })} maxLength="120" autoComplete="email" aria-invalid={Boolean(errores.correo)} aria-describedby={errores.correo ? `${correoId}-error` : undefined} /></Campo>
        <Campo id={rolId} label="Rol" error={errores.rol}><select id={rolId} value={formulario.rol} onChange={(evento) => setFormulario({ ...formulario, rol: evento.target.value })} aria-invalid={Boolean(errores.rol)} aria-describedby={errores.rol ? `${rolId}-error` : undefined}><option value="">Selecciona un rol</option>{roles.map((rol) => <option key={rol.nombre} value={rol.nombre}>{etiquetaRol(rol.nombre)}</option>)}</select></Campo>
        <div className="usuarios-dialogo__acciones campo-completo"><button className="btn primary" disabled={guardando}>{guardando ? 'Creando…' : 'Crear usuario'}</button><button className="btn" type="button" onClick={onCerrar} disabled={guardando}>Cancelar</button></div>
      </form>
    </>
  )
}

function CambiarRol({ usuario, roles, demo, onCambiado, onCerrar }) {
  const [nuevoRol, setNuevoRol] = useState('')
  const [motivo, setMotivo] = useState('')
  const [errorRol, setErrorRol] = useState('')
  const [error, setError] = useState('')
  const [confirmando, setConfirmando] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const botonConfirmar = useRef(null)
  const rolesDisponibles = roles.filter((rol) => rol.nombre !== usuario.rol)

  useEffect(() => { if (confirmando) botonConfirmar.current?.focus() }, [confirmando])

  function revisar(evento) {
    evento.preventDefault()
    if (!nuevoRol) return setErrorRol('Selecciona el nuevo rol del usuario.')
    setErrorRol('')
    setConfirmando(true)
  }

  async function aplicar() {
    setGuardando(true)
    setError('')
    try {
      const resultado = await cambiarRol(usuario, nuevoRol, motivo, demo)
      onCambiado(resultado.usuario, resultado.registro)
    } catch (err) { setConfirmando(false); setError(err.message || 'No se pudo cambiar el rol.') } finally { setGuardando(false) }
  }

  return (
    <>
      <div className="usuarios-dialogo__cabecera"><div><p className="gi-etiqueta">Administración de permisos</p><h2 id="titulo-cambiar-rol">Cambiar rol</h2></div><BotonCerrar onClick={onCerrar} /></div>
      <p>Usuario <strong>{usuario.nombreCompleto}</strong> · Rol actual: <strong>{etiquetaRol(usuario.rol)}</strong></p>
      {error && <div className="gi-mensaje gi-mensaje--error" role="alert">{error}</div>}
      {!confirmando ? (
        <form onSubmit={revisar} noValidate>
          <div className={`fld${errorRol ? ' fld-error' : ''}`}><label htmlFor="rol-nuevo">Nuevo rol</label><select data-autofocus id="rol-nuevo" value={nuevoRol} onChange={(evento) => { setNuevoRol(evento.target.value); setErrorRol('') }} aria-invalid={Boolean(errorRol)} aria-describedby={errorRol ? 'rol-nuevo-error' : 'rol-nuevo-ayuda'}><option value="">Selecciona un rol</option>{rolesDisponibles.map((rol) => <option key={rol.nombre} value={rol.nombre}>{etiquetaRol(rol.nombre)}</option>)}</select>{errorRol ? <span id="rol-nuevo-error" className="gi-error-campo" role="alert">{errorRol}</span> : <span id="rol-nuevo-ayuda" className="hint">El usuario solo accederá a los módulos permitidos por el nuevo rol.</span>}</div>
          <div className="fld"><label htmlFor="rol-motivo">Motivo (opcional)</label><textarea id="rol-motivo" rows={3} maxLength={MAXIMO_MOTIVO} value={motivo} onChange={(evento) => setMotivo(evento.target.value)} aria-describedby="rol-motivo-ayuda" /><span id="rol-motivo-ayuda" className="hint">{motivo.length}/{MAXIMO_MOTIVO} caracteres</span></div>
          <div className="usuarios-dialogo__acciones"><button className="btn primary" type="submit">Revisar cambio</button><button className="btn" type="button" onClick={onCerrar}>Cancelar</button></div>
        </form>
      ) : (
        <div role="alertdialog" aria-labelledby="confirmacion-cambio-rol"><p id="confirmacion-cambio-rol">¿Cambiar el rol de <strong>{usuario.nombreCompleto}</strong> de <strong>{etiquetaRol(usuario.rol)}</strong> a <strong>{etiquetaRol(nuevoRol)}</strong>? El cambio quedará registrado para auditoría.</p><div className="usuarios-dialogo__acciones"><button ref={botonConfirmar} className="btn primary" type="button" onClick={aplicar} disabled={guardando}>{guardando ? 'Guardando…' : 'Sí, cambiar rol'}</button><button className="btn" type="button" onClick={() => setConfirmando(false)} disabled={guardando}>Volver</button></div></div>
      )}
    </>
  )
}

function Dialogo({ tituloId, onCerrar, children }) {
  const panel = useRef(null)

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    requestAnimationFrame(() => {
      const preferido = panel.current?.querySelector('[data-autofocus]')
      const primero = panel.current?.querySelector('button, input, select, textarea')
      const objetivo = preferido || primero
      objetivo?.focus()
    })
    return () => { document.body.style.overflow = anterior }
  }, [])

  function teclado(evento) {
    if (evento.key === 'Escape') return onCerrar()
    if (evento.key !== 'Tab') return
    const controles = [...panel.current.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')]
    if (!controles.length) return
    const primero = controles[0]
    const ultimo = controles[controles.length - 1]
    if (evento.shiftKey && document.activeElement === primero) { evento.preventDefault(); ultimo.focus() }
    if (!evento.shiftKey && document.activeElement === ultimo) { evento.preventDefault(); primero.focus() }
  }

  return <div className="usuarios-modal" role="presentation" onMouseDown={(evento) => { if (evento.target === evento.currentTarget) onCerrar() }}><div className="usuarios-dialogo" ref={panel} role="dialog" aria-modal="true" aria-labelledby={tituloId} onKeyDown={teclado}>{children}</div></div>
}

function BotonCerrar({ onClick }) {
  return <button className="gi-cerrar" type="button" aria-label="Cerrar diálogo" onClick={onClick}><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg></button>
}

function Campo({ id, label, error, children }) {
  return <div className="fld"><label htmlFor={id}>{label}</label>{children}{error && <p id={`${id}-error`} className="gi-error-campo">{error}</p>}</div>
}
