import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { iniciarPagoPortal } from '../services/portalCuentaService.js'
import { mensajeErrorPago, rutaPasarelaSimulada } from '../utils/pagosPortal.js'
import '../pages/PagosPortal.css'

export default function PagoCuentaPortal({ cuenta, onActualizar }) {
  const navegar = useNavigate()
  const [procesando, setProcesando] = useState(false)
  const [error, setError] = useState(null)
  const enCurso = useRef(false)
  const activo = useRef(true)
  const hayDeuda = cuenta.filas.some((fila) => fila.deuda > 0)
  const aviso = error ? mensajeErrorPago(error) : null

  useEffect(() => {
    activo.current = true
    return () => { activo.current = false }
  }, [])

  async function revisarPago() {
    if (enCurso.current) return
    enCurso.current = true
    setProcesando(true)
    setError(null)
    try {
      const orden = await iniciarPagoPortal()
      if (activo.current) navegar(rutaPasarelaSimulada(orden))
    } catch (fallo) {
      if (activo.current) setError(fallo)
    } finally {
      enCurso.current = false
      if (activo.current) setProcesando(false)
    }
  }

  if (!hayDeuda) return null
  return (
    <section className="pcard pago-total" aria-labelledby="pago-total-titulo" aria-busy={procesando}>
      <h2 id="pago-total-titulo">Pagar saldo completo</h2>
      <p>El pago virtual incluye todas tus obligaciones pendientes. Los filtros de esta cuenta no limitan el pago.</p>
      {cuenta.desactualizado ? (
        <p className="hint">Consulta una cuenta actualizada antes de pagar. La última copia disponible no permite crear órdenes de pago.</p>
      ) : (
        <>
          <p className="hint">Al continuar, revisaremos el total y los beneficios vigentes antes de abrir la pasarela.</p>
          {aviso && (
            <div className="alert pago-error" role="alert">
              <strong>{aviso.titulo}</strong><p>{aviso.texto}</p>
              {aviso.accion === 'sesion' && <Link className="btn" to="/login" state={{ from: '/estado-cuenta', motivo: 'servicios-en-linea' }}>Iniciar sesión</Link>}
              {aviso.accion === 'cuenta' && <button className="btn" type="button" onClick={onActualizar}>Actualizar cuenta</button>}
            </div>
          )}
          {(!aviso || aviso.accion === 'reintentar') && (
            <div className="pago-acciones">
              <span className="hint">Todavía no se confirma ningún pago.</span>
              <button className="btn primary" type="button" onClick={revisarPago} disabled={procesando}>
                {procesando ? 'Revisando saldo y beneficios…' : 'Revisar pago total'}
              </button>
            </div>
          )}
          {procesando && <p className="sr-only" role="status">Calculando el importe del pago en el servidor.</p>}
        </>
      )}
    </section>
  )
}
