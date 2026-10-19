import { AlertCircleIcon, ClockIcon, CheckCircleIcon, FileTextIcon } from './Icons';

function formatMonto(monto) {
  if (monto === undefined || monto === null) return 'S/. 0.00';
  return 'S/. ' + Number(monto).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function KpiResumen({ resumen }) {
  if (!resumen) return null;

  return (
    <div className="kpi-grid">
      {/* 1. Deuda Total Vencida (Crítico) */}
      <div className="kpi-card vencido">
        <div>
          <div className="kpi-header">
            <span className="kpi-title">Deuda Total Vencida</span>
            <div className="kpi-icon-badge">
              <AlertCircleIcon size={20} />
            </div>
          </div>
          <div className="kpi-value">{formatMonto(resumen.totalVencido)}</div>
        </div>
        <div className="kpi-caption" style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--critico)', fontWeight: '600' }}>
          <span>{resumen.obligacionesVencidas} cuota(s) vencida(s)</span>
        </div>
      </div>

      {/* 2. Saldo Pendiente Total */}
      <div className="kpi-card pendiente">
        <div>
          <div className="kpi-header">
            <span className="kpi-title">Saldo Pendiente Total</span>
            <div className="kpi-icon-badge">
              <ClockIcon size={20} />
            </div>
          </div>
          <div className="kpi-value">{formatMonto(resumen.totalPendiente)}</div>
        </div>
        <div className="kpi-caption">
          <span>{resumen.obligacionesPendientes} cuota(s) por vencer / pendientes</span>
        </div>
      </div>

      {/* 3. Total Pagado */}
      <div className="kpi-card pagado">
        <div>
          <div className="kpi-header">
            <span className="kpi-title">Total Pagado</span>
            <div className="kpi-icon-badge">
              <CheckCircleIcon size={20} />
            </div>
          </div>
          <div className="kpi-value">{formatMonto(resumen.totalPagado)}</div>
        </div>
        <div className="kpi-caption" style={{ color: 'var(--exito)', fontWeight: '600' }}>
          <span>{resumen.obligacionesPagadas} cuota(s) cancelada(s)</span>
        </div>
      </div>

      {/* 4. Total Emitido */}
      <div className="kpi-card emitido">
        <div>
          <div className="kpi-header">
            <span className="kpi-title">Monto Total Emitido</span>
            <div className="kpi-icon-badge">
              <FileTextIcon size={20} />
            </div>
          </div>
          <div className="kpi-value">{formatMonto(resumen.totalEmitido)}</div>
        </div>
        <div className="kpi-caption">
          <span>{resumen.totalObligaciones} obligación(es) generada(s)</span>
        </div>
      </div>
    </div>
  );
}
