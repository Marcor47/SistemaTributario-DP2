import { useState } from 'react';
import { SearchIcon } from './Icons';

export default function SearchBar({ onSearch, loading, initialValue = '' }) {
  const [identificador, setIdentificador] = useState(initialValue);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (identificador.trim()) {
      onSearch(identificador.trim());
    }
  };

  const handleQuickSelect = (valor) => {
    setIdentificador(valor);
    onSearch(valor);
  };

  return (
    <div className="card" style={{ marginBottom: '24px' }}>
      <form onSubmit={handleSubmit}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <label htmlFor="search-input" className="form-label" style={{ fontSize: '14px', fontWeight: '700', color: 'var(--texto)' }}>
            Identificador del Contribuyente (DNI / RUC o Código de Contribuyente)
          </label>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <div style={{ position: 'relative', flex: '1 1 300px' }}>
              <input
                id="search-input"
                type="text"
                className="form-input"
                style={{ width: '100%', height: '44px', paddingLeft: '40px', fontSize: '15px' }}
                placeholder="Ej. 10458923, 20556677881 o C-100234..."
                value={identificador}
                onChange={(e) => setIdentificador(e.target.value)}
                disabled={loading}
              />
              <span style={{ position: 'absolute', left: '12px', top: '13px', color: 'var(--texto-atenuado)' }}>
                <SearchIcon size={18} />
              </span>
            </div>
            <button
              type="submit"
              className="btn-primary"
              style={{ height: '44px', padding: '0 24px', fontSize: '15px' }}
              disabled={loading || !identificador.trim()}
            >
              {loading ? (
                <>
                  <span className="spinner" style={{ display: 'inline-block', width: '14px', height: '14px', border: '2px solid #FFF', borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.6s linear infinite' }}></span>
                  Consultando...
                </>
              ) : (
                <>
                  <SearchIcon size={18} />
                  Buscar Contribuyente
                </>
              )}
            </button>
          </div>
        </div>
      </form>

      {/* Chips de consulta rápida de prueba */}
      <div style={{ marginTop: '14px', paddingTop: '12px', borderTop: '1px solid var(--borde)', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
        <span style={{ fontSize: '12px', fontWeight: '600', color: 'var(--texto-atenuado)' }}>
          Casos de prueba predefinidos:
        </span>
        <button
          type="button"
          onClick={() => handleQuickSelect('10458923')}
          style={{ background: 'var(--critico-fondo)', color: 'var(--critico)', border: '1px solid var(--critico-borde)', borderRadius: '6px', padding: '4px 10px', fontSize: '12px', fontWeight: '600', cursor: 'pointer' }}
        >
          DNI 10458923 &bull; Juan Mendoza (Con Deuda Vencida)
        </button>
        <button
          type="button"
          onClick={() => handleQuickSelect('20556677881')}
          style={{ background: 'var(--exito-fondo)', color: 'var(--exito)', border: '1px solid var(--exito-borde)', borderRadius: '6px', padding: '4px 10px', fontSize: '12px', fontWeight: '600', cursor: 'pointer' }}
        >
          RUC 20556677881 &bull; Inversiones San Martín (Al Día)
        </button>
        <button
          type="button"
          onClick={() => handleQuickSelect('C-300456')}
          style={{ background: 'var(--azul-suave)', color: 'var(--azul)', border: '1px solid rgba(28,92,171,0.2)', borderRadius: '6px', padding: '4px 10px', fontSize: '12px', fontWeight: '600', cursor: 'pointer' }}
        >
          Código C-300456 &bull; María Elena Ríos
        </button>
      </div>
    </div>
  );
}
