export default function BarraFiltros({
  filtros,
  indicadores,
  onCambiarFiltro,
  onActualizar,
  cargando = false,
  children,
}) {
  return (
    <form className="filters dashboard-filtros" onSubmit={onActualizar} aria-label="Filtrar información del dashboard">
      <div className="fld">
        <label htmlFor="dashboard-indicador">Indicador</label>
        <select
          id="dashboard-indicador"
          value={filtros.indicadorCodigo ?? ''}
          onChange={(evento) => onCambiarFiltro('indicadorCodigo', evento.target.value)}
        >
          <option value="">Todos los indicadores</option>
          {indicadores.map((indicador) => (
            <option key={indicador.codigo} value={indicador.codigo}>{indicador.nombre}</option>
          ))}
        </select>
      </div>
      {children}
      <button className="btn primary" type="submit" disabled={cargando}>
        {cargando ? 'Consultando…' : 'Actualizar'}
      </button>
    </form>
  )
}
