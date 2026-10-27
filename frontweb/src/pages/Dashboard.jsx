import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarAlertasDesvio } from '../services/alertaDesvioService.js'
import { listarIndicadores } from '../services/metaService.js'
import { etiquetaUnidad } from '../utils/metas.js'
import { fecha, fechaHora } from '../utils/formato.js'
import {
  etiquetaAlcance,
  etiquetaSentido,
  explicarDesvio,
  formatearDesvio,
  formatearPorcentaje,
  formatearValorIndicador,
  resumirAlertas,
} from '../utils/alertas.js'
import './Dashboard.css'

function TarjetaAlerta({ alerta }) {
  const valorObservado = alerta.valorObservado == null || alerta.valorObservado === '' ? null : Number(alerta.valorObservado)
  const valorMeta = alerta.valorMeta == null || alerta.valorMeta === '' ? null : Number(alerta.valorMeta)
  const maximo = Math.max(Math.abs(valorObservado ?? 0), Math.abs(valorMeta ?? 0), 1)
  const ancho = (valor) => Number.isFinite(valor) ? Math.min(100, (Math.abs(valor) / maximo) * 100) : 0
  const anchoObservado = ancho(valorObservado)
  const anchoMeta = ancho(valorMeta)

  return (
    <article className="pcard dashboard-alerta">
      <header className="dashboard-alerta__cab">
        <div>
          <h3>{alerta.indicadorNombre || alerta.indicadorCodigo}</h3>
          <p className="hint">
            <code>{alerta.indicadorCodigo}</code> · {etiquetaUnidad(alerta.unidadMedida)}
          </p>
        </div>
        <span className="estado critico">Fuera de meta</span>
      </header>

      <div className="dashboard-alerta__valores">
        <div className="pf">
          <span className="pl">Valor observado</span>
          <strong className="pv">{formatearValorIndicador(valorObservado, alerta.unidadMedida)}</strong>
        </div>
        <div className="pf">
          <span className="pl">Meta</span>
          <strong className="pv">{formatearValorIndicador(valorMeta, alerta.unidadMedida)}</strong>
        </div>
      </div>

      <div className="dashboard-comparacion" aria-hidden="true">
        <div className="dashboard-comparacion__fila">
          <span>Observado</span>
          <div className="dashboard-barra"><span className="dashboard-barra__observado" style={{ width: `${anchoObservado}%` }} /></div>
        </div>
        <div className="dashboard-comparacion__fila">
          <span>Meta</span>
          <div className="dashboard-barra"><span className="dashboard-barra__meta" style={{ width: `${anchoMeta}%` }} /></div>
        </div>
      </div>

      <div className="dashboard-desvio">
        <div>
          <span className="pl">Desvío frente a la meta</span>
          <strong className="dashboard-desvio__valor">{formatearDesvio(alerta.desvioPorcentual)}</strong>
        </div>
        <span className="estado neutro">Umbral {formatearPorcentaje(alerta.umbralDesvio)}</span>
      </div>

      <p className="dashboard-alerta__explicacion">{explicarDesvio(alerta)}</p>

      <dl className="dashboard-alerta__datos">
        <div><dt>Periodo</dt><dd>{alerta.periodo || '—'}</dd></div>
        <div><dt>Alcance</dt><dd>{etiquetaAlcance(alerta.alcance, alerta.valorAlcance)}</dd></div>
        <div><dt>Sentido</dt><dd>{etiquetaSentido(alerta.sentido)}</dd></div>
        <div><dt>Fecha de corte</dt><dd>{fecha(alerta.fechaCorte)}</dd></div>
        <div><dt>Generada</dt><dd>{fechaHora(alerta.fechaGeneracion)}</dd></div>
        <div><dt>Lote</dt><dd>{alerta.idLote == null ? '—' : `#${alerta.idLote}`}</dd></div>
      </dl>

      <div className="dashboard-alerta__acciones">
        <Link className="btn btn-compacto" to="/metas">Revisar metas</Link>
      </div>
    </article>
  )
}

function CargandoAlertas() {
  return (
    <>
      <div className="dashboard-alertas" aria-hidden="true">
        {[0, 1].map((clave) => (
          <div className="pcard dashboard-alerta" key={clave}>
            <span className="dashboard-skeleton dashboard-skeleton--titulo" />
            <span className="dashboard-skeleton dashboard-skeleton--linea" />
            <span className="dashboard-skeleton dashboard-skeleton--bloque" />
            <span className="dashboard-skeleton dashboard-skeleton--linea" />
          </div>
        ))}
      </div>
      <p className="sr-only" role="status">Consultando alertas de desvío…</p>
    </>
  )
}

