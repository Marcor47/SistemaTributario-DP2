import { Link } from 'react-router-dom'
import PanelAlertasDesvio from '../alertas/PanelAlertasDesvio.jsx'
import SkeletonAlertas from '../alertas/SkeletonAlertas.jsx'
import { useAlertasDesvio } from '../../hooks/useAlertasDesvio.js'
import '../alertas/AlertasDesvio.css'

// HU039: alertas activas de desvío de meta de la última carga publicada, como subpestaña de la plataforma analítica.
export default function AlertasDesvio() {
  const alertas = useAlertasDesvio()

  if (alertas.cargando) return <SkeletonAlertas />

  if (alertas.error) {
    return (
      <div className="alert" role="alert">
        <strong>No se pudieron consultar las alertas de desvío.</strong>
        <p>{alertas.error.message || 'Ocurrió un error inesperado. Intenta nuevamente.'}</p>
        <button className="btn" type="button" onClick={alertas.recargar}>Reintentar</button>
      </div>
    )
  }

  if (alertas.total === 0) {
    return (
      <section className="pcard" role="status">
        <h2>Sin alertas de desvío</h2>
        <p>No hay alertas de desvío activas. Cuando una carga publicada deje un indicador fuera de su meta, aparecerá aquí.</p>
        <Link className="btn" to="/metas">Revisar metas</Link>
      </section>
    )
  }

  return <PanelAlertasDesvio alertas={alertas.alertas} total={alertas.total} />
}
