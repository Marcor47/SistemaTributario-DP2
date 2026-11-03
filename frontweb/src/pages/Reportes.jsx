import { useEffect, useMemo, useRef, useState } from 'react'
import { fecha, moneda } from '../utils/formato.js'
import { ETAPAS_COBRANZA, etiquetaEtapa, etiquetaTipoDocumento, etiquetaTributo, filtrosDisponibles, limpiarFiltros, metricaReporte, opcionesZona, parametrosReporte, porcentaje, TIPOS_DOCUMENTO, TRIBUTOS, validarFiltros } from '../utils/reportes.js'
import { abrirVista, descargarReporte, generarReporte, guardarVista, listarVistas, listarZonas, obtenerCatalogoReportes } from '../services/reporteService.js'
import './Reportes.css'

const FILTROS_INICIALES = { anio: '', periodo: '', zona: '', tributo: '', regimen: '', tipoDocumento: '', etapa: '', montoMinimo: '' }
const GRAFICAS = ['TABLA', 'BARRAS']
const ETIQUETAS_GRAFICA = { TABLA: 'Tabla', BARRAS: 'Barras' }
const ORDENES = {
  MOROSOS: [['deudaVencida:desc', 'Deuda: mayor a menor'], ['deudaVencida:asc', 'Deuda: menor a mayor'], ['nombre:asc', 'Nombre: A-Z']],
  PRICOS: [['emitidoAnio:desc', 'Emitido: mayor a menor'], ['emitidoAnio:asc', 'Emitido: menor a mayor'], ['nombre:asc', 'Nombre: A-Z']],
  BUENOS_PAGADORES: [['pagadoAnio:desc', 'Pagado: mayor a menor'], ['pagadoAnio:asc', 'Pagado: menor a mayor'], ['nombre:asc', 'Nombre: A-Z']],
}
const COLUMNAS = {
  MOROSOS: ['codigo', 'razonSocial', 'regimen', 'tipoDocumento', 'numeroDocumento', 'via', 'numero', 'departamento', 'interior', 'manzana', 'lote', 'distrito', 'deudaVencida', 'deudaSinBeneficio', 'condicion', 'tipoPersona', 'correo', 'telefonos'],
  PRICOS: ['codigo', 'nombre', 'tipoPersona', 'zonas', 'emitidoAnio', 'pagadoAnio', 'porcentajePagado', 'deudaVencida'],
  BUENOS_PAGADORES: ['codigo', 'nombre', 'zonas', 'cuotasPagadasATiempo', 'pagadoAnio'],
}
const ETIQUETAS = { codigo: 'Código', nombre: 'Contribuyente', razonSocial: 'Razón social', regimen: 'Régimen', tipoPersona: 'Tipo persona', tipoDocumento: 'Tipo doc.', numeroDocumento: 'Documento', via: 'Vía', numero: 'N°', departamento: 'Dpto', interior: 'Interior', manzana: 'Mz', lote: 'Lote', distrito: 'Distrito', condicion: 'Condición', correo: 'Email', telefonos: 'Teléfonos', zonas: 'Zonas', deudaSinBeneficio: 'Deuda sin beneficio', deudaVencida: 'Deuda vencida', cuotasVencidas: 'Cuotas vencidas', nombreEtapa: 'Etapa', emitidoAnio: 'Emitido', pagadoAnio: 'Pagado', porcentajePagado: '% pagado', cuotasPagadasATiempo: 'Cuotas a tiempo' }
const ETIQUETAS_CABECERA = { ...ETIQUETAS, numeroDocumento: 'N° doc.', deudaVencida: 'Total con beneficio', deudaSinBeneficio: 'Total sin beneficio' }
const COLUMNAS_MONTO_MOROSOS = new Set(['deudaVencida', 'deudaSinBeneficio'])

function periodosRecientes(cantidad = 6) {
  const actual = new Date().getFullYear()
  return Array.from({ length: cantidad }, (_, indice) => actual - indice)
}

