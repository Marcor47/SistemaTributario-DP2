import { useEffect, useRef, useState } from 'react'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import { fechaHora, moneda } from '../utils/formato.js'
import {
  emitirSolicitudConstancia,
  listarSolicitudesConstancia,
  obtenerSolicitudConstancia,
  rechazarSolicitudConstancia,
  verificarSolicitudConstancia,
} from '../services/constanciaGestionService.js'
import {
  confirmarPagoSolicitud,
  consultarContribuyente,
  emitirSolicitud,
  verificarDeudaSolicitud,
} from '../services/constanciaVentanillaService.js'
import './GestionConstancias.css'

const ESTADOS = {
  FIRMADA: 'Pendiente de revisión',
  DEUDA_VERIFICADA: 'Deuda verificada',
  ORDEN_PAGO_EMITIDA: 'Pendiente de pago',
  PAGADA: 'Pendiente de emisión',
  EMITIDA: 'Emitida',
  RECHAZADA: 'Rechazada',
}
const CANALES = { APP: 'Servicios en Línea', VENTANILLA: 'Ventanilla' }
const estado = (valor) => ESTADOS[valor] || valor
const CON_PARAMETROS = ['ORDEN_PAGO_EMITIDA', 'PAGADA']

function DetalleDeuda({ deuda }) {
  const grupos = new Map()
  for (const item of deuda.obligaciones || []) {
    const clave = item.tributoCodigo || item.tributo
    if (!grupos.has(clave)) grupos.set(clave, { titulo: item.tributo, items: [], centimos: 0 })
    const grupo = grupos.get(clave)
    grupo.items.push(item)
    grupo.centimos += Math.round(Number(item.totalAPagar ?? item.deuda) * 100)
  }

  return <section className="gc-deuda" aria-label="Deuda del contribuyente">
    <div className={`gc-deuda-resumen ${Number(deuda.total) === 0 ? 'gc-deuda-resumen--sin-deuda' : ''}`}>
      <div><span className="gc-etiqueta">Deuda pendiente</span>
        <strong className="gc-deuda-total">{moneda(deuda.total)}</strong>
        <span>{Number(deuda.total) > 0 ? 'El contribuyente debe cancelar su deuda para obtener la constancia.' : 'El contribuyente no registra deuda pendiente.'}</span>
      </div>
      <span className="gc-deuda-cantidad">{deuda.obligaciones?.length || 0} obligaciones pendientes</span>
    </div>
    {grupos.size > 0 && <div className="gc-deuda-grupos">
      <div className="gc-subtitulo"><h3>Detalle por tributo</h3><span>Despliega un tributo para ver sus obligaciones</span></div>
      {[...grupos].map(([clave, grupo]) => <details className="gc-tributo" key={clave}>
        <summary><div><strong>{grupo.titulo}</strong><small>{grupo.items.length} obligaciones · Ver detalle</small></div>
          <strong className="gc-importe">{moneda(grupo.centimos / 100)}</strong></summary>
        <div className="gc-deuda-tabla">
          <table><caption className="gc-solo-lectores">Obligaciones de {grupo.titulo}</caption>
            <thead><tr><th scope="col">Período</th><th scope="col">Predio</th><th scope="col">Deuda</th></tr></thead>
            <tbody>{[...grupo.items].sort((a, b) => String(a.periodo).localeCompare(String(b.periodo))).map((item) =>
              <tr key={item.id}><td>{item.periodo}</td><td>{item.codigoPredio || '—'}</td><td>{moneda(item.totalAPagar ?? item.deuda)}</td></tr>)}</tbody>
          </table>
        </div>
      </details>)}
    </div>}
  </section>
}

