import { useCallback, useEffect, useMemo, useState } from 'react'
import ModalConfirmacion from '../components/ModalConfirmacion.jsx'
import {
  crearRestriccionReporte,
  eliminarRestriccionReporte,
  listarRestriccionesReportes,
  obtenerOpcionesRestricciones,
} from '../services/accesoReportesService.js'
import { etiquetaRol } from '../utils/roles.js'
import './ConfiguracionAccesoReportes.css'

// HU045 (RF-060): el administrador define qué reportes no puede ver cada rol, completos o por zona. El backend aplica
// estas restricciones al consultar y exportar la plataforma analítica y los listados.
const REPORTES = {
  MOROSOS: 'Morosos',
  PRICOS: 'Principales contribuyentes (PRICOS)',
  BUENOS_PAGADORES: 'Buenos pagadores',
}
const TIPOS = {
  REPORTE: 'Ocultar el reporte completo',
  ZONA: 'Ocultar una zona',
}
const FORMULARIO_INICIAL = { rol: '', tipoReporte: 'MOROSOS', dimension: 'REPORTE', valor: '' }

export default function ConfiguracionAccesoReportes() {
  const [restricciones, setRestricciones] = useState([])
  const [opciones, setOpciones] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [formulario, setFormulario] = useState(FORMULARIO_INICIAL)
  const [guardando, setGuardando] = useState(false)
  const [aviso, setAviso] = useState(null)
  const [porEliminar, setPorEliminar] = useState(null)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controlador = new AbortController()
    Promise.all([
      listarRestriccionesReportes({ signal: controlador.signal }),
      obtenerOpcionesRestricciones({ signal: controlador.signal }),
    ]).then(([lista, catalogo]) => {
      if (controlador.signal.aborted) return
      setRestricciones(lista)
      setOpciones(catalogo)
      setFormulario((actual) => ({ ...actual, rol: actual.rol || catalogo.roles.find((r) => r !== 'ADMINISTRADOR') || '' }))
      setError('')
    }).catch((err) => {
      if (!controlador.signal.aborted) setError(err.message || 'No se pudo consultar la configuración de acceso.')
    }).finally(() => {
      if (!controlador.signal.aborted) setCargando(false)
    })
    return () => controlador.abort()
  }, [revision])

  const ordenadas = useMemo(() => [...restricciones].sort((a, b) => etiquetaRol(a.rol).localeCompare(etiquetaRol(b.rol))
    || a.tipoReporte.localeCompare(b.tipoReporte) || String(a.valor ?? '').localeCompare(String(b.valor ?? ''))), [restricciones])

  const cambiar = useCallback((campo, valor) => {
    setAviso(null)
    setFormulario((actual) => ({ ...actual, [campo]: valor, ...(campo === 'dimension' && valor === 'REPORTE' ? { valor: '' } : {}) }))
  }, [])

  async function agregar(evento) {
    evento.preventDefault()
    if (formulario.dimension === 'ZONA' && !formulario.valor) {
      setAviso({ tipo: 'error', texto: 'Elige la zona que se ocultará.' })
      return
    }
    setGuardando(true)
    setAviso(null)
    try {
      const nueva = await crearRestriccionReporte(formulario)
      setRestricciones((actual) => [...actual, nueva])
      setAviso({ tipo: 'exito', texto: `Se restringió ${REPORTES[nueva.tipoReporte]} para ${etiquetaRol(nueva.rol)}.` })
      setFormulario((actual) => ({ ...actual, valor: '' }))
    } catch (err) {
      setAviso({ tipo: 'error', texto: err.message || 'No se pudo guardar la restricción.' })
    } finally {
      setGuardando(false)
    }
  }

  async function confirmarEliminar() {
    const restriccion = porEliminar
    setPorEliminar(null)
    try {
      await eliminarRestriccionReporte(restriccion.id)
      setRestricciones((actual) => actual.filter((r) => r.id !== restriccion.id))
      setAviso({ tipo: 'exito', texto: 'Se quitó la restricción.' })
    } catch (err) {
      setAviso({ tipo: 'error', texto: err.message || 'No se pudo quitar la restricción.' })
    }
  }

  if (cargando) return <p className="mensaje-carga" role="status">Consultando la configuración de acceso…</p>
  if (error) {
    return (
      <section className="pcard" role="alert">
        <h2>No se pudo consultar la configuración de acceso</h2>
        <p>{error}</p>
        <button className="btn" type="button" onClick={() => { setCargando(true); setRevision((v) => v + 1) }}>Reintentar</button>
      </section>
    )
  }

  return (
    <div className="acceso-reportes">
      <section className="pcard" aria-labelledby="acceso-nueva-titulo">
        <h2 id="acceso-nueva-titulo">Nueva restricción</h2>
        <p className="hint">
          Por defecto, cada rol con permiso de reportes ve toda la información. Agrega una restricción para ocultarle un
          reporte completo o los contribuyentes de una zona. Se aplica en la plataforma analítica, los listados y las
          exportaciones.
        </p>
        <form className="filters acceso-formulario" onSubmit={agregar}>
          <div className="fld">
            <label htmlFor="acceso-rol">Rol</label>
            <select id="acceso-rol" value={formulario.rol} onChange={(e) => cambiar('rol', e.target.value)} required>
              {opciones.roles.filter((r) => r !== 'ADMINISTRADOR').map((rol) => <option key={rol} value={rol}>{etiquetaRol(rol)}</option>)}
            </select>
          </div>
          <div className="fld">
            <label htmlFor="acceso-reporte">Reporte</label>
            <select id="acceso-reporte" value={formulario.tipoReporte} onChange={(e) => cambiar('tipoReporte', e.target.value)}>
              {opciones.reportes.map((r) => <option key={r} value={r}>{REPORTES[r] || r}</option>)}
            </select>
          </div>
          <div className="fld">
            <label htmlFor="acceso-tipo">Restricción</label>
            <select id="acceso-tipo" value={formulario.dimension} onChange={(e) => cambiar('dimension', e.target.value)}>
              {opciones.dimensiones.map((d) => <option key={d} value={d}>{TIPOS[d] || d}</option>)}
            </select>
          </div>
          {formulario.dimension === 'ZONA' && (
            <div className="fld">
              <label htmlFor="acceso-zona">Zona</label>
              <select id="acceso-zona" value={formulario.valor} onChange={(e) => cambiar('valor', e.target.value)} required>
                <option value="">Elige la zona</option>
                {opciones.zonas.map((z) => <option key={z} value={z}>{z}</option>)}
              </select>
            </div>
          )}
          <button className="btn primary" type="submit" disabled={guardando}>{guardando ? 'Guardando…' : 'Agregar restricción'}</button>
        </form>
        {aviso && <p className={aviso.tipo === 'error' ? 'alert' : 'confirmacion'} role={aviso.tipo === 'error' ? 'alert' : 'status'}>{aviso.texto}</p>}
      </section>

      <section className="pcard" aria-labelledby="acceso-vigentes-titulo">
        <h2 id="acceso-vigentes-titulo">Restricciones vigentes</h2>
        {ordenadas.length === 0 ? (
          <p className="hint">No hay restricciones: todos los roles con permiso de reportes ven toda la información.</p>
        ) : (
          <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Restricciones vigentes">
            <table className="tabla">
              <caption className="sr-only">Restricciones de acceso a reportes por rol</caption>
              <thead>
                <tr>
                  <th scope="col">Rol</th>
                  <th scope="col">Reporte</th>
                  <th scope="col">Restricción</th>
                  <th scope="col">Zona</th>
                  <th scope="col"><span className="sr-only">Acción</span></th>
                </tr>
              </thead>
              <tbody>
                {ordenadas.map((r) => (
                  <tr key={r.id}>
                    <th scope="row">{etiquetaRol(r.rol)}</th>
                    <td>{REPORTES[r.tipoReporte] || r.tipoReporte}</td>
                    <td>{TIPOS[r.dimension] || r.dimension}</td>
                    <td>{r.valor || '—'}</td>
                    <td><button className="btn" type="button" onClick={() => setPorEliminar(r)}>Quitar</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <ModalConfirmacion
        abierto={Boolean(porEliminar)}
        titulo="Quitar restricción"
        mensaje={porEliminar ? `${etiquetaRol(porEliminar.rol)} volverá a ver ${REPORTES[porEliminar.tipoReporte]}${porEliminar.valor ? ` de la zona ${porEliminar.valor}` : ''}.` : ''}
        textoConfirmar="Quitar"
        peligro
        onConfirmar={confirmarEliminar}
        onCerrar={() => setPorEliminar(null)}
      />
    </div>
  )
}
