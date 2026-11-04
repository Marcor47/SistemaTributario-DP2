import { Link } from 'react-router-dom'
import { MODULOS } from '../utils/modulos.jsx'
import './InicioPortal.css'

const RUTAS_SERVICIOS = ['/estado-cuenta', '/constancias', '/portal/reclamos']
const servicios = MODULOS.filter((modulo) => RUTAS_SERVICIOS.includes(modulo.ruta))

export default function InicioPortal() {
  return (
    <div className="portal-inicio">
      <section className="portal-bienvenida" aria-labelledby="portal-bienvenida-titulo">
        <div className="portal-bienvenida__contenido">
          <p className="portal-bienvenida__eyebrow">PORTAL DEL CONTRIBUYENTE</p>
          <h2 id="portal-bienvenida-titulo">Tus gestiones municipales, en un solo lugar</h2>
          <p>
            Consulta tu información tributaria y accede a los servicios en línea de la Municipalidad
            de San Miguel, cuando lo necesites.
          </p>
        </div>
        <div className="portal-bienvenida__sello" aria-hidden="true">
          <svg viewBox="0 0 64 64" fill="none">
            <path d="M8 28 32 9l24 19v27H8V28Z" />
            <path d="M23 55V37h18v18M17 29h30M20 21h24" />
          </svg>
        </div>
      </section>

      <section className="portal-servicios" aria-labelledby="portal-servicios-titulo">
        <div className="portal-servicios__cabecera">
          <div>
            <p className="portal-servicios__eyebrow">SERVICIOS DISPONIBLES</p>
            <h2 id="portal-servicios-titulo">¿Qué deseas hacer?</h2>
          </div>
          <p>Selecciona un servicio para continuar.</p>
        </div>

        <div className="portal-servicios__rejilla">
          {servicios.map((servicio) => (
            <article className="portal-servicio" key={servicio.ruta}>
              <span className="portal-servicio__icono">{servicio.icono}</span>
              <h3>{servicio.texto}</h3>
              <p>{servicio.descripcion}</p>
              <Link className="portal-servicio__enlace" to={servicio.ruta}>
                Ir al servicio <span aria-hidden="true">→</span>
              </Link>
            </article>
          ))}
        </div>
      </section>

      <aside className="portal-ayuda" aria-label="Información del portal">
        <span className="portal-ayuda__marca" aria-hidden="true">i</span>
        <p>
          Para revisar tus obligaciones y pagos aplicados, ingresa a <strong>Estado de cuenta</strong>.
          Los servicios disponibles también se encuentran en el menú lateral.
        </p>
      </aside>
    </div>
  )
}
