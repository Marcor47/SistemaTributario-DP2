import { useState } from 'react'
import { Link } from 'react-router-dom'
import { MotionConfig, motion } from 'framer-motion'
import '../pages/Login.css'

/** Estructura común de las pantallas de acceso: panel lateral institucional y caja del formulario. */
export default function LoginLayout({ titulo, lateralTitulo, lateralTexto, children }) {
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
              <h2>{lateralTitulo}</h2>
              <p>{lateralTexto}</p>
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
            <h1>{titulo}</h1>
            {children}
          </motion.div>
        </main>
      </div>
    </MotionConfig>
  )
}

export function AlertaLogin({ id, mensaje }) {
  if (!mensaje) return null
  return (
    <div id={id} className="lg-alerta" role="alert">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7.5v5.5M12 16.5v.1" />
      </svg>
      <span>{mensaje}</span>
    </div>
  )
}

export function CampoClave({ id, valor, onCambio, idError, conError }) {
  const [ver, setVer] = useState(false)
  return (
    <div className="lg-campo">
      <label htmlFor={id}>Contraseña</label>
      <div className="lg-clave">
        <input
          id={id}
          type={ver ? 'text' : 'password'}
          autoComplete="current-password"
          maxLength={256}
          value={valor}
          onChange={(e) => onCambio(e.target.value)}
          aria-invalid={conError}
          aria-describedby={conError ? idError : undefined}
        />
        <button
          type="button"
          className="lg-clave__ojo"
          onClick={() => setVer((v) => !v)}
          aria-label={ver ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          aria-pressed={ver}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" />
            <circle cx="12" cy="12" r="3" />
            {ver && <path d="M4 4l16 16" />}
          </svg>
        </button>
      </div>
    </div>
  )
}

export function BotonEnviar({ enviando }) {
  return (
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
  )
}
