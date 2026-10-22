import { useEffect, useMemo, useRef, useState } from 'react'
import { actualizarMeta, buscarMetas, eliminarMeta, listarIndicadores, registrarMeta } from '../services/metaService.js'
import { etiquetaUnidad, formatoValorMeta, opcionesPeriodo, periodoSugerido, validarMeta } from '../utils/metas.js'

const ANIO_ACTUAL = new Date().getFullYear()
const FORMULARIO_INICIAL = { indicadorCodigo: '', anio: ANIO_ACTUAL, numeroPeriodo: 1, valorMeta: '', observacion: '' }

function ErrorOperacion({ error }) {
  if (!error) return null
  return (
    <div className="alert" role="alert">
      <strong>{error.message || 'No se pudo completar la operación.'}</strong>
      {error.detalles?.length > 0 && <ul>{error.detalles.map((detalle) => <li key={detalle}>{detalle}</li>)}</ul>}
    </div>
  )
}

function NotificacionTemporal({ notificacion }) {
  if (!notificacion) return null
  return (
    <div key={notificacion.id} className={`notificacion-temporal ${notificacion.tipo === 'error' ? 'alert' : 'confirmacion'}`} role={notificacion.tipo === 'error' ? 'alert' : 'status'}>
      <strong>{notificacion.mensaje}</strong>
      {notificacion.detalles?.length > 0 && <ul>{notificacion.detalles.map((detalle) => <li key={detalle}>{detalle}</li>)}</ul>}
    </div>
  )
}

