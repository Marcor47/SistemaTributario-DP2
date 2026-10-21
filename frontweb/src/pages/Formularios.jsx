import { useEffect, useRef, useState } from 'react'
import BarraBusqueda from '../components/BarraBusqueda.jsx'
import { agregarFormulario, descargarFormulario, eliminarFormulario, obtenerFormularios } from '../services/formularioService.js'
import { fecha } from '../utils/formato.js'

export default function Formularios() {
  const [documentos, setDocumentos] = useState([])
  const [consulta, setConsulta] = useState('')
  const [ocupado, setOcupado] = useState(true)
  const [descargando, setDescargando] = useState(null)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [mostrarFormulario, setMostrarFormulario] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [eliminando, setEliminando] = useState(null)
  const [pendienteEliminar, setPendienteEliminar] = useState(null)
  const [errorFormulario, setErrorFormulario] = useState('')
  const solicitud = useRef(0)
  const formulario = useRef(null)
  const botonAgregar = useRef(null)
  const botonCancelarBorrado = useRef(null)
  const bloqueado = ocupado || guardando || eliminando !== null || descargando !== null

  useEffect(() => {
    let activo = true
    obtenerFormularios()
      .then((resultado) => { if (activo) setDocumentos(resultado) })
      .catch(() => { if (activo) setError('No se pudo cargar el catálogo. Intenta nuevamente con Actualizar catálogo.') })
      .finally(() => { if (activo) setOcupado(false) })
    return () => { activo = false }
  }, [])

  useEffect(() => {
    if (mostrarFormulario) formulario.current?.querySelector('input')?.focus()
  }, [mostrarFormulario])

  useEffect(() => {
    if (pendienteEliminar) botonCancelarBorrado.current?.focus()
  }, [pendienteEliminar])

  async function buscar(texto) {
    const actual = ++solicitud.current
    setConsulta(texto.trim())
    setOcupado(true)
    setError('')
    setAviso('')
    setPendienteEliminar(null)
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

  function cerrarFormulario() {
    setMostrarFormulario(false)
    setErrorFormulario('')
    requestAnimationFrame(() => botonAgregar.current?.focus())
  }

  async function guardar(evento) {
    evento.preventDefault()
    const datos = new FormData(evento.currentTarget)
    setGuardando(true)
    setErrorFormulario('')
    setError('')
    setAviso('')
    try {
      const nuevo = await agregarFormulario({
        nombre: datos.get('nombre'),
        tipo: datos.get('tipo'),
        descripcion: datos.get('descripcion'),
        archivo: datos.get('archivo'),
      })
      setAviso(`Se agregó ${nuevo.nombre} con el código ${nuevo.codigo}.${consulta ? ' Usa Limpiar para ver todo el catálogo.' : ''}`)
      try {
        setDocumentos(await obtenerFormularios(consulta))
      } catch {
        setError('El documento se guardó, pero no se pudo actualizar la lista. Usa Actualizar catálogo.')
      }
      cerrarFormulario()
    } catch (err) {
      setErrorFormulario(err.message || 'No se pudo agregar el documento. Intenta nuevamente.')
    } finally {
      setGuardando(false)
    }
  }

  async function borrar() {
    if (!pendienteEliminar) return
    const documento = pendienteEliminar
    setEliminando(documento.codigo)
    setError('')
    setAviso('')
    try {
      await eliminarFormulario(documento.codigo)
      setDocumentos((actuales) => actuales.filter((item) => item.codigo !== documento.codigo))
      setPendienteEliminar(null)
      setAviso(`Se eliminó ${documento.nombre} del catálogo local.`)
      requestAnimationFrame(() => botonAgregar.current?.focus())
    } catch (err) {
      setError(err.message || 'No se pudo eliminar el documento. Intenta nuevamente.')
    } finally {
      setEliminando(null)
    }
  }

  return (
    <div aria-busy={ocupado || guardando || eliminando !== null}>
      <section className="pcard">
        <h2>Formularios y plantillas oficiales</h2>
        <p className="hint">Descarga los documentos en Word o agrega una nueva plantilla para los trámites municipales.</p>
      </section>
      <BarraBusqueda etiqueta="Buscar formulario o plantilla" placeholder="Nombre, código o tipo de documento" onBuscar={buscar} onLimpiar={() => buscar('')} ocupado={bloqueado} />
      <div className="info">Los documentos de ejemplo son Word editables marcados como DOCUMENTO DE PRUEBA. Las plantillas que agregues o elimines se guardan solo en este navegador.</div>

      {error && <div className="alert" role="alert">{error}</div>}
      {aviso && <div className="info" role="status">{aviso}</div>}
      {ocupado && <p className="hint mensaje-carga" role="status">Cargando formularios y plantillas…</p>}

      {mostrarFormulario && (
        <section className="pcard" id="nueva-plantilla" aria-labelledby="titulo-nueva-plantilla">
          <h2 id="titulo-nueva-plantilla">Agregar plantilla</h2>
          <p className="hint">Selecciona un archivo Word (.docx) de hasta 10 MB. El código y la versión inicial se asignan automáticamente.</p>
          <form className="formulario-plantilla" ref={formulario} onSubmit={guardar}>
            <fieldset className="pfields" disabled={bloqueado}>
              <legend className="sr-only">Datos del documento</legend>
              <div className="fld">
                <label htmlFor="plantilla-nombre">Nombre del documento</label>
                <input id="plantilla-nombre" name="nombre" required maxLength={120} />
              </div>
              <div className="fld">
                <label htmlFor="plantilla-tipo">Tipo de documento</label>
                <select id="plantilla-tipo" name="tipo" defaultValue="Plantilla">
                  <option value="Plantilla">Plantilla</option>
                  <option value="Formulario">Formulario</option>
                </select>
              </div>
              <div className="fld campo-completo">
                <label htmlFor="plantilla-descripcion">Descripción (opcional)</label>
                <textarea id="plantilla-descripcion" name="descripcion" rows={2} maxLength={300} />
              </div>
              <div className="fld campo-completo">
                <label htmlFor="plantilla-archivo">Archivo Word</label>
                <input id="plantilla-archivo" name="archivo" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" required aria-describedby="plantilla-archivo-ayuda" />
                <span id="plantilla-archivo-ayuda" className="hint">Formato .docx · Tamaño máximo: 10 MB</span>
              </div>
            </fieldset>
            {errorFormulario && <div className="alert" role="alert">{errorFormulario}</div>}
            <div className="acciones-documento">
              <button className="btn primary" type="submit" disabled={bloqueado}>{guardando ? 'Guardando…' : 'Guardar plantilla'}</button>
              <button className="btn" type="button" onClick={cerrarFormulario} disabled={bloqueado}>Cancelar</button>
            </div>
          </form>
        </section>
      )}

      <section className="pcard" aria-label="Catálogo de documentos">
        <div className="catalogo-cabecera">
          <div>
            <h2>Documentos vigentes</h2>
            <p className="hint" role="status">{ocupado ? 'Consultando catálogo…' : `${documentos.length} ${documentos.length === 1 ? 'documento disponible' : 'documentos disponibles'}${consulta ? ` para «${consulta}»` : ''}.`}</p>
          </div>
          <div className="acciones-documento">
            <button className="btn" type="button" onClick={() => buscar(consulta)} disabled={bloqueado}>Actualizar catálogo</button>
            <button className="btn primary" type="button" ref={botonAgregar} aria-expanded={mostrarFormulario} aria-controls="nueva-plantilla" disabled={bloqueado || mostrarFormulario} onClick={() => { setMostrarFormulario(true); setPendienteEliminar(null); setErrorFormulario(''); setAviso('') }}>Agregar plantilla</button>
          </div>
        </div>

        {pendienteEliminar && (
          <div className="alert" role="group" aria-labelledby="confirmar-eliminacion">
            <p id="confirmar-eliminacion">¿Eliminar <strong>{pendienteEliminar.nombre}</strong>? Dejará de estar disponible en el catálogo de este navegador.</p>
            <div className="acciones-documento">
              <button className="btn peligro" type="button" onClick={borrar} disabled={bloqueado}>{eliminando ? 'Eliminando…' : 'Sí, eliminar'}</button>
              <button className="btn" type="button" ref={botonCancelarBorrado} onClick={() => setPendienteEliminar(null)} disabled={bloqueado}>Cancelar</button>
            </div>
          </div>
        )}

        {!ocupado && !error && documentos.length === 0 && <div className="info">{consulta ? `No se encontraron documentos para «${consulta}». Prueba con otro nombre o usa Limpiar para ver todos.` : 'No hay documentos guardados. Usa Agregar plantilla para añadir uno.'}</div>}
        {!ocupado && documentos.length > 0 && (
          <div className="tabla-scroll" role="region" aria-label="Documentos vigentes" tabIndex={0}>
            <table className="tabla tabla-formularios">
              <caption className="sr-only">Formularios y plantillas con su versión vigente y fecha de publicación</caption>
              <thead><tr><th scope="col">Documento</th><th scope="col">Tipo</th><th scope="col">Versión</th><th scope="col">Publicación</th><th scope="col">Archivo</th><th scope="col">Acciones</th></tr></thead>
              <tbody>
                {documentos.map((documento) => (
                  <tr key={documento.codigo}>
                    <td><strong>{documento.nombre}</strong><span className="documento-descripcion">{documento.codigo}{documento.descripcion && ` · ${documento.descripcion}`}</span></td>
                    <td>{documento.tipo}</td><td><span className="estado ok">v{documento.version} · Vigente</span></td>
                    <td className="fecha-documento">{fecha(documento.fechaPublicacion)}</td><td>{documento.formato}</td>
                    <td>
                      <div className="acciones-documento">
                        <button className="btn" type="button" aria-label={`Descargar ${documento.nombre}, versión ${documento.version}`} disabled={bloqueado} onClick={() => descargar(documento)}>{descargando === documento.codigo ? 'Descargando…' : 'Descargar'}</button>
                        <button className="btn peligro" type="button" aria-label={`Eliminar ${documento.nombre}`} disabled={bloqueado} onClick={() => { setPendienteEliminar(documento); setError(''); setAviso('') }}>Eliminar</button>
                      </div>
                    </td>
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
