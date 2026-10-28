import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { moneda } from '../utils/formato.js'

export default function ModalPagoConfirmado({ pago, abierto, onCerrar }) {
  const dialogo = useRef(null)
  useEffect(() => {
    if (abierto && !dialogo.current.open) dialogo.current.showModal()
    if (!abierto && dialogo.current.open) dialogo.current.close()
  }, [abierto])

  return (
    <dialog ref={dialogo} className="pago-modal" aria-labelledby="pago-modal-titulo" onClose={onCerrar}>
      <button className="pago-modal-cerrar" type="button" aria-label="Cerrar resumen del pago" onClick={onCerrar}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6" /></svg>
      </button>
      <div className="pago-modal-check" aria-hidden="true">
        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="m5 12 4 4L19 6" /></svg>
      </div>
      <h2 id="pago-modal-titulo">Pago realizado</h2>
      <p className="hint">Tu operación quedó confirmada.</p>
      <dl className="pago-modal-resumen">
        <div><dt>Comprobante</dt><dd>{pago.comprobante}</dd></div>
        <div><dt>Saldo antes de beneficios</dt><dd>{moneda(pago.montoBase)}</dd></div>
        <div><dt>Descuento aplicado</dt><dd>{moneda(pago.montoDescuento)}</dd></div>
        <div className="pago-modal-total"><dt>Total pagado</dt><dd>{moneda(pago.montoTotal)}</dd></div>
      </dl>
      <div className="pago-modal-acciones">
        <Link className="btn primary" to={`/estado-cuenta?pago=${pago.idTransaccion}`}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M19 12H5m7-7-7 7 7 7" /></svg>
          Volver a mi estado de cuenta
        </Link>
        <button className="btn" type="button" onClick={onCerrar}>Cerrar resumen</button>
      </div>
    </dialog>
  )
}
