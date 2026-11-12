import { useEffect, useMemo, useRef, useState } from 'react'
import { buscarEnMapa, obtenerFichaMapa, obtenerMapaSegmentos } from '../../services/segmentosService.js'
import { fecha } from '../../utils/formato.js'
import {
  colorSegmento, filasFicha, porcentajeVarianza, puntosPorSegmento, seriesMapa, zoomA,
} from '../../utils/mapaSegmentos.js'
import echarts from '../../utils/reporteriaEcharts.js'
import './MapaSegmentos.css'

/*
 * HU030 (RF-043): mapa de la segmentación con K-means. Cada punto es un contribuyente en el plano de las dos
 * primeras componentes principales de sus variables de pago, con el color de su segmento. Las etiquetas resaltan
 * un segmento; al hacer clic en un punto se abre su ficha. La búsqueda (por código, documento o nombre) marca los
 * resultados en el mapa. El nombre solo llega con el permiso de consulta de contribuyentes y queda auditado.
 */
const BUSQUEDA_INACTIVA = { estado: 'inactiva', lista: [], error: null }

export default function MapaSegmentos() {
  const [mapa, setMapa] = useState({ cargando: true, datos: null, error: null })
  const [seleccionado, setSeleccionado] = useState(null)
  const [texto, setTexto] = useState('')
  const [listaAbierta, setListaAbierta] = useState(false)
  const [busqueda, setBusqueda] = useState({ estado: 'inactiva', lista: [], error: null })
  const [ficha, setFicha] = useState({ id: null, cargando: false, datos: null, error: null })
  const [revision, setRevision] = useState(0)
  const lienzo = useRef(null)
  const grafico = useRef(null)
  const datos = mapa.datos

  useEffect(() => {
    const controlador = new AbortController()
    obtenerMapaSegmentos({ signal: controlador.signal })
      .then((respuesta) => { if (!controlador.signal.aborted) setMapa({ cargando: false, datos: respuesta, error: null }) })
      .catch((error) => { if (!controlador.signal.aborted) setMapa({ cargando: false, datos: null, error }) })
    return () => controlador.abort()
  }, [revision])

  // Con menos de 3 caracteres (o sin permiso) la búsqueda anterior no cuenta: no se muestra ni se marca.
  const consultaValida = Boolean(datos?.puedeVerNombres) && texto.trim().length >= 3
  const busquedaVigente = consultaValida ? busqueda : BUSQUEDA_INACTIVA
  const grupos = useMemo(() => (datos ? puntosPorSegmento(datos) : []), [datos])
  const resaltados = useMemo(() => busquedaVigente.lista.map((r) => r.idAsignacion), [busquedaVigente.lista])

  function abrirFicha(id) {
    setFicha({ id, cargando: true, datos: null, error: null })
    obtenerFichaMapa(id)
      .then((respuesta) => setFicha((actual) => (actual.id === id ? { id, cargando: false, datos: respuesta, error: null } : actual)))
      .catch((error) => setFicha((actual) => (actual.id === id ? { id, cargando: false, datos: null, error } : actual)))
  }

  // El gráfico se crea una vez por mapa; los clics resuelven el id con la serie y la posición del punto.
  useEffect(() => {
    if (!datos || !lienzo.current) return undefined
    const instancia = echarts.init(lienzo.current, null, { renderer: 'canvas' })
    grafico.current = instancia
    const varianza = datos.modelo.varianzaExplicada ?? []
    const eje = (n) => ({
      type: 'value', scale: true, name: `Componente ${n}${varianza[n - 1] != null ? ` · ${Math.round(varianza[n - 1] * 100)} %` : ''}`,
      nameLocation: 'middle', nameGap: 28, nameTextStyle: { color: '#51627A' },
      axisLabel: { color: '#51627A', formatter: (v) => (Math.round(v * 10) / 10).toLocaleString('es-PE') }, splitLine: { lineStyle: { color: '#E3EAF2' } }, axisLine: { lineStyle: { color: '#B9C8DA' } },
    })
    instancia.setOption({
      animation: datos.puntos.length <= 2000,
      grid: { left: 48, right: 16, top: 16, bottom: 44 },
      xAxis: eje(1),
      yAxis: eje(2),
      dataZoom: [
        { type: 'inside', xAxisIndex: 0, filterMode: 'none' },
        { type: 'inside', yAxisIndex: 0, filterMode: 'none' },
      ],
      tooltip: {
        trigger: 'item',
        formatter: (p) => (p.seriesId?.startsWith('seg-') ? `${p.marker}${p.seriesName}<br/><small>Clic para ver la ficha</small>` : ''),
      },
      series: seriesMapa(datos),
    })
    instancia.on('click', (p) => {
      if (p.seriesId?.startsWith('seg-')) {
        const id = grupos[Number(p.seriesId.slice(4))]?.[p.dataIndex]?.[2]
        if (id != null) abrirFicha(id)
      } else if (p.seriesId === 'resaltados' && Array.isArray(p.value)) {
        abrirFicha(p.value[2])
      }
    })
    const observador = new ResizeObserver(() => instancia.resize())
    observador.observe(lienzo.current)
    return () => { observador.disconnect(); instancia.dispose(); grafico.current = null }
  }, [datos, grupos])

  useEffect(() => {
    if (!grafico.current || !datos) return
    grafico.current.setOption({ series: seriesMapa(datos, { seleccionado, resaltados, activo: ficha.id }) }, { replaceMerge: ['series'] })
  }, [datos, seleccionado, resaltados, ficha.id])

  // Búsqueda con espera corta mientras se escribe.
  useEffect(() => {
    const consulta = texto.trim()
    if (!datos?.puedeVerNombres || consulta.length < 3) return undefined
    const controlador = new AbortController()
    const espera = setTimeout(() => {
      setBusqueda((actual) => ({ ...actual, estado: 'buscando', error: null }))
      buscarEnMapa(consulta, { idModelo: datos.modelo.idModelo, signal: controlador.signal })
        .then((lista) => { if (!controlador.signal.aborted) setBusqueda({ estado: 'lista', lista, error: null }) })
        .catch((error) => { if (!controlador.signal.aborted) setBusqueda({ estado: 'error', lista: [], error }) })
    }, 350)
    return () => { clearTimeout(espera); controlador.abort() }
  }, [texto, datos])

  function irA(resultado) {
    setListaAbierta(false)
    abrirFicha(resultado.idAsignacion)
    const zoom = zoomA(datos, resultado.idAsignacion)
    if (zoom && grafico.current) {
      grafico.current.dispatchAction({ type: 'dataZoom', dataZoomIndex: 0, start: zoom.x.start, end: zoom.x.end })
      grafico.current.dispatchAction({ type: 'dataZoom', dataZoomIndex: 1, start: zoom.y.start, end: zoom.y.end })
    }
  }

  function verTodo() {
    grafico.current?.dispatchAction({ type: 'dataZoom', dataZoomIndex: 0, start: 0, end: 100 })
    grafico.current?.dispatchAction({ type: 'dataZoom', dataZoomIndex: 1, start: 0, end: 100 })
  }

  if (mapa.cargando) return <p className="mensaje-carga" role="status">Consultando el mapa de segmentos…</p>
  if (mapa.error) {
    const sinMapa = mapa.error.codigo === 'MAPA_NO_DISPONIBLE'
    const sinPermiso = mapa.error.estado === 403
    return (
      <section className="pcard" role="status">
        <h2>{sinMapa ? 'Aún no hay una segmentación calculada' : sinPermiso ? 'No tienes acceso a los segmentos' : 'No se pudo consultar el mapa'}</h2>
        <p>{sinMapa ? 'El Responsable de datos debe calcularla primero. Cuando exista, aquí verás a cada contribuyente con el color de su segmento.' : mapa.error.message}</p>
        {!sinMapa && !sinPermiso && <button className="btn" type="button" onClick={() => setRevision((v) => v + 1)}>Reintentar</button>}
      </section>
    )
  }

  const { modelo, segmentos, variables, puedeVerNombres } = datos
  const varianza = porcentajeVarianza(modelo)
  const fichaDatos = ficha.datos
  const indiceFicha = fichaDatos ? segmentos.findIndex((s) => s.idSegmento === fichaDatos.idSegmento) : -1

  return (
    <div className="mapa-seg">
      <div className="mapa-seg__cabecera">
        <p>
          {modelo.contribuyentes.toLocaleString('es-PE')} contribuyentes · carga al {fecha(modelo.fechaCorte)} · {segmentos.length} segmentos
          {varianza != null && <> · el plano conserva el {varianza} % de la variación</>}
        </p>
        <div className="mapa-seg__busqueda">
          <label htmlFor="mapa-buscar">Buscar contribuyente</label>
          <input
            id="mapa-buscar"
            type="search"
            value={texto}
            disabled={!puedeVerNombres}
            placeholder={puedeVerNombres ? 'Código, documento o nombre' : 'Requiere permiso de consulta de contribuyentes'}
            onChange={(e) => { setTexto(e.target.value); setListaAbierta(true) }}
            onFocus={() => setListaAbierta(true)}
            onKeyDown={(e) => { if (e.key === 'Escape') setListaAbierta(false) }}
            aria-describedby="mapa-buscar-ayuda"
          />
          <small id="mapa-buscar-ayuda">
            {!puedeVerNombres ? 'Sin permiso para ver nombres: el mapa y las fichas muestran solo datos de pago.'
              : busquedaVigente.estado === 'buscando' ? 'Buscando…'
                : busquedaVigente.estado === 'lista' ? `${busquedaVigente.lista.length === 20 ? 'Primeros 20' : busquedaVigente.lista.length} resultado${busquedaVigente.lista.length === 1 ? '' : 's'}, marcados en el mapa.`
                  : busquedaVigente.estado === 'error' ? busquedaVigente.error.message : 'Escribe al menos 3 caracteres.'}
          </small>
          {listaAbierta && busquedaVigente.lista.length > 0 && (
            <ul className="mapa-seg__resultados">
              {busquedaVigente.lista.map((r) => {
                const i = segmentos.findIndex((s) => s.idSegmento === r.idSegmento)
                return (
                  <li key={r.idAsignacion}>
                    <button type="button" onClick={() => irA(r)} aria-current={ficha.id === r.idAsignacion}>
                      <span className="mapa-seg__punto" style={{ background: colorSegmento(i) }} aria-hidden="true" />
                      <span><strong>{r.nombre}</strong><small>{r.codigo} · {r.segmento}</small></span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </div>

      <div className="mapa-seg__etiquetas" role="group" aria-label="Resaltar un segmento">
        <button type="button" className="mapa-seg__etiqueta" aria-pressed={seleccionado == null} onClick={() => setSeleccionado(null)}>
          Todos
        </button>
        {segmentos.map((s) => (
          <button
            key={s.idSegmento}
            type="button"
            className="mapa-seg__etiqueta"
            aria-pressed={seleccionado === s.indice}
            title={s.descripcion}
            onClick={() => setSeleccionado(seleccionado === s.indice ? null : s.indice)}
          >
            <span className="mapa-seg__punto" style={{ background: colorSegmento(s.indice) }} aria-hidden="true" />
            {s.nombre}
            <span className="mapa-seg__cuenta">{s.tamano.toLocaleString('es-PE')}</span>
          </button>
        ))}
      </div>

      <div className={`mapa-seg__cuerpo${ficha.id != null ? ' mapa-seg__cuerpo--con-ficha' : ''}`}>
        <div className="mapa-seg__lienzo-marco">
          <div ref={lienzo} className="mapa-seg__lienzo" role="img"
            aria-label={`Mapa de ${modelo.contribuyentes} contribuyentes en ${segmentos.length} segmentos. Usa las etiquetas para resaltar un segmento y la búsqueda para ubicar a un contribuyente.`} />
          <div className="mapa-seg__ayuda">
            <span>Rueda del mouse: acercar · arrastrar: mover</span>
            <button className="btn" type="button" onClick={verTodo}>Ver todo</button>
          </div>
        </div>

        {ficha.id != null && (
          <aside className="mapa-ficha" aria-live="polite" aria-label="Ficha del contribuyente">
            <button className="mapa-ficha__cerrar" type="button" aria-label="Cerrar la ficha" onClick={() => setFicha({ id: null, cargando: false, datos: null, error: null })}>✕</button>
            {ficha.cargando && <p role="status">Cargando la ficha…</p>}
            {ficha.error && <p role="alert">{ficha.error.message}</p>}
            {fichaDatos && (
              <>
                <div className="mapa-ficha__cabeza">
                  <h3>{fichaDatos.contribuyente?.nombre ?? 'Contribuyente'}</h3>
                  <p>{fichaDatos.contribuyente ? `Código ${fichaDatos.contribuyente.codigo}` : `Seudónimo ${fichaDatos.seudonimoCorto}…`}
                    {fichaDatos.tipoPersona && ` · Persona ${fichaDatos.tipoPersona === 'JURIDICA' ? 'jurídica' : 'natural'}`}</p>
                </div>
                <span className="mapa-ficha__segmento" style={{ '--color-seg': colorSegmento(indiceFicha) }}>
                  <span className="mapa-seg__punto" aria-hidden="true" />{fichaDatos.segmento}
                </span>
                <table className="mapa-ficha__tabla">
                  <thead><tr><th scope="col">Variable</th><th scope="col">Contribuyente</th><th scope="col">Mediana del segmento</th></tr></thead>
                  <tbody>
                    {filasFicha(variables, fichaDatos).map((f) => (
                      <tr key={f.clave}><th scope="row">{f.etiqueta}</th><td>{f.valor}</td><td>{f.mediana}</td></tr>
                    ))}
                  </tbody>
                </table>
                <p className="mapa-ficha__nota">
                  {fichaDatos.contribuyente
                    ? 'Ver el nombre queda registrado en la auditoría.'
                    : puedeVerNombres ? 'Sin datos de identificación en la última carga.' : 'Para ver el nombre necesitas el permiso de consulta de contribuyentes.'}
                </p>
              </>
            )}
          </aside>
        )}
      </div>
    </div>
  )
}
