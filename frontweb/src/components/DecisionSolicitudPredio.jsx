import { useEffect, useRef, useState } from 'react'
import ModalConfirmacion from './ModalConfirmacion.jsx'
import {
  aceptarSolicitudPredio, obtenerSolicitudPredio, rechazarSolicitudPredio,
} from '../services/revisionPredioService.js'

const CAMPOS_PREDIO = [
  { name: 'codigoPredio', label: 'Código de predio', required: true, maxLength: 12, pattern: '[A-Za-z0-9\\-]{1,12}', title: 'Usa letras, números o guiones.' },
  { name: 'numeroCatastral', label: 'Número catastral (opcional)', maxLength: 30 },
  { name: 'ubicacion', label: 'Ubicación', required: true, maxLength: 250 },
  { name: 'zona', label: 'Zona', required: true, maxLength: 20 },
  { name: 'usoPredio', label: 'Uso del predio (opcional)', maxLength: 60 },
  { name: 'areaTerreno', label: 'Área del terreno (m²)', required: true, type: 'number', min: '0.01', max: '99999999.99', step: '0.01' },
  { name: 'baseImponible', label: 'Base imponible (S/)', required: true, type: 'number', min: '0', max: '999999999999.99', step: '0.01' },
  { name: 'fechaAdquisicion', label: 'Fecha de adquisición (opcional)', type: 'date' },
]

function Campo({ label, ...atributos }) {
  const id = `revision-predio-${atributos.name}`
  return <div className="fld"><label htmlFor={id}>{label}</label><input id={id} {...atributos} /></div>
}

