import { useCallback, useEffect, useRef, useState } from 'react'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import {
  guardarConfiguracionRecordatorio,
  listarEnviosRecordatorios,
  obtenerConfiguracionRecordatorio,
  procesarRecordatorios,
} from '../services/recordatorioService.js'
import {
  AVISO_SIN_SERVIDOR_CORREO,
  DIAS_RECORDATORIO_POR_DEFECTO,
  calcularFechaProgramada,
  estadoRecordatorio,
  etiquetaTributoRecordatorio,
  resumenEnvioRecordatorios,
  textoAnticipacion,
  validarDiasRecordatorio,
} from '../utils/recordatorios.js'
import { fecha, fechaHora, moneda } from '../utils/formato.js'
import './ConfiguracionRecordatorios.css'

// HU016 (RF-022): configuración de los días de anticipación del recordatorio de vencimiento y envío del día.

function fechaEnDias(dias) {
  const hoy = new Date()
  const fecha = new Date(Date.UTC(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() + dias))
  return fecha.toISOString().slice(0, 10)
}

const EJEMPLO_VENCIMIENTO = fechaEnDias(14)

function NotificacionTemporal({ notificacion }) {
  if (!notificacion) return null
  return (
    <div
      key={notificacion.id}
      className={`notificacion-temporal ${notificacion.tipo === 'error' ? 'alert' : 'confirmacion'}`}
      role={notificacion.tipo === 'error' ? 'alert' : 'status'}
    >
      <strong>{notificacion.mensaje}</strong>
      {notificacion.detalles?.length > 0 && (
        <ul>{notificacion.detalles.map((detalle) => <li key={detalle}>{detalle}</li>)}</ul>
      )}
    </div>
  )
}

