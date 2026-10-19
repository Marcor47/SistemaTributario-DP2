export default function Navbar() {
  return (
    <header className="navbar-header">
      <div className="brand-wrapper">
        <div className="brand-icon">
          <span>ST</span>
        </div>
        <div className="brand-text">
          <h1>SAMNET &bull; Sistema Integral de Gestión Tributaria</h1>
          <p>Módulo de Rentas, Fiscalización y Seguimiento de Recaudación</p>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div className="user-badge">
          <span style={{ display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#10B981' }}></span>
          <span>Analista de Rentas &bull; HU004</span>
        </div>
      </div>
    </header>
  );
}
