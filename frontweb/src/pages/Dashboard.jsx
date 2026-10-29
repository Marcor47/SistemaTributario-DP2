import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import BarraFiltros from '../components/dashboard/BarraFiltros.jsx'
import CardsNumericos from '../components/dashboard/CardsNumericos.jsx'
import CardsPrincipales from '../components/dashboard/CardsPrincipales.jsx'
import PanelAlertasDesvio from '../components/dashboard/PanelAlertasDesvio.jsx'
import SkeletonAlertas from '../components/dashboard/SkeletonAlertas.jsx'
import { listarAlertasDesvio } from '../services/alertaDesvioService.js'
import { listarIndicadores } from '../services/metaService.js'
import { fecha } from '../utils/formato.js'
import { filtrarAlertasDelPeriodo, resumirAlertas } from '../utils/alertas.js'
import './Dashboard.css'

const FILTROS_INICIALES = { indicadorCodigo: '' }

export default function Dashboard() {
  const [indicadores, setIndicadores] = useState([])
  const [filtros, setFiltros] = useState(FILTROS_INICIALES)
  const [revision, setRevision] = useState(0)
  const [estado, setEstado] = useState({ cargando: true, alertas: [], error: null })

  useEffect(() => {
    let activo = true
    listarIndicadores()
      .then((resultado) => { if (activo) setIndicadores(Array.isArray(resultado) ? resultado : []) })
      .catch(() => { if (activo) setIndicadores([]) })
    return () => { activo = false }
  }, [])

  useEffect(() => {
    const controlador = new AbortController()
    listarAlertasDesvio({ signal: controlador.signal })
      .then((alertas) => {
        if (!controlador.signal.aborted) {
          setEstado({ cargando: false, alertas: Array.isArray(alertas) ? alertas : [], error: null })
        }
      })
      .catch((error) => {
        if (!controlador.signal.aborted) setEstado({ cargando: false, alertas: [], error })
      })
    return () => controlador.abort()
  }, [revision])

  function cambiarFiltro(campo, valor) {
    setFiltros((actual) => ({ ...actual, [campo]: valor }))
  }

  function actualizar(evento) {
    evento?.preventDefault()
    setEstado({ cargando: true, alertas: [], error: null })
    setRevision((actual) => actual + 1)
  }

  const alertasVigentes = filtrarAlertasDelPeriodo(estado.alertas)
  const alertasConcluidas = estado.alertas.length - alertasVigentes.length
  const alertasVisibles = filtros.indicadorCodigo
    ? alertasVigentes.filter((alerta) => alerta.indicadorCodigo === filtros.indicadorCodigo)
    : alertasVigentes
  const { total, indicadores: indicadoresEnDesvio, ultimaCorte } = resumirAlertas(alertasVisibles)

  const metricas = [
    {
      id: 'alertas',
      etiqueta: 'Alertas vigentes',
      valor: total,
      detalle: total === 1 ? 'Indicador fuera de meta' : 'Indicadores fuera de meta',
    },
    {
      id: 'indicadores',
      etiqueta: 'Indicadores en desvío',
      valor: indicadoresEnDesvio,
      detalle: 'Con meta configurada y umbral superado',
    },
    {
      id: 'corte',
      etiqueta: 'Corte de las alertas',
      valor: ultimaCorte ? fecha(ultimaCorte) : '—',
      detalle: 'Fecha de corte de las alertas mostradas',
      variante: 'fecha',
    },
  ]

  const mensajeVacio = estado.alertas.length === 0
    ? 'No hay alertas de desvío registradas. Cuando una carga publicada deje un indicador fuera de su meta, aparecerá aquí.'
    : filtros.indicadorCodigo
      ? 'El indicador seleccionado no tiene alertas en el periodo vigente. Las alertas de periodos concluidos no se muestran en el dashboard.'
      : `No hay alertas de desvío en el periodo vigente.${alertasConcluidas > 0 ? ` Se omitieron ${alertasConcluidas} ${alertasConcluidas === 1 ? 'alerta' : 'alertas'} de periodos concluidos.` : ''}`

  return (
    <div className="dashboard" aria-busy={estado.cargando}>
      <div className="dashboard-filtros-sticky">
        <BarraFiltros
          filtros={filtros}
          indicadores={indicadores}
          onCambiarFiltro={cambiarFiltro}
          onActualizar={actualizar}
          cargando={estado.cargando}
        />
      </div>

      <div className="dashboard-contenido">
        {estado.error && (
          <div className="alert" role="alert">
            <strong>No se pudieron consultar las alertas de desvío.</strong>
            <p>{estado.error.message || 'Ocurrió un error inesperado. Intenta nuevamente.'}</p>
            {estado.error.detalles?.length > 0 && (
              <ul>{estado.error.detalles.map((detalle) => <li key={detalle}>{detalle}</li>)}</ul>
            )}
            <button className="btn" type="button" onClick={actualizar}>Reintentar</button>
          </div>
        )}

        {estado.cargando && (
          <>
            <CardsNumericos cargando />
            <SkeletonAlertas />
          </>
        )}

        {!estado.cargando && !estado.error && alertasVisibles.length === 0 && (
          <section className="pcard dashboard-vacio" role="status">
            <h2>{estado.alertas.length === 0 ? 'Sin alertas de desvío' : 'Sin alertas en el periodo vigente'}</h2>
            <p>{mensajeVacio}</p>
            <div className="dashboard-vacio__acciones">
              <button className="btn" type="button" onClick={actualizar}>Actualizar</button>
              <Link className="btn" to="/metas">Revisar metas</Link>
            </div>
          </section>
        )}

        {!estado.cargando && !estado.error && alertasVisibles.length > 0 && (
          <>
            <CardsNumericos metricas={metricas} />
            <CardsPrincipales>
              <PanelAlertasDesvio
                alertas={alertasVisibles}
                total={total}
                alertasConcluidas={alertasConcluidas}
              />
            </CardsPrincipales>
          </>
        )}
      </div>
    </div>
  )
}
