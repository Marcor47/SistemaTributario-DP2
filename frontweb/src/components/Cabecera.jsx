import { NavLink } from 'react-router-dom'

const MODULOS = [
  { ruta: '/predios', texto: 'Consulta de predios' },
  { ruta: '/contribuyentes', texto: 'Contribuyentes' },
  { ruta: '/formularios', texto: 'Formularios y plantillas' },
]

export default function Cabecera() {
  return (
    <header className="topbar">
      <div className="brand">
        <div className="logo" aria-hidden="true">S</div>
        <div>
          <h1>Plataforma Analítica SAMNET</h1>
          <p>Gerencia de Rentas · Municipalidad de San Miguel</p>
        </div>
      </div>

      <nav className="modesw" aria-label="Módulo de la plataforma">
        {MODULOS.map((m) => (
          <NavLink key={m.ruta} to={m.ruta} className="modebtn">
            {m.texto}
          </NavLink>
        ))}
      </nav>

      <span className="badge demo" role="note">
        <svg width="14" height="14" viewBox="0 0 16 16" aria-hidden="true">
          <path d="M8 1.5l7 12.5H1z" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
          <path d="M8 6v4M8 11.8v.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
        Datos ficticios de demostración
      </span>
    </header>
  )
}
