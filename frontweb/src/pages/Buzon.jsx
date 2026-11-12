import { useCallback, useEffect, useRef, useState } from 'react'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import {
  abrirMensajeBuzon,
  cambiarArchivadoBuzon,
  cambiarDestacadoBuzon,
  descargarDocumentoBuzon,
  listarBuzon,
  marcarMensajeNoLeido,
} from '../services/buzonService.js'
import './Buzon.css'

const VISTAS = [
  { valor: 'RECIBIDOS', etiqueta: 'Recibidos' },
  { valor: 'DESTACADOS', etiqueta: 'Destacados' },
  { valor: 'ARCHIVADOS', etiqueta: 'Archivados' },
]

const TIPOS = {
  CONSTANCIA: 'Constancias',
  ESTADO_CUENTA: 'Estados de cuenta',
  RESPUESTA_SOLICITUD: 'Respuestas a solicitudes',
  ASIGNACION_RECLAMO: 'Asignaciones de reclamos',
  RESPUESTA_RECLAMO: 'Respuestas a reclamos',
}

const formatoFecha = (fecha) => new Date(fecha).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

export default function Buzon() {
  const [vista, setVista] = useState('RECIBIDOS')
  const [filtros, setFiltros] = useState({ busqueda: '', tipo: '', leido: '' })
  const [aplicados, setAplicados] = useState(filtros)
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)
  const [datos, setDatos] = useState({ total: 0, sinLeer: 0, documentos: [], totalPaginas: 0 })
  const [seleccionado, setSeleccionado] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [procesando, setProcesando] = useState(null)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const detalleRef = useRef(null)

  const cargar = useCallback(async (signal) => {
    setCargando(true)
    setError('')
    try {
      const respuesta = await listarBuzon({ vista, ...aplicados, pagina: pagina - 1, tamanio, signal })
      setDatos(respuesta)
      if (pagina > Math.max(1, respuesta.totalPaginas)) setPagina(Math.max(1, respuesta.totalPaginas))
    } catch (err) {
      if (err.name !== 'AbortError') setError(err.message || 'No se pudo cargar el buzón.')
    } finally {
      if (!signal?.aborted) setCargando(false)
    }
  }, [vista, aplicados, pagina, tamanio])

  useEffect(() => {
    const controlador = new AbortController()
    // La consulta se sincroniza con la vista, los filtros y la paginación.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    cargar(controlador.signal)
    return () => controlador.abort()
  }, [cargar])

  async function ejecutar(id, accion, mensaje) {
    setProcesando(id)
    setError('')
    setAviso('')
    try {
      const actualizado = await accion()
      if (seleccionado?.idDocumento === id) setSeleccionado(actualizado)
      setAviso(mensaje)
      await cargar()
    } catch (err) {
      setError(err.message || 'No se pudo actualizar el mensaje.')
    } finally {
      setProcesando(null)
    }
  }

  async function abrir(documento) {
    setProcesando(documento.idDocumento)
    setError('')
    try {
      const detalle = documento.leido ? documento : await abrirMensajeBuzon(documento.idDocumento)
      setSeleccionado(detalle)
      setDatos((actual) => ({
        ...actual,
        sinLeer: documento.leido ? actual.sinLeer : Math.max(0, actual.sinLeer - 1),
        documentos: actual.documentos.map((item) => item.idDocumento === detalle.idDocumento ? detalle : item),
      }))
      requestAnimationFrame(() => detalleRef.current?.focus())
    } catch (err) {
      setError(err.message || 'No se pudo abrir el mensaje.')
    } finally {
      setProcesando(null)
    }
  }

  function cambiarVista(siguiente) {
    setVista(siguiente)
    setPagina(1)
    setSeleccionado(null)
    setAviso('')
  }

  function buscar(evento) {
    evento.preventDefault()
    setAplicados(filtros)
    setPagina(1)
  }

  async function descargar(documento) {
    setProcesando(documento.idDocumento)
    try {
      const blob = await descargarDocumentoBuzon(documento.idDocumento)
      const url = URL.createObjectURL(blob)
      const enlace = document.createElement('a')
      enlace.href = url
      enlace.download = documento.archivo?.split('/').pop() || `documento-${documento.idDocumento}.pdf`
      enlace.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err.message || 'No se pudo descargar el documento.')
    } finally {
      setProcesando(null)
    }
  }

  return <div className="buzon-pagina">
    <nav className="buzon-vistas" aria-label="Carpetas del buzón">
      {VISTAS.map((opcion) => <button key={opcion.valor} type="button" aria-pressed={vista === opcion.valor} onClick={() => cambiarVista(opcion.valor)}>{opcion.etiqueta}{opcion.valor === 'RECIBIDOS' && datos.sinLeer > 0 && <span>{datos.sinLeer}</span>}</button>)}
    </nav>

    <section className="pcard buzon-filtros" aria-labelledby="titulo-filtros-buzon">
      <h2 id="titulo-filtros-buzon" className="sr-only">Buscar y filtrar mensajes</h2>
      <form onSubmit={buscar}>
        <div className="fld buzon-busqueda"><label htmlFor="buzon-busqueda">Buscar</label><input id="buzon-busqueda" type="search" value={filtros.busqueda} onChange={(evento) => setFiltros((actual) => ({ ...actual, busqueda: evento.target.value }))} placeholder="Título o contenido" /></div>
        <div className="fld"><label htmlFor="buzon-tipo">Tipo</label><select id="buzon-tipo" value={filtros.tipo} onChange={(evento) => setFiltros((actual) => ({ ...actual, tipo: evento.target.value }))}><option value="">Todos</option>{Object.entries(TIPOS).map(([valor, etiqueta]) => <option key={valor} value={valor}>{etiqueta}</option>)}</select></div>
        <div className="fld"><label htmlFor="buzon-lectura">Lectura</label><select id="buzon-lectura" value={filtros.leido} onChange={(evento) => setFiltros((actual) => ({ ...actual, leido: evento.target.value }))}><option value="">Todos</option><option value="false">No leídos</option><option value="true">Leídos</option></select></div>
        <button className="btn primary" type="submit">Buscar</button>
        <button className="btn" type="button" onClick={() => { const vacios = { busqueda: '', tipo: '', leido: '' }; setFiltros(vacios); setAplicados(vacios); setPagina(1) }}>Limpiar</button>
      </form>
    </section>

    {error && <div className="alert" role="alert">{error}<button type="button" onClick={() => cargar()}>Reintentar</button></div>}
    {aviso && <div className="info" role="status" aria-live="polite">{aviso}</div>}

    <div className={`buzon-contenido${seleccionado ? ' buzon-contenido--detalle' : ''}`}>
      <section className="pcard buzon-bandeja" aria-labelledby="titulo-bandeja">
        <div className="buzon-bandeja__cabecera"><div><h2 id="titulo-bandeja">{VISTAS.find((opcion) => opcion.valor === vista)?.etiqueta}</h2><p>{cargando ? 'Consultando mensajes…' : `${datos.total} ${datos.total === 1 ? 'mensaje' : 'mensajes'}`}</p></div></div>
        {!cargando && datos.documentos.length === 0 && <div className="buzon-vacio"><strong>No hay mensajes en esta vista.</strong><p>Prueba con otros filtros o revisa otra carpeta.</p></div>}
        <ul className="buzon-items" aria-busy={cargando}>
          {datos.documentos.map((documento) => <li key={documento.idDocumento} className={!documento.leido ? 'buzon-item buzon-item--nuevo' : 'buzon-item'}>
            <button className="buzon-item__principal" type="button" onClick={() => abrir(documento)} disabled={procesando === documento.idDocumento}>
              <span className="buzon-item__titulo">{documento.titulo}</span><span className="buzon-item__meta">{TIPOS[documento.tipo] || documento.tipo} · {formatoFecha(documento.fechaEnvio)}</span>
            </button>
            <div className="buzon-item__acciones">
              <button type="button" aria-label={documento.destacado ? `Quitar ${documento.titulo} de destacados` : `Destacar ${documento.titulo}`} aria-pressed={documento.destacado} onClick={() => ejecutar(documento.idDocumento, () => cambiarDestacadoBuzon(documento.idDocumento, !documento.destacado), documento.destacado ? 'Mensaje quitado de destacados.' : 'Mensaje destacado.')} disabled={procesando === documento.idDocumento}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z" /></svg></button>
              <button type="button" onClick={() => ejecutar(documento.idDocumento, () => cambiarArchivadoBuzon(documento.idDocumento, !documento.archivado), documento.archivado ? 'Mensaje restaurado.' : 'Mensaje archivado.')} disabled={procesando === documento.idDocumento}>{documento.archivado ? 'Restaurar' : 'Archivar'}</button>
            </div>
          </li>)}
        </ul>
        {!cargando && datos.total > 0 && <PaginacionTabla id="buzon" totalRegistros={datos.total} pagina={pagina} tamanio={tamanio} onCambiarPagina={setPagina} onCambiarTamanio={(valor) => { setTamanio(valor); setPagina(1) }} />}
      </section>

      {seleccionado && <section className="pcard buzon-detalle" ref={detalleRef} tabIndex={-1} aria-labelledby="titulo-detalle-buzon">
        <div className="buzon-detalle__cabecera"><div><span>{TIPOS[seleccionado.tipo] || seleccionado.tipo}</span><h2 id="titulo-detalle-buzon">{seleccionado.titulo}</h2><time dateTime={seleccionado.fechaEnvio}>{formatoFecha(seleccionado.fechaEnvio)}</time></div><button className="btn" type="button" onClick={() => setSeleccionado(null)}>Cerrar</button></div>
        <div className="buzon-detalle__contenido">{seleccionado.contenido ? seleccionado.contenido.split('\n').map((linea, indice) => <p key={`${indice}-${linea}`}>{linea}</p>) : <p>Este mensaje contiene un documento municipal disponible para consulta.</p>}</div>
        <dl className="buzon-detalle__fechas"><div><dt>Enviado</dt><dd>{formatoFecha(seleccionado.fechaEnvio)}</dd></div><div><dt>Primera lectura</dt><dd>{seleccionado.fechaPrimeraLectura ? formatoFecha(seleccionado.fechaPrimeraLectura) : 'Sin abrir'}</dd></div></dl>
        <div className="buzon-detalle__acciones">
          <button className="btn" type="button" onClick={() => ejecutar(seleccionado.idDocumento, () => seleccionado.leido ? marcarMensajeNoLeido(seleccionado.idDocumento) : abrirMensajeBuzon(seleccionado.idDocumento), seleccionado.leido ? 'Mensaje marcado como no leído.' : 'Mensaje marcado como leído.')} disabled={procesando === seleccionado.idDocumento}>{seleccionado.leido ? 'Marcar como no leído' : 'Marcar como leído'}</button>
          <button className="btn" type="button" onClick={() => ejecutar(seleccionado.idDocumento, () => cambiarDestacadoBuzon(seleccionado.idDocumento, !seleccionado.destacado), seleccionado.destacado ? 'Mensaje quitado de destacados.' : 'Mensaje destacado.')} disabled={procesando === seleccionado.idDocumento}>{seleccionado.destacado ? 'Quitar destacado' : 'Destacar'}</button>
          <button className="btn" type="button" onClick={() => ejecutar(seleccionado.idDocumento, () => cambiarArchivadoBuzon(seleccionado.idDocumento, !seleccionado.archivado), seleccionado.archivado ? 'Mensaje restaurado.' : 'Mensaje archivado.')} disabled={procesando === seleccionado.idDocumento}>{seleccionado.archivado ? 'Restaurar' : 'Archivar'}</button>
          {seleccionado.urlDescarga && <button className="btn primary" type="button" onClick={() => descargar(seleccionado)} disabled={procesando === seleccionado.idDocumento}>Descargar documento</button>}
        </div>
      </section>}
    </div>
  </div>
}
