import { useCallback, useEffect, useRef, useState } from 'react'
import { obtenerEstadoAsistente, preguntarAsistente } from '../../services/reporteriaService.js'
import { fecha } from '../../utils/formato.js'
import { bloquesRespuesta, opcionGraficoAsistente, partesConNegrita } from '../../utils/reporteriaGraficos.js'
import echarts from '../../utils/reporteriaEcharts.js'
import IconoReporte from './IconoReporte.jsx'

const SUGERENCIAS = [
  '¿Cuál es el índice de morosidad del año?',
  'Top 3 zonas con más deuda vencida',
  '¿Qué segmento tiene la peor regularidad de pago?',
  'Compara el porcentaje cobrado por tributo',
]

function TextoConNegrita({ texto }) {
  return partesConNegrita(texto).map((parte, i) => (parte.negrita ? <strong key={i}>{parte.texto}</strong> : <span key={i}>{parte.texto}</span>))
}

function GraficoRespuesta({ grafico }) {
  const lienzo = useRef(null)
  useEffect(() => {
    const instancia = echarts.init(lienzo.current)
    instancia.setOption(opcionGraficoAsistente(grafico))
    return () => instancia.dispose()
  }, [grafico])
  return <div ref={lienzo} className="rep-mensaje__grafico" role="img" aria-label={grafico.titulo || 'Gráfico de la respuesta'} />
}

function Respuesta({ datos }) {
  return (
    <div className="rep-mensaje rep-mensaje--asistente">
      {bloquesRespuesta(datos.respuesta).map((bloque, i) => (bloque.tipo === 'parrafo'
        ? <p key={i}><TextoConNegrita texto={bloque.texto} /></p>
        : (
          <table key={i}>
            <thead><tr>{bloque.encabezados.map((celda, j) => <th key={j}><TextoConNegrita texto={celda} /></th>)}</tr></thead>
            <tbody>{bloque.filas.map((fila, j) => <tr key={j}>{fila.map((celda, k) => <td key={k}><TextoConNegrita texto={celda} /></td>)}</tr>)}</tbody>
          </table>
        )))}
      {(datos.graficos || []).map((grafico, i) => <GraficoRespuesta key={i} grafico={grafico} />)}
      {datos.fechaCorte && <div className="rep-mensaje__alcance">Datos al {fecha(datos.fechaCorte)} · respondido en {datos.segundos} s</div>}
      {datos.consulta && (
        <details>
          <summary>Consulta usada{datos.consultaCorregida ? ' (corregida)' : ''}</summary>
          <p>{datos.consulta}</p>
        </details>
      )}
    </div>
  )
}

