import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import ModalConfirmacion from '../components/ModalConfirmacion.jsx'
import IconoReporte from '../components/reporteria/IconoReporte.jsx'
import MapaSegmentos from '../components/reporteria/MapaSegmentos.jsx'
import PanelAsistente from '../components/reporteria/PanelAsistente.jsx'
import TablaRanking from '../components/reporteria/TablaRanking.jsx'
import TarjetaGrafico from '../components/reporteria/TarjetaGrafico.jsx'
import TarjetaIndicador from '../components/reporteria/TarjetaIndicador.jsx'
import useMenuFlotante from '../components/reporteria/useMenuFlotante.js'
import { useSesionInterna } from '../context/sesionInterna.js'
import {
  eliminarVista, exportarVista, guardarVista, listarVistas, obtenerCatalogo, obtenerDatos,
} from '../services/reporteriaService.js'
import { fecha, fechaHora } from '../utils/formato.js'
import {
  FILTROS_INICIALES, PESTANAS, TIPOS_GRAFICO, avanceMeta, columnasRanking, consultaDeFiltros, consultaDeSeleccion, crearModelo,
  decimal, describirFiltros, estadoGuardable, etiquetaSeleccion, indicadores, mesIso, normalizarEstado, ordenarFilasRanking,
  porcentajeTexto, tablaRanking, vistaExportada,
} from '../utils/reporteria.js'
import { contextoGrafico, tablaDeGrafico } from '../utils/reporteriaGraficos.js'
import './Reporteria.css'

const PESTANA_MAPA = {
  id: 'mapa',
  titulo: 'Mapa de segmentos',
  descripcion: 'Cada punto es un contribuyente, con el color del segmento que le asignó el modelo según cómo paga.',
}

const estadoInicial = (modelo) => ({
  pestana: 'resumen',
  filtros: FILTROS_INICIALES(modelo),
  tipos: {},
  ordenes: {},
  ocultas: {},
  ordenColumnas: {},
  ordenTabla: {},
  fijada: null,
})

function Filtro({ id, etiqueta, valor, cambiado, onCambiar, children }) {
  return (
    <div className="fld">
      <label htmlFor={id}>{etiqueta}</label>
      <select id={id} value={valor} className={cambiado ? 'rep-filtro-cambiado' : undefined} onChange={(e) => onCambiar(e.target.value)}>{children}</select>
    </div>
  )
}

function DialogoGuardar({ abierto, nombreInicial, onGuardar, onCerrar }) {
  const dialogo = useRef(null)
  const entrada = useRef(null)
  useEffect(() => {
    const elemento = dialogo.current
    if (abierto && !elemento.open) {
      elemento.showModal()
      entrada.current.value = nombreInicial
      entrada.current.select()
    }
    if (!abierto && elemento.open) elemento.close()
  }, [abierto, nombreInicial])
  return (
    <dialog ref={dialogo} className="rep-dialogo" aria-labelledby="guardar-vista-titulo" onClose={onCerrar}>
      <form onSubmit={(e) => {
        e.preventDefault()
        const nombre = entrada.current.value.trim()
        if (nombre) onGuardar(nombre)
      }}>
        <h2 id="guardar-vista-titulo">Guardar vista</h2>
        <p>Se guardan la pestaña, los filtros, los tipos de gráfico, el orden, las columnas visibles y la comparación fijada. Solo tú puedes abrirla.</p>
        <label htmlFor="nombre-vista" className="sr-only">Nombre de la vista</label>
        <input id="nombre-vista" ref={entrada} required maxLength={60} placeholder="Nombre de la vista, p. ej.: Morosidad Predial 2026" />
        <div className="rep-dialogo__acciones">
          <button className="btn" type="button" onClick={onCerrar}>Cancelar</button>
          <button className="btn primary" type="submit">Guardar</button>
        </div>
      </form>
    </dialog>
  )
}