function ParametrosEmision({
  titulo, textoBoton, predios, seleccionados, onAlternar, fechaDesde, fechaHasta,
  onFechaDesde, onFechaHasta, rangoCompleto, cargando, ocupado, onSubmit,
}) {
  return <form className="gc-emision" onSubmit={onSubmit}>
    <h3>{titulo}</h3>
    <p className="hint">
      Selecciona los predios que comprende la constancia. Si no seleccionas ninguno, se consideran todos los
      predios del contribuyente.
    </p>
    {cargando ? (
      <p className="hint" role="status">Cargando predios del contribuyente…</p>
    ) : predios.length > 0 ? (
      <div className="gc-predios" role="group" aria-label="Predios del contribuyente">
        {predios.map((predio) => (
          <label className="gc-predio" key={predio.codigoPredio}>
            <input
              type="checkbox"
              checked={seleccionados.includes(predio.codigoPredio)}
              onChange={() => onAlternar(predio.codigoPredio)}
              disabled={ocupado}
            />
            <span>
              <strong>{predio.codigoPredio}</strong>
              <small>{predio.ubicacion || 'Ubicación no disponible'}</small>
              <small>{[predio.tipoPredio, predio.zona].filter(Boolean).join(' · ') || '—'}</small>
            </span>
          </label>
        ))}
      </div>
    ) : (
      <p className="hint">El contribuyente no tiene predios registrados; la constancia comprende todos sus predios.</p>
    )}
    <div className="filters-fields">
      <div className="fld">
        <label htmlFor="gc-fecha-desde">Fecha inicial (opcional)</label>
        <input id="gc-fecha-desde" type="date" value={fechaDesde} max={fechaHasta || undefined}
          onChange={(evento) => onFechaDesde(evento.target.value)} disabled={ocupado} />
      </div>
      <div className="fld">
        <label htmlFor="gc-fecha-hasta">Fecha final (opcional)</label>
        <input id="gc-fecha-hasta" type="date" value={fechaHasta} min={fechaDesde || undefined}
          onChange={(evento) => onFechaHasta(evento.target.value)} disabled={ocupado} />
      </div>
    </div>
    {!rangoCompleto && <p className="campo-error" role="alert">Indica ambas fechas y un rango válido.</p>}
    <p className="hint">Si se omiten ambas fechas, la constancia indica «Hasta la fecha de emisión».</p>
    <button type="submit" className="btn primary" disabled={ocupado || !rangoCompleto}>
      {ocupado ? 'Procesando…' : textoBoton}
    </button>
  </form>
}

