import { useState } from 'react'
import { iniciarPagoPortal, confirmarPagoPortal } from '../services/portalCuentaService.js'
import { agruparCuentaPorPeriodo } from '../utils/estadoCuentaPortal.js'
import { moneda } from '../utils/formato.js'

// Conserva el acceso al pago existente sin alterar los saldos de la consulta localmente.
export default function PagoCuentaPortal({ cuenta, onPagado }) {
  const [seleccion, setSeleccion] = useState([])
  const [procesando, setProcesando] = useState(false)
  const [error, setError] = useState('')
  const pendientes = agruparCuentaPorPeriodo(cuenta.filas).filter((grupo) => grupo.deuda > 0)
  const elegidos = pendientes.filter((grupo) => seleccion.includes(grupo.id))
  const total = elegidos.reduce((suma, grupo) => suma + grupo.deuda, 0)

  function alternar(id) {
    setSeleccion((actual) => actual.includes(id) ? actual.filter((valor) => valor !== id) : [...actual, id])
    setError('')
  }

  async function pagar(evento) {
    evento.preventDefault()
    if (!seleccion.length || procesando) return
    if (!window.confirm(`¿Continuar con el pago de ${seleccion.length} periodo(s) por ${moneda(total)}?`)) return
    setProcesando(true)
    setError('')
    try {
      const orden = await iniciarPagoPortal(elegidos.flatMap((grupo) => grupo.idsPendientes.map(Number)))
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
          <p>Selecciona los periodos que deseas pagar. Se incluirán sus obligaciones pendientes.</p>
          <fieldset className="consulta-pagos" disabled={procesando}>
            <legend className="sr-only">Periodos pendientes de pago</legend>
            {pendientes.map((grupo) => (
              <label key={grupo.id}>
                <input type="checkbox" checked={seleccion.includes(grupo.id)} onChange={() => alternar(grupo.id)} />
                <span>{grupo.titulo} · {grupo.periodo}</span>
                <strong>{moneda(grupo.deuda)}</strong>
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
