import { useEffect, useMemo, useRef, useState } from 'react'
import { listarObligacionesVencidas, obtenerEtapaCobranza } from '../services/cobranzaService.js'
import { obtenerRecordatorioObligacion } from '../services/recordatorioService.js'
import { fecha } from '../utils/formato.js'
import { FILTROS_COBRANZA_INICIALES, filtrarObligaciones, opcionesDeCobranza } from '../utils/cobranza.js'
import { TAMANIOS_PAGINA, paginar } from '../utils/paginacion.js'
import DetalleCobranza from './DetalleCobranza.jsx'
import EtiquetaDeudaVencida from './EtiquetaDeudaVencida.jsx'
import './Cobranza.css'

const CLASE_ETAPA = {
  MORA_TEMPRANA: 'neutro',
  CAMPANA: 'aviso',
  VALOR_EMITIDO: 'aviso',
  ESPERA_LEGAL: 'aviso',
  COACTIVA: 'critico',
}

function Etapa({ etapa, nombre }) {
  return <span className={`estado ${CLASE_ETAPA[etapa] || 'neutro'}`}>{nombre || etapa}</span>
}

export default function CobranzaPanel({ contribuyente }) {
  const [obligaciones, setObligaciones] = useState(null)
  const [ocupado, setOcupado] = useState(true)
  const [error, setError] = useState('')
  const [seleccionada, setSeleccionada] = useState(null)
  const [detalle, setDetalle] = useState(null)
  const [ocupadoDetalle, setOcupadoDetalle] = useState(false)
  const [errorDetalle, setErrorDetalle] = useState('')
  const [recordatorio, setRecordatorio] = useState(null)
  const [ocupadoRecordatorio, setOcupadoRecordatorio] = useState(false)
  const [errorRecordatorio, setErrorRecordatorio] = useState('')
  const [filtros, setFiltros] = useState(FILTROS_COBRANZA_INICIALES)
  const [porPagina, setPorPagina] = useState(TAMANIOS_PAGINA[0])
  const [pagina, setPagina] = useState(1)
  const solicitudListado = useRef(0)
  const solicitudDetalle = useRef(0)

  const opciones = useMemo(() => opcionesDeCobranza(obligaciones ?? []), [obligaciones])
  const filtradas = useMemo(() => filtrarObligaciones(obligaciones ?? [], filtros), [obligaciones, filtros])
  const { visibles, paginaActual, totalPaginas, inicio, fin } = paginar(filtradas, pagina, porPagina)
  const hayFiltros = Object.values(filtros).some(Boolean)

  useEffect(() => {
    let activo = true
    const actual = ++solicitudListado.current
    listarObligacionesVencidas(contribuyente.codigo)
      .then((resultado) => {
        if (activo && actual === solicitudListado.current) setObligaciones(resultado)
      })
      .catch((err) => {
        if (activo && actual === solicitudListado.current) {
          setError(err.message || 'No se pudo consultar la cobranza. Intenta nuevamente.')
        }
      })
      .finally(() => {
        if (activo && actual === solicitudListado.current) setOcupado(false)
      })
    return () => { activo = false }
  }, [contribuyente.codigo])

  async function cargarListado() {
    const actual = ++solicitudListado.current
    solicitudDetalle.current++
    setOcupado(true)
    setError('')
    setObligaciones(null)
    setSeleccionada(null)
    setDetalle(null)
    setErrorDetalle('')
    setRecordatorio(null)
    setErrorRecordatorio('')
    setOcupadoRecordatorio(false)
    setFiltros(FILTROS_COBRANZA_INICIALES)
    setPagina(1)
    try {
      const resultado = await listarObligacionesVencidas(contribuyente.codigo)
      if (actual === solicitudListado.current) setObligaciones(resultado)
    } catch (err) {
      if (actual === solicitudListado.current) {
        setError(err.message || 'No se pudo consultar la cobranza. Intenta nuevamente.')
      }
    } finally {
      if (actual === solicitudListado.current) setOcupado(false)
    }
  }

  async function cargarRecordatorio(obligacion, actual) {
    setRecordatorio(null)
    setErrorRecordatorio('')
    setOcupadoRecordatorio(true)
    try {
      const resultado = await obtenerRecordatorioObligacion({
        id: obligacion.id,
        fechaVencimiento: obligacion.fechaVencimiento,
        contribuyenteCodigo: contribuyente.codigo,
      })
      if (actual === solicitudDetalle.current) setRecordatorio(resultado)
    } catch (err) {
      if (actual === solicitudDetalle.current) {
        setErrorRecordatorio(err.message || 'No se pudo consultar el recordatorio de vencimiento. Intenta nuevamente.')
      }
    } finally {
      if (actual === solicitudDetalle.current) setOcupadoRecordatorio(false)
    }
  }

  async function verEtapa(obligacion) {
    const actual = ++solicitudDetalle.current
    setSeleccionada(obligacion)
    setDetalle(null)
    setErrorDetalle('')
    setOcupadoDetalle(true)
    cargarRecordatorio(obligacion, actual)
    try {
      const resultado = await obtenerEtapaCobranza(obligacion.id)
      if (actual === solicitudDetalle.current) setDetalle(resultado)
    } catch (err) {
      if (actual === solicitudDetalle.current) {
        setErrorDetalle(err.message || 'No se pudo consultar la etapa de cobranza. Intenta nuevamente.')
      }
    } finally {
      if (actual === solicitudDetalle.current) setOcupadoDetalle(false)
    }
  }

  function reintentarRecordatorio() {
    if (!seleccionada) return
    cargarRecordatorio(seleccionada, solicitudDetalle.current)
  }

  function volverAlListado() {
    solicitudDetalle.current++
    setSeleccionada(null)
    setDetalle(null)
    setErrorDetalle('')
    setOcupadoDetalle(false)
    setRecordatorio(null)
    setErrorRecordatorio('')
    setOcupadoRecordatorio(false)
  }

  function cambiarFiltro(evento) {
    const { name, value } = evento.target
    setFiltros((actuales) => ({ ...actuales, [name]: value }))
    setPagina(1)
  }

  function limpiarFiltros() {
    setFiltros(FILTROS_COBRANZA_INICIALES)
    setPagina(1)
  }

  function cambiarPorPagina(evento) {
    setPorPagina(Number(evento.target.value))
    setPagina(1)
  }

  function cambiarPagina(nueva) {
    setPagina(nueva)
  }

  if (seleccionada) {
    return (
      <>
        <button className="btn volver" type="button" onClick={volverAlListado}>← Volver a obligaciones</button>
        {ocupadoDetalle && <p className="hint mensaje-carga" role="status">Consultando etapa de cobranza…</p>}
        {errorDetalle && <div className="alert" role="alert">{errorDetalle}</div>}
        {!ocupadoDetalle && !errorDetalle && detalle && (
          <DetalleCobranza
            detalle={detalle}
            recordatorio={recordatorio}
            ocupadoRecordatorio={ocupadoRecordatorio}
            errorRecordatorio={errorRecordatorio}
            onReintentarRecordatorio={reintentarRecordatorio}
          />
        )}
      </>
    )
  }

  if (ocupado) {
    return <p className="hint mensaje-carga" role="status">Consultando obligaciones vencidas…</p>
  }

  if (error) {
    return (
      <div className="alert" role="alert">
        <p>{error}</p>
        <button className="btn" type="button" onClick={cargarListado}>Reintentar cobranza</button>
      </div>
    )
  }

  if (!obligaciones || obligaciones.length === 0) {
    return (
      <div className="info" role="status">
        El contribuyente no tiene obligaciones vencidas registradas. Se encuentra al día.
      </div>
    )
  }

  return (
    <section className="pcard" aria-label="Obligaciones vencidas">
      <h2>Obligaciones vencidas</h2>
      <p className="hint">Filtra las obligaciones y elige una para consultar sus etapas de cobranza.</p>

      <div className="cobranza-filtros" role="group" aria-label="Filtros y paginación de obligaciones vencidas">
        <div className="fld">
          <label htmlFor="cobranza-periodo">Periodo</label>
          <select id="cobranza-periodo" name="periodo" value={filtros.periodo} onChange={cambiarFiltro}>
            <option value="">Todos los periodos</option>
            {opciones.periodos.map((periodo) => (
              <option key={periodo} value={periodo}>{periodo}</option>
            ))}
          </select>
        </div>
        <div className="fld">
          <label htmlFor="cobranza-etapa">Etapa</label>
          <select id="cobranza-etapa" name="etapa" value={filtros.etapa} onChange={cambiarFiltro}>
            <option value="">Todas las etapas</option>
            {opciones.etapas.map((etapa) => (
              <option key={etapa.valor} value={etapa.valor}>{etapa.etiqueta}</option>
            ))}
          </select>
        </div>
        <div className="fld">
          <label htmlFor="cobranza-tributo">Tributo</label>
          <select id="cobranza-tributo" name="tributo" value={filtros.tributo} onChange={cambiarFiltro}>
            <option value="">Todos los tributos</option>
            {opciones.tributos.map((tributo) => (
              <option key={tributo} value={tributo}>{tributo}</option>
            ))}
          </select>
        </div>
        <div className="fld cobranza-tamano">
          <label htmlFor="cobranza-por-pagina">Filas por página</label>
          <select id="cobranza-por-pagina" value={porPagina} onChange={cambiarPorPagina}>
            {TAMANIOS_PAGINA.map((tamano) => (
              <option key={tamano} value={tamano}>{tamano}</option>
            ))}
          </select>
        </div>
        {hayFiltros && (
          <button className="btn cobranza-limpiar" type="button" onClick={limpiarFiltros}>
            Limpiar filtros
          </button>
        )}
      </div>

      {filtradas.length === 0 ? (
        <p className="cobranza-vacio" role="status">
          No hay obligaciones que coincidan con los filtros seleccionados.
        </p>
      ) : (
        <>
          <div className="tabla-scroll" role="region" aria-label="Obligaciones vencidas del contribuyente" tabIndex={0}>
            <table className="tabla">
              <caption className="sr-only">Obligaciones vencidas del contribuyente</caption>
              <thead>
                <tr>
                  <th scope="col">Tributo</th>
                  <th scope="col">Periodo</th>
                  <th scope="col">Predio</th>
                  <th scope="col">Vencimiento</th>
                  <th scope="col">Deuda vencida</th>
                  <th scope="col">Etapa</th>
                  <th scope="col"><span className="sr-only">Acción</span></th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((obligacion) => (
                  <tr key={obligacion.id}>
                    <td>{obligacion.tributoDescripcion}</td>
                    <td>{obligacion.periodo}</td>
                    <td>{obligacion.codigoPredio ?? '—'}</td>
                    <td>{fecha(obligacion.fechaVencimiento)}</td>
                    <td><EtiquetaDeudaVencida saldo={obligacion.saldo} diasVencida={obligacion.diasVencida} /></td>
                    <td><Etapa etapa={obligacion.etapaActual} nombre={obligacion.etapaNombre} /></td>
                    <td>
                      <button className="btn btn-compacto" type="button" onClick={() => verEtapa(obligacion)}>
                        Ver etapa
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="cobranza-paginacion">
            <p className="cobranza-conteo" role="status">
              Mostrando {inicio + 1}–{fin} de {filtradas.length} {filtradas.length === 1 ? 'obligación' : 'obligaciones'}
            </p>
            <div className="cobranza-paginacion__controles">
              <button
                className="btn"
                type="button"
                onClick={() => cambiarPagina(paginaActual - 1)}
                disabled={paginaActual <= 1}
              >
                Anterior
              </button>
              <span className="cobranza-pagina">Página {paginaActual} de {totalPaginas}</span>
              <button
                className="btn"
                type="button"
                onClick={() => cambiarPagina(paginaActual + 1)}
                disabled={paginaActual >= totalPaginas}
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}
    </section>
  )
}
