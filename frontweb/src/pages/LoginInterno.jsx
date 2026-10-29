import { useId, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { iniciarSesionInterno } from '../services/authInternoService.js'
import { useAuth } from '../context/AuthContext.jsx'
import LoginLayout, { AlertaLogin, BotonEnviar, CampoClave } from '../components/LoginLayout.jsx'

const ACCESO_INTERNO_PRUEBA = { usuario: 'admin', password: 'admin123' }

export default function LoginInterno() {
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const { establecerSesion } = useAuth()
  const idUsuario = useId()
  const idClave = useId()
  const idError = useId()
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')

  async function enviar(e) {
    e.preventDefault()
    if (enviando) return
    if (!usuario.trim() || !clave) {
      setError('Ingresa tu usuario y tu contraseña.')
      return
    }
    setError('')
    setEnviando(true)
    try {
      const sesion = await iniciarSesionInterno(usuario.trim(), clave)
      establecerSesion({ tipo: 'interno', usuario: sesion })
      navegar(ubicacion.state?.from || '/predios', { replace: true })
    } catch (err) {
      setError(err.message)
      setClave('')
    } finally {
      setEnviando(false)
    }
  }

  function usarAccesoPrueba() {
    setUsuario(ACCESO_INTERNO_PRUEBA.usuario)
    setClave(ACCESO_INTERNO_PRUEBA.password)
    setError('')
  }

  return (
    <LoginLayout
      titulo="Acceso personal municipal"
      lateralTitulo="Sistema de Gestión Tributaria."
      lateralTexto="Acceso restringido a funcionarios y personal autorizado de la Municipalidad de San Miguel."
    >
      <p className="lg-caja__sub">Ingresa con tu usuario institucional para acceder a la gestión municipal.</p>

      <form onSubmit={enviar} noValidate>
        <AlertaLogin id={idError} mensaje={error} />

        <div className="lg-campo">
          <label htmlFor={idUsuario}>Usuario</label>
          <input
            id={idUsuario}
            type="text"
            autoComplete="username"
            maxLength={40}
            value={usuario}
            onChange={(e) => setUsuario(e.target.value)}
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
          <strong>Acceso de prueba</strong>
          <p className="lg-demo__datos">Usuario: <code>{ACCESO_INTERNO_PRUEBA.usuario}</code><br />Contraseña: <code>{ACCESO_INTERNO_PRUEBA.password}</code></p>
          <button className="lg-demo__usar" type="button" onClick={usarAccesoPrueba} disabled={enviando}>Usar estos datos</button>
        </div>
      )}

      <p className="lg-ayuda">
        ¿Eres contribuyente? <Link to="/login">Ingresa a Servicios en Línea</Link>
      </p>
    </LoginLayout>
  )
}
