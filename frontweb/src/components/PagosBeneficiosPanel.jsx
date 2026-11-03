import { useEffect, useState } from 'react'
import { obtenerPagosBeneficios } from '../services/pagosBeneficiosService.js'
import { fecha, moneda } from '../utils/formato.js'
import './PagosBeneficiosPanel.css'

const CANALES = { VENTANILLA: 'Ventanilla', EN_LINEA: 'En línea' }
const TIPOS_BENEFICIO = { AMNISTIA: 'Amnistía', PARTICULAR: 'Particular' }
const porcentaje = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 })

function descripcionBeneficio(beneficio) {
  return `${TIPOS_BENEFICIO[beneficio.tipo] ?? beneficio.tipo} · ${porcentaje.format(beneficio.porcentajeDescuento)} % de descuento`
}

function BeneficioAplicado({ id, beneficios }) {
  if (!id) return <span className="hint">Sin beneficio</span>
  const beneficio = beneficios.find((item) => item.idBeneficio === id)
  return (
    <span className="pagos-beneficios__beneficio">
      {beneficio && <strong>{descripcionBeneficio(beneficio)}</strong>}
      <span className={beneficio ? 'hint' : undefined}>{id}</span>
    </span>
  )
}

function mensajeError(error) {
  if (error.estado === 403) return 'Tu usuario no tiene permiso para consultar los pagos y beneficios de contribuyentes.'
  if (error.estado === 503) return 'El servicio de SAMNET no está disponible. Intenta consultar los pagos nuevamente.'
  return error.message || 'No se pudieron consultar los pagos y beneficios. Intenta nuevamente.'
}