export default function Dashboard() {
  const [indicadores, setIndicadores] = useState([])
  const [filtroIndicador, setFiltroIndicador] = useState('')
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
    listarAlertasDesvio(filtroIndicador, { signal: controlador.signal })
      .then((alertas) => {
        if (!controlador.signal.aborted) {
          setEstado({ cargando: false, alertas: Array.isArray(alertas) ? alertas : [], error: null })
        }
      })
      .catch((error) => {
        if (!controlador.signal.aborted) setEstado({ cargando: false, alertas: [], error })
      })
    return () => controlador.abort()
  }, [filtroIndicador, revision])

  function cambiarFiltro(evento) {
    setEstado({ cargando: true, alertas: [], error: null })
    setFiltroIndicador(evento.target.value)
  }

  function actualizar(evento) {
    evento?.preventDefault()
    setEstado({ cargando: true, alertas: [], error: null })
    setRevision((actual) => actual + 1)
  }

  const { total, indicadores: indicadoresEnDesvio, ultimaCorte } = resumirAlertas(estado.alertas)

  return (
    <div className="dashboard" aria-busy={estado.cargando}>
      <form className="filters dashboard-filtros" onSubmit={actualizar} aria-label="Filtrar alertas de desvío">
        <div className="fld">
          <label htmlFor="dashboard-indicador">Indicador</label>
          <select id="dashboard-indicador" value={filtroIndicador} onChange={cambiarFiltro}>
            <option value="">Todos los indicadores</option>
            {indicadores.map((indicador) => (
              <option key={indicador.codigo} value={indicador.codigo}>{indicador.nombre}</option>
            ))}
          </select>
        </div>
        <button className="btn primary" type="submit" disabled={estado.cargando}>
          {estado.cargando ? 'Consultando…' : 'Actualizar'}
        </button>
      </form>

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

      {estado.cargando && <CargandoAlertas />}

      {!estado.cargando && !estado.error && estado.alertas.length === 0 && (
        <section className="pcard dashboard-vacio" role="status">
          <h2>Sin alertas de desvío</h2>
          <p>
            {filtroIndicador
              ? 'El indicador seleccionado no tiene alertas activas en la última carga publicada. Puedes elegir otro indicador o actualizar la consulta.'
              : 'Los indicadores con meta configurada se mantienen dentro del umbral en la última carga publicada. Puedes actualizar la consulta o revisar las metas vigentes.'}
          </p>
          <div className="dashboard-vacio__acciones">
            <button className="btn" type="button" onClick={actualizar}>Actualizar</button>
            <Link className="btn" to="/metas">Revisar metas</Link>
          </div>
        </section>
      )}

      {!estado.cargando && !estado.error && estado.alertas.length > 0 && (
        <>
          <section className="dashboard-resumen" aria-label="Resumen de alertas">
            <div className="pcard dashboard-kpi">
              <span className="pl">Alertas activas</span>
              <strong className="dashboard-kpi__valor">{total}</strong>
              <p className="hint">{total === 1 ? 'Indicador fuera de meta' : 'Indicadores fuera de meta'}</p>
            </div>
            <div className="pcard dashboard-kpi">
              <span className="pl">Indicadores en desvío</span>
              <strong className="dashboard-kpi__valor">{indicadoresEnDesvio}</strong>
              <p className="hint">Con meta configurada y umbral superado</p>
            </div>
            <div className="pcard dashboard-kpi">
              <span className="pl">Última carga</span>
              <strong className="dashboard-kpi__fecha">{ultimaCorte ? fecha(ultimaCorte) : '—'}</strong>
              <p className="hint">Fecha de corte de las alertas mostradas</p>
            </div>
          </section>

          <section aria-labelledby="dashboard-alertas-titulo">
            <div className="dashboard-seccion-cab">
              <h2 id="dashboard-alertas-titulo">Alertas activas</h2>
              <span className="hint">{total === 1 ? '1 alerta' : `${total} alertas`}</span>
            </div>
            <div className="dashboard-alertas">
              {estado.alertas.map((alerta) => <TarjetaAlerta key={alerta.idAlerta} alerta={alerta} />)}
            </div>
          </section>
        </>
      )}
    </div>
  )
}
