import { fecha, moneda } from '../utils/formato.js'

function Dato({ etiqueta, valor }) {
  return (
    <div className="pf">
      <span className="pl">{etiqueta}</span>
      <span className="pv">{valor === '' || valor == null ? '—' : valor}</span>
    </div>
  )
}

export default function LineaTiempoCobranza({ detalle }) {
  const { obligacion, fechaConsulta, diasVencida, etapaActual, diasEnEtapaActual, historial } = detalle

  return (
    <>
      <section className="pcard" aria-label="Obligación vencida">
        <h2>{obligacion.tributoDescripcion} · {obligacion.periodo}</h2>
        <p className="hint">Obligación #{obligacion.id} · {obligacion.contribuyenteNombre} ({obligacion.contribuyenteCodigo})</p>
        <div className="pfields">
          <Dato etiqueta="Periodo" valor={obligacion.periodo} />
          <Dato etiqueta="Código de predio" valor={obligacion.codigoPredio} />
          <Dato etiqueta="Fecha de vencimiento" valor={fecha(obligacion.fechaVencimiento)} />
          <Dato etiqueta="Monto emitido" valor={moneda(obligacion.montoEmitido)} />
          <Dato etiqueta="Monto pagado" valor={moneda(obligacion.montoPagado)} />
          <Dato etiqueta="Saldo" valor={moneda(obligacion.saldo)} />
          <Dato etiqueta="Días vencida" valor={`${diasVencida} días`} />
          <Dato etiqueta="Fecha de consulta" valor={fecha(fechaConsulta)} />
        </div>
      </section>

      <section className="pcard" aria-label="Etapa actual de cobranza">
        <h2>Etapa actual: {etapaActual.nombre}</h2>
        <p>{etapaActual.descripcion}</p>
        <p className="hint">Días en la etapa actual: {diasEnEtapaActual}</p>
        {!etapaActual.registrada && (
          <div className="info" role="status">
            Esta etapa no está registrada en SAMNET. Se muestra como mora temprana y la fecha
            de inicio es calculada a partir del vencimiento.
          </div>
        )}
      </section>

      <section className="pcard" aria-label="Historial de etapas de cobranza">
        <h2>Línea de tiempo de cobranza</h2>
        <ol className="linea-tiempo">
          {historial.map((tramo, indice) => {
            const actual = indice === historial.length - 1
            return (
              <li key={`${tramo.etapa}-${tramo.fechaInicio}-${indice}`} className={actual ? 'actual' : ''}>
                <span className="linea-tiempo-marca" aria-hidden="true" />
                <div className="linea-tiempo-contenido">
                  <div className="linea-tiempo-titulo">
                    <strong>{tramo.nombre}</strong>
                    {actual && <span className="estado ok">Actual</span>}
                    {!tramo.registrada && <span className="estado aviso">No registrada en SAMNET</span>}
                  </div>
                  <p className="hint">{tramo.descripcion}</p>
                  <p>
                    <span className="pl">{tramo.registrada ? 'Inicio' : 'Inicio calculado'}:</span>{' '}
                    {fecha(tramo.fechaInicio)} · <span className="pl">Fin:</span>{' '}
                    {tramo.fechaFin ? fecha(tramo.fechaFin) : 'Actual'}
                  </p>
                  {tramo.documentoReferencia && (
                    <p className="hint">Documento de referencia: {tramo.documentoReferencia}</p>
                  )}
                  {tramo.observacion && <p className="hint">Observación: {tramo.observacion}</p>}
                </div>
              </li>
            )
          })}
        </ol>
      </section>
    </>
  )
}
