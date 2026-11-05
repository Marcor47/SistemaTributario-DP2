import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Reorder } from 'framer-motion'
import { fecha, fechaHora, moneda } from '../utils/formato.js'
import { ETAPAS_COBRANZA, etiquetaBeneficio, etiquetaEtapa, etiquetaTipoDocumento, etiquetaTributo, FILTROS_BENEFICIO, filtrosDisponibles, limpiarFiltros, metricaReporte, opcionesZona, parametrosReporte, porcentaje, TIPOS_DOCUMENTO, TRIBUTOS, validarFiltros } from '../utils/reportes.js'
import { abrirVista, descargarReporte, eliminarVista, exportarVista, generarReporte, guardarVista, listarVistas, listarZonas, obtenerCatalogoReportes } from '../services/reporteService.js'
import ModalConfirmacion from '../components/ModalConfirmacion.jsx'
import ReportePricos from './ReportePricos.jsx'
import './Reportes.css'

const ANIO_ACTUAL = new Date().getFullYear()
const FILTROS_INICIALES = { anio: '', anioDesde: '1900', anioHasta: String(ANIO_ACTUAL), zona: '', tributo: '', regimen: '', tipoDocumento: '', etapa: '', beneficio: 'AMBOS', montoMinimo: '' }
const ORDENES = {
  MOROSOS: [['deudaVencida:desc', 'Deuda: mayor a menor'], ['deudaVencida:asc', 'Deuda: menor a mayor'], ['nombre:asc', 'Nombre: A-Z']],
  PRICOS: [['emitidoAnio:desc', 'Emitido: mayor a menor'], ['emitidoAnio:asc', 'Emitido: menor a mayor'], ['nombre:asc', 'Nombre: A-Z']],
  BUENOS_PAGADORES: [['pagadoAnio:desc', 'Pagado: mayor a menor'], ['pagadoAnio:asc', 'Pagado: menor a mayor'], ['nombre:asc', 'Nombre: A-Z']],
}
const COLUMNAS = {
  MOROSOS: ['codigo', 'razonSocial', 'regimen', 'tipoDocumento', 'numeroDocumento', 'via', 'numero', 'departamento', 'interior', 'manzana', 'lote', 'distrito', 'zonas', 'deudaVencida', 'deudaSinBeneficio', 'condicion', 'tipoPersona', 'correo', 'telefonos'],
  PRICOS: ['codigo', 'nombre', 'tipoPersona', 'zonas', 'emitidoAnio', 'pagadoAnio', 'porcentajePagado', 'deudaVencida'],
  BUENOS_PAGADORES: ['codigo', 'nombre', 'zonas', 'cuotasPagadasATiempo', 'pagadoAnio'],
}
const COLUMNAS_DETALLE_MOROSOS = ['anio', 'codigoPredio', 'zona', 'tributo', 'periodo', 'fechaVencimiento', 'diasVencido', 'etapaCobranza', 'montoSinBeneficio', 'montoConBeneficio']
const ETIQUETAS = { codigo: 'Código', nombre: 'Contribuyente', razonSocial: 'Razón social', regimen: 'Régimen', tipoPersona: 'Tipo persona', tipoDocumento: 'Tipo doc.', numeroDocumento: 'Documento', via: 'Vía', numero: 'N°', departamento: 'Dpto', interior: 'Interior', manzana: 'Mz', lote: 'Lote', distrito: 'Distrito', condicion: 'Condición', correo: 'Email', telefonos: 'Teléfonos', zonas: 'Zonas', deudaSinBeneficio: 'Deuda sin beneficio', deudaVencida: 'Deuda vencida', cuotasVencidas: 'Cuotas vencidas', nombreEtapa: 'Etapa', emitidoAnio: 'Emitido', pagadoAnio: 'Pagado', porcentajePagado: '% pagado', cuotasPagadasATiempo: 'Cuotas a tiempo' }
const ETIQUETAS_CABECERA = { ...ETIQUETAS, numeroDocumento: 'N° doc.', deudaVencida: 'Total con beneficio', deudaSinBeneficio: 'Total sin beneficio' }
const ETIQUETAS_DETALLE = { anio: 'Año', codigoPredio: 'Predio', zona: 'Zona', tributo: 'Tributo', periodo: 'Cuota', fechaVencimiento: 'Vencimiento', diasVencido: 'Días vencido', etapaCobranza: 'Etapa', montoSinBeneficio: 'Sin beneficio', montoConBeneficio: 'Con beneficio' }
const COLUMNAS_MONTO_MOROSOS = new Set(['deudaVencida', 'deudaSinBeneficio'])
const COLUMNAS_MONTO_DETALLE = new Set(['montoSinBeneficio', 'montoConBeneficio'])

