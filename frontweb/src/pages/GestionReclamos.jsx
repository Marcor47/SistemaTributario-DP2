import { useEffect, useRef, useState } from 'react'
import {
  cargarCatalogosReclamos,
  derivarReclamo,
  listarReclamosAsignados,
  listarReclamosGestion,
  responderReclamo,
} from '../services/reclamoService.js'
import { ESTADOS_RECLAMO, estadoReclamo, etiquetaTipo } from '../utils/reclamos.js'
import { etiquetaRol } from '../utils/roles.js'
import './Reclamos.css'

const MAXIMO_OBSERVACION = 400
const MAXIMO_RESPUESTA = 4000
const formatoFechaHora = (iso) => iso ? new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' }) : '—'

export default function GestionReclamos({ modo }) {
  const esGestion = modo === 'gestion'
  const [reclamos, setReclamos] = useState([])
  const [catalogos, setCatalogos] = useState({ areas: [], responsables: [] })
  const [estado, setEstado] = useState(esGestion ? 'ENVIADO' : 'EN_REVISION')
  const [seleccionadoId, setSeleccionadoId] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')

  useEffect(() => {
    let activo = true
    const carga = esGestion
      ? Promise.all([listarReclamosGestion(), cargarCatalogosReclamos()])
      : Promise.all([listarReclamosAsignados(), Promise.resolve({ areas: [], responsables: [] })])
    carga.then(([lista, datosCatalogos]) => {
      if (!activo) return
      setReclamos(lista)
      setCatalogos(datosCatalogos)
    }).catch((err) => { if (activo) setError(err.message || 'No se pudo cargar la bandeja de reclamos.') })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
  }, [esGestion])

  const visibles = estado ? reclamos.filter((reclamo) => reclamo.estado === estado) : reclamos
  const seleccionado = reclamos.find((reclamo) => reclamo.idReclamo === seleccionadoId) ?? null

  function actualizado(reclamo, mensaje) {
    setReclamos((actuales) => actuales.map((actual) => actual.idReclamo === reclamo.idReclamo ? reclamo : actual))
    setSeleccionadoId(reclamo.idReclamo)
    setAviso(mensaje)
  }

  return (
    <div aria-busy={cargando}>
      {error && <div className="alert" role="alert">{error}</div>}
      {aviso && <div className="info" role="status" aria-live="polite">{aviso}</div>}

      <section className="pcard" aria-labelledby="titulo-bandeja-reclamos">
        <div className="catalogo-cabecera">
          <div>
            <h2 id="titulo-bandeja-reclamos">{esGestion ? 'Bandeja general' : 'Reclamos asignados'}</h2>
            <p className="hint" role="status">
              {cargando ? 'Consultando reclamos…' : `${visibles.length} ${visibles.length === 1 ? 'reclamo' : 'reclamos'} para mostrar.`}
            </p>
          </div>
          <div className="fld filtro-estado">
            <label htmlFor="gestion-reclamo-estado">Filtrar por estado</label>
            <select id="gestion-reclamo-estado" value={estado} onChange={(evento) => setEstado(evento.target.value)} disabled={cargando}>
              <option value="">Todos</option>
              {Object.entries(ESTADOS_RECLAMO).map(([valor, datos]) => <option key={valor} value={valor}>{datos.etiqueta}</option>)}
            </select>
          </div>
        </div>

        {!cargando && !error && visibles.length === 0 && (
          <div className="info">{esGestion ? 'No hay reclamos pendientes en este estado.' : 'No tienes reclamos asignados en este estado.'}</div>
        )}
        {!cargando && visibles.length > 0 && (
          <div className="tabla-scroll" role="region" aria-label={esGestion ? 'Bandeja general de reclamos' : 'Reclamos que tengo asignados'} tabIndex={0}>
            <table className="tabla tabla-gestion-reclamos">
              <caption className="sr-only">Reclamos con reclamante, fecha, asignación y estado</caption>
              <thead><tr><th scope="col">Reclamo</th><th scope="col">Reclamante</th><th scope="col">Asignación</th><th scope="col">Estado</th><th scope="col">Acción</th></tr></thead>
              <tbody>{visibles.map((reclamo) => (
                <tr key={reclamo.idReclamo}>
                  <td><strong>{reclamo.numeroReclamo}</strong><span className="reclamo-detalle">{etiquetaTipo(reclamo.tipoReclamo)} · {formatoFechaHora(reclamo.fechaRegistro)}</span></td>
                  <td>{reclamo.reclamante}<span className="reclamo-detalle">{reclamo.tipoReclamante === 'CONTRIBUYENTE' ? 'Contribuyente' : 'Trabajador municipal'}</span></td>
                  <td>{reclamo.areaActual || 'Sin derivar'}<span className="reclamo-detalle">{reclamo.responsableActual || 'Sin responsable'}</span></td>
                  <td><span className={`estado ${estadoReclamo(reclamo.estado).clase}`}>{estadoReclamo(reclamo.estado).etiqueta}</span></td>
                  <td><button className="btn btn-compacto" type="button" aria-expanded={seleccionadoId === reclamo.idReclamo} onClick={() => { setSeleccionadoId(reclamo.idReclamo); setAviso('') }}>{esGestion && reclamo.estado !== 'CANCELADO' && reclamo.estado !== 'APROBADO_CON_RESPUESTA' ? 'Revisar y derivar' : esGestion ? 'Ver detalle' : reclamo.estado === 'EN_REVISION' ? 'Atender' : 'Ver detalle'}</button></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </section>

      {seleccionado && (
        <DetalleGestion
          key={seleccionado.idReclamo}
          reclamo={seleccionado}
          esGestion={esGestion}
          catalogos={catalogos}
          onCerrar={() => setSeleccionadoId(null)}
          onActualizado={actualizado}
        />
      )}
    </div>
  )
}

function DetalleGestion({ reclamo, esGestion, catalogos, onCerrar, onActualizado }) {
  const panel = useRef(null)
  const [idArea, setIdArea] = useState(reclamo.idAreaActual ? String(reclamo.idAreaActual) : '')
  const [idResponsable, setIdResponsable] = useState(reclamo.idResponsableActual ? String(reclamo.idResponsableActual) : '')
  const [observacion, setObservacion] = useState('')
  const [respuesta, setRespuesta] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const cerrado = reclamo.estado === 'CANCELADO' || reclamo.estado === 'APROBADO_CON_RESPUESTA'

  useEffect(() => { panel.current?.focus() }, [reclamo.idReclamo])

  async function derivar(evento) {
    evento.preventDefault()
    const nuevos = {}
    if (!idArea) nuevos.area = 'Selecciona el área que atenderá el reclamo.'
    if (!idResponsable) nuevos.responsable = 'Selecciona al responsable de la revisión.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length) return
    setGuardando(true)
    setError('')
    try {
      const actualizado = await derivarReclamo(reclamo.idReclamo, { idArea: Number(idArea), idResponsable: Number(idResponsable), observacion: observacion.trim() || null })
      onActualizado(actualizado, `El reclamo ${actualizado.numeroReclamo} fue derivado a ${actualizado.areaActual}.`)
      setObservacion('')
    } catch (err) { setError(err.message || 'No se pudo derivar el reclamo.') } finally { setGuardando(false) }
  }

  async function responder(evento) {
    evento.preventDefault()
    if (!respuesta.trim()) return setErrores({ respuesta: 'Escribe la respuesta que recibirá el reclamante.' })
    setGuardando(true)
    setError('')
    try {
      const actualizado = await responderReclamo(reclamo.idReclamo, respuesta.trim())
      onActualizado(actualizado, `La respuesta del reclamo ${actualizado.numeroReclamo} fue registrada.`)
      setRespuesta('')
    } catch (err) { setError(err.message || 'No se pudo responder el reclamo.') } finally { setGuardando(false) }
  }

  return (
    <section className="pcard detalle-gestion-reclamo" aria-labelledby="titulo-gestion-reclamo" ref={panel} tabIndex={-1}>
      <div className="catalogo-cabecera">
        <div><h2 id="titulo-gestion-reclamo">{reclamo.numeroReclamo}</h2><p className="hint">{etiquetaTipo(reclamo.tipoReclamo)} · {reclamo.reclamante} · <span className={`estado ${estadoReclamo(reclamo.estado).clase}`}>{estadoReclamo(reclamo.estado).etiqueta}</span></p></div>
        <button className="btn" type="button" onClick={onCerrar}>Cerrar detalle</button>
      </div>
      {error && <div className="alert" role="alert">{error}</div>}
      <dl className="datos-reclamo">
        <div><dt>Fecha del hecho</dt><dd>{formatoFechaHora(reclamo.fechaHoraIncidente)}</dd></div>
        <div><dt>Referencia</dt><dd>{reclamo.referencia || 'No indicada'}</dd></div>
        <div className="campo-completo"><dt>Comentario</dt><dd>{reclamo.comentario}</dd></div>
      </dl>

      {reclamo.derivaciones.length > 0 && <HistorialDerivaciones derivaciones={reclamo.derivaciones} />}
      {reclamo.respuesta && <div className="info"><strong>Respuesta registrada</strong><p>{reclamo.respuesta}</p><span className="hint">{formatoFechaHora(reclamo.fechaRespuesta)}</span></div>}

      {esGestion && !cerrado && (
        <form className="formulario-gestion-reclamo" onSubmit={derivar} noValidate>
          <h3>{reclamo.estado === 'ENVIADO' ? 'Derivar reclamo' : 'Cambiar asignación'}</h3>
          <div className={`fld${errores.area ? ' fld-error' : ''}`}><label htmlFor="reclamo-area">Área responsable</label><select id="reclamo-area" value={idArea} onChange={(evento) => { setIdArea(evento.target.value); setErrores((actuales) => ({ ...actuales, area: undefined })) }} aria-invalid={Boolean(errores.area)}><option value="">Selecciona un área</option>{catalogos.areas.map((area) => <option key={area.idArea} value={area.idArea}>{area.nombre}</option>)}</select>{errores.area && <span className="error-campo">{errores.area}</span>}</div>
          <div className={`fld${errores.responsable ? ' fld-error' : ''}`}><label htmlFor="reclamo-responsable">Responsable de revisión</label><select id="reclamo-responsable" value={idResponsable} onChange={(evento) => { setIdResponsable(evento.target.value); setErrores((actuales) => ({ ...actuales, responsable: undefined })) }} aria-invalid={Boolean(errores.responsable)}><option value="">Selecciona un responsable</option>{catalogos.responsables.map((usuario) => <option key={usuario.idUsuario} value={usuario.idUsuario}>{usuario.nombreCompleto} · {etiquetaRol(usuario.rol)}</option>)}</select>{errores.responsable && <span className="error-campo">{errores.responsable}</span>}</div>
          <div className="fld campo-completo"><label htmlFor="reclamo-observacion">Observación para el responsable (opcional)</label><textarea id="reclamo-observacion" rows={3} maxLength={MAXIMO_OBSERVACION} value={observacion} onChange={(evento) => setObservacion(evento.target.value)} /><span className="hint">{observacion.length}/{MAXIMO_OBSERVACION} caracteres</span></div>
          <button className="btn primary" type="submit" disabled={guardando}>{guardando ? 'Derivando…' : reclamo.estado === 'ENVIADO' ? 'Derivar reclamo' : 'Actualizar asignación'}</button>
        </form>
      )}

      {!esGestion && reclamo.estado === 'EN_REVISION' && (
        <form className="formulario-gestion-reclamo" onSubmit={responder} noValidate>
          <h3>Registrar respuesta</h3>
          <div className={`fld campo-completo${errores.respuesta ? ' fld-error' : ''}`}><label htmlFor="reclamo-respuesta">Respuesta para el reclamante</label><textarea id="reclamo-respuesta" rows={6} maxLength={MAXIMO_RESPUESTA} value={respuesta} onChange={(evento) => { setRespuesta(evento.target.value); setErrores({}) }} aria-invalid={Boolean(errores.respuesta)} />{errores.respuesta ? <span className="error-campo">{errores.respuesta}</span> : <span className="hint">{respuesta.length}/{MAXIMO_RESPUESTA} caracteres. La respuesta cerrará el reclamo.</span>}</div>
          <button className="btn primary" type="submit" disabled={guardando}>{guardando ? 'Registrando…' : 'Registrar respuesta'}</button>
        </form>
      )}
    </section>
  )
}

function HistorialDerivaciones({ derivaciones }) {
  return <div className="historial-gestion-reclamo"><h3>Historial de derivaciones</h3><ol className="derivaciones">{derivaciones.map((derivacion, indice) => <li key={`${derivacion.fechaDerivacion}-${indice}`}><strong>{derivacion.areaDestino}</strong><span className="reclamo-detalle">{formatoFechaHora(derivacion.fechaDerivacion)} · Responsable: {derivacion.responsable} · Derivó: {derivacion.derivadoPor}</span>{derivacion.observacion && <span className="reclamo-detalle">{derivacion.observacion}</span>}</li>)}</ol></div>
}
