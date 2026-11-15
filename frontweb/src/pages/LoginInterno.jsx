import { useId, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { iniciarSesionInterna } from '../services/authInternaService.js'
import { useSesionInterna } from '../context/sesionInterna.js'
import LoginLayout, { AlertaLogin, BotonEnviar, CampoClave } from '../components/LoginLayout.jsx'
import { rutaInicialGestion } from '../utils/modulos.jsx'


export default function LoginInterno() {
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const { sesion, setSesion, cargando } = useSesionInterna()
  const idUsuario = useId()
  const idClave = useId()
  const idError = useId()
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')

  if (!cargando && sesion) return <Navigate to={sesion.cambioClavePendiente ? '/gestion/cambiar-clave' : rutaInicialGestion(sesion)} replace />

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
      const nuevaSesion = await iniciarSesionInterna(usuario.trim(), clave)
      setSesion(nuevaSesion)
      navegar(nuevaSesion.cambioClavePendiente ? '/gestion/cambiar-clave' : ubicacion.state?.from || rutaInicialGestion(nuevaSesion), { replace: true })
    } catch (err) {
      setError(err.estado === 429 ? 'Se alcanzó el límite de intentos. Espera 15 minutos.' : 'El usuario o la contraseña no son correctos.')
      setClave('')
    } finally {
      setEnviando(false)
    }
  }


  return (
    <LoginLayout
      titulo="Acceso institucional"
      lateralTitulo="Gestión tributaria para el personal municipal."
      lateralTexto="Accede a las funciones autorizadas según el rol asignado a tu cuenta institucional."
    >
      <p className="lg-caja__sub">Ingresa con la cuenta asignada por el Administrador del Sistema.</p>

      <form onSubmit={enviar} noValidate>
        <AlertaLogin id={idError} mensaje={error} />

        <div className="lg-campo">
          <label htmlFor={idUsuario}>Usuario institucional</label>
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

      <p className="lg-ayuda">
        ¿Eres contribuyente? <Link to="/login">Ingresa a Servicios en Línea</Link>
      </p>
    </LoginLayout>
  )
}
