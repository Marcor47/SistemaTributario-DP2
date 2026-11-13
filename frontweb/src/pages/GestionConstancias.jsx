import { useEffect, useState } from 'react'
import {
  emitirSolicitudConstancia,
  listarSolicitudesConstancia,
  obtenerSolicitudConstancia,
  rechazarSolicitudConstancia,
  verificarSolicitudConstancia,
} from '../services/constanciaGestionService.js'
import './GestionConstancias.css'

const ESTADOS = {
  FIRMADA: 'Pendiente de revisión',
  DEUDA_VERIFICADA: 'Deuda verificada',
  ORDEN_PAGO_EMITIDA: 'Pendiente de pago',
  PAGADA: 'Pendiente de emisión',
  EMITIDA: 'Emitida',
  RECHAZADA: 'Rechazada',
}
const estado = (valor) => ESTADOS[valor] || valor
const soles = (monto) => Number(monto).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

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
  const idDetalle = detalle?.idSolicitud
  const estadoDetalle = detalle?.estado
  const sesionPagoDetalle = detalle?.ordenPago?.sesionIniciada

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

  async function seleccionar(id) {
    setOcupado(true)
    setError('')
    setAviso('')
    setMotivo('')
    setPagoRevisado(false)
    try { setDetalle(await obtenerSolicitudConstancia(id)) }
    catch (err) { setError(err.message) }
    finally { setOcupado(false) }
  }

  async function operar(accion) {
    if (ocupado || !detalle) return
    setOcupado(true)
    setError('')
    setAviso('')
    try {
      const resultado = await accion()
      setDetalle(resultado)
      setMotivo('')
      setPagoRevisado(false)
      setAviso(resultado.estado === 'RECHAZADA' ? 'La solicitud fue rechazada.' : 'La solicitud fue actualizada.')
      try { setSolicitudes(await listarSolicitudesConstancia()) }
      catch { setError('La operación se completó, pero no se pudo actualizar la lista.') }
    } catch (err) { setError(err.message) }
    finally { setOcupado(false) }
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
          : <div className="gc-lista">
            {solicitudes.map((item) => <button key={item.idSolicitud} className="gc-solicitud" type="button"
              onClick={() => seleccionar(item.idSolicitud)} disabled={ocupado}>
              <strong>Solicitud N.° {item.idSolicitud}</strong>
              <span>{item.documentoContribuyente}</span>
              <span>{estado(item.estado)}</span>
            </button>)}
          </div>}
      </section>
      {detalle && <section className="gc-panel" aria-label="Detalle de la solicitud">
        <h2>Solicitud N.° {detalle.idSolicitud}</h2>
        <p><strong>Documento del contribuyente:</strong> {detalle.documentoContribuyente}</p>
        <p><strong>Estado:</strong> {estado(detalle.estado)}</p>
        {detalle.deuda && <>
          <p><strong>Deuda pendiente:</strong> S/ {soles(detalle.deuda.total)}</p>
          {detalle.deuda.obligaciones?.length > 0 && <ul>
            {detalle.deuda.obligaciones.map((item) => <li key={item.id}>{item.tributo} · {item.periodo}: S/ {soles(item.deuda)}</li>)}
          </ul>}
        </>}
        {detalle.ordenPago && <p><strong>Derecho del trámite:</strong> S/ {soles(detalle.ordenPago.monto)} · {detalle.ordenPago.estado}</p>}
        {detalle.numeroConstancia && <p><strong>Constancia:</strong> {detalle.numeroConstancia}</p>}
        {detalle.motivoRechazo && <p><strong>Motivo:</strong> {detalle.motivoRechazo}</p>}
        {detalle.estado === 'FIRMADA' && <>
          <p>Al verificar, se generará la orden si no hay deuda. Si existe deuda, se rechazará la solicitud.</p>
          <button type="button" className="btn primary" disabled={ocupado}
            onClick={() => operar(() => verificarSolicitudConstancia(detalle.idSolicitud))}>Verificar deuda y generar orden</button>
        </>}
        {detalle.estado === 'PAGADA' && <>
          <p>El pago está confirmado. La emisión enviará la constancia al buzón del contribuyente.</p>
          <button type="button" className="btn primary" disabled={ocupado}
            onClick={() => operar(() => emitirSolicitudConstancia(detalle.idSolicitud))}>Emitir constancia</button>
        </>}
        {['FIRMADA', 'DEUDA_VERIFICADA', 'ORDEN_PAGO_EMITIDA', 'PAGADA'].includes(detalle.estado) && <form
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
      </section>}
    </div>
  )
}