export default function PanelAsistente({ abierto, onCerrar, filtros }) {
  const [estado, setEstado] = useState({ tipo: 'espera' })
  const [mensajes, setMensajes] = useState([])
  const [pregunta, setPregunta] = useState('')
  const [consultando, setConsultando] = useState(false)
  const registro = useRef(null)
  const entrada = useRef(null)
  const siguienteId = useRef(1)
  const disponible = estado.tipo === 'ok'

  const verificar = useCallback(async (signal) => {
    setEstado({ tipo: 'espera' })
    try {
      const salud = await obtenerEstadoAsistente({ signal })
      if (signal?.aborted) return
      setEstado(salud.disponible
        ? { tipo: 'ok', modelo: salud.modelo }
        : { tipo: 'error', mensaje: salud.mensaje })
    } catch (error) {
      if (!signal?.aborted) setEstado({ tipo: 'error', mensaje: error.message })
    }
  }, [])

  useEffect(() => {
    if (!abierto) return undefined
    const controlador = new AbortController()
    Promise.resolve().then(() => verificar(controlador.signal))
    entrada.current?.focus()
    return () => controlador.abort()
  }, [abierto, verificar])

  useEffect(() => {
    if (registro.current) registro.current.scrollTop = registro.current.scrollHeight
  }, [mensajes, consultando])

  async function preguntar(texto) {
    const limpio = texto.trim()
    if (!limpio || !disponible || consultando) return
    setPregunta('')
    setMensajes((actual) => [...actual, { id: siguienteId.current++, tipo: 'usuario', texto: limpio }])
    setConsultando(true)
    try {
      const datos = await preguntarAsistente(limpio, filtros)
      setMensajes((actual) => [...actual, { id: siguienteId.current++, tipo: 'respuesta', datos }])
    } catch (error) {
      setMensajes((actual) => [...actual, { id: siguienteId.current++, tipo: 'aviso', texto: error.message }])
      if (error.estado === 503 || error.estado === 0) setEstado({ tipo: 'error', mensaje: error.message })
    } finally {
      setConsultando(false)
    }
  }

  if (!abierto) return null
  return (
    <>
      <div className="rep-velo" onClick={onCerrar} aria-hidden="true" />
      <aside className="rep-asistente" id="rep-asistente" aria-labelledby="asistente-titulo">
        <div className="rep-asistente__cabecera">
          <h2 id="asistente-titulo"><IconoReporte nombre="chat" tamano={18} />Pregúntale a tus datos</h2>
          <button className="rep-boton-icono" type="button" aria-label="Contraer el panel de consulta" onClick={onCerrar}><IconoReporte nombre="cerrar" tamano={14} /></button>
        </div>
        <p className="rep-asistente__nota"><strong>Asistente con IA.</strong> Responde con los mismos datos de la plataforma y comprueba las cifras antes de mostrarlas. Si no indicas periodo o zona, usa los filtros actuales. Revisa las respuestas antes de usarlas en una decisión.</p>
        <div className={`rep-asistente__estado rep-asistente__estado--${estado.tipo}`} role="status">
          {estado.tipo === 'espera' && 'Conectando con el servicio de IA…'}
          {estado.tipo === 'ok' && `Conectado · ${estado.modelo || 'modelo local'}`}
          {estado.tipo === 'error' && (
            <>
              <strong>Servicio de IA no disponible. </strong>{estado.mensaje} El resto de la plataforma sigue funcionando.
              <button className="rep-accion" type="button" onClick={() => verificar()}>Reintentar</button>
            </>
          )}
        </div>
        <div className="rep-asistente__registro" ref={registro} role="log" aria-live="polite" aria-label="Conversación">
          {mensajes.map((mensaje) => {
            if (mensaje.tipo === 'usuario') return <div key={mensaje.id} className="rep-mensaje rep-mensaje--usuario">{mensaje.texto}</div>
            if (mensaje.tipo === 'aviso') return <div key={mensaje.id} className="rep-mensaje rep-mensaje--asistente"><p>{mensaje.texto}</p></div>
            return <Respuesta key={mensaje.id} datos={mensaje.datos} />
          })}
          {consultando && <div className="rep-mensaje rep-mensaje--asistente rep-mensaje--pendiente">Consultando los datos…</div>}
        </div>
        <div className="rep-asistente__sugerencias">
          <span>Preguntas sugeridas</span>
          {SUGERENCIAS.map((sugerencia) => <button key={sugerencia} type="button" disabled={!disponible || consultando} onClick={() => preguntar(sugerencia)}>{sugerencia}</button>)}
        </div>
        <form className="rep-asistente__formulario" onSubmit={(e) => { e.preventDefault(); preguntar(pregunta) }}>
          <label htmlFor="asistente-pregunta" className="sr-only">Escribe tu pregunta</label>
          <input id="asistente-pregunta" ref={entrada} type="text" maxLength={500} autoComplete="off" placeholder="Ej.: top 3 zonas con más deuda vencida" value={pregunta} onChange={(e) => setPregunta(e.target.value)} disabled={!disponible} />
          <button type="submit" disabled={!disponible || consultando || !pregunta.trim()}>Enviar</button>
        </form>
      </aside>
    </>
  )
}
