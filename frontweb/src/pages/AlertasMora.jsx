import { useEffect, useRef, useState } from 'react'
import ModalConfirmacion from '../components/ModalConfirmacion.jsx'
import { useSesionInterna } from '../context/sesionInterna.js'
import {
  listarAvisosContribuyente, listarAvisosPorLote, procesarAlertasMora,
} from '../services/alertaMoraService.js'
import {
  cifrasProcesamiento, estadoAviso, ordenarAvisos, puedeProcesarAlertas, tipoAviso,
  validarContribuyente, validarIdLote,
} from '../utils/alertasMora.js'
import { fecha, fechaHora } from '../utils/formato.js'
import './ConsultasTributarias.css'

// HU012 (RF-013): alertas de mora temprana por correo. Procesar un lote publicado envía los avisos;
// el historial se consulta por lote o por contribuyente.

function TablaAvisos({ avisos, titulo }) {
  return (
    <div className="tabla-scroll" tabIndex={0} role="region" aria-label={titulo}>
      <table className="tabla">
        <caption className="sr-only">{titulo}</caption>
        <thead>
          <tr>
            <th scope="col">Enviado</th>
            <th scope="col">Contribuyente</th>
            <th scope="col">Correo</th>
            <th scope="col">Tipo</th>
            <th scope="col">Estado</th>
            <th scope="col">Lote</th>
            <th scope="col">Obligación</th>
          </tr>
        </thead>
        <tbody>
          {ordenarAvisos(avisos).map((aviso) => {
            const estado = estadoAviso(aviso.estado)
            return (
              <tr key={aviso.idAviso}>
                <td>{aviso.fechaEnvio ? fechaHora(aviso.fechaEnvio) : '—'}</td>
                <th scope="row">{aviso.documentoContribuyente}</th>
                <td>{aviso.correoDestino || '—'}</td>
                <td>{tipoAviso(aviso.tipo)}</td>
                <td><span className={`estado ${estado.clase}`}>{estado.texto}</span></td>
                <td>{aviso.idLote ?? '—'}</td>
                <td>{aviso.idObligacion ?? '—'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function ProcesarLote() {
  const [texto, setTexto] = useState('')
  const [errorCampo, setErrorCampo] = useState('')
  const [confirmando, setConfirmando] = useState(null)
  const [estado, setEstado] = useState({ procesando: false, resultado: null, error: null })

  function pedirConfirmacion(evento) {
    evento.preventDefault()
    const idLote = validarIdLote(texto)
    if (!idLote.valido) {
      setErrorCampo(idLote.mensaje)
      return
    }
    setErrorCampo('')
    setConfirmando(idLote.valor)
  }

  async function procesar() {
    const idLote = confirmando
    setEstado({ procesando: true, resultado: null, error: null })
    try {
      const resultado = await procesarAlertasMora(idLote)
      setEstado({ procesando: false, resultado, error: null })
    } catch (error) {
      setEstado({ procesando: false, resultado: null, error })
    }
    setConfirmando(null)
  }

  const { procesando, resultado, error } = estado
  return (
    <section className="pcard" aria-labelledby="alertas-procesar-titulo" aria-busy={procesando}>
      <h2 id="alertas-procesar-titulo">Enviar alertas de un lote</h2>
      <p className="hint">
        Evalúa un lote publicado del ETL y avisa por correo a los contribuyentes con cuotas vencidas sin pago.
        Solo se envía por correo electrónico: quienes no tienen correo registrado quedan excluidos.
      </p>
      <form className="filters consulta-filtros" onSubmit={pedirConfirmacion} aria-label="Procesar alertas de un lote" noValidate>
        <div className="fld">
          <label htmlFor="alertas-lote">Número de lote</label>
          <input
            id="alertas-lote" type="text" inputMode="numeric" autoComplete="off" value={texto}
            aria-invalid={errorCampo ? 'true' : undefined} aria-describedby={errorCampo ? 'alertas-lote-error' : undefined}
            onChange={(e) => { setTexto(e.target.value); setErrorCampo('') }}
          />
          {errorCampo && <span id="alertas-lote-error" className="campo-error" role="alert">{errorCampo}</span>}
        </div>
        <button className="btn primary" type="submit" disabled={procesando}>
          {procesando ? 'Procesando…' : 'Procesar alertas'}
        </button>
      </form>

      {error && (
        <div className="alert" role="alert">
          <strong>No se pudieron procesar las alertas.</strong>
          <p>{error.message}</p>
        </div>
      )}

      {resultado && (
        <div role="status">
          <p className="hint">
            Lote #{resultado.idLote} · fecha de corte {fecha(resultado.fechaCorte)}
          </p>
          <section className="consulta-indicadores" aria-label="Resultado del procesamiento">
            {cifrasProcesamiento(resultado).map((cifra) => (
              <div key={cifra.clave} className="pcard consulta-indicador">
                <span className="pl">{cifra.etiqueta}</span>
                <strong className={`consulta-cifra secundaria${cifra.tono === 'critico' ? ' deuda' : ''}`}>{cifra.valor}</strong>
              </div>
            ))}
          </section>
          {resultado.avisos?.length > 0
            ? <TablaAvisos avisos={resultado.avisos} titulo="Avisos generados en este procesamiento" />
            : <p className="consulta-vacio">Este procesamiento no generó avisos nuevos. Si ya se había procesado el lote, los avisos anteriores no se repiten.</p>}
        </div>
      )}

      <ModalConfirmacion
        abierto={confirmando !== null}
        titulo="¿Enviar las alertas de mora?"
        mensaje={<p>Se enviarán avisos por correo a los contribuyentes con cuotas vencidas sin pago del lote #{confirmando}. Los avisos ya enviados para ese lote no se repiten.</p>}
        textoConfirmar="Enviar alertas"
        procesando={procesando}
        onConfirmar={procesar}
        onCerrar={() => setConfirmando(null)}
      />
    </section>
  )
}

function Historial() {
  const [modo, setModo] = useState('lote')
  const [texto, setTexto] = useState('')
  const [errorCampo, setErrorCampo] = useState('')
  const [estado, setEstado] = useState({ cargando: false, avisos: null, error: null, consulta: null })
  const controlador = useRef(null)

  useEffect(() => () => controlador.current?.abort(), [])

  function consultar(evento) {
    evento?.preventDefault()
    const validacion = modo === 'lote' ? validarIdLote(texto) : validarContribuyente(texto)
    if (!validacion.valido) {
      setErrorCampo(validacion.mensaje)
      return
    }
    setErrorCampo('')
    controlador.current?.abort()
    const actual = new AbortController()
    controlador.current = actual
    const consulta = modo === 'lote' ? `el lote #${validacion.valor}` : `el contribuyente ${validacion.valor}`
    setEstado({ cargando: true, avisos: null, error: null, consulta })
    const pedido = modo === 'lote'
      ? listarAvisosPorLote(validacion.valor, { signal: actual.signal })
      : listarAvisosContribuyente(validacion.valor, { signal: actual.signal })
    pedido
      .then((avisos) => { if (!actual.signal.aborted) setEstado({ cargando: false, avisos, error: null, consulta }) })
      .catch((error) => { if (!actual.signal.aborted) setEstado({ cargando: false, avisos: null, error, consulta }) })
  }

  function cambiarModo(nuevo) {
    controlador.current?.abort()
    setModo(nuevo)
    setTexto('')
    setErrorCampo('')
    setEstado({ cargando: false, avisos: null, error: null, consulta: null })
  }

  const { cargando, avisos, error, consulta } = estado
  const etiqueta = modo === 'lote' ? 'Número de lote' : 'Código o documento del contribuyente'
  return (
    <section className="pcard" aria-labelledby="alertas-historial-titulo" aria-busy={cargando}>
      <h2 id="alertas-historial-titulo">Historial de avisos</h2>
      <p className="hint">Cada envío completado queda registrado en el historial del contribuyente.</p>
      <form className="filters consulta-filtros" onSubmit={consultar} aria-label="Consultar historial de avisos" noValidate>
        <div className="fld">
          <label htmlFor="alertas-modo">Consultar por</label>
          <select id="alertas-modo" value={modo} onChange={(e) => cambiarModo(e.target.value)}>
            <option value="lote">Lote</option>
            <option value="contribuyente">Contribuyente</option>
          </select>
        </div>
        <div className="fld">
          <label htmlFor="alertas-valor">{etiqueta}</label>
          <input
            id="alertas-valor" type="text" autoComplete="off" value={texto}
            inputMode={modo === 'lote' ? 'numeric' : 'text'}
            aria-invalid={errorCampo ? 'true' : undefined} aria-describedby={errorCampo ? 'alertas-valor-error' : undefined}
            onChange={(e) => { setTexto(e.target.value); setErrorCampo('') }}
          />
          {errorCampo && <span id="alertas-valor-error" className="campo-error" role="alert">{errorCampo}</span>}
        </div>
        <button className="btn primary" type="submit" disabled={cargando}>{cargando ? 'Consultando…' : 'Consultar'}</button>
      </form>

      {cargando && <p className="consulta-vacio" role="status">Consultando el historial…</p>}
      {error && (
        <div className="alert" role="alert">
          <strong>No se pudo consultar el historial de {consulta}.</strong>
          <p>{error.message}</p>
          <button type="button" className="btn" onClick={() => consultar()}>Reintentar</button>
        </div>
      )}
      {!cargando && !error && avisos && avisos.length === 0 && (
        <p className="consulta-vacio" role="status">No hay avisos registrados para {consulta}.</p>
      )}
      {!cargando && !error && avisos && avisos.length > 0 && (
        <>
          <p className="hint" role="status">{avisos.length} {avisos.length === 1 ? 'aviso' : 'avisos'} de {consulta}.</p>
          <TablaAvisos avisos={avisos} titulo={`Avisos de ${consulta}`} />
        </>
      )}
    </section>
  )
}

export default function AlertasMora() {
  const { sesion } = useSesionInterna()
  return (
    <div className="consulta-tributaria">
      {puedeProcesarAlertas(sesion) && <ProcesarLote />}
      <Historial />
    </div>
  )
}