export default function ConfiguracionRecordatorios() {
  const [configuracion, setConfiguracion] = useState(null)
  const [valor, setValor] = useState(String(DIAS_RECORDATORIO_POR_DEFECTO))
  const [errorCampo, setErrorCampo] = useState('')
  const [errorCarga, setErrorCarga] = useState('')
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [notificacion, setNotificacion] = useState(null)
  const [procesando, setProcesando] = useState(false)
  const [resultadoEnvio, setResultadoEnvio] = useState(null)
  const [envios, setEnvios] = useState({ total: 0, totalPaginas: 0, envios: [] })
  const [paginaEnvios, setPaginaEnvios] = useState(1)
  const [tamanioEnvios, setTamanioEnvios] = useState(10)
  const [cargandoEnvios, setCargandoEnvios] = useState(true)
  const [errorEnvios, setErrorEnvios] = useState('')
  const [recargaEnvios, setRecargaEnvios] = useState(0)
  const contadorNotificacion = useRef(0)

  function nuevaNotificacion(datos) {
    contadorNotificacion.current += 1
    return { id: contadorNotificacion.current, ...datos }
  }

  const validacion = validarDiasRecordatorio(valor)
  const diasVistaPrevia = validacion.valido
    ? validacion.valor
    : (configuracion?.diasAnticipacion ?? DIAS_RECORDATORIO_POR_DEFECTO)
  const fechaVistaPrevia = calcularFechaProgramada(EJEMPLO_VENCIMIENTO, diasVistaPrevia)
  const sinCambios = Boolean(configuracion) && validacion.valido && validacion.valor === configuracion.diasAnticipacion
  const resumenEnvio = resultadoEnvio ? resumenEnvioRecordatorios(resultadoEnvio) : null
  const listaEnvios = Array.isArray(envios.envios) ? envios.envios : []

  useEffect(() => {
    let activo = true
    obtenerConfiguracionRecordatorio()
      .then((resultado) => {
        if (!activo) return
        setConfiguracion(resultado)
        setValor(String(resultado.diasAnticipacion))
        setErrorCarga('')
      })
      .catch((error) => {
        if (activo) setErrorCarga(error.message || 'No se pudo consultar la configuración de recordatorios.')
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => { activo = false }
  }, [])

  useEffect(() => {
    if (!notificacion) return undefined
    const temporizador = window.setTimeout(() => setNotificacion(null), 5000)
    return () => window.clearTimeout(temporizador)
  }, [notificacion])

  const cargarEnvios = useCallback(async (signal) => {
    setCargandoEnvios(true)
    setErrorEnvios('')
    try {
      const resultado = await listarEnviosRecordatorios({
        pagina: paginaEnvios - 1,
        tamanio: tamanioEnvios,
        signal,
      })
      setEnvios(resultado)
      const totalPaginas = Math.max(1, Number(resultado?.totalPaginas) || 0)
      if (paginaEnvios > totalPaginas) setPaginaEnvios(totalPaginas)
    } catch (error) {
      if (error.name !== 'AbortError') {
        setErrorEnvios(error.message || 'No se pudo cargar el detalle de los envíos del día.')
      }
    } finally {
      if (!signal?.aborted) setCargandoEnvios(false)
    }
  }, [paginaEnvios, tamanioEnvios])

  useEffect(() => {
    const controlador = new AbortController()
    // La consulta se sincroniza con la página y el tamaño elegidos.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    cargarEnvios(controlador.signal)
    return () => controlador.abort()
  }, [cargarEnvios, recargaEnvios])

  async function reintentarCarga() {
    setCargando(true)
    setErrorCarga('')
    try {
      const resultado = await obtenerConfiguracionRecordatorio()
      setConfiguracion(resultado)
      setValor(String(resultado.diasAnticipacion))
    } catch (error) {
      setErrorCarga(error.message || 'No se pudo consultar la configuración de recordatorios.')
    } finally {
      setCargando(false)
    }
  }

  function cambiarValor(evento) {
    setValor(evento.target.value)
    setErrorCampo('')
    setNotificacion(null)
  }

  function usarValorPorDefecto() {
    setValor(String(configuracion?.valorPorDefecto ?? DIAS_RECORDATORIO_POR_DEFECTO))
    setErrorCampo('')
    setNotificacion(null)
  }

  async function guardar(evento) {
    evento.preventDefault()
    if (!validacion.valido) {
      setErrorCampo(validacion.mensaje)
      return
    }

    setGuardando(true)
    setErrorCampo('')
    setNotificacion(null)
    try {
      const resultado = await guardarConfiguracionRecordatorio(validacion.valor)
      setConfiguracion(resultado)
      setValor(String(resultado.diasAnticipacion))
      setNotificacion(nuevaNotificacion({
        tipo: 'exito',
        mensaje: `Los recordatorios se enviarán ${textoAnticipacion(resultado.diasAnticipacion)}. El cambio no requiere modificar código.`,
      }))
    } catch (error) {
      setNotificacion(nuevaNotificacion({
        tipo: 'error',
        mensaje: error.message || 'No se pudo guardar la configuración del recordatorio.',
        detalles: error.detalles,
      }))
    } finally {
      setGuardando(false)
    }
  }

  async function procesarHoy() {
    setProcesando(true)
    setResultadoEnvio(null)
    try {
      setResultadoEnvio(await procesarRecordatorios())
      setPaginaEnvios(1)
      setRecargaEnvios((valor) => valor + 1)
    } catch (error) {
      setNotificacion(nuevaNotificacion({ tipo: 'error', mensaje: error.message || 'No se pudo procesar el envío de recordatorios.' }))
    } finally {
      setProcesando(false)
    }
  }

  return (
    <div className="config-recordatorios" aria-busy={cargando || guardando}>
      <section className="pcard recordatorios-cabecera">
        <div>
          <h2>Recordatorios de vencimiento</h2>
          <p className="hint">
            Define con cuántos días de anticipación se envía el recordatorio por correo antes de que venza una obligación.
          </p>
        </div>
      </section>

      {cargando && <p className="hint mensaje-carga" role="status">Consultando la configuración de recordatorios…</p>}

      {!cargando && errorCarga && (
        <div className="alert" role="alert">
          <p>{errorCarga}</p>
          <button className="btn" type="button" onClick={reintentarCarga}>Reintentar configuración</button>
        </div>
      )}

      {!cargando && !errorCarga && configuracion && (
        <>
          <section className="pcard" aria-labelledby="recordatorios-formulario-titulo">
            <h2 id="recordatorios-formulario-titulo">Días de anticipación</h2>
            <p className="hint">
              El valor debe ser un entero entre {configuracion.minimo} y {configuracion.maximo} días.
              El valor por defecto es {configuracion.valorPorDefecto} días.
            </p>

            <form className="recordatorios-formulario" onSubmit={guardar} noValidate>
              <div className={`fld ${errorCampo ? 'fld-error' : ''}`}>
                <label htmlFor="dias-recordatorio">Días antes del vencimiento</label>
                <input
                  id="dias-recordatorio"
                  type="number"
                  inputMode="numeric"
                  min={configuracion.minimo}
                  max={configuracion.maximo}
                  step="1"
                  value={valor}
                  aria-invalid={errorCampo ? 'true' : undefined}
                  aria-describedby={errorCampo ? 'dias-recordatorio-error' : 'dias-recordatorio-ayuda'}
                  onChange={cambiarValor}
                />
                <span id="dias-recordatorio-ayuda" className="hint">
                  {validacion.valido
                    ? `Se enviará ${textoAnticipacion(validacion.valor)}.`
                    : 'Ingresa un número entero dentro del rango permitido.'}
                </span>
                {errorCampo && (
                  <span id="dias-recordatorio-error" className="campo-error" role="alert">{errorCampo}</span>
                )}
              </div>

              <div className="recordatorios-ejemplo" aria-live="polite">
                <strong>Vista previa</strong>
                <p>
                  Si una obligación vence el <strong>{fecha(EJEMPLO_VENCIMIENTO)}</strong>, el recordatorio
                  se enviaría el <strong>{fecha(fechaVistaPrevia)}</strong>.
                </p>
              </div>

              <NotificacionTemporal notificacion={notificacion} />

              <div className="recordatorios-acciones">
                <button
                  className="btn"
                  type="button"
                  onClick={usarValorPorDefecto}
                  disabled={guardando || Number(valor) === configuracion.valorPorDefecto}
                >
                  Usar {configuracion.valorPorDefecto} días
                </button>
                <button className="btn primary" type="submit" disabled={guardando || !validacion.valido || sinCambios}>
                  {guardando ? 'Guardando…' : 'Guardar cambios'}
                </button>
              </div>
            </form>
          </section>

          <section className="pcard" aria-labelledby="recordatorios-estado-titulo">
            <h2 id="recordatorios-estado-titulo">Configuración vigente</h2>
            <div className="pfields">
              <div className="pf">
                <span className="pl">Días de anticipación</span>
                <strong className="pv">{configuracion.diasAnticipacion} días</strong>
              </div>
              <div className="pf">
                <span className="pl">Valor por defecto</span>
                <strong className="pv">{configuracion.valorPorDefecto} días</strong>
              </div>
              <div className="pf">
                <span className="pl">Rango permitido</span>
                <strong className="pv">{configuracion.minimo} a {configuracion.maximo} días</strong>
              </div>
              <div className="pf">
                <span className="pl">Última actualización</span>
                <strong className="pv">
                  {configuracion.actualizadoEn ? fechaHora(configuracion.actualizadoEn) : 'Valor por defecto'}
                </strong>
              </div>
              <div className="pf">
                <span className="pl">Actualizado por</span>
                <strong className="pv">{configuracion.actualizadoPor || '—'}</strong>
              </div>
            </div>
            <div className="info recordatorios-ayuda">
              <strong>Cómo funciona el recordatorio.</strong>
              <p>
                Se envía únicamente por correo electrónico a los contribuyentes con correo registrado.
                Quienes no tienen correo quedan fuera del envío y se muestran como <em>sin correo registrado</em>.
                Cambiar este valor no modifica los avisos ya enviados y no requiere cambiar código.
              </p>
            </div>
          </section>

          <section className="pcard" aria-labelledby="recordatorios-envio-titulo">
            <h2 id="recordatorios-envio-titulo">Envío del día</h2>
            <p className="hint">
              El envío se ejecuta automáticamente cada día. Puedes procesarlo ahora para las cuotas que vencen dentro
              de la anticipación configurada; las que ya recibieron su recordatorio no se repiten.
            </p>
            <button className="btn primary" type="button" onClick={procesarHoy} disabled={procesando}>
              {procesando ? 'Procesando…' : 'Procesar recordatorios de hoy'}
            </button>
            {resumenEnvio && (
              <>
                {resumenEnvio.sinServidorCorreo && (
                  <div className="alert" role="alert">
                    <p>{AVISO_SIN_SERVIDOR_CORREO}</p>
                  </div>
                )}
                <div className="tabla-scroll" role="region" aria-label="Resultado del envío" tabIndex={0}>
                  <table className="tabla">
                    <caption className="sr-only">Resultado del envío de recordatorios</caption>
                    <thead>
                      <tr>
                        <th scope="col">Cuotas por vencer</th>
                        <th scope="col" className="num">Enviados</th>
                        <th scope="col" className="num">Registrados</th>
                        <th scope="col" className="num">Sin correo</th>
                        <th scope="col" className="num">Desactivados</th>
                        <th scope="col" className="num">Ya notificados</th>
                        <th scope="col" className="num">Fallidos</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <th scope="row">{resumenEnvio.detectados}</th>
                        <td className="num">{resumenEnvio.enviados}</td>
                        <td className="num">{resumenEnvio.registrados}</td>
                        <td className="num">{resumenEnvio.sinCorreo}</td>
                        <td className="num">{resumenEnvio.desactivados}</td>
                        <td className="num">{resumenEnvio.duplicados}</td>
                        <td className="num">{resumenEnvio.fallidos}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </>
            )}

            <div className="recordatorios-envios" aria-busy={cargandoEnvios}>
              <div className="recordatorios-envios-cabecera">
                <h3>Recordatorios del día</h3>
                <span className="hint">Incluye enviados, registrados y fallidos.</span>
              </div>

              {errorEnvios && (
                <div className="alert" role="alert">
                  <p>{errorEnvios}</p>
                  <button className="btn" type="button" onClick={() => setRecargaEnvios((valor) => valor + 1)}>
                    Reintentar detalle
                  </button>
                </div>
              )}

              {!errorEnvios && cargandoEnvios && (
                <p className="hint" role="status">Consultando los envíos del día…</p>
              )}

              {!errorEnvios && !cargandoEnvios && listaEnvios.length === 0 && (
                <p className="hint">No hay recordatorios procesados hoy.</p>
              )}

              {!errorEnvios && !cargandoEnvios && listaEnvios.length > 0 && (
                <>
                  <div className="tabla-scroll" role="region" aria-label="Detalle de los envíos del día" tabIndex={0}>
                    <table className="tabla">
                      <caption className="sr-only">Recordatorios procesados hoy</caption>
                      <thead>
                        <tr>
                          <th scope="col">Fecha y hora</th>
                          <th scope="col">Contribuyente</th>
                          <th scope="col">Correo destino</th>
                          <th scope="col">Obligación</th>
                          <th scope="col">Tributo</th>
                          <th scope="col">Vence</th>
                          <th scope="col" className="num">Saldo</th>
                          <th scope="col">Estado</th>
                        </tr>
                      </thead>
                      <tbody>
                        {listaEnvios.map((envio) => {
                          const estado = estadoRecordatorio(envio.estado)
                          const saldo = envio.saldo === null || envio.saldo === undefined ? '—' : moneda(envio.saldo)
                          return (
                            <tr key={envio.idAviso}>
                              <td>{fechaHora(envio.fechaEnvio || envio.fechaProgramada)}</td>
                              <td>
                                <strong>{envio.nombre || '—'}</strong>
                                <span className="recordatorios-documento">{envio.documentoContribuyente || '—'}</span>
                              </td>
                              <td>{envio.correoDestino || '—'}</td>
                              <td>{envio.idObligacion ? `#${envio.idObligacion}` : '—'}</td>
                              <td>{etiquetaTributoRecordatorio(envio.tributo)}</td>
                              <td>{fecha(envio.fechaVencimiento)}</td>
                              <td className="num">{saldo}</td>
                              <td><span className={`estado ${estado.clase}`}>{estado.texto}</span></td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                  <PaginacionTabla
                    id="recordatorios-envios"
                    totalRegistros={envios.total}
                    pagina={paginaEnvios}
                    tamanio={tamanioEnvios}
                    onCambiarPagina={setPaginaEnvios}
                    onCambiarTamanio={(valor) => { setTamanioEnvios(valor); setPaginaEnvios(1) }}
                  />
                </>
              )}
            </div>
          </section>
        </>
      )}
    </div>
  )
}
