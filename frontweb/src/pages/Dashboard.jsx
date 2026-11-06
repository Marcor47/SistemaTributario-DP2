import { useEffect, useState } from 'react'
import CardsNumericos from '../components/dashboard/CardsNumericos.jsx'
import AlertasDashboard from './AlertasDashboard.jsx'
import { obtenerDashboard, obtenerFiltrosDashboard } from '../services/dashboardService.js'
import { useSesionInterna } from '../context/sesionInterna.js'
import { fecha, moneda } from '../utils/formato.js'
import { formatearPorcentaje, formatearValorIndicador } from '../utils/alertas.js'
import './Dashboard.css'

const INICIALES = { anio: '', numeroPeriodo: '', zona: '', idSegmento: '', tributo: '' }
const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']

export default function DashboardEjecutivo() {
  const { sesion } = useSesionInterna()
  const [filtros, setFiltros] = useState(INICIALES)
  const [revision, setRevision] = useState(0)
  const [estado, setEstado] = useState({ cargando: true, datos: null, opciones: null, error: null })
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

  function cambiar(campo, valor) {
    setEstado((anterior) => ({ ...anterior, cargando: true, error: null }))
    setFiltros((anterior) => ({ ...anterior, [campo]: valor }))
  }
  function actualizar(evento) {
    evento?.preventDefault()
    setEstado((anterior) => ({ ...anterior, cargando: true, error: null }))
    setRevision((valor) => valor + 1)
  }
  const { datos, opciones, cargando, error } = estado
  const resumen = datos?.resumen
  const metricas = resumen ? [
    { id: 'recaudado', etiqueta: 'Recaudación', valor: moneda(resumen.montoRecaudado), detalle: `${formatearPorcentaje(resumen.porcentajeRecaudacion)} de la emisión` },
    { id: 'emitido', etiqueta: 'Monto emitido', valor: moneda(resumen.montoEmitido), detalle: `Obligaciones exigibles al ${fecha(datos.fechaCorte)}` },
    { id: 'morosidad', etiqueta: 'Índice de morosidad', valor: formatearPorcentaje(resumen.indiceMorosidad), detalle: `${moneda(resumen.montoVencidoNoPagado)} vencidos sin pagar` },
  ] : []

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
          <button className="btn primary" disabled={cargando}>{cargando ? 'Consultando…' : 'Actualizar'}</button>
        </form>
      </div>
      <div className="dashboard-contenido">
        {cargando && <CardsNumericos cargando />}
        {!cargando && error && <section className="pcard" role="status">
          <h2>{error.codigo === 'SIN_CARGA_ETL' ? 'Aún no hay una carga publicada' : 'No se pudo consultar el dashboard'}</h2>
          <p>{error.message}</p><button className="btn" onClick={actualizar}>Reintentar</button>
        </section>}
        {!cargando && datos && <>
          <p className="dashboard-nota">Periodo: <strong>{datos.periodo}</strong> · Última carga: <strong>{fecha(datos.fechaCorteCarga)}</strong> · Corte de consulta: <strong>{fecha(datos.fechaCorte)}</strong></p>
          <CardsNumericos metricas={metricas} />
          {Number(resumen.montoEmitido) === 0 && <p role="status" className="dashboard-nota">No hay obligaciones exigibles para los filtros seleccionados. Los porcentajes sin base se muestran como «—».</p>}
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
            <section className="pcard">
              <h2>Distribución por segmento</h2>
              <Barras filas={datos.porSegmento.map((s) => ({ nombre: s.nombre, valor: s.contribuyentes }))} formato={(n) => `${n} contribuyentes`} />
              <p className="hint">Incluye solo asignaciones a segmentos publicados.</p>
            </section>
          </div>
          <section className="pcard"><h2>Resultados por zona</h2>
            <div className="dashboard-tabla"><table><thead><tr><th>Zona</th><th>Emitido</th><th>Recaudado</th><th>Recaudación / emisión</th><th>Morosidad</th></tr></thead><tbody>
              {datos.porZona.map((z) => <tr key={z.zona}><th scope="row">{z.zona}</th><td>{moneda(z.montoEmitido)}</td><td>{moneda(z.montoRecaudado)}</td><td>{formatearPorcentaje(z.porcentajeRecaudacion)}</td><td>{formatearPorcentaje(z.indiceMorosidad)}</td></tr>)}
            </tbody></table></div>
          </section>
        </>}
      </div>
      {sesion?.permisos?.includes('ALERTAS:CONSULTAR') && <details className="dashboard-alertas"><summary>Alertas de desvío de metas · periodo vigente</summary><AlertasDashboard /></details>}
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
