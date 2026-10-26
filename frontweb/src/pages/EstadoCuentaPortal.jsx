import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ETAPAS_COBRANZA_INFO,
  confirmarPagoPortal,
  iniciarPagoPortal,
  obtenerEstadoCuentaPortal,
  obtenerHistorialPagosPortal,
} from '../services/portalCuentaService.js'
import { fecha, moneda } from '../utils/formato.js'
import './EstadoCuentaPortal.css'

const PESTANAS = [
  { id: 'TODOS', nombre: 'Todas las obligaciones' },
  { id: 'PREDIAL', nombre: 'Impuesto Predial' },
  { id: 'ARBITRIOS', nombre: 'Arbitrios Municipales' },
  { id: 'MULTAS', nombre: 'Multas y Sanciones' },
  { id: 'HISTORIAL', nombre: 'Historial de Pagos' },
]

export default function EstadoCuentaPortal() {
  const [cuenta, setCuenta] = useState(null)
  const [historialPagos, setHistorialPagos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [sinSesion, setSinSesion] = useState(false)
  const [simularContingencia, setSimularContingencia] = useState(false)

  // Filtros
  const [tabActiva, setTabActiva] = useState('TODOS')
  const [anioSeleccionado, setAnioSeleccionado] = useState('TODOS')
  const [estadoFiltro, setEstadoFiltro] = useState('TODOS')

  // Selección de obligaciones para Pago HU022
  const [seleccionadas, setSeleccionadas] = useState(new Set())
  const [modalPagoAbierto, setModalPagoAbierto] = useState(false)
  const [procesandoPago, setProcesandoPago] = useState(false)
  const [pagoCompletado, setPagoCompletado] = useState(null)

  const solicitudRef = useRef(0)
  const idAnioFiltro = useId()
  const idEstadoFiltro = useId()

  useEffect(() => {
    let activo = true
    const actual = ++solicitudRef.current
    Promise.all([
      obtenerEstadoCuentaPortal({ simularCaida: simularContingencia }),
      obtenerHistorialPagosPortal(),
    ])
      .then(([datosCuenta, pagos]) => {
        if (activo && actual === solicitudRef.current) {
          setCuenta(datosCuenta)
          setHistorialPagos(pagos)
        }
      })
      .catch((err) => {
        if (activo && actual === solicitudRef.current) {
          if (err.estado === 401) {
            setSinSesion(true)
          } else {
            setError(err.message || 'No se pudo cargar el estado de cuenta. Intenta nuevamente.')
          }
        }
      })
      .finally(() => {
        if (activo && actual === solicitudRef.current) {
          setCargando(false)
        }
      })

    return () => {
      activo = false
    }
  }, [simularContingencia])

  async function recargar() {
    const actual = ++solicitudRef.current
    setCargando(true)
    setError('')
    setSinSesion(false)
    try {
      const [datosCuenta, pagos] = await Promise.all([
        obtenerEstadoCuentaPortal({ simularCaida: simularContingencia }),
        obtenerHistorialPagosPortal(),
      ])
      if (actual === solicitudRef.current) {
        setCuenta(datosCuenta)
        setHistorialPagos(pagos)
      }
    } catch (err) {
      if (actual === solicitudRef.current) {
        if (err.estado === 401) {
          setSinSesion(true)
        } else {
          setError(err.message || 'No se pudo cargar el estado de cuenta. Intenta nuevamente.')
        }
      }
    } finally {
      if (actual === solicitudRef.current) {
        setCargando(false)
      }
    }
  }

  function alternarContingencia(activar) {
    setCargando(true)
    setSimularContingencia(activar)
  }

  // Años disponibles según las obligaciones registradas
  const aniosDisponibles = useMemo(() => {
    if (!cuenta?.filas) return []
    const setAnios = new Set(cuenta.filas.map((f) => f.anio).filter(Boolean))
    return Array.from(setAnios).sort((a, b) => b - a)
  }, [cuenta])

  // Filtrado de obligaciones
  const filasFiltradas = useMemo(() => {
    if (!cuenta?.filas) return []
    return cuenta.filas.filter((fila) => {
      // Filtro por pestaña de categoría
      if (tabActiva !== 'TODOS' && tabActiva !== 'HISTORIAL') {
        if (fila.categoria !== tabActiva) return false
      }
      // Filtro por Año
      if (anioSeleccionado !== 'TODOS') {
        if (String(fila.anio) !== String(anioSeleccionado)) return false
      }
      // Filtro por Estado
      if (estadoFiltro === 'PENDIENTES_VENCIDAS') {
        if (fila.estado === 'Pagada') return false
      } else if (estadoFiltro === 'PAGADA') {
        if (fila.estado !== 'Pagada') return false
      }
      return true
    })
  }, [cuenta, tabActiva, anioSeleccionado, estadoFiltro])

  // Contadores por pestaña para los badges del menú
  const conteoPorCategoria = useMemo(() => {
    if (!cuenta?.filas) return {}
    return {
      TODOS: cuenta.filas.length,
      PREDIAL: cuenta.filas.filter((f) => f.categoria === 'PREDIAL').length,
      ARBITRIOS: cuenta.filas.filter((f) => f.categoria === 'ARBITRIOS').length,
      MULTAS: cuenta.filas.filter((f) => f.categoria === 'MULTAS').length,
    }
  }, [cuenta])

  // Totales de KPI
  const { totalDeudaVigente, totalPagado, totalVencido, cantidadVencidas, cuotasPendientes } = useMemo(() => {
    if (!cuenta?.filas) {
      return { totalDeudaVigente: 0, totalPagado: 0, totalVencido: 0, cantidadVencidas: 0, cuotasPendientes: [] }
    }
    let deudaVigente = 0
    let pagado = 0
    let vencido = 0
    let conteoVencidas = 0
    const pendientes = []

    for (const f of cuenta.filas) {
      if (f.estado === 'Pagada') {
        pagado += f.pagado
      } else {
        deudaVigente += f.montoPagar
        pendientes.push(f)
        if (f.estado === 'Vencida') {
          vencido += f.montoPagar
          conteoVencidas++
        }
      }
    }
    return {
      totalDeudaVigente: Math.round(deudaVigente * 100) / 100,
      totalPagado: Math.round(pagado * 100) / 100,
      totalVencido: Math.round(vencido * 100) / 100,
      cantidadVencidas: conteoVencidas,
      cuotasPendientes: pendientes,
    }
  }, [cuenta])

  // Manejo de Selección para Pago (HU022)
  function alternarSeleccion(id) {
    setSeleccionadas((prev) => {
      const nuevo = new Set(prev)
      if (nuevo.has(id)) nuevo.delete(id)
      else nuevo.add(id)
      return nuevo
    })
  }

  function seleccionarTodasPendientes() {
    const ids = cuotasPendientes.map((f) => f.id)
    setSeleccionadas(new Set(ids))
  }

  function limpiarSeleccion() {
    setSeleccionadas(new Set())
  }

  const filasSeleccionadas = useMemo(() => {
    if (!cuenta?.filas) return []
    return cuenta.filas.filter((f) => seleccionadas.has(f.id))
  }, [cuenta, seleccionadas])

  const totalAPagarSeleccion = useMemo(() => {
    return filasSeleccionadas.reduce((acc, f) => acc + f.montoPagar, 0)
  }, [filasSeleccionadas])

  // Ejecución de Pago en Línea (HU022)
  async function procederPago() {
    if (seleccionadas.size === 0) return
    setProcesandoPago(true)
    setError('')
    try {
      const ids = Array.from(seleccionadas).map(Number)
      const orden = await iniciarPagoPortal(ids)
      await new Promise((r) => setTimeout(r, 600))
      await confirmarPagoPortal(orden.idTransaccion)

      setPagoCompletado({
        idTransaccion: orden.idTransaccion,
        monto: totalAPagarSeleccion,
        cantidad: seleccionadas.size,
      })

      // Actualizar localmente las obligaciones como pagadas
      setCuenta((prev) => {
        if (!prev) return prev
        const nuevasFilas = prev.filas.map((f) => {
          if (seleccionadas.has(f.id)) {
            return {
              ...f,
              estado: 'Pagada',
              pagado: f.emitido,
              deuda: 0,
              montoPagar: 0,
              etapaCobranza: null,
            }
          }
          return f
        })
        return { ...prev, filas: nuevasFilas }
      })

      // Agregar al historial de pagos
      const nuevoPagoHistorial = {
        idPago: `WEB-${orden.idTransaccion}`,
        fechaPago: new Date().toISOString().slice(0, 10),
        canal: 'En línea',
        concepto: `${seleccionadas.size} obligación(es) tributaria(s) cancelada(s)`,
        montoPagado: totalAPagarSeleccion,
        beneficioAplicado: filasSeleccionadas.reduce((a, f) => a + f.descuento, 0),
        recibo: `OP-${orden.idTransaccion}`,
      }
      setHistorialPagos((prev) => [nuevoPagoHistorial, ...prev])
      limpiarSeleccion()
    } catch (err) {
      setError(err.message || 'No se pudo procesar el pago. Intenta nuevamente.')
      setModalPagoAbierto(false)
    } finally {
      setProcesandoPago(false)
    }
  }

  // Navegación accesible de pestañas con teclado
  function manejarTecladoTabs(e, index) {
    if (e.key === 'ArrowRight') {
      e.preventDefault()
      const sig = (index + 1) % PESTANAS.length
      setTabActiva(PESTANAS[sig].id)
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault()
      const ant = (index - 1 + PESTANAS.length) % PESTANAS.length
      setTabActiva(PESTANAS[ant].id)
    }
  }

  // Si no hay sesión activa (RNF-023 / RNF-028)
  if (sinSesion) {
    return (
      <div className="portal-cuenta" role="main" aria-label="Estado de cuenta">
        <section className="pcard alerta-contingencia" role="alert">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <div>
            <h3>Sesión no iniciada o caducada</h3>
            <p>
              Para consultar tu estado de cuenta tributario debes iniciar sesión con tus credenciales de contribuyente.
              Por seguridad, el sistema solo expone las obligaciones vinculadas a tu propia sesión.
            </p>
            <div style={{ marginTop: '12px' }}>
              <Link to="/login" className="btn primary">
                Iniciar sesión en Servicios en Línea
              </Link>
            </div>
          </div>
        </section>
      </div>
    )
  }

  return (
    <div className="portal-cuenta" role="main" aria-label="Portal Contribuyente: Mi Estado de Cuenta">
      {/* 1. Cabecera y Contexto de Datos (RNF-023 / RNF-028 / RNF-002) */}
      {cargando && (
        <div className="pcard skeleton-cabecera skeleton-shimmer" aria-busy="true" aria-label="Cargando cabecera..." />
      )}

      {!cargando && cuenta && (
        <section className="portal-cabecera" aria-label="Datos del contribuyente autenticado">
          <div className="portal-contribuyente-info">
            <h2>{cuenta.contribuyente.nombre}</h2>
            <div className="portal-contribuyente-meta">
              <span>Código: <strong>{cuenta.contribuyente.codigo}</strong></span>
              <span className="portal-separador">·</span>
              <span>Documento: <strong>{cuenta.contribuyente.tipoDoc} {cuenta.contribuyente.doc}</strong></span>
              <span className="portal-separador">·</span>
              <span>Portal del Ciudadano · San Miguel</span>
            </div>
          </div>

          <div className="portal-cabecera-badges">
            <div className="badge-corte" title="Fecha oficial de corte de datos según SAMNET">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              <span>Fecha de Corte: <strong>{fecha(cuenta.fechaCorte)}</strong></span>
            </div>

            {totalDeudaVigente === 0 && (
              <span className="badge-al-dia">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                Contribuyente al día
              </span>
            )}
          </div>
        </section>
      )}

      {/* Alerta de Contingencia SAMNET (RNF-002) */}
      {!cargando && cuenta && (cuenta.esUltimaCopia || cuenta.desactualizado) && (
        <aside className="alerta-contingencia" role="alert" aria-live="polite">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
          <div>
            <h3>Aviso de contingencia: Mostrando última información registrada al {fecha(cuenta.fechaCorte)}</h3>
            <p>
              El sistema tributario municipal SAMNET no responde en este momento. De acuerdo con la política de
              contingencia institucional (RNF-002), se expone la última copia registrada de tu estado de cuenta.
              Los pagos realizados recientemente podrían demorar en reflejarse.
            </p>
          </div>
        </aside>
      )}

      {/* Toggle de demostración para evaluar contingencia RNF-002 */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '-10px' }}>
        <label className="demo-opcion">
          <input
            type="checkbox"
            checked={simularContingencia}
            onChange={(e) => alternarContingencia(e.target.checked)}
          />
          Simular contingencia SAMNET (RNF-002)
        </label>
      </div>

      {/* Alerta de Error si ocurre */}
      {error && (
        <div className="alert" role="alert">
          <p>{error}</p>
          <button className="btn" type="button" onClick={() => recargar()} style={{ marginTop: '8px' }}>
            Reintentar consulta
          </button>
        </div>
      )}

      {/* 2. Resumen de Deuda (KPI Cards) */}
      {cargando && (
        <div className="portal-kpi-grid">
          <div className="pcard skeleton-kpi skeleton-shimmer" />
          <div className="pcard skeleton-kpi skeleton-shimmer" />
          <div className="pcard skeleton-kpi skeleton-shimmer" />
        </div>
      )}

      {!cargando && cuenta && (
        <section className="portal-kpi-grid" aria-label="Resumen de Estados de Cuenta">
          {/* Card 1: Deuda Total Vigente */}
          <div className="portal-kpi-card deuda-total">
            <div className="portal-kpi-cab">
              <span className="kpi-etiqueta">Deuda Total Vigente</span>
              <div className="kpi-icono" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
              </div>
            </div>
            <div>
              <p className="kpi-monto">{moneda(totalDeudaVigente)}</p>
              <p className="kpi-detalle">
                {cuotasPendientes.length > 0
                  ? `${cuotasPendientes.length} cuota(s) pendiente(s) de cancelación`
                  : 'Sin deudas tributarias pendientes'}
              </p>
            </div>
          </div>

          {/* Card 2: Total Pagado */}
          <div className="portal-kpi-card pagado-total">
            <div className="portal-kpi-cab">
              <span className="kpi-etiqueta">Total Pagado</span>
              <div className="kpi-icono" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                  <polyline points="22 4 12 14.01 9 11.01" />
                </svg>
              </div>
            </div>
            <div>
              <p className="kpi-monto">{moneda(totalPagado)}</p>
              <p className="kpi-detalle">Suma de pagos regularizados en el ejercicio</p>
            </div>
          </div>

          {/* Card 3: Botonera de Acción Rápida (HU022 Pago en Línea) */}
          <div className="portal-kpi-card accion-pago">
            <div className="portal-kpi-cab">
              <span className="kpi-etiqueta">Pago de Obligaciones</span>
              <div className="kpi-icono" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
                  <line x1="1" y1="10" x2="23" y2="10" />
                </svg>
              </div>
            </div>
            <div>
              <p className="kpi-monto">
                {totalDeudaVigente > 0 ? moneda(totalDeudaVigente) : 'S/ 0.00'}
              </p>
              <p className="kpi-detalle">
                {totalVencido > 0
                  ? `Incluye ${cantidadVencidas} cuota(s) vencida(s) (${moneda(totalVencido)})`
                  : 'Sin obligaciones vencidas al corte'}
              </p>
            </div>
            <button
              className="btn-pagar-principal"
              type="button"
              disabled={totalDeudaVigente <= 0}
              onClick={() => {
                seleccionarTodasPendientes()
                setModalPagoAbierto(true)
              }}
              title={totalDeudaVigente > 0 ? 'Pagar todas las cuotas pendientes' : 'No registra deudas pendientes'}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
                <line x1="1" y1="10" x2="23" y2="10" />
              </svg>
              {totalDeudaVigente > 0 ? 'Ir a Pagar' : 'Cuenta al día'}
            </button>
          </div>
        </section>
      )}

      {/* 3. Filtros y Organización en Pestañas (RF-018 / RF-004) */}
      {!cargando && cuenta && (
        <section className="portal-controles-card" aria-label="Filtros y navegación de categorías">
          {/* Tabs por Categoría */}
          <div className="portal-tabs-nav" role="tablist" aria-label="Categorías de obligaciones">
            {PESTANAS.map((tab, idx) => (
              <button
                key={tab.id}
                id={`tab-${tab.id}`}
                role="tab"
                aria-selected={tabActiva === tab.id}
                aria-controls={`panel-${tab.id}`}
                tabIndex={tabActiva === tab.id ? 0 : -1}
                className={`portal-tab-btn ${tabActiva === tab.id ? 'activo' : ''}`}
                onClick={() => setTabActiva(tab.id)}
                onKeyDown={(e) => manejarTecladoTabs(e, idx)}
              >
                <span>{tab.nombre}</span>
                {conteoPorCategoria[tab.id] !== undefined && (
                  <span className="tab-badge-contador">{conteoPorCategoria[tab.id]}</span>
                )}
                {tab.id === 'HISTORIAL' && (
                  <span className="tab-badge-contador">{historialPagos.length}</span>
                )}
              </button>
            ))}
          </div>

          {/* Filtros secundarios: Año y Estado */}
          {tabActiva !== 'HISTORIAL' && (
            <div className="portal-filtros-fila">
              <div className="portal-filtros-selects">
                <div className="filtro-item">
                  <label htmlFor={idAnioFiltro}>Periodo / Año:</label>
                  <select
                    id={idAnioFiltro}
                    value={anioSeleccionado}
                    onChange={(e) => setAnioSeleccionado(e.target.value)}
                  >
                    <option value="TODOS">Todos los años</option>
                    {aniosDisponibles.map((a) => (
                      <option key={a} value={a}>{a}</option>
                    ))}
                  </select>
                </div>

                <div className="filtro-item">
                  <label htmlFor={idEstadoFiltro}>Estado de Pago:</label>
                  <select
                    id={idEstadoFiltro}
                    value={estadoFiltro}
                    onChange={(e) => setEstadoFiltro(e.target.value)}
                  >
                    <option value="TODOS">Todos los estados</option>
                    <option value="PENDIENTES_VENCIDAS">Solo Pendientes y Vencidas</option>
                    <option value="PAGADA">Solo Pagadas</option>
                  </select>
                </div>
              </div>

              <div className="portal-filtros-acciones">
                {cuotasPendientes.length > 0 && (
                  <button
                    className="btn"
                    type="button"
                    onClick={seleccionadas.size === cuotasPendientes.length ? limpiarSeleccion : seleccionarTodasPendientes}
                  >
                    {seleccionadas.size === cuotasPendientes.length ? 'Deseleccionar todo' : 'Seleccionar pendientes'}
                  </button>
                )}
                <button
                  className="btn"
                  type="button"
                  onClick={() => recargar()}
                  title="Actualizar información"
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.19" />
                  </svg>
                  Actualizar
                </button>
              </div>
            </div>
          )}
        </section>
      )}

      {/* 4. Tabla de Obligaciones Tributarias o Historial de Pagos */}
      {cargando && (
        <div className="pcard skeleton-tabla skeleton-shimmer" aria-busy="true" aria-label="Cargando tabla de obligaciones..." />
      )}

      {!cargando && cuenta && tabActiva !== 'HISTORIAL' && (
        <section
          id={`panel-${tabActiva}`}
          role="tabpanel"
          aria-labelledby={`tab-${tabActiva}`}
          className="portal-tabla-contenedor"
        >
          <div className="portal-tabla-cab">
            <h3>
              {tabActiva === 'TODOS' && 'Desglose General de Obligaciones'}
              {tabActiva === 'PREDIAL' && 'Detalle de Impuesto Predial por Trimestre'}
              {tabActiva === 'ARBITRIOS' && 'Detalle de Arbitrios Municipales (Limpieza, Parques y Seguridad)'}
              {tabActiva === 'MULTAS' && 'Multas y Sanciones Administrativas'}
            </h3>
            <span className="hint">
              Mostrando {filasFiltradas.length} registro(s)
            </span>
          </div>

          {/* Aviso especial para multas según RF-018 */}
          {tabActiva === 'MULTAS' && (
            <div className="multas-aviso-banner" role="note">
              <strong>Nota legal (RF-018):</strong> Las multas tributarias, administrativas y gastos de emisión se
              presentan de forma independiente a los tributos municipales (Impuesto Predial y Arbitrios) para garantizar la
              transparencia en la liquidación de la deuda.
            </div>
          )}

          {filasFiltradas.length === 0 ? (
            <div className="portal-empty-state" role="status">
              <div className="portal-empty-icono" aria-hidden="true">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                  <polyline points="22 4 12 14.01 9 11.01" />
                </svg>
              </div>
              <h3>No se encontraron obligaciones con los filtros aplicados</h3>
              <p>No registras deudas pendientes bajo la categoría o periodo seleccionado.</p>
            </div>
          ) : (
            <div className="tabla-scroll-portal" tabIndex={0} role="region" aria-label="Tabla de obligaciones tributarias">
              <table className="tabla-portal">
                <thead>
                  <tr>
                    <th scope="col" className="col-checkbox">
                      <span className="sr-only">Seleccionar</span>
                    </th>
                    <th scope="col">Tributo / Concepto</th>
                    <th scope="col">Año / Cuota</th>
                    <th scope="col">Vencimiento</th>
                    <th scope="col" className="num">Emitido (S/)</th>
                    <th scope="col" className="num">Descuento (S/)</th>
                    <th scope="col" className="num">A Pagar (S/)</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Etapa Cobranza</th>
                  </tr>
                </thead>
                <tbody>
                  {filasFiltradas.map((f) => {
                    const pagada = f.estado === 'Pagada'
                    const seleccionada = seleccionadas.has(f.id)
                    const etapaInfo = f.etapaCobranza
                      ? Object.values(ETAPAS_COBRANZA_INFO).find((e) => e.nombre.toLowerCase() === f.etapaCobranza.toLowerCase())
                      : null

                    return (
                      <tr key={f.id} className={seleccionada ? 'seleccionada' : ''}>
                        <td className="col-checkbox">
                          {!pagada ? (
                            <input
                              type="checkbox"
                              aria-label={`Seleccionar ${f.tributo} ${f.periodo} para pago`}
                              checked={seleccionada}
                              onChange={() => alternarSeleccion(f.id)}
                            />
                          ) : (
                            <span aria-hidden="true" style={{ color: 'var(--exito)' }}>✓</span>
                          )}
                        </td>
                        <td>
                          <strong>{f.tributo}</strong>
                        </td>
                        <td>
                          <span>{f.anio} · {f.cuota}</span>
                        </td>
                        <td>
                          {f.fechaVencimiento ? fecha(f.fechaVencimiento) : f.periodo}
                        </td>
                        <td className="num">{moneda(f.emitido)}</td>
                        <td className="num" style={{ color: f.descuento > 0 ? 'var(--exito)' : 'inherit' }}>
                          {f.descuento > 0 ? `-${moneda(f.descuento)}` : 'S/ 0.00'}
                        </td>
                        <td className="num">
                          <strong>{moneda(f.montoPagar)}</strong>
                        </td>
                        <td>
                          <span className={`badge-estado ${f.estado.toLowerCase()}`}>
                            {f.estado === 'Pagada' && '✓ Pagada'}
                            {f.estado === 'Vencida' && '! Vencida'}
                            {f.estado === 'Pendiente' && '… Pendiente'}
                          </span>
                        </td>
                        <td>
                          {f.estado === 'Vencida' && f.etapaCobranza ? (
                            <span
                              className={`badge-etapa ${f.etapaCobranza.toLowerCase().includes('coactiva') ? 'coactiva' : ''}`}
                              title={etapaInfo?.descripcion || 'Etapa del procedimiento de cobranza'}
                            >
                              {f.etapaCobranza}
                            </span>
                          ) : (
                            <span className="hint">—</span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={4}><strong>Total Filtro</strong></td>
                    <td className="num"><strong>{moneda(filasFiltradas.reduce((a, f) => a + f.emitido, 0))}</strong></td>
                    <td className="num" style={{ color: 'var(--exito)' }}>
                      <strong>{moneda(filasFiltradas.reduce((a, f) => a + f.descuento, 0))}</strong>
                    </td>
                    <td className="num">
                      <strong>{moneda(filasFiltradas.reduce((a, f) => a + f.montoPagar, 0))}</strong>
                    </td>
                    <td colSpan={2} />
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </section>
      )}

      {/* 5. Pestaña de Historial de Pagos Realizados (RF-062) */}
      {!cargando && cuenta && tabActiva === 'HISTORIAL' && (
        <section
          id="panel-HISTORIAL"
          role="tabpanel"
          aria-labelledby="tab-HISTORIAL"
          className="portal-tabla-contenedor"
        >
          <div className="portal-tabla-cab">
            <h3>Historial de Pagos Realizados (RF-062)</h3>
            <span className="hint">Pagos registrados en ventanilla y pasarela en línea</span>
          </div>

          {historialPagos.length === 0 ? (
            <div className="portal-empty-state" role="status">
              <div className="portal-empty-icono" aria-hidden="true">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
                  <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
                </svg>
              </div>
              <h3>No registras pagos en el historial</h3>
              <p>Los pagos realizados a través de la pasarela o en ventanilla municipal aparecerán aquí.</p>
            </div>
          ) : (
            <div className="tabla-scroll-portal" tabIndex={0} role="region" aria-label="Historial de pagos del contribuyente">
              <table className="tabla-portal">
                <thead>
                  <tr>
                    <th scope="col">Fecha de Pago</th>
                    <th scope="col">Canal</th>
                    <th scope="col">Concepto / Periodo</th>
                    <th scope="col">N.º Operación / Recibo</th>
                    <th scope="col" className="num">Beneficio Aplicado (S/)</th>
                    <th scope="col" className="num">Monto Pagado (S/)</th>
                  </tr>
                </thead>
                <tbody>
                  {historialPagos.map((p, idx) => (
                    <tr key={p.idPago || idx}>
                      <td>{fecha(p.fechaPago)}</td>
                      <td>
                        <span className="canal-badge">
                          {p.canal === 'En línea' ? '🌐 En línea' : '🏛️ Ventanilla'}
                        </span>
                      </td>
                      <td>
                        <strong>{p.concepto}</strong>
                      </td>
                      <td>
                        <span className="recibo-badge">{p.recibo || p.idPago}</span>
                      </td>
                      <td className="num" style={{ color: p.beneficioAplicado > 0 ? 'var(--exito)' : 'inherit' }}>
                        {p.beneficioAplicado > 0 ? moneda(p.beneficioAplicado) : 'S/ 0.00'}
                      </td>
                      <td className="num">
                        <strong style={{ color: 'var(--exito)' }}>{moneda(p.montoPagado)}</strong>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={4}><strong>Total Pagado en Historial</strong></td>
                    <td className="num" style={{ color: 'var(--exito)' }}>
                      <strong>{moneda(historialPagos.reduce((a, p) => a + (p.beneficioAplicado || 0), 0))}</strong>
                    </td>
                    <td className="num">
                      <strong style={{ color: 'var(--exito)' }}>
                        {moneda(historialPagos.reduce((a, p) => a + p.montoPagado, 0))}
                      </strong>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Barra de Pago Flotante cuando hay cuotas seleccionadas (HU022) */}
      {seleccionadas.size > 0 && (
        <aside className="barra-pago-flotante" role="region" aria-label="Resumen de pago seleccionado">
          <div className="barra-pago-info">
            <span className="barra-pago-conteo">
              {seleccionadas.size} obligación(es) seleccionada(s)
            </span>
            <div>
              <span style={{ fontSize: '13px', opacity: 0.85, display: 'block' }}>Total a pagar:</span>
              <span className="barra-pago-monto">{moneda(totalAPagarSeleccion)}</span>
            </div>
          </div>
          <div className="barra-pago-acciones">
            <button className="btn-deseleccionar" type="button" onClick={limpiarSeleccion}>
              Limpiar selección
            </button>
            <button
              className="btn-pagar-seleccion"
              type="button"
              onClick={() => setModalPagoAbierto(true)}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
                <line x1="1" y1="10" x2="23" y2="10" />
              </svg>
              Continuar al Pago
            </button>
          </div>
        </aside>
      )}

      {/* Modal de Pago en Línea (HU022) */}
      {modalPagoAbierto && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="modal-pago-titulo">
          <div className="modal-pago">
            <div className="modal-pago-cab">
              <h2 id="modal-pago-titulo">Pasarela de Pago de Obligaciones (HU022)</h2>
              <button
                type="button"
                className="btn-cerrar-modal"
                onClick={() => setModalPagoAbierto(false)}
                aria-label="Cerrar ventana de pago"
              >
                ✕
              </button>
            </div>

            <div className="modal-pago-cuerpo">
              <p className="hint">
                Contribuyente: <strong>{cuenta?.contribuyente.nombre}</strong> ({cuenta?.contribuyente.tipoDoc} {cuenta?.contribuyente.doc})
              </p>

              <div className="modal-pasarela-preview">
                <h4 style={{ margin: 0, color: 'var(--marino-osc)' }}>Resumen de la orden de pago:</h4>
                <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '14px', color: 'var(--texto-secundario)' }}>
                  {filasSeleccionadas.map((f) => (
                    <li key={f.id} style={{ marginBottom: '4px' }}>
                      <strong>{f.tributo}</strong> ({f.periodo}): {moneda(f.montoPagar)}
                    </li>
                  ))}
                </ul>

                <div className="modal-total-linea">
                  <span>Subtotal insoluto:</span>
                  <span>{moneda(filasSeleccionadas.reduce((a, f) => a + f.deuda, 0))}</span>
                </div>
                <div className="modal-total-linea" style={{ color: 'var(--exito)' }}>
                  <span>Descuento / beneficio aplicado:</span>
                  <span>-{moneda(filasSeleccionadas.reduce((a, f) => a + f.descuento, 0))}</span>
                </div>
                <div className="modal-total-linea total-final">
                  <span>Monto Total a Pagar:</span>
                  <span>{moneda(totalAPagarSeleccion)}</span>
                </div>
              </div>

              <div className="info" role="note">
                <strong>Integración segura con Niubiz:</strong> El pago se registra atómicamente contra SAMNET
                y emite comprobante oficial electrónico con valor tributario municipal.
              </div>
            </div>

            <div className="modal-pago-pie">
              <button
                type="button"
                className="btn"
                onClick={() => setModalPagoAbierto(false)}
                disabled={procesandoPago}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="btn primary"
                onClick={procederPago}
                disabled={procesandoPago || seleccionadas.size === 0}
              >
                {procesandoPago ? 'Procesando en pasarela…' : `Confirmar y Pagar ${moneda(totalAPagarSeleccion)}`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Alerta de Éxito de Pago */}
      {pagoCompletado && (
        <aside className="confirmacion" role="status" style={{ marginTop: '16px' }}>
          <strong>¡Pago confirmado exitosamente! </strong>
          Se cancelaron {pagoCompletado.cantidad} obligación(es) por un total de {moneda(pagoCompletado.monto)}.
          Número de operación: <code>WEB-{pagoCompletado.idTransaccion}</code>. Tu estado de cuenta ha sido actualizado.
        </aside>
      )}
    </div>
  )
}
