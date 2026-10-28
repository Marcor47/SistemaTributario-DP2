import { useEffect, useRef, useState } from 'react'
import { listarObligacionesVencidas, obtenerEtapaCobranza } from '../services/cobranzaService.js'
import { fecha } from '../utils/formato.js'
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
  const solicitudListado = useRef(0)
  const solicitudDetalle = useRef(0)

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
    setOcupado(true)
    setError('')
    setObligaciones(null)
    setSeleccionada(null)
    setDetalle(null)
    setErrorDetalle('')
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

  async function verEtapa(obligacion) {
    const actual = ++solicitudDetalle.current
    setSeleccionada(obligacion)
    setDetalle(null)
    setErrorDetalle('')
    setOcupadoDetalle(true)
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

  function volverAlListado() {
    solicitudDetalle.current++
    setSeleccionada(null)
    setDetalle(null)
    setErrorDetalle('')
    setOcupadoDetalle(false)
  }

  if (seleccionada) {
    return (
      <>
        <button className="btn volver" type="button" onClick={volverAlListado}>← Volver a obligaciones</button>
        {ocupadoDetalle && <p className="hint mensaje-carga" role="status">Consultando etapa de cobranza…</p>}
        {errorDetalle && <div className="alert" role="alert">{errorDetalle}</div>}
        {!ocupadoDetalle && !errorDetalle && detalle && <DetalleCobranza detalle={detalle} />}
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
      <p className="hint">Elige una obligación para consultar sus etapas de cobranza.</p>
      <div className="tabla-scroll" role="region" aria-label="Obligaciones vencidas del contribuyente" tabIndex={0}>
        <table className="tabla">
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
            {obligaciones.map((obligacion) => (
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
    </section>
  )
}
