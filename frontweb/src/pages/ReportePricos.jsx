import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import GestionVistasReporte from '../components/GestionVistasReporte.jsx'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import SelectorColumnasAgrupadas from '../components/SelectorColumnasAgrupadas.jsx'
import VisualizacionReporte from '../components/VisualizacionReporte.jsx'
import { fecha, moneda } from '../utils/formato.js'
import {
  CRITERIOS_PRICOS, GRUPOS_PRICOS, alternarGrupoColumnas, etiquetaCriterioPricos, etiquetaTipoDocumento, normalizarOrdenColumnas,
  ordenarGrupos, paginarFilas, porcentaje, reordenarColumnasDeGrupo, reordenarGruposColumnas, validarPricos,
} from '../utils/reportes.js'
import { exportarReportePricos, generarReportePricos } from '../services/reporteService.js'
import './Reportes.css'

const GRUPOS = GRUPOS_PRICOS

const COLUMNAS_PRICOS = GRUPOS.flatMap(({ columnas }) => columnas)

const ETIQUETAS = {
  codigo: 'Código',
  razonSocial: 'Razón social',
  regimen: 'Régimen',
  tipoDocumento: 'Tipo doc.',
  numeroDocumento: 'N° doc.',
  domicilioFiscal: 'Domicilio fiscal',
  via: 'Vía',
  numero: 'N°',
  departamento: 'Dpto',
  interior: 'Interior',
  manzana: 'Mz',
  lote: 'Lote',
  distrito: 'Distrito',
  baseImponible: 'Base imponible',
  emisionPredial: 'Predial',
  emisionArbitrios: 'Arbitrios',
  deudaAnteriorPredial: 'Predial',
  deudaAnteriorArbitrios: 'Arbitrios',
  deudaActualPredial: 'Predial',
  deudaActualArbitrios: 'Arbitrios',
  porcentajeDescuentoArbitrios: '% dscto. arbitrios',
  condicion: 'Condición',
  tipoPersona: 'Tipo persona',
  correo: 'Email',
  telefonos: 'Teléfonos',
}

const COLUMNAS_NUMERICAS = new Set([
  'baseImponible',
  'emisionPredial',
  'emisionArbitrios',
  'deudaAnteriorPredial',
  'deudaAnteriorArbitrios',
  'deudaActualPredial',
  'deudaActualArbitrios',
  'porcentajeDescuentoArbitrios',
])

const SERIES = [
  { id: 'baseImponible', etiqueta: 'Base imponible', valor: (fila) => fila.baseImponible },
  { id: 'emisionTotal', etiqueta: 'Emisión total', valor: (fila) => Number(fila.emisionPredial || 0) + Number(fila.emisionArbitrios || 0) },
  { id: 'deudaAnterior', etiqueta: 'Deuda de años anteriores', valor: (fila) => Number(fila.deudaAnteriorPredial || 0) + Number(fila.deudaAnteriorArbitrios || 0) },
  { id: 'deudaActual', etiqueta: 'Deuda con el año de corte', valor: (fila) => Number(fila.deudaActualPredial || 0) + Number(fila.deudaActualArbitrios || 0) },
]

const COLUMNAS_ANCHAS = new Set(['razonSocial', 'domicilioFiscal', 'via', 'correo'])

function montoCelda(valor) {
  return valor == null || valor === '' ? '—' : moneda(valor)
}

function porcentajeCelda(valor) {
  return valor == null || valor === '' ? '—' : porcentaje(valor)
}

function tipoPersonaEtiqueta(valor) {
  if (valor === 'JURIDICA') return 'Persona jurídica'
  if (valor === 'NATURAL') return 'Persona natural'
  return valor || '—'
}

