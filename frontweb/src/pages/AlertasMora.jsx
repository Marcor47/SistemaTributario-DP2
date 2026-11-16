import { useEffect, useRef, useState } from 'react'
import ModalConfirmacion from '../components/ModalConfirmacion.jsx'
import { useSesionInterna } from '../context/sesionInterna.js'
import {
  configurarAutomatizacionAlertasMora, listarAvisosContribuyente, listarAvisosPorLote,
  listarProcesosAutomaticos, obtenerAutomatizacionAlertasMora, obtenerResumenLoteAlertas,
  procesarAlertasMora,
} from '../services/alertaMoraService.js'
import {
  cifrasProcesamiento, estadoAviso, ordenarAvisos, puedeProcesarAlertas, tipoAviso,
  validarContribuyente, validarIdLote,
} from '../utils/alertasMora.js'
import { fecha, fechaHora } from '../utils/formato.js'
import './ConsultasTributarias.css'

// HU012 (RF-013): alertas de mora temprana por correo. Procesar un lote publicado envía los avisos;
// el historial se consulta por lote o por contribuyente.

function Automatizacion() {
  const [configuracion, setConfiguracion] = useState(null)
  const [procesos, setProcesos] = useState([])
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [confirmando, setConfirmando] = useState(false)
  const [horaConsulta, setHoraConsulta] = useState(null)

  async function actualizar() {
    try {
      const [config, trabajos] = await Promise.all([
        obtenerAutomatizacionAlertasMora(), listarProcesosAutomaticos(),
      ])
      setConfiguracion(config)
      setProcesos(trabajos)
      setHoraConsulta(new Date())
      setError(null)
    } catch (fallo) { setError(fallo.message) }
  }

  useEffect(() => {
    Promise.all([obtenerAutomatizacionAlertasMora(), listarProcesosAutomaticos()])
      .then(([config, trabajos]) => {
        setConfiguracion(config)
        setProcesos(trabajos)
        setHoraConsulta(new Date())
      })
      .catch((fallo) => setError(fallo.message))
  }, [])

  async function cambiar() {
    setGuardando(true)
    try {
      const nueva = await configurarAutomatizacionAlertasMora(!configuracion.habilitado)
      setConfiguracion(nueva)
      setError(null)
      await actualizar()
    } catch (fallo) { setError(fallo.message) }
    setGuardando(false)
    setConfirmando(false)
  }

  return (
    <section className="pcard" aria-labelledby="alertas-automaticas-titulo">
      <h2 id="alertas-automaticas-titulo">Envíos automáticos</h2>
      <p className="hint">
        {configuracion?.habilitado
          ? 'Activados: los nuevos lotes publicados se procesan por bloques sin pulsar el botón manual.'
          : 'Desactivados: las nuevas cargas no enviarán correos automáticamente. El envío manual sigue disponible.'}
      </p>
      <p role="status">Estado: <strong>{configuracion ? (configuracion.habilitado ? 'Activado' : 'Desactivado') : 'Cargando…'}</strong></p>
      {configuracion && <p className="hint">Modo de correo: <strong>{configuracion.correoReal ? 'SMTP real' : 'Registro local (no envía correos)'}</strong>.</p>}
      <div className="filters">
        <button className="btn primary" type="button" disabled={!configuracion || guardando}
          onClick={() => setConfirmando(true)}>
          {configuracion?.habilitado ? 'Desactivar envíos automáticos' : 'Activar envíos automáticos'}
        </button>
        <button className="btn" type="button" onClick={actualizar}>Actualizar progreso</button>
      </div>
      {error && <p className="campo-error" role="alert">{error}</p>}
      {procesos.length > 0 && (
        <div className="tabla-scroll" role="region" aria-label="Progreso de envíos automáticos" tabIndex={0}>
          <table className="tabla">
            <thead><tr><th scope="col">Lote</th><th scope="col">Estado</th><th scope="col">Última fila procesada</th><th scope="col">Límite de 24 h</th></tr></thead>
            <tbody>{procesos.map((proceso) => (
              <tr key={proceso.idLote}><th scope="row">{proceso.idLote}</th>
                <td>{proceso.estado.replaceAll('_', ' ')}</td><td>{proceso.ultimaFila}</td>
                <td>{proceso.fechaLimite ? fechaHora(proceso.fechaLimite) : '—'}
                  {proceso.estado !== 'COMPLETADO' && proceso.fechaLimite && horaConsulta
                    && new Date(proceso.fechaLimite).getTime() < horaConsulta.getTime() ? ' · Plazo vencido' : ''}</td></tr>
            ))}</tbody>
          </table>
        </div>
      )}
      <ModalConfirmacion abierto={confirmando}
        titulo={configuracion?.habilitado ? '¿Desactivar los envíos automáticos?' : '¿Activar los envíos automáticos?'}
        mensaje={<p>{configuracion?.habilitado
          ? 'El procesamiento automático se detendrá después del bloque actual. Podrás reanudarlo después.'
          : `Las cargas creadas y publicadas después de activar se procesarán sin una confirmación por lote. ${configuracion?.correoReal ? 'Se enviarán correos reales.' : 'El modo actual solo registrará avisos: no enviará correos reales.'} Los lotes que ya existen no se enviarán automáticamente.`}</p>}
        textoConfirmar={configuracion?.habilitado ? 'Desactivar' : 'Activar'}
        procesando={guardando} onConfirmar={cambiar} onCerrar={() => setConfirmando(false)} />
    </section>
  )
}

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
  const [resumen, setResumen] = useState(null)
  const [consultando, setConsultando] = useState(false)
  const [estado, setEstado] = useState({ procesando: false, resultado: null, error: null })

  async function pedirConfirmacion(evento) {
    evento.preventDefault()
    const idLote = validarIdLote(texto)
    if (!idLote.valido) {
      setErrorCampo(idLote.mensaje)
      return
    }
    setErrorCampo('')
    setConsultando(true)
    try {
      const datos = await obtenerResumenLoteAlertas(idLote.valor)
      if (datos.estado !== 'PUBLICADO') {
        setErrorCampo('Solo se pueden procesar lotes publicados.')
      } else {
        setResumen(datos)
        setConfirmando(idLote.valor)
      }
    } catch (error) { setErrorCampo(error.message) }
    setConsultando(false)
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
        <button className="btn primary" type="submit" disabled={procesando || consultando}>
          {procesando ? 'Procesando…' : consultando ? 'Revisando lote…' : 'Procesar alertas'}
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
        mensaje={<p>El lote #{confirmando} leyó originalmente {resumen?.registrosLeidos?.toLocaleString('es-PE') ?? '—'} registros y tiene {resumen?.avisosExistentes?.toLocaleString('es-PE') ?? '—'} avisos registrados. El envío manual vuelve a consultar SAMNET, por lo que la cantidad actual puede ser diferente y puede ser grande. Se enviarán correos reales a las cuotas vencidas con saldo y correo; los avisos ya enviados no se repiten.</p>}
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
      {sesion?.permisos?.includes('ALERTAS:EVALUAR') && <Automatizacion />}
      {puedeProcesarAlertas(sesion) && <ProcesarLote />}
      <Historial />
    </div>
  )
}
