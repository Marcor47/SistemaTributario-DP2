import { useId, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { MotionConfig, motion } from 'framer-motion'
import { iniciarSesionInterna } from '../services/authInternaService.js'
import { useSesionInterna } from '../context/sesionInterna.js'
import { rutaInicialGestion } from '../utils/modulos.jsx'
import './Login.css'

const ACCESO_INTERNO_PRUEBA = { usuario: 'admin.local', clave: 'AdminLocalSM#2026' }

export default function LoginInterno() {
  const { sesion, setSesion, cargando } = useSesionInterna()
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const idUsuario = useId()
  const idClave = useId()
  const idError = useId()
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [verClave, setVerClave] = useState(false)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  function usarAccesoPrueba() {
    setUsuario(ACCESO_INTERNO_PRUEBA.usuario)
    setClave(ACCESO_INTERNO_PRUEBA.clave)
    setError('')
  }

  if (!cargando && sesion) return <Navigate to={sesion.cambioClavePendiente ? '/gestion/cambiar-clave' : rutaInicialGestion(sesion)} replace />

  async function enviar(evento) {
    evento.preventDefault()
    if (!usuario.trim() || !clave) return setError('Ingresa tu usuario institucional y contraseña.')
    setError('')
    setEnviando(true)
    try {
      const nuevaSesion = await iniciarSesionInterna(usuario.trim(), clave)
      setSesion(nuevaSesion)
      const destino = nuevaSesion.cambioClavePendiente ? '/gestion/cambiar-clave' : ubicacion.state?.from || rutaInicialGestion(nuevaSesion)
      navegar(destino, { replace: true })
    } catch (err) {
      setError(err.estado === 429 ? 'Se alcanzó el límite de intentos. Espera 15 minutos.' : 'El usuario o la contraseña no son correctos.')
      setClave('')
    } finally { setEnviando(false) }
  }

  return (
    <MotionConfig reducedMotion="user">
      <div className="lg">
        <aside className="lg-lado">
          <img className="lg-lado__foto" src="/municipalidad.jpg" alt="" />
          <div className="lg-lado__velo" aria-hidden="true" />
          <div className="lg-lado__contenido">
            <Link to="/" className="lg-lado__logo" aria-label="Volver al inicio">
              <img src="/logo-san-miguel-circulo.png" alt="" width="342" height="342" />
              <span>Municipalidad<br /><strong>San Miguel</strong></span>
            </Link>
            <div><h2>Gestión tributaria para el personal municipal.</h2><p>Accede a las funciones autorizadas según el rol asignado a tu cuenta institucional.</p></div>
          </div>
        </aside>
        <main className="lg-panel">
          <motion.div className="lg-caja" initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, ease: 'easeOut' }}>
            <Link to="/" className="lg-volver">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M19 12H5M11 6l-6 6 6 6" /></svg>
              Volver al inicio
            </Link>
            <h1>Acceso institucional</h1>
            <p className="lg-caja__sub">Ingresa con la cuenta asignada por el Administrador del Sistema.</p>
            <form onSubmit={enviar} noValidate>
              {error && <div id={idError} className="lg-alerta" role="alert"><span>{error}</span></div>}
              <div className="lg-campo">
                <label htmlFor={idUsuario}>Usuario institucional</label>
                <input id={idUsuario} type="text" value={usuario} onChange={(e) => setUsuario(e.target.value)} maxLength="40" autoComplete="username" aria-invalid={Boolean(error)} aria-describedby={error ? idError : undefined} autoFocus />
              </div>
              <div className="lg-campo">
                <label htmlFor={idClave}>Contraseña</label>
                <div className="lg-clave">
                  <input id={idClave} type={verClave ? 'text' : 'password'} value={clave} onChange={(e) => setClave(e.target.value)} maxLength="256" autoComplete="current-password" aria-invalid={Boolean(error)} aria-describedby={error ? idError : undefined} />
                  <BotonVisibilidad visible={verClave} onClick={() => setVerClave((valor) => !valor)} />
                </div>
              </div>
              <button type="submit" className="lg-enviar" disabled={enviando}>{enviando ? 'Verificando…' : 'Ingresar'}</button>
            </form>
            {import.meta.env.DEV && <div className="lg-demo" role="note"><strong>Acceso administrativo local</strong><p className="lg-demo__datos">Usuario: <code>{ACCESO_INTERNO_PRUEBA.usuario}</code><br />Contraseña: <code>{ACCESO_INTERNO_PRUEBA.clave}</code></p><button className="lg-demo__usar" type="button" onClick={usarAccesoPrueba} disabled={enviando}>Usar estos datos</button></div>}
            <div className="lg-alternar">
              <span>¿Eres contribuyente?</span>
              <Link to="/login">Ingresar a Servicios en Línea</Link>
            </div>
            <p className="lg-ayuda">Este acceso es exclusivo para personal autorizado de la Municipalidad.</p>
          </motion.div>
        </main>
      </div>
    </MotionConfig>
  )
}

function BotonVisibilidad({ visible, onClick }) {
  return (
    <button type="button" className="lg-clave__ojo" onClick={onClick} aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={visible}>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" /><circle cx="12" cy="12" r="3" />{visible && <path d="M4 4l16 16" />}
      </svg>
    </button>
  )
}
