import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import CardsNumericos from '../components/dashboard/CardsNumericos.jsx'
import PanelAlertasDesvio from '../components/dashboard/PanelAlertasDesvio.jsx'
import SkeletonAlertas from '../components/dashboard/SkeletonAlertas.jsx'
import { useAlertasDesvio } from '../hooks/useAlertasDesvio.js'
import { obtenerDashboard, obtenerFiltrosDashboard } from '../services/dashboardService.js'
import { listarIndicadores } from '../services/metaService.js'
import { useSesionInterna } from '../context/sesionInterna.js'
import { fecha, fechaHora, moneda } from '../utils/formato.js'
import { formatearPorcentaje, formatearValorIndicador } from '../utils/alertas.js'
import { nivelMorosidad, prepararBarrasSegmentos, resumirSegmentacionPublicada } from '../utils/segmentos.js'
import './Dashboard.css'

const INICIALES = { anio: '', numeroPeriodo: '', zona: '', idSegmento: '', tributo: '' }
const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']

export default function DashboardEjecutivo() {
  const { sesion } = useSesionInterna()
  const [filtros, setFiltros] = useState(INICIALES)
  const [filtroIndicador, setFiltroIndicador] = useState('')
  const [indicadores, setIndicadores] = useState([])
  const [revision, setRevision] = useState(0)
  const [estado, setEstado] = useState({ cargando: true, datos: null, opciones: null, error: null })
  const [alertasAbiertas, setAlertasAbiertas] = useState(true)
  const [metricaSegmento, setMetricaSegmento] = useState('deuda')

  useEffect(() => {
    const controlador = new AbortController()
    Promise.all([
      obtenerDashboard(filtros, { signal: controlador.signal }),
      obtenerFiltrosDashboard({ signal: controlador.signal }),
    ]).then(([datos, opciones]) => {
      if (!controlador.signal.aborted) setEstado({ cargando: false, datos, opciones, error: null })
    }).catch((error) => {
      if (!controlador.signal.aborted) setEstado((anterior) => ({ ...anterior, cargando: false, datos: null, error }))
    })
    return () => controlador.abort()
  }, [filtros, revision])

  useEffect(() => {
    let activo = true
    listarIndicadores()
      .then((resultado) => { if (activo) setIndicadores(Array.isArray(resultado) ? resultado : []) })
      .catch(() => { if (activo) setIndicadores([]) })
    return () => { activo = false }
  }, [])

  const { datos, opciones, cargando, error } = estado
  const resumen = datos?.resumen
  const puedeVerAlertas = Boolean(sesion?.permisos?.includes('ALERTAS:CONSULTAR'))
  const puedeGestionarSegmentos = Boolean(sesion?.permisos?.includes('SEGMENTOS:CONSULTAR'))
  const segmentacionPublicada = resumirSegmentacionPublicada(opciones?.segmentos)
  const barrasSegmento = prepararBarrasSegmentos(datos?.porSegmento, metricaSegmento)
  const alertasDesvio = useAlertasDesvio({
    activo: puedeVerAlertas,
    filtros,
    indicadorCodigo: filtroIndicador,
    periodo: datos?.periodo,
    segmentos: opciones?.segmentos,
    revision,
  })

  function cambiar(campo, valor) {
    setEstado((anterior) => ({ ...anterior, cargando: true, error: null }))
    setFiltros((anterior) => ({ ...anterior, [campo]: valor }))
  }

  function cambiarIndicador(campo, valor) {
    setFiltroIndicador(valor)
  }

  function actualizar(evento) {
    evento?.preventDefault()
    setEstado((anterior) => ({ ...anterior, cargando: true, error: null }))
    setRevision((valor) => valor + 1)
  }

  const metricas = resumen ? [
    { id: 'recaudado', etiqueta: 'Recaudación', valor: moneda(resumen.montoRecaudado), detalle: `${formatearPorcentaje(resumen.porcentajeRecaudacion)} de la emisión` },
    { id: 'emitido', etiqueta: 'Monto emitido', valor: moneda(resumen.montoEmitido), detalle: `Obligaciones exigibles al ${fecha(datos.fechaCorte)}` },
    { id: 'morosidad', etiqueta: 'Índice de morosidad', valor: formatearPorcentaje(resumen.indiceMorosidad), detalle: `${moneda(resumen.montoVencidoNoPagado)} vencidos sin pagar` },
  ] : []

  const metricasAlertas = puedeVerAlertas ? [
    {
      id: 'alertas',
      etiqueta: 'Alertas vigentes',
      valor: alertasDesvio.cargando || alertasDesvio.error ? '—' : alertasDesvio.total,
      detalle: alertasDesvio.error
        ? 'No disponible'
        : alertasDesvio.cargando
          ? 'Consultando alertas…'
          : alertasDesvio.total === 1 ? 'Indicador fuera de meta' : 'Indicadores fuera de meta',
    },
    {
      id: 'indicadores',
      etiqueta: 'Indicadores en desvío',
      valor: alertasDesvio.cargando || alertasDesvio.error ? '—' : alertasDesvio.indicadoresEnDesvio,
      detalle: alertasDesvio.error
        ? 'No disponible'
        : alertasDesvio.cargando ? 'Consultando alertas…' : 'Con meta configurada y umbral superado',
    },
    {
      id: 'corte',
      etiqueta: 'Corte de las alertas',
      valor: alertasDesvio.cargando || alertasDesvio.error ? '—' : alertasDesvio.ultimaCorte ? fecha(alertasDesvio.ultimaCorte) : '—',
      detalle: alertasDesvio.error ? 'No disponible' : 'Fecha de corte de las alertas mostradas',
      variante: 'fecha',
    },
  ] : []

  const metricasCombinadas = [...metricas, ...metricasAlertas]

  return (
    <div className="dashboard" aria-busy={cargando}>
      <div className="dashboard-filtros-sticky">
        <form className="filters dashboard-filtros dashboard-filtros--ejecutivo" onSubmit={actualizar} aria-label="Filtrar dashboard ejecutivo">
          <Filtro campo="anio" titulo="Año" valor={filtros.anio} onCambiar={cambiar}>
            <option value="">Última carga</option>
            {opciones?.anios.map((anio) => <option key={anio} value={anio}>{anio}</option>)}
          </Filtro>
          <Filtro campo="numeroPeriodo" titulo="Periodo" valor={filtros.numeroPeriodo} onCambiar={cambiar}>
            <option value="">Según corte</option>
            {MESES.map((mes, i) => <option key={mes} value={i + 1}>{mes}</option>)}
          </Filtro>
          <Filtro campo="zona" titulo="Zona" valor={filtros.zona} onCambiar={cambiar}>
            <option value="">Todas</option>
            {opciones?.zonas.map((zona) => <option key={zona}>{zona}</option>)}
          </Filtro>
          <Filtro campo="idSegmento" titulo="Segmento" valor={filtros.idSegmento} onCambiar={cambiar}>
            <option value="">Todos</option>
            {opciones?.segmentos.map((s) => <option key={s.idSegmento} value={s.idSegmento}>{s.nombre}</option>)}
          </Filtro>
          <Filtro campo="tributo" titulo="Tributo" valor={filtros.tributo} onCambiar={cambiar}>
            <option value="">Todos</option>
            {opciones?.tributos.map((t) => <option key={t.codigo} value={t.codigo}>{t.descripcion}</option>)}
          </Filtro>
          {puedeVerAlertas && (
            <Filtro campo="indicador" titulo="Indicador" valor={filtroIndicador} onCambiar={cambiarIndicador}>
              <option value="">Todos</option>
              {indicadores.map((indicador) => <option key={indicador.codigo} value={indicador.codigo}>{indicador.nombre}</option>)}
            </Filtro>
          )}
          <button className="btn primary" disabled={cargando}>{cargando ? 'Consultando…' : 'Actualizar'}</button>
        </form>
      </div>

      <div
        className="dashboard-scroll"
        tabIndex={0}
        role="region"
        aria-label="Contenido desplazable del dashboard"
      >
        <div className={`dashboard-cuerpo${puedeVerAlertas && !alertasAbiertas ? ' dashboard-cuerpo--alertas-plegadas' : ''}${puedeVerAlertas ? '' : ' dashboard-cuerpo--sin-alertas'}`}>
          <div className="dashboard-kpis">
            {cargando && <CardsNumericos cargando cantidad={puedeVerAlertas ? 6 : 3} />}
            {!cargando && datos && <>
              <p className="dashboard-nota">Periodo: <strong>{datos.periodo}</strong> · Última carga: <strong>{fecha(datos.fechaCorteCarga)}</strong> · Corte de consulta: <strong>{fecha(datos.fechaCorte)}</strong></p>
              <CardsNumericos metricas={metricasCombinadas} />
              {Number(resumen.montoEmitido) === 0 && <p role="status" className="dashboard-nota">No hay obligaciones exigibles para los filtros seleccionados. Los porcentajes sin base se muestran como «—».</p>}
            </>}
          </div>

          <div className="dashboard-contenido">
            {!cargando && error && <section className="pcard" role="status">
              <h2>{error.codigo === 'SIN_CARGA_ETL' ? 'Aún no hay una carga publicada' : 'No se pudo consultar el dashboard'}</h2>
              <p>{error.message}</p><button className="btn" onClick={actualizar}>Reintentar</button>
            </section>}
            {!cargando && datos && <>
              <div className="dashboard-rejilla">
                <section className="pcard">
                  <h2>Recaudación frente a emisión</h2>
                  <Barras filas={[
                    { nombre: 'Emisión', valor: resumen.montoEmitido },
                    { nombre: 'Recaudación', valor: resumen.montoRecaudado },
                  ]} formato={moneda} />
                </section>
                <section className="pcard">
                  <h2>Avance frente a metas</h2>
                  {datos.avanceMetas.length === 0 && <p className="hint">No hay metas comparables para este periodo y combinación de filtros.</p>}
                  {datos.avanceMetas.map((meta) => <div className="dashboard-meta" key={meta.indicadorCodigo}>
                    <strong>{meta.indicador}</strong>
                    <p>{formatearValorIndicador(meta.valorObservado, meta.unidadMedida)} / meta {formatearValorIndicador(meta.valorMeta, meta.unidadMedida)}</p>
                    <p className="hint">{formatearPorcentaje(meta.porcentajeAvance)} del valor meta · {meta.cumpleMeta == null ? 'Sin valor observado' : meta.cumpleMeta ? 'Meta cumplida' : 'Meta pendiente'}</p>
                    {meta.sentido === 'DESCENDENTE' && <p className="hint">Un valor menor indica mejor resultado.</p>}
                  </div>)}
                </section>
                <section className="pcard">
                  <h2>Recaudación por tributo</h2>
                  <Barras filas={datos.porTributo.map((t) => ({ nombre: t.descripcion, valor: t.montoRecaudado }))} formato={moneda} />
                </section>
              </div>

              <section className="pcard dashboard-segmentos" aria-labelledby="dashboard-segmentos-titulo">
                <div className="dashboard-segmentos__cab">
                  <div>
                    <h2 id="dashboard-segmentos-titulo">Segmentación publicada</h2>
                    {segmentacionPublicada ? (
                      <p className="hint">
                        {segmentacionPublicada.modelo} · {segmentacionPublicada.version}
                        {' · '}
                        {segmentacionPublicada.segmentos} {segmentacionPublicada.segmentos === 1 ? 'segmento' : 'segmentos'}
                        {' · '}
                        {segmentacionPublicada.contribuyentes} {segmentacionPublicada.contribuyentes === 1 ? 'contribuyente' : 'contribuyentes'}
                      </p>
                    ) : (
                      <p className="hint">Aún no hay una segmentación publicada.</p>
                    )}
                    {segmentacionPublicada?.aprobador && (
                      <p className="hint">
                        Aprobada por {segmentacionPublicada.aprobador}
                        {segmentacionPublicada.fechaAprobacion ? ` el ${fechaHora(segmentacionPublicada.fechaAprobacion)}` : ''}.
                      </p>
                    )}
                  </div>
                  {puedeGestionarSegmentos && (
                    <Link className="btn" to="/segmentos">Gestionar segmentaciones</Link>
                  )}
                </div>

                {segmentacionPublicada ? (
                  <>
                    <div className="dashboard-segmentos__control" role="group" aria-label="Métrica del gráfico por segmento">
                      <button
                        type="button"
                        className={metricaSegmento === 'deuda' ? 'activo' : undefined}
                        aria-pressed={metricaSegmento === 'deuda'}
                        onClick={() => setMetricaSegmento('deuda')}
                      >
                        Deuda vencida
                      </button>
                      <button
                        type="button"
                        className={metricaSegmento === 'contribuyentes' ? 'activo' : undefined}
                        aria-pressed={metricaSegmento === 'contribuyentes'}
                        onClick={() => setMetricaSegmento('contribuyentes')}
                      >
                        Contribuyentes
                      </button>
                    </div>
                    <BarrasSegmentos
                      datos={barrasSegmento}
                      onSeleccionar={(idSegmento) => cambiar('idSegmento', String(idSegmento))}
                    />
                    <p className="hint">
                      Color de la morosidad: verde &lt; 5 %, ámbar 5–20 %, rojo &gt; 20 %. Haz clic en una barra para
                      filtrar el dashboard por ese segmento.
                    </p>
                  </>
                ) : (
                  <p className="hint">El filtro «Segmento» aparecerá cuando Gerencia apruebe una segmentación.</p>
                )}
              </section>

              <section className="pcard"><h2>Resultados por zona</h2>
                <div className="dashboard-tabla"><table><thead><tr><th>Zona</th><th>Emitido</th><th>Recaudado</th><th>Recaudación / emisión</th><th>Morosidad</th></tr></thead><tbody>
                  {datos.porZona.map((z) => <tr key={z.zona}><th scope="row">{z.zona}</th><td>{moneda(z.montoEmitido)}</td><td>{moneda(z.montoRecaudado)}</td><td>{formatearPorcentaje(z.porcentajeRecaudacion)}</td><td>{formatearPorcentaje(z.indiceMorosidad)}</td></tr>)}
                </tbody></table></div>
              </section>
            </>}
          </div>

          {puedeVerAlertas && (
            <aside
              className={`dashboard-columna-alertas${alertasAbiertas ? '' : ' dashboard-columna-alertas--plegada'}`}
              aria-label="Alertas de desvío de metas"
            >
              <details
                className="dashboard-alertas-panel"
                open={alertasAbiertas}
                onToggle={(evento) => setAlertasAbiertas(evento.currentTarget.open)}
              >
                <summary className="dashboard-alertas-panel__summary">
                  <span className="dashboard-alertas-panel__toggle" aria-hidden="true">
                    {alertasAbiertas ? '▾' : '▸'}
                  </span>
                  <span>{alertasAbiertas ? 'Alertas de desvío de metas' : 'Alertas'}</span>
                </summary>
                <div className="dashboard-alertas-panel__contenido">
                  {alertasDesvio.cargando && <SkeletonAlertas />}

                  {!alertasDesvio.cargando && alertasDesvio.error && (
                    <div className="alert" role="alert">
                      <strong>No se pudieron consultar las alertas de desvío.</strong>
                      <p>{alertasDesvio.error.message || 'Ocurrió un error inesperado. Intenta nuevamente.'}</p>
                      {alertasDesvio.error.detalles?.length > 0 && (
                        <ul>{alertasDesvio.error.detalles.map((detalle) => <li key={detalle}>{detalle}</li>)}</ul>
                      )}
                      <button className="btn" type="button" onClick={alertasDesvio.recargar}>Reintentar</button>
                    </div>
                  )}

                  {!alertasDesvio.cargando && !alertasDesvio.error && alertasDesvio.total === 0 && (
                    <section className="pcard dashboard-vacio" role="status">
                      <h2>{alertasDesvio.hayAlertas ? 'Sin alertas con estos filtros' : 'Sin alertas de desvío'}</h2>
                      <p>{alertasDesvio.hayAlertas
                        ? 'No hay alertas activas que coincidan con los filtros del dashboard.'
                        : 'No hay alertas de desvío registradas. Cuando una carga publicada deje un indicador fuera de su meta, aparecerá aquí.'}</p>
                      <div className="dashboard-vacio__acciones">
                        <Link className="btn" to="/metas">Revisar metas</Link>
                      </div>
                    </section>
                  )}

                  {!alertasDesvio.cargando && !alertasDesvio.error && alertasDesvio.total > 0 && (
                    <PanelAlertasDesvio alertas={alertasDesvio.alertas} total={alertasDesvio.total} />
                  )}
                </div>
              </details>
            </aside>
          )}
        </div>
      </div>
    </div>
  )
}

