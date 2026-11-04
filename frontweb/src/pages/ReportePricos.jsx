import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fecha, moneda } from '../utils/formato.js'
import { CRITERIOS_PRICOS, etiquetaCriterioPricos, etiquetaTipoDocumento, porcentaje, validarPricos } from '../utils/reportes.js'
import { generarReportePricos } from '../services/reporteService.js'
import './Reportes.css'

const GRUPOS = [
  {
    etiqueta: 'Contribuyente',
    columnas: ['codigo', 'razonSocial', 'regimen', 'tipoDocumento', 'numeroDocumento'],
  },
  {
    etiqueta: 'Domicilio fiscal',
    columnas: ['domicilioFiscal', 'via', 'numero', 'departamento', 'interior', 'manzana', 'lote', 'distrito'],
  },
  { etiqueta: 'Base imponible', columnas: ['baseImponible'] },
  { etiqueta: 'Emisión', columnas: ['emisionPredial', 'emisionArbitrios'] },
  { etiqueta: 'Deuda años anteriores', columnas: ['deudaAnteriorPredial', 'deudaAnteriorArbitrios'] },
  {
    etiqueta: 'Deuda con el año de corte',
    columnas: ['deudaActualPredial', 'deudaActualArbitrios', 'porcentajeDescuentoArbitrios'],
  },
  { etiqueta: 'Datos del contribuyente', columnas: ['condicion', 'tipoPersona', 'correo', 'telefonos'] },
]

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

