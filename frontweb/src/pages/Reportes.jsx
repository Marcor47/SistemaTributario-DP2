import { useEffect, useMemo, useRef, useState } from 'react'
import { fecha, moneda } from '../utils/formato.js'
import { etiquetaTributo, filtrosDisponibles, limpiarFiltros, metricaReporte, opcionesZona, parametrosReporte, porcentaje, TRIBUTOS, validarFiltros } from '../utils/reportes.js'
import { abrirVista, descargarReporte, generarReporte, guardarVista, listarVistas, listarZonas, obtenerCatalogoReportes } from '../services/reporteService.js'
import './Reportes.css'

const FILTROS_INICIALES = { anio: '', periodo: '', zona: '', tributo: '', montoMinimo: '' }
const GRAFICAS = ['TABLA', 'BARRAS', 'LINEAS', 'CIRCULAR']
const COLUMNAS = {
  MOROSOS: ['codigo', 'razonSocial', 'regimen', 'tipoDocumento', 'numeroDocumento', 'zonas', 'deudaSinBeneficio', 'deudaVencida', 'cuotasVencidas', 'nombreEtapa'],
  PRICOS: ['codigo', 'nombre', 'tipoPersona', 'zonas', 'emitidoAnio', 'pagadoAnio', 'porcentajePagado', 'deudaVencida'],
  BUENOS_PAGADORES: ['codigo', 'nombre', 'zonas', 'cuotasPagadasATiempo', 'pagadoAnio'],
}
const ETIQUETAS = { codigo: 'Código', nombre: 'Contribuyente', razonSocial: 'Razón social', regimen: 'Régimen', tipoPersona: 'Tipo persona', tipoDocumento: 'Tipo doc.', numeroDocumento: 'Documento', zonas: 'Zonas', deudaSinBeneficio: 'Deuda sin beneficio', deudaVencida: 'Deuda vencida', cuotasVencidas: 'Cuotas vencidas', nombreEtapa: 'Etapa', emitidoAnio: 'Emitido', pagadoAnio: 'Pagado', porcentajePagado: '% pagado', cuotasPagadasATiempo: 'Cuotas a tiempo' }

function valorColumna(fila, columna) {
  const valor = columna === 'zonas' ? fila.zonas?.join(', ') : fila[columna]
  return ['deudaSinBeneficio', 'deudaVencida', 'emitidoAnio', 'pagadoAnio'].includes(columna) ? moneda(valor) : columna === 'porcentajePagado' ? porcentaje(valor) : (valor ?? '—')
}

function Identificacion({ fila }) {
  return <><strong>{fila.nombre}</strong><span className="reporte-secundario">{fila.tipoDocumento}: {fila.numeroDocumento}</span><span className="reporte-secundario">Código {fila.codigo}</span></>
}

function TablaMorosos({ filas }) {
  return <table className="tabla reporte-tabla"><caption className="sr-only">Contribuyentes morosos</caption><thead><tr><th scope="col">Contribuyente</th><th scope="col">Zonas</th><th scope="col" className="num">Deuda vencida</th><th scope="col" className="num">Cuotas</th><th scope="col">Antigüedad</th><th scope="col">Etapa</th><th scope="col">Desglose</th></tr></thead><tbody>{filas.map((fila) => <tr key={fila.codigo}><th scope="row"><Identificacion fila={fila} /></th><td>{fila.zonas?.join(', ') || 'Sin zona'}</td><td className="num"><strong>{moneda(fila.deudaVencida)}</strong></td><td className="num">{fila.cuotasVencidas}</td><td>{fecha(fila.vencimientoMasAntiguo)}<span className="reporte-secundario">{fila.diasVencido} días</span></td><td>{fila.nombreEtapa}</td><td>{fila.deudaPorTributo?.map((item) => <span className="reporte-secundario" key={item.tributo}>{etiquetaTributo(item.tributo)}: {moneda(item.monto)}</span>)}</td></tr>)}</tbody></table>
}

