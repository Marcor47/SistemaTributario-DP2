import { useState } from 'react'
import { moneda } from '../utils/formato.js'
import PaginacionTabla from './PaginacionTabla.jsx'

function numero(valor) {
  const convertido = Number(valor)
  return Number.isFinite(convertido) ? convertido : 0
}

function etiquetaValor(valor, formato) {
  return formato === 'cantidad' ? new Intl.NumberFormat('es-PE').format(valor) : moneda(valor)
}

function Barras({ filas, serie }) {
  const valores = filas.map((fila) => numero(serie.valor(fila)))
  const maximo = Math.max(...valores, 1)
  return <div className="reporte-grafica-barras" role="img" aria-label={`${serie.etiqueta} por contribuyente`}>
    {filas.map((fila, indice) => <div className="reporte-grafica-fila" key={fila.codigo || indice}>
      <div className="reporte-grafica-etiqueta"><strong>{fila.razonSocial || fila.nombre || fila.codigo}</strong><span>{etiquetaValor(valores[indice], serie.formato)}</span></div>
      <div className="reporte-grafica-pista"><span style={{ width: `${(valores[indice] / maximo) * 100}%` }} /></div>
    </div>)}
  </div>
}

function Lineas({ datos, serie }) {
  const categorias = datos?.categorias || []
  const valores = datos?.valores || []
  const [categoriaSeleccionada, setCategoriaActiva] = useState(categorias.at(-1) || null)
  const [detalleAbierto, setDetalleAbierto] = useState(null)
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)
  const categoriaActiva = categorias.includes(categoriaSeleccionada) ? categoriaSeleccionada : categorias.at(-1) || null
  const maximo = Math.max(...valores, 1)
  const posicionX = (indice) => categorias.length === 1 ? 50 : 4 + (indice / (categorias.length - 1)) * 92
  const posicionY = (valor) => 94 - (numero(valor) / maximo) * 84
  const indiceActivo = Math.max(0, categorias.indexOf(categoriaActiva))
  const resumenActivo = datos?.resumenes?.[indiceActivo]
  const detalle = datos?.detalles?.[detalleAbierto] || []
  const totalPaginas = Math.max(1, Math.ceil(detalle.length / tamanio))
  const paginaActual = Math.min(pagina, totalPaginas)
  const detalleVisible = detalle.slice((paginaActual - 1) * tamanio, paginaActual * tamanio)
  return <div className="reporte-lineas-completa">
    {resumenActivo && <div className="reporte-lineas-tooltip" role="status">
      <strong>Año {categoriaActiva}</strong>
      <span>Total: {etiquetaValor(resumenActivo.total, serie.formato)}</span>
      <span>Contribuyentes: {resumenActivo.cantidad}</span>
      <span>Promedio: {etiquetaValor(resumenActivo.promedio, serie.formato)}</span>
      <span>Mayor deuda: {etiquetaValor(resumenActivo.maximo, serie.formato)}</span>
    </div>}
    <div className="reporte-lineas-area">
      <div className="reporte-lineas-eje-y" aria-hidden="true"><span>{etiquetaValor(maximo, serie.formato)}</span><span>{etiquetaValor(maximo / 2, serie.formato)}</span><span>{etiquetaValor(0, serie.formato)}</span></div>
      <div className="reporte-grafica-lineas">
      <svg viewBox="0 0 100 100" role="img" aria-label={`Evolución anual agregada de ${serie.etiqueta}`} preserveAspectRatio="none">
        <line x1="0" y1="10" x2="100" y2="10" className="reporte-linea-guia" />
        <line x1="0" y1="52" x2="100" y2="52" className="reporte-linea-guia" />
        <line x1="0" y1="94" x2="100" y2="94" className="reporte-linea-guia" />
        <polyline points={valores.map((valor, indice) => `${posicionX(indice)},${posicionY(valor)}`).join(' ')} fill="none" vectorEffect="non-scaling-stroke" />
        {valores.map((valor, indice) => <circle key={categorias[indice]} cx={posicionX(indice)} cy={posicionY(valor)} r={categorias[indice] === categoriaActiva ? '1.8' : '1.35'}><title>{`Año ${categorias[indice]}: ${etiquetaValor(valor, serie.formato)}`}</title></circle>)}
      </svg>
      <div className="reporte-lineas-eje-x">{categorias.map((categoria, indice) => <button key={categoria} type="button" aria-pressed={categoria === categoriaActiva} onMouseEnter={() => setCategoriaActiva(categoria)} onFocus={() => setCategoriaActiva(categoria)} onClick={() => { setCategoriaActiva(categoria); setDetalleAbierto(categoria); setPagina(1) }}><strong>{categoria}</strong><span>{etiquetaValor(valores[indice], serie.formato)}</span></button>)}</div>
      </div>
    </div>
    {detalleAbierto && <section className="reporte-lineas-detalle" aria-labelledby="detalle-linea-anual">
      <div className="reporte-lineas-detalle-cab"><div><h4 id="detalle-linea-anual">Contribuyentes del año {detalleAbierto}</h4><p className="hint">Detalle que compone el total anual seleccionado.</p></div><button className="btn" type="button" onClick={() => setDetalleAbierto(null)}>Cerrar detalle</button></div>
      <div className="reporte-tabla-barra reporte-tabla-barra--solo-paginacion"><PaginacionTabla id="detalle-linea-anual" totalRegistros={detalle.length} pagina={paginaActual} tamanio={tamanio} onCambiarPagina={setPagina} onCambiarTamanio={(valor) => { setTamanio(valor); setPagina(1) }} /></div>
      <div className="tabla-scroll" tabIndex="0" role="region" aria-label={`Contribuyentes con deuda en ${detalleAbierto}`}><table className="tabla"><thead><tr><th scope="col">Código</th><th scope="col">Contribuyente</th><th scope="col" className="num">Monto</th></tr></thead><tbody>{detalleVisible.map((item) => <tr key={item.codigo}><td>{item.codigo}</td><th scope="row">{item.nombre}</th><td className="num">{etiquetaValor(item.monto, serie.formato)}</td></tr>)}</tbody></table></div>
    </section>}
  </div>
}