function opcionesDesdeMorosos(filas) {
  const etapasPresentes = new Set(filas.map((fila) => fila.etapaCobranza).filter(Boolean))
  const tiposPresentes = new Set(filas.map((fila) => fila.tipoDocumento).filter(Boolean))
  const periodos = new Set()
  for (const fila of filas) {
    const anioVencimiento = Number(String(fila.vencimientoMasAntiguo || '').slice(0, 4))
    if (Number.isInteger(anioVencimiento)) periodos.add(anioVencimiento)
    try {
      const detalle = JSON.parse(fila.detalleDeuda || '{}')
      for (const grupo of ['conBeneficio', 'sinBeneficio']) {
        for (const item of detalle[grupo] || []) {
          const anio = Number(item.anio)
          if (Number.isInteger(anio)) periodos.add(anio)
        }
      }
    } catch {
      // Un detalle ilegible no debe impedir que se muestren los demás filtros.
    }
  }
  return {
    regimenes: [...new Set(filas.map((fila) => fila.regimen).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es')),
    tiposDocumento: TIPOS_DOCUMENTO.filter(({ valor }) => tiposPresentes.has(valor)),
    etapas: ETAPAS_COBRANZA.filter(({ valor }) => etapasPresentes.has(valor)),
    periodos: [...periodos].sort((a, b) => b - a),
  }
}

function graficaVisible(tipo) {
  return tipo === 'TABLA' ? 'TABLA' : 'BARRAS'
}

function valorColumna(fila, columna) {
  const valor = columna === 'zonas' ? fila.zonas?.join(', ') : fila[columna]
  return ['deudaSinBeneficio', 'deudaVencida', 'emitidoAnio', 'pagadoAnio'].includes(columna) ? moneda(valor) : columna === 'porcentajePagado' ? porcentaje(valor) : (valor ?? '—')
}

function Identificacion({ fila }) {
  return <><strong>{fila.nombre}</strong><span className="reporte-secundario">{fila.tipoDocumento}: {fila.numeroDocumento}</span><span className="reporte-secundario">Código {fila.codigo}</span></>
}

function textoCabecera(valor) {
  const texto = valor == null ? '' : String(valor).trim()
  return texto || '-'
}

function montoCabecera(valor) {
  if (valor == null || valor === '') return '-'
  const numero = Number(valor)
  return Number.isFinite(numero) ? moneda(numero) : '-'
}

function filasOrdenadas(filas, orden) {
  const [campo, direccion] = (orden || '').split(':')
  if (!campo) return filas
  return [...filas].sort((a, b) => {
    const uno = a[campo] ?? ''
    const dos = b[campo] ?? ''
    return (uno > dos ? 1 : uno < dos ? -1 : 0) * (direccion === 'asc' ? 1 : -1)
  })
}

function valorCabecera(fila, columna) {
  const domicilio = fila.domicilioFiscalDesglosado || {}
  switch (columna) {
    case 'codigo': return textoCabecera(fila.codigo)
    case 'razonSocial': return textoCabecera(fila.razonSocial || fila.nombre)
    case 'regimen': return textoCabecera(fila.regimen)
    case 'tipoDocumento': return textoCabecera(fila.tipoDocumento)
    case 'numeroDocumento': return textoCabecera(fila.numeroDocumento)
    case 'via': return textoCabecera(domicilio.via)
    case 'numero': return textoCabecera(domicilio.numero)
    case 'departamento': return textoCabecera(domicilio.departamento)
    case 'interior': return textoCabecera(domicilio.interior)
    case 'manzana': return textoCabecera(domicilio.manzana)
    case 'lote': return textoCabecera(domicilio.lote)
    case 'distrito': return textoCabecera(domicilio.distrito)
    case 'deudaVencida': return montoCabecera(fila.deudaVencida)
    case 'deudaSinBeneficio': return montoCabecera(fila.deudaSinBeneficio)
    case 'condicion': return textoCabecera(fila.condicion)
    case 'tipoPersona': return textoCabecera(fila.tipoPersona)
    case 'correo': return textoCabecera(fila.correo)
    case 'telefonos': return textoCabecera(fila.telefonos)
    default: return '-'
  }
}

function claseCabecera(columna) {
  if (COLUMNAS_MONTO_MOROSOS.has(columna)) return 'num'
  if (columna === 'codigo') return 'reporte-col-codigo'
  if (columna === 'razonSocial') return 'reporte-col-nombre'
  return undefined
}

function TablaCabeceraMorosos({ datos, columnas, orden }) {
  const seleccionadas = new Set(columnas?.length ? columnas : COLUMNAS.MOROSOS)
  const columnasSeleccionadas = COLUMNAS.MOROSOS.filter((columna) => seleccionadas.has(columna))
  const visibles = columnasSeleccionadas.length ? columnasSeleccionadas : COLUMNAS.MOROSOS
  const filas = filasOrdenadas(datos.filas, orden)
  return <div className="tabla-scroll" tabIndex="0" role="region" aria-label={`${datos.titulo} en tabla cabecera`}>
    <table className="tabla reporte-tabla reporte-tabla-cabecera">
      <caption className="sr-only">Cabecera de reporte de morosos</caption>
      <thead><tr>
        {visibles.map((columna) => <th key={columna} scope="col" className={claseCabecera(columna)}>{ETIQUETAS_CABECERA[columna]}</th>)}
      </tr></thead>
      <tbody>
        {filas.map((fila) => <tr key={fila.codigo}>
          {visibles.map((columna) => {
            const valor = valorCabecera(fila, columna)
            const esMonto = COLUMNAS_MONTO_MOROSOS.has(columna)
            return <td key={columna} className={claseCabecera(columna)}>{esMonto ? <strong>{valor}</strong> : valor}</td>
          })}
        </tr>)}
      </tbody>
    </table>
  </div>
}

function TablaPricos({ filas }) {
  return <table className="tabla reporte-tabla"><caption className="sr-only">Principales contribuyentes</caption><thead><tr><th scope="col">Contribuyente</th><th scope="col">Tipo</th><th scope="col">Zonas</th><th scope="col" className="num">Emitido</th><th scope="col" className="num">Pagado</th><th scope="col" className="num">Porcentaje pagado</th><th scope="col" className="num">Deuda vencida</th></tr></thead><tbody>{filas.map((fila) => <tr key={fila.codigo}><th scope="row"><Identificacion fila={fila} /></th><td>{fila.tipoPersona === 'JURIDICA' ? 'Persona jurídica' : 'Persona natural'}</td><td>{fila.zonas?.join(', ') || 'Sin zona'}</td><td className="num">{moneda(fila.emitidoAnio)}</td><td className="num">{moneda(fila.pagadoAnio)}</td><td className="num">{porcentaje(fila.porcentajePagado)}</td><td className="num">{moneda(fila.deudaVencida)}</td></tr>)}</tbody></table>
}

function TablaBuenosPagadores({ filas }) {
  return <table className="tabla reporte-tabla"><caption className="sr-only">Buenos pagadores</caption><thead><tr><th scope="col">Contribuyente</th><th scope="col">Zonas</th><th scope="col" className="num">Cuotas pagadas a tiempo</th><th scope="col" className="num">Monto pagado</th></tr></thead><tbody>{filas.map((fila) => <tr key={fila.codigo}><th scope="row"><Identificacion fila={fila} /></th><td>{fila.zonas?.join(', ') || 'Sin zona'}</td><td className="num">{fila.cuotasPagadasATiempo}</td><td className="num"><strong>{moneda(fila.pagadoAnio)}</strong></td></tr>)}</tbody></table>
}

function TablaPersonalizada({ datos, columnas, orden }) {
  const filas = filasOrdenadas(datos.filas, orden)
  return <div className="tabla-scroll" tabIndex="0" role="region" aria-label={`${datos.titulo} en tabla`}><table className="tabla reporte-tabla"><caption className="sr-only">{datos.titulo}</caption><thead><tr>{columnas.map((columna) => <th key={columna} scope="col">{ETIQUETAS[columna] || columna}</th>)}</tr></thead><tbody>{filas.map((fila) => <tr key={fila.codigo}>{columnas.map((columna) => <td key={columna}>{valorColumna(fila, columna)}</td>)}</tr>)}</tbody></table></div>
}

function TablaReporte({ datos, columnas, orden }) {
  if (datos.tipoReporte === 'MOROSOS') return <TablaCabeceraMorosos datos={datos} columnas={datos.__columnas || columnas} orden={datos.__orden || orden} />
  if (datos.__columnas?.length) return <TablaPersonalizada datos={datos} columnas={datos.__columnas} orden={datos.__orden} />
  if (columnas?.length) return <TablaPersonalizada datos={datos} columnas={columnas} orden={orden} />
  const Tabla = datos.tipoReporte === 'PRICOS' ? TablaPricos : TablaBuenosPagadores
  return <div className="tabla-scroll" tabIndex="0" role="region" aria-label={`${datos.titulo} en tabla`}><Tabla filas={datos.filas} /></div>
}

function BarrasReporte({ datos }) {
  const maximo = Math.max(...datos.filas.map((fila) => metricaReporte(datos.tipoReporte, fila)), 0)
  return <ol className="reporte-barras" aria-label={`${datos.titulo} en barras`}>{datos.filas.map((fila) => { const valor = metricaReporte(datos.tipoReporte, fila); return <li key={fila.codigo}><div className="reporte-barra-etiqueta"><span><strong>{fila.nombre}</strong><small>Código {fila.codigo}</small></span><strong>{moneda(valor)}</strong></div><div className="reporte-barra" aria-hidden="true"><span style={{ width: `${maximo ? (valor / maximo) * 100 : 0}%` }} /></div></li> })}</ol>
}

function Resumen({ datos }) {
  const filtros = datos.filtrosAplicados || {}
  const aplicados = [filtros.periodo ? `Periodo: ${filtros.periodo}` : filtros.anio && `Año: ${filtros.anio}`, filtros.zona && `Zona: ${filtros.zona}`, filtros.tributo && `Tributo: ${etiquetaTributo(filtros.tributo)}`, filtros.regimen && `Régimen: ${filtros.regimen}`, filtros.tipoDocumento && `Tipo doc.: ${etiquetaTipoDocumento(filtros.tipoDocumento)}`, filtros.etapa && `Etapa: ${etiquetaEtapa(filtros.etapa)}`, filtros.montoMinimo != null && `Monto mínimo: ${moneda(filtros.montoMinimo)}`].filter(Boolean)
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
  const [grafica, setGrafica] = useState('TABLA')
  const [columnas, setColumnas] = useState([])
  const [opcionesMorosos, setOpcionesMorosos] = useState({ regimenes: [], tiposDocumento: [], etapas: [], periodos: [] })
  const [orden, setOrden] = useState('')
  const [vistas, setVistas] = useState([])
  const [nombreVista, setNombreVista] = useState('')
  const [accionError, setAccionError] = useState(null)
  const [zonas, setZonas] = useState({ tipo: '', datos: [], error: null })
  const consultaActiva = useRef(null)
  const cargaInicialMorosos = useRef(false)
  const reporte = useMemo(() => catalogo.datos.find(({ tipo }) => tipo === seleccion), [catalogo.datos, seleccion])

  async function cargarMorososInicial(reporteMorosos) {
    consultaActiva.current?.abort()
    const controlador = new AbortController()
    consultaActiva.current = controlador
    setResultado({ cargando: true, datos: null, error: null })
    try {
      const datos = await generarReporte(reporteMorosos, FILTROS_INICIALES, { signal: controlador.signal })
      datos.__columnas = COLUMNAS[reporteMorosos.tipo] || []
      datos.__orden = ''
      if (controlador.signal.aborted) return
      setResultado({ cargando: false, datos, error: null })
      setOpcionesMorosos(opcionesDesdeMorosos(datos.filas))
    } catch (error) {
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error })
    }
  }

  useEffect(() => {
    const controlador = new AbortController()
    obtenerCatalogoReportes({ signal: controlador.signal }).then((datos) => {
      if (controlador.signal.aborted) return
      setCatalogo((actual) => ({ ...actual, cargando: false, datos, error: null }))
      setSeleccion((actual) => actual && datos.some(({ tipo }) => tipo === actual) ? actual : datos[0]?.tipo || '')
      const morosos = datos.find(({ tipo }) => tipo === 'MOROSOS')
      if (morosos && !cargaInicialMorosos.current) {
        cargaInicialMorosos.current = true
        cargarMorososInicial(morosos)
      }
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
    consultaActiva.current?.abort()
    const siguiente = catalogo.datos.find(({ tipo }) => tipo === nuevo)
    setSeleccion(nuevo); setFiltros((actual) => limpiarFiltros(siguiente, actual)); setErrores({}); setResultado({ cargando: false, datos: null, error: null }); setGrafica('TABLA'); setColumnas([]); setOrden('')
  }

  async function abrirConfiguracion(vistaGuardada) {
    try {
      const datos = await abrirVista(vistaGuardada.idVista)
      const columnasDisponiblesVista = COLUMNAS[datos.vista.tipoReporte] || []
      const columnasValidas = (datos.vista.columnasVisibles || []).filter((columna) => columnasDisponiblesVista.includes(columna))
      const columnasVista = columnasValidas.length ? columnasValidas : columnasDisponiblesVista
      datos.reporte.__columnas = columnasVista; datos.reporte.__orden = datos.vista.orden || ''
      if (datos.vista.tipoReporte === 'MOROSOS') {
        setOpcionesMorosos((actual) => actual.regimenes.length ? actual : opcionesDesdeMorosos(datos.reporte.filas))
      }
      setSeleccion(datos.vista.tipoReporte); setFiltros({ ...FILTROS_INICIALES, ...datos.vista.filtros }); setGrafica(graficaVisible(datos.vista.tipoGrafica)); setColumnas(columnasVista); setOrden(datos.vista.orden || ''); setResultado({ cargando: false, datos: datos.reporte, error: null })
    } catch (error) { setAccionError(error) }
  }

  async function guardarConfiguracion() {
    if (!nombreVista.trim() || !resultado.datos) return
    try {
      const guardada = await guardarVista(seleccion, { nombre: nombreVista.trim(), tipoGrafica: grafica, filtros: resultado.datos.filtrosAplicados, columnasVisibles: columnasEfectivas, orden })
      setVistas((actual) => [guardada, ...actual]); setNombreVista(''); setAccionError(null)
    } catch (error) { setAccionError(error) }
  }

  async function exportar(formato) {
    try {
      const parametros = parametrosReporte(reporte, filtros); parametros.set('formato', formato); columnasEfectivas.forEach((columna) => parametros.append('columnas', columna))
      const { blob, nombre } = await descargarReporte(`/reportes/${encodeURIComponent(seleccion)}/exportar?${parametros}`)
      const url = URL.createObjectURL(blob); const enlace = document.createElement('a'); enlace.href = url; enlace.download = nombre; enlace.click(); URL.revokeObjectURL(url)
    } catch (error) { setAccionError(error) }
  }

  function alternarColumna(columna) {
    setColumnas((actual) => {
      const base = actual.length ? actual : (COLUMNAS[seleccion] || [])
      if (base.includes(columna) && base.length === 1) return actual
      const siguiente = base.includes(columna) ? base.filter((item) => item !== columna) : [...base, columna]
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
      const datos = await generarReporte(reporte, filtros, { signal: controlador.signal })
      datos.__columnas = columnasEfectivas
      datos.__orden = orden
      if (!controlador.signal.aborted) {
        setResultado({ cargando: false, datos, error: null })
        if (reporte.tipo === 'MOROSOS' && !opcionesMorosos.regimenes.length) {
          setOpcionesMorosos(opcionesDesdeMorosos(datos.filas))
        }
      }
    } catch (error) {
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error })
    }
  }

  const disponibles = new Set(filtrosDisponibles(reporte))
  const errorGeneral = resultado.error
  const reintentarCatalogo = () => setCatalogo((actual) => ({ ...actual, cargando: true, error: null, revision: actual.revision + 1 }))
  const columnasDisponibles = COLUMNAS[seleccion] || []
  const columnasEfectivas = columnas.length ? columnas : (COLUMNAS[seleccion] || [])
  const tiposDocumentoVisibles = opcionesMorosos.tiposDocumento.length ? opcionesMorosos.tiposDocumento : TIPOS_DOCUMENTO
  const etapasVisibles = opcionesMorosos.etapas.length ? opcionesMorosos.etapas : ETAPAS_COBRANZA
  const periodosVisibles = opcionesMorosos.periodos.length ? opcionesMorosos.periodos : periodosRecientes()
  const zonasDisponibles = zonas.tipo === seleccion ? zonas.datos : []
  const cargandoZonas = Boolean(seleccion) && zonas.tipo !== seleccion
  const errorZonas = zonas.tipo === seleccion ? zonas.error : null
  return <div className="reportes" aria-busy={catalogo.cargando || resultado.cargando}>
    {catalogo.cargando && <div className="pcard reporte-estado" role="status">Cargando el catálogo de reportes…</div>}
    {catalogo.error && <div className="alert" role="alert"><strong>No se pudo cargar el catálogo.</strong><p>{catalogo.error.message}</p><button className="btn" type="button" onClick={reintentarCatalogo}>Reintentar</button></div>}
    {!catalogo.cargando && !catalogo.error && catalogo.datos.length === 0 && <section className="pcard reporte-estado" role="status"><h2>Sin reportes disponibles</h2><p>Tu perfil no tiene reportes habilitados en este momento.</p></section>}
    {catalogo.datos.length > 0 && <>
      <section className="pcard reporte-catalogo" aria-labelledby="catalogo-reportes"><h2 id="catalogo-reportes">Selecciona un reporte</h2><p className="hint">El catálogo muestra únicamente los reportes habilitados para tu perfil.</p><div className="reporte-opciones" role="radiogroup" aria-label="Catálogo de reportes">{catalogo.datos.map((item) => <button key={item.tipo} type="button" className={`reporte-opcion${seleccion === item.tipo ? ' seleccionada' : ''}`} role="radio" aria-checked={seleccion === item.tipo} onClick={() => elegir(item.tipo)}><strong>{item.titulo}</strong><span>{item.descripcion}</span></button>)}</div></section>
      {reporte && <section className="pcard reporte-configuracion" aria-labelledby="configuracion-reporte">
        <form className="reporte-configuracion-form" onSubmit={consultar} noValidate>
          <div className="reporte-configuracion-cab">
            <div>
              <h2 id="configuracion-reporte">Filtros y personalización</h2>
              <p className="hint">Configura los filtros y cómo quieres ver el reporte. Los filtros son opcionales; se aplicarán al pulsar “Generar reporte”.</p>
            </div>
            <button className="btn primary reporte-generar" type="submit" disabled={resultado.cargando}>{resultado.cargando ? 'Generando…' : 'Generar reporte'}</button>
          </div>

          <div className="reporte-configuracion-grid">
            <fieldset className="reporte-config-grupo">
              <legend>Filtros</legend>
              <div className="reporte-config-campos">
                {disponibles.has('anio') && <div className={`fld${errores.anio ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-anio">Año</label>
                  <input id="reporte-anio" inputMode="numeric" placeholder="Ej. 2026" value={filtros.anio} onChange={(e) => setFiltros({ ...filtros, anio: e.target.value })} aria-describedby={errores.anio ? 'error-anio' : undefined} />
                  {errores.anio && <span id="error-anio" className="error-campo">{errores.anio}</span>}
                </div>}
                {disponibles.has('periodo') && <div className={`fld${errores.periodo ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-periodo">Periodo</label>
                  <select id="reporte-periodo" value={filtros.periodo} onChange={(e) => setFiltros({ ...filtros, periodo: e.target.value })} aria-describedby={errores.periodo ? 'error-periodo' : undefined}>
                    <option value="">Todos</option>
                    {periodosVisibles.map((periodo) => <option key={periodo} value={periodo}>{periodo}</option>)}
                  </select>
                  {errores.periodo && <span id="error-periodo" className="error-campo">{errores.periodo}</span>}
                </div>}
                {disponibles.has('zona') && <FiltroZona valor={filtros.zona} zonas={zonasDisponibles} cargando={cargandoZonas} error={errorZonas} errorCampo={errores.zona} onCambio={(zona) => setFiltros({ ...filtros, zona })} />}
                {disponibles.has('tributo') && <div className={`fld${errores.tributo ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-tributo">Tributo</label>
                  <select id="reporte-tributo" value={filtros.tributo} onChange={(e) => setFiltros({ ...filtros, tributo: e.target.value })}>
                    <option value="">Todos</option>
                    {TRIBUTOS.map((item) => <option key={item.valor} value={item.valor}>{item.etiqueta}</option>)}
                  </select>
                  {errores.tributo && <span className="error-campo">{errores.tributo}</span>}
                </div>}
                {disponibles.has('regimen') && <div className={`fld${errores.regimen ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-regimen">Régimen</label>
                  <select id="reporte-regimen" value={filtros.regimen} onChange={(e) => setFiltros({ ...filtros, regimen: e.target.value })}>
                    <option value="">Todos</option>
                    {opcionesMorosos.regimenes.map((regimen) => <option key={regimen} value={regimen}>{regimen}</option>)}
                  </select>
                  {errores.regimen && <span className="error-campo">{errores.regimen}</span>}
                </div>}
                {disponibles.has('tipoDocumento') && <div className={`fld${errores.tipoDocumento ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-tipo-documento">Tipo de documento</label>
                  <select id="reporte-tipo-documento" value={filtros.tipoDocumento} onChange={(e) => setFiltros({ ...filtros, tipoDocumento: e.target.value })}>
                    <option value="">Todos</option>
                    {tiposDocumentoVisibles.map((item) => <option key={item.valor} value={item.valor}>{item.etiqueta}</option>)}
                  </select>
                  {errores.tipoDocumento && <span className="error-campo">{errores.tipoDocumento}</span>}
                </div>}
                {disponibles.has('etapa') && <div className={`fld${errores.etapa ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-etapa">Etapa de cobranza</label>
                  <select id="reporte-etapa" value={filtros.etapa} onChange={(e) => setFiltros({ ...filtros, etapa: e.target.value })}>
                    <option value="">Todas</option>
                    {etapasVisibles.map((item) => <option key={item.valor} value={item.valor}>{item.etiqueta}</option>)}
                  </select>
                  {errores.etapa && <span className="error-campo">{errores.etapa}</span>}
                </div>}
                {disponibles.has('montoMinimo') && <div className={`fld${errores.montoMinimo ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-monto">Monto mínimo (S/)</label>
                  <input id="reporte-monto" type="number" min="0" step="0.01" placeholder="0.00" value={filtros.montoMinimo} onChange={(e) => setFiltros({ ...filtros, montoMinimo: e.target.value })} />
                  {errores.montoMinimo && <span className="error-campo">{errores.montoMinimo}</span>}
                </div>}
              </div>
            </fieldset>

            <fieldset className="reporte-config-grupo reporte-config-grupo--personalizacion">
              <legend>Personalización</legend>
              <div className="reporte-config-campos reporte-config-campos--personalizacion">
                <div className="fld reporte-campo-grafica">
                  <span className="fld-etiqueta" id="reporte-grafica-label">Tipo de gráfica</span>
                  <div className="reporte-vistas" role="group" aria-labelledby="reporte-grafica-label">
                    {GRAFICAS.map((tipo) => <button key={tipo} className="btn" type="button" aria-pressed={grafica === tipo} onClick={() => setGrafica(tipo)}>{ETIQUETAS_GRAFICA[tipo]}</button>)}
                  </div>
                </div>
                <div className="fld">
                  <label htmlFor="reporte-orden">Orden</label>
                  <select id="reporte-orden" value={orden} onChange={(e) => cambiarOrden(e.target.value)}>
                    <option value="">Predeterminado</option>
                    {(ORDENES[seleccion] || ORDENES.MOROSOS).map(([valor, etiqueta]) => <option key={valor} value={valor}>{etiqueta}</option>)}
                  </select>
                </div>
                <fieldset className="reporte-columnas">
                  <legend>Columnas visibles</legend>
                  <div className="reporte-columnas-grid">
                    {columnasDisponibles.map((columna) => <label className="reporte-check" key={columna}>
                      <input type="checkbox" checked={columnasEfectivas.includes(columna)} onChange={() => alternarColumna(columna)} />
                      <span>{seleccion === 'MOROSOS' ? ETIQUETAS_CABECERA[columna] : ETIQUETAS[columna]}</span>
                    </label>)}
                  </div>
                </fieldset>
              </div>
            </fieldset>
          </div>

          <div className="reporte-config-acciones">
            <div className="reporte-config-guardar">
              <div className="fld reporte-campo-vista">
                <label htmlFor="reporte-nombre-vista">Nombre de la vista</label>
                <input id="reporte-nombre-vista" placeholder="Ej. Morosos zona 2" value={nombreVista} onChange={(e) => setNombreVista(e.target.value)} />
              </div>
              <button className="btn" type="button" onClick={guardarConfiguracion} disabled={!resultado.datos || !nombreVista.trim() || columnasEfectivas.length === 0}>Guardar vista</button>
            </div>
            <div className="reporte-config-exportar">
              <button className="btn" type="button" onClick={() => exportar('EXCEL')} disabled={!resultado.datos}>Exportar Excel</button>
              <button className="btn" type="button" onClick={() => exportar('PDF')} disabled={!resultado.datos}>Exportar PDF</button>
            </div>
          </div>
          {!resultado.datos && <p className="hint reporte-config-ayuda">Genera el reporte para habilitar “Guardar vista” y la exportación.</p>}
          {accionError && <div className="alert" role="alert">{accionError.message}</div>}
        </form>
      </section>}
      <div aria-live="polite">{resultado.cargando && <div className="pcard reporte-estado" role="status">Generando el reporte con la información disponible…</div>}{errorGeneral && <div className="alert" role="alert"><strong>{errorGeneral.estado === 403 ? 'Reporte restringido.' : errorGeneral.estado === 400 ? 'Revisa los filtros.' : 'No se pudo generar el reporte.'}</strong><p>{errorGeneral.message}</p>{errorGeneral.estado === 503 && <button className="btn" type="button" onClick={consultar}>Reintentar</button>}</div>}</div>
      {resultado.datos && <section className="reporte-resultados" aria-labelledby="resultado-reporte"><div className="reporte-resultados-cab"><div><h2 id="resultado-reporte">{resultado.datos.titulo}</h2><p className="hint">Información oficial a la fecha de corte indicada.</p></div></div><Resumen datos={resultado.datos} />{resultado.datos.filas.length === 0 ? <div className="pcard reporte-estado" role="status"><h3>Sin resultados</h3><p>No hay contribuyentes que cumplan los criterios seleccionados.</p></div> : grafica === 'TABLA' ? <TablaReporte datos={resultado.datos} /> : <BarrasReporte datos={resultado.datos} />}</section>}
      {vistas.length > 0 && <section className="pcard"><h3>Vistas guardadas</h3><ul>{vistas.map((vistaGuardada) => <li key={vistaGuardada.idVista}><button className="btn" type="button" onClick={() => abrirConfiguracion(vistaGuardada)}>{vistaGuardada.nombre} ({vistaGuardada.tipoReporte})</button></li>)}</ul></section>}
    </>}
  </div>
}