function TablaPricos({ filas }) {
  return <table className="tabla reporte-tabla"><caption className="sr-only">Principales contribuyentes</caption><thead><tr><th scope="col">Contribuyente</th><th scope="col">Tipo</th><th scope="col">Zonas</th><th scope="col" className="num">Emitido</th><th scope="col" className="num">Pagado</th><th scope="col" className="num">Porcentaje pagado</th><th scope="col" className="num">Deuda vencida</th></tr></thead><tbody>{filas.map((fila) => <tr key={fila.codigo}><th scope="row"><Identificacion fila={fila} /></th><td>{fila.tipoPersona === 'JURIDICA' ? 'Persona jurídica' : 'Persona natural'}</td><td>{fila.zonas?.join(', ') || 'Sin zona'}</td><td className="num">{moneda(fila.emitidoAnio)}</td><td className="num">{moneda(fila.pagadoAnio)}</td><td className="num">{porcentaje(fila.porcentajePagado)}</td><td className="num">{moneda(fila.deudaVencida)}</td></tr>)}</tbody></table>
}

function TablaBuenosPagadores({ filas }) {
  return <table className="tabla reporte-tabla"><caption className="sr-only">Buenos pagadores</caption><thead><tr><th scope="col">Contribuyente</th><th scope="col">Zonas</th><th scope="col" className="num">Cuotas pagadas a tiempo</th><th scope="col" className="num">Monto pagado</th></tr></thead><tbody>{filas.map((fila) => <tr key={fila.codigo}><th scope="row"><Identificacion fila={fila} /></th><td>{fila.zonas?.join(', ') || 'Sin zona'}</td><td className="num">{fila.cuotasPagadasATiempo}</td><td className="num"><strong>{moneda(fila.pagadoAnio)}</strong></td></tr>)}</tbody></table>
}

function TablaPersonalizada({ datos, columnas, orden }) {
  const filas = [...datos.filas].sort((a, b) => {
    const [campo, direccion] = (orden || '').split(':'); if (!campo) return 0
    const uno = a[campo] ?? ''; const dos = b[campo] ?? ''
    return (uno > dos ? 1 : uno < dos ? -1 : 0) * (direccion === 'asc' ? 1 : -1)
  })
  return <div className="tabla-scroll" tabIndex="0" role="region" aria-label={`${datos.titulo} en tabla`}><table className="tabla reporte-tabla"><caption className="sr-only">{datos.titulo}</caption><thead><tr>{columnas.map((columna) => <th key={columna} scope="col">{ETIQUETAS[columna] || columna}</th>)}</tr></thead><tbody>{filas.map((fila) => <tr key={fila.codigo}>{columnas.map((columna) => <td key={columna}>{valorColumna(fila, columna)}</td>)}</tr>)}</tbody></table></div>
}

function TablaReporte({ datos, columnas, orden }) {
  if (datos.__columnas?.length) return <TablaPersonalizada datos={datos} columnas={datos.__columnas} orden={datos.__orden} />
  if (columnas?.length) return <TablaPersonalizada datos={datos} columnas={columnas} orden={orden} />
  const Tabla = datos.tipoReporte === 'MOROSOS' ? TablaMorosos : datos.tipoReporte === 'PRICOS' ? TablaPricos : TablaBuenosPagadores
  return <div className="tabla-scroll" tabIndex="0" role="region" aria-label={`${datos.titulo} en tabla`}><Tabla filas={datos.filas} /></div>
}

function BarrasReporte({ datos }) {
  const maximo = Math.max(...datos.filas.map((fila) => metricaReporte(datos.tipoReporte, fila)), 0)
  return <ol className="reporte-barras" aria-label={`${datos.titulo} en barras`}>{datos.filas.map((fila) => { const valor = metricaReporte(datos.tipoReporte, fila); return <li key={fila.codigo}><div className="reporte-barra-etiqueta"><span><strong>{fila.nombre}</strong><small>Código {fila.codigo}</small></span><strong>{moneda(valor)}</strong></div><div className="reporte-barra" aria-hidden="true"><span style={{ width: `${maximo ? (valor / maximo) * 100 : 0}%` }} /></div></li> })}</ol>
}

