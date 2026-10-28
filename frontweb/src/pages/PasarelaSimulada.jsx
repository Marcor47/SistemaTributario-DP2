import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom'
import { confirmarPagoYRecuperarResultado, consultarPagoPortal } from '../services/portalCuentaService.js'
import { mensajeErrorPago } from '../utils/pagosPortal.js'
import { moneda } from '../utils/formato.js'
import ModalPagoConfirmado from '../components/ModalPagoConfirmado.jsx'
import './PagosPortal.css'

function ImportesPago({ pago }) {
  return (
    <dl className="pago-importes">
      <div><dt>Saldo antes de beneficios</dt><dd>{moneda(pago.montoBase)}</dd></div>
      <div><dt>Descuento aplicado</dt><dd>{moneda(pago.montoDescuento)}</dd></div>
      <div className="pago-importe-total"><dt>{pago.estado === 'CONFIRMADA' ? 'Total pagado' : 'Total a pagar'}</dt><dd>{moneda(pago.montoTotal)}</dd></div>
    </dl>
  )
}

function FlujoPago({ idTransaccion }) {
  const ubicacion = useLocation()
  const [resultado, setResultado] = useState({ cargando: true, pago: null, error: null })
  const [accion, setAccion] = useState({ ocupado: false, error: null })
  const [revision, setRevision] = useState(0)
  const [mostrarResumen, setMostrarResumen] = useState(false)
  const enCurso = useRef(false)
  const activo = useRef(true)

  useEffect(() => {
    activo.current = true
    const controlador = new AbortController()
    consultarPagoPortal(idTransaccion, { signal: controlador.signal })
      .then((pago) => { if (!controlador.signal.aborted) setResultado({ cargando: false, pago, error: null }) })
      .catch((error) => { if (!controlador.signal.aborted) setResultado({ cargando: false, pago: null, error }) })
    return () => { activo.current = false; controlador.abort() }
  }, [idTransaccion, revision])

  function recargar() {
    setResultado({ cargando: true, pago: null, error: null })
    setAccion({ ocupado: false, error: null })
    setRevision((valor) => valor + 1)
  }

  async function confirmar() {
    if (enCurso.current || resultado.pago?.estado !== 'INICIADA' || resultado.pago?.dePrueba !== true) return
    enCurso.current = true
    setAccion({ ocupado: true, error: null })
    try {
      const recuperado = await confirmarPagoYRecuperarResultado(idTransaccion)
      if (activo.current) {
        setResultado({ cargando: false, pago: recuperado.pago, error: null })
        setAccion({ ocupado: false, error: recuperado.error })
        if (recuperado.pago.estado === 'CONFIRMADA') setMostrarResumen(true)
      }
    } catch (error) {
      if (activo.current) {
        if (error.estado === 401) setResultado({ cargando: false, pago: null, error })
        setAccion({ ocupado: false, error })
      }
    } finally {
      enCurso.current = false
    }
  }

  const { pago, cargando } = resultado
  const error = resultado.error || accion.error
  const aviso = error ? mensajeErrorPago(error) : null
  const confirmado = pago?.estado === 'CONFIRMADA'
  const terminal = pago && ['RECHAZADA', 'EXPIRADA'].includes(pago.estado)
  const bloqueado = aviso && aviso.accion !== 'reintentar'
  const retornoLogin = `${ubicacion.pathname}${ubicacion.search}`

  return (
    <main className="pasarela-pagina">
      <header className="pasarela-marca">
        <Link to="/estado-cuenta" aria-label="Volver a mi estado de cuenta">
          <img src="/logo-san-miguel-circulo.png" width="44" height="44" alt="" />
          <span>Municipalidad de San Miguel<strong>Servicios en Línea</strong></span>
        </Link>
        <span className="estado aviso">Simulación</span>
      </header>
      <section className="pcard pasarela-ficha" aria-labelledby="pasarela-titulo" aria-busy={cargando || accion.ocupado}>
        <h1 id="pasarela-titulo">{confirmado ? 'Comprobante de pago' : 'Pago en línea'}</h1>
        <p className="hint pasarela-nota">{confirmado ? 'Consulta el detalle de tu operación.' : 'Revisa el importe total antes de confirmar.'}</p>
        {cargando && <p className="mensaje-carga" role="status">Consultando la orden de pago…</p>}
        {aviso && (
          <div className="alert pago-error" role="alert">
            <strong>{aviso.titulo}</strong><p>{aviso.texto}</p>
            {aviso.accion === 'sesion' && <Link className="btn" to="/login" state={{ from: retornoLogin, motivo: 'servicios-en-linea' }}>Iniciar sesión</Link>}
            {!accion.ocupado && (aviso.accion === 'consultar' || aviso.accion === 'reintentar') && (
              <button className="btn" type="button" onClick={recargar}>Consultar resultado de esta operación</button>
            )}
          </div>
        )}
        {!cargando && pago && (
          <>
            <div className="pago-referencia">
              <span>Operación #{pago.idTransaccion}</span>
              <span className={`estado ${confirmado ? 'ok' : 'aviso'}`}>
                {confirmado ? 'Confirmada' : terminal ? (pago.estado === 'RECHAZADA' ? 'Rechazada' : 'Expirada') : 'Pendiente de confirmación'}
              </span>
            </div>
            <ImportesPago pago={pago} />
            {confirmado ? (
              <div className="confirmacion" role="status">
                <strong>Pago realizado</strong>
                <p>Comprobante: <strong>{pago.comprobante}</strong></p>
                <button className="btn" type="button" onClick={() => setMostrarResumen(true)}>Ver resumen del pago</button>
              </div>
            ) : pago.dePrueba !== true ? (
              <div className="alert" role="status">Esta pantalla solo permite operaciones simuladas. No se enviará una confirmación para esta orden.</div>
            ) : terminal ? (
              <p className="alert" role="status">Esta orden está cerrada y no permite continuar. Revisa tu estado de cuenta antes de iniciar otro pago.</p>
            ) : (
              <>
                <p>Se pagará el saldo completo de <strong>{pago.obligaciones.length}</strong> obligaciones.{pago.montoDescuento > 0 && ' Se incluyen los beneficios verificados por la Municipalidad.'}</p>
                {!bloqueado && (
                  <button className="btn primary pago-confirmar" type="button" disabled={accion.ocupado} onClick={confirmar}>
                    {accion.ocupado ? 'Comprobando el pago…' : 'Confirmar pago'}
                  </button>
                )}
                {accion.ocupado && <p className="hint" role="status">Espera mientras se verifica el resultado. No inicies otra operación.</p>}
              </>
            )}
          </>
        )}
        {!accion.ocupado && (
          <div className="pago-volver">
            <Link className={`btn${confirmado ? ' primary' : ''}`} to={confirmado ? `/estado-cuenta?pago=${pago.idTransaccion}` : '/estado-cuenta'}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M19 12H5m7-7-7 7 7 7" /></svg>
              Volver a mi estado de cuenta
            </Link>
          </div>
        )}
      </section>
      {confirmado && <ModalPagoConfirmado pago={pago} abierto={mostrarResumen} onCerrar={() => setMostrarResumen(false)} />}
    </main>
  )
}

export default function PasarelaSimulada() {
  const parametros = useParams()
  const [consulta] = useSearchParams()
  const idTransaccion = parametros.id || consulta.get('transaccion')
  return <FlujoPago key={`${parametros.idOrden || ''}:${idTransaccion}`} idTransaccion={idTransaccion} />
}
