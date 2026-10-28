import { useEffect, useState } from 'react'
import { obtenerMorosidad } from '../services/morosidadService.js'
import { etiquetaTributo, porcentaje } from '../utils/morosidad.js'
import { fecha, moneda } from '../utils/formato.js'
import './ConsultasTributarias.css'

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']
const ANIOS = Array.from({ length: 8 }, (_, i) => new Date().getFullYear() - i)
const monto = (valor) => valor == null ? 'No disponible' : moneda(valor)

function Desglose({ titulo, filas, tipo }) {
  const [vista, setVista] = useState('grafico')
  const [orden, setOrden] = useState('mayor')
  const ordenadas = [...filas].sort((a, b) => {
    if (orden === 'nombre') return a.valorAlcance.localeCompare(b.valorAlcance, 'es')
    if (a.indice === null) return b.indice === null ? 0 : 1
    if (b.indice === null) return -1
    return orden === 'menor' ? a.indice - b.indice : b.indice - a.indice
  })
  const etiqueta = (fila) => tipo === 'tributo' ? etiquetaTributo(fila.valorAlcance) : fila.valorAlcance
  return (
    <section className="pcard" aria-labelledby={`titulo-${tipo}`}>
      <div className="consulta-cabecera">
        <div><h2 id={`titulo-${tipo}`}>{titulo}</h2><p className="hint">Prioriza los mayores índices de morosidad.</p></div>
        <div className="consulta-acciones">
          <label className="consulta-orden">Ordenar
            <select value={orden} onChange={(e) => setOrden(e.target.value)} aria-label={`Ordenar por ${tipo}`}>
              <option value="mayor">Mayor morosidad</option><option value="menor">Menor morosidad</option><option value="nombre">Nombre</option>
            </select>
          </label>
          <button type="button" className="btn" aria-pressed={vista === 'tabla'} onClick={() => setVista(vista === 'tabla' ? 'grafico' : 'tabla')}>
            {vista === 'tabla' ? 'Ver gráfico' : 'Ver tabla'}
          </button>
        </div>
      </div>
      {filas.length === 0 ? <p className="consulta-vacio" role="status">La carga consultada no incluye un desglose por {tipo}.</p>
        : vista === 'grafico' ? (
          <ul className="consulta-barras" aria-label={titulo}>
            {ordenadas.map((fila) => <li key={fila.valorAlcance}>
              <div><span>{etiqueta(fila)}</span><strong>{porcentaje(fila.indice)}</strong></div>
              <div className="consulta-barra" aria-hidden="true"><span style={{ width: `${Math.min(100, Math.max(0, fila.indice ?? 0))}%` }} /></div>
            </li>)}
          </ul>
        ) : (
          <div className="tabla-scroll" tabIndex={0} role="region" aria-label={titulo}>
            <table className="tabla"><caption className="sr-only">{titulo}</caption>
              <thead><tr><th scope="col">{tipo === 'tributo' ? 'Tributo' : 'Zona'}</th><th scope="col" className="num">Emitido exigible</th><th scope="col" className="num">Vencido no pagado</th><th scope="col" className="num">Morosidad</th></tr></thead>
              <tbody>{ordenadas.map((fila) => <tr key={fila.valorAlcance}><th scope="row">{etiqueta(fila)}</th><td className="num">{monto(fila.montoEmitido)}</td><td className="num">{monto(fila.montoVencido)}</td><td className="num"><strong>{porcentaje(fila.indice)}</strong></td></tr>)}</tbody>
            </table>
          </div>
        )}
    </section>
  )
}

