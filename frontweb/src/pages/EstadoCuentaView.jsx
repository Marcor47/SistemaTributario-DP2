import { useState, useEffect, useMemo } from 'react';
import SearchBar from '../components/SearchBar';
import ContribuyenteHeader from '../components/ContribuyenteHeader';
import KpiResumen from '../components/KpiResumen';
import FiltrosObligaciones from '../components/FiltrosObligaciones';
import TablaObligaciones from '../components/TablaObligaciones';
import { AlertCircleIcon, RefreshCwIcon } from '../components/Icons';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export default function EstadoCuentaView() {
  const [identificadorActual, setIdentificadorActual] = useState('10458923');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Estados de filtros
  const [filtroTributo, setFiltroTributo] = useState('TODOS');
  const [filtroPeriodo, setFiltroPeriodo] = useState('TODOS');
  const [filtroEstado, setFiltroEstado] = useState('TODOS');

  // Consulta al backend
  const fetchEstadoCuenta = async (idContribuyente) => {
    if (!idContribuyente) return;
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE_URL}/v1/contribuyentes/${encodeURIComponent(idContribuyente)}/estado-cuenta`);
      if (!response.ok) {
        if (response.status === 404) {
          throw new Error(`No se encontró ningún contribuyente con el identificador "${idContribuyente}". Verifique el DNI, RUC o Código.`);
        }
        throw new Error(`Error en el servidor al consultar estado de cuenta (Código HTTP: ${response.status})`);
      }
      const json = await response.json();
      setData(json);
      setIdentificadorActual(idContribuyente);
    } catch (err) {
      setError(err.message || 'Error de conexión con el servidor backend');
      setData(null);
    } finally {
      setLoading(false);
    }
  };

  // Cargar por defecto al montar la vista
  useEffect(() => {
    fetchEstadoCuenta('10458923');
  }, []);

  // Lista de periodos disponibles en los datos
  const periodosDisponibles = useMemo(() => {
    if (!data?.obligaciones) return [];
    const setP = new Set(data.obligaciones.map((o) => o.periodo));
    return Array.from(setP).sort((a, b) => b - a);
  }, [data]);

  // Lista de tributos únicos
  const tributosDisponibles = useMemo(() => {
    if (!data?.obligaciones) return [];
    const map = new Map();
    data.obligaciones.forEach((o) => {
      if (!map.has(o.tributoCodigo)) {
        map.set(o.tributoCodigo, {
          codigo: o.tributoCodigo,
          nombre: o.tributoNombre,
        });
      }
    });
    return Array.from(map.values());
  }, [data]);

  // Obligaciones filtradas
  const obligacionesFiltradas = useMemo(() => {
    if (!data?.obligaciones) return [];

    return data.obligaciones.filter((ob) => {
      // Filtro por Tributo
      if (filtroTributo === 'ARBITRIOS_TODOS') {
        if (ob.tributoCategoria !== 'ARBITRIO') return false;
      } else if (filtroTributo !== 'TODOS' && ob.tributoCodigo !== filtroTributo) {
        return false;
      }

      // Filtro por Periodo
      if (filtroPeriodo !== 'TODOS' && String(ob.periodo) !== String(filtroPeriodo)) {
        return false;
      }

      // Filtro por Estado
      if (filtroEstado !== 'TODOS' && ob.estado !== filtroEstado) {
        return false;
      }

      return true;
    });
  }, [data, filtroTributo, filtroPeriodo, filtroEstado]);

  const handleResetFiltros = () => {
    setFiltroTributo('TODOS');
    setFiltroPeriodo('TODOS');
    setFiltroEstado('TODOS');
  };

  return (
    <div className="main-content">
      {/* Título de la Historia de Usuario */}
      <div style={{ marginBottom: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
          <span style={{ backgroundColor: 'var(--azul-suave)', color: 'var(--azul)', fontSize: '11px', fontWeight: '700', padding: '3px 8px', borderRadius: '4px' }}>
            RF-004 &bull; HU004
          </span>
          <span style={{ fontSize: '13px', color: 'var(--texto-atenuado)' }}>
            Módulo de Administración Tributaria Municipal
          </span>
        </div>
        <h2 style={{ margin: 0, fontSize: '26px', fontWeight: '800', color: 'var(--cabecera)', letterSpacing: '-0.5px' }}>
          Consulta de Estado de Cuenta del Contribuyente
        </h2>
        <p style={{ margin: '6px 0 0', fontSize: '14px', color: 'var(--texto-secundario)' }}>
          Consulta detallada y desglosada por tributo, cuota y periodo fiscal con cálculo analítico de deuda vencida y saldos pendientes.
        </p>
      </div>

      {/* 1. Barra de Búsqueda */}
      <SearchBar onSearch={fetchEstadoCuenta} loading={loading} initialValue={identificadorActual} />

      {/* Mensaje de Error */}
      {error && (
        <div className="card" style={{ borderLeft: '5px solid var(--critico)', backgroundColor: 'var(--critico-fondo)' }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
            <div style={{ color: 'var(--critico)', marginTop: '2px' }}>
              <AlertCircleIcon size={20} />
            </div>
            <div>
              <h4 style={{ margin: 0, fontSize: '15px', color: 'var(--critico)', fontWeight: '700' }}>
                No se pudo completar la consulta
              </h4>
              <p style={{ margin: '4px 0 0', fontSize: '13.5px', color: '#7A271A' }}>
                {error}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Contenido cuando hay datos */}
      {data && (
        <>
          {/* 2. Cabecera del Contribuyente */}
          <ContribuyenteHeader contribuyente={data.contribuyente} resumen={data.resumen} />

          {/* 3. Tarjetas de Resumen KPI */}
          <KpiResumen resumen={data.resumen} />

          {/* 4. Barra de Filtros */}
          <FiltrosObligaciones
            filtroTributo={filtroTributo}
            setFiltroTributo={setFiltroTributo}
            filtroPeriodo={filtroPeriodo}
            setFiltroPeriodo={setFiltroPeriodo}
            filtroEstado={filtroEstado}
            setFiltroEstado={setFiltroEstado}
            periodosDisponibles={periodosDisponibles}
            tributosDisponibles={tributosDisponibles}
            totalFiltrados={obligacionesFiltradas.length}
            totalGeneral={data.obligaciones?.length || 0}
            onReset={handleResetFiltros}
          />

          {/* 5. Tabla de Obligaciones Desglosadas */}
          <TablaObligaciones obligaciones={obligacionesFiltradas} />
        </>
      )}
    </div>
  );
}
