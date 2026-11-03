export default function CardsNumericos({ metricas = [], cargando = false }) {
  if (cargando) {
    return (
      <section className="dashboard-resumen" aria-hidden="true">
        {[0, 1, 2].map((clave) => (
          <div className="pcard dashboard-kpi" key={clave}>
            <span className="dashboard-skeleton dashboard-skeleton--linea" style={{ width: '55%' }} />
            <span className="dashboard-skeleton dashboard-skeleton--valor" style={{ width: '42%' }} />
            <span className="dashboard-skeleton dashboard-skeleton--linea" style={{ width: '80%' }} />
          </div>
        ))}
      </section>
    )
  }

  if (metricas.length === 0) return null

  return (
    <section className="dashboard-resumen" aria-label="Resumen numérico del dashboard">
      {metricas.map((metrica) => (
        <div className="pcard dashboard-kpi" key={metrica.id}>
          <span className="pl">{metrica.etiqueta}</span>
          <strong className={metrica.variante === 'fecha' ? 'dashboard-kpi__fecha' : 'dashboard-kpi__valor'}>
            {metrica.valor}
          </strong>
          {metrica.detalle && <p className="hint">{metrica.detalle}</p>}
        </div>
      ))}
    </section>
  )
}
