import { useId, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { CREDENCIALES_DEMO, MODO_DEMO, iniciarSesion } from '../services/authService.js'
import { useAuth } from '../context/AuthContext.jsx'
import LoginLayout, { AlertaLogin, BotonEnviar, CampoClave } from '../components/LoginLayout.jsx'

const ACCESO_PORTAL_PRUEBA = { codigo: '021390', password: 'Portal021390!' }

export default function Login() {
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const { establecerSesion } = useAuth()
  const idCodigo = useId()
  const idClave = useId()
  const idError = useId()
  const [codigo, setCodigo] = useState('')
  const [clave, setClave] = useState('')
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
      establecerSesion({ tipo: 'contribuyente', usuario: { codigoContribuyente: codigo.trim() } })
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
    <LoginLayout
      titulo="Iniciar sesión"
      lateralTitulo="Tu información tributaria, siempre a la mano."
      lateralTexto="Consulta predios, estado de cuenta y regularidad de pago desde un solo lugar."
    >
      <p className="lg-caja__sub">
        {ubicacion.state?.motivo === 'servicios-en-linea'
          ? 'Inicia sesión con tus credenciales de contribuyente para acceder a Servicios en Línea.'
          : 'Ingresa con tu código de contribuyente para acceder al sistema.'}
      </p>

      <form onSubmit={enviar} noValidate>
        <AlertaLogin id={idError} mensaje={error} />

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

        <CampoClave id={idClave} valor={clave} onCambio={setClave} idError={idError} conError={Boolean(error)} />
        <BotonEnviar enviando={enviando} />
      </form>

      {import.meta.env.DEV && (
        <div className="lg-demo" role="note">
          <strong>{MODO_DEMO ? 'Modo demostración (sin servidor)' : 'Acceso de prueba'}</strong>
          <p className="lg-demo__datos">Código: <code>{accesoPrueba.codigo}</code><br />Contraseña: <code>{accesoPrueba.password}</code></p>
          <button className="lg-demo__usar" type="button" onClick={usarAccesoPrueba} disabled={enviando}>Usar estos datos</button>
        </div>
      )}

      <p className="lg-ayuda">
        ¿Problemas para ingresar? Acércate a la Gerencia de Rentas de la Municipalidad de San Miguel.
        <br />
        <Link to="/interno/login">Acceso personal municipal</Link>
      </p>
    </LoginLayout>
  )
}