export default function IndiceMorosidad() {
  const [filtros, setFiltros] = useState({ anio: '', mes: '' })
  const [consulta, setConsulta] = useState({ anio: '', mes: '', revision: 0 })
  const [resultado, setResultado] = useState({ cargando: true, datos: null, error: null })

  useEffect(() => {
    const controlador = new AbortController()
    obtenerMorosidad(consulta.anio, consulta.mes, { signal: controlador.signal })
      .then((datos) => { if (!controlador.signal.aborted) setResultado({ cargando: false, datos, error: null }) })
      .catch((error) => { if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error }) })
    return () => controlador.abort()
  }, [consulta])

  function consultar(evento) {
    evento?.preventDefault()
    setResultado({ cargando: true, datos: null, error: null })
    setConsulta((actual) => ({ ...filtros, revision: actual.revision + 1 }))
  }

  const { datos, error, cargando } = resultado
  return (
    <div className="consulta-tributaria" aria-busy={cargando}>
      <form className="filters consulta-filtros" onSubmit={consultar} aria-label="Consultar periodo de morosidad">
        <div className="fld"><label htmlFor="morosidad-anio">Año</label><select id="morosidad-anio" value={filtros.anio} onChange={(e) => setFiltros({ anio: e.target.value, mes: '' })}>
          <option value="">Última carga publicada</option>{ANIOS.map((anio) => <option key={anio}>{anio}</option>)}
        </select></div>
        <div className="fld"><label htmlFor="morosidad-mes">Mes</label><select id="morosidad-mes" value={filtros.mes} disabled={!filtros.anio} onChange={(e) => setFiltros({ ...filtros, mes: e.target.value })}>
          <option value="">Último disponible</option>{MESES.map((mes, i) => <option key={mes} value={i + 1}>{mes}</option>)}
        </select></div>
        <button className="btn primary" type="submit" disabled={cargando}>{cargando ? 'Consultando…' : 'Consultar'}</button>
      </form>
      {cargando && <div className="pcard consulta-vacio" role="status">Consultando el índice de morosidad…</div>}
      {error && <div className="alert" role="alert"><strong>No se pudo consultar la morosidad.</strong><p>{error.message}</p><button type="button" className="btn" onClick={() => consultar()}>Reintentar</button></div>}
      {!cargando && !error && !datos && <section className="pcard consulta-vacio" role="status"><h2>Sin índice publicado</h2><p>No hay un índice calculado para el periodo seleccionado. Consulta otro periodo o vuelve a la última carga publicada.</p></section>}
      {!cargando && datos && <>
        {datos.esDemo && <div className="info" role="note"><strong>Datos de demostración.</strong> Esta muestra permite explorar la pantalla y no representa una carga real.</div>}
        <section className="pcard" aria-label="Carga del ETL consultada">
          <div className="consulta-cabecera"><div><h2>Carga publicada</h2><p className="hint">Los tres alcances corresponden al mismo lote y fecha de corte.</p></div><span className="estado ok">Lote #{datos.lote.idLote}</span></div>
          <div className="pfields"><div className="pf"><span className="pl">Periodo</span><strong className="pv">{datos.lote.periodo}</strong></div><div className="pf"><span className="pl">Fecha de corte</span><strong className="pv">{fecha(datos.lote.fechaCorte)}</strong></div></div>
        </section>
        <section className="consulta-indicadores" aria-label="Resumen de morosidad">
          <div className="pcard consulta-indicador principal"><span className="pl">Índice de morosidad total</span><strong className="consulta-cifra">{porcentaje(datos.total)}</strong><p className="hint">De la emisión exigible al corte</p></div>
          <div className="pcard consulta-indicador"><span className="pl">Emitido exigible</span><strong className="consulta-cifra secundaria">{monto(datos.montoEmitidoTotal)}</strong><p className="hint">Base del índice</p></div>
          <div className="pcard consulta-indicador"><span className="pl">Vencido no pagado</span><strong className="consulta-cifra secundaria">{monto(datos.montoVencidoTotal)}</strong><p className="hint">Saldo de cuotas vencidas</p></div>
        </section>
        {datos.total === null && <div className="info">El índice total no está disponible para esta carga. Un valor no disponible no equivale a 0 %.</div>}
        <Desglose titulo="Morosidad por tributo" filas={datos.porTributo} tipo="tributo" />
        <Desglose titulo="Morosidad por zona" filas={datos.porZona} tipo="zona" />
        <details className="pcard consulta-metodologia"><summary>¿Cómo se interpreta el índice?</summary><p><strong>Índice = (vencido no pagado ÷ emitido exigible) × 100.</strong> Un porcentaje mayor indica una mayor proporción de deuda impaga.</p><p>El cálculo considera las cuotas del año de corte cuyo vencimiento es anterior a esa fecha. El total se calcula sobre los montos, no promediando los porcentajes de tributos o zonas.</p><p className="hint">Cuando la consulta publicada no incluye los montos base, se muestran como «No disponible».</p></details>
      </>}
    </div>
  )
}