function Filtro({ campo, titulo, valor, onCambiar, children }) {
  return <div className="fld"><label htmlFor={`ejecutivo-${campo}`}>{titulo}</label><select id={`ejecutivo-${campo}`} value={valor} onChange={(e) => onCambiar(campo, e.target.value)}>{children}</select></div>
}

function Barras({ filas, formato }) {
  const maximo = Math.max(0, ...filas.map((f) => Number(f.valor)))
  if (filas.length === 0) return <p className="hint">No hay datos para los filtros seleccionados.</p>
  return <ul className="dashboard-barras">{filas.map((fila) => <li key={fila.nombre}>
    <div><span>{fila.nombre}</span><strong>{formato(fila.valor)}</strong></div>
    <div className="dashboard-barra" aria-hidden="true"><span style={{ width: `${maximo ? Number(fila.valor) / maximo * 100 : 0}%` }} /></div>
  </li>)}</ul>
}

function BarrasSegmentos({ datos, onSeleccionar }) {
  const { filas, maximo, metrica } = datos
  if (filas.length === 0) {
    return <p className="hint">No hay segmentos publicados para los filtros seleccionados.</p>
  }

  return (
    <ul className="dashboard-segmentos__barras">
      {filas.map((fila) => {
        const nivel = nivelMorosidad(fila.morosidad)
        const porcentaje = maximo > 0 ? (fila.valor / maximo) * 100 : 0
        const contribuyentes = `${fila.contribuyentes} ${fila.contribuyentes === 1 ? 'contribuyente' : 'contribuyentes'}`
        const principal = metrica === 'deuda' ? moneda(fila.deuda) : contribuyentes
        const secundaria = metrica === 'deuda' ? contribuyentes : `${moneda(fila.deuda)} vencidos`
        const morosidad = formatearPorcentaje(fila.morosidad)
        return (
          <li key={fila.idSegmento}>
            <button
              type="button"
              className="dashboard-segmentos__fila"
              onClick={() => onSeleccionar(fila.idSegmento)}
              aria-label={`${fila.nombre}: ${principal}, ${morosidad === '—' ? 'sin morosidad' : `${morosidad} de morosidad`}. Filtrar el dashboard por este segmento.`}
            >
              <span className="dashboard-segmentos__nombre">
                <strong>{fila.nombre}</strong>
                <small>{secundaria}</small>
              </span>
              <span className="dashboard-segmentos__barra" aria-hidden="true">
                <span
                  className={`dashboard-segmentos__relleno nivel-${nivel}`}
                  style={{ width: `${fila.valor > 0 ? Math.max(porcentaje, 2) : 0}%` }}
                />
              </span>
              <span className="dashboard-segmentos__valor">{principal}</span>
              <span className={`dashboard-segmentos__morosidad nivel-${nivel}`}>
                {morosidad === '—' ? 'Sin morosidad' : `${morosidad} morosidad`}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
