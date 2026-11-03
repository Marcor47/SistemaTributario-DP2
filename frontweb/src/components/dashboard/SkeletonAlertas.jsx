export default function SkeletonAlertas() {
  return (
    <>
      <div className="dashboard-alertas" aria-hidden="true">
        {[0, 1].map((clave) => (
          <div className="pcard dashboard-alerta" key={clave}>
            <span className="dashboard-skeleton dashboard-skeleton--titulo" />
            <span className="dashboard-skeleton dashboard-skeleton--linea" />
            <span className="dashboard-skeleton dashboard-skeleton--bloque" />
            <span className="dashboard-skeleton dashboard-skeleton--linea" />
          </div>
        ))}
      </div>
      <p className="sr-only" role="status">Consultando alertas de desvío…</p>
    </>
  )
}
