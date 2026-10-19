import { AlertCircleIcon, CheckCircleIcon, ClockIcon } from './Icons';

function formatMonto(monto) {
  if (monto === undefined || monto === null) return '0.00';
  return Number(monto).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatFecha(fechaStr) {
  if (!fechaStr) return '-';
  const [year, month, day] = fechaStr.split('-');
  return `${day}/${month}/${year}`;
}

function getTributoColor(codigo, colorHex) {
  if (colorHex) return colorHex;
  switch (codigo) {
    case 'PREDIAL':
      return '#2A78D6';
    case 'LIMPIEZA':
      return '#EB6834';
    case 'PARQUES':
      return '#1BAF7A';
    case 'SERENAZGO':
      return '#EDA100';
    default:
      return '#1C5CAB';
  }
}

function EstadoBadge({ estado }) {
  const normEstado = estado ? estado.toUpperCase() : 'PENDIENTE';

  if (normEstado === 'VENCIDO') {
    return (
      <span className="badge-estado badge-vencido">
        <AlertCircleIcon size={14} />
        <span>Vencido</span>
      </span>
    );
  }

  if (normEstado === 'PAGADO') {
    return (
      <span className="badge-estado badge-pagado">
        <CheckCircleIcon size={14} />
        <span>Pagado</span>
      </span>
    );
  }

  if (normEstado === 'POR_VENCER') {
    return (
      <span className="badge-estado badge-por-vencer">
        <ClockIcon size={14} />
        <span>Por Vencer</span>
      </span>
    );
  }

  // PENDIENTE
  return (
    <span className="badge-estado badge-pendiente">
      <ClockIcon size={14} />
      <span>Pendiente</span>
    </span>
  );
}

export default function TablaObligaciones({ obligaciones = [] }) {
  if (obligaciones.length === 0) {
    return (
      <div className="card" style={{ textAlign: 'center', padding: '48px 20px' }}>
        <p style={{ fontSize: '16px', color: 'var(--texto-atenuado)', margin: 0 }}>
          No se encontraron obligaciones tributarias con los filtros seleccionados.
        </p>
      </div>
    );
  }

  // Totales de la selección visible
  const totalEmitido = obligaciones.reduce((acc, o) => acc + Number(o.montoEmision || 0), 0);
  const totalPagado = obligaciones.reduce((acc, o) => acc + Number(o.montoPagado || 0), 0);
  const totalSaldo = obligaciones.reduce((acc, o) => acc + Number(o.saldoPendiente || 0), 0);

  return (
    <div className="card" style={{ padding: '0', overflow: 'hidden' }}>
      <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--borde)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0, fontSize: '16px', fontWeight: '700', color: 'var(--texto)' }}>
          Detalle Desglosado de Obligaciones Tributarias
        </h3>
        <span style={{ fontSize: '13px', color: 'var(--texto-atenuado)' }}>
          {obligaciones.length} cuota(s) listada(s)
        </span>
      </div>

      <div className="table-responsive" style={{ border: 'none', borderRadius: 0 }}>
        <table className="data-table">
          <thead>
            <tr>
              <th style={{ width: '280px' }}>Tributo / Arbitrio</th>
              <th style={{ width: '150px' }}>Periodo / Cuota</th>
              <th style={{ width: '130px' }}>Vencimiento</th>
              <th style={{ textAlign: 'right', width: '130px' }}>Monto Emitido</th>
              <th style={{ textAlign: 'right', width: '130px' }}>Monto Pagado</th>
              <th style={{ textAlign: 'right', width: '130px' }}>Saldo Pendiente</th>
              <th style={{ textAlign: 'center', width: '160px' }}>Estado</th>
            </tr>
          </thead>
          <tbody>
            {obligaciones.map((ob) => {
              const colorTributo = getTributoColor(ob.tributoCodigo, ob.tributoColor);
              const esVencido = ob.estado === 'VENCIDO';

              return (
                <tr key={ob.id}>
                  {/* Tributo */}
                  <td>
                    <div className="tributo-tag">
                      <span className="tributo-dot" style={{ backgroundColor: colorTributo }}></span>
                      <span style={{ fontWeight: '600', color: 'var(--texto)' }}>
                        {ob.tributoNombre}
                      </span>
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--texto-atenuado)', marginLeft: '16px' }}>
                      {ob.tributoCategoria} &bull; {ob.tributoCodigo}
                    </div>
                  </td>

                  {/* Periodo y Cuota */}
                  <td>
                    <span style={{ fontWeight: '600' }}>Año {ob.periodo}</span>
                    <span style={{ display: 'block', fontSize: '12px', color: 'var(--texto-atenuado)' }}>
                      Cuota {ob.cuota} (Trimestre {ob.cuota})
                    </span>
                  </td>

                  {/* Vencimiento */}
                  <td>
                    <span style={{ color: esVencido ? 'var(--critico)' : 'inherit', fontWeight: esVencido ? '600' : 'normal' }}>
                      {formatFecha(ob.fechaVencimiento)}
                    </span>
                  </td>

                  {/* Monto Emitido */}
                  <td className="monto-num">
                    S/. {formatMonto(ob.montoEmision)}
                  </td>

                  {/* Monto Pagado */}
                  <td className="monto-num" style={{ color: ob.montoPagado > 0 ? 'var(--exito)' : 'var(--texto-atenuado)' }}>
                    S/. {formatMonto(ob.montoPagado)}
                  </td>

                  {/* Saldo Pendiente */}
                  <td
                    className="monto-num"
                    style={{
                      fontWeight: '700',
                      color: esVencido ? 'var(--critico)' : ob.saldoPendiente > 0 ? 'var(--advertencia)' : 'var(--exito)',
                    }}
                  >
                    S/. {formatMonto(ob.saldoPendiente)}
                  </td>

                  {/* Estado (Ícono + Texto obligatorio) */}
                  <td style={{ textAlign: 'center' }}>
                    <EstadoBadge estado={ob.estado} />
                  </td>
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan="3" style={{ textAlign: 'left', fontWeight: '700' }}>
                TOTALES DE LA CONSULTA ({obligaciones.length} REGISTROS)
              </td>
              <td className="monto-num" style={{ fontWeight: '700' }}>
                S/. {formatMonto(totalEmitido)}
              </td>
              <td className="monto-num" style={{ fontWeight: '700', color: 'var(--exito)' }}>
                S/. {formatMonto(totalPagado)}
              </td>
              <td className="monto-num" style={{ fontWeight: '700', color: totalSaldo > 0 ? 'var(--critico)' : 'var(--exito)' }}>
                S/. {formatMonto(totalSaldo)}
              </td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  );
}
