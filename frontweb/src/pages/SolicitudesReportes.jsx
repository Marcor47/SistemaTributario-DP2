import { useEffect, useState } from 'react'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import { consultarSolicitudesReporte } from '../services/solicitudReporteService.js'
import { fechaHora } from '../utils/formato.js'
import {
  etiquetaOperacion,
  FILTROS_SOLICITUDES_INICIALES,
  textoFiltrosAplicados,
  TIPOS_REPORTE,
  validarPeriodoSolicitudes,
} from '../utils/solicitudesReportes.js'
import './SolicitudesReportes.css'

const DATOS_VACIOS = { total: 0, usuariosDistintos: 0, frecuencias: [], solicitudes: [], totalPaginas: 0 }

export default function SolicitudesReportes() {
  const [filtros, setFiltros] = useState(FILTROS_SOLICITUDES_INICIALES)
  const [aplicados, setAplicados] = useState(FILTROS_SOLICITUDES_INICIALES)
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)
  const [revision, setRevision] = useState(0)
  const [validacion, setValidacion] = useState('')
  const [estado, setEstado] = useState({ cargando: true, datos: DATOS_VACIOS, error: null })

  useEffect(() => {
    const controlador = new AbortController()
    consultarSolicitudesReporte(aplicados, pagina, tamanio, { signal: controlador.signal })
      .then((datos) => {
        if (!controlador.signal.aborted) setEstado({ cargando: false, datos, error: null })
      })
      .catch((error) => {
        if (!controlador.signal.aborted && error.name !== 'AbortError') {
          setEstado((anterior) => ({ ...anterior, cargando: false, error }))
        }
      })
    return () => controlador.abort()
  }, [aplicados, pagina, tamanio, revision])

  function cambiar(campo, valor) {
    setFiltros((anteriores) => ({ ...anteriores, [campo]: valor }))
    if (campo === 'desde' || campo === 'hasta') setValidacion('')
  }

  function buscar(evento) {
    evento.preventDefault()
    const error = validarPeriodoSolicitudes(filtros)
    setValidacion(error)
    if (error) return
    setEstado((anterior) => ({ ...anterior, cargando: true, error: null }))
    setPagina(1)
    setAplicados({ ...filtros, usuario: filtros.usuario.trim() })
  }

  function limpiar() {
    setEstado((anterior) => ({ ...anterior, cargando: true, error: null }))
    setFiltros(FILTROS_SOLICITUDES_INICIALES)
    setAplicados(FILTROS_SOLICITUDES_INICIALES)
    setValidacion('')
    setPagina(1)
  }

  const { cargando, datos, error } = estado

  return (
    <div className="solicitudes-reportes" aria-busy={cargando}>
      <form className="pcard solicitudes-reportes__filtros" onSubmit={buscar} aria-label="Filtros del uso de reportes">
        <div className="solicitudes-reportes__filtros-cab">
          <div>
            <h2>Consultar solicitudes</h2>
            <p className="hint">Filtra el registro por usuario, reporte o periodo. Los datos se actualizan al pulsar Buscar.</p>
          </div>
          <div className="solicitudes-reportes__acciones">
            <button className="btn" type="button" onClick={limpiar} disabled={cargando}>Limpiar</button>
            <button className="btn primary" type="submit" disabled={cargando}>{cargando ? 'Consultando…' : 'Buscar'}</button>
          </div>
        </div>
        <div className="solicitudes-reportes__campos">
          <label className="fld" htmlFor="solicitudes-usuario">
            <span>Usuario</span>
            <input id="solicitudes-usuario" value={filtros.usuario} maxLength="200" placeholder="Nombre o usuario institucional" onChange={(evento) => cambiar('usuario', evento.target.value)} />
          </label>
          <label className="fld" htmlFor="solicitudes-tipo">
            <span>Reporte</span>
            <select id="solicitudes-tipo" value={filtros.tipo} onChange={(evento) => cambiar('tipo', evento.target.value)}>
              <option value="">Todos los reportes</option>
              {TIPOS_REPORTE.map((tipo) => <option key={tipo.valor} value={tipo.valor}>{tipo.etiqueta}</option>)}
            </select>
          </label>
          <label className="fld" htmlFor="solicitudes-desde">
            <span>Desde</span>
            <input id="solicitudes-desde" type="date" value={filtros.desde} onChange={(evento) => cambiar('desde', evento.target.value)} aria-describedby={validacion ? 'solicitudes-periodo-error' : undefined} />
          </label>
          <label className="fld" htmlFor="solicitudes-hasta">
            <span>Hasta</span>
            <input id="solicitudes-hasta" type="date" value={filtros.hasta} onChange={(evento) => cambiar('hasta', evento.target.value)} aria-describedby={validacion ? 'solicitudes-periodo-error' : undefined} />
          </label>
        </div>
        {validacion && <p id="solicitudes-periodo-error" className="solicitudes-reportes__error-campo" role="alert">{validacion}</p>}
      </form>

      {error && <section className="alert solicitudes-reportes__alerta" role="alert">
        <div><strong>{error.estado === 403 ? 'Acceso restringido' : 'No se pudo consultar el registro'}</strong><p>{error.message}</p></div>
        {error.estado !== 403 && <button className="btn" type="button" onClick={() => { setEstado((anterior) => ({ ...anterior, cargando: true, error: null })); setRevision((valor) => valor + 1) }}>Reintentar</button>}
      </section>}

      {!error && <>
        <section className="solicitudes-reportes__resumen" aria-label="Resumen del uso de reportes" aria-live="polite">
          <TarjetaResumen etiqueta="Solicitudes" valor={cargando ? '—' : datos.total} detalle="En el resultado filtrado" />
          <TarjetaResumen etiqueta="Usuarios distintos" valor={cargando ? '—' : datos.usuariosDistintos} detalle="Funcionarios que solicitaron reportes" />
          {datos.frecuencias.map((frecuencia) => <TarjetaResumen key={frecuencia.tipo} etiqueta={frecuencia.titulo} valor={cargando ? '—' : frecuencia.cantidad} detalle="Solicitudes registradas" />)}
        </section>

        <section className="pcard solicitudes-reportes__registro">
          <div className="solicitudes-reportes__registro-cab">
            <div><h2>Registro de solicitudes</h2><p className="hint">La información se conserva como trazabilidad del uso de los reportes tributarios.</p></div>
            {!cargando && datos.total > 0 && <PaginacionTabla id="solicitudes-reportes" totalRegistros={datos.total} pagina={pagina} tamanio={tamanio} onCambiarPagina={(valor) => { setEstado((anterior) => ({ ...anterior, cargando: true, error: null })); setPagina(valor) }} onCambiarTamanio={(valor) => { setEstado((anterior) => ({ ...anterior, cargando: true, error: null })); setTamanio(valor); setPagina(1) }} />}
          </div>
          {cargando && <p className="solicitudes-reportes__estado" role="status">Consultando solicitudes de reportes…</p>}
          {!cargando && datos.total === 0 && <div className="solicitudes-reportes__estado" role="status"><h3>No se encontraron solicitudes</h3><p>Prueba con otros filtros o limpia la búsqueda para consultar todo el registro.</p></div>}
          {!cargando && datos.total > 0 && <div className="solicitudes-reportes__tabla" tabIndex="0" role="region" aria-label="Tabla de solicitudes de reportes">
            <table>
              <thead><tr><th scope="col">Usuario</th><th scope="col">Reporte</th><th scope="col">Fecha y hora</th><th scope="col">Operación</th><th scope="col">Filtros aplicados</th></tr></thead>
              <tbody>{datos.solicitudes.map((solicitud) => <tr key={solicitud.idSolicitud}>
                <td><strong>{solicitud.nombreCompleto}</strong><span className="solicitudes-reportes__usuario">{solicitud.nombreUsuario}</span></td>
                <td>{solicitud.tituloReporte}</td>
                <td className="solicitudes-reportes__fecha">{fechaHora(solicitud.fechaHora)}</td>
                <td><span className={`solicitudes-reportes__operacion solicitudes-reportes__operacion--${solicitud.operacion.toLowerCase()}`}>{etiquetaOperacion(solicitud.operacion)}</span></td>
                <td><span className="solicitudes-reportes__filtros-texto" title={textoFiltrosAplicados(solicitud.filtrosAplicados)}>{textoFiltrosAplicados(solicitud.filtrosAplicados)}</span></td>
              </tr>)}</tbody>
            </table>
          </div>}
        </section>
      </>}
    </div>
  )
}

function TarjetaResumen({ etiqueta, valor, detalle }) {
  return <article className="pcard solicitudes-reportes__tarjeta"><span>{etiqueta}</span><strong>{valor}</strong><p>{detalle}</p></article>
}