function Plataforma({ modelo }) {
  const { sesion } = useSesionInterna()
  const puedeExportar = sesion?.permisos?.includes('REPORTES:EXPORTAR')
  // HU030: el mapa de segmentos es una pestaña aparte; no usa los filtros ni se guarda en las vistas.
  const puedeVerMapa = sesion?.permisos?.includes('SEGMENTOS:CONSULTAR')
  const [vistaMapa, setVistaMapa] = useState(false)
  const pestanasVisibles = puedeVerMapa ? [...PESTANAS, PESTANA_MAPA] : PESTANAS
  const [estado, setEstado] = useState(() => estadoInicial(modelo))
  const [seleccion, setSeleccion] = useState(null)
  const [respuesta, setRespuesta] = useState({ clave: null, A: null, S: null, R: null, error: null })
  const [revision, setRevision] = useState(0)
  const [asistenteAbierto, setAsistenteAbierto] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [vistas, setVistas] = useState(null)
  const [vistaAEliminar, setVistaAEliminar] = useState(null)
  const [avisos, setAvisos] = useState([])
  const [exportando, setExportando] = useState(false)
  const contenedorVistas = useRef(null)
  const contenedorExportar = useRef(null)
  const menuVistas = useMenuFlotante(contenedorVistas)
  const menuExportar = useMenuFlotante(contenedorExportar)
  const graficos = useRef(new Map())
  const cache = useRef(new Map())
  const barraFiltros = useRef(null)
  const siguienteAviso = useRef(1)

  const { pestana, filtros, fijada } = estado
  const periodo = modelo.periodos[filtros.periodo] ?? modelo.periodos[modelo.periodoInicial]

  const avisar = useCallback((texto) => {
    const id = siguienteAviso.current++
    setAvisos((actual) => [...actual, { id, texto }])
    setTimeout(() => setAvisos((actual) => actual.filter((aviso) => aviso.id !== id)), 4200)
  }, [])

  const consultas = useMemo(() => {
    const destacado = seleccion?.dimension === 'contribuyente' ? seleccion.clave : undefined
    const principal = consultaDeFiltros(modelo, filtros, { destacado })
    let referencia = null
    if (fijada) referencia = consultaDeFiltros(modelo, fijada)
    else if (periodo.previo) referencia = { ...consultaDeFiltros(modelo, filtros), desde: mesIso(periodo.previo[0]), hasta: mesIso(periodo.previo[1]) }
    return { principal, seleccion: seleccion ? consultaDeSeleccion(modelo, filtros, seleccion) : null, referencia }
  }, [modelo, filtros, fijada, periodo, seleccion])
  const claveConsulta = `${revision}|${JSON.stringify(consultas)}`
  const cargando = respuesta.clave !== claveConsulta

  useEffect(() => {
    const controlador = new AbortController()
    const pedirConCache = (consulta) => {
      if (!consulta) return Promise.resolve(null)
      const clave = `${revision}|${JSON.stringify(consulta)}`
      const guardada = cache.current.get(clave)
      if (guardada) return Promise.resolve(guardada)
      return obtenerDatos(consulta, { signal: controlador.signal }).then((datos) => {
        if (cache.current.size > 40) cache.current.clear()
        cache.current.set(clave, datos)
        return datos
      })
    }
    Promise.all([pedirConCache(consultas.principal), pedirConCache(consultas.seleccion), pedirConCache(consultas.referencia)])
      .then(([A, S, R]) => {
        if (!controlador.signal.aborted) setRespuesta({ clave: claveConsulta, A, S, R, error: null })
      })
      .catch((error) => {
        if (!controlador.signal.aborted) setRespuesta((actual) => ({ ...actual, clave: claveConsulta, error }))
      })
    return () => controlador.abort()
  }, [consultas, claveConsulta, revision])

  useEffect(() => {
    const elemento = barraFiltros.current
    if (!elemento) return undefined
    const observador = new ResizeObserver(() => document.documentElement.style.setProperty('--rep-alto-filtros', `${elemento.offsetHeight}px`))
    observador.observe(elemento)
    return () => observador.disconnect()
  }, [])

  useEffect(() => {
    const alTecla = (evento) => {
      if (evento.key !== 'Escape' || document.querySelector('dialog[open]')) return
      if (asistenteAbierto && window.innerWidth < 1200) setAsistenteAbierto(false)
      else setSeleccion(null)
    }
    document.addEventListener('keydown', alTecla)
    return () => document.removeEventListener('keydown', alTecla)
  }, [asistenteAbierto])

  const registrarGrafico = useCallback((id, instancia) => {
    if (instancia) graficos.current.set(id, instancia)
    else graficos.current.delete(id)
  }, [])

  const cambiarFiltro = (campo, valor) => {
    setSeleccion(null)
    setEstado((actual) => ({ ...actual, filtros: { ...actual.filtros, [campo]: valor } }))
  }

  const seleccionar = useCallback((dimension, clave, fila) => {
    setSeleccion((actual) => {
      if (actual && actual.dimension === dimension && actual.clave === clave) return null
      if (dimension !== 'contribuyente') return { dimension, clave }
      return { dimension, clave, nombre: fila?.nombre, zona: fila?.zona, segmento: fila?.segmento, uso: fila?.uso, esPrico: fila?.esPrico ?? true }
    })
  }, [])

  const cambiarTipo = useCallback((id, tipo) => setEstado((actual) => ({ ...actual, tipos: { ...actual.tipos, [id]: tipo } })), [])
  const cambiarOrden = useCallback((id, orden) => setEstado((actual) => ({ ...actual, ordenes: { ...actual.ordenes, [id]: orden } })), [])

  const { A, S, R } = respuesta
  const referenciaEtiqueta = fijada ? 'la vista fijada' : periodo.etiquetaPrevia
  const especificaciones = modelo.graficos[pestana]

  const contextos = useMemo(() => {
    if (!A) return null
    return especificaciones.map((especificacion) => {
      const contexto = contextoGrafico({ modelo, especificacion, A, S, F: fijada ? R : null, seleccion, filtros, tipos: estado.tipos, ordenes: estado.ordenes, fijada })
      return { especificacion, contexto, tabla: tablaDeGrafico(especificacion, contexto) }
    })
  }, [modelo, especificaciones, A, S, R, seleccion, filtros, estado.tipos, estado.ordenes, fijada])

  const indicadoresVista = useMemo(() => (A ? indicadores(modelo, pestana, A, R) : []), [modelo, pestana, A, R])
  const meta = pestana === 'resumen' && A ? avanceMeta(A) : null
  const configuracionRanking = modelo.rankings[pestana]
  const { todas, visibles } = useMemo(
    () => columnasRanking(configuracionRanking, estado.ocultas[pestana], estado.ordenColumnas[pestana]),
    [configuracionRanking, estado.ocultas, estado.ordenColumnas, pestana],
  )
  const usaSubconjunto = seleccion?.dimension === 'tributo' && S
  const baseRanking = usaSubconjunto ? S : A
  const ordenTabla = estado.ordenTabla[pestana]
  const { filasRanking, puestos, filaDestacada } = useMemo(() => {
    const filas = baseRanking?.top?.[pestana] ?? []
    const mapa = new Map(filas.map((fila, i) => [fila.id, i + 1]))
    const destacado = baseRanking?.destacado
    let extra = null
    if (destacado && !mapa.has(destacado.fila.id) && destacado.puestos[pestana]) {
      extra = destacado.fila
      mapa.set(extra.id, destacado.puestos[pestana])
    }
    const columna = ordenTabla ? configuracionRanking.columnas.find((c) => c.clave === ordenTabla.clave) : null
    return { filasRanking: ordenarFilasRanking(filas, columna, ordenTabla?.direccion, mapa), puestos: mapa, filaDestacada: extra }
  }, [baseRanking, pestana, ordenTabla, configuracionRanking])

  const filtrosAsistente = useMemo(() => {
    const consulta = consultaDeFiltros(modelo, filtros)
    return { desde: consulta.desde, hasta: consulta.hasta, zona: consulta.zona || null, tributo: consulta.tributo || null, segmento: consulta.segmento || null }
  }, [modelo, filtros])

  function alternarFijada() {
    if (fijada) avisar('Se quitó la comparación fijada.')
    else avisar('Vista fijada. Cambia los filtros para comparar contra ella.')
    setEstado((actual) => ({ ...actual, fijada: actual.fijada ? null : { ...actual.filtros } }))
  }

  function restablecer() {
    setSeleccion(null)
    setEstado((actual) => ({ ...actual, filtros: FILTROS_INICIALES(modelo) }))
    avisar('Filtros restablecidos.')
  }

  function cambiarPestana(id) {
    if (id === PESTANA_MAPA.id) {
      setVistaMapa(true)
      return
    }
    setVistaMapa(false)
    setEstado((actual) => ({ ...actual, pestana: id }))
  }

  function alTeclaPestana(evento, indice) {
    const total = pestanasVisibles.length
    let destino = null
    if (evento.key === 'ArrowRight') destino = (indice + 1) % total
    else if (evento.key === 'ArrowLeft') destino = (indice + total - 1) % total
    else if (evento.key === 'Home') destino = 0
    else if (evento.key === 'End') destino = total - 1
    if (destino != null) {
      evento.preventDefault()
      cambiarPestana(pestanasVisibles[destino].id)
      document.getElementById(`rep-pestana-${pestanasVisibles[destino].id}`)?.focus()
    }
  }

  async function guardar(nombre) {
    try {
      await guardarVista(nombre, estadoGuardable(estado))
      setGuardando(false)
      avisar(`Vista "${nombre}" guardada.`)
    } catch (error) {
      avisar(error.message || `No se pudo guardar la vista "${nombre}".`)
    }
  }

  async function abrirMenuVistas() {
    const abrir = !menuVistas.abierto
    menuExportar.setAbierto(false)
    menuVistas.setAbierto(abrir)
    if (!abrir) return
    setVistas(null)
    try {
      setVistas(await listarVistas())
    } catch (error) {
      setVistas([])
      avisar(error.message || 'No se pudieron leer las vistas guardadas.')
    }
  }

  function abrirVista(vista) {
    menuVistas.setAbierto(false)
    setSeleccion(null)
    setEstado(normalizarEstado(modelo, vista.estado, estadoInicial(modelo)))
    avisar(`Vista "${vista.nombre}" abierta.`)
  }

  async function confirmarEliminarVista() {
    const vista = vistaAEliminar
    setVistaAEliminar(null)
    try {
      await eliminarVista(vista.idVista)
      setVistas((actual) => actual?.filter((v) => v.idVista !== vista.idVista) ?? null)
      avisar('Vista eliminada.')
    } catch (error) {
      avisar(error.message || 'No se pudo eliminar la vista.')
    }
  }

  async function exportar(formato) {
    menuExportar.setAbierto(false)
    setExportando(true)
    const etiqueta = formato === 'PDF' ? 'el PDF' : 'el Excel'
    avisar(`Generando ${etiqueta}…`)
    try {
      const tablaTop = tablaRanking(visibles, filasRanking, puestos)
      const vista = vistaExportada({
        modelo,
        estado,
        seleccion,
        indicadoresVista,
        graficos: contextos.map(({ especificacion, contexto, tabla }) => ({
          titulo: especificacion.titulo,
          tipo: TIPOS_GRAFICO[contexto.tipo],
          encabezados: tabla.encabezados,
          filas: tabla.filas.map((fila) => fila.crudo),
          imagen: formato === 'PDF' ? graficos.current.get(especificacion.id)?.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' }) ?? null : null,
        })),
        ranking: filasRanking.length ? { titulo: configuracionRanking.titulo, encabezados: tablaTop.encabezados, filas: tablaTop.filas } : null,
      })
      const { blob, nombre } = await exportarVista(formato, vista)
      const url = URL.createObjectURL(blob)
      const enlace = document.createElement('a')
      enlace.href = url
      enlace.download = nombre
      document.body.append(enlace)
      enlace.click()
      setTimeout(() => {
        URL.revokeObjectURL(url)
        enlace.remove()
      }, 500)
      avisar(`Se descargó ${etiqueta} de la vista actual.`)
    } catch (error) {
      avisar(error.message || `No se pudo generar ${etiqueta}.`)
    } finally {
      setExportando(false)
    }
  }

  const datosPestana = PESTANAS.find((p) => p.id === pestana)
  const pestanaActiva = vistaMapa ? PESTANA_MAPA.id : pestana
  const nombreSugerido = `${datosPestana.titulo} · ${periodo.etiqueta}`.slice(0, 60)
  const fechaCorte = fecha(modelo.catalogo.fechaCorte)
  const error = respuesta.error

  return (
    <div className="reporteria" aria-busy={cargando}>
      <div className="rep-acciones">
        <button className="btn" type="button" onClick={() => setGuardando(true)}><IconoReporte nombre="guardar" />Guardar vista</button>
        <div className="rep-desplegable" ref={contenedorVistas}>
          <button className="btn" type="button" aria-haspopup="true" aria-expanded={menuVistas.abierto} onClick={abrirMenuVistas}>Vistas guardadas ▾</button>
          {menuVistas.abierto && (
            <div className="rep-menu">
              {vistas == null && <div className="rep-menu__vacio" role="status">Consultando las vistas…</div>}
              {vistas?.length === 0 && <div className="rep-menu__vacio">Aún no hay vistas guardadas. Usa «Guardar vista».</div>}
              {vistas?.map((vista) => (
                <div className="rep-menu__fila" key={vista.idVista}>
                  <button className="rep-menu__opcion" type="button" onClick={() => abrirVista(vista)}>
                    <span>{vista.nombre}<small>Guardada el {fechaHora(vista.fechaActualizacion)}</small></span>
                  </button>
                  <button className="rep-menu__eliminar" type="button" aria-label={`Eliminar la vista ${vista.nombre}`} onClick={() => { menuVistas.setAbierto(false); setVistaAEliminar(vista) }}>✕</button>
                </div>
              ))}
            </div>
          )}
        </div>
        {puedeExportar && (
          <div className="rep-desplegable" ref={contenedorExportar}>
            <button className="btn" type="button" aria-haspopup="true" aria-expanded={menuExportar.abierto} disabled={!A || exportando || vistaMapa} onClick={() => { menuVistas.setAbierto(false); menuExportar.setAbierto(!menuExportar.abierto) }}>
              <IconoReporte nombre="exportar" />{exportando ? 'Exportando…' : 'Exportar ▾'}
            </button>
            {menuExportar.abierto && (
              <div className="rep-menu">
                <button className="rep-menu__opcion" type="button" onClick={() => exportar('EXCEL')}>
                  <span>Excel (.xlsx) de la vista actual<small>Indicadores, datos de cada gráfico y el Top 10 con las columnas visibles, en su orden</small></span>
                </button>
                <button className="rep-menu__opcion" type="button" onClick={() => exportar('PDF')}>
                  <span>PDF de la vista actual<small>Filtros, fecha de corte, indicadores, gráficos y Top 10</small></span>
                </button>
              </div>
            )}
          </div>
        )}
        <button className="btn rep-interruptor" type="button" role="switch" aria-checked={asistenteAbierto} aria-controls="rep-asistente" onClick={() => setAsistenteAbierto(!asistenteAbierto)}>
          <span className="rep-interruptor__marca" aria-hidden="true" />Asistente IA
        </button>
      </div>

      <nav className="rep-pestanas" role="tablist" aria-label="Reportes">
        {pestanasVisibles.map((p, i) => (
          <button
            key={p.id}
            id={`rep-pestana-${p.id}`}
            type="button"
            className="rep-pestana"
            role="tab"
            aria-selected={pestanaActiva === p.id}
            aria-controls="rep-panel"
            tabIndex={pestanaActiva === p.id ? 0 : -1}
            onClick={() => cambiarPestana(p.id)}
            onKeyDown={(e) => alTeclaPestana(e, i)}
          >{p.titulo}</button>
        ))}
      </nav>

      <section className="filters rep-filtros" aria-label="Filtros globales" ref={barraFiltros} hidden={vistaMapa}>
        <Filtro id="rep-periodo" etiqueta="Periodo" valor={filtros.periodo} cambiado={filtros.periodo !== modelo.periodoInicial} onCambiar={(v) => cambiarFiltro('periodo', v)}>
          {Object.entries(modelo.periodos).map(([clave, p]) => <option key={clave} value={clave}>{p.etiqueta}</option>)}
        </Filtro>
        <Filtro id="rep-zona" etiqueta="Zona" valor={filtros.zona} cambiado={Boolean(filtros.zona)} onCambiar={(v) => cambiarFiltro('zona', v)}>
          <option value="">Todas las zonas</option>
          {modelo.zonas.map((z) => <option key={z.codigo} value={z.codigo}>{z.nombre}</option>)}
        </Filtro>
        <Filtro id="rep-tributo" etiqueta="Tipo de tributo" valor={filtros.tributo} cambiado={Boolean(filtros.tributo)} onCambiar={(v) => cambiarFiltro('tributo', v)}>
          <option value="">Todos los tributos</option>
          {modelo.tributos.map((t) => <option key={t.codigo} value={t.codigo}>{t.nombre}</option>)}
        </Filtro>
        <Filtro id="rep-segmento" etiqueta="Segmento" valor={filtros.segmento} cambiado={Boolean(filtros.segmento)} onCambiar={(v) => cambiarFiltro('segmento', v)}>
          <option value="">Todos los segmentos</option>
          {modelo.segmentos.map((s) => <option key={s} value={s}>{s}</option>)}
        </Filtro>
        <button className="btn" type="button" onClick={restablecer}>Restablecer filtros</button>
        <button
          className={`btn${fijada ? ' rep-boton-activo' : ''}`}
          type="button"
          aria-pressed={Boolean(fijada)}
          title="Guarda los filtros actuales como referencia. Luego cambia los filtros y los indicadores se compararán contra esta referencia."
          onClick={alternarFijada}
        >
          <IconoReporte nombre="fijar" tamano={14} />{fijada ? 'Quitar comparación' : 'Fijar para comparar'}
        </button>
        <span className="rep-corte">Datos al {fechaCorte}</span>
        {(fijada || seleccion) && (
          <div className="rep-chips" aria-live="polite">
            {fijada && (
              <span className="rep-chip rep-chip--fijada">
                <IconoReporte nombre="fijar" tamano={13} />Comparando con: {describirFiltros(modelo, fijada)}
                <button type="button" aria-label="Quitar la comparación fijada" onClick={() => setEstado((actual) => ({ ...actual, fijada: null }))}>Quitar</button>
              </span>
            )}
            {seleccion && (
              <span className="rep-chip rep-chip--seleccion">
                Resaltado: {etiquetaSeleccion(modelo, seleccion)}
                <button type="button" aria-label="Limpiar el resaltado" onClick={() => setSeleccion(null)}>Limpiar (Esc)</button>
              </span>
            )}
          </div>
        )}
      </section>

      <div className={`rep-distribucion${asistenteAbierto ? ' rep-distribucion--con-panel' : ''}`}>
        {vistaMapa && (
          <div id="rep-panel" className="rep-contenido" role="tabpanel" aria-labelledby={`rep-pestana-${PESTANA_MAPA.id}`}>
            <div className="rep-titulo-pestana">
              <h2>{PESTANA_MAPA.titulo}</h2>
              <p>{PESTANA_MAPA.descripcion}</p>
            </div>
            <MapaSegmentos />
          </div>
        )}
        <div id={vistaMapa ? undefined : 'rep-panel'} className="rep-contenido" role="tabpanel" aria-labelledby={`rep-pestana-${pestana}`} hidden={vistaMapa}>
          <div className="rep-titulo-pestana">
            <h2>{datosPestana.titulo}</h2>
            <p>{datosPestana.descripcion}</p>
          </div>
          {modelo.avisos[pestana] && <div className="rep-info"><IconoReporte nombre="info" />{modelo.avisos[pestana]}</div>}
          {!A && !error && <p className="mensaje-carga" role="status">Consultando la carga publicada…</p>}
          {error && (
            <section className="pcard" role="status">
              <h2>No se pudo consultar la plataforma analítica</h2>
              <p>{error.message}</p>
              <button className="btn" type="button" onClick={() => setRevision((valor) => valor + 1)}>Reintentar</button>
            </section>
          )}
          {A && (
            <>
              {meta && meta.desvio > meta.umbral && (
                <div className="rep-alerta" role="status">
                  <IconoReporte nombre="alerta" tamano={18} />
                  <div><strong>Alerta de meta. </strong>Lo cobrado de la emisión ({porcentajeTexto(meta.cobrado)}) está {decimal(meta.desvio)} % por debajo de la meta del periodo ({porcentajeTexto(meta.meta)}). El umbral de alerta es {decimal(meta.umbral)} %.</div>
                </div>
              )}
              <section className="rep-kpis" aria-label="Indicadores clave">
                {indicadoresVista.map((indicador) => <TarjetaIndicador key={indicador.etiqueta} indicador={indicador} referencia={R ? referenciaEtiqueta : null} segmentos={modelo.segmentos} />)}
              </section>
              <section className="rep-graficos" aria-label="Gráficos">
                {contextos.map(({ especificacion, contexto, tabla }) => (
                  <TarjetaGrafico
                    key={especificacion.id}
                    especificacion={especificacion}
                    contexto={contexto}
                    tabla={tabla}
                    ordenes={estado.ordenes}
                    onCambiarTipo={cambiarTipo}
                    onCambiarOrden={cambiarOrden}
                    onSeleccionar={seleccionar}
                    onAviso={avisar}
                    registrarGrafico={registrarGrafico}
                  />
                ))}
              </section>
              <TablaRanking
                configuracion={configuracionRanking}
                todas={todas}
                visibles={visibles}
                filas={filasRanking}
                filaDestacada={filaDestacada}
                puestos={puestos}
                ordenTabla={ordenTabla}
                ocultas={estado.ocultas[pestana] ?? []}
                seleccion={seleccion}
                subtituloExtra={usaSubconjunto ? ` Montos de: ${modelo.tributos[seleccion.clave].nombre}.` : ''}
                onSeleccionar={seleccionar}
                onOrdenar={(clave) => setEstado((actual) => {
                  const previo = actual.ordenTabla[pestana]
                  const direccion = previo?.clave === clave && previo.direccion === 'desc' ? 'asc' : 'desc'
                  return { ...actual, ordenTabla: { ...actual.ordenTabla, [pestana]: { clave, direccion } } }
                })}
                onQuitarOrden={() => setEstado((actual) => {
                  const siguiente = { ...actual.ordenTabla }
                  delete siguiente[pestana]
                  return { ...actual, ordenTabla: siguiente }
                })}
                onCambiarOcultas={(ocultas) => setEstado((actual) => ({ ...actual, ocultas: { ...actual.ocultas, [pestana]: ocultas } }))}
                onCambiarOrdenColumnas={(orden) => setEstado((actual) => ({ ...actual, ordenColumnas: { ...actual.ordenColumnas, [pestana]: orden } }))}
                onRestablecerColumnas={() => setEstado((actual) => {
                  const ocultas = { ...actual.ocultas }
                  const ordenColumnas = { ...actual.ordenColumnas }
                  delete ocultas[pestana]
                  delete ordenColumnas[pestana]
                  return { ...actual, ocultas, ordenColumnas }
                })}
              />
            </>
          )}
        </div>
        <PanelAsistente abierto={asistenteAbierto} onCerrar={() => setAsistenteAbierto(false)} filtros={filtrosAsistente} />
      </div>

      <DialogoGuardar abierto={guardando} nombreInicial={nombreSugerido} onGuardar={guardar} onCerrar={() => setGuardando(false)} />
      <ModalConfirmacion
        abierto={Boolean(vistaAEliminar)}
        titulo="Eliminar vista"
        mensaje={vistaAEliminar ? `Se eliminará la vista "${vistaAEliminar.nombre}".` : ''}
        textoConfirmar="Eliminar"
        peligro
        onConfirmar={confirmarEliminarVista}
        onCerrar={() => setVistaAEliminar(null)}
      />
      <div className="rep-avisos" aria-live="polite">
        {avisos.map((aviso) => <div className="rep-aviso" key={aviso.id}>{aviso.texto}</div>)}
      </div>
    </div>
  )
}

export default function Reporteria() {
  const [estado, setEstado] = useState({ modelo: null, error: null })
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controlador = new AbortController()
    obtenerCatalogo({ signal: controlador.signal })
      .then((catalogo) => { if (!controlador.signal.aborted) setEstado({ modelo: crearModelo(catalogo), error: null }) })
      .catch((error) => { if (!controlador.signal.aborted) setEstado({ modelo: null, error }) })
    return () => controlador.abort()
  }, [revision])

  if (estado.error) {
    return (
      <section className="pcard" role="status">
        <h2>{estado.error.codigo === 'REPORTERIA_SIN_CARGA' ? 'Aún no hay una carga publicada' : 'No se pudo consultar la plataforma analítica'}</h2>
        <p>{estado.error.message}</p>
        <button className="btn" type="button" onClick={() => { setEstado({ modelo: null, error: null }); setRevision((valor) => valor + 1) }}>Reintentar</button>
      </section>
    )
  }
  if (!estado.modelo) return <p className="mensaje-carga" role="status">Consultando la carga publicada…</p>
  return <Plataforma modelo={estado.modelo} />
}
