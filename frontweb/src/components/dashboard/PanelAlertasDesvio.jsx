import CarruselAlertas from './CarruselAlertas.jsx'

export default function PanelAlertasDesvio({ alertas, total }) {
  if (alertas.length === 0) return null

  return (
    <section aria-labelledby="dashboard-alertas-titulo">
      <div className="dashboard-seccion-cab">
        <div>
          <h2 id="dashboard-alertas-titulo">Alertas según los filtros del dashboard</h2>
          <p className="hint">Se muestran las alertas activas compatibles con los filtros seleccionados.</p>
        </div>
        <div className="dashboard-seccion-cab__resumen">
          <span className="estado neutro">Filtros aplicados</span>
          <span className="hint">{total === 1 ? '1 alerta' : `${total} alertas`}</span>
        </div>
      </div>
      <CarruselAlertas alertas={alertas} />
    </section>
  )
}
