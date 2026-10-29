import { useEffect, useRef, useState } from 'react'
import { listarMisReclamos, ordenarReclamos, registrarReclamo } from '../services/reclamoService.js'
import {
  ESTADOS_RECLAMO,
  MAXIMO_COMENTARIO,
  MAXIMO_REFERENCIA,
  aFechaHoraLocal,
  estadoReclamo,
  etiquetaTipo,
  tiposPara,
} from '../utils/reclamos.js'
import './Reclamos.css'

// En la plataforma web reclama un trabajador interno; el contribuyente lo hace desde la app móvil (RF-064).
const RECLAMANTE = 'TRABAJADOR'
const FORMULARIO_VACIO = { tipoReclamo: '', fechaHoraIncidente: '', referencia: '', comentario: '' }

const formatoFechaHora = (iso) =>
  iso ? new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' }) : '—'

const CAMPOS = [
  ['tipoReclamo', 'reclamo-tipo'],
  ['fechaHoraIncidente', 'reclamo-fecha'],
  ['referencia', 'reclamo-referencia'],
  ['comentario', 'reclamo-comentario'],
]

export default function Reclamos() {
  const [reclamos, setReclamos] = useState([])
  const [demo, setDemo] = useState(false)
  const [ocupado, setOcupado] = useState(true)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [datos, setDatos] = useState(FORMULARIO_VACIO)
  const [errores, setErrores] = useState({})
  const [intentos, setIntentos] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const [errorEnvio, setErrorEnvio] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('')
  const [detalleId, setDetalleId] = useState(null)
  const resumen = useRef(null)
  const botonDetalle = useRef(null)
  const tipos = tiposPara(RECLAMANTE)

  useEffect(() => {
    let activo = true
    listarMisReclamos()
      .then((resultado) => {
        if (!activo) return
        setReclamos(ordenarReclamos(resultado.reclamos))
        setDemo(resultado.demo)
      })
      .catch((err) => {
        if (activo) setError(err.message || 'No se pudo cargar tus reclamos. Recarga la página para intentarlo nuevamente.')
      })
      .finally(() => { if (activo) setOcupado(false) })
    return () => { activo = false }
  }, [])

  // Tras un envío fallido, el foco va al resumen de errores para que se anuncie y se pueda llegar a cada campo.
  useEffect(() => {
    if (intentos > 0) resumen.current?.focus()
  }, [intentos])

  const mensajesError = CAMPOS.filter(([campo]) => errores[campo])
  const visibles = filtroEstado ? reclamos.filter((r) => r.estado === filtroEstado) : reclamos
  const detalle = reclamos.find((r) => r.idReclamo === detalleId) ?? null
  const bloqueado = ocupado || enviando

  function cambiar(campo) {
    return (evento) => {
      setDatos((actual) => ({ ...actual, [campo]: evento.target.value }))
      if (errores[campo]) setErrores((actual) => ({ ...actual, [campo]: undefined }))
    }
  }

  async function enviar(evento) {
    evento.preventDefault()
    setAviso('')
    setErrorEnvio('')
    setEnviando(true)
    try {
      const creado = await registrarReclamo(datos, demo, RECLAMANTE)
      setReclamos((actuales) => ordenarReclamos([creado, ...actuales.filter((r) => r.idReclamo !== creado.idReclamo)]))
      setDatos(FORMULARIO_VACIO)
      setErrores({})
      setIntentos(0)
      setAviso(`Tu reclamo se registró con el número ${creado.numeroReclamo}. Puedes seguir su estado en «Mis reclamos».`)
    } catch (err) {
      if (err.errores) {
        setErrores(err.errores)
        setIntentos((n) => n + 1)
      } else {
        setErrorEnvio(err.message || 'No se pudo registrar el reclamo. Intenta nuevamente.')
      }
    } finally {
      setEnviando(false)
    }
  }

  function abrirDetalle(reclamo, evento) {
    botonDetalle.current = evento.currentTarget
    setDetalleId(reclamo.idReclamo)
  }

  function cerrarDetalle() {
    setDetalleId(null)
    requestAnimationFrame(() => botonDetalle.current?.focus())
  }

  return (
    <div aria-busy={ocupado || enviando}>
      <section className="pcard">
        <h2>Reclamos</h2>
        <p className="hint">
          Registra un reclamo por un incidente laboral y consulta su estado. Un gestor de reclamos lo recibe y lo deriva
          al área correspondiente hasta que se atienda.
        </p>
      </section>

      {demo && (
        <div className="alert" role="note">
          <strong>Datos ficticios de demostración.</strong> El servicio de reclamos no está disponible: los reclamos que
          registres se guardan solo en esta pantalla y se pierden al recargar.
        </div>
      )}
      {error && <div className="alert" role="alert">{error}</div>}
      {aviso && <div className="info" role="status">{aviso}</div>}

      <section className="pcard" aria-labelledby="titulo-nuevo-reclamo">
        <h2 id="titulo-nuevo-reclamo">Registrar un reclamo</h2>
        <p className="hint">Los campos con * son obligatorios.</p>

        {mensajesError.length > 0 && (
          <div className="alert resumen-errores" role="alert" tabIndex={-1} ref={resumen} aria-labelledby="titulo-errores-reclamo">
            <h3 id="titulo-errores-reclamo">Hay {mensajesError.length === 1 ? 'un dato' : `${mensajesError.length} datos`} por corregir</h3>
            <ul>
              {mensajesError.map(([campo, id]) => (
                <li key={campo}><a href={`#${id}`} onClick={(e) => { e.preventDefault(); document.getElementById(id)?.focus() }}>{errores[campo]}</a></li>
              ))}
            </ul>
          </div>
        )}
        {errorEnvio && <div className="alert" role="alert">{errorEnvio}</div>}

        <form className="formulario-reclamo" onSubmit={enviar} noValidate>
          <fieldset className="pfields" disabled={bloqueado}>
            <legend className="sr-only">Datos del reclamo</legend>
            <div className={`fld${errores.tipoReclamo ? ' fld-error' : ''}`}>
              <label htmlFor="reclamo-tipo">Tipo de reclamo *</label>
              <select
                id="reclamo-tipo"
                value={datos.tipoReclamo}
                onChange={cambiar('tipoReclamo')}
                aria-invalid={errores.tipoReclamo ? 'true' : undefined}
                aria-describedby={errores.tipoReclamo ? 'reclamo-tipo-error' : undefined}
              >
                <option value="">Selecciona un tipo</option>
                {tipos.map((t) => <option key={t.valor} value={t.valor}>{t.etiqueta}</option>)}
              </select>
              {errores.tipoReclamo && <span id="reclamo-tipo-error" className="error-campo">{errores.tipoReclamo}</span>}
            </div>

            <div className={`fld${errores.fechaHoraIncidente ? ' fld-error' : ''}`}>
              <label htmlFor="reclamo-fecha">Fecha y hora del incidente *</label>
              <input
                id="reclamo-fecha"
                type="datetime-local"
                max={aFechaHoraLocal(new Date())}
                value={datos.fechaHoraIncidente}
                onChange={cambiar('fechaHoraIncidente')}
                aria-invalid={errores.fechaHoraIncidente ? 'true' : undefined}
                aria-describedby={errores.fechaHoraIncidente ? 'reclamo-fecha-error' : undefined}
              />
              {errores.fechaHoraIncidente && <span id="reclamo-fecha-error" className="error-campo">{errores.fechaHoraIncidente}</span>}
            </div>

            <div className={`fld campo-completo${errores.referencia ? ' fld-error' : ''}`}>
              <label htmlFor="reclamo-referencia">Documento o servicio relacionado (opcional)</label>
              <input
                id="reclamo-referencia"
                type="text"
                maxLength={MAXIMO_REFERENCIA + 20}
                value={datos.referencia}
                onChange={cambiar('referencia')}
                aria-invalid={errores.referencia ? 'true' : undefined}
                aria-describedby={errores.referencia ? 'reclamo-referencia-error' : 'reclamo-referencia-ayuda'}
              />
              {errores.referencia
                ? <span id="reclamo-referencia-error" className="error-campo">{errores.referencia}</span>
                : <span id="reclamo-referencia-ayuda" className="hint">Por ejemplo, el número de un memorándum o el nombre del servicio.</span>}
            </div>

            <div className={`fld campo-completo${errores.comentario ? ' fld-error' : ''}`}>
              <label htmlFor="reclamo-comentario">Comentario *</label>
              <textarea
                id="reclamo-comentario"
                rows={5}
                value={datos.comentario}
                onChange={cambiar('comentario')}
                aria-invalid={errores.comentario ? 'true' : undefined}
                aria-describedby={errores.comentario ? 'reclamo-comentario-error' : 'reclamo-comentario-ayuda'}
              />
              {errores.comentario
                ? <span id="reclamo-comentario-error" className="error-campo">{errores.comentario}</span>
                : <span id="reclamo-comentario-ayuda" className="hint">{datos.comentario.length}/{MAXIMO_COMENTARIO} caracteres</span>}
            </div>
          </fieldset>
          <div className="acciones-documento">
            <button className="btn primary" type="submit" disabled={bloqueado}>{enviando ? 'Enviando…' : 'Enviar reclamo'}</button>
            <button
              className="btn"
              type="button"
              disabled={bloqueado}
              onClick={() => { setDatos(FORMULARIO_VACIO); setErrores({}); setIntentos(0) }}
            >
              Limpiar
            </button>
          </div>
        </form>
      </section>

      <section className="pcard" aria-labelledby="titulo-mis-reclamos">
        <div className="catalogo-cabecera">
          <div>
            <h2 id="titulo-mis-reclamos">Mis reclamos</h2>
            <p className="hint" role="status">
              {ocupado
                ? 'Consultando tus reclamos…'
                : `${visibles.length} ${visibles.length === 1 ? 'reclamo' : 'reclamos'}${filtroEstado ? ` en estado «${estadoReclamo(filtroEstado).etiqueta}»` : ''}.`}
            </p>
          </div>
          <div className="fld filtro-estado">
            <label htmlFor="reclamo-filtro">Filtrar por estado</label>
            <select id="reclamo-filtro" value={filtroEstado} onChange={(e) => setFiltroEstado(e.target.value)} disabled={bloqueado}>
              <option value="">Todos</option>
              {Object.entries(ESTADOS_RECLAMO).map(([valor, e]) => <option key={valor} value={valor}>{e.etiqueta}</option>)}
            </select>
          </div>
        </div>

        {!ocupado && !error && visibles.length === 0 && (
          <div className="info">
            {filtroEstado ? 'No tienes reclamos en ese estado. Elige «Todos» para ver la lista completa.' : 'Aún no has registrado reclamos.'}
          </div>
        )}
        {!ocupado && visibles.length > 0 && (
          <div className="tabla-scroll" role="region" aria-label="Lista de mis reclamos" tabIndex={0}>
            <table className="tabla tabla-reclamos">
              <caption className="sr-only">Reclamos registrados con su tipo, fecha del incidente y estado</caption>
              <thead>
                <tr><th scope="col">Número</th><th scope="col">Tipo</th><th scope="col">Incidente</th><th scope="col">Estado</th><th scope="col">Acciones</th></tr>
              </thead>
              <tbody>
                {visibles.map((r) => (
                  <tr key={r.idReclamo}>
                    <td><strong>{r.numeroReclamo}</strong><span className="reclamo-detalle">Registrado el {formatoFechaHora(r.fechaRegistro)}</span></td>
                    <td>{etiquetaTipo(r.tipoReclamo)}{r.referencia && <span className="reclamo-detalle">{r.referencia}</span>}</td>
                    <td className="fecha-documento">{formatoFechaHora(r.fechaHoraIncidente)}</td>
                    <td><span className={`estado ${estadoReclamo(r.estado).clase}`}>{estadoReclamo(r.estado).etiqueta}</span></td>
                    <td>
                      <button
                        className="btn btn-compacto"
                        type="button"
                        aria-label={`Ver el detalle del reclamo ${r.numeroReclamo}`}
                        aria-expanded={detalleId === r.idReclamo}
                        aria-controls="detalle-reclamo"
                        onClick={(e) => abrirDetalle(r, e)}
                      >
                        Ver detalle
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {detalle && (
        <section className="pcard" id="detalle-reclamo" aria-labelledby="titulo-detalle-reclamo">
          <div className="catalogo-cabecera">
            <div>
              <h2 id="titulo-detalle-reclamo">Reclamo {detalle.numeroReclamo}</h2>
              <p className="hint">
                {etiquetaTipo(detalle.tipoReclamo)} · Incidente del {formatoFechaHora(detalle.fechaHoraIncidente)} ·{' '}
                <span className={`estado ${estadoReclamo(detalle.estado).clase}`}>{estadoReclamo(detalle.estado).etiqueta}</span>
              </p>
            </div>
            <button className="btn" type="button" onClick={cerrarDetalle}>Cerrar detalle</button>
          </div>
          {detalle.referencia && <p><strong>Relacionado con:</strong> {detalle.referencia}</p>}
          <p><strong>Comentario:</strong> {detalle.comentario}</p>

          <h3>Derivaciones</h3>
          {detalle.derivaciones.length === 0 ? (
            <p className="hint">Todavía no se derivó a ningún área. El gestor de reclamos lo hará cuando lo revise.</p>
          ) : (
            <ol className="derivaciones">
              {detalle.derivaciones.map((d, i) => (
                <li key={`${d.fechaDerivacion}-${i}`}>
                  <strong>{d.areaDestino}</strong>
                  <span className="reclamo-detalle">
                    {formatoFechaHora(d.fechaDerivacion)} · Encargado de revisión: {d.responsable} · Derivó: {d.derivadoPor}
                  </span>
                  {d.observacion && <span className="reclamo-detalle">Observación: {d.observacion}</span>}
                </li>
              ))}
            </ol>
          )}

          <h3>Respuesta</h3>
          {detalle.respuesta ? (
            <div className="info">
              <p>{detalle.respuesta}</p>
              <p className="hint">Respondido el {formatoFechaHora(detalle.fechaRespuesta)}</p>
            </div>
          ) : (
            <p className="hint">Aún no hay una respuesta oficial.</p>
          )}
        </section>
      )}
    </div>
  )
}
