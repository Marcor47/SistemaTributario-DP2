import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import AdjuntosSolicitudPredio from '../components/AdjuntosSolicitudPredio.jsx'
import DecisionSolicitudPredio from '../components/DecisionSolicitudPredio.jsx'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import { listarSolicitudesPredio, obtenerSolicitudPredio } from '../services/revisionPredioService.js'
import { fechaHora, moneda } from '../utils/formato.js'
import { paginar, TAMANIOS_PAGINA } from '../utils/paginacion.js'
import './SolicitudesPredio.css'

const RUTA = '/ventanilla/solicitudes-predio'
const ESTADOS = {
  EN_REVISION: { texto: 'En revisión', clase: 'aviso' },
  ACEPTADA: { texto: 'Aceptada', clase: 'ok' },
  RECHAZADA: { texto: 'Rechazada', clase: 'critico' },
}
const TRAMITES = { REGISTRO: 'Registro', TRASPASO: 'Traspaso' }
const TIPOS_PREDIO = { URBANO: 'Urbano', RUSTICO: 'Rústico', OTRO: 'Otro' }

function Estado({ valor }) {
  const estado = ESTADOS[valor]
  return <span className={`estado ${estado?.clase ?? ''}`}>{estado?.texto ?? valor}</span>
}

function ErrorConsulta({ mensaje, onReintentar }) {
  return (
    <div className="alert" role="alert">
      <p>{mensaje}</p>
      <button className="btn" type="button" onClick={onReintentar}>Reintentar</button>
    </div>
  )
}

export default function SolicitudesPredio() {
  const { idSolicitud } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const filtro = parametros.get('estado')
  const estado = Object.hasOwn(ESTADOS, filtro) ? filtro : 'EN_REVISION'
  const consulta = estado === 'EN_REVISION' ? '' : `?estado=${estado}`

  if (idSolicitud) return <DetalleSolicitud key={idSolicitud} id={idSolicitud} volver={`${RUTA}${consulta}`} />

  return (
    <BandejaSolicitudes
      estado={estado}
      consulta={consulta}
      onFiltrar={(valor) => setParametros(valor === 'EN_REVISION' ? {} : { estado: valor }, { replace: true })}
    />
  )
}

