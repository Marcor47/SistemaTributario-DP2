import { useEffect, useMemo, useState } from 'react'
import ModalConfirmacion from './ModalConfirmacion.jsx'
import PaginacionTabla from './PaginacionTabla.jsx'
import { abrirVista, eliminarVista, exportarVista, guardarVista, listarVistas } from '../services/reporteService.js'
import { fechaHora } from '../utils/formato.js'

function descargar({ blob, nombre }) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  enlace.click()
  URL.revokeObjectURL(url)
}

export default function GestionVistasReporte({ tipo, configuracion, habilitado, onAbrir }) {
  const [vistas, setVistas] = useState([])
  const [nombre, setNombre] = useState('')
  const [estado, setEstado] = useState({ cargando: true, procesando: false, error: null, mensaje: '' })
  const [confirmacion, setConfirmacion] = useState(null)
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)

  useEffect(() => {
    const controlador = new AbortController()
    setEstado((actual) => ({ ...actual, cargando: true, error: null }))
    listarVistas({ signal: controlador.signal })
      .then((datos) => setVistas(datos.filter((vista) => vista.tipoReporte === tipo)))
      .catch((error) => { if (!controlador.signal.aborted) setEstado((actual) => ({ ...actual, error })) })
      .finally(() => { if (!controlador.signal.aborted) setEstado((actual) => ({ ...actual, cargando: false })) })
    return () => controlador.abort()
  }, [tipo])

  const totalPaginas = Math.max(1, Math.ceil(vistas.length / tamanio))
  const paginaActual = Math.min(pagina, totalPaginas)
  const visibles = useMemo(() => vistas.slice((paginaActual - 1) * tamanio, paginaActual * tamanio), [vistas, paginaActual, tamanio])

  async function ejecutarGuardado() {
    setEstado((actual) => ({ ...actual, procesando: true, error: null, mensaje: '' }))
    try {
      const guardada = await guardarVista(tipo, { ...configuracion, nombre: nombre.trim() })
      setVistas((actual) => [guardada, ...actual.filter((vista) => vista.idVista !== guardada.idVista)])
      setNombre('')
      setPagina(1)
      setConfirmacion(null)
      setEstado((actual) => ({ ...actual, mensaje: 'La vista se guardó correctamente.' }))
    } catch (error) {
      setEstado((actual) => ({ ...actual, error }))
      setConfirmacion(null)
    } finally {
      setEstado((actual) => ({ ...actual, procesando: false }))
    }
  }

  function solicitarGuardado() {
    const limpio = nombre.trim()
    if (!limpio || !habilitado) return
    const existente = vistas.find((vista) => vista.nombre.toLocaleLowerCase() === limpio.toLocaleLowerCase())
    if (existente) setConfirmacion({ tipo: 'sobrescribir', vista: existente })
    else ejecutarGuardado()
  }

  async function utilizar(vista) {
    setEstado((actual) => ({ ...actual, procesando: true, error: null, mensaje: '' }))
    try {
      const datos = await abrirVista(vista.idVista)
      onAbrir(datos)
      setEstado((actual) => ({ ...actual, mensaje: `Vista “${vista.nombre}” aplicada.` }))
    } catch (error) {
      setEstado((actual) => ({ ...actual, error }))
    } finally {
      setEstado((actual) => ({ ...actual, procesando: false }))
    }
  }

  async function exportar(vista, formato) {
    setEstado((actual) => ({ ...actual, procesando: true, error: null, mensaje: '' }))
    try {
      descargar(await exportarVista(vista.idVista, formato))
      setEstado((actual) => ({ ...actual, mensaje: `Exportación ${formato === 'EXCEL' ? 'Excel' : 'PDF'} preparada.` }))
    } catch (error) {
      setEstado((actual) => ({ ...actual, error }))
    } finally {
      setEstado((actual) => ({ ...actual, procesando: false }))
    }
  }

  async function confirmar() {
    if (confirmacion?.tipo === 'sobrescribir') return ejecutarGuardado()
    if (confirmacion?.tipo !== 'eliminar') return
    setEstado((actual) => ({ ...actual, procesando: true, error: null, mensaje: '' }))
    try {
      await eliminarVista(confirmacion.vista.idVista)
      setVistas((actual) => actual.filter((vista) => vista.idVista !== confirmacion.vista.idVista))
      setConfirmacion(null)
      setEstado((actual) => ({ ...actual, mensaje: 'La vista se eliminó correctamente.' }))
    } catch (error) {
      setEstado((actual) => ({ ...actual, error }))
    } finally {
      setEstado((actual) => ({ ...actual, procesando: false }))
    }
  }

  return <section className="pcard reporte-vistas-gestion" aria-labelledby={`vistas-${tipo}`}>
    <div className="reporte-vistas-cabecera">
      <div><h3 id={`vistas-${tipo}`}>Vistas guardadas</h3><p className="hint">Guarda esta configuración para volver a utilizarla sin repetir los ajustes.</p></div>
      <div className="reporte-vista-nueva">
        <div className="fld"><label htmlFor={`nombre-vista-${tipo}`}>Nombre de la vista</label><input id={`nombre-vista-${tipo}`} maxLength="80" placeholder="Ej. Reporte mensual" value={nombre} onChange={(evento) => setNombre(evento.target.value)} /></div>
        <button className="btn" type="button" onClick={solicitarGuardado} disabled={!habilitado || !nombre.trim() || estado.procesando}>Guardar vista</button>
      </div>
    </div>
    {!habilitado && <p className="hint">Genera el reporte para habilitar el guardado de la vista.</p>}
    <div className="reporte-vistas-barra">
      <span className="hint">{estado.cargando ? 'Cargando vistas…' : `${vistas.length} vista${vistas.length === 1 ? '' : 's'}`}</span>
      {vistas.length > 0 && <PaginacionTabla id={`vistas-${tipo}`} totalRegistros={vistas.length} pagina={paginaActual} tamanio={tamanio} onCambiarPagina={setPagina} onCambiarTamanio={(valor) => { setTamanio(valor); setPagina(1) }} />}
    </div>
    {!estado.cargando && vistas.length === 0 && <p className="reporte-vistas-vacio">Aún no tienes vistas guardadas para este reporte.</p>}
    {visibles.length > 0 && <div className="tabla-scroll" tabIndex="0" role="region" aria-label="Vistas guardadas"><table className="tabla reporte-vistas-tabla"><caption className="sr-only">Vistas guardadas del reporte</caption><thead><tr><th scope="col">Nombre</th><th scope="col">Creada</th><th scope="col">Acciones</th></tr></thead><tbody>{visibles.map((vista) => <tr key={vista.idVista}><th scope="row">{vista.nombre}</th><td>{fechaHora(vista.fechaCreacion)}</td><td><div className="reporte-vistas-acciones"><button className="btn" type="button" onClick={() => utilizar(vista)} disabled={estado.procesando}>Utilizar</button><button className="btn" type="button" onClick={() => exportar(vista, 'EXCEL')} disabled={estado.procesando}>Excel</button><button className="btn" type="button" onClick={() => exportar(vista, 'PDF')} disabled={estado.procesando}>PDF</button><button className="btn peligro" type="button" onClick={() => setConfirmacion({ tipo: 'eliminar', vista })} disabled={estado.procesando}>Eliminar</button></div></td></tr>)}</tbody></table></div>}
    <div className="reporte-vistas-mensajes" aria-live="polite">{estado.mensaje && <p className="mensaje-exito">{estado.mensaje}</p>}{estado.error && <div className="alert" role="alert">{estado.error.message}</div>}</div>
    <ModalConfirmacion abierto={Boolean(confirmacion)} titulo={confirmacion?.tipo === 'eliminar' ? 'Eliminar vista' : 'Sobrescribir vista'} mensaje={confirmacion?.tipo === 'eliminar' ? <>¿Eliminar la vista <strong>{confirmacion?.vista?.nombre}</strong>?</> : <>Ya existe una vista con ese nombre. ¿Deseas reemplazar su configuración?</>} textoConfirmar={confirmacion?.tipo === 'eliminar' ? 'Eliminar' : 'Sobrescribir'} peligro={confirmacion?.tipo === 'eliminar'} procesando={estado.procesando} onConfirmar={confirmar} onCerrar={() => setConfirmacion(null)} />
  </section>
}
