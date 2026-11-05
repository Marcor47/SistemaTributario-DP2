import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import GestionVistasReporte from '../components/GestionVistasReporte.jsx'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import VisualizacionReporte from '../components/VisualizacionReporte.jsx'
import { exportarBuenosPagadoresPorZona, generarBuenosPagadoresPorZona } from '../services/reporteService.js'
import { fecha, moneda } from '../utils/formato.js'
import { etiquetaTributo, etiquetaTipoDocumento, opcionesZona, paginarFilas, TRIBUTOS, validarFiltros } from '../utils/reportes.js'
import './Reportes.css'

const ANIO_ACTUAL = new Date().getFullYear()
const REPORTE = { filtros: ['periodo', 'zona', 'tributo', 'montoMinimo'] }
const FILTROS_INICIALES = { periodo: String(ANIO_ACTUAL), zona: '', tributo: '', montoMinimo: '' }
const COLUMNAS_BUENOS_PAGADORES = ['codigo', 'razonSocial', 'regimen', 'tipoDocumento', 'numeroDocumento', 'domicilioFiscal', 'zonas', 'montoEmitidoPredial', 'montoEmitidoArbitrios', 'montoPagadoPredial', 'montoPagadoArbitrios', 'totalEmitido', 'totalPagado', 'cuotasPagadasATiempo']
const ETIQUETAS_COLUMNAS = { codigo: 'Código', razonSocial: 'Razón social', regimen: 'Régimen', tipoDocumento: 'Tipo doc.', numeroDocumento: 'N° doc.', domicilioFiscal: 'Domicilio fiscal', zonas: 'Zonas', montoEmitidoPredial: 'Emitido predial', montoEmitidoArbitrios: 'Emitido arbitrios', montoPagadoPredial: 'Pagado predial', montoPagadoArbitrios: 'Pagado arbitrios', totalEmitido: 'Total emitido', totalPagado: 'Total pagado', cuotasPagadasATiempo: 'Cuotas a tiempo' }
const COLUMNAS_MONETARIAS = new Set(['montoEmitidoPredial', 'montoEmitidoArbitrios', 'montoPagadoPredial', 'montoPagadoArbitrios', 'totalEmitido', 'totalPagado'])
const SERIES = [
  { id: 'totalPagado', etiqueta: 'Total pagado', valor: (fila) => fila.totalPagado ?? fila.pagadoAnio },
  { id: 'montoPagadoPredial', etiqueta: 'Pagado por Impuesto Predial', valor: (fila) => fila.montoPagadoPredial },
  { id: 'montoPagadoArbitrios', etiqueta: 'Pagado por Arbitrios', valor: (fila) => fila.montoPagadoArbitrios },
  { id: 'cuotasPagadasATiempo', etiqueta: 'Cuotas pagadas a tiempo', valor: (fila) => fila.cuotasPagadasATiempo, formato: 'cantidad' },
]

function texto(valor) {
  return valor == null || valor === '' ? '—' : valor
}

function monto(valor) {
  return valor == null || valor === '' ? '—' : moneda(valor)
}

function domicilio(fila) {
  if (typeof fila.domicilioFiscal === 'string') return fila.domicilioFiscal
  return fila.domicilioFiscal?.direccion || fila.domicilioFiscalDesglosado?.direccion || '—'
}

function tituloError(error) {
  if (error?.estado === 403) return 'Reporte restringido.'
  if (error?.estado === 400) return 'Revisa los filtros.'
  if (error?.codigo === 'SAMNET_NO_DISPONIBLE' || error?.estado === 503) return 'La fuente tributaria no está disponible.'
  return 'No se pudo generar el reporte.'
}

function descargar({ blob, nombre }) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  enlace.click()
  URL.revokeObjectURL(url)
}

function medirDesborde(elemento) {
  if (!elemento) return { izquierda: false, derecha: false }
  return { izquierda: elemento.scrollLeft > 2, derecha: elemento.scrollLeft + elemento.clientWidth < elemento.scrollWidth - 2 }
}

function desplazarColumnas(ref, direccion) {
  const grid = ref.current?.querySelector('.reporte-columnas-grid')
  if (grid) grid.scrollBy({ left: direccion * Math.max(180, grid.clientWidth * 0.75), behavior: 'smooth' })
}

