import { UserIcon, BuildingIcon, MapPinIcon, PhoneIcon, AlertCircleIcon, CheckCircleIcon } from './Icons';

export default function ContribuyenteHeader({ contribuyente, resumen }) {
  if (!contribuyente) return null;

  const esEmpresa = contribuyente.tipoDocumento === 'RUC';
  const tieneDeudaVencida = resumen && resumen.totalVencido > 0;

  return (
    <div className="card" style={{ marginBottom: '20px', borderLeft: tieneDeudaVencida ? '5px solid var(--critico)' : '5px solid var(--exito)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
          <div
            style={{
              width: '50px',
              height: '50px',
              borderRadius: '10px',
              backgroundColor: esEmpresa ? 'var(--azul-suave)' : '#F0F9FF',
              color: esEmpresa ? 'var(--azul)' : '#0284C7',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {esEmpresa ? <BuildingIcon size={26} /> : <UserIcon size={26} />}
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              <h2 style={{ margin: 0, fontSize: '20px', fontWeight: '700', color: 'var(--texto)' }}>
                {contribuyente.nombresRazonSocial}
              </h2>
              <span
                style={{
                  backgroundColor: 'var(--azul-suave)',
                  color: 'var(--azul)',
                  fontSize: '11px',
                  fontWeight: '700',
                  padding: '3px 8px',
                  borderRadius: '6px',
                  border: '1px solid rgba(28,92,171,0.2)',
                }}
              >
                CÓD: {contribuyente.codigoContribuyente}
              </span>
            </div>
            <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--texto-secundario)', fontWeight: '500' }}>
              {contribuyente.tipoDocumento}: <strong>{contribuyente.numeroDocumento}</strong>
            </p>
          </div>
        </div>

        {/* Píldora de estado global accesible con Ícono + Texto */}
        <div>
          {tieneDeudaVencida ? (
            <div className="badge-estado badge-vencido" style={{ padding: '6px 14px', fontSize: '13px' }}>
              <AlertCircleIcon size={16} />
              <span>REGISTRA DEUDA VENCIDA</span>
            </div>
          ) : (
            <div className="badge-estado badge-pagado" style={{ padding: '6px 14px', fontSize: '13px' }}>
              <CheckCircleIcon size={16} />
              <span>AL DÍA EN SUS OBLIGACIONES</span>
            </div>
          )}
        </div>
      </div>

      <div
        style={{
          marginTop: '16px',
          paddingTop: '14px',
          borderTop: '1px solid #EDF2F7',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
          gap: '12px',
          fontSize: '13px',
          color: 'var(--texto-secundario)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <MapPinIcon size={16} className="text-atenuado" />
          <span><strong>Domicilio Fiscal:</strong> {contribuyente.direccionFiscal}</span>
        </div>
        {contribuyente.telefono && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <PhoneIcon size={16} className="text-atenuado" />
            <span><strong>Teléfono:</strong> {contribuyente.telefono}</span>
          </div>
        )}
        {contribuyente.email && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span><strong>Email:</strong> {contribuyente.email}</span>
          </div>
        )}
      </div>
    </div>
  );
}
