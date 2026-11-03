import CarruselAlertas from './CarruselAlertas.jsx'

export default function PanelAlertasDesvio({ alertas, total, alertasConcluidas = 0 }) {
  if (alertas.length === 0) return null

  return (
    <section aria-labelledby="dashboard-alertas-titulo">
      <div className="dashboard-seccion-cab">
        <div>
          <h2 id="dashboard-alertas-titulo">Alertas del periodo vigente</h2>
          <p className="hint">Las metas y alertas de periodos concluidos no se muestran en el dashboard.</p>
        </div>
        <div className="dashboard-seccion-cab__resumen">
          <span className="estado neutro">Periodo vigente</span>
          <span className="hint">{total === 1 ? '1 alerta' : `${total} alertas`}</span>
        </div>
      </div>
      {alertasConcluidas > 0 && (
        <p className="dashboard-nota" role="note">
          Se omitieron {alertasConcluidas} {alertasConcluidas === 1 ? 'alerta' : 'alertas'} de periodos concluidos.
        </p>
      )}
      <CarruselAlertas alertas={alertas} />
    </section>
  )
}