function Resumen({ datos }) {
  const filtros = datos.filtrosAplicados || {}
  const aplicados = [filtros.anio && `Año: ${filtros.anio}`, filtros.zona && `Zona: ${filtros.zona}`, filtros.tributo && `Tributo: ${etiquetaTributo(filtros.tributo)}`, filtros.montoMinimo != null && `Monto mínimo: ${moneda(filtros.montoMinimo)}`].filter(Boolean)
  return <><section className="reporte-resumen" aria-label="Resumen del reporte"><article className="pcard"><span className="pl">Fecha de corte</span><strong>{fecha(datos.fechaCorte)}</strong></article><article className="pcard"><span className="pl">Contribuyentes</span><strong>{datos.resumen.cantidad}</strong></article><article className="pcard"><span className="pl">{datos.resumen.conceptoMonto}</span><strong>{moneda(datos.resumen.montoTotal)}</strong></article>{datos.resumen.umbralPrico != null && <article className="pcard"><span className="pl">Umbral PRICO</span><strong>{moneda(datos.resumen.umbralPrico)}</strong></article>}</section><div className="reporte-aplicados"><strong>Filtros aplicados:</strong>{aplicados.length ? aplicados.map((filtro) => <span key={filtro}>{filtro}</span>) : <span>Sin filtros adicionales</span>}</div></>
}

function FiltroZona({ valor, zonas, cargando, error, errorCampo, onCambio }) {
  const seleccionada = valor?.trim() || ''
  return <div className={`fld${errorCampo ? ' fld-error' : ''}`}>
    <label htmlFor="reporte-zona">Zona</label>
    {error
      ? <input id="reporte-zona" maxLength="20" placeholder="Ej. Z2" value={valor} onChange={(e) => onCambio(e.target.value)} />
      : <select id="reporte-zona" value={seleccionada} onChange={(e) => onCambio(e.target.value)} disabled={cargando}>
          <option value="">Todas</option>
          {opcionesZona(zonas, seleccionada).map((zona) => <option key={zona} value={zona}>{zona}</option>)}
        </select>}
    {cargando && <span className="hint">Cargando zonas…</span>}
    {error && <span className="hint">No se pudo cargar el catálogo; escribe la zona.</span>}
    {errorCampo && <span className="error-campo">{errorCampo}</span>}
  </div>
}

