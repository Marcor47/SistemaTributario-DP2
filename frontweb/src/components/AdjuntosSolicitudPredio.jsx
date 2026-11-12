import { useEffect, useState } from 'react'
import { obtenerAdjuntoPredio } from '../services/revisionPredioService.js'

export default function AdjuntosSolicitudPredio({ idSolicitud, adjuntos }) {
  const [idSeleccionado, setIdSeleccionado] = useState(null)
  const seleccionado = adjuntos.find((adjunto) => adjunto.id === idSeleccionado)

  return (
    <section className="pcard" aria-labelledby="titulo-adjuntos-predio">
      <h2 id="titulo-adjuntos-predio">Documentos adjuntos</h2>
      {adjuntos.length === 0 && <p className="hint">La solicitud no tiene documentos adjuntos.</p>}
      {adjuntos.length > 0 && (
        <ul className="revision-predio__archivos">
          {adjuntos.map((adjunto) => (
            <li key={adjunto.id}>
              <div><strong>{adjunto.requisito}</strong><span className="revision-predio__secundario">{adjunto.nombre}</span></div>
              <button type="button" className="btn btn-compacto" aria-expanded={idSeleccionado === adjunto.id}
                aria-label={`${idSeleccionado === adjunto.id ? 'Cerrar' : 'Ver'} ${adjunto.nombre}`}
                onClick={() => setIdSeleccionado(idSeleccionado === adjunto.id ? null : adjunto.id)}>
                {idSeleccionado === adjunto.id ? 'Cerrar' : 'Ver'}
              </button>
            </li>
          ))}
        </ul>
      )}
      {seleccionado && <VisorAdjunto key={seleccionado.id} idSolicitud={idSolicitud} adjunto={seleccionado} />}
    </section>
  )
}

function VisorAdjunto({ idSolicitud, adjunto }) {
  const [vista, setVista] = useState(null)
  const [error, setError] = useState('')
  const [recarga, setRecarga] = useState(0)
  const idAdjunto = adjunto.id

  useEffect(() => {
    const peticion = new AbortController()
    let url
    obtenerAdjuntoPredio(idSolicitud, idAdjunto, peticion.signal)
      .then((archivo) => {
        if (peticion.signal.aborted) return
        url = URL.createObjectURL(archivo)
        setVista({ url, esPdf: archivo.type === 'application/pdf' })
      })
      .catch((err) => { if (!peticion.signal.aborted) setError(err.message) })
    return () => {
      peticion.abort()
      if (url) URL.revokeObjectURL(url)
    }
  }, [idSolicitud, idAdjunto, recarga])

  return (
    <div className="revision-predio__visor" aria-label={`Vista previa de ${adjunto.nombre}`} aria-busy={!vista && !error}>
      {!vista && !error && <p className="hint" role="status">Abriendo adjunto…</p>}
      {error && (
        <div className="alert" role="alert">
          <p>{error}</p>
          <button type="button" className="btn" onClick={() => { setError(''); setRecarga((valor) => valor + 1) }}>Reintentar adjunto</button>
        </div>
      )}
      {vista && (
        <>
          {vista.esPdf ? <iframe src={vista.url} title={adjunto.nombre} /> : <img src={vista.url} alt={adjunto.nombre} />}
          <a className="btn btn-compacto" href={vista.url} download={adjunto.nombre}>Descargar adjunto</a>
        </>
      )}
    </div>
  )
}
