import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { iniciarPagoPortal } from '../services/portalCuentaService.js'
import { mensajeErrorPago, rutaPasarelaSimulada } from '../utils/pagosPortal.js'
import { resumirCuenta } from '../utils/estadoCuentaPortal.js'
import { moneda } from '../utils/formato.js'
import '../pages/PagosPortal.css'

export default function PagoCuentaPortal({ cuenta, onActualizar }) {
  const navegar = useNavigate()
  const [procesando, setProcesando] = useState(false)
  const [error, setError] = useState(null)
  const enCurso = useRef(false)
  const activo = useRef(true)
  const hayDeuda = cuenta.filas.some((fila) => fila.deuda > 0)
  const aviso = error ? mensajeErrorPago(error) : null
  const saldo = resumirCuenta(cuenta.filas).deuda

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
    <section className="pcard pago-total cuenta-pago" aria-labelledby="pago-total-titulo" aria-busy={procesando}>
      <div className="cuenta-pago-cabecera"><div><p className="cuenta-etiqueta">Siguiente paso</p><h2 id="pago-total-titulo">Pagar todo el saldo</h2><p>Incluye todas las obligaciones pendientes de tu cuenta, aunque hayas filtrado la tabla.</p></div><div className="cuenta-pago-importe"><span>Saldo actual</span><strong>{moneda(saldo)}</strong></div></div>
      {cuenta.desactualizado ? (
        <p className="hint">Consulta una cuenta actualizada antes de pagar. La última copia disponible no permite crear órdenes de pago.</p>
      ) : (
        <>
          <p className="hint">Al continuar, revisaremos el importe y los beneficios vigentes. Podrás confirmar el pago en la siguiente pantalla.</p>
          {aviso && (
            <div className="alert pago-error" role="alert">
              <strong>{aviso.titulo}</strong><p>{aviso.texto}</p>
              {aviso.accion === 'sesion' && <Link className="btn" to="/login" state={{ from: '/estado-cuenta', motivo: 'servicios-en-linea' }}>Iniciar sesión</Link>}
              {aviso.accion === 'cuenta' && <button className="btn" type="button" onClick={onActualizar}>Actualizar cuenta</button>}
            </div>
          )}
          {(!aviso || aviso.accion === 'reintentar') && (
            <div className="pago-acciones">
              <span className="hint">Este botón solo abre la revisión. Todavía no se registrará el pago.</span>
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
