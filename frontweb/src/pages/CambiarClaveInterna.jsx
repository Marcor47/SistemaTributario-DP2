import { useEffect, useId, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSesionInterna } from '../context/sesionInterna.js'
import { cambiarClaveInterna } from '../services/authInternaService.js'
import './GestionInterna.css'

const VACIO = { actual: '', nueva: '', confirmar: '' }

export default function CambiarClaveInterna() {
  const { setSesion, salir } = useSesionInterna()
  const navegar = useNavigate()
  const actualId = useId()
  const nuevaId = useId()
  const confirmarId = useId()
  const exitoRef = useRef(null)
  const resumenRef = useRef(null)
  const [datos, setDatos] = useState(VACIO)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [cancelando, setCancelando] = useState(false)
  const [exito, setExito] = useState(false)

  useEffect(() => { if (exito) exitoRef.current?.focus() }, [exito])

  function validar() {
    const nuevos = {}
    if (!datos.actual) nuevos.actual = 'Ingresa la contraseña temporal.'
    if (datos.nueva.length < 12) nuevos.nueva = 'Debe tener al menos 12 caracteres.'
    else if (datos.nueva.length > 64 || new TextEncoder().encode(datos.nueva).length > 72) nuevos.nueva = 'No puede superar 64 caracteres ni 72 bytes.'
    else if (!/[A-Z]/.test(datos.nueva) || !/[a-z]/.test(datos.nueva) || !/\d/.test(datos.nueva) || !/[^A-Za-z0-9]/.test(datos.nueva)) nuevos.nueva = 'Debe incluir mayúscula, minúscula, número y símbolo.'
    if (!datos.confirmar) nuevos.confirmar = 'Confirma la nueva contraseña.'
    else if (datos.nueva !== datos.confirmar) nuevos.confirmar = 'La confirmación no coincide con la nueva contraseña.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length) setTimeout(() => resumenRef.current?.focus(), 0)
    return Object.keys(nuevos).length === 0
  }

  async function enviar(evento) {
    evento.preventDefault()
    if (!validar()) return
    setError('')
    setEnviando(true)
    try {
      await cambiarClaveInterna(datos.actual, datos.nueva)
      setDatos(VACIO)
      setExito(true)
    } catch (err) { setError(err.message) } finally { setEnviando(false) }
  }

  async function cancelar() {
    setError('')
    setCancelando(true)
    try {
      await salir()
      navegar('/gestion/login', { replace: true })
    } catch (err) { setError(err.message) } finally { setCancelando(false) }
  }

  function volverAlLogin() {
    setSesion(null)
    navegar('/gestion/login', { replace: true })
  }

  return (
    <main className="gi-acceso">
      <section className="gi-login" aria-labelledby={exito ? 'titulo-clave-actualizada' : 'titulo-cambio-clave'}>
        {exito ? (
          <div className="gi-confirmacion" role="status" tabIndex="-1" ref={exitoRef}>
            <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M8 12.5l2.5 2.5L16 9" /></svg>
            <p className="gi-etiqueta">Proceso completado</p>
            <h1 id="titulo-clave-actualizada">Contraseña actualizada correctamente</h1>
            <p>La sesión temporal se cerró por seguridad. Ingresa nuevamente con tu contraseña nueva.</p>
            <button type="button" className="btn primary gi-ancho" onClick={volverAlLogin}>Volver a iniciar sesión</button>
          </div>
        ) : (
          <>
            <p className="gi-etiqueta">Primer ingreso</p>
            <h1 id="titulo-cambio-clave">Crea una contraseña personal</h1>
            <p>Debe tener 12 caracteres como mínimo e incluir mayúscula, minúscula, número y símbolo.</p>
            {Object.keys(errores).length > 0 && <div className="gi-mensaje gi-mensaje--error" role="alert" tabIndex="-1" ref={resumenRef}><strong>Revisa los campos señalados.</strong></div>}
            {error && <div className="gi-mensaje gi-mensaje--error" role="alert">{error}</div>}
            <form onSubmit={enviar} noValidate>
              <CampoClave id={actualId} label="Contraseña temporal" valor={datos.actual} alCambiar={(valor) => setDatos({ ...datos, actual: valor })} autoComplete="current-password" error={errores.actual} />
              <CampoClave id={nuevaId} label="Nueva contraseña" valor={datos.nueva} alCambiar={(valor) => setDatos({ ...datos, nueva: valor })} autoComplete="new-password" error={errores.nueva} />
              <CampoClave id={confirmarId} label="Confirmar nueva contraseña" valor={datos.confirmar} alCambiar={(valor) => setDatos({ ...datos, confirmar: valor })} autoComplete="new-password" error={errores.confirmar} />
              <button type="submit" className="btn primary gi-ancho" disabled={enviando || cancelando}>{enviando ? 'Guardando…' : 'Cambiar contraseña'}</button>
              <button type="button" className="btn gi-ancho" onClick={cancelar} disabled={enviando || cancelando}>{cancelando ? 'Cerrando sesión…' : 'Cancelar y volver al inicio de sesión'}</button>
            </form>
          </>
        )}
      </section>
    </main>
  )
}

function CampoClave({ id, label, valor, alCambiar, autoComplete, error }) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="fld">
      <label htmlFor={id}>{label}</label>
      <div className="gi-clave">
        <input id={id} type={visible ? 'text' : 'password'} autoComplete={autoComplete} maxLength="64" value={valor} onChange={(evento) => alCambiar(evento.target.value)} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : undefined} />
        <button type="button" className="gi-clave__ojo" onClick={() => setVisible((valorActual) => !valorActual)} aria-label={visible ? `Ocultar ${label.toLowerCase()}` : `Mostrar ${label.toLowerCase()}`} aria-pressed={visible}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" /><circle cx="12" cy="12" r="3" />{visible && <path d="M4 4l16 16" />}</svg>
        </button>
      </div>
      {error && <p id={`${id}-error`} className="gi-error-campo">{error}</p>}
    </div>
  )
}
