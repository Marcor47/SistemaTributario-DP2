import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  codificarArchivo, decodificarBase64, firmarSolicitud, iniciarSolicitud,
  obtenerDocumento, obtenerSesion, obtenerSolicitud,
} from '../services/constanciaService.js'
import './Constancia.css'

const claveSolicitud = (codigo) => `constancia-pendiente-${codigo}`

export default function Constancia() {
  const [sesion, setSesion] = useState(null)
  const [solicitud, setSolicitud] = useState(null)
  const [documento, setDocumento] = useState(null)
  const [numero, setNumero] = useState('')
  const [archivoFirma, setArchivoFirma] = useState(null)
  const [certificado, setCertificado] = useState('')
  const [cargando, setCargando] = useState(true)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')
  const [sinSesion, setSinSesion] = useState(false)

  useEffect(() => {
    let activo = true
    async function cargar() {
      try {
        const usuario = await obtenerSesion()
        if (!activo) return
        setSesion(usuario)
        const guardado = window.localStorage.getItem(claveSolicitud(usuario.codigoContribuyente))
        if (guardado) {
          try {
            const pendiente = await obtenerSolicitud(guardado)
            if (activo) {
              setSolicitud(pendiente)
              setNumero(String(pendiente.idSolicitud))
            }
          } catch (err) {
            if (err.estado === 404) window.localStorage.removeItem(claveSolicitud(usuario.codigoContribuyente))
            else if (activo) setError(err.message)
          }
        }
      } catch (err) {
        if (activo) {
          setSinSesion(err.estado === 401)
          setError(err.estado === 401 ? '' : err.message)
        }
      } finally {
        if (activo) setCargando(false)
      }
    }
    cargar()
    return () => { activo = false }
  }, [])

  async function nuevaSolicitud() {
    setOcupado(true)
    setError('')
    try {
      const nueva = await iniciarSolicitud()
      setSolicitud(nueva)
      setDocumento(null)
      setNumero(String(nueva.idSolicitud))
      window.localStorage.setItem(claveSolicitud(sesion.codigoContribuyente), String(nueva.idSolicitud))
    } catch (err) {
      setError(err.message)
    } finally {
      setOcupado(false)
    }
  }

  async function recuperar(evento) {
    evento.preventDefault()
    if (!/^\d+$/.test(numero) || Number(numero) <= 0) {
      setError('Ingresa un número de solicitud válido.')
      return
    }
    setOcupado(true)
    setError('')
    try {
      const pendiente = await obtenerSolicitud(numero)
      setSolicitud(pendiente)
      setDocumento(null)
      window.localStorage.setItem(claveSolicitud(sesion.codigoContribuyente), String(pendiente.idSolicitud))
    } catch (err) {
      setError(err.message)
    } finally {
      setOcupado(false)
    }
  }

  async function prepararDocumento() {
    setOcupado(true)
    setError('')
    try {
      setDocumento(await obtenerDocumento(solicitud.idSolicitud))
    } catch (err) {
      setError(err.message)
    } finally {
      setOcupado(false)
    }
  }

  function descargarDocumento() {
    const contenido = decodificarBase64(documento.contenidoBase64)
    const url = URL.createObjectURL(new Blob([contenido], { type: documento.tipoContenido }))
    const enlace = document.createElement('a')
    enlace.href = url
    enlace.download = documento.nombreArchivo
    document.body.append(enlace)
    enlace.click()
    enlace.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  async function enviarFirma(firmaBase64, nombreCertificado) {
    setOcupado(true)
    setError('')
    try {
      const firmada = await firmarSolicitud(solicitud.idSolicitud, firmaBase64, nombreCertificado)
      setSolicitud(firmada)
      setDocumento(null)
      setArchivoFirma(null)
      setCertificado('')
    } catch (err) {
      setError(err.message)
    } finally {
      setOcupado(false)
    }
  }

  async function firmarConArchivo(evento) {
    evento.preventDefault()
    if (!certificado.trim()) {
      setError('Ingresa la información del certificado.')
      return
    }
    try {
      const firmaBase64 = await codificarArchivo(archivoFirma)
      await enviarFirma(firmaBase64, certificado.trim())
    } catch (err) {
      setError(err.message)
    }
  }

  async function firmarDePrueba() {
    const firma = btoa(`FIRMA-DE-PRUEBA:${documento.hashSha256}`)
    await enviarFirma(firma, 'CERTIFICADO DE PRUEBA')
  }

  const pendienteFirma = solicitud?.estado === 'INICIADA'
  const solicitudCerrada = solicitud && ['EMITIDA', 'RECHAZADA'].includes(solicitud.estado)
  const vistaPrevia = documento?.tipoContenido?.startsWith('text/')
    ? new TextDecoder().decode(decodificarBase64(documento.contenidoBase64)) : null

  return (
    <div className="constancia" aria-busy={cargando || ocupado}>
      <section className="pcard constancia-intro">
        <div>
          <p className="constancia-etiqueta">Portal del contribuyente · HU015</p>
          <h2>Firma tu solicitud de Constancia de No Adeudo</h2>
          <p className="hint">Inicia la solicitud, revisa el documento que genera el servidor y envía tu firma.</p>
        </div>
        <div className="constancia-pasos" aria-label="Pasos del trámite">
          <span>1. Solicitud</span><span>2. Documento</span><span>3. Firma</span>
        </div>
      </section>

      {cargando && <p className="hint" role="status">Comprobando la sesión…</p>}
      {sinSesion && <div className="alert" role="alert">Inicia sesión con tu cuenta de contribuyente para continuar. <Link to="/login">Ir al inicio de sesión</Link>.</div>}
      {error && <div className="alert" role="alert">{error}</div>}

      {sesion && !cargando && (
        <>
          <section className="pcard">
            <h2>Tu solicitud</h2>
            <p className="hint">Contribuyente: <strong>{sesion.codigoContribuyente}</strong>. Solo puedes consultar y firmar solicitudes de tu cuenta.</p>
            {(!solicitud || solicitudCerrada) && <button className="btn primary" type="button" onClick={nuevaSolicitud} disabled={ocupado}>{ocupado ? 'Creando…' : 'Iniciar nueva solicitud'}</button>}
            {solicitud && (
              <div className="constancia-resumen">
                <div><span>Número</span><strong>{solicitud.idSolicitud}</strong></div>
                <div><span>Estado</span><strong>{solicitud.estado.replaceAll('_', ' ')}</strong></div>
                <div><span>Fecha</span><strong>{new Date(solicitud.fechaSolicitud).toLocaleString('es-PE')}</strong></div>
              </div>
            )}
            <form className="constancia-recuperar" onSubmit={recuperar}>
              <label htmlFor="numero-constancia">¿Ya tienes una solicitud? Ingresa su número</label>
              <div>
                <input id="numero-constancia" type="number" min="1" value={numero} onChange={(evento) => setNumero(evento.target.value)} disabled={ocupado} />
                <button className="btn" type="submit" disabled={ocupado}>Consultar</button>
              </div>
            </form>
          </section>

          {solicitud && pendienteFirma && (
            <section className="pcard">
              <h2>Documento para firmar</h2>
              <p className="hint">Descarga y revisa el documento antes de enviar la firma. El servidor verificará la firma contra esta versión.</p>
              {!documento && <button className="btn primary" type="button" onClick={prepararDocumento} disabled={ocupado}>Preparar documento</button>}
              {documento && (
                <>
                  {documento.documentoDePrueba && <div className="alert" role="note">Documento de prueba. La firma simulada no tiene validez legal.</div>}
                  <div className="constancia-archivo">
                    <div><strong>{documento.nombreArchivo}</strong><span>SHA-256: {documento.hashSha256}</span></div>
                    <button className="btn" type="button" onClick={descargarDocumento}>Descargar</button>
                  </div>
                  {vistaPrevia && <pre className="constancia-vista">{vistaPrevia}</pre>}
                  {documento.documentoDePrueba && <button className="btn primary" type="button" onClick={firmarDePrueba} disabled={ocupado}>Firmar en modo de prueba</button>}
                  <form className="constancia-firma" onSubmit={firmarConArchivo}>
                    <h3>Enviar una firma y certificado</h3>
                    <p className="hint">Si cuentas con una firma generada para este documento, adjunta el archivo de firma y la información del certificado.</p>
                    <div className="fld">
                      <label htmlFor="archivo-firma">Archivo de firma (máximo 48 KB)</label>
                      <input id="archivo-firma" type="file" accept=".p7s,.sig,.bin" onChange={(evento) => setArchivoFirma(evento.target.files?.[0] || null)} disabled={ocupado} required />
                    </div>
                    <div className="fld">
                      <label htmlFor="certificado-firma">Certificado</label>
                      <textarea id="certificado-firma" value={certificado} onChange={(evento) => setCertificado(evento.target.value)} maxLength={16384} disabled={ocupado} required />
                    </div>
                    <button className="btn" type="submit" disabled={ocupado}>Enviar firma</button>
                  </form>
                </>
              )}
            </section>
          )}

          {solicitud && !pendienteFirma && (
            <section className="pcard">
              <h2>Solicitud registrada</h2>
              <div className="confirmacion" role="status">La solicitud N.º {solicitud.idSolicitud} está en estado {solicitud.estado.replaceAll('_', ' ')}.</div>
              {solicitud.firmaContribuyente && <p className="hint">Firma registrada el {new Date(solicitud.firmaContribuyente.fechaFirma).toLocaleString('es-PE')}.</p>}
              <p className="hint">Guarda el número de solicitud para consultar su avance. La emisión de la constancia depende de los pasos posteriores del trámite.</p>
            </section>
          )}
        </>
      )}
    </div>
  )
}
