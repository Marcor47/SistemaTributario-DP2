import TarjetaAlerta from './TarjetaAlerta.jsx'

export default function PanelAlertasDesvio({ alertas, total }) {
  if (alertas.length === 0) return null

  return (
    <section aria-labelledby="desvio-alertas-titulo">
      <div className="desvio-seccion-cab">
        <div>
          <h2 id="desvio-alertas-titulo">Alertas activas de desvío de metas</h2>
          <p className="hint">
            Cada tarjeta compara el valor observado en la carga publicada con su meta y explica el desvío.
          </p>
        </div>
        <div className="desvio-seccion-cab__resumen">
          <span className="hint">{total === 1 ? '1 alerta' : `${total} alertas`}</span>
        </div>
      </div>
      <div className="desvio-alertas">
        {alertas.map((alerta) => <TarjetaAlerta key={alerta.idAlerta} alerta={alerta} />)}
      </div>
    </section>
  )
}