function valorCelda(fila, columna) {
  const domicilio = fila.domicilioFiscal || {}
  switch (columna) {
    case 'codigo': return fila.codigo || '—'
    case 'razonSocial': return fila.razonSocial || '—'
    case 'regimen': return fila.regimen || '—'
    case 'tipoDocumento': return fila.documento?.tipo ? etiquetaTipoDocumento(fila.documento.tipo) : '—'
    case 'numeroDocumento': return fila.documento?.numero || '—'
    case 'domicilioFiscal': return domicilio.direccion || '—'
    case 'via': return domicilio.via || '—'
    case 'numero': return domicilio.numero || '—'
    case 'departamento': return domicilio.departamento || '—'
    case 'interior': return domicilio.interior || '—'
    case 'manzana': return domicilio.manzana || '—'
    case 'lote': return domicilio.lote || '—'
    case 'distrito': return domicilio.distrito || '—'
    case 'baseImponible': return montoCelda(fila.baseImponible)
    case 'emisionPredial': return montoCelda(fila.emisionAnioCorte?.predial)
    case 'emisionArbitrios': return montoCelda(fila.emisionAnioCorte?.arbitrios)
    case 'deudaAnteriorPredial': return montoCelda(fila.deudaAniosAnteriores?.predial)
    case 'deudaAnteriorArbitrios': return montoCelda(fila.deudaAniosAnteriores?.arbitrios)
    case 'deudaActualPredial': return montoCelda(fila.deudaAnioCorte?.predial)
    case 'deudaActualArbitrios': return montoCelda(fila.deudaAnioCorte?.arbitrios)
    case 'porcentajeDescuentoArbitrios': return porcentajeCelda(fila.deudaAnioCorte?.porcentajeDescuentoArbitrios)
    case 'condicion': return fila.condicion || '—'
    case 'tipoPersona': return tipoPersonaEtiqueta(fila.tipoPersona)
    case 'correo': return fila.correo || '—'
    case 'telefonos': return fila.telefonos || '—'
    default: return '—'
  }
}

function claseColumna(columna) {
  const clases = []
  if (COLUMNAS_NUMERICAS.has(columna)) clases.push('num')
  if (COLUMNAS_ANCHAS.has(columna)) clases.push('reporte-col-ancha')
  if (columna === 'codigo') clases.push('reporte-col-codigo')
  return clases.length ? clases.join(' ') : undefined
}

function tituloError(error) {
  if (error?.estado === 403) return 'Reporte restringido.'
  if (error?.estado === 400) return 'Revisa el criterio y el límite.'
  if (error?.codigo === 'SIN_CARGA_ETL') return 'No hay una carga publicada del ETL con hechos.'
  if (error?.codigo === 'ETL_SIN_CORRESPONDENCIA') return 'La carga del ETL no tiene correspondencia con SAMNET.'
  return 'No se pudo generar el reporte.'
}

function medirDesborde(elemento) {
  if (!elemento) return { izquierda: false, derecha: false }
  return {
    izquierda: elemento.scrollLeft > 2,
    derecha: elemento.scrollLeft + elemento.clientWidth < elemento.scrollWidth - 2,
  }
}

function desplazarColumnas(ref, direccion) {
  const grid = ref.current?.querySelector('.reporte-columnas-grid')
  if (!grid) return
  grid.scrollBy({ left: direccion * Math.max(180, grid.clientWidth * 0.75), behavior: 'smooth' })
}

function descargar({ blob, nombre }) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  enlace.click()
  URL.revokeObjectURL(url)
}

function ResumenPricos({ datos, paginacion }) {
  return <>
    <section className="reporte-resumen" aria-label="Resumen del reporte">
      <article className="pcard"><span className="pl">Fecha de corte</span><strong>{fecha(datos.fechaCorte)}</strong></article>
      <article className="pcard"><span className="pl">Contribuyentes considerados</span><strong>{datos.totalContribuyentes}</strong></article>
      <article className="pcard"><span className="pl">Contribuyentes mostrados</span><strong>{datos.filas.length}</strong></article>
      <article className="pcard"><span className="pl">Criterio</span><strong className="reporte-resumen-texto">{etiquetaCriterioPricos(datos.criterio)}</strong></article>
    </section>
    <div className="reporte-tabla-barra">
      <div className="reporte-aplicados">
        <strong>Filtros aplicados:</strong>
        <span>Criterio: {etiquetaCriterioPricos(datos.criterio)}</span>
        <span>Límite: {datos.limite}</span>
        <span>Carga ETL: #{datos.idLote}</span>
      </div>
      {paginacion}
    </div>
  </>
}

