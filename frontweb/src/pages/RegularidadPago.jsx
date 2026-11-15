import { useEffect, useRef, useState } from 'react'
import { obtenerRegularidad } from '../services/regularidadService.js'
import { fecha, moneda } from '../utils/formato.js'

function FichaRegularidad({ ficha, comparativaAnterior, ocupado, onAnio }) {
  const { contribuyente, anio, porcentaje, cuotas } = ficha
  const [ordenReciente, setOrdenReciente] = useState(true)
  const valor = porcentaje === null ? 'Sin cuotas' : `${porcentaje.toLocaleString('es-PE', { maximumFractionDigits: 1 })} %`
  const nivel = porcentaje === null ? 'sin-datos' : porcentaje >= 80 ? 'alta' : porcentaje >= 50 ? 'media' : 'baja'
  const etiquetaNivel = { alta: 'Regularidad alta', media: 'Regularidad media', baja: 'Regularidad baja' }[nivel]

  const anioAnterior = comparativaAnterior?.anio
  const porcentajeAnterior = comparativaAnterior?.porcentaje
  const variacion = (porcentaje !== null && porcentajeAnterior != null)
    ? Number((porcentaje - porcentajeAnterior).toFixed(1))
    : null

  const cuotasOrdenadas = [...cuotas].sort((a, b) => {
    const fechaA = a.fechaVencimiento || ''
    const fechaB = b.fechaVencimiento || ''
    if (fechaA !== fechaB) {
      return ordenReciente ? fechaB.localeCompare(fechaA) : fechaA.localeCompare(fechaB)
    }
    return ordenReciente
      ? (b.periodo || '').localeCompare(a.periodo || '')
      : (a.periodo || '').localeCompare(b.periodo || '')
  })

  return (
    <>
      <section className="pcard" aria-label="Ficha del contribuyente">
        <h2>{contribuyente.nombre}</h2>
        <p className="hint">Código {contribuyente.codigo}{contribuyente.tipoDoc ? ` · ${contribuyente.tipoDoc} ${contribuyente.doc}` : ''}</p>
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
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '10px', flexWrap: 'wrap' }}>
              <strong>{valor}</strong>
              {variacion !== null && (
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                    padding: '3px 9px',
                    borderRadius: '999px',
                    fontSize: '13px',
                    fontWeight: 700,
                    color: variacion > 0 ? 'var(--exito)' : variacion < 0 ? 'var(--critico)' : 'var(--texto-atenuado)',
                    background: variacion > 0 ? 'var(--exito-fondo)' : variacion < 0 ? 'var(--critico-fondo)' : 'var(--fondo)',
                  }}
                  title={`En el año ${anioAnterior} fue de ${porcentajeAnterior.toLocaleString('es-PE', { maximumFractionDigits: 1 })} %`}
                >
                  {variacion > 0 ? `↑ +${variacion} % vs. ${anioAnterior}` : variacion < 0 ? `↓ ${variacion} % vs. ${anioAnterior}` : `= 0.0 % vs. ${anioAnterior}`}
                </span>
              )}
            </div>
            {etiquetaNivel && <span className="nivel-regularidad">{etiquetaNivel}</span>}
            {porcentaje !== null && (
              <progress className="barra-regularidad" max="100" value={porcentaje} aria-label={`Regularidad de pago: ${valor}`} />
            )}
            <span className="hint">{ficha.aTiempo} de {ficha.exigibles} cuotas exigibles pagadas a tiempo ({ficha.emitidas} emitidas en el año)</span>
          </div>
          <div>
            <span className="pl">Pagadas a tiempo</span>
            <strong style={{ color: 'var(--exito)' }}>{ficha.aTiempo}</strong>
          </div>
          <div>
            <span className="pl">Pagadas con atraso</span>
            <strong style={{ color: 'var(--advertencia)' }}>{ficha.conAtraso}</strong>
          </div>
          <div className={ficha.vencidasSinPagar ? 'deuda' : ''}>
            <span className="pl">Vencidas sin pagar</span>
            <strong style={{ color: ficha.vencidasSinPagar > 0 ? 'var(--critico)' : 'inherit' }}>{ficha.vencidasSinPagar}</strong>
          </div>
          <div>
            <span className="pl">Por vencer</span>
            <strong style={{ color: 'var(--marino-osc)' }}>{ficha.porVencer}</strong>
          </div>
        </div>
        <ul className="leyenda-regularidad" aria-label="Rangos de regularidad de pago">
          <li className="regularidad-alta"><span aria-hidden="true" />Alta: 80–100 %</li>
          <li className="regularidad-media"><span aria-hidden="true" />Media: 50 % a menos de 80 %</li>
          <li className="regularidad-baja"><span aria-hidden="true" />Baja: menos de 50 %</li>
        </ul>
        <p className="hint formula-regularidad">
          Cuotas pagadas hasta su fecha de vencimiento ÷ cuotas del año que ya vencieron × 100.
          Se cuenta cada tributo y cuota por separado. Pagar después del vencimiento no cuenta como pago a tiempo,
          y una cuota que aún no vence no cuenta en contra.
        </p>
      </section>

      {cuotas.length === 0 ? (
        <div className="info" role="status">Este contribuyente no tiene cuotas emitidas en {anio}. No se calcula un porcentaje para ese año.</div>
      ) : (
        <section className="pcard" aria-label="Detalle de cuotas">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
            <h2 style={{ margin: 0 }}>Detalle de cuotas {anio}</h2>
            <button
              type="button"
              className="btn"
              style={{ fontSize: '13px', padding: '4px 10px' }}
              onClick={() => setOrdenReciente((prev) => !prev)}
            >
              {ordenReciente ? '↓ Más recientes primero' : '↑ Más antiguas primero'}
            </button>
          </div>
          <div className="tabla-scroll" role="region" aria-label="Cuotas del contribuyente" tabIndex={0} style={{ marginTop: '12px' }}>
            <table className="tabla">
              <caption className="sr-only">Cuotas utilizadas para calcular la regularidad de {contribuyente.nombre} en {anio}</caption>
              <thead>
                <tr>
                  <th scope="col">
                    <button
                      type="button"
                      style={{ background: 'none', border: 'none', font: 'inherit', fontWeight: 'bold', color: 'inherit', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '4px', padding: 0 }}
                      onClick={() => setOrdenReciente((prev) => !prev)}
                      title={ordenReciente ? 'Cambiar orden a ascendente' : 'Cambiar orden a descendente'}
                    >
                      Periodo {ordenReciente ? '↓' : '↑'}
                    </button>
                  </th>
                  <th scope="col">Tributo</th>
                  <th scope="col">Vencimiento</th>
                  <th scope="col">Fecha de pago</th>
                  <th scope="col" className="num">Emitido</th>
                  <th scope="col">Estado</th>
                </tr>
              </thead>
              <tbody>
                {cuotasOrdenadas.map((cuota) => (
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
  const [comparativaAnterior, setComparativaAnterior] = useState(null)
  const [ocupado, setOcupado] = useState(true)
  const [error, setError] = useState('')
  const solicitud = useRef(0)

  async function cargarComparativa(datosFicha, idSolicitud) {
    const anioPrevio = datosFicha.aniosDisponibles?.find((a) => a < datosFicha.anio)
    if (!anioPrevio) {
      if (idSolicitud === solicitud.current) setComparativaAnterior(null)
      return
    }
    try {
      const resPrevio = await obtenerRegularidad(contribuyente, anioPrevio)
      if (idSolicitud === solicitud.current) {
        setComparativaAnterior({
          anio: anioPrevio,
          porcentaje: resPrevio.porcentaje,
        })
      }
    } catch {
      if (idSolicitud === solicitud.current) setComparativaAnterior(null)
    }
  }

  useEffect(() => {
    let activo = true
    const actual = ++solicitud.current
    obtenerRegularidad(contribuyente)
      .then((resultado) => {
        if (!activo || actual !== solicitud.current) return
        setFicha(resultado)
        cargarComparativa(resultado, actual)
      })
      .catch(() => { if (activo && actual === solicitud.current) setError('No se pudo consultar la regularidad. Intenta nuevamente.') })
      .finally(() => { if (activo && actual === solicitud.current) setOcupado(false) })
    return () => { activo = false }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contribuyente.codigo])

  async function cargarFicha(anio) {
    const actual = ++solicitud.current
    setOcupado(true)
    setError('')
    try {
      const resultado = await obtenerRegularidad(contribuyente, anio)
      if (actual === solicitud.current) {
        setFicha(resultado)
        cargarComparativa(resultado, actual)
      }
    } catch {
      if (actual === solicitud.current) setError('No se pudo obtener el indicador. Intenta nuevamente.')
    } finally {
      if (actual === solicitud.current) setOcupado(false)
    }
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
      {ficha && (
        <FichaRegularidad
          ficha={ficha}
          comparativaAnterior={comparativaAnterior}
          ocupado={ocupado}
          onAnio={cargarFicha}
        />
      )}
    </div>
  )
}