function ResumenPricos({ datos }) {
  return <>
    <section className="reporte-resumen" aria-label="Resumen del reporte">
      <article className="pcard"><span className="pl">Fecha de corte</span><strong>{fecha(datos.fechaCorte)}</strong></article>
      <article className="pcard"><span className="pl">Contribuyentes considerados</span><strong>{datos.totalContribuyentes}</strong></article>
      <article className="pcard"><span className="pl">Contribuyentes mostrados</span><strong>{datos.filas.length}</strong></article>
      <article className="pcard"><span className="pl">Criterio</span><strong className="reporte-resumen-texto">{etiquetaCriterioPricos(datos.criterio)}</strong></article>
    </section>
    <div className="reporte-aplicados">
      <strong>Filtros aplicados:</strong>
      <span>Criterio: {etiquetaCriterioPricos(datos.criterio)}</span>
      <span>Límite: {datos.limite}</span>
      <span>Carga ETL: #{datos.idLote}</span>
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

export default function ReportePricos({ titulo, onCambiarReporte }) {
  const [criterio, setCriterio] = useState(CRITERIOS_PRICOS[0].valor)
  const [limite, setLimite] = useState('10')
  const [aplicado, setAplicado] = useState({ criterio: CRITERIOS_PRICOS[0].valor, limite: '10' })
  const [errores, setErrores] = useState({})
  const [resultado, setResultado] = useState({ cargando: true, datos: null, error: null })
  const [columnasVisibles, setColumnasVisibles] = useState(COLUMNAS_PRICOS)
  const [desbordeColumnas, setDesbordeColumnas] = useState({ izquierda: false, derecha: false })
  const consultaActiva = useRef(null)
  const grupoColumnasRef = useRef(null)

  const actualizarDesbordeColumnas = useCallback(() => {
    setDesbordeColumnas((actual) => {
      const siguiente = medirDesborde(grupoColumnasRef.current?.querySelector('.reporte-columnas-grid'))
      return actual.izquierda === siguiente.izquierda && actual.derecha === siguiente.derecha ? actual : siguiente
    })
  }, [])

  const ejecutar = useCallback(async (criterioSolicitado, limiteSolicitado) => {
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
    } catch (error) {
      if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error })
    }
  }, [])

  useEffect(() => {
    const controlador = new AbortController()
    consultaActiva.current = controlador
    generarReportePricos({ criterio: CRITERIOS_PRICOS[0].valor, limite: '10' }, { signal: controlador.signal })
      .then((datos) => {
        if (controlador.signal.aborted) return
        setResultado({ cargando: false, datos, error: null })
        setAplicado({ criterio: CRITERIOS_PRICOS[0].valor, limite: '10' })
      })
      .catch((error) => {
        if (!controlador.signal.aborted) setResultado({ cargando: false, datos: null, error })
      })
    return () => controlador.abort()
  }, [])

  useEffect(() => {
    const actualizar = () => actualizarDesbordeColumnas()
    const frame = window.requestAnimationFrame(actualizar)
    window.addEventListener('resize', actualizar)
    return () => {
      window.cancelAnimationFrame(frame)
      window.removeEventListener('resize', actualizar)
    }
  }, [columnasVisibles, actualizarDesbordeColumnas])

  const columnasOrdenadas = useMemo(
    () => COLUMNAS_PRICOS.filter((columna) => columnasVisibles.includes(columna)),
    [columnasVisibles],
  )
  const gruposVisibles = useMemo(
    () => GRUPOS
      .map((grupo) => ({ ...grupo, columnas: grupo.columnas.filter((columna) => columnasVisibles.includes(columna)) }))
      .filter((grupo) => grupo.columnas.length > 0),
    [columnasVisibles],
  )
  const criterioSeleccionado = CRITERIOS_PRICOS.find(({ valor }) => valor === criterio)
  const hayCambios = criterio !== aplicado.criterio || String(limite) !== aplicado.limite
  const mostrarAvisoCambios = hayCambios && !resultado.cargando
  const columnasPredeterminadas = columnasVisibles.length === COLUMNAS_PRICOS.length

  function consultar(evento) {
    evento?.preventDefault()
    ejecutar(criterio, limite)
  }

  function alternarColumna(columna) {
    setColumnasVisibles((actual) => {
      if (actual.includes(columna)) {
        return actual.length === 1 ? actual : actual.filter((item) => item !== columna)
      }
      return COLUMNAS_PRICOS.filter((item) => actual.includes(item) || item === columna)
    })
  }

  function restablecerColumnas() {
    setColumnasVisibles(COLUMNAS_PRICOS)
  }

  const errorGeneral = resultado.error

  return <div className="reportes" aria-busy={resultado.cargando}>
    <section className="pcard reporte-configuracion" aria-labelledby="configuracion-pricos">
      <form className="reporte-configuracion-form" onSubmit={consultar} noValidate>
        <div className="reporte-configuracion-cab">
          <div>
            <h2 id="configuracion-pricos">Criterio y personalización</h2>
            <p className="hint">El reporte se calcula sobre la última carga publicada del ETL. Define el criterio y el límite; se aplicarán al pulsar “Generar reporte”.</p>
          </div>
          <div className="reporte-configuracion-acciones">
            <button className="btn" type="button" onClick={onCambiarReporte}>Cambiar reporte</button>
            <button className="btn primary reporte-generar" type="submit" disabled={resultado.cargando}>{resultado.cargando ? 'Generando…' : 'Generar reporte'}</button>
          </div>
        </div>

        <div className={`reporte-cambios-pendientes-wrap${mostrarAvisoCambios ? ' reporte-cambios-pendientes-wrap--visible' : ''}`} aria-hidden={!mostrarAvisoCambios}>
          <div className="reporte-cambios-pendientes-interior">
            <p className="reporte-cambios-pendientes" role="status">Hay cambios en el criterio o el límite sin aplicar. Pulsa “Generar reporte” para verlos.</p>
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
              <div className="reporte-fila-restablecer">
                <div
                  className="reporte-columnas-grupo"
                  ref={grupoColumnasRef}
                  data-desborde-izquierda={desbordeColumnas.izquierda}
                  data-desborde-derecha={desbordeColumnas.derecha}
                >
                  <div
                    className="reporte-columnas-grid"
                    role="group"
                    aria-label="Columnas visibles del reporte PRICOS"
                    onScroll={actualizarDesbordeColumnas}
                  >
                    {COLUMNAS_PRICOS.map((columna) => <button
                      key={columna}
                      type="button"
                      className="reporte-segmento"
                      aria-pressed={columnasVisibles.includes(columna)}
                      onClick={() => alternarColumna(columna)}
                    >
                      {ETIQUETAS[columna]}
                    </button>)}
                  </div>
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

    <div aria-live="polite">
      {resultado.cargando && <div className="pcard reporte-estado" role="status">Generando el reporte con la última carga publicada del ETL…</div>}
      {errorGeneral && <div className="alert" role="alert">
        <strong>{tituloError(errorGeneral)}</strong>
        <p>{errorGeneral.message}</p>
        {errorGeneral.estado === 503 && <button className="btn" type="button" onClick={() => ejecutar(criterio, limite)}>Reintentar</button>}
      </div>}
    </div>

    {resultado.datos && <section className="reporte-resultados" aria-labelledby="resultado-pricos">
      <div className="reporte-resultados-cab">
        <div>
          <h2 id="resultado-pricos">{titulo || 'Reporte de PRICOS'}</h2>
          <p className="hint">Información oficial a la fecha de corte de la última carga publicada del ETL.</p>
        </div>
      </div>
      <ResumenPricos datos={resultado.datos} />
      {resultado.datos.filas.length === 0
        ? <div className="pcard reporte-estado" role="status"><h3>Sin resultados</h3><p>No hay contribuyentes en la carga del ETL para este criterio.</p></div>
        : <TablaPricos filas={resultado.datos.filas} columnas={columnasOrdenadas} grupos={gruposVisibles} anioCorte={resultado.datos.fechaCorte?.slice(0, 4)} />}
    </section>}
  </div>
}
