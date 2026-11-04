import { useEffect, useRef } from 'react'
import './ModalConfirmacion.css'

export default function ModalConfirmacion({
  abierto,
  titulo,
  mensaje,
  textoConfirmar = 'Aceptar',
  peligro = false,
  procesando = false,
  onConfirmar,
  onCerrar,
}) {
  const dialogo = useRef(null)

  useEffect(() => {
    const elemento = dialogo.current
    if (!elemento) return
    if (abierto && !elemento.open) elemento.showModal()
    if (!abierto && elemento.open) elemento.close()
  }, [abierto])

  function cerrar() {
    if (!procesando) onCerrar()
  }

  return (
    <dialog
      ref={dialogo}
      className="modal-confirmacion"
      role="alertdialog"
      aria-labelledby="modal-confirmacion-titulo"
      aria-describedby="modal-confirmacion-mensaje"
      onClose={cerrar}
      onClick={(evento) => { if (evento.target === dialogo.current) cerrar() }}
    >
      <h2 id="modal-confirmacion-titulo">{titulo}</h2>
      <div id="modal-confirmacion-mensaje" className="modal-confirmacion-mensaje">{mensaje}</div>
      <div className="modal-confirmacion-acciones">
        <button
          className={`btn${peligro ? ' peligro' : ' primary'}`}
          type="button"
          onClick={onConfirmar}
          disabled={procesando}
        >
          {procesando ? 'Procesando…' : textoConfirmar}
        </button>
        <button className="btn" type="button" onClick={cerrar} disabled={procesando}>Cancelar</button>
      </div>
    </dialog>
  )
}
