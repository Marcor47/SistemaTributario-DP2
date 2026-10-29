import { useEffect, useMemo, useRef, useState } from 'react'
import { fecha, moneda } from '../utils/formato.js'
import { etiquetaTributo, filtrosDisponibles, limpiarFiltros, metricaReporte, porcentaje, TRIBUTOS, validarFiltros } from '../utils/reportes.js'
import { generarReporte, obtenerCatalogoReportes } from '../services/reporteService.js'
import './Reportes.css'

const FILTROS_INICIALES = { anio: '', zona: '', tributo: '', montoMinimo: '' }

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

function TablaReporte({ datos }) {
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

export default function Reportes() {
  const [catalogo, setCatalogo] = useState({ cargando: true, datos: [], error: null, revision: 0 })
  const [seleccion, setSeleccion] = useState('')
  const [filtros, setFiltros] = useState(FILTROS_INICIALES)
  const [errores, setErrores] = useState({})
  const [resultado, setResultado] = useState({ cargando: false, datos: null, error: null })
  const [vista, setVista] = useState('tabla')
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

  useEffect(() => () => consultaActiva.current?.abort(), [])

  function elegir(nuevo) {
    const siguiente = catalogo.datos.find(({ tipo }) => tipo === nuevo)
    setSeleccion(nuevo); setFiltros((actual) => limpiarFiltros(siguiente, actual)); setErrores({}); setResultado({ cargando: false, datos: null, error: null }); setVista('tabla')
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
      const datos = await generarReporte(reporte, filtros, { signal: controlador.signal })
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos, error: null })
    } catch (error) {
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error })
    }
  }

  const disponibles = new Set(filtrosDisponibles(reporte))
  const errorGeneral = resultado.error
  const reintentarCatalogo = () => setCatalogo((actual) => ({ ...actual, cargando: true, error: null, revision: actual.revision + 1 }))
  return <div className="reportes" aria-busy={catalogo.cargando || resultado.cargando}>
    {catalogo.cargando && <div className="pcard reporte-estado" role="status">Cargando el catálogo de reportes…</div>}
    {catalogo.error && <div className="alert" role="alert"><strong>No se pudo cargar el catálogo.</strong><p>{catalogo.error.message}</p><button className="btn" type="button" onClick={reintentarCatalogo}>Reintentar</button></div>}
    {!catalogo.cargando && !catalogo.error && catalogo.datos.length === 0 && <section className="pcard reporte-estado" role="status"><h2>Sin reportes disponibles</h2><p>Tu perfil no tiene reportes habilitados en este momento.</p></section>}
    {catalogo.datos.length > 0 && <>
      <section className="pcard reporte-catalogo" aria-labelledby="catalogo-reportes"><h2 id="catalogo-reportes">Selecciona un reporte</h2><p className="hint">El catálogo muestra únicamente los reportes habilitados para tu perfil.</p><div className="reporte-opciones" role="radiogroup" aria-label="Catálogo de reportes">{catalogo.datos.map((item) => <button key={item.tipo} type="button" className={`reporte-opcion${seleccion === item.tipo ? ' seleccionada' : ''}`} role="radio" aria-checked={seleccion === item.tipo} onClick={() => elegir(item.tipo)}><strong>{item.titulo}</strong><span>{item.descripcion}</span></button>)}</div></section>
      {reporte && <form className="filters reporte-filtros" onSubmit={consultar} noValidate><div className="reporte-filtros-titulo"><h2>Filtros</h2><p className="hint">Todos son opcionales. El reporte se actualizará solo al pulsar “Generar reporte”.</p></div>{disponibles.has('anio') && <div className={`fld${errores.anio ? ' fld-error' : ''}`}><label htmlFor="reporte-anio">Año</label><input id="reporte-anio" inputMode="numeric" value={filtros.anio} onChange={(e) => setFiltros({ ...filtros, anio: e.target.value })} aria-describedby={errores.anio ? 'error-anio' : undefined} />{errores.anio && <span id="error-anio" className="error-campo">{errores.anio}</span>}</div>}{disponibles.has('zona') && <div className={`fld${errores.zona ? ' fld-error' : ''}`}><label htmlFor="reporte-zona">Zona</label><input id="reporte-zona" maxLength="20" placeholder="Ej. Z2" value={filtros.zona} onChange={(e) => setFiltros({ ...filtros, zona: e.target.value })} />{errores.zona && <span className="error-campo">{errores.zona}</span>}</div>}{disponibles.has('tributo') && <div className={`fld${errores.tributo ? ' fld-error' : ''}`}><label htmlFor="reporte-tributo">Tributo</label><select id="reporte-tributo" value={filtros.tributo} onChange={(e) => setFiltros({ ...filtros, tributo: e.target.value })}><option value="">Todos</option>{TRIBUTOS.map((item) => <option key={item.valor} value={item.valor}>{item.etiqueta}</option>)}</select>{errores.tributo && <span className="error-campo">{errores.tributo}</span>}</div>}{disponibles.has('montoMinimo') && <div className={`fld${errores.montoMinimo ? ' fld-error' : ''}`}><label htmlFor="reporte-monto">Monto mínimo (S/)</label><input id="reporte-monto" type="number" min="0" step="0.01" value={filtros.montoMinimo} onChange={(e) => setFiltros({ ...filtros, montoMinimo: e.target.value })} />{errores.montoMinimo && <span className="error-campo">{errores.montoMinimo}</span>}</div>}<button className="btn primary" type="submit" disabled={resultado.cargando}>{resultado.cargando ? 'Generando…' : 'Generar reporte'}</button></form>}
      <div aria-live="polite">{resultado.cargando && <div className="pcard reporte-estado" role="status">Generando el reporte con la información disponible…</div>}{errorGeneral && <div className="alert" role="alert"><strong>{errorGeneral.estado === 403 ? 'Reporte restringido.' : errorGeneral.estado === 400 ? 'Revisa los filtros.' : 'No se pudo generar el reporte.'}</strong><p>{errorGeneral.message}</p>{errorGeneral.estado === 503 && <button className="btn" type="button" onClick={consultar}>Reintentar</button>}</div>}</div>
      {resultado.datos && <section className="reporte-resultados" aria-labelledby="resultado-reporte"><div className="reporte-resultados-cab"><div><h2 id="resultado-reporte">{resultado.datos.titulo}</h2><p className="hint">Información oficial a la fecha de corte indicada.</p></div><div className="reporte-vistas" role="group" aria-label="Visualización"><button className="btn" type="button" aria-pressed={vista === 'tabla'} onClick={() => setVista('tabla')}>Tabla</button><button className="btn" type="button" aria-pressed={vista === 'barras'} onClick={() => setVista('barras')}>Barras</button></div></div><Resumen datos={resultado.datos} />{resultado.datos.filas.length === 0 ? <div className="pcard reporte-estado" role="status"><h3>Sin resultados</h3><p>No hay contribuyentes que cumplan los criterios seleccionados.</p></div> : vista === 'tabla' ? <TablaReporte datos={resultado.datos} /> : <BarrasReporte datos={resultado.datos} />}</section>}
    </>}
  </div>
}
