import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { MODULOS, icono } from '../utils/modulos.jsx'
import { cerrarSesion } from '../services/authService.js'

export default function MenuLateral() {
  const navegar = useNavigate()
  const [errorSalida, setErrorSalida] = useState('')

  async function salir(evento) {
    evento.preventDefault()
    setErrorSalida('')
    try {
      await cerrarSesion()
      navegar('/login', { replace: true })
    } catch (err) {
      setErrorSalida(err.message)
    }
  }

  return (
    <aside className="menu">
      <Link to="/" className="menu-marca" aria-label="Municipalidad de San Miguel, ir al inicio">
        <img src="/logo-san-miguel-circulo.png" alt="" width="342" height="342" />
        <div>
          <span>Municipalidad</span>
          <strong>San Miguel</strong>
        </div>
      </Link>

      <nav aria-label="Módulos del sistema">
        <p className="menu-etiqueta">Módulos</p>
        <div className="menu-nav">
          {MODULOS.map((m) => (
            <NavLink key={m.ruta} to={m.ruta} className="menu-enlace">
              {m.icono}
              <span>{m.texto}</span>
            </NavLink>
          ))}
        </div>
      </nav>

      <div className="menu-pie">
        <p className="menu-demo" role="note">Datos ficticios de demostración</p>
        {errorSalida && <p role="alert">{errorSalida}</p>}
        <Link to="/login" className="menu-salir" onClick={salir}>
          {icono(<><path d="M9 21H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h4" /><path d="M16 17l5-5-5-5M21 12H9" /></>)}
          Salir
        </Link>
      </div>
    </aside>
  )
}
