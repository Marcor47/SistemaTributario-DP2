import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { pedir } from '../services/api.js'
import { fechaHora } from '../utils/formato.js'

// HU047 (RF-061): el contribuyente consulta el estado de sus solicitudes de registro o traspaso de predio. Si fue
// rechazada ve la observación registrada; el resultado también se deposita en su buzón.
const ESTADOS = {
  EN_REVISION: ['En revisión', 'aviso', 'La Municipalidad está revisando tu solicitud y sus documentos.'],
  ACEPTADA: ['Aceptada', 'ok', 'Tu solicitud fue aceptada. La resolución está disponible en tu buzón.'],
  RECHAZADA: ['Rechazada', 'critico', 'Tu solicitud fue rechazada. Revisa la observación y, si corresponde, presenta una nueva.'],
}
const TRAMITES = { REGISTRO: 'Registro de predio', TRASPASO: 'Traspaso de predio' }
const PREDIOS = { URBANO: 'Urbano', RUSTICO: 'Rústico', OTRO: 'Otro' }

export default function TramitesPredio() {
  const [solicitudes, setSolicitudes] = useState(null)
  const [error, setError] = useState('')
  const [abierta, setAbierta] = useState(null)

  useEffect(() => {
    const controlador = new AbortController()
    pedir('/portal/solicitudes-predio', { signal: controlador.signal })
      .then((lista) => {
        if (controlador.signal.aborted) return
        setSolicitudes([...lista].sort((a, b) => String(b.fechaEnvio).localeCompare(String(a.fechaEnvio))))
      })
      .catch((err) => { if (!controlador.signal.aborted) setError(err.message || 'No se pudo consultar tus trámites.') })
    return () => controlador.abort()
  }, [])

  return (
    <section className="pcard" aria-labelledby="tramites-titulo">
      <h2 id="tramites-titulo">Mis trámites de predio</h2>
      <p className="hint">Estado de tus solicitudes de registro o traspaso de predio. Las solicitudes se presentan desde la aplicación móvil de Servicios en Línea.</p>
      {error && <div className="alert" role="alert">{error}</div>}
      {!solicitudes && !error && <p className="hint" role="status">Consultando tus trámites…</p>}
      {solicitudes?.length === 0 && <p className="hint">No tienes solicitudes de registro o traspaso de predio.</p>}
      {solicitudes?.length > 0 && (
        <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Mis trámites de predio">
          <table className="tabla">
            <caption className="sr-only">Solicitudes de registro o traspaso de predio con su estado</caption>
            <thead>
              <tr>
                <th scope="col">N.°</th><th scope="col">Trámite</th><th scope="col">Predio</th><th scope="col">Enviada</th>
                <th scope="col">Respondida</th><th scope="col">Estado</th><th scope="col"><span className="sr-only">Detalle</span></th>
              </tr>
            </thead>
            <tbody>
              {solicitudes.map((s) => {
                const [texto, clase, descripcion] = ESTADOS[s.estado] ?? [s.estado, 'neutro', '']
                const expandida = abierta === s.idSolicitud
                return (
                  <tr key={s.idSolicitud}>
                    <th scope="row">{s.idSolicitud}</th>
                    <td>{TRAMITES[s.tipoTramite] ?? s.tipoTramite}</td>
                    <td>{[s.codigoPredio, PREDIOS[s.tipoPredio]].filter(Boolean).join(' · ')}</td>
                    <td>{fechaHora(s.fechaEnvio)}</td>
                    <td>{s.fechaRevision ? fechaHora(s.fechaRevision) : '—'}</td>
                    <td>
                      <span className={`estado ${clase}`}>{texto}</span>
                      {expandida && (
                        <div className="hint">
                          <p>{descripcion}</p>
                          {s.estado === 'RECHAZADA' && <p><strong>Observación: </strong>{s.motivoRechazo || 'Sin observación registrada; revisa la respuesta en tu buzón.'}</p>}
                          {s.estado !== 'EN_REVISION' && <p><Link to="/portal/buzon">Ver la respuesta en mi buzón</Link></p>}
                        </div>
                      )}
                    </td>
                    <td>
                      <button className="btn" type="button" style={{ whiteSpace: 'nowrap' }} aria-expanded={expandida} onClick={() => setAbierta(expandida ? null : s.idSolicitud)}>
                        {expandida ? 'Ocultar' : 'Ver detalle'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
