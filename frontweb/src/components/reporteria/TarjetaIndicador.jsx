import { colorSegmento, entero } from '../../utils/reporteria.js'
import IconoReporte from './IconoReporte.jsx'

export default function TarjetaIndicador({ indicador, referencia, segmentos }) {
  const { etiqueta, valor, detalle, ayuda, alerta, marca, variacion, texto } = indicador
  const clase = variacion?.mejora == null ? 'neutra' : variacion.mejora ? 'mejora' : 'empeora'
  const icono = variacion ? (variacion.valor > 0.05 ? 'sube' : variacion.valor < -0.05 ? 'baja' : 'igual') : null
  const totalSegmentos = indicador.segmentos?.reduce((a, b) => a + b, 0) || 1
  return (
    <article className={`rep-kpi${alerta ? ' rep-kpi--alerta' : ''}`}>
      <div className="rep-kpi__etiqueta">
        {etiqueta}
        {ayuda && <span className="rep-kpi__ayuda" title={ayuda} role="img" aria-label={`Qué mide: ${ayuda}`}><IconoReporte nombre="info" tamano={14} /></span>}
      </div>
      <div className={`rep-kpi__valor${texto ? ' rep-kpi__valor--texto' : ''}`}>{valor}</div>
      {detalle && <div className="rep-kpi__detalle">{detalle}</div>}
      {indicador.segmentos && (
        <div>
          <div className="rep-segbar" aria-hidden="true">
            {indicador.segmentos.map((n, i) => (n ? <span key={segmentos[i]} style={{ width: `${(n / totalSegmentos) * 100}%`, background: colorSegmento(i) }} /> : null))}
          </div>
          <div className="rep-segleyenda">
            {indicador.segmentos.map((n, i) => (n ? <span key={segmentos[i]}><i style={{ background: colorSegmento(i) }} />{segmentos[i]} {entero(n)}</span> : null))}
          </div>
        </div>
      )}
      {marca && (
        <div className={`rep-kpi__marca${marca.ok ? ' rep-kpi__marca--ok' : ''}`} role={marca.ok ? undefined : 'status'}>
          <IconoReporte nombre={marca.ok ? 'ok' : 'alerta'} tamano={14} />{marca.texto}
        </div>
      )}
      {variacion && referencia && (
        <div className={`rep-variacion rep-variacion--${clase}`}>
          <IconoReporte nombre={icono} tamano={12} />{variacion.texto}
          <span className="sr-only">{variacion.mejora == null ? ' (sin cambio)' : variacion.mejora ? ' (mejora)' : ' (empeora)'}</span>
          <span className="rep-variacion__contra"> vs {referencia}</span>
        </div>
      )}
    </article>
  )
}
