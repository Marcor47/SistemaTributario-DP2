import { useEffect, useRef } from 'react'
import { COMPATIBILIDAD, ORDENABLES, SIN_ORDEN, TIPOS_GRAFICO, tiposListados } from '../../utils/reporteria.js'
import { opcionGrafico, ordenActual, resumenAccesible } from '../../utils/reporteriaGraficos.js'
import echarts from '../../utils/reporteriaEcharts.js'
import IconoReporte from './IconoReporte.jsx'

const ETIQUETA_ORDEN = { ninguno: 'Orden original', desc: 'Mayor a menor', asc: 'Menor a mayor' }
const SIGUIENTE_ORDEN = { ninguno: 'desc', desc: 'asc', asc: 'ninguno' }

function TablaGrafico({ especificacion, tabla, onSeleccionar }) {
  const dimension = especificacion.dimension
  const seleccionable = Boolean(dimension) && especificacion.clase !== 'temporalMulti'
  return (
    <table className="rep-tabla">
      <caption className="sr-only">{especificacion.titulo}</caption>
      <thead>
        <tr>{tabla.encabezados.map((encabezado, i) => <th key={encabezado} scope="col" className={tabla.numericas[i] ? 'num' : undefined}>{encabezado}</th>)}</tr>
      </thead>
      <tbody>
        {tabla.filas.map((fila) => {
          const activa = seleccionable && fila.clave != null
          const seleccionar = () => onSeleccionar(dimension, fila.clave, fila.fila)
          return (
            <tr
              key={fila.texto[0]}
              className={`${fila.resaltada ? 'resaltada ' : ''}${activa ? 'pulsable' : ''}`}
              tabIndex={activa ? 0 : undefined}
              aria-label={activa ? `${fila.texto[0]}. Pulsa Intro para resaltar.` : undefined}
              onClick={activa ? seleccionar : undefined}
              onKeyDown={activa ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); seleccionar() } } : undefined}
            >
              {fila.texto.map((valor, i) => (i === 0
                ? <th key="categoria" scope="row">{valor}</th>
                : <td key={tabla.encabezados[i]} className={tabla.numericas[i] ? 'num' : undefined}>{valor}</td>))}
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

export default function TarjetaGrafico({ especificacion, contexto, tabla, ordenes, onCambiarTipo, onCambiarOrden, onSeleccionar, onAviso, registrarGrafico }) {
  const lienzo = useRef(null)
  const grafico = useRef(null)
  const alSeleccionar = useRef(onSeleccionar)
  const { tipo, nota } = contexto
  const compatibilidad = COMPATIBILIDAD[especificacion.clase]
  const ordenable = ORDENABLES.has(especificacion.clase)
  const orden = ordenActual(especificacion, ordenes)
  const esTabla = tipo === 'tabla'
  const id = especificacion.id

  useEffect(() => { alSeleccionar.current = onSeleccionar }, [onSeleccionar])

  useEffect(() => {
    if (esTabla || !lienzo.current) return undefined
    const instancia = echarts.init(lienzo.current, null, { renderer: 'canvas' })
    grafico.current = instancia
    registrarGrafico(id, instancia)
    instancia.on('click', (p) => {
      const dimension = especificacion.dimensionSerie || especificacion.dimension
      const clave = p.data?.clave
      if (dimension && clave != null) alSeleccionar.current(dimension, clave, p.data.fila)
    })
    let temporizador
    const observador = new ResizeObserver(() => {
      clearTimeout(temporizador)
      temporizador = setTimeout(() => instancia.resize(), 80)
    })
    observador.observe(lienzo.current)
    return () => {
      clearTimeout(temporizador)
      observador.disconnect()
      registrarGrafico(id, null)
      grafico.current = null
      instancia.dispose()
    }
  }, [esTabla, id, especificacion, registrarGrafico])

  useEffect(() => {
    if (!esTabla && grafico.current) grafico.current.setOption(opcionGrafico(especificacion, contexto), true)
  }, [esTabla, especificacion, contexto])

  const seleccionable = Boolean(especificacion.dimension || especificacion.dimensionSerie)
  return (
    <article className="rep-card" aria-labelledby={`grafico-${id}`}>
      <header className="rep-card__cabecera">
        <div>
          <h3 id={`grafico-${id}`}>{especificacion.titulo}</h3>
          <p className="rep-card__sub">{especificacion.descripcion}</p>
        </div>
        <div className="rep-herramientas" role="toolbar" aria-label={`Opciones del gráfico ${especificacion.titulo}`}>
          {tiposListados(especificacion.clase).map((opcion) => {
            const permitido = compatibilidad.permitidos.includes(opcion)
            const titulo = TIPOS_GRAFICO[opcion]
            return (
              <button
                key={opcion}
                type="button"
                className="rep-herramienta"
                aria-label={titulo + (permitido ? '' : ' (no disponible)')}
                aria-pressed={tipo === opcion}
                aria-disabled={permitido ? undefined : true}
                title={permitido ? titulo : `${titulo} no disponible: ${compatibilidad.motivos[opcion]}`}
                onClick={() => (permitido ? onCambiarTipo(id, opcion) : onAviso(`${titulo} no disponible. ${compatibilidad.motivos[opcion]}`))}
              >
                <IconoReporte nombre={opcion} />
              </button>
            )
          })}
          <span className="rep-herramientas__separador" aria-hidden="true" />
          <button
            type="button"
            className="rep-herramienta rep-herramienta--orden"
            aria-disabled={ordenable ? undefined : true}
            aria-label={ordenable ? `Orden actual: ${ETIQUETA_ORDEN[orden].toLowerCase()}. Pulsa para cambiar.` : 'Reordenar (no disponible)'}
            title={ordenable ? 'Cambiar el orden' : `Reordenar no disponible: ${SIN_ORDEN[especificacion.clase]}`}
            onClick={() => (ordenable ? onCambiarOrden(id, SIGUIENTE_ORDEN[orden]) : onAviso(`Reordenar no disponible. ${SIN_ORDEN[especificacion.clase]}`))}
          >
            <IconoReporte nombre="orden" tamano={15} /><span>{ordenable ? ETIQUETA_ORDEN[orden] : 'Orden'}</span>
          </button>
        </div>
      </header>
      {nota && <p className="rep-nota">{nota}</p>}
      {esTabla
        ? <div key="tabla" className="rep-plot rep-plot--tabla"><TablaGrafico especificacion={especificacion} tabla={tabla} onSeleccionar={onSeleccionar} /></div>
        : <div key="lienzo" ref={lienzo} className="rep-plot" role="img" aria-label={resumenAccesible(especificacion, contexto, tabla)} />}
      <p className="rep-pista">{seleccionable ? 'Haz clic en un elemento para resaltarlo en toda la pestaña.' : 'Pasa el cursor para ver el detalle.'}</p>
    </article>
  )
}
