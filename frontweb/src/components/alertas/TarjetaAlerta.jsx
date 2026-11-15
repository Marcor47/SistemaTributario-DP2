import { Link } from 'react-router-dom'
import { enlaceMeta } from '../../utils/metas.js'
import { fecha, fechaHora } from '../../utils/formato.js'
import {
  etiquetaAlcance,
  etiquetaPeriodo,
  etiquetaSentido,
  explicarDesvio,
  formatearDesvio,
  formatearPorcentaje,
  formatearValorIndicador,
} from '../../utils/alertas.js'

export default function TarjetaAlerta({ alerta }) {
  const valorObservado = alerta.valorObservado == null || alerta.valorObservado === '' ? null : Number(alerta.valorObservado)
  const valorMeta = alerta.valorMeta == null || alerta.valorMeta === '' ? null : Number(alerta.valorMeta)
  const maximo = Math.max(Math.abs(valorObservado ?? 0), Math.abs(valorMeta ?? 0), 1)
  const ancho = (valor) => Number.isFinite(valor) ? Math.min(100, (Math.abs(valor) / maximo) * 100) : 0
  const anchoObservado = ancho(valorObservado)
  const anchoMeta = ancho(valorMeta)

  return (
    <article className="pcard desvio-alerta">
      <header className="desvio-alerta__cab">
        <div>
          <h3>{alerta.indicadorNombre || alerta.indicadorCodigo}</h3>
          <p className="hint">
            {etiquetaPeriodo(alerta.periodo)} · {etiquetaAlcance(alerta.alcance, alerta.valorAlcance)}
          </p>
        </div>
        <span className="estado critico">Fuera de meta</span>
      </header>

      <div className="desvio-alerta__valores">
        <div className="pf">
          <span className="pl">Valor observado</span>
          <strong className="pv">{formatearValorIndicador(valorObservado, alerta.unidadMedida)}</strong>
        </div>
        <div className="pf">
          <span className="pl">Meta</span>
          <strong className="pv">{formatearValorIndicador(valorMeta, alerta.unidadMedida)}</strong>
        </div>
      </div>

      <div className="desvio-comparacion" aria-hidden="true">
        <div className="desvio-comparacion__fila">
          <span>Observado</span>
          <div className="desvio-barra"><span className="desvio-barra__observado" style={{ width: `${anchoObservado}%` }} /></div>
        </div>
        <div className="desvio-comparacion__fila">
          <span>Meta</span>
          <div className="desvio-barra"><span className="desvio-barra__meta" style={{ width: `${anchoMeta}%` }} /></div>
        </div>
      </div>

      <div className="desvio-desvio">
        <div>
          <span className="pl">Desvío frente a la meta</span>
          <strong className="desvio-desvio__valor">{formatearDesvio(alerta.desvioPorcentual)}</strong>
        </div>
        <span className="estado neutro">Umbral {formatearPorcentaje(alerta.umbralDesvio)}</span>
      </div>

      <p className="desvio-alerta__explicacion">{explicarDesvio(alerta)}</p>

      <dl className="desvio-alerta__datos">
        <div><dt>Sentido</dt><dd>{etiquetaSentido(alerta.sentido)}</dd></div>
        <div><dt>Fecha de corte</dt><dd>{fecha(alerta.fechaCorte)}</dd></div>
        <div><dt>Generada</dt><dd>{fechaHora(alerta.fechaGeneracion)}</dd></div>
      </dl>

      <div className="desvio-alerta__acciones">
        <Link className="btn btn-compacto" to={enlaceMeta(alerta)}>Revisar metas</Link>
      </div>
    </article>
  )
}