export default function GestionConstancias() {
  const [solicitudes, setSolicitudes] = useState([])
  const [detalle, setDetalle] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [motivo, setMotivo] = useState('')
  const [pagoRevisado, setPagoRevisado] = useState(false)
  const [errorActualizacion, setErrorActualizacion] = useState('')
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)
  const [predios, setPredios] = useState([])
  const [prediosSeleccionados, setPrediosSeleccionados] = useState([])
  const [fechaDesde, setFechaDesde] = useState('')
  const [fechaHasta, setFechaHasta] = useState('')
  const [cargandoPredios, setCargandoPredios] = useState(false)
  const [scrollTick, setScrollTick] = useState(0)
  const detalleRef = useRef(null)
  const idDetalle = detalle?.idSolicitud
  const estadoDetalle = detalle?.estado
  const canalDetalle = detalle?.canal
  const documentoDetalle = detalle?.documentoContribuyente
  const sesionPagoDetalle = detalle?.ordenPago?.sesionIniciada
  const esVentanilla = canalDetalle === 'VENTANILLA'
  const paginaVisible = Math.min(pagina, Math.max(1, Math.ceil(solicitudes.length / tamanio)))
  const rangoCompleto = (!fechaDesde && !fechaHasta) || (fechaDesde && fechaHasta && fechaHasta >= fechaDesde)

  useEffect(() => {
    let activo = true
    let consultando = false
    async function actualizar() {
      if (ocupado || consultando) return
      consultando = true
      try {
        const lista = await listarSolicitudesConstancia()
        if (!activo) return
        setSolicitudes(lista)
        setErrorActualizacion('')
        if (idDetalle && !['EMITIDA', 'RECHAZADA'].includes(estadoDetalle)) {
          const actual = lista.find((item) => item.idSolicitud === idDetalle)
          if (!actual || actual.estado !== estadoDetalle || actual.ordenPago?.sesionIniciada !== sesionPagoDetalle) {
            const nuevoDetalle = await obtenerSolicitudConstancia(idDetalle)
            if (activo) { setDetalle(nuevoDetalle); setPagoRevisado(false) }
          }
        }
      } catch {
        if (activo) setErrorActualizacion('No se pudo actualizar la bandeja. Se volverá a intentar automáticamente.')
      } finally {
        consultando = false
        if (activo) setCargando(false)
      }
    }
    actualizar()
    const intervalo = setInterval(actualizar, 15000)
    return () => { activo = false; clearInterval(intervalo) }
  }, [idDetalle, estadoDetalle, sesionPagoDetalle, ocupado])

  useEffect(() => {
    if (!idDetalle || !esVentanilla || !CON_PARAMETROS.includes(estadoDetalle)) return undefined
    let activo = true
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setCargandoPredios(true)
    consultarContribuyente(documentoDetalle)
      .then((datos) => { if (activo) setPredios(datos.predios ?? []) })
      .catch(() => { if (activo) setPredios([]) })
      .finally(() => { if (activo) setCargandoPredios(false) })
    return () => { activo = false }
  }, [idDetalle, esVentanilla, estadoDetalle, documentoDetalle])

  useEffect(() => {
    if (!scrollTick || !detalleRef.current) return
    const nodo = detalleRef.current
    const reducirMovimiento = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
    nodo.focus({ preventScroll: true })
    nodo.scrollIntoView({
      behavior: reducirMovimiento ? 'auto' : 'smooth',
      block: 'start',
    })
  }, [scrollTick])

  function limpiarEmision() {
    setPredios([])
    setPrediosSeleccionados([])
    setFechaDesde('')
    setFechaHasta('')
  }

  async function seleccionar(id) {
    setOcupado(true)
    setError('')
    setAviso('')
    setMotivo('')
    setPagoRevisado(false)
    limpiarEmision()
    try {
      const actualizado = await obtenerSolicitudConstancia(id)
      setDetalle(actualizado)
      setScrollTick((valor) => valor + 1)
    } catch (err) { setError(err.message) }
    finally { setOcupado(false) }
  }

  async function operar(accion) {
    if (ocupado || !detalle) return
    setOcupado(true)
    setError('')
    setAviso('')
    try {
      await accion()
      const actualizado = await obtenerSolicitudConstancia(detalle.idSolicitud)
      setDetalle(actualizado)
      setMotivo('')
      setPagoRevisado(false)
      limpiarEmision()
      setAviso(actualizado.estado === 'RECHAZADA'
        ? 'La solicitud fue rechazada.'
        : actualizado.estado === 'EMITIDA'
          ? 'La constancia fue emitida y depositada en el buzón del contribuyente.'
          : 'La solicitud fue actualizada.')
      try { setSolicitudes(await listarSolicitudesConstancia()) }
      catch { setError('La operación se completó, pero no se pudo actualizar la lista.') }
    } catch (err) { setError(err.message) }
    finally { setOcupado(false) }
  }

  function alternarPredio(codigo) {
    setPrediosSeleccionados((actuales) => (actuales.includes(codigo)
      ? actuales.filter((valor) => valor !== codigo)
      : [...actuales, codigo]))
  }

  function confirmarPagoVentanilla(evento) {
    evento.preventDefault()
    if (!rangoCompleto) return
    operar(() => confirmarPagoSolicitud(detalle.idSolicitud, {
      predios: prediosSeleccionados, fechaDesde, fechaHasta,
    }))
  }

  function emitirVentanilla(evento) {
    evento.preventDefault()
    if (!rangoCompleto) return
    operar(() => emitirSolicitud(detalle.idSolicitud, {
      predios: prediosSeleccionados, fechaDesde, fechaHasta,
    }))
  }

  return (
    <div className="gestion-constancias">
      {error && <p className="gc-aviso gc-aviso--error" role="alert">{error}</p>}
      {errorActualizacion && <p className="gc-aviso gc-aviso--error" role="alert">{errorActualizacion}</p>}
      {aviso && <p className="gc-aviso" role="status">{aviso}</p>}
      <section className="gc-panel" aria-label="Solicitudes recibidas">
        <h2>Solicitudes recibidas</h2>
        {cargando ? <p>Cargando solicitudes…</p> : solicitudes.length === 0
          ? <p>No hay solicitudes pendientes de atención.</p>
          : <div className="gc-tabla-contenedor">
            <table className="gc-tabla">
              <thead><tr>
                <th scope="col">Solicitud</th><th scope="col">Fecha</th>
                <th scope="col">Contribuyente</th>
                <th scope="col">Documento</th>
                <th scope="col">Canal</th><th scope="col">Estado</th>
              </tr></thead>
              <tbody>{solicitudes.slice((paginaVisible - 1) * tamanio, paginaVisible * tamanio).map((item) => <tr key={item.idSolicitud}
                className={detalle?.idSolicitud === item.idSolicitud ? 'gc-fila--seleccionada' : undefined}>
                <td><button className="gc-solicitud" type="button"
                  onClick={() => seleccionar(item.idSolicitud)} disabled={ocupado}
                  aria-pressed={detalle?.idSolicitud === item.idSolicitud}>
                  Solicitud N.° {item.idSolicitud}
                </button></td>
                <td>{fechaHora(item.fechaSolicitud)}</td>
                <td><strong>{item.nombreContribuyente || 'Nombre no disponible'}</strong>
                  {item.codigoContribuyente && <small className="gc-codigo">Código: {item.codigoContribuyente}</small>}</td>
                <td>{item.tipoDocumentoContribuyente && <small className="gc-codigo">{item.tipoDocumentoContribuyente}</small>}
                  {item.documentoContribuyente}</td>
                <td><span className={`gc-canal gc-canal--${item.canal?.toLowerCase()}`}>{CANALES[item.canal] || item.canal}</span></td>
                <td><span className="gc-estado">{estado(item.estado)}</span></td>
              </tr>)}</tbody>
            </table>
            <PaginacionTabla id="solicitudes-constancia" totalRegistros={solicitudes.length} pagina={paginaVisible} tamanio={tamanio}
              onCambiarPagina={setPagina} onCambiarTamanio={(valor) => { setTamanio(valor); setPagina(1) }} />
          </div>}
      </section>
      {detalle && <section
        className="gc-panel gc-detalle"
        ref={detalleRef}
        tabIndex={-1}
        aria-labelledby="gc-detalle-titulo"
      >
        <div className="gc-detalle-cabecera">
          <div><span className="gc-etiqueta">Detalle de la solicitud</span><h2 id="gc-detalle-titulo">Solicitud N.° {detalle.idSolicitud}</h2></div>
          <div className="gc-detalle-etiquetas">
            <span className={`gc-canal gc-canal--${detalle.canal?.toLowerCase()}`}>{CANALES[detalle.canal] || detalle.canal}</span>
            <span className="gc-estado">{estado(detalle.estado)}</span>
          </div>
        </div>
        <section className="gc-contribuyente" aria-label="Datos del contribuyente">
          <h3>Contribuyente</h3>
          <strong className="gc-contribuyente-nombre">{detalle.nombreContribuyente || 'Nombre no disponible'}</strong>
          <dl className="gc-datos">
            <div><dt>Código de contribuyente</dt><dd>{detalle.codigoContribuyente || 'No disponible'}</dd></div>
            <div><dt>{detalle.tipoDocumentoContribuyente || 'Documento de identidad'}</dt><dd>{detalle.documentoContribuyente}</dd></div>
          </dl>
        </section>
        {detalle.deuda && <DetalleDeuda key={detalle.idSolicitud} deuda={detalle.deuda} />}
        <section className="gc-acciones" aria-label="Gestión del trámite">
        <h3>Gestión del trámite</h3>
        {esVentanilla && detalle.deudaPresentacion != null && (
          <p><strong>Deuda al presentar:</strong> {moneda(detalle.deudaPresentacion)}</p>
        )}
        {detalle.ordenPago && <p><strong>Derecho del trámite:</strong> {moneda(detalle.ordenPago.monto)} · {detalle.ordenPago.estado}</p>}
        {detalle.ordenPago?.comprobante && <p><strong>Comprobante:</strong> {detalle.ordenPago.comprobante}</p>}

        {detalle.numeroConstancia && <p><strong>Constancia:</strong> {detalle.numeroConstancia}</p>}
        {detalle.motivoRechazo && <p><strong>Motivo:</strong> {detalle.motivoRechazo}</p>}

        {detalle.estado === 'FIRMADA' && <>
          <p>{esVentanilla
            ? 'La firma presencial ya está registrada. Al generar la orden se usa la deuda evaluada al presentar la solicitud.'
            : 'Al verificar, se generará la orden si no hay deuda. Si existe deuda, se rechazará la solicitud.'}</p>
          <button type="button" className="btn primary" disabled={ocupado}
            onClick={() => operar(() => (esVentanilla
              ? verificarDeudaSolicitud(detalle.idSolicitud)
              : verificarSolicitudConstancia(detalle.idSolicitud)))}>
            {esVentanilla ? 'Generar orden de pago' : 'Verificar deuda y generar orden'}
          </button>
        </>}

        {detalle.estado === 'ORDEN_PAGO_EMITIDA' && !esVentanilla && (
          <p className="gc-espera">
            El contribuyente paga en Servicios en Línea. La constancia se emitirá automáticamente al confirmarse el pago.
          </p>
        )}

        {detalle.estado === 'ORDEN_PAGO_EMITIDA' && esVentanilla && (
          <ParametrosEmision
            titulo="Confirmar pago y emitir constancia"
            textoBoton="Confirmar pago y emitir"
            predios={predios}
            seleccionados={prediosSeleccionados}
            onAlternar={alternarPredio}
            fechaDesde={fechaDesde}
            fechaHasta={fechaHasta}
            onFechaDesde={setFechaDesde}
            onFechaHasta={setFechaHasta}
            rangoCompleto={rangoCompleto}
            cargando={cargandoPredios}
            ocupado={ocupado}
            onSubmit={confirmarPagoVentanilla}
          />
        )}

        {detalle.estado === 'PAGADA' && !esVentanilla && <>
          <p>El pago está confirmado. La emisión enviará la constancia al buzón del contribuyente.</p>
          <button type="button" className="btn primary" disabled={ocupado}
            onClick={() => operar(() => emitirSolicitudConstancia(detalle.idSolicitud))}>Emitir constancia</button>
        </>}

        {detalle.estado === 'PAGADA' && esVentanilla && (
          <ParametrosEmision
            titulo="Completar emisión pendiente"
            textoBoton="Emitir constancia pendiente"
            predios={predios}
            seleccionados={prediosSeleccionados}
            onAlternar={alternarPredio}
            fechaDesde={fechaDesde}
            fechaHasta={fechaHasta}
            onFechaDesde={setFechaDesde}
            onFechaHasta={setFechaHasta}
            rangoCompleto={rangoCompleto}
            cargando={cargandoPredios}
            ocupado={ocupado}
            onSubmit={emitirVentanilla}
          />
        )}

        {['FIRMADA', 'DEUDA_VERIFICADA', 'ORDEN_PAGO_EMITIDA', 'PAGADA'].includes(detalle.estado) && <form
          className="gc-rechazo"
          onSubmit={(evento) => {
            evento.preventDefault()
            if (motivo.trim() && (detalle.estado !== 'PAGADA' || pagoRevisado)) operar(() => rechazarSolicitudConstancia(detalle.idSolicitud, motivo.trim()))
          }}>
          <label htmlFor="motivo-constancia">Motivo de rechazo</label>
          <textarea id="motivo-constancia" value={motivo} onChange={(evento) => setMotivo(evento.target.value)}
            maxLength={400} disabled={ocupado} rows={3} />
          {detalle.estado === 'PAGADA' && <>
            <p>El pago ya fue confirmado. Rechazar la solicitud no devuelve el dinero; coordina su atención con el área responsable.</p>
            <label><input type="checkbox" checked={pagoRevisado} onChange={(evento) => setPagoRevisado(evento.target.checked)} disabled={ocupado} /> He revisado el pago y coordinaré su atención.</label>
          </>}
          {detalle.estado === 'ORDEN_PAGO_EMITIDA' && detalle.ordenPago?.sesionIniciada && <p>Hay un pago iniciado. Comprueba su resultado antes de rechazar la solicitud.</p>}
          <button type="submit" className="btn" disabled={ocupado || !motivo.trim() || (detalle.estado === 'PAGADA' && !pagoRevisado) || (detalle.estado === 'ORDEN_PAGO_EMITIDA' && detalle.ordenPago?.sesionIniciada)}>Rechazar solicitud</button>
        </form>}
        </section>
      </section>}
    </div>
  )
}
