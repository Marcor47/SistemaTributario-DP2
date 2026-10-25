import { useCallback, useEffect, useRef, useState } from 'react'
import { etiquetaTributo, tieneAlertaTotal } from '../mocks/morosidad.js'
import { obtenerMorosidad } from '../services/morosidadService.js'
import { fecha, moneda } from '../utils/formato.js'

/* ─── Constantes ─────────────────────────────────────────────── */
const ANIO_ACTUAL = new Date().getFullYear()
const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']
const ANIOS = Array.from({ length: 5 }, (_, i) => ANIO_ACTUAL - i)

function porcentaje(valor) {
  if (valor === null || valor === undefined) return '—'
  return `${Number(valor).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} %`
}

function claseSemaforo(indice, meta) {
  if (meta === null || meta === undefined) return ''
  if (indice > meta * 1.1) return 'critico'
  if (indice > meta) return 'aviso'
  return 'ok'
}

/* ─── Barra SVG inline ───────────────────────────────────────── */
function BarraSVG({ filas, colorPrincipal = '#35497D', colorAlerta = '#B42318', meta }) {
  if (!filas.length) return null
  const maximo = Math.max(...filas.map((f) => f.indice), meta ?? 0, 1)
  const anchoBarras = 540
  const alturaFila = 40
  const paddingIzq = 180
  const paddingDer = 60
  const svgAncho = paddingIzq + anchoBarras + paddingDer
  const svgAlto = filas.length * alturaFila + 32

  return (
    <svg
      viewBox={`0 0 ${svgAncho} ${svgAlto}`}
      aria-hidden="true"
      style={{ width: '100%', maxWidth: svgAncho, display: 'block', overflow: 'visible' }}
    >
      {/* Línea de meta */}
      {meta !== null && meta !== undefined && (
        <>
          <line
            x1={paddingIzq + (meta / maximo) * anchoBarras}
            y1={8}
            x2={paddingIzq + (meta / maximo) * anchoBarras}
            y2={svgAlto - 8}
            stroke="#067647" strokeWidth="1.5" strokeDasharray="6,3"
          />
          <text
            x={paddingIzq + (meta / maximo) * anchoBarras + 4}
            y={16}
            fontSize="11" fill="#067647" fontWeight="700"
          >
            Meta {porcentaje(meta)}
          </text>
        </>
      )}

      {filas.map((fila, i) => {
        const ancho = (fila.indice / maximo) * anchoBarras
        const y = i * alturaFila + 16
        const color = meta && fila.indice > meta * 1.1 ? colorAlerta : colorPrincipal
        return (
          <g key={fila.valorAlcance}>
            <text x={paddingIzq - 8} y={y + 13} fontSize="12.5" fill="#3A4A5F" textAnchor="end" dominantBaseline="auto">
              {fila.etiqueta ?? fila.valorAlcance}
            </text>
            <rect x={paddingIzq} y={y} width={Math.max(ancho, 2)} height={24} rx="5" fill={color} opacity="0.85" />
            <text x={paddingIzq + ancho + 6} y={y + 14} fontSize="11.5" fill={color} fontWeight="700">
              {porcentaje(fila.indice)}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

/* ─── Cabecera del lote ETL ──────────────────────────────────── */
function CabeceraLote({ lote, cargando }) {
  if (cargando) return (
    <div className="pcard morosidad-lote-header" aria-busy="true">
      <p className="hint">Cargando información del lote ETL…</p>
    </div>
  )
  if (!lote) return null
  return (
    <div className="pcard morosidad-lote-header">
      <div className="morosidad-lote-grid">
        <div>
          <span className="pl">Última carga del ETL</span>
          <strong className="pv">{lote.periodo}</strong>
        </div>
        <div>
          <span className="pl">Fecha de corte</span>
          <strong className="pv">{fecha(lote.fechaCorte)}</strong>
        </div>
        <div>
          <span className="pl">ID de lote</span>
          <strong className="pv">#{lote.idLote}</strong>
        </div>
        <div>
          <span className="pl">Datos al</span>
          <strong className="pv">{lote.anio}</strong>
        </div>
      </div>
    </div>
  )
}

/* ─── Tarjetas KPI ───────────────────────────────────────────── */
function KpiCards({ datos, cargando }) {
  if (cargando) {
    return (
      <div className="morosidad-kpi-grid">
        {[1, 2, 3].map((n) => (
          <div key={n} className="pcard morosidad-kpi-card" aria-busy="true">
            <div className="morosidad-skeleton" style={{ height: 24, width: '60%', marginBottom: 8 }} />
            <div className="morosidad-skeleton" style={{ height: 48, width: '80%' }} />
          </div>
        ))}
      </div>
    )
  }
  if (!datos) return null

  const hayAlerta = tieneAlertaTotal(datos)
  const desvio = datos.meta ? ((datos.total - datos.meta) / datos.meta) * 100 : null

  return (
    <div className="morosidad-kpi-grid">
      {/* Card principal: índice total */}
      <div className={`pcard morosidad-kpi-card morosidad-kpi-principal ${claseSemaforo(datos.total, datos.meta)}`}>
        <span className="pl">Índice de Morosidad Total</span>
        <strong
          className="morosidad-kpi-valor"
          aria-label={`Índice de morosidad total: ${porcentaje(datos.total)}`}
        >
          {porcentaje(datos.total)}
        </strong>
        {hayAlerta && (
          <span className="estado critico morosidad-badge-alerta" role="alert" aria-live="polite">
            ⚠ Alerta de Desvío
          </span>
        )}
        {datos.meta !== null && (
          <span className="hint">
            Meta: {porcentaje(datos.meta)}
            {desvio !== null && (
              <> · Desvío: <strong style={{ color: desvio > 0 ? 'var(--critico)' : 'var(--exito)' }}>{desvio > 0 ? '+' : ''}{porcentaje(desvio)}</strong></>
            )}
          </span>
        )}
      </div>

      {/* Card: monto emitido */}
      <div className="pcard morosidad-kpi-card">
        <span className="pl">Total Emitido</span>
        <strong className="morosidad-kpi-valor morosidad-kpi-secundario">
          {datos.montoEmitidoTotal !== null ? moneda(datos.montoEmitidoTotal) : '—'}
        </strong>
        <span className="hint">Monto emitido en el periodo</span>
      </div>

      {/* Card: monto vencido no pagado */}
      <div className="pcard morosidad-kpi-card">
        <span className="pl">Total Vencido No Pagado</span>
        <strong className="morosidad-kpi-valor" style={{ color: 'var(--critico)' }}>
          {datos.montoVencidoTotal !== null ? moneda(datos.montoVencidoTotal) : '—'}
        </strong>
        <span className="hint">
          Cuotas vencidas exigibles sin pagar
          {datos.umbral !== null && (
            <> · Umbral de alerta: {porcentaje(datos.umbral)}</>
          )}
        </span>
      </div>
    </div>
  )
}

/* ─── Sección gráfica + tabla (por tributo o por zona) ───────── */
function SeccionDesglose({ titulo, filas, meta, vistaTabla, onToggle, idBtnToggle, idTabla, idGrafico }) {
  const filasConEtiqueta = filas.map((f) => ({
    ...f,
    etiqueta: etiquetaTributo(f.valorAlcance),
  }))

  return (
    <div className="pcard">
      <div className="morosidad-seccion-cab">
        <h2 id={`${idGrafico}-titulo`}>{titulo}</h2>
        <button
          id={idBtnToggle}
          type="button"
          className="btn btn-compacto"
          onClick={onToggle}
          aria-pressed={vistaTabla}
          aria-controls={vistaTabla ? idTabla : idGrafico}
        >
          {vistaTabla ? 'Ver gráfico' : 'Ver tabla'}
        </button>
      </div>

      {vistaTabla ? (
        <div className="tabla-scroll" role="region" aria-labelledby={`${idGrafico}-titulo`} tabIndex={0} id={idTabla}>
          <table className="tabla">
            <caption className="sr-only">{titulo} — vista de tabla</caption>
            <thead>
              <tr>
                <th scope="col">Concepto</th>
                <th scope="col" className="num">Monto Emitido (S/)</th>
                <th scope="col" className="num">Monto Vencido (S/)</th>
                <th scope="col" className="num">% Morosidad</th>
                <th scope="col" className="num">Meta (%)</th>
                <th scope="col">Estado</th>
              </tr>
            </thead>
            <tbody>
              {filasConEtiqueta.map((fila) => {
                const cls = claseSemaforo(fila.indice, meta)
                return (
                  <tr key={fila.valorAlcance}>
                    <td>{fila.etiqueta}</td>
                    <td className="num">{fila.montoEmitido !== null ? moneda(fila.montoEmitido) : '—'}</td>
                    <td className="num">{fila.montoVencido !== null ? moneda(fila.montoVencido) : '—'}</td>
                    <td className="num"><strong>{porcentaje(fila.indice)}</strong></td>
                    <td className="num">{meta !== null ? porcentaje(meta) : '—'}</td>
                    <td>
                      {cls && (
                        <span className={`estado ${cls}`}>
                          {cls === 'ok' ? 'Dentro de meta' : cls === 'aviso' ? 'Con desvío' : 'Alerta'}
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div id={idGrafico} aria-labelledby={`${idGrafico}-titulo`}>
          <BarraSVG filas={filasConEtiqueta} meta={meta} />
        </div>
      )}
    </div>
  )
}

/* ─── Tabla de detalle completo ──────────────────────────────── */
function TablaDetalle({ datos, orden, onOrdenar }) {
  if (!datos) return null

  const filas = [
    { alcance: 'TOTAL', concepto: 'Total institucional', indice: datos.total, montoEmitido: datos.montoEmitidoTotal, montoVencido: datos.montoVencidoTotal, meta: datos.meta },
    ...datos.porTributo.map((f) => ({ alcance: 'TRIBUTO', concepto: etiquetaTributo(f.valorAlcance), indice: f.indice, montoEmitido: f.montoEmitido, montoVencido: f.montoVencido, meta: datos.meta })),
    ...datos.porZona.map((f) => ({ alcance: 'ZONA', concepto: f.valorAlcance, indice: f.indice, montoEmitido: f.montoEmitido, montoVencido: f.montoVencido, meta: datos.meta })),
  ]

  const ordenadas = [...filas].sort((a, b) => {
    let va = a[orden.campo]
    let vb = b[orden.campo]
    if (va === null || va === undefined) va = -Infinity
    if (vb === null || vb === undefined) vb = -Infinity
    if (typeof va === 'string') return orden.asc ? va.localeCompare(vb) : vb.localeCompare(va)
    return orden.asc ? va - vb : vb - va
  })

  function Th({ campo, children }) {
    const activo = orden.campo === campo
    return (
      <th
        scope="col"
        className={`tabla-th-orden num ${activo ? 'activo' : ''}`}
        aria-sort={activo ? (orden.asc ? 'ascending' : 'descending') : 'none'}
      >
        <button type="button" className="btn-ordenar" onClick={() => onOrdenar(campo)}>
          {children}
          <span aria-hidden="true" className="orden-icono">
            {activo ? (orden.asc ? ' ▲' : ' ▼') : ' ⇅'}
          </span>
        </button>
      </th>
    )
  }

  return (
    <div className="pcard">
      <h2>Detalle completo</h2>
      <div className="tabla-scroll" role="region" aria-label="Detalle completo de morosidad" tabIndex={0} id="tabla-detalle-morosidad">
        <table className="tabla tabla-morosidad-detalle">
          <caption className="sr-only">Índice de morosidad: total, por tributo y por zona</caption>
          <thead>
            <tr>
              <th scope="col">Alcance</th>
              <th scope="col">Concepto</th>
              <Th campo="montoEmitido">Emitido (S/)</Th>
              <Th campo="montoVencido">Vencido (S/)</Th>
              <Th campo="indice">% Morosidad</Th>
              <th scope="col" className="num">Meta (%)</th>
              <th scope="col">Estado / Alerta</th>
            </tr>
          </thead>
          <tbody>
            {ordenadas.map((fila, i) => {
              const cls = claseSemaforo(fila.indice, fila.meta)
              return (
                <tr key={i} className={fila.alcance === 'TOTAL' ? 'fila-total' : ''}>
                  <td><span className={`estado ${fila.alcance === 'TOTAL' ? 'neutro' : fila.alcance === 'TRIBUTO' ? 'aviso' : 'ok'}`}>{fila.alcance}</span></td>
                  <td><strong>{fila.concepto}</strong></td>
                  <td className="num">{fila.montoEmitido !== null ? moneda(fila.montoEmitido) : '—'}</td>
                  <td className="num">{fila.montoVencido !== null ? moneda(fila.montoVencido) : '—'}</td>
                  <td className="num"><strong style={{ color: fila.indice > (fila.meta ?? Infinity) ? 'var(--critico)' : 'var(--exito)' }}>{porcentaje(fila.indice)}</strong></td>
                  <td className="num">{fila.meta !== null ? porcentaje(fila.meta) : '—'}</td>
                  <td>
                    {cls && (
                      <span className={`estado ${cls}`}>
                        {cls === 'ok' ? 'Dentro de meta' : cls === 'aviso' ? 'Con desvío' : '⚠ Alerta'}
                      </span>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/* ─── Filtros de periodo ─────────────────────────────────────── */
function FiltrosPeriodo({ anio, mes, onAnio, onMes, ocupado }) {
  return (
    <div className="filters morosidad-filtros">
      <div className="fld">
        <label htmlFor="filtro-anio-morosidad">Año</label>
        <select id="filtro-anio-morosidad" value={anio} onChange={(e) => onAnio(Number(e.target.value))} disabled={ocupado}>
          {ANIOS.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
      </div>
      <div className="fld">
        <label htmlFor="filtro-mes-morosidad">Mes</label>
        <select id="filtro-mes-morosidad" value={mes} onChange={(e) => onMes(Number(e.target.value))} disabled={ocupado}>
          <option value="">Último disponible</option>
          {MESES.map((m, i) => <option key={i + 1} value={i + 1}>{m}</option>)}
        </select>
      </div>
    </div>
  )
}

/* ─── Página principal ───────────────────────────────────────── */
export default function IndiceMorosidad() {
  const [datos, setDatos] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [anio, setAnio] = useState(ANIO_ACTUAL)
  const [mes, setMes] = useState('')
  const [vistaTablaTributo, setVistaTablaTributo] = useState(false)
  const [vistaTablaZona, setVistaTablaZona] = useState(false)
  const [orden, setOrden] = useState({ campo: 'alcance', asc: true })
  const solicitud = useRef(0)

  const cargar = useCallback(async () => {
    const actual = ++solicitud.current
    setCargando(true)
    setError(null)
    try {
      const resultado = await obtenerMorosidad(anio, mes || undefined)
      if (actual === solicitud.current) setDatos(resultado)
    } catch (err) {
      if (actual === solicitud.current)
        setError(err.mensaje ?? err.message ?? 'No se pudo cargar el índice de morosidad.')
    } finally {
      if (actual === solicitud.current) setCargando(false)
    }
  }, [anio, mes])

  useEffect(() => { cargar() }, [cargar])

  function ordenarPor(campo) {
    setOrden((actual) => ({ campo, asc: actual.campo === campo ? !actual.asc : true }))
  }

  return (
    <div aria-busy={cargando}>
      {/* Filtros */}
      <FiltrosPeriodo
        anio={anio} mes={mes}
        onAnio={setAnio} onMes={setMes}
        ocupado={cargando}
      />

      {/* Error */}
      {error && (
        <div className="alert" role="alert">
          <strong>{error}</strong>{' '}
          <button type="button" className="btn btn-compacto" onClick={cargar} disabled={cargando}>
            Reintentar
          </button>
        </div>
      )}

      {/* Estado vacío (sin carga ETL) */}
      {!cargando && !error && !datos && (
        <div className="info" role="status">
          No hay cargas del ETL publicadas para el periodo seleccionado. Ajusta los filtros o espera a que el Responsable de datos ejecute el proceso ETL (HU025).
        </div>
      )}

      {/* Cabecera del lote ETL */}
      <CabeceraLote lote={datos?.lote} cargando={cargando} />

      {/* Tarjetas KPI */}
      <KpiCards datos={datos} cargando={cargando} />

      {/* Gráfico / tabla por Tributo */}
      {(datos?.porTributo?.length > 0 || cargando) && (
        cargando ? (
          <div className="pcard" aria-busy="true"><div className="morosidad-skeleton" style={{ height: 180 }} /></div>
        ) : (
          <SeccionDesglose
            titulo="Morosidad por Tributo"
            filas={datos.porTributo}
            meta={datos.meta}
            vistaTabla={vistaTablaTributo}
            onToggle={() => setVistaTablaTributo((v) => !v)}
            idBtnToggle="btn-toggle-tributo"
            idTabla="tabla-morosidad-tributo"
            idGrafico="grafico-morosidad-tributo"
          />
        )
      )}

      {/* Gráfico / tabla por Zona */}
      {(datos?.porZona?.length > 0 || cargando) && (
        cargando ? (
          <div className="pcard" aria-busy="true"><div className="morosidad-skeleton" style={{ height: 200 }} /></div>
        ) : (
          <SeccionDesglose
            titulo="Morosidad por Zona Territorial"
            filas={datos.porZona}
            meta={datos.meta}
            vistaTabla={vistaTablaZona}
            onToggle={() => setVistaTablaZona((v) => !v)}
            idBtnToggle="btn-toggle-zona"
            idTabla="tabla-morosidad-zona"
            idGrafico="grafico-morosidad-zona"
          />
        )
      )}

      {/* Tabla de detalle completo */}
      {!cargando && datos && (
        <TablaDetalle datos={datos} orden={orden} onOrdenar={ordenarPor} />
      )}

      {/* Nota de fórmula */}
      {!cargando && datos && (
        <p className="hint formula-regularidad">
          Fórmula: Índice de Morosidad = (Monto cuotas vencidas no pagadas ÷ Monto emitido) × 100.
          Se consideran las cuotas del año de la fecha de corte que ya vencieron (exigibles).
          La alerta de desvío se activa cuando el desvío respecto a la meta supera el umbral configurado ({datos.umbral !== null ? `${datos.umbral?.toFixed(0)} %` : '10 %'}).
        </p>
      )}
    </div>
  )
}