export default function DecisionSolicitudPredio({ solicitud, onResuelta }) {
  const [accion, setAccion] = useState(null)
  const [confirmacion, setConfirmacion] = useState(null)
  const [error, setError] = useState(null)
  const [procesando, setProcesando] = useState(false)
  const [requiereComprobar, setRequiereComprobar] = useState(false)
  const bloqueo = useRef(false)
  const formularioRef = useRef(null)
  const id = solicitud.idSolicitud
  const esRegistro = solicitud.tipoTramite === 'REGISTRO'
  const esRechazo = accion === 'rechazar'
  const operacion = esRegistro ? 'registro' : 'traspaso'
  const hoy = new Date().toLocaleDateString('sv-SE', { timeZone: 'America/Lima' })

  useEffect(() => {
    if (accion) formularioRef.current?.querySelector('input, textarea')?.focus()
  }, [accion])

  function preparar(evento) {
    evento.preventDefault()
    setError(null)
    const formulario = new FormData(evento.currentTarget)
    const leer = (campo) => formulario.get(campo)?.trim() || null

    if (esRechazo) {
      const motivo = leer('motivo')
      if (!motivo) {
        setError(new Error('Escribe el motivo que se enviará al contribuyente.'))
        return
      }
      setConfirmacion({ motivo })
      return
    }

    const datos = { condicionPropiedad: leer('condicionPropiedad'), porcentajePropiedad: null }
    const porcentaje = leer('porcentajePropiedad')
    if (porcentaje) datos.porcentajePropiedad = Number(porcentaje)
    if (esRegistro) {
      if (!leer('ubicacion') || !leer('zona')) {
        setError(new Error('Ingresa la ubicación y la zona del predio.'))
        return
      }
      datos.predio = Object.fromEntries(CAMPOS_PREDIO.map(({ name, type }) => [
        name, type === 'number' ? Number(formulario.get(name)) : leer(name),
      ]))
    }
    setConfirmacion(datos)
  }

  async function comprobarEstado() {
    try {
      const actual = await obtenerSolicitudPredio(id)
      setRequiereComprobar(false)
      if (actual.estado !== 'EN_REVISION') onResuelta(actual, 'Se actualizó el detalle con la decisión registrada.')
    } catch {
      setRequiereComprobar(true)
    }
  }

  async function confirmar() {
    if (bloqueo.current || !confirmacion) return
    bloqueo.current = true
    setProcesando(true)
    setError(null)
    setConfirmacion(null)
    try {
      const resuelta = esRechazo
        ? await rechazarSolicitudPredio(id, confirmacion)
        : await aceptarSolicitudPredio(id, confirmacion)
      onResuelta(resuelta, `Solicitud ${esRechazo ? 'rechazada' : 'aceptada'}. El resultado se envió al buzón del contribuyente.`)
    } catch (err) {
      setError(err)
      if (err.codigo === 'SOLICITUD_NO_EN_REVISION' || err.estado === 0 || err.estado >= 500) await comprobarEstado()
    } finally {
      bloqueo.current = false
      setProcesando(false)
    }
  }

  async function volverAConsultar() {
    setProcesando(true)
    await comprobarEstado()
    setProcesando(false)
  }

  return (
    <section className="pcard" aria-labelledby="titulo-decision-predio" aria-busy={procesando}>
      <h2 id="titulo-decision-predio">Decisión de Ventanilla</h2>
      {!accion ? (
        <div className="revision-predio__acciones">
          <button className="btn primary" type="button" onClick={() => setAccion('aceptar')}>Aceptar solicitud</button>
          <button className="btn peligro" type="button" onClick={() => setAccion('rechazar')}>Rechazar solicitud</button>
        </div>
      ) : (
        <form className="revision-predio__formulario" ref={formularioRef} onSubmit={preparar}>
          <h3>{esRechazo ? 'Rechazar solicitud' : `Aceptar ${operacion}`}</h3>
          {error && <div className="alert" role="alert"><p>{error.message}</p></div>}
          {requiereComprobar && (
            <div className="alert" role="alert">
              <p>Consulta el estado de la solicitud antes de volver a confirmar.</p>
              <button className="btn" type="button" onClick={volverAConsultar} disabled={procesando}>Consultar estado</button>
            </div>
          )}
          <fieldset disabled={procesando || requiereComprobar}>
            {esRechazo ? (
              <div className="fld">
                <label htmlFor="motivo-rechazo-predio">Motivo del rechazo</label>
                <textarea id="motivo-rechazo-predio" name="motivo" required maxLength={400} rows={4} aria-describedby="ayuda-motivo-predio" />
                <p id="ayuda-motivo-predio" className="hint">Hasta 400 caracteres. El motivo se enviará al buzón del contribuyente.</p>
              </div>
            ) : (
              <>
                <p className="hint">{esRegistro ? 'Ingresa los datos del predio tal como figuran en los documentos adjuntos.' : `El traspaso se aplicará al predio ${solicitud.codigoPredio}.`}</p>
                <div className="revision-predio__campos">
                  {esRegistro && CAMPOS_PREDIO.map((campo) => (
                    <Campo key={campo.name} {...campo} max={campo.type === 'date' ? hoy : campo.max} />
                  ))}
                  <Campo name="condicionPropiedad" label="Condición de propiedad (opcional)" maxLength={30} placeholder="Propietario" />
                  <Campo name="porcentajePropiedad" label="Porcentaje de propiedad (opcional)" type="number" min="0.01" max="100" step="0.01" placeholder="100" />
                </div>
              </>
            )}
          </fieldset>
          <div className="revision-predio__acciones">
            <button className={`btn ${esRechazo ? 'peligro' : 'primary'}`} type="submit" disabled={procesando || requiereComprobar}>
              {procesando ? 'Procesando…' : 'Revisar y confirmar'}
            </button>
            <button className="btn" type="button" disabled={procesando || requiereComprobar} onClick={() => { setAccion(null); setError(null) }}>Cancelar</button>
          </div>
        </form>
      )}
      <ModalConfirmacion
        abierto={Boolean(confirmacion)}
        titulo={esRechazo ? 'Confirmar rechazo' : `Confirmar ${operacion}`}
        mensaje={esRechazo
          ? 'Se enviará al buzón el motivo del rechazo y la deuda del predio, si está disponible.'
          : `Se realizará el ${operacion} en SAMNET y se enviará el resultado al buzón del contribuyente.`}
        textoConfirmar={esRechazo ? 'Confirmar rechazo' : 'Confirmar aceptación'}
        peligro={esRechazo}
        onConfirmar={confirmar}
        onCerrar={() => setConfirmacion(null)}
      />
    </section>
  )
}
