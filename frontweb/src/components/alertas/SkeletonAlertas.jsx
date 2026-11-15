export default function SkeletonAlertas() {
  return (
    <>
      <div className="desvio-alertas" aria-hidden="true">
        {[0, 1, 2, 3].map((clave) => (
          <div className="pcard desvio-alerta" key={clave}>
            <span className="desvio-skeleton desvio-skeleton--titulo" />
            <span className="desvio-skeleton desvio-skeleton--linea" />
            <span className="desvio-skeleton desvio-skeleton--bloque" />
            <span className="desvio-skeleton desvio-skeleton--linea" />
          </div>
        ))}
      </div>
      <p className="sr-only" role="status">Consultando alertas de desvío…</p>
    </>
  )
}