export default function PagosBeneficiosPanel({ contribuyente }) {
  const [revision, setRevision] = useState(0)
  const [consulta, setConsulta] = useState({ estado: 'cargando', datos: null, error: null })

  useEffect(() => {
    const controlador = new AbortController()
    obtenerPagosBeneficios(contribuyente.codigo, { signal: controlador.signal })
      .then((datos) => {
        if (!controlador.signal.aborted) setConsulta({ estado: 'listo', datos, error: null })
      })
      .catch((error) => {
        if (!controlador.signal.aborted) setConsulta({ estado: 'error', datos: null, error })
      })
    return () => controlador.abort()
  }, [contribuyente.codigo, revision])

  function recargar() {
    setConsulta({ estado: 'cargando', datos: null, error: null })
    setRevision((actual) => actual + 1)
  }

  const { datos, error } = consulta
  const ocupado = consulta.estado === 'cargando'
  const puedeReintentar = error?.estado !== 403
  const hayBeneficioSinDetalle = datos?.pagos.some((pago) => pago.beneficioAplicado
    && !datos.beneficiosVigentes.some((beneficio) => beneficio.idBeneficio === pago.beneficioAplicado))

  return (
    <section className="pcard pagos-beneficios" aria-labelledby="pagos-beneficios-titulo" aria-busy={ocupado}>
      <header className="pagos-beneficios__cabecera">
        <div>
          <h2 id="pagos-beneficios-titulo">Pagos y beneficios</h2>
          {datos && (
            <p className="pagos-beneficios__titular">
              <strong>{datos.contribuyente.nombre}</strong>
              <span className="hint">Código {datos.contribuyente.codigo} · {datos.contribuyente.tipoDocumento} {datos.contribuyente.numeroDocumento}</span>
            </p>
          )}
        </div>
        <div className="pagos-beneficios__acciones">
          {datos && <p className="corte">Fecha de corte: <strong>{fecha(datos.fechaCorte)}</strong></p>}
          {puedeReintentar && (
            <button className="btn" type="button" onClick={recargar} disabled={ocupado}>
              {ocupado ? 'Consultando…' : error ? 'Reintentar' : 'Actualizar pagos'}
            </button>
          )}
        </div>
      </header>

      {ocupado && <p className="hint mensaje-carga" role="status">Consultando pagos, beneficios y estado de cuenta…</p>}
      {error && <div className="alert" role="alert">{mensajeError(error)}</div>}

      {datos && (
        <>
          <dl className="pagos-beneficios__cuenta" aria-label="Resumen del estado de cuenta">
            <div>
              <dt>Total emitido</dt>
              <dd>{moneda(datos.estadoCuenta.totalEmitido)}</dd>
            </div>
            <div>
              <dt>Pagos aplicados</dt>
              <dd>{moneda(datos.estadoCuenta.totalPagado)}</dd>
            </div>
            <div className={Number(datos.estadoCuenta.totalDeuda) > 0 ? 'pagos-beneficios__deuda' : undefined}>
              <dt>Deuda pendiente</dt>
              <dd>{moneda(datos.estadoCuenta.totalDeuda)}</dd>
            </div>
          </dl>
          <p className="hint">Obligaciones vencidas: <strong>{datos.estadoCuenta.obligacionesVencidas}</strong></p>

          <section className="pagos-beneficios__seccion" aria-labelledby="pagos-beneficios-historial">
            <h3 id="pagos-beneficios-historial">Historial de pagos</h3>
            {datos.pagos.length === 0 ? (
              <p className="info" role="status">El contribuyente no tiene pagos registrados.</p>
            ) : (
              <>
                <p className="hint">{datos.resumen.cantidadPagos} pagos registrados · {datos.resumen.pagosConBeneficio} con beneficio aplicado</p>
                <div className="tabla-scroll" role="region" aria-label="Historial de pagos del contribuyente" tabIndex={0}>
                  <table className="tabla pagos-beneficios__tabla">
                    <caption className="sr-only">Pagos y beneficios al {fecha(datos.fechaCorte)}</caption>
                    <thead>
                      <tr>
                        <th scope="col">Pago</th>
                        <th scope="col">Fecha</th>
                        <th scope="col">Canal</th>
                        <th scope="col" className="num">Monto pagado</th>
                        <th scope="col" className="num">Correspondía pagar</th>
                        <th scope="col" className="num">Diferencia</th>
                        <th scope="col">Beneficio aplicado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {datos.pagos.map((pago) => (
                        <tr key={pago.idPago}>
                          <th scope="row">{pago.idPago}</th>
                          <td className="pagos-beneficios__fecha">{fecha(pago.fechaPago)}</td>
                          <td>{CANALES[pago.canal] ?? pago.canal}</td>
                          <td className="num">{moneda(pago.montoPagado)}</td>
                          <td className="num">{moneda(pago.montoCorrespondiente)}</td>
                          <td className={`num${Number(pago.diferencia) !== 0 ? ' pagos-beneficios__diferencia' : ''}`}>
                            {Number(pago.diferencia) > 0 ? '+' : ''}{moneda(pago.diferencia)}
                          </td>
                          <td><BeneficioAplicado id={pago.beneficioAplicado} beneficios={datos.beneficiosVigentes} /></td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr>
                        <th scope="row" colSpan={3}>Total de pagos registrados</th>
                        <td className="num">{moneda(datos.resumen.totalPagado)}</td>
                        <td className="num">{moneda(datos.resumen.totalCorrespondiente)}</td>
                        <td className="num">{moneda(datos.resumen.totalDiferencia)}</td>
                        <td />
                      </tr>
                    </tfoot>
                  </table>
                </div>
              </>
            )}
          </section>

          <section className="pagos-beneficios__seccion" aria-labelledby="pagos-beneficios-vigentes">
            <h3 id="pagos-beneficios-vigentes">Beneficios vigentes</h3>
            {datos.beneficiosVigentes.length === 0 ? (
              <p className="hint">No tiene beneficios vigentes a la fecha de corte.</p>
            ) : (
              <ul className="pagos-beneficios__lista">
                {datos.beneficiosVigentes.map((beneficio) => (
                  <li key={beneficio.idBeneficio}>
                    <strong>{descripcionBeneficio(beneficio)}</strong>
                    <span className="hint">{beneficio.idBeneficio} · Del {fecha(beneficio.vigenciaDesde)} al {fecha(beneficio.vigenciaHasta)}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <footer className="pagos-beneficios__observaciones hint">
            <p>La diferencia es el monto pagado menos el monto que correspondía pagar. Un beneficio puede explicar una diferencia.</p>
            {hayBeneficioSinDetalle && <p>Algunos beneficios aplicados solo cuentan con su identificador; el detalle no está disponible entre los beneficios vigentes.</p>}
          </footer>
        </>
      )}
    </section>
  )
}