function Circular({ filas, serie }) {
  const elementos = filas.map((fila) => ({ etiqueta: fila.razonSocial || fila.nombre || fila.codigo, valor: numero(serie.valor(fila)) })).filter((item) => item.valor > 0).slice(0, 8)
  const total = elementos.reduce((suma, item) => suma + item.valor, 0)
  let acumulado = 0
  const colores = ['#214478', '#1f9bc7', '#74c9dd', '#5a6fa5', '#2f7f8f', '#8ba4ca', '#0c6980', '#a8ddeb']
  const segmentos = elementos.map((item, indice) => {
    const inicio = acumulado
    acumulado += total ? (item.valor / total) * 100 : 0
    return `${colores[indice]} ${inicio}% ${acumulado}%`
  })
  return <div className="reporte-grafica-circular-wrap">
    <div className="reporte-grafica-circular" style={{ background: total ? `conic-gradient(${segmentos.join(',')})` : '#e7edf5' }} role="img" aria-label={`Distribución de ${serie.etiqueta}`}><span>{etiquetaValor(total, serie.formato)}</span></div>
    <ul className="reporte-grafica-leyenda">{elementos.map((item, indice) => <li key={`${item.etiqueta}-${indice}`}><i style={{ background: colores[indice] }} /><span>{item.etiqueta}</span><strong>{etiquetaValor(item.valor, serie.formato)}</strong></li>)}</ul>
  </div>
}

export default function VisualizacionReporte({ tipo, onCambiarTipo, serie, onCambiarSerie, series, tipos = ['TABLA', 'BARRAS'], filas, tabla, descripcion, lineas }) {
  const serieActiva = series.find((item) => item.id === serie) || series[0]
  return <>
    <section className="pcard reporte-visualizacion-config" aria-labelledby="titulo-visualizacion">
      <div><h3 id="titulo-visualizacion">Visualización</h3><p className="hint">Cambia la presentación sin modificar los datos del reporte.</p></div>
      <div className="reporte-visualizacion-controles">
        <fieldset><legend>Tipo</legend><div className="reporte-selector-visual">{tipos.map((opcion) => <button key={opcion} type="button" aria-pressed={tipo === opcion} onClick={() => onCambiarTipo(opcion)}>{opcion === 'TABLA' ? 'Tabla' : opcion === 'BARRAS' ? 'Barras' : opcion === 'LINEAS' ? 'Líneas' : 'Circular'}</button>)}</div></fieldset>
        {tipo !== 'TABLA' && <div className="fld reporte-serie"><label htmlFor="serie-reporte">Serie</label><select id="serie-reporte" value={serieActiva.id} onChange={(evento) => onCambiarSerie(evento.target.value)}>{series.map((item) => <option value={item.id} key={item.id}>{item.etiqueta}</option>)}</select></div>}
      </div>
    </section>
    {tipo === 'TABLA' ? tabla : <section className="pcard reporte-grafica" aria-live="polite">
      <div className="reporte-grafica-cab"><h3>{serieActiva.etiqueta}</h3><p className="hint">{descripcion || `Se representan ${filas.length} registros en el orden actual del reporte.`}</p></div>
      {tipo === 'BARRAS' && <Barras filas={filas} serie={serieActiva} />}
      {tipo === 'LINEAS' && <Lineas key={`${serieActiva.id}-${lineas?.categorias?.join('-')}`} datos={lineas} serie={serieActiva} />}
      {tipo === 'CIRCULAR' && <Circular filas={filas} serie={serieActiva} />}
    </section>}
  </>
}
