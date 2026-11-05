import { Link } from 'react-router-dom'
import { MODULOS } from '../utils/modulos.jsx'
import { useSesionInterna } from '../context/sesionInterna.js'
import { etiquetaRol } from '../utils/roles.js'
import './InicioGestion.css'

const DESCRIPCIONES_ROL = {
  ADMINISTRADOR: 'Administración centralizada de usuarios, control de roles institucionales y seguridad de accesos.',
  GERENCIA: 'Supervisión integral de recaudación tributaria, seguimiento de metas e indicadores gerenciales.',
  ANALISTA_RENTAS: 'Fiscalización, consulta técnica de predios y contribuyentes, y reportes analíticos de rentas.',
  RESPONSABLE_DATOS: 'Homologación de información, aseguramiento de la calidad de datos y auditoría de procesos.',
  VENTANILLA: 'Atención presencial a los vecinos y emisión de constancias tributarias en plataforma.',
  TERMINALISTA: 'Recepción, verificación y procesamiento de formularios y declaraciones juradas tributarias.',
  GESTOR_RECLAMOS: 'Recepción, clasificación, derivación y seguimiento integral de reclamos ciudadanos y laborales.',
}

export default function InicioGestion() {
  const { sesion } = useSesionInterna()

  const rolCodigo = sesion?.rol || sesion?.roles?.[0]
  const rolTexto = etiquetaRol(rolCodigo)
  const descripcionRol = DESCRIPCIONES_ROL[rolCodigo] || 'Accede a los módulos y herramientas autorizadas según tus funciones.'

  // Módulos de gestión a los que este usuario tiene permiso (excluyendo la propia pantalla de Inicio)
  const modulosDisponibles = MODULOS.filter(
    (modulo) =>
      modulo.seccion === 'gestion' &&
      modulo.ruta !== '/gestion/inicio' &&
      (!modulo.permiso || sesion?.permisos?.includes(modulo.permiso))
  )

  const nombreUsuario = sesion?.nombreCompleto || sesion?.nombreUsuario || 'Funcionario Municipal'

  return (
    <div className="gestion-inicio">
      <section className="gestion-bienvenida" aria-labelledby="gestion-bienvenida-titulo">
        <div className="gestion-bienvenida__contenido">
          <div className="gestion-bienvenida__meta">
            <span className="gestion-bienvenida__eyebrow">SISTEMA DE GESTIÓN TRIBUTARIA</span>
            <span className="gestion-bienvenida__badge" title={`Rol: ${rolTexto}`}>
              {rolTexto}
            </span>
          </div>
          <h2 id="gestion-bienvenida-titulo">
            Bienvenido(a), <strong>{nombreUsuario}</strong>
          </h2>
          <p className="gestion-bienvenida__desc">
            {descripcionRol} Selecciona cualquiera de las herramientas habilitadas para tu perfil o utiliza el menú lateral para navegar.
          </p>
        </div>
        <div className="gestion-bienvenida__sello" aria-hidden="true">
          <svg viewBox="0 0 64 64" fill="none">
            <path d="M8 28 32 9l24 19v27H8V28Z" />
            <path d="M23 55V37h18v18M17 29h30M20 21h24" />
          </svg>
        </div>
      </section>

      <section className="gestion-modulos" aria-labelledby="gestion-modulos-titulo">
        <div className="gestion-modulos__cabecera">
          <div>
            <p className="gestion-modulos__eyebrow">MÓDULOS HABILITADOS PARA TU ROL</p>
            <h2 id="gestion-modulos-titulo">¿Qué deseas gestionar hoy?</h2>
          </div>
          <p className="gestion-modulos__conteo">
            {modulosDisponibles.length} {modulosDisponibles.length === 1 ? 'módulo disponible' : 'módulos disponibles'}
          </p>
        </div>

        {modulosDisponibles.length === 0 ? (
          <div className="gestion-modulos__vacio" role="alert">
            <span className="gestion-modulos__vacio-icono" aria-hidden="true">ℹ</span>
            <div>
              <h3>Sin módulos asignados actualmente</h3>
              <p>
                Tu cuenta institucional no tiene permisos activos asignados. Comunícate con el
                Administrador del Sistema para habilitar los accesos correspondientes a tus funciones.
              </p>
            </div>
          </div>
        ) : (
          <div className="gestion-modulos__rejilla">
            {modulosDisponibles.map((modulo) => (
              <article className="gestion-card-modulo" key={modulo.ruta}>
                <div className="gestion-card-modulo__cabecera">
                  <span className="gestion-card-modulo__icono">{modulo.icono}</span>
                  {modulo.permiso && (
                    <span className="gestion-card-modulo__clave" title={`Permiso: ${modulo.permiso}`}>
                      Acceso activo
                    </span>
                  )}
                </div>
                <h3>{modulo.texto}</h3>
                <p>{modulo.descripcion}</p>
                <Link className="gestion-card-modulo__enlace" to={modulo.ruta}>
                  Ingresar al módulo <span aria-hidden="true">→</span>
                </Link>
              </article>
            ))}
          </div>
        )}
      </section>

      <aside className="gestion-ayuda" aria-label="Información institucional de seguridad">
        <span className="gestion-ayuda__marca" aria-hidden="true">i</span>
        <p>
          Estás operando en la plataforma interna con credenciales de <strong>{rolTexto}</strong>.
          Toda acción realizada dentro de los módulos queda registrada para fines de auditoría y trazabilidad institucional.
        </p>
      </aside>
    </div>
  )
}

