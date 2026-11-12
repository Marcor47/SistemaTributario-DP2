import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { abrirMensajeBuzon, cambiarDestacadoBuzon, listarBuzon, marcarMensajeNoLeido } from '../services/buzonService.js'
import './BuzonNotificaciones.css'

const formatoFecha = (fecha) => new Date(fecha).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })

export default function BuzonNotificaciones() {
  const [buzon, setBuzon] = useState({ total: 0, sinLeer: 0, documentos: [] })
  const [abierto, setAbierto] = useState(false)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const contenedor = useRef(null)
  const boton = useRef(null)

  useEffect(() => {
    let activo = true
    listarBuzon({ tamanio: 5 }).then((datos) => { if (activo) setBuzon(datos) })
      .catch((err) => { if (activo) setError(err.message || 'No se pudo cargar el buzón.') })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
  }, [])

  useEffect(() => {
    if (!abierto) return undefined
    const cerrar = (evento) => {
      if (evento.key === 'Escape' || (evento.type === 'mousedown' && !contenedor.current?.contains(evento.target))) {
        setAbierto(false)
        if (evento.key === 'Escape') requestAnimationFrame(() => boton.current?.focus())
      }
    }
    document.addEventListener('keydown', cerrar)
    document.addEventListener('mousedown', cerrar)
    return () => {
      document.removeEventListener('keydown', cerrar)
      document.removeEventListener('mousedown', cerrar)
    }
  }, [abierto])

  function actualizar(documento) {
    setBuzon((actual) => {
      const anterior = actual.documentos.find((item) => item.idDocumento === documento.idDocumento)
      const documentos = actual.documentos.map((item) => item.idDocumento === documento.idDocumento ? documento : item)
      const cambioLectura = anterior && anterior.leido !== documento.leido ? (documento.leido ? -1 : 1) : 0
      return { ...actual, documentos, sinLeer: Math.max(0, actual.sinLeer + cambioLectura) }
    })
  }

  async function cambiarLectura(documento) {
    setError('')
    try {
      actualizar(documento.leido
        ? await marcarMensajeNoLeido(documento.idDocumento)
        : await abrirMensajeBuzon(documento.idDocumento))
    } catch (err) {
      setError(err.message || 'No se pudo actualizar el mensaje.')
    }
  }

  async function cambiarDestacado(documento) {
    setError('')
    try {
      actualizar(await cambiarDestacadoBuzon(documento.idDocumento, !documento.destacado))
    } catch (err) {
      setError(err.message || 'No se pudo actualizar el mensaje.')
    }
  }

  return (
    <div className="buzon" ref={contenedor}>
      <button
        ref={boton}
        className="buzon__boton"
        type="button"
        aria-label={`Buzón de notificaciones${buzon.sinLeer ? `, ${buzon.sinLeer} sin leer` : ''}`}
        aria-expanded={abierto}
        aria-controls="panel-buzon"
        onClick={() => setAbierto((valor) => !valor)}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
        {buzon.sinLeer > 0 && <span className="buzon__contador" aria-hidden="true">{buzon.sinLeer > 99 ? '99+' : buzon.sinLeer}</span>}
      </button>
      <span className="sr-only" role="status" aria-atomic="true">{buzon.sinLeer} notificaciones sin leer</span>

      {abierto && (
        <section id="panel-buzon" className="buzon__panel" aria-labelledby="titulo-buzon">
          <div className="buzon__cabecera">
            <div><h2 id="titulo-buzon">Buzón</h2><p>{buzon.sinLeer} sin leer de {buzon.total}</p></div>
            <button className="buzon__cerrar" type="button" aria-label="Cerrar buzón" onClick={() => { setAbierto(false); boton.current?.focus() }}>×</button>
          </div>
          {error && <p className="buzon__error" role="alert">{error}</p>}
          {cargando ? <p className="buzon__estado" role="status">Cargando mensajes…</p>
            : buzon.documentos.length === 0 ? <p className="buzon__estado">Todavía no tienes mensajes.</p>
              : <ul className="buzon__lista">{buzon.documentos.map((documento) => (
                <li key={documento.idDocumento} className={documento.leido ? 'buzon__mensaje' : 'buzon__mensaje buzon__mensaje--nuevo'}>
                  <div className="buzon__mensaje-cab"><strong>{documento.titulo}</strong>{!documento.leido && <span>Nuevo</span>}</div>
                  {documento.contenido && <p>{documento.contenido}</p>}
                  <time dateTime={documento.fechaEnvio}>{formatoFecha(documento.fechaEnvio)}</time>
                  <div className="buzon__acciones">
                    <button type="button" onClick={() => cambiarLectura(documento)}>{documento.leido ? 'Marcar como no leído' : 'Marcar como leído'}</button>
                    <button type="button" aria-pressed={documento.destacado} onClick={() => cambiarDestacado(documento)}>{documento.destacado ? 'Quitar destacado' : 'Destacar'}</button>
                  </div>
                </li>
              ))}</ul>}
          <Link className="buzon__ver-todo" to="/portal/buzon" onClick={() => setAbierto(false)}>Ver todo el buzón</Link>
        </section>
      )}
    </div>
  )
}