function valorColumna(fila, columna) {
  if (columna === 'razonSocial') return texto(fila.razonSocial || fila.nombre)
  if (columna === 'tipoDocumento') return fila.tipoDocumento ? etiquetaTipoDocumento(fila.tipoDocumento) : '—'
  if (columna === 'domicilioFiscal') return domicilio(fila)
  if (columna === 'zonas') return fila.zonas?.length ? fila.zonas.join(', ') : '—'
  if (COLUMNAS_MONETARIAS.has(columna)) return monto(fila[columna] ?? (columna === 'totalPagado' ? fila.pagadoAnio : null))
  return texto(fila[columna])
}

function claseColumna(columna) {
  if (COLUMNAS_MONETARIAS.has(columna) || columna === 'cuotasPagadasATiempo') return 'num'
  if (columna === 'codigo') return 'reporte-col-codigo'
  if (columna === 'razonSocial') return 'reporte-col-nombre'
  if (columna === 'domicilioFiscal') return 'reporte-col-domicilio'
  return undefined
}

function TablaBuenosPagadores({ filas, columnas }) {
  return <div className="reporte-tabla-con-paginacion">
    <div className="tabla-scroll" tabIndex="0" role="region" aria-label="Reporte de buenos pagadores por zona">
      <table className="tabla reporte-tabla reporte-tabla-buenos-pagadores">
        <caption className="sr-only">Contribuyentes puntuales por zona y periodo</caption>
        <thead><tr>{columnas.map((columna) => <th key={columna} scope="col" className={claseColumna(columna)}>{ETIQUETAS_COLUMNAS[columna]}</th>)}</tr></thead>
        <tbody>{filas.map((fila) => <tr key={fila.codigo}>
          {columnas.map((columna, indice) => indice === 0
            ? <th key={columna} scope="row" className={claseColumna(columna)}>{valorColumna(fila, columna)}</th>
            : <td key={columna} className={claseColumna(columna)}>{valorColumna(fila, columna)}</td>)}
        </tr>)}</tbody>
      </table>
    </div>
  </div>
}

