import { useRef, useState } from 'react'
import { fecha, moneda } from '../utils/formato.js'
import EtiquetaDeudaVencida from './EtiquetaDeudaVencida.jsx'

function Dato({ etiqueta, valor }) {
  return (
    <div className="pf">
      <span className="pl">{etiqueta}</span>
      <span className="pv">{valor === '' || valor == null ? '—' : valor}</span>
    </div>
  )
}

export default function DetalleCobranza({ detalle }) {
  const { obligacion, fechaConsulta, diasVencida, diasEnEtapaActual, historial } = detalle
  const tramos = Array.isArray(historial) ? historial : []
  const [indiceActivo, setIndiceActivo] = useState(() => Math.max(0, tramos.length - 1))
  const botones = useRef([])

  function navegarPestanas(evento, indice) {
    if (tramos.length === 0) return
    let siguiente
    if (evento.key === 'ArrowRight') siguiente = (indice + 1) % tramos.length
    else if (evento.key === 'ArrowLeft') siguiente = (indice + tramos.length - 1) % tramos.length
    else if (evento.key === 'Home') siguiente = 0
    else if (evento.key === 'End') siguiente = tramos.length - 1
    else return
    evento.preventDefault()
    setIndiceActivo(siguiente)
    botones.current[siguiente]?.focus()
  }

  return (
    <>
      <section className="pcard" aria-label="Obligación vencida">
        <h2>{obligacion.tributoDescripcion} · {obligacion.periodo}</h2>
        <p className="hint">Obligación #{obligacion.id} · {obligacion.contribuyenteNombre} ({obligacion.contribuyenteCodigo})</p>
        <div className="detalle-cobranza__deuda">
          <EtiquetaDeudaVencida saldo={obligacion.saldo} diasVencida={diasVencida} />
        </div>
        <div className="pfields">
          <Dato etiqueta="Periodo" valor={obligacion.periodo} />
          <Dato etiqueta="Código de predio" valor={obligacion.codigoPredio} />
          <Dato etiqueta="Fecha de vencimiento" valor={fecha(obligacion.fechaVencimiento)} />
          <Dato etiqueta="Monto emitido" valor={moneda(obligacion.montoEmitido)} />
          <Dato etiqueta="Monto pagado" valor={moneda(obligacion.montoPagado)} />
          <Dato etiqueta="Fecha de consulta" valor={fecha(fechaConsulta)} />
        </div>
      </section>

      <section className="pcard" aria-label="Etapas de cobranza">
        <h2>Etapas de cobranza</h2>
        <p className="hint">Consulta el detalle de cada etapa registrada para esta obligación.</p>

        {tramos.length === 0 ? (
          <p className="hint" role="status">No hay etapas de cobranza registradas.</p>
        ) : (
          <>
            <div className="pestanas-cobranza" role="tablist" aria-label="Etapas de cobranza">
              {tramos.map((tramo, indice) => (
                <button
                  key={`${tramo.etapa}-${tramo.fechaInicio}-${indice}`}
                  ref={(elemento) => { botones.current[indice] = elemento }}
                  id={`pestana-etapa-${indice}`}
                  className={`btn${indiceActivo === indice ? ' primary' : ''}`}
                  type="button"
                  role="tab"
                  aria-selected={indiceActivo === indice}
                  aria-controls={`panel-etapa-${indice}`}
                  tabIndex={indiceActivo === indice ? 0 : -1}
                  onClick={() => setIndiceActivo(indice)}
                  onKeyDown={(evento) => navegarPestanas(evento, indice)}
                >
                  {tramo.nombre}
                  {indice === tramos.length - 1 && <span className="estado ok">Actual</span>}
                </button>
              ))}
            </div>

            {tramos.map((tramo, indice) => {
              const actual = indice === tramos.length - 1
              return (
                <div
                  key={`panel-${tramo.etapa}-${tramo.fechaInicio}-${indice}`}
                  id={`panel-etapa-${indice}`}
                  className="detalle-cobranza__etapa"
                  role="tabpanel"
                  aria-labelledby={`pestana-etapa-${indice}`}
                  hidden={indiceActivo !== indice}
                  tabIndex={0}
                >
                  <h3>{tramo.nombre}</h3>
                  <p>{tramo.descripcion}</p>
                  <div className="pfields">
                    <Dato etiqueta={tramo.registrada ? 'Inicio' : 'Inicio calculado'} valor={fecha(tramo.fechaInicio)} />
                    <Dato etiqueta="Fin" valor={tramo.fechaFin ? fecha(tramo.fechaFin) : 'Actual'} />
                    <Dato etiqueta="Documento de referencia" valor={tramo.documentoReferencia} />
                    <Dato etiqueta="Observación" valor={tramo.observacion} />
                    {actual && <Dato etiqueta="Días en la etapa actual" valor={`${diasEnEtapaActual} días`} />}
                  </div>
                  {!tramo.registrada && (
                    <div className="info" role="status">
                      Esta etapa no está registrada en SAMNET. Se muestra como mora temprana y la fecha
                      de inicio es calculada a partir del vencimiento.
                    </div>
                  )}
                </div>
              )
            })}
          </>
        )}
      </section>
    </>
  )
}
