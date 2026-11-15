import { useEffect, useState } from 'react'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import { fechaHora, moneda } from '../utils/formato.js'
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
const CANALES = { APP: 'Servicios en Línea', VENTANILLA: 'Ventanilla' }
const estado = (valor) => ESTADOS[valor] || valor

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
  const idDetalle = detalle?.idSolicitud
  const estadoDetalle = detalle?.estado
  const sesionPagoDetalle = detalle?.ordenPago?.sesionIniciada
  const paginaVisible = Math.min(pagina, Math.max(1, Math.ceil(solicitudes.length / tamanio)))

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
        <h2>Solicitudes por atender</h2>
        {cargando ? <p role="status">Cargando solicitudes…</p> : solicitudes.length === 0
          ? <p className="hint">No hay solicitudes pendientes de atención.</p>
          : <>
            <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Solicitudes por atender">
              <table className="tabla">
                <caption className="sr-only">Solicitudes de Constancia de No Adeudo pendientes de atención</caption>
                <thead>
                  <tr>
                    <th scope="col">N.° de solicitud</th>
                    <th scope="col">Fecha</th>
                    <th scope="col">Documento</th>
                    <th scope="col">Canal</th>
                    <th scope="col">Estado</th>
                    <th scope="col"><span className="sr-only">Acción</span></th>
                  </tr>
                </thead>
                <tbody>
                  {solicitudes.slice((paginaVisible - 1) * tamanio, paginaVisible * tamanio).map((item) => (
                    <tr key={item.idSolicitud} aria-current={item.idSolicitud === idDetalle ? 'true' : undefined}>
                      <th scope="row">{item.idSolicitud}</th>
                      <td>{fechaHora(item.fechaSolicitud)}</td>
                      <td>{item.documentoContribuyente}</td>
                      <td>{CANALES[item.canal] || item.canal}</td>
                      <td>{estado(item.estado)}</td>
                      <td>
                        <button className="btn" type="button" onClick={() => seleccionar(item.idSolicitud)} disabled={ocupado}>
                          Ver detalle
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <PaginacionTabla id="solicitudes-constancia" totalRegistros={solicitudes.length} pagina={paginaVisible} tamanio={tamanio}
              onCambiarPagina={setPagina} onCambiarTamanio={(valor) => { setTamanio(valor); setPagina(1) }} />
          </>}
      </section>
      {detalle && <section className="gc-panel" aria-label="Detalle de la solicitud">
        <h2>Solicitud N.° {detalle.idSolicitud}</h2>
        <p><strong>Documento del contribuyente:</strong> {detalle.documentoContribuyente}</p>
        <p><strong>Estado:</strong> {estado(detalle.estado)}</p>
        {detalle.deuda && <>
          <p><strong>Deuda pendiente (incluye interés moratorio):</strong> {moneda(detalle.deuda.total)}</p>
          {detalle.deuda.obligaciones?.length > 0 && <ul>
            {detalle.deuda.obligaciones.map((item) => <li key={item.id}>{item.tributo} · {item.periodo}: {moneda(item.totalAPagar ?? item.deuda)}</li>)}
          </ul>}
        </>}
        {detalle.ordenPago && <p><strong>Derecho del trámite:</strong> {moneda(detalle.ordenPago.monto)} · {detalle.ordenPago.estado}</p>}
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
