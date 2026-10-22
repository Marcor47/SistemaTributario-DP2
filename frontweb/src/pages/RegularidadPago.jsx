import { useEffect, useRef, useState } from 'react'
import { obtenerRegularidad, tieneDatosRegularidad } from '../services/regularidadService.js'
import { fecha, moneda } from '../utils/formato.js'

function FichaRegularidad({ ficha, ocupado, onAnio }) {
  const { contribuyente, anio, porcentaje, cuotas } = ficha
  const valor = porcentaje === null ? 'Sin cuotas' : `${porcentaje.toLocaleString('es-PE', { maximumFractionDigits: 1 })} %`
  const nivel = porcentaje === null ? 'sin-datos' : porcentaje >= 80 ? 'alta' : porcentaje >= 50 ? 'media' : 'baja'
  const etiquetaNivel = { alta: 'Regularidad alta', media: 'Regularidad media', baja: 'Regularidad baja' }[nivel]

  return (
    <>
      <section className="pcard" aria-label="Ficha del contribuyente">
        <h2>{contribuyente.nombre}</h2>
        <p className="hint">Código {contribuyente.codigo} · {contribuyente.tipoDoc} {contribuyente.doc}</p>
        <p className="corte"><strong>Fecha de corte:</strong> {fecha(ficha.fechaCorte)}</p>
        <div className="fld selector-anio">
          <label htmlFor="anio-regularidad">Año de la regularidad</label>
          <select id="anio-regularidad" value={anio} onChange={(e) => onAnio(Number(e.target.value))} disabled={ocupado}>
            {ficha.aniosDisponibles.map((opcion) => <option key={opcion} value={opcion}>{opcion}</option>)}
          </select>
        </div>
      </section>

      <section className="pcard" aria-label={`Regularidad de pago de ${anio}`}>
        <h2>Regularidad de pago {anio}</h2>
        <div className="resumen resumen-regularidad">
          <div className={`indicador-regularidad regularidad-${nivel}`}>
            <span className="pl">Cuotas pagadas a tiempo</span>
            <strong>{valor}</strong>
            {etiquetaNivel && <span className="nivel-regularidad">{etiquetaNivel}</span>}
            {porcentaje !== null && (
              <progress className="barra-regularidad" max="100" value={porcentaje} aria-label={`Regularidad de pago: ${valor}`} />
            )}
            <span className="hint">{ficha.aTiempo} de {ficha.emitidas} cuotas emitidas</span>
          </div>
          <div><span className="pl">Pagadas con atraso</span><strong>{ficha.conAtraso}</strong></div>
          <div className={ficha.vencidasSinPagar ? 'deuda' : ''}><span className="pl">Vencidas sin pagar</span><strong>{ficha.vencidasSinPagar}</strong></div>
          <div><span className="pl">Por vencer</span><strong>{ficha.porVencer}</strong></div>
        </div>
        <ul className="leyenda-regularidad" aria-label="Rangos de regularidad de pago">
          <li className="regularidad-alta"><span aria-hidden="true" />Alta: 80–100 %</li>
          <li className="regularidad-media"><span aria-hidden="true" />Media: 50 % a menos de 80 %</li>
          <li className="regularidad-baja"><span aria-hidden="true" />Baja: menos de 50 %</li>
        </ul>
        <p className="hint formula-regularidad">
          Cuotas pagadas hasta su fecha de vencimiento ÷ cuotas emitidas del año × 100.
          Se cuenta cada tributo y cuota por separado. Pagar después del vencimiento no cuenta como pago a tiempo.
        </p>
      </section>

      {cuotas.length === 0 ? (
        <div className="info" role="status">Este contribuyente no tiene cuotas emitidas en {anio}. No se calcula un porcentaje para ese año.</div>
      ) : (
        <section className="pcard" aria-label="Detalle de cuotas">
          <h2>Detalle de cuotas {anio}</h2>
          <div className="tabla-scroll" role="region" aria-label="Cuotas del contribuyente" tabIndex={0}>
            <table className="tabla">
              <caption className="sr-only">Cuotas utilizadas para calcular la regularidad de {contribuyente.nombre} en {anio}</caption>
              <thead><tr><th scope="col">Periodo</th><th scope="col">Tributo</th><th scope="col">Vencimiento</th><th scope="col">Fecha de pago</th><th scope="col" className="num">Emitido</th><th scope="col">Estado</th></tr></thead>
              <tbody>
                {cuotas.map((cuota) => (
                  <tr key={cuota.id}>
                    <td>{cuota.periodo}</td><td>{cuota.tributo}</td><td>{fecha(cuota.fechaVencimiento)}</td>
                    <td>{fecha(cuota.fechaPago)}</td><td className="num">{moneda(cuota.emitido)}</td>
                    <td><span className={`estado ${cuota.estado === 'A tiempo' ? 'ok' : cuota.estado === 'Vencida sin pagar' ? 'critico' : 'aviso'}`}>{cuota.estado}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  )
}

export default function RegularidadPago({ contribuyente }) {
  const [ficha, setFicha] = useState(null)
  const [ocupado, setOcupado] = useState(true)
  const [error, setError] = useState('')
  const solicitud = useRef(0)
  const disponible = tieneDatosRegularidad(contribuyente.codigo)

  useEffect(() => {
    if (!disponible) return undefined
    let activo = true
    obtenerRegularidad(contribuyente.codigo)
      .then((resultado) => { if (activo) setFicha(resultado) })
      .catch(() => { if (activo) setError('No se pudo consultar la regularidad. Intenta nuevamente.') })
      .finally(() => { if (activo) setOcupado(false) })
    return () => { activo = false }
  }, [contribuyente.codigo, disponible])

  async function cargarFicha(anio) {
    const actual = ++solicitud.current
    setOcupado(true)
    setError('')
    try {
      const resultado = await obtenerRegularidad(contribuyente.codigo, anio)
      if (actual === solicitud.current) setFicha(resultado)
    } catch {
      if (actual === solicitud.current) setError('No se pudo obtener el indicador. Intenta nuevamente.')
    } finally {
      if (actual === solicitud.current) setOcupado(false)
    }
  }

  if (!disponible) {
    return (
      <div className="info" role="status">
        No hay información de regularidad de pago disponible para este contribuyente en la demostración.
      </div>
    )
  }

  return (
    <div aria-busy={ocupado}>
      {ocupado && <p className="hint mensaje-carga" role="status">Consultando regularidad de pago…</p>}
      {error && (
        <div className="alert" role="alert">
          <p>{error}</p>
          <button className="btn" type="button" onClick={() => cargarFicha(ficha?.anio)} disabled={ocupado}>
            Reintentar regularidad
          </button>
        </div>
      )}
      {ficha && <FichaRegularidad ficha={ficha} ocupado={ocupado} onAnio={cargarFicha} />}
    </div>
  )
}
