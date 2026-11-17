import { useEffect, useMemo, useRef, useState } from 'react'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import { useSesionInterna } from '../context/sesionInterna.js'
import {
  MAX_DOCUMENTO, MINIMO_SUGERENCIAS,
  buscarCoincidenciasVentanilla, confirmarPagoSolicitud, consultarContribuyente,
  crearSolicitudVentanilla, emitirSolicitud, firmaEnVentanilla,
  listarSolicitudesContribuyente, normalizarDocumento,
  obtenerDocumentoFirmaSolicitud, registrarFirmaSolicitud, textoDocumento, urlConstanciaPdf,
  verificarDeudaSolicitud,
} from '../services/constanciaVentanillaService.js'
import {
  FILTROS_DEUDA_INICIALES, filtrarObligacionesDeuda, opcionesDeuda,
} from '../utils/constanciaVentanilla.js'
import { fechaHora, moneda } from '../utils/formato.js'
import { TAMANIOS_PAGINA, paginar } from '../utils/paginacion.js'
import './ConstanciaVentanilla.css'

const ETIQUETA_ESTADO = {
  INICIADA: 'Pendiente de firma',
  FIRMADA: 'Firmada por el contribuyente',
  DEUDA_VERIFICADA: 'Deuda verificada',
  ORDEN_PAGO_EMITIDA: 'Orden de pago pendiente',
  PAGADA: 'Pago recibido',
  EMITIDA: 'Constancia emitida',
  RECHAZADA: 'Solicitud rechazada',
}

const PASOS = ['Solicitud', 'Firma', 'Orden de pago', 'Pago', 'Constancia']

const PASO_ACTUAL = {
  INICIADA: 1,
  FIRMADA: 2,
  DEUDA_VERIFICADA: 2,
  ORDEN_PAGO_EMITIDA: 3,
  PAGADA: 4,
  EMITIDA: PASOS.length,
}

const ESTADOS_CERRADOS = new Set(['EMITIDA', 'RECHAZADA'])

function Campo({ etiqueta, valor }) {
  return (
    <div className="pf">
      <span className="pl">{etiqueta}</span>
      <span className="pv">{valor === '' || valor == null ? '—' : valor}</span>
    </div>
  )
}

