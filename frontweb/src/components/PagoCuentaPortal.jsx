import { useState } from 'react'
import { iniciarPagoPortal, confirmarPagoPortal } from '../services/portalCuentaService.js'
import { moneda } from '../utils/formato.js'

// Conserva el acceso al pago existente sin alterar los saldos de la consulta localmente.
export default function PagoCuentaPortal({ cuenta, onPagado }) {
  const [seleccion, setSeleccion] = useState([])
  const [procesando, setProcesando] = useState(false)
  const [error, setError] = useState('')
  const pendientes = cuenta.filas.filter((fila) => fila.deuda > 0)
  const total = pendientes.filter((fila) => seleccion.includes(fila.id)).reduce((suma, fila) => suma + fila.deuda, 0)

  function alternar(id) {
    setSeleccion((actual) => actual.includes(id) ? actual.filter((valor) => valor !== id) : [...actual, id])
    setError('')
  }

  async function pagar(evento) {
    evento.preventDefault()
    if (!seleccion.length || procesando) return
    if (!window.confirm(`¿Continuar con el pago de ${seleccion.length} obligación(es) por ${moneda(total)}?`)) return
    setProcesando(true)
    setError('')
    try {
      const orden = await iniciarPagoPortal(seleccion.map(Number))
      const confirmacion = await confirmarPagoPortal(orden.idTransaccion)
      if (confirmacion.estado !== 'CONFIRMADA') throw new Error('El pago aún no está confirmado. Actualiza tu cuenta para revisar su estado.')
      onPagado({ ...confirmacion, dePrueba: Boolean(orden.dePrueba || confirmacion.dePrueba) })
    } catch (fallo) {
      setError(fallo.message || 'No se pudo confirmar el pago. Actualiza tu cuenta antes de volver a intentarlo.')
    } finally {
      setProcesando(false)
    }
  }

  if (!pendientes.length) return null
  return (
    <details className="pcard consulta-metodologia">
      <summary>Pagos en línea</summary>
      {cuenta.esDemo || cuenta.desactualizado
        ? <p className="hint">Para realizar un pago, consulta una cuenta actualizada con tu sesión de contribuyente. Esta vista es de {cuenta.esDemo ? 'demostración' : 'la última copia disponible'}.</p>
        : <form onSubmit={pagar}>
          <p>Selecciona las obligaciones que deseas pagar.</p>
          <fieldset className="consulta-pagos" disabled={procesando}>
            <legend className="sr-only">Obligaciones pendientes de pago</legend>
            {pendientes.map((fila) => (
              <label key={fila.id}>
                <input type="checkbox" checked={seleccion.includes(fila.id)} onChange={() => alternar(fila.id)} />
                <span>{fila.tributo} · {fila.periodo}</span>
                <strong>{moneda(fila.deuda)}</strong>
              </label>
            ))}
          </fieldset>
          {error && <p className="alert" role="alert">{error}</p>}
          <div className="consulta-cabecera">
            <p>Total seleccionado: <strong>{moneda(total)}</strong></p>
            <button type="submit" className="btn primary" disabled={!seleccion.length || procesando}>
              {procesando ? 'Procesando pago…' : 'Continuar con el pago'}
            </button>
          </div>
        </form>}
    </details>
  )
}