function BandejaSolicitudes({ estado, consulta, onFiltrar }) {
  const [resultado, setResultado] = useState(null)
  const [recarga, setRecarga] = useState(0)
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)
  const actual = resultado?.estado === estado && resultado.recarga === recarga ? resultado : null
  const { solicitudes, error } = actual ?? {}
  const listado = paginar(solicitudes ?? [], pagina, tamanio)
  const cargando = !actual

  useEffect(() => {
    const peticion = new AbortController()
    listarSolicitudesPredio(estado, peticion.signal)
      .then((solicitudes) => { if (!peticion.signal.aborted) setResultado({ estado, recarga, solicitudes }) })
      .catch((err) => { if (!peticion.signal.aborted) setResultado({ estado, recarga, error: err.message }) })
    return () => peticion.abort()
  }, [estado, recarga])

  function recargar() {
    setRecarga((valor) => valor + 1)
  }

  function filtrar(evento) {
    setPagina(1)
    setResultado(null)
    onFiltrar(evento.target.value)
  }

  return (
    <section className="pcard revision-predio" aria-labelledby="titulo-bandeja-predios" aria-busy={cargando}>
      <div className="revision-predio__cabecera">
        <h2 id="titulo-bandeja-predios">Bandeja de Ventanilla</h2>
        <div className="revision-predio__acciones">
          <div className="fld">
            <label htmlFor="filtro-estado-predio">Estado</label>
            <select id="filtro-estado-predio" value={estado} onChange={filtrar}>
              {Object.entries(ESTADOS).map(([valor, { texto }]) => <option value={valor} key={valor}>{texto}</option>)}
            </select>
          </div>
          <button className="btn" type="button" onClick={recargar} disabled={cargando}>Actualizar</button>
        </div>
      </div>

      {cargando && <p className="hint mensaje-carga" role="status">Cargando solicitudes…</p>}
      {error && <ErrorConsulta mensaje={error} onReintentar={recargar} />}
      {solicitudes?.length === 0 && <div className="info" role="status">No hay solicitudes en este estado.</div>}
      {listado.total > 0 && (
        <>
          <div className="tabla-scroll" role="region" aria-label="Solicitudes de predio" tabIndex={0}>
            <table className="tabla revision-predio__tabla">
              <thead><tr>
                <th scope="col">Solicitud</th><th scope="col">Trámite</th><th scope="col">Documento</th>
                <th scope="col">Predio</th><th scope="col">Estado</th>
              </tr></thead>
              <tbody>{listado.visibles.map((solicitud) => (
                <tr key={solicitud.idSolicitud}>
                  <td>
                    <Link className="revision-predio__enlace" to={`${RUTA}/${solicitud.idSolicitud}${consulta}`} aria-label={`Ver solicitud N.° ${solicitud.idSolicitud}`}>
                      N.° {solicitud.idSolicitud}
                    </Link>
                    <span className="revision-predio__secundario">{fechaHora(solicitud.fechaEnvio)}</span>
                  </td>
                  <td>{TRAMITES[solicitud.tipoTramite]}<span className="revision-predio__secundario">{TIPOS_PREDIO[solicitud.tipoPredio]}</span></td>
                  <td>{solicitud.documentoContribuyente}</td>
                  <td>{solicitud.codigoPredio || '—'}<span className="revision-predio__secundario">{solicitud.cantidadAdjuntos} {solicitud.cantidadAdjuntos === 1 ? 'adjunto' : 'adjuntos'}</span></td>
                  <td><Estado valor={solicitud.estado} /></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          {listado.total > TAMANIOS_PAGINA[0] && (
            <PaginacionTabla id="solicitudes-predio" totalRegistros={listado.total} pagina={listado.paginaActual}
              tamanio={tamanio} opciones={TAMANIOS_PAGINA} onCambiarPagina={setPagina}
              onCambiarTamanio={(valor) => { setTamanio(valor); setPagina(1) }} />
          )}
        </>
      )}
    </section>
  )
}

function DetalleSolicitud({ id, volver }) {
  const titulo = useRef(null)
  const [solicitud, setSolicitud] = useState(null)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [recarga, setRecarga] = useState(0)
  const cargando = !solicitud && !error

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  useEffect(() => {
    const peticion = new AbortController()
    obtenerSolicitudPredio(id, peticion.signal)
      .then((detalle) => { if (!peticion.signal.aborted) setSolicitud(detalle) })
      .catch((err) => { if (!peticion.signal.aborted) setError(err.message) })
    return () => peticion.abort()
  }, [id, recarga])

  function resuelta(detalle, mensaje) {
    setSolicitud(detalle)
    setAviso(mensaje)
    titulo.current?.focus()
  }

  return (
    <div className="revision-predio">
      <section className="pcard" aria-labelledby="titulo-revision-predio" aria-busy={cargando}>
        <div className="revision-predio__cabecera">
          <div>
            <h2 id="titulo-revision-predio" ref={titulo} tabIndex={-1}>Solicitud N.° {id}</h2>
            {solicitud && <Estado valor={solicitud.estado} />}
          </div>
          <Link className="btn" to={volver}>Volver a la bandeja</Link>
        </div>
        {cargando && <p className="hint mensaje-carga" role="status">Consultando solicitud…</p>}
        {error && <ErrorConsulta mensaje={error} onReintentar={() => { setError(''); setRecarga((valor) => valor + 1) }} />}
        {aviso && <p className="confirmacion" role="status">{aviso}</p>}
        {solicitud && <DatosSolicitud solicitud={solicitud} />}
      </section>

      {solicitud && (
        <div className={`revision-predio__revision${solicitud.estado === 'EN_REVISION' ? ' revision-predio__revision--pendiente' : ''}`}>
          <AdjuntosSolicitudPredio idSolicitud={solicitud.idSolicitud} adjuntos={solicitud.adjuntos} />
          {solicitud.estado === 'EN_REVISION' && <DecisionSolicitudPredio solicitud={solicitud} onResuelta={resuelta} />}
        </div>
      )}
    </div>
  )
}

function DatosSolicitud({ solicitud }) {
  const { contribuyente, predio, obligacionesPendientes, deudaPredio, revisor, fechaRevision, motivoRechazo } = solicitud
  const resuelta = solicitud.estado !== 'EN_REVISION'
  const datos = [
    ['Trámite', TRAMITES[solicitud.tipoTramite]],
    ['Tipo de predio', TIPOS_PREDIO[solicitud.tipoPredio]],
    ['Fecha de envío', fechaHora(solicitud.fechaEnvio)],
    ['Documento del contribuyente', solicitud.documentoContribuyente],
    ['Nombre o razón social', contribuyente?.nombre],
    ['Código de contribuyente', contribuyente?.codigoContribuyente],
    ['Código de predio', solicitud.codigoPredio],
    ['Deuda del predio', deudaPredio == null ? 'Sin información' : moneda(deudaPredio)],
  ]
  if (predio) datos.push(['Ubicación', predio.ubicacion], ['Zona', predio.zona])
  if (resuelta) datos.push(['Revisor', revisor], ['Fecha de revisión', fechaHora(fechaRevision)])

  return (
    <>
      <dl className="pfields revision-predio__datos">
        {datos.map(([etiqueta, valor]) => <div className="pf" key={etiqueta}><dt className="pl">{etiqueta}</dt><dd className="pv">{valor || '—'}</dd></div>)}
      </dl>
      {motivoRechazo && <div className="revision-predio__motivo"><h3>Motivo del rechazo</h3><p>{motivoRechazo}</p></div>}
      {obligacionesPendientes.length > 0 && (
        <details className="revision-predio__deuda">
          <summary>Ver obligaciones pendientes ({obligacionesPendientes.length})</summary>
          <div className="tabla-scroll" role="region" aria-label="Obligaciones pendientes" tabIndex={0}>
            <table className="tabla revision-predio__tabla">
              <thead><tr><th scope="col">Tributo</th><th scope="col">Periodo</th><th scope="col">Estado</th><th scope="col" className="num">Saldo</th></tr></thead>
              <tbody>{obligacionesPendientes.map((cuota) => (
                <tr key={cuota.id}><td>{cuota.tributo}</td><td>{cuota.periodo}</td><td>{cuota.estado}</td><td className="num">{moneda(cuota.deuda)}</td></tr>
              ))}</tbody>
            </table>
          </div>
        </details>
      )}
    </>
  )
}