function EstadoDeuda({ deuda }) {
  const obligaciones = useMemo(
    () => deuda.obligaciones ?? deuda.obligacionesPendientes ?? [],
    [deuda],
  )
  const [filtros, setFiltros] = useState(FILTROS_DEUDA_INICIALES)
  const [porPagina, setPorPagina] = useState(TAMANIOS_PAGINA[0])
  const [pagina, setPagina] = useState(1)

  const opciones = useMemo(() => opcionesDeuda(obligaciones), [obligaciones])
  const filtradas = useMemo(
    () => filtrarObligacionesDeuda(obligaciones, filtros),
    [obligaciones, filtros],
  )
  const { visibles, paginaActual } = paginar(filtradas, pagina, porPagina)
  const hayFiltros = Object.values(filtros).some(Boolean)

  if (deuda.puedeSolicitar) {
    return (
      <div className="confirmacion" role="status">
        <strong>Sin deuda pendiente.</strong> El contribuyente puede solicitar la constancia de no adeudo.
      </div>
    )
  }

  function cambiarFiltro(campo) {
    return (evento) => {
      setFiltros((actuales) => ({ ...actuales, [campo]: evento.target.value }))
      setPagina(1)
    }
  }

  function limpiarFiltros() {
    setFiltros(FILTROS_DEUDA_INICIALES)
    setPagina(1)
  }

  return (
    <>
      <div className="alert" role="alert">
        <strong>Deuda pendiente: {moneda(deuda.total)}.</strong> La constancia de no adeudo requiere que el
        contribuyente no registre deuda al presentar la solicitud.
      </div>
      {obligaciones.length > 0 && (
        <>
          <div className="cv-filtros" role="group" aria-label="Filtros de obligaciones">
            <div className="fld">
              <label htmlFor="cv-filtro-tributo">Tributo</label>
              <select id="cv-filtro-tributo" value={filtros.tributo} onChange={cambiarFiltro('tributo')}>
                <option value="">Todos los tributos</option>
                {opciones.tributos.map((tributo) => <option key={tributo} value={tributo}>{tributo}</option>)}
              </select>
            </div>
            <div className="fld">
              <label htmlFor="cv-filtro-anio">Año</label>
              <select id="cv-filtro-anio" value={filtros.anio} onChange={cambiarFiltro('anio')}>
                <option value="">Todos los años</option>
                {opciones.anios.map((anio) => <option key={anio} value={anio}>{anio}</option>)}
              </select>
            </div>
            <div className="fld">
              <label htmlFor="cv-filtro-estado">Estado</label>
              <select id="cv-filtro-estado" value={filtros.estado} onChange={cambiarFiltro('estado')}>
                <option value="">Todos los estados</option>
                {opciones.estados.map((estado) => <option key={estado} value={estado}>{estado}</option>)}
              </select>
            </div>
            {hayFiltros && (
              <button className="btn" type="button" onClick={limpiarFiltros}>Limpiar filtros</button>
            )}
          </div>

          {filtradas.length === 0 ? (
            <p className="hint cv-vacio" role="status">
              No hay obligaciones que coincidan con los filtros seleccionados.
            </p>
          ) : (
            <>
              <div className="tabla-scroll" role="region" aria-label="Obligaciones pendientes" tabIndex={0}>
                <table className="tabla">
                  <thead>
                    <tr>
                      <th scope="col">Tributo</th>
                      <th scope="col">Periodo</th>
                      <th scope="col">Vencimiento</th>
                      <th scope="col" className="num">Deuda</th>
                      <th scope="col">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibles.map((obligacion) => (
                      <tr key={obligacion.id ?? `${obligacion.tributoCodigo}-${obligacion.periodo}-${obligacion.cuota}`}>
                        <td>{obligacion.tributo || '—'}</td>
                        <td>{obligacion.periodo || '—'}</td>
                        <td>{fechaHora(obligacion.fechaVencimiento)}</td>
                        <td className="num">{moneda(obligacion.deuda)}</td>
                        <td>{obligacion.estado || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="cv-paginacion">
                <PaginacionTabla
                  id="cv-deudas"
                  totalRegistros={filtradas.length}
                  pagina={paginaActual}
                  tamanio={porPagina}
                  opciones={TAMANIOS_PAGINA}
                  onCambiarPagina={setPagina}
                  onCambiarTamanio={(valor) => { setPorPagina(valor); setPagina(1) }}
                />
              </div>
            </>
          )}

          <p className="hint cv-nota">
            El total adeudado corresponde a todas las obligaciones; los filtros no lo modifican.
          </p>
        </>
      )}
    </>
  )
}

function Pasos({ estado }) {
  const actual = PASO_ACTUAL[estado]
  return (
    <div className="cv-pasos" aria-label="Pasos del trámite">
      {PASOS.map((paso, indice) => (
        <span key={paso} className={indice < actual ? 'hecho' : indice === actual ? 'actual' : 'pendiente'}>
          {indice + 1}. {paso}
        </span>
      ))}
    </div>
  )
}

export default function ConstanciaVentanilla() {
  const { sesion } = useSesionInterna()
  const [documento, setDocumento] = useState('')
  const [consulta, setConsulta] = useState(null)
  const [solicitud, setSolicitud] = useState(null)
  const [documentoFirma, setDocumentoFirma] = useState(null)
  const [prediosSeleccionados, setPrediosSeleccionados] = useState([])
  const [fechaDesde, setFechaDesde] = useState('')
  const [fechaHasta, setFechaHasta] = useState('')
  const [resultado, setResultado] = useState(null)
  const [solicitudes, setSolicitudes] = useState([])
  const [cargandoSolicitudes, setCargandoSolicitudes] = useState(false)
  const [errorSolicitudes, setErrorSolicitudes] = useState('')
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(null)
  const [paginaSolicitudes, setPaginaSolicitudes] = useState(1)
  const [tamanioSolicitudes, setTamanioSolicitudes] = useState(TAMANIOS_PAGINA[0])
  const [sugerencias, setSugerencias] = useState([])
  const [sugerenciasAbiertas, setSugerenciasAbiertas] = useState(false)
  const [sugerenciaActiva, setSugerenciaActiva] = useState(-1)
  const [buscandoSugerencias, setBuscandoSugerencias] = useState(false)
  const [errorSugerencias, setErrorSugerencias] = useState('')
  const emitiendo = useRef(false)
  const solicitudSugerencias = useRef(0)
  const temporizadorSugerencias = useRef(null)
  const buscarSugerenciasRef = useRef(null)
  const omitirSugerencias = useRef(false)
  const comprobanteRef = useRef(null)
  const firmaRegistradaReciente = useRef(false)

  useEffect(() => {
    buscarSugerenciasRef.current = buscarSugerencias
  })

  useEffect(() => {
    if (firmaRegistradaReciente.current && solicitud?.firmaContribuyente) {
      comprobanteRef.current?.focus()
      firmaRegistradaReciente.current = false
    }
  }, [solicitud?.firmaContribuyente])

  useEffect(() => {
    if (omitirSugerencias.current) {
      omitirSugerencias.current = false
      return undefined
    }
    if (documento.length < MINIMO_SUGERENCIAS) {
      return undefined
    }
    const timer = setTimeout(() => {
      temporizadorSugerencias.current = null
      buscarSugerenciasRef.current?.(documento)
    }, 400)
    temporizadorSugerencias.current = timer
    return () => {
      clearTimeout(timer)
      if (temporizadorSugerencias.current === timer) temporizadorSugerencias.current = null
    }
  }, [documento])

  const trabajador = sesion?.nombreCompleto || sesion?.nombreUsuario || 'Trabajador de ventanilla'
  const contribuyente = consulta?.contribuyente
  const predios = consulta?.predios ?? []
  const estadoSolicitud = solicitud?.estado
  const datosFirma = documentoFirma?.datosContribuyente
  const nombreFirma = datosFirma?.nombre || contribuyente?.nombreRazonSocial || '—'
  const documentoFirmaTexto = datosFirma
    ? `${datosFirma.tipoDocumento ?? ''} ${datosFirma.numeroDocumento ?? ''}`.trim()
    : [contribuyente?.tipoDocumento, contribuyente?.numeroDocumento].filter(Boolean).join(' ')
  const codigoFirma = datosFirma?.codigo || contribuyente?.codigoContribuyente || '—'
  const haySolicitudAbiertaVentanilla = solicitudes.some((item) => (
    item.canal === 'VENTANILLA' && !ESTADOS_CERRADOS.has(item.estado)
  ))
  /* La fila del trámite en curso se pinta con el estado local, sin volver a pedir la lista en cada paso. */
  const solicitudesVisibles = useMemo(() => {
    if (!solicitud || resultado) return solicitudes
    return solicitudes.map((item) => (
      item.idSolicitud === solicitud.idSolicitud ? { ...item, ...solicitud } : item
    ))
  }, [solicitudes, solicitud, resultado])
  const { visibles: solicitudesPagina, paginaActual: paginaSolicitudesActual } = paginar(
    solicitudesVisibles, paginaSolicitudes, tamanioSolicitudes,
  )

  async function refrescarSolicitudes(valor) {
    setCargandoSolicitudes(true)
    setErrorSolicitudes('')
    try {
      setSolicitudes(await listarSolicitudesContribuyente(valor))
    } catch (err) {
      setSolicitudes([])
      setErrorSolicitudes(err.message || 'No se pudieron cargar las solicitudes del contribuyente.')
    } finally {
      setCargandoSolicitudes(false)
    }
  }

  async function buscarSugerencias(texto) {
    const actual = ++solicitudSugerencias.current
    setBuscandoSugerencias(true)
    setErrorSugerencias('')
    try {
      const encontrados = await buscarCoincidenciasVentanilla(texto)
      if (actual !== solicitudSugerencias.current) return
      setSugerencias(encontrados)
      setSugerenciasAbiertas(encontrados.length > 0)
      setSugerenciaActiva(encontrados.length > 0 ? 0 : -1)
    } catch (err) {
      if (actual !== solicitudSugerencias.current) return
      setSugerencias([])
      setSugerenciasAbiertas(false)
      setSugerenciaActiva(-1)
      setErrorSugerencias(err.estado === 403
        ? 'Tu sesión no tiene permiso para buscar coincidencias por documento.'
        : 'No se pudieron cargar las coincidencias. Puedes consultar con el documento completo.')
    } finally {
      if (actual === solicitudSugerencias.current) setBuscandoSugerencias(false)
    }
  }

  function cerrarSugerencias() {
    setSugerenciasAbiertas(false)
    setSugerenciaActiva(-1)
  }

  function abrirSugerencias() {
    if (sugerencias.length > 0) {
      setSugerenciasAbiertas(true)
      setSugerenciaActiva((actual) => (actual >= 0 ? actual : 0))
    }
  }

  function cerrarSiSale(evento) {
    if (!evento.currentTarget.contains(evento.relatedTarget)) cerrarSugerencias()
  }

  function manejarTecladoSugerencias(evento) {
    if (evento.key === 'Escape' || evento.key === 'Tab') {
      cerrarSugerencias()
      return
    }
    if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      if (!sugerencias.length) return
      evento.preventDefault()
      if (!sugerenciasAbiertas) {
        setSugerenciasAbiertas(true)
        setSugerenciaActiva(evento.key === 'ArrowDown' ? 0 : sugerencias.length - 1)
        return
      }
      setSugerenciaActiva((actual) => (evento.key === 'ArrowDown'
        ? (actual + 1) % sugerencias.length
        : (actual - 1 + sugerencias.length) % sugerencias.length))
      return
    }
    if (evento.key === 'Enter' && sugerenciasAbiertas && sugerenciaActiva >= 0 && sugerencias[sugerenciaActiva]) {
      evento.preventDefault()
      seleccionarSugerencia(sugerencias[sugerenciaActiva])
    }
  }

  function cambiarDocumento(evento) {
    omitirSugerencias.current = false
    solicitudSugerencias.current++
    setDocumento(normalizarDocumento(evento.target.value))
    if (error) setError('')
    setSugerencias([])
    setSugerenciasAbiertas(false)
    setSugerenciaActiva(-1)
    setBuscandoSugerencias(false)
    setErrorSugerencias('')
  }

  function seleccionarSugerencia(contribuyenteCoincidente) {
    if (temporizadorSugerencias.current) {
      clearTimeout(temporizadorSugerencias.current)
      temporizadorSugerencias.current = null
    }
    solicitudSugerencias.current++
    omitirSugerencias.current = true
    setDocumento(contribuyenteCoincidente.doc)
    setSugerencias([])
    setSugerenciasAbiertas(false)
    setSugerenciaActiva(-1)
    setBuscandoSugerencias(false)
    setErrorSugerencias('')
    ejecutarConsulta(contribuyenteCoincidente.doc)
  }

  async function ejecutar(paso, accion) {
    if (ocupado || (paso === 'emision' && emitiendo.current)) return
    if (paso === 'emision') emitiendo.current = true
    setError('')
    setOcupado(paso)
    try {
      await accion()
    } catch (err) {
      setError(err.message || 'No se pudo completar la operación.')
    } finally {
      if (paso === 'emision') emitiendo.current = false
      setOcupado(null)
    }
  }

  async function ejecutarConsulta(valor) {
    setError('')
    setSolicitud(null)
    setDocumentoFirma(null)
    setResultado(null)
    setPrediosSeleccionados([])
    setFechaDesde('')
    setFechaHasta('')
    setOcupado('consulta')
    try {
      const datos = await consultarContribuyente(valor)
      setConsulta(datos)
      omitirSugerencias.current = true
      setDocumento(datos.contribuyente.numeroDocumento)
      setPaginaSolicitudes(1)
      await refrescarSolicitudes(datos.contribuyente.numeroDocumento)
    } catch (err) {
      setConsulta(null)
      setError(err.message || 'No se pudo consultar al contribuyente.')
    } finally {
      setOcupado(null)
    }
  }

  function consultar(evento) {
    evento.preventDefault()
    cerrarSugerencias()
    ejecutarConsulta(documento)
  }

  function crearSolicitud() {
    ejecutar('solicitud', async () => {
      const creada = await crearSolicitudVentanilla(contribuyente.numeroDocumento)
      setSolicitud(creada)
      setDocumentoFirma(null)
      setPrediosSeleccionados([])
      setFechaDesde('')
      setFechaHasta('')
      await refrescarSolicitudes(contribuyente.numeroDocumento)
    })
  }

  function prepararDocumento() {
    ejecutar('firma', async () => {
      setDocumentoFirma(await obtenerDocumentoFirmaSolicitud(solicitud.idSolicitud))
    })
  }

  function registrarFirmaContribuyente() {
    ejecutar('firma', async () => {
      const firmada = await registrarFirmaSolicitud(
        solicitud.idSolicitud,
        firmaEnVentanilla(documentoFirma.hashSha256),
        'Firma del contribuyente en ventanilla',
      )
      setSolicitud(firmada)
      setDocumentoFirma(null)
      firmaRegistradaReciente.current = true
    })
  }

  function verificarDeuda() {
    ejecutar('verificacion', async () => {
      setSolicitud(await verificarDeudaSolicitud(solicitud.idSolicitud))
    })
  }

  function confirmarPago(evento) {
    evento.preventDefault()
    ejecutar('pago', async () => {
      const emitida = await confirmarPagoSolicitud(solicitud.idSolicitud, {
        predios: prediosSeleccionados,
        fechaDesde,
        fechaHasta,
      })
      setResultado(emitida)
      await refrescarSolicitudes(emitida.documentoContribuyente)
    })
  }

  function alternarPredio(codigoPredio) {
    setPrediosSeleccionados((actuales) => (actuales.includes(codigoPredio)
      ? actuales.filter((codigo) => codigo !== codigoPredio)
      : [...actuales, codigoPredio]))
  }

  function emitir(evento) {
    evento.preventDefault()
    ejecutar('emision', async () => {
      const emitida = await emitirSolicitud(solicitud.idSolicitud, {
        predios: prediosSeleccionados,
        fechaDesde,
        fechaHasta,
      })
      setResultado(emitida)
      await refrescarSolicitudes(emitida.documentoContribuyente)
    })
  }

  /* Cierra la vista del trámite sin tocar la solicitud: queda retomable desde la tabla. */
  function cerrarTramite() {
    const documentoActual = contribuyente?.numeroDocumento
    setSolicitud(null)
    setDocumentoFirma(null)
    setPrediosSeleccionados([])
    setFechaDesde('')
    setFechaHasta('')
    setError('')
    if (documentoActual) refrescarSolicitudes(documentoActual)
  }

  /* Retoma una solicitud abierta sin crear otra: el flujo sigue desde su estado real. */
  function continuarSolicitud(item) {
    setSolicitud(item)
    setDocumentoFirma(null)
    setPrediosSeleccionados([])
    setFechaDesde('')
    setFechaHasta('')
    setResultado(null)
    setError('')
  }

  function nuevaConsulta() {
    setDocumento('')
    setConsulta(null)
    setSolicitud(null)
    setDocumentoFirma(null)
    setPrediosSeleccionados([])
    setFechaDesde('')
    setFechaHasta('')
    setResultado(null)
    setSolicitudes([])
    setCargandoSolicitudes(false)
    setErrorSolicitudes('')
    setError('')
    setPaginaSolicitudes(1)
  }

  /* La condición de no adeudo se congeló al presentar la solicitud; solo falta el pago del trámite. */
  const rangoCompleto = (!fechaDesde && !fechaHasta) || (fechaDesde && fechaHasta && fechaHasta >= fechaDesde)

  return (
    <div className="constancia-ventanilla" aria-busy={ocupado !== null}>
      <form className="filters-separados" onSubmit={consultar} role="search">
        <div className="filters-header">
          <h3>Consultar contribuyente</h3>
          <p className="hint">
            Ingresa el DNI, RUC o código exacto del contribuyente. La constancia se registra a nombre de {trabajador}.
          </p>
        </div>
        <div className="filters-fields">
          <div className="fld">
            <label htmlFor="cv-documento">DNI, RUC o código del contribuyente</label>
            <div className="documento-combobox" onBlur={cerrarSiSale}>
              <input
                id="cv-documento"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                maxLength={MAX_DOCUMENTO}
                value={documento}
                placeholder="Ej. 41028765"
                role="combobox"
                aria-autocomplete="list"
                aria-expanded={sugerenciasAbiertas}
                aria-controls="cv-sugerencias"
                aria-activedescendant={sugerenciasAbiertas && sugerenciaActiva >= 0 && sugerencias[sugerenciaActiva]
                  ? `cv-sugerencia-${sugerencias[sugerenciaActiva].codigo}` : undefined}
                onFocus={abrirSugerencias}
                onKeyDown={manejarTecladoSugerencias}
                onChange={cambiarDocumento}
                disabled={ocupado !== null}
                required
              />
              {sugerenciasAbiertas && sugerencias.length > 0 && (
                <ul id="cv-sugerencias" className="documento-opciones" role="listbox" aria-label="Coincidencias de contribuyentes">
                  {sugerencias.map((coincidente, indice) => (
                    <li
                      id={`cv-sugerencia-${coincidente.codigo}`}
                      key={coincidente.codigo}
                      role="option"
                      aria-selected={indice === sugerenciaActiva}
                      className={indice === sugerenciaActiva ? 'activa' : ''}
                      onMouseDown={(evento) => evento.preventDefault()}
                      onMouseEnter={() => setSugerenciaActiva(indice)}
                      onClick={() => seleccionarSugerencia(coincidente)}
                    >
                      <strong>{coincidente.nombre}</strong>
                      <span className="hint">
                        {coincidente.tipoDoc} {coincidente.doc} · Código {coincidente.codigo}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            {(buscandoSugerencias || errorSugerencias) && (
              <div className="documento-mensajes">
                {buscandoSugerencias && <span className="hint" role="status">Buscando coincidencias…</span>}
                {errorSugerencias && <span className="campo-error" role="alert">{errorSugerencias}</span>}
              </div>
            )}
          </div>
          <div className="filters-actions">
            <button className="btn primary" type="submit" disabled={ocupado !== null || !documento}>
              {ocupado === 'consulta' ? 'Consultando…' : 'Consultar'}
            </button>
          </div>
        </div>
      </form>

      {error && <div className="alert" role="alert">{error}</div>}

      {consulta && (
        <section className="pcard" aria-label="Contribuyente consultado">
          <h2>Contribuyente</h2>
          <div className="pfields">
            <Campo etiqueta="Nombre o razón social" valor={contribuyente.nombreRazonSocial} />
            <Campo etiqueta="Documento" valor={`${contribuyente.tipoDocumento} ${contribuyente.numeroDocumento}`} />
            <Campo etiqueta="Código de contribuyente" valor={contribuyente.codigoContribuyente} />
          </div>
          <EstadoDeuda key={contribuyente.numeroDocumento} deuda={consulta.deuda} />
          {!solicitud && !resultado && !haySolicitudAbiertaVentanilla && (
            <div className="cv-acciones">
              <button className="btn primary" type="button" onClick={crearSolicitud} disabled={ocupado !== null}>
                {ocupado === 'solicitud' ? 'Emitiendo solicitud…' : 'Emitir solicitud'}
              </button>
              <p className="hint cv-nota">
                La solicitud queda a nombre del contribuyente y la deuda se evalúa al presentarla. Si registra deuda
                pendiente, quedará rechazada y se mostrará el detalle.
              </p>
            </div>
          )}
        </section>
      )}

      {consulta && (
        <section className="pcard" aria-label="Solicitudes del contribuyente">
          <h2>Solicitudes del contribuyente</h2>
          {cargandoSolicitudes ? (
            <p className="hint" role="status">Cargando solicitudes…</p>
          ) : errorSolicitudes ? (
            <div className="alert" role="alert">{errorSolicitudes}</div>
          ) : solicitudesVisibles.length === 0 ? (
            <p className="hint">El contribuyente no tiene solicitudes de constancia registradas.</p>
          ) : (
            <div className="tabla-scroll" role="region" aria-label="Solicitudes del contribuyente" tabIndex={0}>
              <table className="tabla">
                <thead>
                  <tr>
                    <th scope="col">N.º</th>
                    <th scope="col">Fecha</th>
                    <th scope="col">Canal</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Paso</th>
                    <th scope="col">Orden / constancia</th>
                    <th scope="col">Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {solicitudesPagina.map((item) => {
                    const abierta = !ESTADOS_CERRADOS.has(item.estado)
                    const enCurso = solicitud?.idSolicitud === item.idSolicitud
                    return (
                      <tr key={item.idSolicitud}>
                        <th scope="row">N.º {item.idSolicitud}</th>
                        <td>{fechaHora(item.fechaSolicitud)}</td>
                        <td>{item.canal === 'VENTANILLA' ? 'Ventanilla' : 'App'}</td>
                        <td>{ETIQUETA_ESTADO[item.estado] ?? item.estado}</td>
                        <td>
                          {PASO_ACTUAL[item.estado] == null
                            ? '—'
                            : `${Math.min(PASO_ACTUAL[item.estado] + 1, PASOS.length)}/${PASOS.length}`}
                        </td>
                        <td>
                          {item.numeroConstancia ? (
                            <span className="cv-constancia">
                              {item.numeroConstancia}{' '}
                              <a
                                className="btn btn-compacto"
                                href={urlConstanciaPdf(item.numeroConstancia)}
                                target="_blank"
                                rel="noopener noreferrer"
                                aria-label={`Abrir PDF de la constancia ${item.numeroConstancia}`}
                              >
                                Ver PDF
                              </a>
                            </span>
                          ) : item.ordenPago ? (
                            `${moneda(item.ordenPago.monto)} · ${item.ordenPago.comprobante || 'Pendiente de pago'}`
                          ) : '—'}
                        </td>
                        <td>
                          {abierta && item.canal === 'VENTANILLA' ? (
                            <button
                              className="btn btn-compacto"
                              type="button"
                              onClick={() => continuarSolicitud(item)}
                              disabled={ocupado !== null || enCurso}
                            >
                              {enCurso ? 'En curso' : 'Continuar'}
                            </button>
                          ) : abierta ? (
                            <span className="hint">Se completa desde la app</span>
                          ) : item.estado === 'RECHAZADA' ? (
                            <span className="hint cv-motivo" title={item.motivoRechazo || ''}>
                              {item.motivoRechazo || '—'}
                            </span>
                          ) : '—'}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
          {solicitudesVisibles.length > 0 && (
            <div className="cv-paginacion">
              <PaginacionTabla
                id="cv-solicitudes"
                totalRegistros={solicitudesVisibles.length}
                pagina={paginaSolicitudesActual}
                tamanio={tamanioSolicitudes}
                opciones={TAMANIOS_PAGINA}
                onCambiarPagina={setPaginaSolicitudes}
                onCambiarTamanio={(valor) => { setTamanioSolicitudes(valor); setPaginaSolicitudes(1) }}
              />
            </div>
          )}
          <p className="hint cv-nota">
            Se muestran las últimas solicitudes del contribuyente en ambos canales; una solicitud emitida incluye su
            constancia.
          </p>
        </section>
      )}

      {solicitud && !resultado && estadoSolicitud !== 'RECHAZADA' && (
        <section className="pcard" aria-label="Trámite de la constancia">
          <div className="cv-cabecera">
            <h2>Trámite de la constancia</h2>
            <button
              className="btn btn-compacto"
              type="button"
              onClick={cerrarTramite}
              disabled={ocupado !== null}
              title="Vuelve a la consulta del contribuyente; la solicitud queda retomable"
            >
              Cerrar trámite
            </button>
          </div>
          <Pasos estado={estadoSolicitud} />
          <div className="pfields">
            <Campo etiqueta="Solicitud" valor={`N.º ${solicitud.idSolicitud}`} />
            <Campo etiqueta="Estado" valor={ETIQUETA_ESTADO[estadoSolicitud] ?? estadoSolicitud} />
            <Campo etiqueta="Fecha de solicitud" valor={fechaHora(solicitud.fechaSolicitud)} />
          </div>

          {estadoSolicitud === 'INICIADA' && (
            <section className="cv-firma" aria-labelledby="cv-firma-titulo">
              <div className="cv-firma-cabecera">
                <div>
                  <h3 id="cv-firma-titulo">Firma del contribuyente</h3>
                  <p className="hint">
                    Revisa el documento con el contribuyente antes de registrar su firma. El servidor verifica la
                    firma contra esta versión.
                  </p>
                </div>
                <span className="cv-firma-paso" aria-label="Paso 2 de 5">Paso 2 de 5</span>
              </div>

              <div className="cv-firma-cuerpo">
                <section className="cv-firma-documento" aria-label="Documento de la solicitud">
                  <div className="cv-firma-documento-cabecera">
                    <div>
                      <strong>{documentoFirma?.nombreArchivo || 'Documento de la solicitud'}</strong>
                      <span className="hint">
                        {documentoFirma?.tipoContenido || 'Documento generado por el servidor'}
                      </span>
                    </div>
                    {documentoFirma && <span className="cv-firma-sello">Versión a firmar</span>}
                  </div>
                  {!documentoFirma ? (
                    <div className="cv-firma-vacio">
                      <p className="hint">
                        Prepara el documento para mostrarlo al contribuyente y capturar su firma.
                      </p>
                      <button className="btn primary" type="button" onClick={prepararDocumento} disabled={ocupado !== null}>
                        {ocupado === 'firma' ? 'Preparando documento…' : 'Preparar documento para firma'}
                      </button>
                    </div>
                  ) : (
                    <div className="cv-hoja" role="document" tabIndex={0} aria-label="Texto del documento a firmar">
                      <div className="cv-hoja-texto">{textoDocumento(documentoFirma.contenidoBase64)}</div>
                    </div>
                  )}
                </section>

                <aside className="cv-firma-resumen" aria-labelledby="cv-firma-resumen-titulo">
                  <h4 id="cv-firma-resumen-titulo">Resumen de la firma</h4>
                  <div className="pfields">
                    <Campo etiqueta="Contribuyente" valor={nombreFirma} />
                    <Campo etiqueta="Documento" valor={documentoFirmaTexto} />
                    <Campo etiqueta="Código" valor={codigoFirma} />
                    <Campo etiqueta="Solicitud" valor={`N.º ${solicitud.idSolicitud}`} />
                    <Campo etiqueta="Fecha" valor={fechaHora(solicitud.fechaSolicitud)} />
                    <Campo etiqueta="Canal" valor="Ventanilla" />
                  </div>
                  <p className="cv-firma-metodo">
                    <strong>Método:</strong>{' '}
                    {documentoFirma
                      ? (documentoFirma.firmaLocal
                        ? 'Firma electrónica de Servicios en Línea'
                        : 'Dispositivo de firma presencial')
                      : 'Se confirma al preparar el documento'}
                  </p>
                  <p className="hint cv-nota" role="note">
                    El contribuyente revisa el documento y firma en presencia del trabajador.
                  </p>
                  {documentoFirma && (
                    documentoFirma.firmaLocal ? (
                      <div className="cv-firma-acciones">
                        <button className="btn primary" type="button" onClick={registrarFirmaContribuyente} disabled={ocupado !== null}>
                          {ocupado === 'firma' ? 'Registrando firma…' : 'Registrar firma del contribuyente'}
                        </button>
                      </div>
                    ) : (
                      <p className="hint cv-nota" role="note">
                        Conecta el dispositivo de firma presencial para capturar la firma del contribuyente.
                      </p>
                    )
                  )}
                </aside>
              </div>
            </section>
          )}

          {solicitud.firmaContribuyente && ['FIRMADA', 'DEUDA_VERIFICADA', 'ORDEN_PAGO_EMITIDA', 'PAGADA'].includes(estadoSolicitud) && (
            <section
              className="cv-firma-comprobante"
              ref={comprobanteRef}
              tabIndex={-1}
              aria-live="polite"
              aria-labelledby="cv-firma-comprobante-titulo"
            >
              <div className="cv-firma-comprobante-cabecera">
                <h3 id="cv-firma-comprobante-titulo">Firma registrada</h3>
                <span className="estado ok">Firmada</span>
              </div>
              <p className="hint">La firma del contribuyente quedó registrada para esta solicitud.</p>
              <div className="pfields">
                <Campo etiqueta="Fecha de la firma" valor={fechaHora(solicitud.firmaContribuyente.fechaFirma)} />
                <Campo etiqueta="Método" valor={solicitud.firmaContribuyente.entidadCertificadora || 'Firma en ventanilla'} />
              </div>
              {estadoSolicitud === 'FIRMADA' && (
                <p className="hint">Siguiente paso: generar la orden de pago del derecho de trámite.</p>
              )}
            </section>
          )}

          {(estadoSolicitud === 'FIRMADA' || estadoSolicitud === 'DEUDA_VERIFICADA') && (
            <>
              <h3>Orden de pago</h3>
              <p className="hint">
                La deuda se evaluó al presentar la solicitud ({fechaHora(solicitud.fechaSolicitud)}). Se genera la
                orden de pago del derecho de trámite.
              </p>
              <div className="cv-acciones">
                <button className="btn primary" type="button" onClick={verificarDeuda} disabled={ocupado !== null}>
                  {ocupado === 'verificacion' ? 'Generando…' : 'Generar orden de pago'}
                </button>
              </div>
            </>
          )}

          {(estadoSolicitud === 'ORDEN_PAGO_EMITIDA' || estadoSolicitud === 'PAGADA') && (
            <form onSubmit={estadoSolicitud === 'ORDEN_PAGO_EMITIDA' ? confirmarPago : emitir}>
              <h3>{estadoSolicitud === 'PAGADA' ? 'Completar emisión pendiente' : 'Confirmar pago y emitir constancia'}</h3>
              <div className="pfields">
                <Campo etiqueta="Deuda al presentar" valor={solicitud.deuda ? moneda(solicitud.deuda.total) : '—'} />
                <Campo etiqueta="Derecho de trámite" valor={moneda(solicitud.ordenPago?.monto)} />
                <Campo etiqueta="Estado de la orden" valor={solicitud.ordenPago?.estado ?? 'Pendiente de pago'} />
                <Campo etiqueta="Comprobante" valor={solicitud.ordenPago?.comprobante} />
              </div>
              <p className="hint">
                Selecciona los predios que comprende la constancia. Si no seleccionas ninguno, se consideran todos los
                predios del contribuyente.
              </p>

              {predios.length > 0 ? (
                <div className="cv-predios" role="group" aria-label="Predios del contribuyente">
                  {predios.map((predio) => (
                    <label className="cv-predio" key={predio.codigoPredio}>
                      <input
                        type="checkbox"
                        checked={prediosSeleccionados.includes(predio.codigoPredio)}
                        onChange={() => alternarPredio(predio.codigoPredio)}
                        disabled={ocupado !== null}
                      />
                      <span>
                        <strong>{predio.codigoPredio}</strong>
                        <span className="hint">{predio.ubicacion || 'Ubicación no disponible'}</span>
                        <span className="hint">
                          {predio.tipoPredio || '—'} · {predio.zona || '—'} · Catastro {predio.numeroCatastral || '—'}
                        </span>
                      </span>
                    </label>
                  ))}
                </div>
              ) : (
                <p className="hint">El contribuyente no tiene predios registrados; la constancia comprende todos sus predios.</p>
              )}

              <div className="filters-fields">
                <div className="fld">
                  <label htmlFor="cv-fecha-desde">Fecha inicial (opcional)</label>
                  <input
                    id="cv-fecha-desde"
                    type="date"
                    value={fechaDesde}
                    max={fechaHasta || undefined}
                    onChange={(evento) => setFechaDesde(evento.target.value)}
                    disabled={ocupado !== null}
                  />
                </div>
                <div className="fld">
                  <label htmlFor="cv-fecha-hasta">Fecha final (opcional)</label>
                  <input
                    id="cv-fecha-hasta"
                    type="date"
                    value={fechaHasta}
                    min={fechaDesde || undefined}
                    onChange={(evento) => setFechaHasta(evento.target.value)}
                    disabled={ocupado !== null}
                  />
                </div>
              </div>
              {!rangoCompleto && <p className="campo-error" role="alert">Indica ambas fechas y un rango válido.</p>}
              <p className="hint">Si se omiten ambas fechas, la constancia indica «Hasta la fecha de emisión».</p>

              <div className="cv-acciones">
                <button
                  className="btn primary"
                  type="submit"
                  disabled={ocupado !== null || !rangoCompleto}
                >
                  {ocupado === 'pago' ? 'Confirmando y emitiendo…'
                    : ocupado === 'emision' ? 'Emitiendo…'
                      : estadoSolicitud === 'PAGADA' ? 'Emitir constancia pendiente' : 'Confirmar pago y emitir'}
                </button>
              </div>
              <p className="hint cv-nota">
                Al confirmar el pago, el sistema genera el comprobante, firma la constancia automáticamente y la deposita
                en el buzón del contribuyente.
              </p>
            </form>
          )}
        </section>
      )}

      {solicitud && estadoSolicitud === 'RECHAZADA' && (
        <section className="pcard" aria-label="Solicitud rechazada">
          <div className="alert" role="alert">
            <strong>Solicitud N.º {solicitud.idSolicitud} rechazada.</strong> {solicitud.motivoRechazo}
          </div>
          {solicitud.deuda && <EstadoDeuda deuda={solicitud.deuda} />}
          <div className="cv-acciones">
            <button className="btn" type="button" onClick={nuevaConsulta} disabled={ocupado !== null}>
              Atender a otro contribuyente
            </button>
          </div>
        </section>
      )}

      {resultado && (
        <section className="pcard" aria-label="Constancia emitida">
          <h2>Constancia emitida</h2>
          <div className="confirmacion" role="status">
            <strong>{resultado.numeroConstancia}</strong> emitida correctamente para {resultado.nombreContribuyente}.
          </div>
          <div className="pfields">
            <Campo etiqueta="Número de constancia" valor={resultado.numeroConstancia} />
            <Campo etiqueta="Solicitud" valor={`N.º ${resultado.idSolicitud}`} />
            <Campo etiqueta="Fecha de emisión" valor={fechaHora(resultado.fechaEmision)} />
            <Campo etiqueta="Contribuyente" valor={resultado.nombreContribuyente} />
            <Campo etiqueta="Documento" valor={resultado.documentoContribuyente} />
            <Campo etiqueta="Predios incluidos" valor={resultado.prediosIncluidos} />
            <Campo etiqueta="Vigencia" valor={resultado.rangoFechas} />
            <Campo etiqueta="Emitida por" valor={resultado.nombreTrabajadorEmisor || resultado.trabajadorEmisor} />
            <Campo etiqueta="Comprobante de pago" valor={resultado.comprobantePago} />
            <Campo etiqueta="Derecho de trámite" valor={moneda(resultado.montoDerechoTramite)} />
            <Campo etiqueta="Estado" valor={resultado.estado} />
          </div>
          <div className="cv-acciones">
            <a
              className="btn primary"
              href={urlConstanciaPdf(resultado.numeroConstancia)}
              target="_blank"
              rel="noopener noreferrer"
            >
              Abrir PDF para imprimir
            </a>
            <button className="btn" type="button" onClick={nuevaConsulta} disabled={ocupado !== null}>
              Atender a otro contribuyente
            </button>
          </div>
          <p className="hint cv-nota">La constancia ya fue depositada en el buzón de documentos del contribuyente.</p>
        </section>
      )}
    </div>
  )
}
