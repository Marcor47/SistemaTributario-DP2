import { useEffect, useMemo, useRef, useState } from 'react'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import { useSesionInterna } from '../context/sesionInterna.js'
import {
  MAX_DOCUMENTO, MAX_PREDIOS_TEXTO, MAX_RANGO_FECHAS, MINIMO_SUGERENCIAS,
  buscarCoincidenciasVentanilla, confirmarPagoSolicitud, consultarContribuyente,
  crearSolicitudVentanilla, emitirSolicitud, firmaDePrueba, listarConstancias, normalizarDocumento,
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

const PASOS = ['Solicitud', 'Firma', 'Deuda y orden', 'Pago', 'Constancia']

const PASO_ACTUAL = {
  INICIADA: 1,
  FIRMADA: 2,
  DEUDA_VERIFICADA: 2,
  ORDEN_PAGO_EMITIDA: 3,
  PAGADA: 4,
  EMITIDA: PASOS.length,
}

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
        <strong>Deuda pendiente: {moneda(deuda.total)}.</strong> No se puede emitir la constancia hasta que el
        contribuyente regularice sus pagos.
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
  const [prediosTexto, setPrediosTexto] = useState('')
  const [rangoFechas, setRangoFechas] = useState('')
  const [resultado, setResultado] = useState(null)
  const [historial, setHistorial] = useState([])
  const [filtroHistorial, setFiltroHistorial] = useState('')
  const [error, setError] = useState('')
  const [errorHistorial, setErrorHistorial] = useState('')
  const [ocupado, setOcupado] = useState(null)
  const [cargandoHistorial, setCargandoHistorial] = useState(true)
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

  useEffect(() => {
    let activo = true
    listarConstancias()
      .then((datos) => { if (activo) setHistorial(datos) })
      .catch((err) => { if (activo) setErrorHistorial(err.message || 'No se pudo cargar el historial de constancias.') })
      .finally(() => { if (activo) setCargandoHistorial(false) })
    return () => { activo = false }
  }, [])

  useEffect(() => {
    buscarSugerenciasRef.current = buscarSugerencias
  })

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
  const historialVentanilla = historial.filter((item) => item.canal === 'VENTANILLA')

  async function refrescarHistorial(filtro = '') {
    setCargandoHistorial(true)
    setErrorHistorial('')
    setFiltroHistorial(filtro)
    try {
      setHistorial(await listarConstancias(filtro))
    } catch (err) {
      setErrorHistorial(err.message || 'No se pudo cargar el historial de constancias.')
    } finally {
      setCargandoHistorial(false)
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
    setPrediosTexto('')
    setRangoFechas('')
    setOcupado('consulta')
    try {
      const datos = await consultarContribuyente(valor)
      setConsulta(datos)
      omitirSugerencias.current = true
      setDocumento(datos.contribuyente.numeroDocumento)
      await refrescarHistorial(datos.contribuyente.numeroDocumento)
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
      setPrediosTexto('')
      setRangoFechas('')
    })
  }

  function prepararDocumento() {
    ejecutar('firma', async () => {
      setDocumentoFirma(await obtenerDocumentoFirmaSolicitud(solicitud.idSolicitud))
    })
  }

  function firmarSimulado() {
    ejecutar('firma', async () => {
      const firmada = await registrarFirmaSolicitud(
        solicitud.idSolicitud,
        firmaDePrueba(documentoFirma.hashSha256),
        'CERTIFICADO DE PRUEBA',
      )
      setSolicitud(firmada)
      setDocumentoFirma(null)
    })
  }

  function verificarDeuda() {
    ejecutar('verificacion', async () => {
      setSolicitud(await verificarDeudaSolicitud(solicitud.idSolicitud))
    })
  }

  function confirmarPago() {
    ejecutar('pago', async () => {
      setSolicitud(await confirmarPagoSolicitud(solicitud.idSolicitud))
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
        prediosTexto,
        rangoFechas,
      })
      setResultado(emitida)
      await refrescarHistorial(emitida.documentoContribuyente)
    })
  }

  function nuevaConsulta() {
    setDocumento('')
    setConsulta(null)
    setSolicitud(null)
    setDocumentoFirma(null)
    setPrediosSeleccionados([])
    setPrediosTexto('')
    setRangoFechas('')
    setResultado(null)
    setError('')
    refrescarHistorial()
  }

  const puedeEmitir = Boolean(consulta?.deuda?.puedeSolicitar) && estadoSolicitud === 'PAGADA'

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
          {!solicitud && !resultado && (
            <div className="cv-acciones">
              <button className="btn primary" type="button" onClick={crearSolicitud} disabled={ocupado !== null}>
                {ocupado === 'solicitud' ? 'Emitiendo solicitud…' : 'Emitir solicitud'}
              </button>
              <p className="hint cv-nota">
                La solicitud queda a nombre del contribuyente y se retoma si ya tiene una abierta en ventanilla.
              </p>
            </div>
          )}
        </section>
      )}

      {solicitud && !resultado && estadoSolicitud !== 'RECHAZADA' && (
        <section className="pcard" aria-label="Trámite de la constancia">
          <Pasos estado={estadoSolicitud} />
          <div className="pfields">
            <Campo etiqueta="Solicitud" valor={`N.º ${solicitud.idSolicitud}`} />
            <Campo etiqueta="Estado" valor={ETIQUETA_ESTADO[estadoSolicitud] ?? estadoSolicitud} />
            <Campo etiqueta="Fecha de solicitud" valor={fechaHora(solicitud.fechaSolicitud)} />
          </div>

          {estadoSolicitud === 'INICIADA' && (
            <>
              <h3>Firma del contribuyente</h3>
              <p className="hint">
                La solicitud se muestra en el dispositivo de firma presencial. El servidor verifica la firma contra
                esta versión.
              </p>
              {!documentoFirma && (
                <div className="cv-acciones">
                  <button className="btn primary" type="button" onClick={prepararDocumento} disabled={ocupado !== null}>
                    {ocupado === 'firma' ? 'Preparando documento…' : 'Preparar documento'}
                  </button>
                </div>
              )}
              {documentoFirma && (
                <>
                  <pre className="cv-documento" aria-label="Documento de la solicitud">
                    {textoDocumento(documentoFirma.contenidoBase64)}
                  </pre>
                  <p className="hint">SHA-256: {documentoFirma.hashSha256}</p>
                  {documentoFirma.documentoDePrueba ? (
                    <>
                      <div className="cv-acciones">
                        <button className="btn primary" type="button" onClick={firmarSimulado} disabled={ocupado !== null}>
                          {ocupado === 'firma' ? 'Firmando…' : 'Firmar en dispositivo (simulado)'}
                        </button>
                      </div>
                      <p className="hint cv-nota" role="note">
                        Firma simulada sin validez legal. La integración con el dispositivo presencial queda pendiente.
                      </p>
                    </>
                  ) : (
                    <p className="hint cv-nota" role="note">
                      Conecta el dispositivo de firma presencial para capturar la firma del contribuyente.
                    </p>
                  )}
                </>
              )}
            </>
          )}

          {(estadoSolicitud === 'FIRMADA' || estadoSolicitud === 'DEUDA_VERIFICADA') && (
            <>
              <h3>Verificación de deuda</h3>
              <p className="hint">
                Firma registrada. Se consulta la deuda en SAMNET; con deuda 0 se genera la orden de pago del derecho
                de trámite.
              </p>
              <div className="cv-acciones">
                <button className="btn primary" type="button" onClick={verificarDeuda} disabled={ocupado !== null}>
                  {ocupado === 'verificacion' ? 'Verificando…' : 'Verificar deuda y generar orden'}
                </button>
              </div>
            </>
          )}

          {(estadoSolicitud === 'ORDEN_PAGO_EMITIDA' || estadoSolicitud === 'DEUDA_VERIFICADA') && (
            <>
              <h3>Orden de pago</h3>
              <div className="pfields">
                <Campo etiqueta="Derecho de trámite" valor={moneda(solicitud.ordenPago?.monto)} />
                <Campo etiqueta="Estado de la orden" valor={solicitud.ordenPago?.estado ?? 'Pendiente de pago'} />
                <Campo etiqueta="Comprobante" valor={solicitud.ordenPago?.comprobante} />
              </div>
              {estadoSolicitud === 'ORDEN_PAGO_EMITIDA' && (
                <>
                  <div className="cv-acciones">
                    <button className="btn primary" type="button" onClick={confirmarPago} disabled={ocupado !== null}>
                      {ocupado === 'pago' ? 'Confirmando…' : 'Confirmar pago recibido'}
                    </button>
                  </div>
                  <p className="hint cv-nota">
                    Confirma el cobro presencial del derecho de trámite. El comprobante se genera automáticamente.
                  </p>
                </>
              )}
            </>
          )}

          {estadoSolicitud === 'PAGADA' && (
            <form onSubmit={emitir}>
              <h3>Emitir constancia</h3>
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
                  <label htmlFor="cv-predios-texto">Descripción libre de predios (opcional)</label>
                  <input
                    id="cv-predios-texto"
                    type="text"
                    maxLength={MAX_PREDIOS_TEXTO}
                    value={prediosTexto}
                    onChange={(evento) => setPrediosTexto(evento.target.value)}
                    placeholder="Solo si los predios no figuran en la lista"
                    disabled={ocupado !== null || prediosSeleccionados.length > 0}
                  />
                  <span className="hint">Se usa solo si no seleccionas predios de la lista.</span>
                </div>
                <div className="fld">
                  <label htmlFor="cv-rango-fechas">Rango de fechas o vigencia (opcional)</label>
                  <input
                    id="cv-rango-fechas"
                    type="text"
                    maxLength={MAX_RANGO_FECHAS}
                    value={rangoFechas}
                    onChange={(evento) => setRangoFechas(evento.target.value)}
                    placeholder="Periodo 2024 - 2026"
                    disabled={ocupado !== null}
                  />
                  <span className="hint">Si se omite, la constancia indica «Hasta la fecha de emisión».</span>
                </div>
              </div>

              <div className="cv-acciones">
                <button
                  className="btn primary"
                  type="submit"
                  disabled={ocupado !== null || !puedeEmitir}
                  title={puedeEmitir ? undefined : 'Vuelve a consultar la deuda del contribuyente antes de emitir.'}
                >
                  {ocupado === 'emision' ? 'Emitiendo…' : 'Emitir constancia'}
                </button>
              </div>
              <p className="hint cv-nota">
                La firma municipal se incorpora automáticamente y la constancia se deposita en el buzón del contribuyente.
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
          {resultado.esDePrueba && (
            <div className="alert" role="note">
              Documento generado en ambiente de pruebas; la firma municipal es simulada.
            </div>
          )}
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

      <section className="pcard" aria-label="Constancias emitidas en ventanilla">
        <h2>Constancias emitidas</h2>
        {cargandoHistorial ? (
          <p className="hint" role="status">Cargando constancias…</p>
        ) : errorHistorial ? (
          <div className="alert" role="alert">{errorHistorial}</div>
        ) : historialVentanilla.length === 0 ? (
          <p className="hint">No hay constancias emitidas en ventanilla para mostrar.</p>
        ) : (
          <div className="tabla-scroll" role="region" aria-label="Historial de constancias emitidas" tabIndex={0}>
            <table className="tabla">
              <thead>
                <tr>
                  <th scope="col">N.º constancia</th>
                  <th scope="col">Fecha</th>
                  <th scope="col">Contribuyente</th>
                  <th scope="col">Documento</th>
                  <th scope="col">Emitida por</th>
                  <th scope="col">PDF</th>
                </tr>
              </thead>
              <tbody>
                {historialVentanilla.map((item) => (
                  <tr key={item.numeroConstancia}>
                    <th scope="row">{item.numeroConstancia}</th>
                    <td>{fechaHora(item.fechaEmision)}</td>
                    <td>{item.nombreContribuyente || '—'}</td>
                    <td>{item.documentoContribuyente}</td>
                    <td>{item.nombreTrabajadorEmisor || item.trabajadorEmisor || '—'}</td>
                    <td>
                      <a
                        className="btn btn-compacto"
                        href={urlConstanciaPdf(item.numeroConstancia)}
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label={`Abrir PDF de la constancia ${item.numeroConstancia}`}
                      >
                        Ver PDF
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint cv-nota">
          {filtroHistorial
            ? `Constancias emitidas en ventanilla para el documento ${filtroHistorial}.`
            : 'Se muestran las últimas constancias emitidas en ventanilla.'}
        </p>
      </section>
    </div>
  )
}
