import { useEffect, useRef, useState } from 'react'
import { useSesionInterna } from '../context/sesionInterna.js'
import {
  RECORDATORIOS_SIMULADOS,
  guardarConfiguracionRecordatorio,
  obtenerConfiguracionRecordatorio,
  reiniciarDatosSimulados,
} from '../services/recordatorioService.js'
import {
  DIAS_RECORDATORIO_POR_DEFECTO,
  calcularFechaProgramada,
  textoAnticipacion,
  validarDiasRecordatorio,
} from '../utils/recordatorios.js'
import { fecha, fechaHora } from '../utils/formato.js'
import './ConfiguracionRecordatorios.css'

// HU016 (RF-022): configuración de los días de anticipación del recordatorio de vencimiento.
// La pantalla usa el mock de recordatorioService mientras el backend no publique la historia.

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
  const { sesion } = useSesionInterna()
  const [configuracion, setConfiguracion] = useState(null)
  const [valor, setValor] = useState(String(DIAS_RECORDATORIO_POR_DEFECTO))
  const [errorCampo, setErrorCampo] = useState('')
  const [errorCarga, setErrorCarga] = useState('')
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [notificacion, setNotificacion] = useState(null)
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
      const resultado = await guardarConfiguracionRecordatorio(validacion.valor, {
        usuario: sesion?.nombreCompleto,
      })
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

  async function restablecerDatos() {
    const confirmado = window.confirm(
      '¿Restablecer la configuración y los recordatorios simulados de este navegador? '
      + 'La pantalla volverá a los datos de demostración iniciales.',
    )
    if (!confirmado) return

    reiniciarDatosSimulados()
    await reintentarCarga()
    setNotificacion(nuevaNotificacion({
      tipo: 'exito',
      mensaje: 'Los datos simulados se restablecieron. El valor volvió a 5 días.',
    }))
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

      {RECORDATORIOS_SIMULADOS && (
        <div className="info recordatorios-demo" role="note">
          <strong>Datos simulados.</strong> El backend de HU016 todavía no está implementado. La configuración
          y los avisos que se muestran en la pestaña Cobranza se guardan en este navegador para la demostración.
        </div>
      )}

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

          {RECORDATORIOS_SIMULADOS && (
            <section className="pcard recordatorios-demo-datos" aria-labelledby="recordatorios-demo-titulo">
              <h2 id="recordatorios-demo-titulo">Datos de demostración</h2>
              <p className="hint">
                Los estados e indicadores de la pestaña Cobranza son simulados y se conservan en este navegador.
                Restablecerlos devuelve la configuración a 5 días y borra los avisos de prueba.
              </p>
              <button className="btn peligro" type="button" onClick={restablecerDatos} disabled={guardando}>
                Restablecer datos simulados
              </button>
            </section>
          )}
        </>
      )}
    </div>
  )
}