export default function Reportes() {
  const [catalogo, setCatalogo] = useState({ cargando: true, datos: [], error: null, revision: 0 })
  const [seleccion, setSeleccion] = useState('')
  const [filtros, setFiltros] = useState(FILTROS_INICIALES)
  const [errores, setErrores] = useState({})
  const [resultado, setResultado] = useState({ cargando: false, datos: null, error: null })
  const [vista, setVista] = useState('tabla')
  const [grafica, setGrafica] = useState('TABLA')
  const [columnas, setColumnas] = useState([])
  const [orden, setOrden] = useState('')
  const [vistas, setVistas] = useState([])
  const [nombreVista, setNombreVista] = useState('')
  const [accionError, setAccionError] = useState(null)
  const [zonas, setZonas] = useState({ tipo: '', datos: [], error: null })
  const consultaActiva = useRef(null)
  const reporte = useMemo(() => catalogo.datos.find(({ tipo }) => tipo === seleccion), [catalogo.datos, seleccion])

  useEffect(() => {
    const controlador = new AbortController()
    obtenerCatalogoReportes({ signal: controlador.signal }).then((datos) => {
      if (controlador.signal.aborted) return
      setCatalogo((actual) => ({ ...actual, cargando: false, datos, error: null }))
      setSeleccion((actual) => actual && datos.some(({ tipo }) => tipo === actual) ? actual : datos[0]?.tipo || '')
    }).catch((error) => { if (!controlador.signal.aborted) setCatalogo((actual) => ({ ...actual, cargando: false, datos: [], error })) })
    return () => controlador.abort()
  }, [catalogo.revision])

  useEffect(() => {
    if (!seleccion) return
    const controlador = new AbortController()
    listarZonas(seleccion, { signal: controlador.signal }).then((datos) => {
      if (!controlador.signal.aborted) setZonas({ tipo: seleccion, datos, error: null })
    }).catch((error) => {
      if (!controlador.signal.aborted) setZonas({ tipo: seleccion, datos: [], error })
    })
    return () => controlador.abort()
  }, [seleccion])

  useEffect(() => () => consultaActiva.current?.abort(), [])
  useEffect(() => { listarVistas().then(setVistas).catch(() => {}) }, [])

  function elegir(nuevo) {
    const siguiente = catalogo.datos.find(({ tipo }) => tipo === nuevo)
    setSeleccion(nuevo); setFiltros((actual) => limpiarFiltros(siguiente, actual)); setErrores({}); setResultado({ cargando: false, datos: null, error: null }); setVista('tabla'); setGrafica('TABLA'); setColumnas(COLUMNAS[nuevo] || []); setOrden('')
  }

  async function abrirConfiguracion(vistaGuardada) {
    try {
      const datos = await abrirVista(vistaGuardada.idVista)
      datos.reporte.__columnas = datos.vista.columnasVisibles; datos.reporte.__orden = datos.vista.orden || ''
      setSeleccion(datos.vista.tipoReporte); setFiltros({ ...FILTROS_INICIALES, ...datos.vista.filtros }); setGrafica(datos.vista.tipoGrafica); setColumnas(datos.vista.columnasVisibles); setOrden(datos.vista.orden || ''); setResultado({ cargando: false, datos: datos.reporte, error: null })
    } catch (error) { setAccionError(error) }
  }

  async function guardarConfiguracion() {
    if (!nombreVista.trim() || !resultado.datos) return
    try {
      const guardada = await guardarVista(seleccion, { nombre: nombreVista.trim(), tipoGrafica: grafica, filtros: resultado.datos.filtrosAplicados, columnasVisibles: columnas, orden })
      setVistas((actual) => [guardada, ...actual]); setNombreVista(''); setAccionError(null)
    } catch (error) { setAccionError(error) }
  }

  async function exportar(formato) {
    try {
      const parametros = parametrosReporte(reporte, filtros); parametros.set('formato', formato); columnas.forEach((columna) => parametros.append('columnas', columna))
      const { blob, nombre } = await descargarReporte(`/reportes/${encodeURIComponent(seleccion)}/exportar?${parametros}`)
      const url = URL.createObjectURL(blob); const enlace = document.createElement('a'); enlace.href = url; enlace.download = nombre; enlace.click(); URL.revokeObjectURL(url)
    } catch (error) { setAccionError(error) }
  }

  function alternarColumna(columna) {
    setColumnas((actual) => {
      const siguiente = actual.includes(columna) ? actual.filter((item) => item !== columna) : [...actual, columna]
      setResultado((estado) => estado.datos ? { ...estado, datos: { ...estado.datos, __columnas: siguiente, __orden: orden } } : estado)
      return siguiente
    })
  }

  function cambiarOrden(siguiente) {
    setOrden(siguiente)
    setResultado((estado) => estado.datos ? { ...estado, datos: { ...estado.datos, __orden: siguiente } } : estado)
  }

  async function consultar(evento) {
    evento?.preventDefault()
    const validacion = validarFiltros(reporte, filtros)
    setErrores(validacion)
    if (Object.keys(validacion).length) return
    consultaActiva.current?.abort()
    const controlador = new AbortController()
    consultaActiva.current = controlador
    setResultado({ cargando: true, datos: null, error: null })
    try {
      const datos = await generarReporte(reporte, filtros, { signal: controlador.signal }); datos.__columnas = COLUMNAS[reporte.tipo] || []; datos.__orden = orden
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos, error: null })
    } catch (error) {
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error })
    }
  }

  const disponibles = new Set(filtrosDisponibles(reporte))
  const errorGeneral = resultado.error
  const reintentarCatalogo = () => setCatalogo((actual) => ({ ...actual, cargando: true, error: null, revision: actual.revision + 1 }))
  const columnasDisponibles = COLUMNAS[seleccion] || []
  const zonasDisponibles = zonas.tipo === seleccion ? zonas.datos : []
  const cargandoZonas = Boolean(seleccion) && zonas.tipo !== seleccion
  const errorZonas = zonas.tipo === seleccion ? zonas.error : null
  return <div className="reportes" aria-busy={catalogo.cargando || resultado.cargando}>
    {catalogo.cargando && <div className="pcard reporte-estado" role="status">Cargando el catálogo de reportes…</div>}
    {catalogo.error && <div className="alert" role="alert"><strong>No se pudo cargar el catálogo.</strong><p>{catalogo.error.message}</p><button className="btn" type="button" onClick={reintentarCatalogo}>Reintentar</button></div>}
    {!catalogo.cargando && !catalogo.error && catalogo.datos.length === 0 && <section className="pcard reporte-estado" role="status"><h2>Sin reportes disponibles</h2><p>Tu perfil no tiene reportes habilitados en este momento.</p></section>}
    {catalogo.datos.length > 0 && <>
      <section className="pcard reporte-catalogo" aria-labelledby="catalogo-reportes"><h2 id="catalogo-reportes">Selecciona un reporte</h2><p className="hint">El catálogo muestra únicamente los reportes habilitados para tu perfil.</p><div className="reporte-opciones" role="radiogroup" aria-label="Catálogo de reportes">{catalogo.datos.map((item) => <button key={item.tipo} type="button" className={`reporte-opcion${seleccion === item.tipo ? ' seleccionada' : ''}`} role="radio" aria-checked={seleccion === item.tipo} onClick={() => elegir(item.tipo)}><strong>{item.titulo}</strong><span>{item.descripcion}</span></button>)}</div></section>
      {reporte && <form className="filters reporte-filtros" onSubmit={consultar} noValidate><div className="reporte-filtros-titulo"><h2>Filtros</h2><p className="hint">Todos son opcionales. El reporte se actualizará solo al pulsar “Generar reporte”.</p></div>{disponibles.has('anio') && <div className={`fld${errores.anio ? ' fld-error' : ''}`}><label htmlFor="reporte-anio">Año</label><input id="reporte-anio" inputMode="numeric" value={filtros.anio} onChange={(e) => setFiltros({ ...filtros, anio: e.target.value })} aria-describedby={errores.anio ? 'error-anio' : undefined} />{errores.anio && <span id="error-anio" className="error-campo">{errores.anio}</span>}</div>}{disponibles.has('zona') && <FiltroZona valor={filtros.zona} zonas={zonasDisponibles} cargando={cargandoZonas} error={errorZonas} errorCampo={errores.zona} onCambio={(zona) => setFiltros({ ...filtros, zona })} />}{disponibles.has('tributo') && <div className={`fld${errores.tributo ? ' fld-error' : ''}`}><label htmlFor="reporte-tributo">Tributo</label><select id="reporte-tributo" value={filtros.tributo} onChange={(e) => setFiltros({ ...filtros, tributo: e.target.value })}><option value="">Todos</option>{TRIBUTOS.map((item) => <option key={item.valor} value={item.valor}>{item.etiqueta}</option>)}</select>{errores.tributo && <span className="error-campo">{errores.tributo}</span>}</div>}{disponibles.has('montoMinimo') && <div className={`fld${errores.montoMinimo ? ' fld-error' : ''}`}><label htmlFor="reporte-monto">Monto mínimo (S/)</label><input id="reporte-monto" type="number" min="0" step="0.01" value={filtros.montoMinimo} onChange={(e) => setFiltros({ ...filtros, montoMinimo: e.target.value })} />{errores.montoMinimo && <span className="error-campo">{errores.montoMinimo}</span>}</div>}<button className="btn primary" type="submit" disabled={resultado.cargando}>{resultado.cargando ? 'Generando…' : 'Generar reporte'}</button></form>}
      <div aria-live="polite">{resultado.cargando && <div className="pcard reporte-estado" role="status">Generando el reporte con la información disponible…</div>}{errorGeneral && <div className="alert" role="alert"><strong>{errorGeneral.estado === 403 ? 'Reporte restringido.' : errorGeneral.estado === 400 ? 'Revisa los filtros.' : 'No se pudo generar el reporte.'}</strong><p>{errorGeneral.message}</p>{errorGeneral.estado === 503 && <button className="btn" type="button" onClick={consultar}>Reintentar</button>}</div>}</div>
      {resultado.datos && <section className="reporte-resultados" aria-labelledby="resultado-reporte"><div className="reporte-resultados-cab"><div><h2 id="resultado-reporte">{resultado.datos.titulo}</h2><p className="hint">Información oficial a la fecha de corte indicada.</p></div><div className="reporte-vistas" role="group" aria-label="Visualización"><button className="btn" type="button" aria-pressed={vista === 'tabla'} onClick={() => setVista('tabla')}>Tabla</button><button className="btn" type="button" aria-pressed={vista === 'barras'} onClick={() => setVista('barras')}>Barras</button></div></div><Resumen datos={resultado.datos} />{resultado.datos.filas.length === 0 ? <div className="pcard reporte-estado" role="status"><h3>Sin resultados</h3><p>No hay contribuyentes que cumplan los criterios seleccionados.</p></div> : vista === 'tabla' ? <TablaReporte datos={resultado.datos} /> : <BarrasReporte datos={resultado.datos} />}</section>}
      {resultado.datos && <section className="pcard reporte-personalizacion" aria-label="Personalizar reporte">
        <h3>Personalizar y reutilizar</h3>
        {disponibles.has('periodo') && <label className="fld">Periodo <input inputMode="numeric" value={filtros.periodo} onChange={(e) => setFiltros({ ...filtros, periodo: e.target.value })} /></label>}
        <div className="reporte-vistas" role="group" aria-label="Tipo de gráfica">
          {GRAFICAS.map((tipo) => <button key={tipo} className="btn" type="button" aria-pressed={grafica === tipo} onClick={() => { setGrafica(tipo); setVista(tipo === 'TABLA' ? 'tabla' : 'barras') }}>{tipo[0] + tipo.slice(1).toLowerCase()}</button>)}
        </div>
        <label className="fld">Orden <select value={orden} onChange={(e) => cambiarOrden(e.target.value)}><option value="">Predeterminado</option><option value="deudaVencida:desc">Deuda: mayor a menor</option><option value="deudaVencida:asc">Deuda: menor a mayor</option><option value="nombre:asc">Nombre: A-Z</option></select></label>
        <fieldset><legend>Columnas visibles</legend>{columnasDisponibles.map((columna) => <label key={columna}><input type="checkbox" checked={columnas.includes(columna)} onChange={() => alternarColumna(columna)} /> {ETIQUETAS[columna]}</label>)}</fieldset>
        <div className="reporte-vistas"><input aria-label="Nombre de la vista" placeholder="Nombre de la vista" value={nombreVista} onChange={(e) => setNombreVista(e.target.value)} /><button className="btn" type="button" onClick={guardarConfiguracion} disabled={!nombreVista.trim() || columnas.length === 0}>Guardar vista</button><button className="btn" type="button" onClick={() => exportar('EXCEL')}>Exportar Excel</button><button className="btn" type="button" onClick={() => exportar('PDF')}>Exportar PDF</button></div>
        {accionError && <div className="alert" role="alert">{accionError.message}</div>}
      </section>}
      {vistas.length > 0 && <section className="pcard"><h3>Vistas guardadas</h3><ul>{vistas.map((vistaGuardada) => <li key={vistaGuardada.idVista}><button className="btn" type="button" onClick={() => abrirConfiguracion(vistaGuardada)}>{vistaGuardada.nombre} ({vistaGuardada.tipoReporte})</button></li>)}</ul></section>}
    </>}
  </div>
}