function opcionesDesdeMorosos(filas) {
  const etapasPresentes = new Set(filas.map((fila) => fila.etapaCobranza).filter(Boolean))
  const tiposPresentes = new Set(filas.map((fila) => fila.tipoDocumento).filter(Boolean))
  return {
    regimenes: [...new Set(filas.map((fila) => fila.regimen).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es')),
    tiposDocumento: TIPOS_DOCUMENTO.filter(({ valor }) => tiposPresentes.has(valor)),
    etapas: ETAPAS_COBRANZA.filter(({ valor }) => etapasPresentes.has(valor)),
  }
}

function graficaVisible(tipo) {
  return tipo === 'TABLA' ? 'TABLA' : 'BARRAS'
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

function fechaCabecera(valor) {
  return valor ? fecha(valor) : '-'
}

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']
const ORDEN_TRIBUTOS = { IMPUESTO_PREDIAL: 0, ARBITRIOS: 1 }

function etiquetaCuota(tributo, periodo) {
  if (periodo == null || periodo === '') return '-'
  const cuota = Number(periodo)
  if (tributo === 'IMPUESTO_PREDIAL' && cuota >= 1 && cuota <= 4) return `${cuota}.° trimestre`
  if (tributo === 'ARBITRIOS' && cuota >= 1 && cuota <= 12) return MESES[cuota - 1]
  return `Cuota ${periodo}`
}

function detalleObligaciones(fila, anioDesde, anioHasta) {
  const detalle = Array.isArray(fila.detalleDeuda) ? fila.detalleDeuda : []
  const filtrado = detalle.filter((item) => {
    const anio = Number(item.anio)
    return Number.isInteger(anio) && anio >= anioDesde && anio <= anioHasta
  })
  return [...filtrado].sort((a, b) => {
    const anioA = Number(a.anio) || 0
    const anioB = Number(b.anio) || 0
    const tributoA = ORDEN_TRIBUTOS[a.tributo] ?? 99
    const tributoB = ORDEN_TRIBUTOS[b.tributo] ?? 99
    const periodoA = Number(a.periodo) || 0
    const periodoB = Number(b.periodo) || 0
    return anioA - anioB || tributoA - tributoB || periodoA - periodoB
      || String(a.codigoPredio || '').localeCompare(String(b.codigoPredio || ''))
  })
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
    case 'zonas': return textoCabecera(fila.zonas?.join(', '))
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

function valorDetalle(item, columna) {
  switch (columna) {
    case 'anio': return textoCabecera(item.anio)
    case 'codigoPredio': return textoCabecera(item.codigoPredio)
    case 'zona': return textoCabecera(item.zona)
    case 'tributo': return etiquetaTributo(item.tributo)
    case 'periodo': return etiquetaCuota(item.tributo, item.periodo)
    case 'fechaVencimiento': return fechaCabecera(item.fechaVencimiento)
    case 'diasVencido': return textoCabecera(item.diasVencido)
    case 'etapaCobranza': return etiquetaEtapa(item.etapaCobranza || 'MORA_TEMPRANA')
    case 'montoSinBeneficio': return montoCabecera(item.montoSinBeneficio)
    case 'montoConBeneficio': return montoCabecera(item.montoConBeneficio)
    default: return '-'
  }
}

function claseDetalle(columna) {
  if (COLUMNAS_MONTO_DETALLE.has(columna) || columna === 'diasVencido') return 'num'
  if (columna === 'tributo') return 'reporte-tributo-detalle'
  return undefined
}

function TablaCabeceraMorosos({ datos, columnas, columnasDetalle, orden, mostrarDetalle }) {
  const columnasPedidas = columnas?.length ? columnas : COLUMNAS.MOROSOS
  const columnasFiltradas = columnasPedidas.filter((columna) => COLUMNAS.MOROSOS.includes(columna))
  const visibles = columnasFiltradas.length ? columnasFiltradas : COLUMNAS.MOROSOS
  const detallePedidas = columnasDetalle?.length ? columnasDetalle : COLUMNAS_DETALLE_MOROSOS
  const detalleFiltradas = detallePedidas.filter((columna) => COLUMNAS_DETALLE_MOROSOS.includes(columna))
  const filas = filasOrdenadas(datos.filas, orden)
  const anioDesde = Number(datos.filtrosAplicados?.anioDesde ?? 1900)
  const anioHasta = Number(datos.filtrosAplicados?.anioHasta ?? ANIO_ACTUAL)
  const mostrarAnio = anioDesde !== anioHasta
  const detalleVisibles = mostrarDetalle
    ? (detalleFiltradas.length ? detalleFiltradas : COLUMNAS_DETALLE_MOROSOS)
      .filter((columna) => columna !== 'anio' || mostrarAnio)
    : []
  return <div className="tabla-scroll" tabIndex="0" role="region" aria-label={`${datos.titulo} en tabla cabecera`}>
    <table className="tabla reporte-tabla reporte-tabla-cabecera">
      <caption className="sr-only">Cabecera de reporte de morosos</caption>
      <thead><tr>
        {visibles.map((columna) => <th key={columna} scope="col" className={claseCabecera(columna)}>{ETIQUETAS_CABECERA[columna]}</th>)}
      </tr></thead>
      <tbody>
        {filas.map((fila) => {
          const obligaciones = detalleObligaciones(fila, anioDesde, anioHasta)
          return <Fragment key={fila.codigo}>
            <tr className="reporte-fila-contribuyente">
              {visibles.map((columna) => {
                const valor = valorCabecera(fila, columna)
                const esMonto = COLUMNAS_MONTO_MOROSOS.has(columna)
                return <td key={columna} className={claseCabecera(columna)}>{esMonto ? <strong>{valor}</strong> : valor}</td>
              })}
            </tr>
            {obligaciones.length > 0 && detalleVisibles.length > 0 && <tr className="reporte-fila-detalle">
              <td colSpan={visibles.length}>
                <div className="reporte-detalle">
                  <table className="tabla reporte-tabla-detalle">
                    <caption className="sr-only">Detalle de obligaciones de {fila.razonSocial || fila.nombre}</caption>
                    <thead><tr>
                      {detalleVisibles.map((columna) => <th key={columna} scope="col" className={claseDetalle(columna)}>{ETIQUETAS_DETALLE[columna]}</th>)}
                    </tr></thead>
                    <tbody>
                      {obligaciones.map((item, indice) => <tr key={`${item.anio}-${item.tributo}-${item.periodo}-${item.codigoPredio}-${indice}`}>
                        {detalleVisibles.map((columna) => {
                          const valor = valorDetalle(item, columna)
                          return <td key={columna} className={claseDetalle(columna)}>{columna === 'montoConBeneficio' ? <strong>{valor}</strong> : valor}</td>
                        })}
                      </tr>)}
                    </tbody>
                  </table>
                </div>
              </td>
            </tr>}
          </Fragment>
        })}
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

function TablaReporte({ datos, columnas, columnasDetalle, orden }) {
  if (datos.tipoReporte === 'MOROSOS') return <TablaCabeceraMorosos datos={datos} columnas={datos.__columnas || columnas} columnasDetalle={columnasDetalle} orden={datos.__orden || orden} mostrarDetalle={datos.__mostrarDetalle !== false} />
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
  const esMorosos = datos.tipoReporte === 'MOROSOS'
  const desde = filtros.anioDesde ?? 1900
  const hasta = filtros.anioHasta ?? ANIO_ACTUAL
  const rango = esMorosos
    ? (Number(desde) === Number(hasta) ? `Año: ${desde}` : `Años: ${desde} - ${hasta}`)
    : filtros.anio && `Año: ${filtros.anio}`
  const beneficio = filtros.beneficio && filtros.beneficio !== 'AMBOS'
    ? `Beneficio: ${etiquetaBeneficio(filtros.beneficio)}`
    : null
  const aplicados = [rango, filtros.zona && `Zona: ${filtros.zona}`, filtros.tributo && `Tributo: ${etiquetaTributo(filtros.tributo)}`, filtros.regimen && `Régimen: ${filtros.regimen}`, filtros.tipoDocumento && `Tipo doc.: ${etiquetaTipoDocumento(filtros.tipoDocumento)}`, filtros.etapa && `Etapa: ${etiquetaEtapa(filtros.etapa)}`, beneficio, filtros.montoMinimo != null && `Monto mínimo: ${moneda(filtros.montoMinimo)}`].filter(Boolean)
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
  const [filtrosAplicados, setFiltrosAplicados] = useState(FILTROS_INICIALES)
  const [errores, setErrores] = useState({})
  const [resultado, setResultado] = useState({ cargando: false, datos: null, error: null })
  const [grafica, setGrafica] = useState('TABLA')
  const [columnas, setColumnas] = useState([])
  const [columnasDetalle, setColumnasDetalle] = useState([])
  const [ordenColumnas, setOrdenColumnas] = useState([])
  const [ordenColumnasDetalle, setOrdenColumnasDetalle] = useState([])
  const [columnasAplicadas, setColumnasAplicadas] = useState([])
  const [columnasDetalleAplicadas, setColumnasDetalleAplicadas] = useState([])
  const [ordenColumnasAplicadas, setOrdenColumnasAplicadas] = useState([])
  const [ordenColumnasDetalleAplicadas, setOrdenColumnasDetalleAplicadas] = useState([])
  const [opcionesMorosos, setOpcionesMorosos] = useState({ regimenes: [], tiposDocumento: [], etapas: [] })
  const [orden, setOrden] = useState('')
  const [ordenAplicado, setOrdenAplicado] = useState('')
  const [detalleActivo, setDetalleActivo] = useState(true)
  const [detalleActivoAplicado, setDetalleActivoAplicado] = useState(true)
  const [vistas, setVistas] = useState([])
  const [nombreVista, setNombreVista] = useState('')
  const [accionError, setAccionError] = useState(null)
  const [confirmacion, setConfirmacion] = useState(null)
  const [procesandoConfirmacion, setProcesandoConfirmacion] = useState(false)
  const [zonas, setZonas] = useState({ tipo: '', datos: [], error: null })
  const consultaActiva = useRef(null)
  const arrastreColumnas = useRef(false)
  const arrastreDetalle = useRef(false)
  const grupoColumnasRef = useRef(null)
  const grupoDetalleRef = useRef(null)
  const [desbordeColumnas, setDesbordeColumnas] = useState({ izquierda: false, derecha: false })
  const [desbordeDetalle, setDesbordeDetalle] = useState({ izquierda: false, derecha: false })
  const reporte = useMemo(() => catalogo.datos.find(({ tipo }) => tipo === seleccion), [catalogo.datos, seleccion])
  const columnasEfectivas = columnas.length ? columnas : (COLUMNAS[seleccion] || [])
  const columnasDetalleEfectivas = columnasDetalle.length
    ? columnasDetalle
    : (seleccion === 'MOROSOS' ? COLUMNAS_DETALLE_MOROSOS : [])
  const ordenColumnasEfectivas = ordenColumnas.length ? ordenColumnas : (COLUMNAS[seleccion] || [])
  const ordenColumnasDetalleEfectivas = ordenColumnasDetalle.length
    ? ordenColumnasDetalle
    : (seleccion === 'MOROSOS' ? COLUMNAS_DETALLE_MOROSOS : [])
  const columnasAplicadasEfectivas = columnasAplicadas.length ? columnasAplicadas : (COLUMNAS[seleccion] || [])
  const columnasDetalleAplicadasEfectivas = columnasDetalleAplicadas.length
    ? columnasDetalleAplicadas
    : (seleccion === 'MOROSOS' ? COLUMNAS_DETALLE_MOROSOS : [])
  const ordenColumnasAplicadasEfectivas = ordenColumnasAplicadas.length
    ? ordenColumnasAplicadas
    : (COLUMNAS[seleccion] || [])
  const ordenColumnasDetalleAplicadasEfectivas = ordenColumnasDetalleAplicadas.length
    ? ordenColumnasDetalleAplicadas
    : (seleccion === 'MOROSOS' ? COLUMNAS_DETALLE_MOROSOS : [])
  const columnasPredeterminadas = COLUMNAS[seleccion] || []
  const detallePredeterminado = seleccion === 'MOROSOS' ? COLUMNAS_DETALLE_MOROSOS : []
  const columnasSonPredeterminadas = ordenColumnasEfectivas.join('|') === columnasPredeterminadas.join('|')
    && columnasEfectivas.length === columnasPredeterminadas.length
  const detalleSonPredeterminados = ordenColumnasDetalleEfectivas.join('|') === detallePredeterminado.join('|')
    && columnasDetalleEfectivas.length === detallePredeterminado.length
  const filtrosSonPredeterminados = Object.keys(FILTROS_INICIALES)
    .every((clave) => String(filtros[clave] ?? '') === String(FILTROS_INICIALES[clave] ?? ''))
  const filtrosCambiados = Object.keys(FILTROS_INICIALES)
    .some((clave) => String(filtros[clave] ?? '') !== String(filtrosAplicados[clave] ?? ''))
  const vistasDelReporte = seleccion ? vistas.filter((vista) => vista.tipoReporte === seleccion) : []
  const hayCambiosSinAplicar = filtrosCambiados
    || columnasEfectivas.join('|') !== columnasAplicadasEfectivas.join('|')
    || ordenColumnasEfectivas.join('|') !== ordenColumnasAplicadasEfectivas.join('|')
    || columnasDetalleEfectivas.join('|') !== columnasDetalleAplicadasEfectivas.join('|')
    || ordenColumnasDetalleEfectivas.join('|') !== ordenColumnasDetalleAplicadasEfectivas.join('|')
    || detalleActivo !== detalleActivoAplicado
    || orden !== ordenAplicado
  const mostrarAvisoCambios = hayCambiosSinAplicar && !resultado.cargando

  const actualizarDesbordeColumnas = useCallback(() => {
    setDesbordeColumnas((actual) => {
      const siguiente = medirDesborde(grupoColumnasRef.current?.querySelector('.reporte-columnas-grid'))
      return actual.izquierda === siguiente.izquierda && actual.derecha === siguiente.derecha ? actual : siguiente
    })
  }, [])

  const actualizarDesbordeDetalle = useCallback(() => {
    setDesbordeDetalle((actual) => {
      const siguiente = medirDesborde(grupoDetalleRef.current?.querySelector('.reporte-columnas-grid'))
      return actual.izquierda === siguiente.izquierda && actual.derecha === siguiente.derecha ? actual : siguiente
    })
  }, [])

  useEffect(() => {
    const actualizar = () => {
      actualizarDesbordeColumnas()
      actualizarDesbordeDetalle()
    }
    const frame = window.requestAnimationFrame(actualizar)
    window.addEventListener('resize', actualizar)
    return () => {
      window.cancelAnimationFrame(frame)
      window.removeEventListener('resize', actualizar)
    }
  }, [seleccion, columnasEfectivas.length, columnasDetalleEfectivas.length, detalleActivo,
    actualizarDesbordeColumnas, actualizarDesbordeDetalle])

  async function cargarMorososInicial(reporteMorosos, filtrosIniciales = FILTROS_INICIALES) {
    consultaActiva.current?.abort()
    const controlador = new AbortController()
    consultaActiva.current = controlador
    setResultado({ cargando: true, datos: null, error: null })
    try {
      const datos = await generarReporte(reporteMorosos, filtrosIniciales, { signal: controlador.signal })
      const columnasIniciales = COLUMNAS[reporteMorosos.tipo] || []
      const detalleIniciales = COLUMNAS_DETALLE_MOROSOS
      datos.__columnas = columnasIniciales
      datos.__orden = ''
      datos.__mostrarDetalle = true
      if (controlador.signal.aborted) return
      setFiltrosAplicados({ ...filtrosIniciales })
      setColumnasAplicadas(columnasIniciales)
      setOrdenColumnasAplicadas(columnasIniciales)
      setColumnasDetalleAplicadas(detalleIniciales)
      setOrdenColumnasDetalleAplicadas(detalleIniciales)
      setDetalleActivo(true)
      setDetalleActivoAplicado(true)
      setOrdenAplicado('')
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
      setSeleccion((actual) => actual && datos.some(({ tipo }) => tipo === actual) ? actual : '')
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
    const limpios = limpiarFiltros(siguiente, filtros)
    const filtrosNuevo = nuevo === 'MOROSOS'
      ? {
          ...limpios,
          anioDesde: limpios.anioDesde || '1900',
          anioHasta: limpios.anioHasta || String(ANIO_ACTUAL),
          beneficio: limpios.beneficio || 'AMBOS',
        }
      : limpios
    setSeleccion(nuevo)
    setFiltros(filtrosNuevo)
    setFiltrosAplicados({ ...filtrosNuevo })
    setErrores({}); setResultado({ cargando: false, datos: null, error: null }); setGrafica('TABLA')
    setColumnas([]); setOrdenColumnas(COLUMNAS[nuevo] || [])
    setColumnasDetalle([]); setOrdenColumnasDetalle(nuevo === 'MOROSOS' ? COLUMNAS_DETALLE_MOROSOS : [])
    setColumnasAplicadas([]); setOrdenColumnasAplicadas(COLUMNAS[nuevo] || [])
    setColumnasDetalleAplicadas([]); setOrdenColumnasDetalleAplicadas(nuevo === 'MOROSOS' ? COLUMNAS_DETALLE_MOROSOS : [])
    setDetalleActivo(true); setDetalleActivoAplicado(true)
    setOrden(''); setOrdenAplicado(''); setNombreVista('')
    if (nuevo === 'MOROSOS' && siguiente) cargarMorososInicial(siguiente, filtrosNuevo)
  }

  function cambiarReporte() {
    consultaActiva.current?.abort()
    setSeleccion('')
    setFiltros(FILTROS_INICIALES)
    setFiltrosAplicados({ ...FILTROS_INICIALES })
    setErrores({})
    setResultado({ cargando: false, datos: null, error: null })
    setColumnas([])
    setColumnasDetalle([])
    setOrdenColumnas([])
    setOrdenColumnasDetalle([])
    setColumnasAplicadas([])
    setColumnasDetalleAplicadas([])
    setOrdenColumnasAplicadas([])
    setOrdenColumnasDetalleAplicadas([])
    setDetalleActivo(true)
    setDetalleActivoAplicado(true)
    setOrden('')
    setOrdenAplicado('')
    setGrafica('TABLA')
    setNombreVista('')
    setAccionError(null)
  }

  async function abrirConfiguracion(vistaGuardada) {
    try {
      const datos = await abrirVista(vistaGuardada.idVista)
      const columnasDisponiblesVista = COLUMNAS[datos.vista.tipoReporte] || []
      const columnasValidas = (datos.vista.columnasVisibles || []).filter((columna) => columnasDisponiblesVista.includes(columna))
      const columnasVista = columnasValidas.length ? columnasValidas : columnasDisponiblesVista
      const ordenGuardadoMain = (datos.vista.ordenColumnas || []).filter((columna) => columnasDisponiblesVista.includes(columna))
      const ordenMainBase = ordenGuardadoMain.length ? ordenGuardadoMain : columnasVista
      const ordenMain = [...ordenMainBase, ...columnasDisponiblesVista.filter((columna) => !ordenMainBase.includes(columna))]
      const visiblesMain = new Set(columnasVista)
      const columnasVistaOrdenadas = ordenMain.filter((columna) => visiblesMain.has(columna))

      const detalleDisponiblesVista = datos.vista.tipoReporte === 'MOROSOS' ? COLUMNAS_DETALLE_MOROSOS : []
      const detalleValidas = (datos.vista.columnasDetalleVisibles || []).filter((columna) => detalleDisponiblesVista.includes(columna))
      const detalleGuardado = datos.vista.columnasDetalleVisibles
      const tieneDetalle = datos.vista.tipoReporte === 'MOROSOS'
        && (detalleGuardado == null || detalleGuardado.length > 0)
      const columnasDetalleVista = detalleValidas.length ? detalleValidas : detalleDisponiblesVista
      const ordenGuardadoDetalle = (datos.vista.ordenColumnasDetalle || []).filter((columna) => detalleDisponiblesVista.includes(columna))
      const ordenDetalleBase = ordenGuardadoDetalle.length ? ordenGuardadoDetalle : columnasDetalleVista
      const ordenDetalle = [...ordenDetalleBase, ...detalleDisponiblesVista.filter((columna) => !ordenDetalleBase.includes(columna))]
      const visiblesDetalle = new Set(columnasDetalleVista)
      const columnasDetalleVistaOrdenadas = ordenDetalle.filter((columna) => visiblesDetalle.has(columna))

      datos.reporte.__columnas = columnasVistaOrdenadas; datos.reporte.__orden = datos.vista.orden || ''
      datos.reporte.__mostrarDetalle = tieneDetalle
      const filtrosVista = { ...FILTROS_INICIALES, ...datos.vista.filtros }
      if (datos.vista.tipoReporte === 'MOROSOS') {
        if ((!filtrosVista.anioDesde || !filtrosVista.anioHasta) && filtrosVista.periodo) {
          filtrosVista.anioDesde = filtrosVista.periodo
          filtrosVista.anioHasta = filtrosVista.periodo
        }
        filtrosVista.anioDesde = filtrosVista.anioDesde || '1900'
        filtrosVista.anioHasta = filtrosVista.anioHasta || String(ANIO_ACTUAL)
        filtrosVista.beneficio = filtrosVista.beneficio || 'AMBOS'
        setOpcionesMorosos((actual) => actual.regimenes.length ? actual : opcionesDesdeMorosos(datos.reporte.filas))
      }
      setSeleccion(datos.vista.tipoReporte); setFiltros(filtrosVista); setFiltrosAplicados({ ...filtrosVista }); setGrafica(graficaVisible(datos.vista.tipoGrafica))
      setColumnas(columnasVistaOrdenadas); setOrdenColumnas(ordenMain)
      setColumnasDetalle(columnasDetalleVistaOrdenadas); setOrdenColumnasDetalle(ordenDetalle)
      setColumnasAplicadas(columnasVistaOrdenadas); setOrdenColumnasAplicadas(ordenMain)
      setColumnasDetalleAplicadas(columnasDetalleVistaOrdenadas); setOrdenColumnasDetalleAplicadas(ordenDetalle)
      setDetalleActivo(tieneDetalle); setDetalleActivoAplicado(tieneDetalle)
      setOrden(datos.vista.orden || ''); setOrdenAplicado(datos.vista.orden || ''); setNombreVista(datos.vista.nombre || '')
      setResultado({ cargando: false, datos: datos.reporte, error: null })
    } catch (error) { setAccionError(error) }
  }

  async function guardarConfiguracion() {
    const nombre = nombreVista.trim()
    if (!nombre || !resultado.datos) return
    const existente = vistasDelReporte.find((vista) => vista.nombre === nombre)
    if (existente) {
      setConfirmacion({ tipo: 'sobrescribir', nombre })
      return
    }
    await ejecutarGuardado(nombre)
  }

  async function ejecutarGuardado(nombre) {
    setProcesandoConfirmacion(true)
    try {
      const guardada = await guardarVista(seleccion, {
        nombre,
        tipoGrafica: grafica,
        filtros: resultado.datos.filtrosAplicados,
        columnasVisibles: columnasAplicadasEfectivas,
        columnasDetalleVisibles: detalleActivoAplicado ? columnasDetalleAplicadasEfectivas : [],
        ordenColumnas: ordenColumnasAplicadasEfectivas,
        ordenColumnasDetalle: detalleActivoAplicado ? ordenColumnasDetalleAplicadasEfectivas : [],
        orden: ordenAplicado,
      })
      setVistas((actual) => actual.some((vista) => vista.idVista === guardada.idVista)
        ? actual.map((vista) => vista.idVista === guardada.idVista ? guardada : vista)
        : [guardada, ...actual])
      setNombreVista(''); setAccionError(null); setConfirmacion(null)
    } catch (error) {
      setAccionError(error); setConfirmacion(null)
    } finally {
      setProcesandoConfirmacion(false)
    }
  }

  function seleccionarVista(nombre) {
    const vista = vistasDelReporte.find((item) => item.nombre === nombre)
    if (vista) abrirConfiguracion(vista)
  }

  async function exportarVistaGuardada(vista, formato) {
    try {
      const { blob, nombre } = await exportarVista(vista.idVista, formato)
      const url = URL.createObjectURL(blob)
      const enlace = document.createElement('a')
      enlace.href = url; enlace.download = nombre; enlace.click()
      URL.revokeObjectURL(url)
    } catch (error) { setAccionError(error) }
  }

  function eliminarVistaGuardada(vista) {
    setConfirmacion({ tipo: 'eliminar', vista })
  }

  async function ejecutarEliminacion(vista) {
    setProcesandoConfirmacion(true)
    try {
      await eliminarVista(vista.idVista)
      setVistas((actual) => actual.filter((item) => item.idVista !== vista.idVista))
      if (nombreVista === vista.nombre) setNombreVista('')
      setAccionError(null); setConfirmacion(null)
    } catch (error) {
      setAccionError(error); setConfirmacion(null)
    } finally {
      setProcesandoConfirmacion(false)
    }
  }

  function confirmarAccion() {
    if (!confirmacion || procesandoConfirmacion) return
    if (confirmacion.tipo === 'sobrescribir') ejecutarGuardado(confirmacion.nombre)
    else ejecutarEliminacion(confirmacion.vista)
  }

  async function exportar(formato) {
    try {
      const filtrosAplicados = resultado.datos?.filtrosAplicados || filtros
      const parametros = parametrosReporte(reporte, filtrosAplicados)
      parametros.set('formato', formato)
      columnasAplicadasEfectivas.forEach((columna) => parametros.append('columnas', columna))
      if (seleccion === 'MOROSOS' && detalleActivoAplicado) columnasDetalleAplicadasEfectivas.forEach((columna) => parametros.append('columnasDetalle', columna))
      if (ordenAplicado) parametros.set('orden', ordenAplicado)
      const { blob, nombre } = await descargarReporte(`/reportes/${encodeURIComponent(seleccion)}/exportar?${parametros}`)
      const url = URL.createObjectURL(blob); const enlace = document.createElement('a'); enlace.href = url; enlace.download = nombre; enlace.click(); URL.revokeObjectURL(url)
    } catch (error) { setAccionError(error) }
  }

  function alternarColumna(columna) {
    const visibles = columnasEfectivas
    if (visibles.includes(columna) && visibles.length === 1) return
    const visiblesSet = new Set(visibles)
    if (visiblesSet.has(columna)) visiblesSet.delete(columna)
    else visiblesSet.add(columna)
    const siguiente = ordenColumnasEfectivas.filter((item) => visiblesSet.has(item))
    setColumnas(siguiente)
  }

  function alternarColumnaDetalle(columna) {
    const visibles = columnasDetalleEfectivas
    if (visibles.includes(columna) && visibles.length === 1) return
    const visiblesSet = new Set(visibles)
    if (visiblesSet.has(columna)) visiblesSet.delete(columna)
    else visiblesSet.add(columna)
    setColumnasDetalle(ordenColumnasDetalleEfectivas.filter((item) => visiblesSet.has(item)))
  }

  function reordenarColumnas(nuevoOrden) {
    setOrdenColumnas(nuevoOrden)
    const visibles = new Set(columnasEfectivas)
    setColumnas(nuevoOrden.filter((columna) => visibles.has(columna)))
  }

  function reordenarColumnasDetalle(nuevoOrden) {
    setOrdenColumnasDetalle(nuevoOrden)
    const visibles = new Set(columnasDetalleEfectivas)
    setColumnasDetalle(nuevoOrden.filter((columna) => visibles.has(columna)))
  }

  function restablecerColumnas() {
    setColumnas([])
    setOrdenColumnas([...columnasPredeterminadas])
    grupoColumnasRef.current?.querySelector('.reporte-columnas-grid')?.scrollTo({ left: 0 })
    window.requestAnimationFrame(actualizarDesbordeColumnas)
  }

  function restablecerColumnasDetalle() {
    setColumnasDetalle([])
    setOrdenColumnasDetalle([...detallePredeterminado])
    grupoDetalleRef.current?.querySelector('.reporte-columnas-grid')?.scrollTo({ left: 0 })
    window.requestAnimationFrame(actualizarDesbordeDetalle)
  }

  function restablecerFiltros() {
    setFiltros({ ...FILTROS_INICIALES })
    setErrores({})
    setAccionError(null)
  }

  function cambiarOrden(siguiente) {
    setOrden(siguiente)
  }

  async function consultar(evento, filtrosSolicitados = filtros) {
    evento?.preventDefault()
    const validacion = validarFiltros(reporte, filtrosSolicitados)
    setErrores(validacion)
    if (Object.keys(validacion).length) return
    consultaActiva.current?.abort()
    const controlador = new AbortController()
    consultaActiva.current = controlador
    setResultado({ cargando: true, datos: null, error: null })
    try {
      const datos = await generarReporte(reporte, filtrosSolicitados, { signal: controlador.signal })
      datos.__columnas = columnasEfectivas
      datos.__orden = orden
      datos.__mostrarDetalle = detalleActivo
      if (!controlador.signal.aborted) {
        setFiltrosAplicados({ ...filtrosSolicitados })
        setColumnasAplicadas(columnasEfectivas)
        setOrdenColumnasAplicadas(ordenColumnasEfectivas)
        setColumnasDetalleAplicadas(columnasDetalleEfectivas)
        setOrdenColumnasDetalleAplicadas(ordenColumnasDetalleEfectivas)
        setDetalleActivoAplicado(detalleActivo)
        setOrdenAplicado(orden)
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
  const tiposDocumentoVisibles = opcionesMorosos.tiposDocumento.length ? opcionesMorosos.tiposDocumento : TIPOS_DOCUMENTO
  const etapasVisibles = opcionesMorosos.etapas.length ? opcionesMorosos.etapas : ETAPAS_COBRANZA
  const zonasDisponibles = zonas.tipo === seleccion ? zonas.datos : []
  const cargandoZonas = Boolean(seleccion) && zonas.tipo !== seleccion
  const errorZonas = zonas.tipo === seleccion ? zonas.error : null
  return <div className="reportes" aria-busy={catalogo.cargando || resultado.cargando}>
    {catalogo.cargando && <div className="pcard reporte-estado" role="status">Cargando el catálogo de reportes…</div>}
    {catalogo.error && <div className="alert" role="alert"><strong>No se pudo cargar el catálogo.</strong><p>{catalogo.error.message}</p><button className="btn" type="button" onClick={reintentarCatalogo}>Reintentar</button></div>}
    {!catalogo.cargando && !catalogo.error && catalogo.datos.length === 0 && <section className="pcard reporte-estado" role="status"><h2>Sin reportes disponibles</h2><p>Tu perfil no tiene reportes habilitados en este momento.</p></section>}
    {catalogo.datos.length > 0 && !reporte && <section className="pcard reporte-catalogo reporte-catalogo--entrada" aria-labelledby="catalogo-reportes"><h2 id="catalogo-reportes">Selecciona un reporte</h2><p className="hint">El catálogo muestra únicamente los reportes habilitados para tu perfil.</p><div className="reporte-opciones" role="radiogroup" aria-label="Catálogo de reportes">{catalogo.datos.map((item) => <button key={item.tipo} type="button" className="reporte-opcion" role="radio" aria-checked="false" onClick={() => elegir(item.tipo)}><strong>{item.titulo}</strong><span>{item.descripcion}</span></button>)}</div></section>}
    {reporte && seleccion === 'PRICOS' && <ReportePricos titulo={reporte.titulo} onCambiarReporte={cambiarReporte} />}
    {reporte && seleccion === 'MOROSOS' && <>
      <section className="pcard reporte-configuracion" aria-labelledby="configuracion-reporte">
        <form className="reporte-configuracion-form" onSubmit={consultar} noValidate>
          <div className="reporte-configuracion-cab">
            <div>
              <h2 id="configuracion-reporte">Filtros y personalización</h2>
              <p className="hint">Configura los filtros, el orden y las columnas. Se aplicarán al pulsar “Generar reporte”.</p>
            </div>
            <div className="reporte-configuracion-acciones">
              <button className="btn" type="button" onClick={cambiarReporte}>Cambiar reporte</button>
              <button className="btn primary reporte-generar" type="submit" disabled={resultado.cargando}>{resultado.cargando ? 'Generando…' : 'Generar reporte'}</button>
            </div>
          </div>
          <div
            className={`reporte-cambios-pendientes-wrap${mostrarAvisoCambios ? ' reporte-cambios-pendientes-wrap--visible' : ''}`}
            aria-hidden={!mostrarAvisoCambios}
          >
            <div className="reporte-cambios-pendientes-interior">
              <p className="reporte-cambios-pendientes" role="status">Hay cambios de filtros, columnas u orden sin aplicar. Pulsa “Generar reporte” para verlos.</p>
            </div>
          </div>

          <div className="reporte-configuracion-grid">
            <fieldset className="reporte-config-grupo">
              <legend>Filtros</legend>
              <div className="reporte-fila-restablecer reporte-fila-restablecer--filtros">
                <div className="reporte-config-campos">
                {disponibles.has('anio') && <div className={`fld${errores.anio ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-anio">Año</label>
                  <input id="reporte-anio" inputMode="numeric" placeholder="Ej. 2026" value={filtros.anio} onChange={(e) => setFiltros({ ...filtros, anio: e.target.value })} aria-describedby={errores.anio ? 'error-anio' : undefined} />
                  {errores.anio && <span id="error-anio" className="error-campo">{errores.anio}</span>}
                </div>}
                {disponibles.has('anioDesde') && <div className={`fld${errores.anioDesde ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-anio-desde">Año desde</label>
                  <input id="reporte-anio-desde" type="number" min="1900" max="2100" value={filtros.anioDesde} onChange={(e) => setFiltros({ ...filtros, anioDesde: e.target.value })} aria-describedby={errores.anioDesde ? 'error-anio-desde' : undefined} />
                  {errores.anioDesde && <span id="error-anio-desde" className="error-campo">{errores.anioDesde}</span>}
                </div>}
                {disponibles.has('anioHasta') && <div className={`fld${errores.anioHasta ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-anio-hasta">Año hasta</label>
                  <input id="reporte-anio-hasta" type="number" min="1900" max="2100" value={filtros.anioHasta} onChange={(e) => setFiltros({ ...filtros, anioHasta: e.target.value })} aria-describedby={errores.anioHasta ? 'error-anio-hasta' : undefined} />
                  {errores.anioHasta && <span id="error-anio-hasta" className="error-campo">{errores.anioHasta}</span>}
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
                {disponibles.has('beneficio') && <div className={`fld${errores.beneficio ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-beneficio">Beneficio</label>
                  <select id="reporte-beneficio" value={filtros.beneficio} onChange={(e) => setFiltros({ ...filtros, beneficio: e.target.value })}>
                    {FILTROS_BENEFICIO.map((item) => <option key={item.valor} value={item.valor}>{item.etiqueta}</option>)}
                  </select>
                  {errores.beneficio && <span className="error-campo">{errores.beneficio}</span>}
                </div>}
                {disponibles.has('montoMinimo') && <div className={`fld${errores.montoMinimo ? ' fld-error' : ''}`}>
                  <label htmlFor="reporte-monto">Monto mínimo (S/)</label>
                  <input id="reporte-monto" type="number" min="0" step="0.01" placeholder="0.00" value={filtros.montoMinimo} onChange={(e) => setFiltros({ ...filtros, montoMinimo: e.target.value })} />
                  {errores.montoMinimo && <span className="error-campo">{errores.montoMinimo}</span>}
                </div>}
                </div>
                <div className="reporte-accion-filtros">
                  <span className="fld-etiqueta reporte-espaciador" aria-hidden="true">&nbsp;</span>
                  <button
                    className="btn reporte-btn-restablecer"
                    type="button"
                    onClick={restablecerFiltros}
                    disabled={filtrosSonPredeterminados}
                  >
                    Restablecer filtros
                  </button>
                </div>
              </div>
            </fieldset>

            <fieldset className="reporte-config-grupo reporte-config-grupo--personalizacion">
              <legend>Personalización</legend>
              <div className="reporte-config-campos reporte-config-campos--personalizacion">
                <div className="fld">
                  <label htmlFor="reporte-orden">Orden</label>
                  <select id="reporte-orden" value={orden} onChange={(e) => cambiarOrden(e.target.value)}>
                    <option value="">Predeterminado</option>
                    {(ORDENES[seleccion] || ORDENES.MOROSOS).map(([valor, etiqueta]) => <option key={valor} value={valor}>{etiqueta}</option>)}
                  </select>
                </div>
                {seleccion === 'MOROSOS' && <div className="fld reporte-toggle-detalle">
                  <span className="fld-etiqueta">Detalle de la mora</span>
                  <label className="reporte-check" htmlFor="reporte-detalle">
                    <input
                      id="reporte-detalle"
                      type="checkbox"
                      checked={detalleActivo}
                      onChange={(e) => setDetalleActivo(e.target.checked)}
                    />
                    Incluir en pantalla y exportación
                  </label>
                </div>}
                <fieldset className="reporte-columnas">
                  <legend>Columnas visibles</legend>
                  <div className="reporte-fila-restablecer">
                    <div
                      className="reporte-columnas-grupo"
                      ref={grupoColumnasRef}
                      data-desborde-izquierda={desbordeColumnas.izquierda}
                      data-desborde-derecha={desbordeColumnas.derecha}
                    >
                      <Reorder.Group
                        as="div"
                        axis="x"
                        values={ordenColumnasEfectivas}
                        onReorder={reordenarColumnas}
                        onScroll={actualizarDesbordeColumnas}
                        className="reporte-columnas-grid"
                        role="group"
                        aria-label="Columnas visibles"
                      >
                        {ordenColumnasEfectivas.map((columna) => <Reorder.Item
                          as="button"
                          key={columna}
                          value={columna}
                          type="button"
                          className="reporte-segmento"
                          aria-pressed={columnasEfectivas.includes(columna)}
                          onDragStart={() => { arrastreColumnas.current = true }}
                          onDragEnd={() => { window.setTimeout(() => { arrastreColumnas.current = false }, 0) }}
                          onClick={() => {
                            if (arrastreColumnas.current) return
                            alternarColumna(columna)
                          }}
                        >
                          {seleccion === 'MOROSOS' ? ETIQUETAS_CABECERA[columna] : ETIQUETAS[columna]}
                        </Reorder.Item>)}
                      </Reorder.Group>
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
                    <button
                      className="btn reporte-btn-restablecer"
                      type="button"
                      onClick={restablecerColumnas}
                      disabled={columnasSonPredeterminadas}
                    >
                      Restablecer columnas
                    </button>
                  </div>
                  {seleccion === 'MOROSOS' && detalleActivo && <>
                    <p className="reporte-columnas-subtitulo">Columnas del detalle</p>
                    <div className="reporte-fila-restablecer">
                      <div
                        className="reporte-columnas-grupo"
                        ref={grupoDetalleRef}
                        data-desborde-izquierda={desbordeDetalle.izquierda}
                        data-desborde-derecha={desbordeDetalle.derecha}
                      >
                        <Reorder.Group
                          as="div"
                          axis="x"
                          values={ordenColumnasDetalleEfectivas}
                          onReorder={reordenarColumnasDetalle}
                          onScroll={actualizarDesbordeDetalle}
                          className="reporte-columnas-grid"
                          role="group"
                          aria-label="Columnas del detalle"
                        >
                          {ordenColumnasDetalleEfectivas.map((columna) => <Reorder.Item
                            as="button"
                            key={columna}
                            value={columna}
                            type="button"
                            className="reporte-segmento"
                            aria-pressed={columnasDetalleEfectivas.includes(columna)}
                            onDragStart={() => { arrastreDetalle.current = true }}
                            onDragEnd={() => { window.setTimeout(() => { arrastreDetalle.current = false }, 0) }}
                            onClick={() => {
                              if (arrastreDetalle.current) return
                              alternarColumnaDetalle(columna)
                            }}
                          >
                            {ETIQUETAS_DETALLE[columna]}
                          </Reorder.Item>)}
                        </Reorder.Group>
                        {desbordeDetalle.izquierda && <button
                          className="reporte-columnas-flecha reporte-columnas-flecha--izquierda"
                          type="button"
                          aria-label="Desplazar columnas del detalle a la izquierda"
                          onClick={() => desplazarColumnas(grupoDetalleRef, -1)}
                        ><span aria-hidden="true">‹</span></button>}
                        {desbordeDetalle.derecha && <button
                          className="reporte-columnas-flecha reporte-columnas-flecha--derecha"
                          type="button"
                          aria-label="Desplazar columnas del detalle a la derecha"
                          onClick={() => desplazarColumnas(grupoDetalleRef, 1)}
                        ><span aria-hidden="true">›</span></button>}
                      </div>
                      <button
                        className="btn reporte-btn-restablecer"
                        type="button"
                        onClick={restablecerColumnasDetalle}
                        disabled={detalleSonPredeterminados}
                      >
                        Restablecer detalle
                      </button>
                    </div>
                  </>}
                </fieldset>
              </div>
            </fieldset>
          </div>

          <div className="reporte-config-acciones">
            <div className="reporte-config-guardar">
              <div className="fld reporte-campo-vista">
                <label htmlFor="reporte-nombre-vista">Nombre de la vista</label>
                <div className="reporte-campo-vista-fila">
                  <input id="reporte-nombre-vista" placeholder="Ej. Morosos zona 2" value={nombreVista} onChange={(e) => setNombreVista(e.target.value)} />
                  <select
                    className="reporte-vista-selector"
                    aria-label="Seleccionar una vista guardada"
                    value=""
                    onChange={(e) => seleccionarVista(e.target.value)}
                    disabled={vistasDelReporte.length === 0}
                  >
                    <option value="">{vistasDelReporte.length ? 'Seleccionar vista…' : 'Sin vistas guardadas'}</option>
                    {vistasDelReporte.map((vista) => <option key={vista.idVista} value={vista.nombre}>{vista.nombre}</option>)}
                  </select>
                </div>
              </div>
              <button className="btn" type="button" onClick={guardarConfiguracion} disabled={!resultado.datos || !nombreVista.trim() || columnasEfectivas.length === 0}>Guardar vista</button>
            </div>
            <div className="reporte-config-exportar">
              <button className="btn" type="button" onClick={() => exportar('EXCEL')} disabled={!resultado.datos}>Exportar Excel</button>
              <button className="btn" type="button" onClick={() => exportar('PDF')} disabled={!resultado.datos}>Exportar PDF</button>
            </div>
          </div>

          <section className="reporte-vistas-guardadas" aria-labelledby="vistas-guardadas-titulo">
            <h3 id="vistas-guardadas-titulo">Vistas guardadas</h3>
            {vistasDelReporte.length === 0
              ? <p className="hint">Aún no hay vistas guardadas para este reporte.</p>
              : <div className="tabla-scroll" tabIndex="0" role="region" aria-label="Vistas guardadas">
                  <table className="tabla reporte-vistas-tabla">
                    <caption className="sr-only">Vistas guardadas del reporte actual</caption>
                    <thead><tr>
                      <th scope="col">Nombre</th>
                      <th scope="col" className="num">Columnas reporte</th>
                      <th scope="col" className="num">Columnas detalle</th>
                      <th scope="col">Fecha de creación</th>
                      <th scope="col">Acciones</th>
                    </tr></thead>
                    <tbody>
                      {vistasDelReporte.map((vista) => <tr key={vista.idVista}>
                        <th scope="row">{vista.nombre}</th>
                        <td className="num">{vista.columnasVisibles?.length ?? 0}</td>
                        <td className="num">{vista.columnasDetalleVisibles?.length ?? 0}</td>
                        <td>{fechaHora(vista.fechaCreacion)}</td>
                        <td>
                          <div className="reporte-vistas-acciones">
                            <button className="btn" type="button" onClick={() => abrirConfiguracion(vista)}>Utilizar</button>
                            <button className="btn" type="button" onClick={() => exportarVistaGuardada(vista, 'EXCEL')}>Excel</button>
                            <button className="btn" type="button" onClick={() => exportarVistaGuardada(vista, 'PDF')}>PDF</button>
                            <button className="btn peligro" type="button" onClick={() => eliminarVistaGuardada(vista)}>Eliminar</button>
                          </div>
                        </td>
                      </tr>)}
                    </tbody>
                  </table>
                </div>}
          </section>
          {!resultado.datos && <p className="hint reporte-config-ayuda">Genera el reporte para habilitar “Guardar vista” y la exportación.</p>}
          {accionError && <div className="alert" role="alert">{accionError.message}</div>}
        </form>
      </section>
      <div aria-live="polite">{resultado.cargando && <div className="pcard reporte-estado" role="status">Generando el reporte con la información disponible…</div>}{errorGeneral && <div className="alert" role="alert"><strong>{errorGeneral.estado === 403 ? 'Reporte restringido.' : errorGeneral.estado === 400 ? 'Revisa los filtros.' : errorGeneral.codigo === 'SIN_CARGA_ETL' ? 'El ETL no tiene una carga publicada.' : errorGeneral.codigo === 'ETL_SIN_CORRESPONDENCIA' ? 'El ETL no tiene correspondencia con SAMNET.' : 'No se pudo generar el reporte.'}</strong><p>{errorGeneral.message}</p>{errorGeneral.estado === 503 && <button className="btn" type="button" onClick={consultar}>Reintentar</button>}</div>}</div>
      {resultado.datos && <section className="reporte-resultados" aria-labelledby="resultado-reporte"><div className="reporte-resultados-cab"><div><h2 id="resultado-reporte">{resultado.datos.titulo}</h2><p className="hint">Información oficial a la fecha de corte indicada.</p></div></div><Resumen datos={resultado.datos} />{resultado.datos.filas.length === 0 ? <div className="pcard reporte-estado" role="status"><h3>Sin resultados</h3><p>No hay contribuyentes que cumplan los criterios seleccionados.</p></div> : grafica === 'TABLA' ? <TablaReporte datos={resultado.datos} columnasDetalle={columnasDetalleAplicadasEfectivas} /> : <BarrasReporte datos={resultado.datos} />}</section>}
    </>}
    <ModalConfirmacion
      abierto={Boolean(confirmacion)}
      titulo={confirmacion?.tipo === 'eliminar' ? 'Eliminar vista' : 'Sobrescribir vista'}
      mensaje={confirmacion?.tipo === 'eliminar'
        ? <>¿Eliminar la vista <strong>{confirmacion?.vista?.nombre}</strong>? Esta acción no se puede deshacer.</>
        : <>Ya existe una vista llamada <strong>{confirmacion?.nombre}</strong>. Si continúas, se reemplazará su configuración actual.</>}
      textoConfirmar={confirmacion?.tipo === 'eliminar' ? 'Eliminar' : 'Sobrescribir'}
      peligro={confirmacion?.tipo === 'eliminar'}
      procesando={procesandoConfirmacion}
      onConfirmar={confirmarAccion}
      onCerrar={() => { if (!procesandoConfirmacion) setConfirmacion(null) }}
    />
  </div>
}