export default function ReporteBuenosPagadores({ titulo, zonas, cargandoZonas, errorZonas, onCambiarReporte, puedePersonalizar }) {
  const [filtros, setFiltros] = useState(FILTROS_INICIALES)
  const [filtrosAplicados, setFiltrosAplicados] = useState(FILTROS_INICIALES)
  const [errores, setErrores] = useState({})
  const [resultado, setResultado] = useState({ cargando: false, datos: null, error: null })
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)
  const [exportando, setExportando] = useState('')
  const [columnasVisibles, setColumnasVisibles] = useState(COLUMNAS_BUENOS_PAGADORES)
  const [errorExportacion, setErrorExportacion] = useState(null)
  const [tipoGrafica, setTipoGrafica] = useState('TABLA')
  const [serie, setSerie] = useState('totalPagado')
  const [desbordeColumnas, setDesbordeColumnas] = useState({ izquierda: false, derecha: false })
  const consultaActiva = useRef(null)
  const grupoColumnasRef = useRef(null)

  const actualizarDesbordeColumnas = useCallback(() => {
    setDesbordeColumnas((actual) => {
      const siguiente = medirDesborde(grupoColumnasRef.current?.querySelector('.reporte-columnas-grid'))
      return actual.izquierda === siguiente.izquierda && actual.derecha === siguiente.derecha ? actual : siguiente
    })
  }, [])

  const opcionesZonas = useMemo(() => opcionesZona(zonas, filtros.zona), [zonas, filtros.zona])
  const hayCambios = Object.keys(FILTROS_INICIALES).some((campo) => filtros[campo] !== filtrosAplicados[campo])

  useEffect(() => () => consultaActiva.current?.abort(), [])
  useEffect(() => {
    const frame = window.requestAnimationFrame(actualizarDesbordeColumnas)
    window.addEventListener('resize', actualizarDesbordeColumnas)
    return () => { window.cancelAnimationFrame(frame); window.removeEventListener('resize', actualizarDesbordeColumnas) }
  }, [actualizarDesbordeColumnas, columnasVisibles])

  const consultar = useCallback(async (evento) => {
    evento?.preventDefault()
    const validacion = validarFiltros(REPORTE, filtros)
    setErrores(validacion)
    if (Object.keys(validacion).length) return
    consultaActiva.current?.abort()
    const controlador = new AbortController()
    consultaActiva.current = controlador
    setResultado({ cargando: true, datos: null, error: null })
    setErrorExportacion(null)
    try {
      const datos = await generarBuenosPagadoresPorZona(filtros, { signal: controlador.signal })
      if (controlador.signal.aborted) return
      setFiltrosAplicados({ ...filtros })
      setPagina(1)
      setResultado({ cargando: false, datos, error: null })
    } catch (error) {
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error })
    }
  }, [filtros])

  useEffect(() => {
    if (!resultado.datos || !hayCambios) return undefined
    const temporizador = window.setTimeout(() => consultar(), 450)
    return () => window.clearTimeout(temporizador)
  }, [hayCambios, resultado.datos, consultar])

  async function exportar(formato) {
    setExportando(formato)
    setErrorExportacion(null)
    try {
      descargar(await exportarBuenosPagadoresPorZona(filtrosAplicados, formato, columnasVisibles))
    } catch (error) {
      setErrorExportacion(error)
    } finally {
      setExportando('')
    }
  }

  function cambiarTamanio(siguiente) {
    setTamanio(siguiente)
    setPagina(1)
  }

  function abrirConfiguracion({ vista, reporte }) {
    const siguientes = { ...FILTROS_INICIALES, ...vista.filtros, periodo: String(vista.filtros?.periodo || vista.filtros?.anio || ANIO_ACTUAL), montoMinimo: vista.filtros?.montoMinimo ?? '' }
    setFiltros(siguientes)
    setFiltrosAplicados(siguientes)
    const columnasVista = (vista.columnasVisibles || []).filter((columna) => COLUMNAS_BUENOS_PAGADORES.includes(columna))
    setColumnasVisibles(columnasVista.length ? columnasVista : COLUMNAS_BUENOS_PAGADORES)
    setTipoGrafica(vista.tipoGrafica || 'TABLA')
    setSerie(vista.series?.find((item) => SERIES.some((opcion) => opcion.id === item)) || 'totalPagado')
    setResultado({ cargando: false, datos: reporte, error: null })
    setPagina(1)
  }

  const datos = resultado.datos
  const paginacion = paginarFilas(datos?.filas, pagina, tamanio)
  const errorGeneral = resultado.error
  const configuracionVista = { tipoGrafica, filtros: filtrosAplicados, columnasVisibles, ordenColumnas: COLUMNAS_BUENOS_PAGADORES, series: [serie] }
  function alternarColumna(columna) {
    setColumnasVisibles((actual) => actual.includes(columna)
      ? (actual.length === 1 ? actual : actual.filter((item) => item !== columna))
      : COLUMNAS_BUENOS_PAGADORES.filter((item) => actual.includes(item) || item === columna))
  }
  return <div className="reportes" aria-busy={resultado.cargando}>
    <section className="pcard reporte-configuracion" aria-labelledby="configuracion-buenos-pagadores">
      <form className="reporte-configuracion-form" onSubmit={consultar} noValidate>
        <div className="reporte-configuracion-cab">
          <div><h2 id="configuracion-buenos-pagadores">Filtros de buenos pagadores</h2><p className="hint">Genera la primera consulta; después los cambios se aplicarán automáticamente.</p></div>
          <div className="reporte-configuracion-acciones">
            <button className="btn" type="button" onClick={onCambiarReporte}>Cambiar reporte</button>
            <button className="btn primary reporte-generar" type="submit" disabled={resultado.cargando}>{resultado.cargando ? 'Generando…' : 'Generar reporte'}</button>
          </div>
        </div>
        <div className={`reporte-cambios-pendientes-wrap${hayCambios && datos ? ' reporte-cambios-pendientes-wrap--visible' : ''}`} aria-hidden={!hayCambios || !datos}>
          <div className="reporte-cambios-pendientes-interior"><p className="reporte-cambios-pendientes" role="status">Actualizando el reporte con los nuevos filtros…</p></div>
        </div>
        <fieldset className="reporte-config-grupo"><legend>Datos de consulta</legend><div className="reporte-config-campos">
          <div className={`fld${errores.periodo ? ' fld-error' : ''}`}><label htmlFor="buenos-periodo">Periodo</label><input id="buenos-periodo" type="number" min="2000" max="2100" value={filtros.periodo} onChange={(event) => setFiltros({ ...filtros, periodo: event.target.value })} aria-describedby={errores.periodo ? 'error-buenos-periodo' : 'ayuda-buenos-periodo'} /><span id="ayuda-buenos-periodo" className="hint">Año tributario evaluado.</span>{errores.periodo && <span id="error-buenos-periodo" className="error-campo">{errores.periodo}</span>}</div>
          <div className={`fld${errores.zona ? ' fld-error' : ''}`}><label htmlFor="buenos-zona">Zona tributaria</label><select id="buenos-zona" value={filtros.zona} onChange={(event) => setFiltros({ ...filtros, zona: event.target.value })} disabled={cargandoZonas}><option value="">Todas las zonas</option>{opcionesZonas.map((zona) => <option key={zona} value={zona}>{zona}</option>)}</select>{cargandoZonas && <span className="hint">Cargando zonas…</span>}{errorZonas && <span className="error-campo">No se pudieron cargar las zonas.</span>}{errores.zona && <span className="error-campo">{errores.zona}</span>}</div>
          <div className={`fld${errores.tributo ? ' fld-error' : ''}`}><label htmlFor="buenos-tributo">Tributo</label><select id="buenos-tributo" value={filtros.tributo} onChange={(event) => setFiltros({ ...filtros, tributo: event.target.value })}><option value="">Todos</option>{TRIBUTOS.map((tributo) => <option key={tributo.valor} value={tributo.valor}>{tributo.etiqueta}</option>)}</select>{errores.tributo && <span className="error-campo">{errores.tributo}</span>}</div>
          <div className={`fld${errores.montoMinimo ? ' fld-error' : ''}`}><label htmlFor="buenos-monto">Monto mínimo pagado (S/)</label><input id="buenos-monto" type="number" min="0" step="0.01" placeholder="0.00" value={filtros.montoMinimo} onChange={(event) => setFiltros({ ...filtros, montoMinimo: event.target.value })} />{errores.montoMinimo && <span className="error-campo">{errores.montoMinimo}</span>}</div>
        </div></fieldset>
        <fieldset className="reporte-config-grupo reporte-config-grupo--personalizacion">
          <legend>Personalización</legend>
          <p className="reporte-columnas-subtitulo">Columnas visibles</p>
          <div className="reporte-fila-restablecer reporte-fila-restablecer--buenos">
            <div className="reporte-columnas-grupo" ref={grupoColumnasRef} data-desborde-izquierda={desbordeColumnas.izquierda} data-desborde-derecha={desbordeColumnas.derecha}>
              <div className="reporte-columnas-grid" role="group" aria-label="Columnas visibles del reporte de buenos pagadores" onScroll={actualizarDesbordeColumnas}>
                {COLUMNAS_BUENOS_PAGADORES.map((columna) => <button key={columna} className="reporte-segmento" type="button" aria-pressed={columnasVisibles.includes(columna)} onClick={() => alternarColumna(columna)}>{ETIQUETAS_COLUMNAS[columna]}</button>)}
              </div>
              {desbordeColumnas.izquierda && <button className="reporte-columnas-flecha reporte-columnas-flecha--izquierda" type="button" aria-label="Desplazar columnas a la izquierda" onClick={() => desplazarColumnas(grupoColumnasRef, -1)}><span aria-hidden="true">‹</span></button>}
              {desbordeColumnas.derecha && <button className="reporte-columnas-flecha reporte-columnas-flecha--derecha" type="button" aria-label="Desplazar columnas a la derecha" onClick={() => desplazarColumnas(grupoColumnasRef, 1)}><span aria-hidden="true">›</span></button>}
            </div>
            <button className="btn reporte-btn-restablecer" type="button" onClick={() => setColumnasVisibles(COLUMNAS_BUENOS_PAGADORES)} disabled={columnasVisibles.length === COLUMNAS_BUENOS_PAGADORES.length}>Restablecer columnas</button>
          </div>
        </fieldset>
      </form>
    </section>

    <GestionVistasReporte tipo="BUENOS_PAGADORES" configuracion={configuracionVista} habilitado={Boolean(datos)} onAbrir={abrirConfiguracion} />

    <div aria-live="polite">
      {resultado.cargando && <div className="pcard reporte-estado" role="status">Generando el reporte de buenos pagadores…</div>}
      {errorGeneral && <div className="alert" role="alert"><strong>{tituloError(errorGeneral)}</strong><p>{errorGeneral.message}</p>{errorGeneral.estado === 503 && <button className="btn" type="button" onClick={consultar}>Reintentar</button>}</div>}
    </div>

    {datos && <section className="reporte-resultados" aria-labelledby="resultado-buenos-pagadores">
      <div className="reporte-resultados-cab"><div><h2 id="resultado-buenos-pagadores">{titulo || 'Reporte de buenos pagadores'}</h2><p className="hint">Contribuyentes puntuales a la fecha de corte indicada.</p></div><div className="reporte-config-exportar"><button className="btn" type="button" onClick={() => exportar('EXCEL')} disabled={Boolean(exportando)}>{exportando === 'EXCEL' ? 'Exportando…' : 'Exportar Excel'}</button><button className="btn" type="button" onClick={() => exportar('PDF')} disabled={Boolean(exportando)}>{exportando === 'PDF' ? 'Exportando…' : 'Exportar PDF'}</button></div></div>
      <section className="reporte-resumen" aria-label="Resumen del reporte">
        <article className="pcard"><span className="pl">Fecha de corte</span><strong>{fecha(datos.fechaCorte)}</strong></article>
        <article className="pcard"><span className="pl">Buenos pagadores</span><strong>{datos.resumen?.cantidad ?? datos.filas.length}</strong></article>
        <article className="pcard"><span className="pl">Total pagado</span><strong>{monto(datos.resumen?.montoTotal)}</strong></article>
        <article className="pcard"><span className="pl">Periodo</span><strong>{datos.filtrosAplicados?.periodo ?? datos.filtrosAplicados?.anio ?? filtrosAplicados.periodo}</strong></article>
      </section>
      <div className="reporte-tabla-barra">
        <div className="reporte-aplicados"><strong>Filtros aplicados:</strong><span>Zona: {datos.filtrosAplicados?.zona || 'Todas'}</span><span>Tributo: {etiquetaTributo(datos.filtrosAplicados?.tributo)}</span>{datos.filtrosAplicados?.montoMinimo != null && <span>Mínimo: {monto(datos.filtrosAplicados.montoMinimo)}</span>}</div>
        {datos.filas.length > 0 && tipoGrafica === 'TABLA' && <PaginacionTabla id="buenos-pagadores" totalRegistros={datos.filas.length} pagina={paginacion.pagina} tamanio={paginacion.tamanio} onCambiarPagina={setPagina} onCambiarTamanio={cambiarTamanio} />}
      </div>
      {errorExportacion && <div className="alert" role="alert"><strong>No se pudo exportar el reporte.</strong><p>{errorExportacion.message}</p></div>}
      {datos.filas.length === 0
        ? <div className="pcard reporte-estado" role="status"><h3>Sin resultados</h3><p>No hay contribuyentes que cumplan los filtros seleccionados.</p></div>
        : puedePersonalizar ? <VisualizacionReporte tipo={tipoGrafica} onCambiarTipo={setTipoGrafica} serie={serie} onCambiarSerie={setSerie} series={SERIES} tipos={['TABLA', 'BARRAS', 'CIRCULAR']} filas={datos.filas} tabla={<TablaBuenosPagadores filas={paginacion.filas} columnas={columnasVisibles} />} /> : <TablaBuenosPagadores filas={paginacion.filas} columnas={columnasVisibles} />}
    </section>}
  </div>
}
