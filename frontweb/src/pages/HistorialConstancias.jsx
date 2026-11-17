import { useEffect, useMemo, useState } from 'react'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import { listarSolicitudesConstancia } from '../services/constanciaGestionService.js'
import { listarConstancias, urlConstanciaPdf } from '../services/constanciaVentanillaService.js'
import { fechaHora } from '../utils/formato.js'
import { TAMANIOS_PAGINA, paginar } from '../utils/paginacion.js'
import './HistorialConstancias.css'

// HU020/HU042: archivo de constancias emitidas y solicitudes rechazadas de ambos canales.

const VISTAS = [
  { id: 'EMITIDAS', texto: 'Emitidas' },
  { id: 'RECHAZADAS', texto: 'Rechazadas' },
]
const CANALES = { APP: 'Servicios en Línea', VENTANILLA: 'Ventanilla' }
const normalizar = (valor) => String(valor ?? '').replace(/\s+/g, '').toUpperCase()

export default function HistorialConstancias() {
  const [vista, setVista] = useState('EMITIDAS')
  const [documento, setDocumento] = useState('')
  const [emitidas, setEmitidas] = useState([])
  const [rechazadas, setRechazadas] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [recarga, setRecarga] = useState(0)
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(TAMANIOS_PAGINA[1])

  useEffect(() => {
    let activo = true
    Promise.all([listarConstancias(), listarSolicitudesConstancia('RECHAZADA')])
      .then(([constancias, solicitudes]) => {
        if (!activo) return
        setEmitidas(Array.isArray(constancias) ? constancias : [])
        setRechazadas(Array.isArray(solicitudes) ? solicitudes : [])
        setError('')
      })
      .catch((err) => {
        if (activo) setError(err.message || 'No se pudo cargar el historial de constancias.')
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => { activo = false }
  }, [recarga])

  const filtro = normalizar(documento)
  const listaEmitidas = useMemo(() => emitidas.filter((item) => !filtro
    || normalizar(item.documentoContribuyente).includes(filtro)
    || normalizar(item.numeroConstancia).includes(filtro)), [emitidas, filtro])
  const listaRechazadas = useMemo(() => rechazadas.filter((item) => !filtro
    || normalizar(item.documentoContribuyente).includes(filtro)
    || normalizar(item.idSolicitud).includes(filtro)), [rechazadas, filtro])

  const activa = vista === 'EMITIDAS' ? listaEmitidas : listaRechazadas
  const { visibles, paginaActual } = paginar(activa, pagina, tamanio)

  function cambiarVista(valor) {
    setVista(valor)
    setPagina(1)
  }

  function cambiarFiltro(valor) {
    setDocumento(valor)
    setPagina(1)
  }

  return (
    <div className="historial-constancias">
      <section className="pcard" aria-labelledby="historial-titulo">
        <div className="historial-cabecera">
          <div>
            <h2 id="historial-titulo">Historial</h2>
            <p className="hint">Constancias emitidas y solicitudes rechazadas de ambos canales.</p>
          </div>
          <div className="historial-vistas" role="group" aria-label="Tipo de registro">
            {VISTAS.map((opcion) => (
              <button
                key={opcion.id}
                type="button"
                aria-pressed={vista === opcion.id}
                onClick={() => cambiarVista(opcion.id)}
              >
                {opcion.texto}
              </button>
            ))}
          </div>
        </div>

        <div className="historial-filtros">
          <div className="fld historial-buscador">
            <label htmlFor="historial-filtro">Filtrar por documento o número</label>
            <input
              id="historial-filtro"
              type="search"
              value={documento}
              placeholder="DNI, RUC, N.º de constancia o solicitud"
              onChange={(evento) => cambiarFiltro(evento.target.value)}
            />
          </div>
        </div>

        {cargando && <p className="hint" role="status">Cargando historial…</p>}

        {!cargando && error && (
          <div className="alert" role="alert">
            <p>{error}</p>
            <button
              className="btn"
              type="button"
              onClick={() => { setCargando(true); setError(''); setRecarga((valor) => valor + 1) }}
            >
              Reintentar
            </button>
          </div>
        )}

        {!cargando && !error && visibles.length === 0 && (
          <p className="hint">
            {vista === 'EMITIDAS'
              ? 'No hay constancias emitidas que coincidan con el filtro.'
              : 'No hay solicitudes rechazadas que coincidan con el filtro.'}
          </p>
        )}

        {!cargando && !error && visibles.length > 0 && (
          <>
            <div
              className="tabla-scroll"
              role="region"
              aria-label={vista === 'EMITIDAS' ? 'Constancias emitidas' : 'Solicitudes rechazadas'}
              tabIndex={0}
            >
              {vista === 'EMITIDAS' ? (
                <table className="tabla">
                  <caption className="sr-only">Constancias emitidas</caption>
                  <thead>
                    <tr>
                      <th scope="col">N.º constancia</th>
                      <th scope="col">Fecha de emisión</th>
                      <th scope="col">Contribuyente</th>
                      <th scope="col">Documento</th>
                      <th scope="col">Canal</th>
                      <th scope="col">Emitida por</th>
                      <th scope="col">PDF</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibles.map((item) => (
                      <tr key={item.numeroConstancia}>
                        <th scope="row">{item.numeroConstancia}</th>
                        <td>{fechaHora(item.fechaEmision)}</td>
                        <td>{item.nombreContribuyente || '—'}</td>
                        <td>{item.documentoContribuyente || '—'}</td>
                        <td>{CANALES[item.canal] || item.canal || '—'}</td>
                        <td>{item.nombreTrabajadorEmisor || item.trabajadorEmisor || 'Automática'}</td>
                        <td>
                          <a
                            className="btn btn-compacto"
                            href={urlConstanciaPdf(item.numeroConstancia)}
                            target="_blank"
                            rel="noopener noreferrer"
                            aria-label={`Abrir el PDF de la constancia ${item.numeroConstancia}`}
                          >
                            Ver PDF
                          </a>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <table className="tabla">
                  <caption className="sr-only">Solicitudes rechazadas</caption>
                  <thead>
                    <tr>
                      <th scope="col">Solicitud</th>
                      <th scope="col">Fecha</th>
                      <th scope="col">Contribuyente</th>
                      <th scope="col">Documento</th>
                      <th scope="col">Canal</th>
                      <th scope="col">Motivo del rechazo</th>
                      <th scope="col">Revisada por</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibles.map((item) => (
                      <tr key={item.idSolicitud}>
                        <th scope="row">N.º {item.idSolicitud}</th>
                        <td>{fechaHora(item.fechaRevision || item.fechaSolicitud)}</td>
                        <td>{item.nombreContribuyente || '—'}</td>
                        <td>{item.documentoContribuyente || '—'}</td>
                        <td>{CANALES[item.canal] || item.canal || '—'}</td>
                        <td>{item.motivoRechazo || '—'}</td>
                        <td>{item.operador || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            <PaginacionTabla
              id={`historial-${vista.toLowerCase()}`}
              totalRegistros={activa.length}
              pagina={paginaActual}
              tamanio={tamanio}
              opciones={TAMANIOS_PAGINA}
              onCambiarPagina={setPagina}
              onCambiarTamanio={(valor) => { setTamanio(valor); setPagina(1) }}
            />
          </>
        )}
      </section>
    </div>
  )
}
