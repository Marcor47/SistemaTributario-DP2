import { useEffect, useRef, useState } from 'react'
import BarraBusqueda from '../components/BarraBusqueda.jsx'
import { descargarFormulario, obtenerFormularios } from '../services/formularioService.js'
import { fecha } from '../utils/formato.js'

export default function Formularios() {
  const [documentos, setDocumentos] = useState([])
  const [consulta, setConsulta] = useState('')
  const [ocupado, setOcupado] = useState(true)
  const [descargando, setDescargando] = useState(null)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const solicitud = useRef(0)

  useEffect(() => {
    let activo = true
    obtenerFormularios()
      .then((resultado) => { if (activo) setDocumentos(resultado) })
      .catch(() => { if (activo) setError('No se pudo cargar el catálogo. Intenta nuevamente con Actualizar catálogo.') })
      .finally(() => { if (activo) setOcupado(false) })
    return () => { activo = false }
  }, [])

  async function buscar(texto) {
    const actual = ++solicitud.current
    setConsulta(texto.trim())
    setOcupado(true)
    setError('')
    setAviso('')
    try {
      const resultado = await obtenerFormularios(texto)
      if (actual === solicitud.current) setDocumentos(resultado)
    } catch {
      if (actual === solicitud.current) setError('No se pudo cargar el catálogo. Intenta nuevamente con Actualizar catálogo.')
    } finally {
      if (actual === solicitud.current) setOcupado(false)
    }
  }

  async function descargar(documento) {
    setDescargando(documento.codigo)
    setError('')
    setAviso('')
    try {
      const { archivo, nombre } = await descargarFormulario(documento.codigo, documento.version)
      const url = URL.createObjectURL(archivo)
      const enlace = document.createElement('a')
      enlace.href = url
      enlace.download = nombre
      document.body.append(enlace)
      enlace.click()
      enlace.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      setAviso(`Se inició la descarga de ${documento.nombre}, versión ${documento.version}.`)
    } catch (err) {
      setError(err.message || 'No se pudo descargar el documento. Intenta nuevamente.')
    } finally {
      setDescargando(null)
    }
  }

  return (
    <div aria-busy={ocupado}>
      <section className="pcard">
        <h2>Formularios y plantillas oficiales</h2>
        <p className="hint">Consulta y descarga la versión vigente de los documentos para los trámites municipales.</p>
      </section>
      <BarraBusqueda etiqueta="Buscar formulario o plantilla" placeholder="Nombre, código o tipo de documento" onBuscar={buscar} onLimpiar={() => buscar('')} ocupado={ocupado} />
      <div className="info">El catálogo muestra la misma versión vigente para todos los usuarios. Los archivos de esta demostración están marcados como DOCUMENTO DE PRUEBA.</div>

      {error && <div className="alert" role="alert">{error}</div>}
      {aviso && <div className="info" role="status">{aviso}</div>}
      {ocupado && <p className="hint mensaje-carga" role="status">Cargando formularios y plantillas…</p>}

      <section className="pcard" aria-label="Catálogo de documentos">
        <div className="catalogo-cabecera">
          <div>
            <h2>Documentos vigentes</h2>
            <p className="hint" role="status">{ocupado ? 'Consultando catálogo…' : `${documentos.length} ${documentos.length === 1 ? 'documento disponible' : 'documentos disponibles'}${consulta ? ` para «${consulta}»` : ''}.`}</p>
          </div>
          <button className="btn" type="button" onClick={() => buscar(consulta)} disabled={ocupado || descargando !== null}>Actualizar catálogo</button>
        </div>

        {!ocupado && !error && documentos.length === 0 && <div className="info">No se encontraron documentos para «{consulta}». Prueba con otro nombre o usa Limpiar para ver todos.</div>}
        {!ocupado && documentos.length > 0 && (
          <div className="tabla-scroll" role="region" aria-label="Documentos vigentes" tabIndex={0}>
            <table className="tabla tabla-formularios">
              <caption className="sr-only">Formularios y plantillas con su versión vigente y fecha de publicación</caption>
              <thead><tr><th scope="col">Documento</th><th scope="col">Tipo</th><th scope="col">Versión</th><th scope="col">Publicación</th><th scope="col">Archivo</th><th scope="col">Acción</th></tr></thead>
              <tbody>
                {documentos.map((documento) => (
                  <tr key={documento.codigo}>
                    <td><strong>{documento.nombre}</strong><span className="documento-descripcion">{documento.codigo} · {documento.descripcion}</span></td>
                    <td>{documento.tipo}</td><td><span className="estado ok">v{documento.version} · Vigente</span></td>
                    <td className="fecha-documento">{fecha(documento.fechaPublicacion)}</td><td>{documento.formato}</td>
                    <td><button className="btn" type="button" aria-label={`Descargar ${documento.nombre}, versión ${documento.version}`} disabled={descargando !== null} onClick={() => descargar(documento)}>{descargando === documento.codigo ? 'Descargando…' : 'Descargar'}</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