export default function ConfiguracionMetas() {
  const [indicadores, setIndicadores] = useState([])
  const [metas, setMetas] = useState([])
  const [filtroIndicador, setFiltroIndicador] = useState('')
  const [filtroAnio, setFiltroAnio] = useState(ANIO_ACTUAL)
  const [formulario, setFormulario] = useState(FORMULARIO_INICIAL)
  const [metaEditada, setMetaEditada] = useState(null)
  const [errores, setErrores] = useState({})
  const [errorCarga, setErrorCarga] = useState(null)
  const [notificacionFormulario, setNotificacionFormulario] = useState(null)
  const [notificacionLista, setNotificacionLista] = useState(null)
  const [cargandoCatalogo, setCargandoCatalogo] = useState(true)
  const [cargandoMetas, setCargandoMetas] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [eliminandoId, setEliminandoId] = useState(null)
  const formularioRef = useRef(null)

  const indicadorSeleccionado = useMemo(
    () => indicadores.find((indicador) => indicador.codigo === formulario.indicadorCodigo),
    [formulario.indicadorCodigo, indicadores],
  )
  const periodos = opcionesPeriodo(indicadorSeleccionado, formulario.anio)

  useEffect(() => {
    if (!notificacionFormulario) return undefined
    const temporizador = window.setTimeout(() => setNotificacionFormulario(null), 5000)
    return () => window.clearTimeout(temporizador)
  }, [notificacionFormulario])

  useEffect(() => {
    if (!notificacionLista) return undefined
    const temporizador = window.setTimeout(() => setNotificacionLista(null), 5000)
    return () => window.clearTimeout(temporizador)
  }, [notificacionLista])

  // El catálogo define la unidad y la periodicidad que utiliza el formulario.
  useEffect(() => {
    let activo = true
    listarIndicadores()
      .then((resultado) => {
        if (!activo) return
        setIndicadores(resultado)
        if (resultado.length > 0) setFormulario((actual) => ({ ...actual, indicadorCodigo: resultado[0].codigo, numeroPeriodo: periodoSugerido(resultado[0], actual.anio) }))
      })
      .catch((error) => { if (activo) setErrorCarga(error) })
      .finally(() => { if (activo) setCargandoCatalogo(false) })
    return () => { activo = false }
  }, [])

  useEffect(() => {
    let activo = true
    buscarMetas(filtroIndicador, filtroAnio)
      .then((resultado) => { if (activo) { setMetas(resultado); setErrorCarga(null) } })
      .catch((error) => { if (activo) setErrorCarga(error) })
      .finally(() => { if (activo) setCargandoMetas(false) })
    return () => { activo = false }
  }, [filtroAnio, filtroIndicador])

  function cambiarCampo(evento) {
    const { name, value } = evento.target
    setFormulario((actual) => {
      const siguiente = { ...actual, [name]: value }
      if (name === 'indicadorCodigo') siguiente.numeroPeriodo = periodoSugerido(indicadores.find((indicador) => indicador.codigo === value), actual.anio)
      if (name === 'anio') siguiente.numeroPeriodo = periodoSugerido(indicadorSeleccionado, value)
      return siguiente
    })
    setErrores((actual) => ({ ...actual, [name]: '' }))
    setNotificacionFormulario(null)
  }

  function reiniciarFormulario() {
    const primero = indicadores[0]
    setFormulario({ ...FORMULARIO_INICIAL, indicadorCodigo: primero?.codigo || '', numeroPeriodo: periodoSugerido(primero, ANIO_ACTUAL) })
    setMetaEditada(null)
    setErrores({})
  }

  async function recargarMetas() {
    setCargandoMetas(true)
    try {
      setMetas(await buscarMetas(filtroIndicador, filtroAnio))
      setErrorCarga(null)
    } catch (error) {
      setErrorCarga(error)
    } finally {
      setCargandoMetas(false)
    }
  }

  async function guardar(evento) {
    evento.preventDefault()
    const encontrados = validarMeta({ ...formulario, indicador: indicadorSeleccionado })
    setErrores(encontrados)
    setNotificacionFormulario(null)
    if (Object.keys(encontrados).length > 0) return

    setGuardando(true)
    const cuerpo = { valorMeta: Number(formulario.valorMeta), observacion: formulario.observacion.trim() || null }
    try {
      // Al editar, el backend conserva el indicador y el periodo originales.
      if (metaEditada) await actualizarMeta(metaEditada.id, cuerpo)
      else await registrarMeta({ indicadorCodigo: formulario.indicadorCodigo, anio: Number(formulario.anio), numeroPeriodo: Number(formulario.numeroPeriodo), ...cuerpo })
      const mensaje = metaEditada ? 'La meta se actualizó correctamente.' : 'La meta se registró correctamente.'
      reiniciarFormulario()
      setNotificacionFormulario({ id: Date.now(), tipo: 'exito', mensaje })
      await recargarMetas()
    } catch (error) {
      setNotificacionFormulario({ id: Date.now(), tipo: 'error', mensaje: error.message || 'No se pudo completar la operación.', detalles: error.detalles })
    } finally {
      setGuardando(false)
    }
  }

  function editar(meta) {
    setMetaEditada(meta)
    setFormulario({ indicadorCodigo: meta.indicadorCodigo, anio: meta.anio, numeroPeriodo: meta.numeroPeriodo, valorMeta: String(meta.valorMeta), observacion: meta.observacion || '' })
    setErrores({})
    setNotificacionFormulario(null)
    formularioRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  async function confirmarEliminacion(meta) {
    const confirmado = window.confirm(`¿Eliminar la meta de ${meta.indicadorNombre} para el periodo ${meta.periodo}? Esta acción no se puede deshacer.`)
    if (!confirmado) return

    setEliminandoId(meta.id)
    setNotificacionLista(null)
    try {
      await eliminarMeta(meta.id)
      if (metaEditada?.id === meta.id) reiniciarFormulario()
      await recargarMetas()
      setNotificacionLista({ id: Date.now(), tipo: 'exito', mensaje: 'La meta se eliminó correctamente.' })
    } catch (error) {
      setNotificacionLista({ id: Date.now(), tipo: 'error', mensaje: error.message || 'No se pudo eliminar la meta.', detalles: error.detalles })
    } finally {
      setEliminandoId(null)
    }
  }

  return (
    <div aria-busy={cargandoCatalogo || cargandoMetas || guardando}>
      <section className="pcard metas-cabecera">
        <div>
          <h2>Metas por indicador</h2>
          <p className="hint">Registra y consulta los objetivos que usarán el dashboard y las alertas de desvío.</p>
        </div>
        <span className="estado neutro">HU009 · RF-010</span>
      </section>

      <section className="filters metas-filtros" aria-label="Filtros de metas">
        <div className="fld">
          <label htmlFor="filtro-indicador">Indicador</label>
          <select id="filtro-indicador" value={filtroIndicador} onChange={(evento) => { setCargandoMetas(true); setFiltroIndicador(evento.target.value) }}>
            <option value="">Todos los indicadores</option>
            {indicadores.map((indicador) => <option key={indicador.codigo} value={indicador.codigo}>{indicador.nombre}</option>)}
          </select>
        </div>
        <div className="fld metas-anio">
          <label htmlFor="filtro-anio">Año</label>
          <input id="filtro-anio" type="number" min="2000" max="2100" value={filtroAnio} onChange={(evento) => { setCargandoMetas(true); setFiltroAnio(evento.target.value) }} />
        </div>
        <button className="btn" type="button" onClick={recargarMetas} disabled={cargandoMetas}>Actualizar lista</button>
      </section>

      <ErrorOperacion error={errorCarga} />

      <section className="pcard" aria-labelledby="metas-registradas-titulo">
        <div className="catalogo-cabecera">
          <div>
            <h2 id="metas-registradas-titulo">Metas registradas</h2>
            <p className="hint">Las metas de periodos concluidos se conservan como referencia y no se pueden modificar.</p>
          </div>
          {cargandoMetas && <span className="hint" role="status">Consultando metas…</span>}
        </div>
        <NotificacionTemporal notificacion={notificacionLista} />
        <div className="tabla-scroll" role="region" aria-label="Metas registradas" tabIndex={0}>
          <table className="tabla tabla-metas">
            <thead><tr><th scope="col">Indicador</th><th scope="col">Periodo</th><th scope="col">Alcance</th><th scope="col" className="num">Meta</th><th scope="col">Observación</th><th scope="col">Estado</th><th scope="col">Acción</th></tr></thead>
            <tbody>
              {!cargandoMetas && metas.length === 0 && <tr><td colSpan="7">No hay metas registradas para los filtros seleccionados.</td></tr>}
              {metas.map((meta) => (
                <tr key={meta.id}>
                  <td>{meta.indicadorNombre}</td><td>{meta.periodo}</td><td>Total municipal</td>
                  <td className="num">{formatoValorMeta(meta.valorMeta, meta.unidadMedida)}</td>
                  <td>{meta.observacion || '—'}</td>
                  <td><span className={`estado ${meta.editable ? 'ok' : 'neutro'}`}>{meta.editable ? 'Vigente' : 'Periodo concluido'}</span></td>
                  <td>
                    <div className="acciones-fila-meta">
                      <button className="btn btn-compacto" type="button" onClick={() => editar(meta)} disabled={!meta.editable || eliminandoId === meta.id} title={meta.editable ? 'Editar meta' : 'Los periodos concluidos no se pueden modificar'}>Editar</button>
                      <button className="btn btn-compacto peligro" type="button" onClick={() => confirmarEliminacion(meta)} disabled={eliminandoId === meta.id}>{eliminandoId === meta.id ? 'Eliminando…' : 'Eliminar'}</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section ref={formularioRef} className="pcard" aria-labelledby="formulario-meta-titulo">
        <h2 id="formulario-meta-titulo">{metaEditada ? 'Editar meta vigente' : 'Registrar una meta'}</h2>
        <p className="hint">En esta versión, la meta se aplica al total municipal.</p>
        <form className="formulario-meta" onSubmit={guardar} noValidate>
          <div className="pfields">
            <div className={`fld ${errores.indicadorCodigo ? 'fld-error' : ''}`}>
              <label htmlFor="meta-indicador">Indicador</label>
              <select id="meta-indicador" name="indicadorCodigo" value={formulario.indicadorCodigo} onChange={cambiarCampo} disabled={cargandoCatalogo || Boolean(metaEditada)} aria-invalid={Boolean(errores.indicadorCodigo)}>
                {indicadores.map((indicador) => <option key={indicador.codigo} value={indicador.codigo}>{indicador.nombre}</option>)}
              </select>
              {errores.indicadorCodigo && <span className="campo-error">{errores.indicadorCodigo}</span>}
            </div>
            <div className={`fld ${errores.anio ? 'fld-error' : ''}`}>
              <label htmlFor="meta-anio">Año</label>
              <input id="meta-anio" name="anio" type="number" min={ANIO_ACTUAL} max="2100" value={formulario.anio} onChange={cambiarCampo} disabled={Boolean(metaEditada)} aria-invalid={Boolean(errores.anio)} />
              {errores.anio && <span className="campo-error">{errores.anio}</span>}
            </div>
            <div className={`fld ${errores.numeroPeriodo ? 'fld-error' : ''}`}>
              <label htmlFor="meta-periodo">Periodo</label>
              <select id="meta-periodo" name="numeroPeriodo" value={formulario.numeroPeriodo} onChange={cambiarCampo} disabled={Boolean(metaEditada)} aria-invalid={Boolean(errores.numeroPeriodo)}>
                {periodos.map((periodo) => <option key={periodo.numero} value={periodo.numero}>{periodo.etiqueta}</option>)}
              </select>
              {errores.numeroPeriodo && <span className="campo-error">{errores.numeroPeriodo}</span>}
            </div>
            <div className="fld">
              <label htmlFor="meta-alcance">Alcance</label>
              <input id="meta-alcance" value="Total municipal" disabled />
            </div>
            <div className={`fld ${errores.valorMeta ? 'fld-error' : ''}`}>
              <label htmlFor="meta-valor">Valor de la meta{indicadorSeleccionado ? ` (${etiquetaUnidad(indicadorSeleccionado.unidadMedida)})` : ''}</label>
              <input id="meta-valor" name="valorMeta" type="number" min="0" max={indicadorSeleccionado?.unidadMedida === 'PORCENTAJE' ? '100' : undefined} step="0.01" value={formulario.valorMeta} onChange={cambiarCampo} aria-invalid={Boolean(errores.valorMeta)} />
              {errores.valorMeta && <span className="campo-error">{errores.valorMeta}</span>}
            </div>
            <div className={`fld campo-completo ${errores.observacion ? 'fld-error' : ''}`}>
              <label htmlFor="meta-observacion">Observación opcional</label>
              <textarea id="meta-observacion" name="observacion" rows="3" maxLength="250" value={formulario.observacion} onChange={cambiarCampo} aria-invalid={Boolean(errores.observacion)} />
              <span className="hint">{formulario.observacion.length}/250 caracteres</span>
              {errores.observacion && <span className="campo-error">{errores.observacion}</span>}
            </div>
          </div>

          {indicadorSeleccionado && (
            <div className="info ficha-indicador">
              <strong>{indicadorSeleccionado.nombre}</strong>
              <p>{indicadorSeleccionado.definicion}</p>
              <p><strong>Fórmula:</strong> {indicadorSeleccionado.formula}</p>
              <p><strong>Fuente:</strong> {indicadorSeleccionado.fuente}</p>
            </div>
          )}

          <NotificacionTemporal notificacion={notificacionFormulario} />

          <div className="acciones-meta">
            {metaEditada && <button className="btn" type="button" onClick={reiniciarFormulario} disabled={guardando}>Cancelar edición</button>}
            <button className="btn primary" type="submit" disabled={guardando || cargandoCatalogo}>{guardando ? 'Guardando…' : metaEditada ? 'Guardar cambios' : 'Registrar meta'}</button>
          </div>
        </form>
      </section>
    </div>
  )
}
