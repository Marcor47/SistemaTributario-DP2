import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { consultarPagoPortal } from '../services/portalCuentaService.js'
import { idPagoValido } from '../utils/pagosPortal.js'
import { moneda } from '../utils/formato.js'

export default function AvisoPagoCuenta({ idTransaccion }) {
  const [resultado, setResultado] = useState({ pago: null, error: null })
  useEffect(() => {
    if (!idPagoValido(idTransaccion)) return undefined
    const controlador = new AbortController()
    consultarPagoPortal(idTransaccion, { signal: controlador.signal })
      .then((pago) => { if (!controlador.signal.aborted) setResultado({ pago, error: null }) })
      .catch((error) => { if (!controlador.signal.aborted) setResultado({ pago: null, error }) })
    return () => controlador.abort()
  }, [idTransaccion])

  if (!idPagoValido(idTransaccion)) return <p className="alert" role="status">La referencia del pago no es válida.</p>
  const { pago, error } = resultado
  if (error) return (
    <div className="alert" role="status">
      <strong>No pudimos comprobar el resultado del pago.</strong>
      <p>{error.message}</p>
      <Link className="btn" to={`/pagos/${idTransaccion}`}>Consultar esta operación</Link>
    </div>
  )
  if (!pago) return <p className="hint mensaje-carga" role="status">Comprobando el resultado de la operación…</p>
  if (pago.estado !== 'CONFIRMADA') return (
    <div className="info" role="status">
      La operación #{pago.idTransaccion} todavía no tiene un pago confirmado.{' '}
      <Link to={`/pagos/${pago.idTransaccion}`}>Consultar resultado</Link>
    </div>
  )
  return (
    <div className="confirmacion" role="status">
      <strong>Pago registrado.</strong>{' '}
      Comprobante {pago.comprobante} · Importe {moneda(pago.montoTotal)}.{' '}
      <Link to={`/pagos/${pago.idTransaccion}`}>Ver comprobante</Link>
    </div>
  )
}
