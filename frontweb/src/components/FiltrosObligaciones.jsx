import { FilterIcon, RefreshCwIcon } from './Icons';

export default function FiltrosObligaciones({
  filtroTributo,
  setFiltroTributo,
  filtroPeriodo,
  setFiltroPeriodo,
  filtroEstado,
  setFiltroEstado,
  periodosDisponibles = [],
  tributosDisponibles = [],
  totalFiltrados,
  totalGeneral,
  onReset,
}) {
  const hayFiltrosActivos =
    filtroTributo !== 'TODOS' || filtroPeriodo !== 'TODOS' || filtroEstado !== 'TODOS';

  return (
    <div className="card" style={{ padding: '16px 20px', marginBottom: '20px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px', flexWrap: 'wrap', gap: '8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FilterIcon size={18} className="text-azul" />
          <h3 style={{ margin: 0, fontSize: '15px', fontWeight: '700', color: 'var(--texto)' }}>
            Filtrar Detalle de Obligaciones Tributarias
          </h3>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <span style={{ fontSize: '13px', color: 'var(--texto-atenuado)' }}>
            Mostrando <strong>{totalFiltrados}</strong> de {totalGeneral} registro(s)
          </span>
          {hayFiltrosActivos && (
            <button
              type="button"
              className="btn-secondary"
              onClick={onReset}
              style={{ padding: '4px 10px', fontSize: '12px' }}
            >
              <RefreshCwIcon size={13} />
              Limpiar Filtros
            </button>
          )}
        </div>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '14px',
        }}
      >
        {/* Filtro por Tipo de Tributo */}
        <div className="form-group">
          <label htmlFor="filtro-tributo" className="form-label">
            Tipo de Tributo / Arbitrio
          </label>
          <select
            id="filtro-tributo"
            className="form-select"
            value={filtroTributo}
            onChange={(e) => setFiltroTributo(e.target.value)}
          >
            <option value="TODOS">Todos los tributos</option>
            <option value="ARBITRIOS_TODOS">Todos los Arbitrios (Limpieza, Parques, Serenazgo)</option>
            {tributosDisponibles.map((t) => (
              <option key={t.codigo} value={t.codigo}>
                {t.nombre}
              </option>
            ))}
          </select>
        </div>

        {/* Filtro por Periodo / Año */}
        <div className="form-group">
          <label htmlFor="filtro-periodo" className="form-label">
            Año / Periodo Fiscal
          </label>
          <select
            id="filtro-periodo"
            className="form-select"
            value={filtroPeriodo}
            onChange={(e) => setFiltroPeriodo(e.target.value)}
          >
            <option value="TODOS">Todos los años</option>
            {periodosDisponibles.map((p) => (
              <option key={p} value={p}>
                Periodo {p}
              </option>
            ))}
          </select>
        </div>

        {/* Filtro por Estado */}
        <div className="form-group">
          <label htmlFor="filtro-estado" className="form-label">
            Estado de Obligación
          </label>
          <select
            id="filtro-estado"
            className="form-select"
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
          >
            <option value="TODOS">Todos los estados</option>
            <option value="VENCIDO">Vencido (Deuda Exigible)</option>
            <option value="POR_VENCER">Por Vencer</option>
            <option value="PENDIENTE">Pendiente</option>
            <option value="PAGADO">Pagado (Al Día)</option>
          </select>
        </div>
      </div>
    </div>
  );
}