function TablaPricos({ filas, columnas, grupos, anioCorte }) {
  return <div className="tabla-scroll" tabIndex="0" role="region" aria-label="Reporte de PRICOS en tabla">
    <table className="tabla reporte-tabla reporte-tabla-pricos">
      <caption className="sr-only">Reporte de principales contribuyentes</caption>
      <thead>
        <tr className="reporte-grupo">
          {grupos.map((grupo) => <th key={grupo.etiqueta} scope="colgroup" colSpan={grupo.columnas.length}>
            {grupo.etiqueta === 'Deuda con el año de corte' && anioCorte ? `Deuda con ${anioCorte}` : grupo.etiqueta}
          </th>)}
        </tr>
        <tr>
          {columnas.map((columna) => <th key={columna} scope="col" className={claseColumna(columna)}>{ETIQUETAS[columna]}</th>)}
        </tr>
      </thead>
      <tbody>
        {filas.map((fila) => <tr key={`${fila.codigo}-${fila.posicion}`}>
          {columnas.map((columna) => <td key={columna} className={claseColumna(columna)}>{valorCelda(fila, columna)}</td>)}
        </tr>)}
      </tbody>
    </table>
  </div>
}

export default function ReportePricos({ titulo, onCambiarReporte, puedePersonalizar }) {
  const [criterio, setCriterio] = useState(CRITERIOS_PRICOS[0].valor)
  const [limite, setLimite] = useState('10')
  const [aplicado, setAplicado] = useState({ criterio: CRITERIOS_PRICOS[0].valor, limite: '10' })
  const [errores, setErrores] = useState({})
  const [resultado, setResultado] = useState({ cargando: false, datos: null, error: null })
  const [columnasVisibles, setColumnasVisibles] = useState(COLUMNAS_PRICOS)
  const [ordenColumnas, setOrdenColumnas] = useState(COLUMNAS_PRICOS)
  const [columnasAplicadas, setColumnasAplicadas] = useState(COLUMNAS_PRICOS)
  const [ordenColumnasAplicadas, setOrdenColumnasAplicadas] = useState(COLUMNAS_PRICOS)
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)
  const [exportando, setExportando] = useState('')
  const [errorAccion, setErrorAccion] = useState(null)
  const [tipoGrafica, setTipoGrafica] = useState('TABLA')
  const [serie, setSerie] = useState('baseImponible')
  const [desbordeColumnas, setDesbordeColumnas] = useState({ izquierda: false, derecha: false })
  const consultaActiva = useRef(null)
  const grupoColumnasRef = useRef(null)
  const arrastreColumnas = useRef(false)

  const actualizarDesbordeColumnas = useCallback(() => {
    setDesbordeColumnas((actual) => {
      const siguiente = medirDesborde(grupoColumnasRef.current?.querySelector('.reporte-columnas-grid'))
      return actual.izquierda === siguiente.izquierda && actual.derecha === siguiente.derecha ? actual : siguiente
    })
  }, [])

  const ejecutar = useCallback(async (criterioSolicitado, limiteSolicitado, columnasSolicitadas, ordenSolicitado) => {
    const validacion = validarPricos({ criterio: criterioSolicitado, limite: limiteSolicitado })
    setErrores(validacion)
    if (Object.keys(validacion).length) return

    consultaActiva.current?.abort()
    const controlador = new AbortController()
    consultaActiva.current = controlador
    setResultado({ cargando: true, datos: null, error: null })
    try {
      const datos = await generarReportePricos(
        { criterio: criterioSolicitado, limite: limiteSolicitado },
        { signal: controlador.signal },
      )
      if (controlador.signal.aborted) return
      setResultado({ cargando: false, datos, error: null })
      setAplicado({ criterio: criterioSolicitado, limite: String(limiteSolicitado) })
      setColumnasAplicadas(columnasSolicitadas)
      setOrdenColumnasAplicadas(ordenSolicitado)
      setPagina(1)
    } catch (error) {
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error })
    }
  }, [])

  useEffect(() => () => consultaActiva.current?.abort(), [])

  useEffect(() => {
    const actualizar = () => actualizarDesbordeColumnas()
    const frame = window.requestAnimationFrame(actualizar)
    window.addEventListener('resize', actualizar)
    return () => {
      window.cancelAnimationFrame(frame)
      window.removeEventListener('resize', actualizar)
    }
  }, [columnasVisibles, ordenColumnas, actualizarDesbordeColumnas])

  const gruposOrdenados = useMemo(
    () => ordenarGrupos(GRUPOS, ordenColumnas).map((grupo) => ({
      ...grupo,
      columnas: ordenColumnas.filter((columna) => grupo.columnas.includes(columna)),
    })),
    [ordenColumnas],
  )
  const gruposAplicados = useMemo(
    () => ordenarGrupos(GRUPOS, ordenColumnasAplicadas).map((grupo) => ({
      ...grupo,
      columnas: ordenColumnasAplicadas.filter((columna) => grupo.columnas.includes(columna)),
    })),
    [ordenColumnasAplicadas],
  )
  const gruposVisiblesAplicados = useMemo(
    () => gruposAplicados
      .map((grupo) => ({ ...grupo, columnas: grupo.columnas.filter((columna) => columnasAplicadas.includes(columna)) }))
      .filter((grupo) => grupo.columnas.length > 0),
    [gruposAplicados, columnasAplicadas],
  )
  const columnasAplicadasOrdenadas = useMemo(
    () => ordenColumnasAplicadas.filter((columna) => columnasAplicadas.includes(columna)),
    [ordenColumnasAplicadas, columnasAplicadas],
  )
  const criterioSeleccionado = CRITERIOS_PRICOS.find(({ valor }) => valor === criterio)
  const hayCambiosFiltros = criterio !== aplicado.criterio || String(limite) !== aplicado.limite
  const mismasColumnasVisibles = columnasVisibles.length === columnasAplicadas.length
    && columnasVisibles.every((columna) => columnasAplicadas.includes(columna))
  const hayCambiosColumnas = !mismasColumnasVisibles
    || ordenColumnas.join('|') !== ordenColumnasAplicadas.join('|')
  const hayCambiosSinAplicar = hayCambiosFiltros || hayCambiosColumnas
  const mostrarAvisoCambios = Boolean(resultado.datos) && hayCambiosSinAplicar && !resultado.cargando
  const columnasPredeterminadas = columnasVisibles.length === COLUMNAS_PRICOS.length
    && ordenColumnas.every((columna, indice) => columna === COLUMNAS_PRICOS[indice])

  function consultar(evento) {
    evento?.preventDefault()
    ejecutar(criterio, limite, columnasVisibles, ordenColumnas)
  }

  function alternarColumna(columna) {
    setColumnasVisibles((actual) => {
      if (actual.includes(columna)) {
        return actual.length === 1 ? actual : actual.filter((item) => item !== columna)
      }
      return COLUMNAS_PRICOS.filter((item) => actual.includes(item) || item === columna)
    })
  }

  function alternarGrupo(grupoClave) {
    const grupo = GRUPOS_PRICOS.find(({ clave }) => clave === grupoClave)
    if (!grupo) return
    setColumnasVisibles((actual) => alternarGrupoColumnas(actual, grupo, COLUMNAS_PRICOS))
  }

  function restablecerColumnas() {
    setColumnasVisibles(COLUMNAS_PRICOS)
    setOrdenColumnas(COLUMNAS_PRICOS)
  }

  function reordenarGrupos(claves) {
    setOrdenColumnas((actual) => reordenarGruposColumnas(actual, GRUPOS, claves))
  }

  function reordenarColumnasGrupo(grupoClave, columnas) {
    setOrdenColumnas((actual) => reordenarColumnasDeGrupo(actual, GRUPOS, grupoClave, columnas))
  }

  async function exportar(formato) {
    setExportando(formato)
    setErrorAccion(null)
    try {
      descargar(await exportarReportePricos({ ...aplicado, columnas: columnasAplicadasOrdenadas }, formato))
    } catch (error) {
      setErrorAccion(error)
    } finally {
      setExportando('')
    }
  }

  function abrirConfiguracion({ vista, reporte }) {
    consultaActiva.current?.abort()
    const criterioVista = vista.filtros?.criterio || CRITERIOS_PRICOS[0].valor
    const limiteVista = String(vista.filtros?.limite || 10)
    const columnasVista = (vista.columnasVisibles || []).filter((columna) => COLUMNAS_PRICOS.includes(columna))
    const ordenVista = normalizarOrdenColumnas(vista.ordenColumnas, GRUPOS)
    const columnasVistaOrdenadas = ordenVista.filter((columna) => (columnasVista.length ? columnasVista.includes(columna) : true))
    setCriterio(criterioVista)
    setLimite(limiteVista)
    setAplicado({ criterio: criterioVista, limite: limiteVista })
    setColumnasVisibles(columnasVistaOrdenadas)
    setOrdenColumnas(ordenVista)
    setColumnasAplicadas(columnasVistaOrdenadas)
    setOrdenColumnasAplicadas(ordenVista)
    setTipoGrafica(vista.tipoGrafica || 'TABLA')
    setSerie(vista.series?.find((item) => SERIES.some((opcion) => opcion.id === item)) || 'baseImponible')
    setResultado({ cargando: false, datos: reporte, error: null })
    setPagina(1)
  }

  const errorGeneral = resultado.error
  const paginacion = paginarFilas(resultado.datos?.filas, pagina, tamanio)
  const configuracionVista = {
    tipoGrafica,
    filtros: { criterio: aplicado.criterio, limite: Number(aplicado.limite) },
    columnasVisibles: columnasAplicadasOrdenadas,
    ordenColumnas: ordenColumnasAplicadas,
    orden: '',
    series: [serie],
  }

  return <div className="reportes" aria-busy={resultado.cargando}>
    <section className="pcard reporte-configuracion" aria-labelledby="configuracion-pricos">
      <form className="reporte-configuracion-form" onSubmit={consultar} noValidate>
        <div className="reporte-configuracion-cab">
          <div>
            <h2 id="configuracion-pricos">Criterio y personalización</h2>
            <p className="hint">Configura el criterio, el límite y las columnas. El reporte se generará solo al pulsar “Generar reporte”.</p>
          </div>
          <div className="reporte-configuracion-acciones">
            <button className="btn" type="button" onClick={onCambiarReporte}>Cambiar reporte</button>
            <button className="btn primary reporte-generar" type="submit" disabled={resultado.cargando}>{resultado.cargando ? 'Generando…' : 'Generar reporte'}</button>
          </div>
        </div>

        <div className={`reporte-cambios-pendientes-wrap${mostrarAvisoCambios ? ' reporte-cambios-pendientes-wrap--visible' : ''}`} aria-hidden={!mostrarAvisoCambios}>
          <div className="reporte-cambios-pendientes-interior">
            <p className="reporte-cambios-pendientes" role="status">Hay cambios de criterio, columnas u orden sin aplicar. Pulsa “Generar reporte” para verlos.</p>
          </div>
        </div>

        <div className="reporte-configuracion-grid">
          <fieldset className="reporte-config-grupo">
            <legend>Criterio del reporte</legend>
            <div className="reporte-config-campos">
              <div className={`fld${errores.criterio ? ' fld-error' : ''}`}>
                <label htmlFor="pricos-criterio">Criterio</label>
                <select id="pricos-criterio" value={criterio} onChange={(e) => setCriterio(e.target.value)}>
                  {CRITERIOS_PRICOS.map((item) => <option key={item.valor} value={item.valor}>{item.etiqueta}</option>)}
                </select>
                {errores.criterio
                  ? <span className="error-campo">{errores.criterio}</span>
                  : <span className="hint">{criterioSeleccionado?.descripcion}</span>}
              </div>
              <div className={`fld${errores.limite ? ' fld-error' : ''}`}>
                <label htmlFor="pricos-limite">Límite de contribuyentes</label>
                <input id="pricos-limite" type="number" min="1" max="200" step="1" value={limite} onChange={(e) => setLimite(e.target.value)} />
                {errores.limite
                  ? <span className="error-campo">{errores.limite}</span>
                  : <span className="hint">Entre 1 y 200 contribuyentes.</span>}
              </div>
            </div>
          </fieldset>

          <fieldset className="reporte-config-grupo reporte-config-grupo--personalizacion">
            <legend>Personalización</legend>
            <fieldset className="reporte-columnas">
              <legend>Columnas visibles</legend>
              <p className="hint reporte-columnas-ayuda">Arrastra una columna dentro de su grupo, o una cabecera para mover el grupo completo con todas sus columnas.</p>
              <div className="reporte-fila-restablecer">
                <div
                  className="reporte-columnas-grupo"
                  ref={grupoColumnasRef}
                  data-desborde-izquierda={desbordeColumnas.izquierda}
                  data-desborde-derecha={desbordeColumnas.derecha}
                >
                  <SelectorColumnasAgrupadas
                    grupos={gruposOrdenados}
                    columnasVisibles={columnasVisibles}
                    etiquetas={ETIQUETAS}
                    arrastre={arrastreColumnas}
                    onAlternar={alternarColumna}
                    onAlternarGrupo={alternarGrupo}
                    onReordenarGrupos={reordenarGrupos}
                    onReordenarColumnas={reordenarColumnasGrupo}
                    onScroll={actualizarDesbordeColumnas}
                    ariaLabel="Columnas visibles del reporte PRICOS"
                  />
                  {desbordeColumnas.izquierda && <button
                    className="reporte-columnas-flecha reporte-columnas-flecha--izquierda"
                    type="button"
                    aria-label="Desplazar columnas a la izquierda"
                    onClick={() => desplazarColumnas(grupoColumnasRef, -1)}
                  ><span aria-hidden="true">‹</span></button>}
                  {desbordeColumnas.derecha && <button
                    className="reporte-columnas-flecha reporte-columnas-flecha--derecha"
                    type="button"
                    aria-label="Desplazar columnas a la derecha"
                    onClick={() => desplazarColumnas(grupoColumnasRef, 1)}
                  ><span aria-hidden="true">›</span></button>}
                </div>
                <button className="btn reporte-btn-restablecer" type="button" onClick={restablecerColumnas} disabled={columnasPredeterminadas}>Restablecer columnas</button>
              </div>
            </fieldset>
          </fieldset>
        </div>
      </form>
    </section>

    <GestionVistasReporte tipo="PRICOS" configuracion={configuracionVista} habilitado={Boolean(resultado.datos)} onAbrir={abrirConfiguracion} />

    <div aria-live="polite">
      {resultado.cargando && <div className="pcard reporte-estado" role="status">Generando el reporte con la última carga publicada del ETL…</div>}
      {errorGeneral && <div className="alert" role="alert">
        <strong>{tituloError(errorGeneral)}</strong>
        <p>{errorGeneral.message}</p>
        {errorGeneral.estado === 503 && <button className="btn" type="button" onClick={() => ejecutar(criterio, limite, columnasVisibles, ordenColumnas)}>Reintentar</button>}
      </div>}
      {!resultado.datos && !resultado.cargando && !errorGeneral && <div className="pcard reporte-estado" role="status"><h3>Reporte sin generar</h3><p>Configura el criterio, el límite y las columnas y pulsa “Generar reporte”.</p></div>}
    </div>

    {resultado.datos && <section className="reporte-resultados" aria-labelledby="resultado-pricos">
      <div className="reporte-resultados-cab">
        <div>
          <h2 id="resultado-pricos">{titulo || 'Reporte de PRICOS'}</h2>
          <p className="hint">Información oficial a la fecha de corte de la última carga publicada del ETL.</p>
        </div><div className="reporte-config-exportar"><button className="btn" type="button" onClick={() => exportar('EXCEL')} disabled={Boolean(exportando)}>{exportando === 'EXCEL' ? 'Exportando…' : 'Exportar Excel'}</button><button className="btn" type="button" onClick={() => exportar('PDF')} disabled={Boolean(exportando)}>{exportando === 'PDF' ? 'Exportando…' : 'Exportar PDF'}</button></div>
      </div>
      <ResumenPricos datos={resultado.datos} paginacion={resultado.datos.filas.length > 0 && tipoGrafica === 'TABLA' ? <PaginacionTabla id="pricos" totalRegistros={resultado.datos.filas.length} pagina={paginacion.pagina} tamanio={paginacion.tamanio} onCambiarPagina={setPagina} onCambiarTamanio={(valor) => { setTamanio(valor); setPagina(1) }} /> : null} />
      {errorAccion && <div className="alert" role="alert">{errorAccion.message}</div>}
      {resultado.datos.filas.length === 0
        ? <div className="pcard reporte-estado" role="status"><h3>Sin resultados</h3><p>No hay contribuyentes en la carga del ETL para este criterio.</p></div>
        : puedePersonalizar ? <VisualizacionReporte tipo={tipoGrafica} onCambiarTipo={setTipoGrafica} serie={serie} onCambiarSerie={setSerie} series={SERIES} tipos={['TABLA', 'BARRAS', 'CIRCULAR']} filas={resultado.datos.filas} tabla={<TablaPricos filas={paginacion.filas} columnas={columnasAplicadasOrdenadas} grupos={gruposVisiblesAplicados} anioCorte={resultado.datos.fechaCorte?.slice(0, 4)} />} /> : <TablaPricos filas={paginacion.filas} columnas={columnasAplicadasOrdenadas} grupos={gruposVisiblesAplicados} anioCorte={resultado.datos.fechaCorte?.slice(0, 4)} />}
    </section>}
  </div>
}
