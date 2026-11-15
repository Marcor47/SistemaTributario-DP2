import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  codificarArchivo, firmarSolicitud, iniciarSolicitud,
  obtenerDocumento, obtenerSesion, obtenerSolicitud,
} from '../services/constanciaService.js'
import { guardarArchivo, pedirArchivo } from '../services/api.js'
import { descargarDocumentoBuzon, listarBuzon } from '../services/buzonService.js'
import './Constancia.css'
import { fechaHoraLima } from '../utils/formato.js'

// 5.4: las constancias emitidas llegan al buzón; aquí se listan y se descargan en PDF con la plantilla institucional.
function ConstanciasEmitidas() {
  const [estado, setEstado] = useState({ cargando: true, documentos: [], error: '' })
  const [descargando, setDescargando] = useState(null)

  useEffect(() => {
    let activo = true
    listarBuzon({ tipo: 'CONSTANCIA', tamanio: 20 })
      .then((datos) => { if (activo) setEstado({ cargando: false, documentos: datos.documentos ?? [], error: '' }) })
      .catch((err) => { if (activo) setEstado({ cargando: false, documentos: [], error: err.message || 'No se pudo consultar tus constancias.' }) })
    return () => { activo = false }
  }, [])

  async function descargar(documento) {
    setDescargando(documento.idDocumento)
    try {
      const blob = await descargarDocumentoBuzon(documento.idDocumento)
      guardarArchivo({ blob: new Blob([blob], { type: 'application/pdf' }), nombre: documento.archivo || `constancia-${documento.idDocumento}.pdf` })
    } catch (err) {
      setEstado((actual) => ({ ...actual, error: err.message || 'No se pudo descargar la constancia.' }))
    } finally {
      setDescargando(null)
    }
  }

  return (
    <section className="pcard" aria-labelledby="constancias-emitidas-titulo">
      <h2 id="constancias-emitidas-titulo">Mis constancias emitidas</h2>
      {estado.cargando && <p className="hint" role="status">Consultando tus constancias…</p>}
      {estado.error && <div className="alert" role="alert">{estado.error}</div>}
      {!estado.cargando && !estado.error && estado.documentos.length === 0 && (
        <p className="hint">Aún no tienes constancias emitidas. Cuando se emita una, podrás descargarla aquí y en tu buzón.</p>
      )}
      {estado.documentos.length > 0 && (
        <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Constancias emitidas">
          <table className="tabla">
            <caption className="sr-only">Constancias de No Adeudo emitidas</caption>
            <thead><tr><th scope="col">Constancia</th><th scope="col">Emitida el</th><th scope="col"><span className="sr-only">Acción</span></th></tr></thead>
            <tbody>
              {estado.documentos.map((documento) => (
                <tr key={documento.idDocumento}>
                  <th scope="row">{documento.titulo}</th>
                  <td>{fechaHoraLima(documento.fechaEnvio, { dateStyle: 'medium', timeStyle: 'short' })}</td>
                  <td>
                    <button className="btn" type="button" disabled={descargando !== null || !documento.urlDescarga} onClick={() => descargar(documento)}>
                      {descargando === documento.idDocumento ? 'Descargando…' : 'Descargar PDF'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

const claveSolicitud = (codigo) => `constancia-pendiente-${codigo}`
const nombrePdf = (idSolicitud) => `solicitud-constancia-${idSolicitud}.pdf`

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
  // PDF institucional generado por el servidor para la solicitud: se previsualiza y se descarga tal cual.
  const [pdfCargado, setPdfCargado] = useState({ id: null, archivo: null, url: '', error: '' })

  const idSolicitudDocumento = documento ? solicitud?.idSolicitud : null
  // Solo vale el PDF de la solicitud que se está mostrando; mientras llega el nuevo, se considera en carga.
  const pdf = idSolicitudDocumento && pdfCargado.id === idSolicitudDocumento
    ? { ...pdfCargado, cargando: false }
    : { archivo: null, url: '', error: '', cargando: Boolean(idSolicitudDocumento) }
  useEffect(() => {
    if (!idSolicitudDocumento) return undefined
    let activo = true
    let url = ''
    pedirArchivo(`/constancias/${idSolicitudDocumento}/documento-firma/pdf`, nombrePdf(idSolicitudDocumento))
      .then((archivo) => {
        if (!activo) return
        const blob = new Blob([archivo.blob], { type: 'application/pdf' })
        url = URL.createObjectURL(blob)
        setPdfCargado({ id: idSolicitudDocumento, archivo: { blob, nombre: archivo.nombre }, url, error: '' })
      })
      .catch((err) => {
        if (activo) setPdfCargado({ id: idSolicitudDocumento, archivo: null, url: '', error: err.message || 'No se pudo cargar la vista previa del documento.' })
      })
    return () => {
      activo = false
      if (url) URL.revokeObjectURL(url)
      setPdfCargado({ id: null, archivo: null, url: '', error: '' })
    }
  }, [idSolicitudDocumento])

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

  async function descargarDocumento() {
    setError('')
    try {
      guardarArchivo(pdf.archivo ?? await pedirArchivo(`/constancias/${solicitud.idSolicitud}/documento-firma/pdf`, nombrePdf(solicitud.idSolicitud)))
    } catch (err) {
      setError(err.message || 'No se pudo descargar el documento de la solicitud.')
    }
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

  async function firmarConServiciosEnLinea() {
    const firma = btoa(`FIRMA-SERVICIOS-EN-LINEA:${documento.hashSha256}`)
    await enviarFirma(firma, 'Servicios en Línea - Municipalidad Distrital de San Miguel')
  }

  const pendienteFirma = solicitud?.estado === 'INICIADA'
  const solicitudCerrada = solicitud && ['EMITIDA', 'RECHAZADA'].includes(solicitud.estado)

  return (
    <div className="constancia" aria-busy={cargando || ocupado}>
      <section className="pcard constancia-intro">
        <div>
          <p className="constancia-etiqueta">Servicios en Línea · Constancia de No Adeudo</p>
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
            {/* Una solicitud ya firmada no bloquea iniciar otra: el servidor decide si se permite (SOLICITUD_EN_CURSO). */}
            {(!solicitud || solicitudCerrada || !pendienteFirma) && <button className="btn primary" type="button" onClick={nuevaSolicitud} disabled={ocupado}>{ocupado ? 'Creando…' : 'Iniciar nueva solicitud'}</button>}
            {solicitud && (
              <div className="constancia-resumen">
                <div><span>Número</span><strong>{solicitud.idSolicitud}</strong></div>
                <div><span>Estado</span><strong>{solicitud.estado.replaceAll('_', ' ')}</strong></div>
                <div><span>Fecha</span><strong>{fechaHoraLima(solicitud.fechaSolicitud, { dateStyle: 'medium', timeStyle: 'short' })}</strong></div>
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
                  <div className="constancia-archivo">
                    <div className="constancia-archivo-info">
                      <span className="constancia-archivo-icono" aria-hidden="true">PDF</span>
                      <div>
                        <strong>{pdf.archivo?.nombre || nombrePdf(solicitud.idSolicitud)}</strong>
                        <span>Documento PDF · Código de verificación (SHA-256): {documento.hashSha256}</span>
                      </div>
                    </div>
                    <button className="btn" type="button" onClick={descargarDocumento}>Descargar PDF</button>
                  </div>
                  {pdf.cargando && <p className="hint" role="status">Cargando vista previa del documento…</p>}
                  {pdf.error && <div className="alert" role="alert">{pdf.error}</div>}
                  {pdf.url && (
                    <iframe className="constancia-vista" src={`${pdf.url}#toolbar=0&navpanes=0&view=FitH`} title={`Vista previa de ${pdf.archivo?.nombre || nombrePdf(solicitud.idSolicitud)}`} />
                  )}
                  {documento.firmaLocal && <button className="btn primary" type="button" onClick={firmarConServiciosEnLinea} disabled={ocupado}>Firmar con mi cuenta de Servicios en Línea</button>}
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
              {solicitud.firmaContribuyente && <p className="hint">Firma registrada el {fechaHoraLima(solicitud.firmaContribuyente.fechaFirma, { dateStyle: 'medium', timeStyle: 'short' })}.</p>}
              <p className="hint">Guarda el número de solicitud para consultar su avance. La emisión de la constancia depende de los pasos posteriores del trámite.</p>
            </section>
          )}

          <ConstanciasEmitidas />
        </>
      )}
    </div>
  )
}
