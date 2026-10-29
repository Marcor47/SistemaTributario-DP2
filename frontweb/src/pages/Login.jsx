import { useId, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { MotionConfig, motion } from 'framer-motion'
import { CREDENCIALES_DEMO, MODO_DEMO, iniciarSesion } from '../services/authService.js'
import './Login.css'

const ACCESO_PORTAL_PRUEBA = { codigo: '021390', password: 'Portal021390!' }

export default function Login() {
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const idCodigo = useId()
  const idClave = useId()
  const idError = useId()
  const [codigo, setCodigo] = useState('')
  const [clave, setClave] = useState('')
  const [verClave, setVerClave] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')
  const accesoPrueba = MODO_DEMO ? CREDENCIALES_DEMO : ACCESO_PORTAL_PRUEBA

  function usarAccesoPrueba() {
    setCodigo(accesoPrueba.codigo)
    setClave(accesoPrueba.password)
    setError('')
  }

  async function enviar(e) {
    e.preventDefault()
    if (enviando) return
    if (!codigo.trim() || !clave) {
      setError('Ingresa tu código de contribuyente y tu contraseña.')
      return
    }
    setError('')
    setEnviando(true)
    try {
      await iniciarSesion(codigo.trim(), clave)
      const destino = ubicacion.state?.from || '/estado-cuenta'
      navegar(destino, { replace: true })
    } catch (err) {
      setError(err.message)
      setClave('')
    } finally {
      setEnviando(false)
    }
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
            <div>
              <h2>Tu información tributaria, siempre a la mano.</h2>
              <p>Consulta predios, estado de cuenta y regularidad de pago desde un solo lugar.</p>
            </div>
          </div>
        </aside>

        <main className="lg-panel">
          <motion.div
            className="lg-caja"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: 'easeOut' }}
          >
            <Link to="/" className="lg-volver">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M19 12H5M11 6l-6 6 6 6" />
              </svg>
              Volver al inicio
            </Link>

            <h1>Iniciar sesión</h1>
            <p className="lg-caja__sub">
              {ubicacion.state?.motivo === 'servicios-en-linea'
                ? 'Inicia sesión con tus credenciales de contribuyente para acceder a Servicios en Línea.'
                : 'Ingresa con tu código de contribuyente para acceder al sistema.'}
            </p>

            <form onSubmit={enviar} noValidate>
              {error && (
                <div id={idError} className="lg-alerta" role="alert">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="9" />
                    <path d="M12 7.5v5.5M12 16.5v.1" />
                  </svg>
                  <span>{error}</span>
                </div>
              )}

              <div className="lg-campo">
                <label htmlFor={idCodigo}>Código de contribuyente</label>
                <input
                  id={idCodigo}
                  type="text"
                  autoComplete="username"
                  maxLength={40}
                  value={codigo}
                  onChange={(e) => setCodigo(e.target.value)}
                  aria-invalid={Boolean(error)}
                  aria-describedby={error ? idError : undefined}
                  autoFocus
                />
              </div>

              <div className="lg-campo">
                <label htmlFor={idClave}>Contraseña</label>
                <div className="lg-clave">
                  <input
                    id={idClave}
                    type={verClave ? 'text' : 'password'}
                    autoComplete="current-password"
                    maxLength={256}
                    value={clave}
                    onChange={(e) => setClave(e.target.value)}
                    aria-invalid={Boolean(error)}
                    aria-describedby={error ? idError : undefined}
                  />
                  <button
                    type="button"
                    className="lg-clave__ojo"
                    onClick={() => setVerClave((v) => !v)}
                    aria-label={verClave ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    aria-pressed={verClave}
                  >
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" />
                      <circle cx="12" cy="12" r="3" />
                      {verClave && <path d="M4 4l16 16" />}
                    </svg>
                  </button>
                </div>
              </div>

              <button type="submit" className="lg-enviar" disabled={enviando}>
                {enviando ? (
                  <>
                    <span className="lg-spinner" aria-hidden="true" />
                    Verificando…
                  </>
                ) : (
                  'Ingresar'
                )}
              </button>
            </form>

            {import.meta.env.DEV && (
              <div className="lg-demo" role="note">
                <strong>{MODO_DEMO ? 'Modo demostración (sin servidor)' : 'Acceso de prueba'}</strong>
                <p className="lg-demo__datos">Código: <code>{accesoPrueba.codigo}</code><br />Contraseña: <code>{accesoPrueba.password}</code></p>
                <button className="lg-demo__usar" type="button" onClick={usarAccesoPrueba} disabled={enviando}>Usar estos datos</button>
              </div>
            )}

            <div className="lg-alternar">
              <span>¿Trabajas en la Municipalidad?</span>
              <Link to="/gestion/login">Ingresar como funcionario</Link>
            </div>

            <p className="lg-ayuda">
              ¿Problemas para ingresar? Acércate a la Gerencia de Rentas de la Municipalidad de San Miguel.
            </p>
          </motion.div>
        </main>
      </div>
    </MotionConfig>
  )
}
