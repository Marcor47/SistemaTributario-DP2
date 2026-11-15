import { useCallback, useEffect, useMemo, useState } from 'react'
import SelectorMultiple from '../components/SelectorMultiple.jsx'
import {
  guardarRestriccionesReportes,
  listarRestriccionesReportes,
  obtenerOpcionesRestricciones,
} from '../services/accesoReportesService.js'
import { etiquetaRol } from '../utils/roles.js'
import './ConfiguracionAccesoReportes.css'

// HU045 (RF-060): el administrador define qué información se oculta por rol. Sin regla, el rol ve todo.
// La configuración es una denylist: reporte completo, zonas y segmentos de la última segmentación publicada.
const REPORTES = {
  MOROSOS: {
    titulo: 'Morosos',
    descripcion: 'Contribuyentes con cuotas vencidas impagas a la fecha de corte.',
  },
  PRICOS: {
    titulo: 'Principales contribuyentes (PRICOS)',
    descripcion: 'Contribuyentes con mayor emisión o deuda a la fecha de corte.',
  },
  BUENOS_PAGADORES: {
    titulo: 'Buenos pagadores',
    descripcion: 'Contribuyentes que pagaron a tiempo todas sus cuotas vencidas del año.',
  },
}

function configuracionVacia(roles, reportes) {
  return Object.fromEntries(roles.map((rol) => [rol, Object.fromEntries(
    reportes.map((reporte) => [reporte, { oculto: false, zonas: [], segmentos: [] }]),
  )]))
}

function configuracionDesdeReglas(roles, reportes, reglas) {
  const config = configuracionVacia(roles, reportes)
  for (const regla of reglas) {
    const porRol = config[regla.rol]
    const item = porRol?.[regla.tipoReporte]
    if (!item) continue
    if (regla.dimension === 'REPORTE') item.oculto = true
    if (regla.dimension === 'ZONA' && regla.valor) item.zonas.push(regla.valor)
    if (regla.dimension === 'SEGMENTO' && regla.valor) item.segmentos.push(regla.valor)
  }
  return config
}

function reglasDeConfiguracion(config, roles) {
  const salida = {}
  for (const rol of roles) {
    const reglas = []
    for (const [reporte, item] of Object.entries(config[rol] ?? {})) {
      if (item.oculto) reglas.push({ tipoReporte: reporte, dimension: 'REPORTE', valor: null })
      for (const zona of item.zonas) reglas.push({ tipoReporte: reporte, dimension: 'ZONA', valor: zona })
      for (const segmento of item.segmentos) {
        reglas.push({ tipoReporte: reporte, dimension: 'SEGMENTO', valor: segmento })
      }
    }
    salida[rol] = reglas
  }
  return salida
}

export default function ConfiguracionAccesoReportes() {
  const [restricciones, setRestricciones] = useState([])
  const [opciones, setOpciones] = useState(null)
  const [config, setConfig] = useState(null)
  const [original, setOriginal] = useState(null)
  const [rolActivo, setRolActivo] = useState('')
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState(null)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controlador = new AbortController()
    Promise.all([
      listarRestriccionesReportes({ signal: controlador.signal }),
      obtenerOpcionesRestricciones({ signal: controlador.signal }),
    ]).then(([lista, catalogo]) => {
      if (controlador.signal.aborted) return
      const roles = catalogo.roles.filter((rol) => rol !== 'ADMINISTRADOR')
      const configuracion = configuracionDesdeReglas(roles, catalogo.reportes, lista)
      setRestricciones(lista)
      setOpciones({ ...catalogo, roles })
      setConfig(configuracion)
      setOriginal(JSON.parse(JSON.stringify(configuracion)))
      setRolActivo((actual) => (roles.includes(actual) ? actual : roles[0] ?? ''))
    }).catch((err) => {
      if (!controlador.signal.aborted) setError(err.message || 'No se pudo consultar la configuración de acceso.')
    }).finally(() => {
      if (!controlador.signal.aborted) setCargando(false)
    })
    return () => controlador.abort()
  }, [revision])

  const hayCambios = useMemo(
    () => Boolean(config && original && JSON.stringify(config) !== JSON.stringify(original)),
    [config, original],
  )

  const cambiar = useCallback((reporte, cambios) => {
    setAviso(null)
    setConfig((actual) => ({
      ...actual,
      [rolActivo]: {
        ...actual[rolActivo],
        [reporte]: { ...actual[rolActivo][reporte], ...cambios },
      },
    }))
  }, [rolActivo])

  const alternarReporte = (reporte) => {
    const item = config[rolActivo][reporte]
    cambiar(reporte, { oculto: !item.oculto })
  }

  function copiarDeRol(origen) {
    if (!origen || origen === rolActivo) return
    setConfig((actual) => ({
      ...actual,
      [rolActivo]: JSON.parse(JSON.stringify(actual[origen])),
    }))
    setAviso({ tipo: 'exito', texto: `Se copió la configuración de ${etiquetaRol(origen)}.` })
  }

  function restablecer() {
    setConfig(configuracionVacia(opciones.roles, opciones.reportes))
    setAviso({ tipo: 'exito', texto: 'Se quitaron todas las restricciones de la vista. Guarda para aplicarlas.' })
  }

  async function guardar() {
    if (!config || !hayCambios) return
    setGuardando(true)
    setAviso(null)
    try {
      await guardarRestriccionesReportes(reglasDeConfiguracion(config, opciones.roles))
      setAviso({ tipo: 'exito', texto: 'La configuración de acceso se guardó.' })
      setRevision((valor) => valor + 1)
    } catch (err) {
      setAviso({ tipo: 'error', texto: err.message || 'No se pudo guardar la configuración.' })
    } finally {
      setGuardando(false)
    }
  }

  if (cargando) return <p className="mensaje-carga" role="status">Consultando la configuración de acceso…</p>
  if (error) {
    return (
      <section className="pcard" role="alert">
        <h2>No se pudo consultar la configuración de acceso</h2>
        <p>{error}</p>
        <button className="btn" type="button" onClick={() => {
          setCargando(true)
          setRevision((valor) => valor + 1)
        }}>Reintentar</button>
      </section>
    )
  }
  if (!opciones || !config || !rolActivo) {
    return <p className="hint" role="status">No hay roles con acceso a reportes para configurar.</p>
  }

  const itemActivo = config[rolActivo]

  return (
    <div className="config-acceso-reportes">
      <section className="pcard acceso-reportes-cabecera">
        <div>
          <h2>Acceso y alcance de reportes</h2>
          <p className="hint">
            Por defecto, cada rol con permiso de reportes ve toda la información. Aquí puedes ocultar un reporte
            completo, zonas o segmentos de la última segmentación publicada. Los cambios se aplican al consultar,
            exportar y preguntar al asistente.
          </p>
        </div>
        <span className="estado">{restricciones.length} restricciones vigentes</span>
      </section>

      <div className="acceso-reportes-layout">
        <aside className="pcard acceso-reportes-roles" aria-label="Roles">
          <h2>Roles</h2>
          <div className="acceso-reportes-rol-lista">
            {opciones.roles.map((rol) => {
              const ocultos = Object.values(config[rol]).filter((item) => item.oculto).length
              const parciales = Object.values(config[rol]).filter((item) => !item.oculto
                && (item.zonas.length || item.segmentos.length)).length
              return (
                <button
                  key={rol}
                  type="button"
                  className="acceso-reportes-rol"
                  aria-pressed={rolActivo === rol}
                  onClick={() => setRolActivo(rol)}
                >
                  <strong>{etiquetaRol(rol)}</strong>
                  <small>
                    {ocultos ? `${ocultos} reporte(s) oculto(s)` : 'Sin reportes ocultos'}
                    {parciales ? ` · ${parciales} con alcance limitado` : ''}
                  </small>
                </button>
              )
            })}
          </div>
        </aside>

        <section className="acceso-reportes-contenido">
          <div className="pcard acceso-reportes-rol-cabecera">
            <div>
              <p className="acceso-reportes-etiqueta">Configurando</p>
              <h2>{etiquetaRol(rolActivo)}</h2>
              <p className="hint">Marca lo que este rol no debe ver. Lo no marcado queda disponible.</p>
            </div>
            <div className="acceso-reportes-acciones">
              <label className="fld" htmlFor="copiar-rol">
                <span className="pl">Copiar de otro rol</span>
                <select id="copiar-rol" defaultValue="" onChange={(evento) => copiarDeRol(evento.target.value)}>
                  <option value="">Elige un rol</option>
                  {opciones.roles.filter((rol) => rol !== rolActivo).map((rol) => (
                    <option key={rol} value={rol}>{etiquetaRol(rol)}</option>
                  ))}
                </select>
              </label>
              <button className="btn" type="button" onClick={restablecer}>Quitar todas</button>
            </div>
          </div>

          <div className="acceso-reportes-modulo">
            {opciones.reportes.map((reporte) => {
              const definicion = REPORTES[reporte] ?? { titulo: reporte, descripcion: '' }
              const item = itemActivo[reporte]
              return (
                <article className="acceso-reportes-reporte" key={reporte} aria-labelledby={`reporte-${reporte}`}>
                  <header className="acceso-reportes-reporte-cab">
                    <div className="acceso-reportes-reporte-encabezado">
                      <h3 id={`reporte-${reporte}`}>{definicion.titulo}</h3>
                      <p className="hint">{definicion.descripcion}</p>
                    </div>
                    <label className="acceso-reportes-toggle">
                      <input
                        type="checkbox"
                        checked={item.oculto}
                        onChange={() => alternarReporte(reporte)}
                      />
                      <span className="acceso-reportes-toggle__pista" aria-hidden="true">
                        <span className="acceso-reportes-toggle__bola" />
                      </span>
                      <span>{item.oculto ? 'Oculto' : 'Visible'}</span>
                    </label>
                  </header>

                  {item.oculto ? (
                    <p className="hint acceso-reportes-reporte-omitido">
                      El reporte completo no estará disponible para este rol.
                    </p>
                  ) : (
                    <div className="acceso-reportes-alcance-grid">
                      <div className="acceso-reportes-alcance-caja">
                        <SelectorMultiple
                          id={`zonas-${reporte}`}
                          etiqueta="Zonas ocultas"
                          descripcion="Vacío = todas las zonas. Marca las zonas que este rol no debe consultar."
                          valores={item.zonas}
                          opciones={opciones.zonas}
                          onCambiar={(zonas) => cambiar(reporte, { zonas })}
                          permitirPersonalizado={false}
                          placeholder="Buscar una zona…"
                          vacio="No hay zonas disponibles."
                        />
                      </div>
                      <div className="acceso-reportes-alcance-caja">
                        <SelectorMultiple
                          id={`segmentos-${reporte}`}
                          etiqueta="Segmentos ocultos"
                          descripcion="Vacío = todos los segmentos. Solo se ofrecen los de la última segmentación publicada."
                          valores={item.segmentos}
                          opciones={opciones.segmentos}
                          onCambiar={(segmentos) => cambiar(reporte, { segmentos })}
                          permitirPersonalizado={false}
                          placeholder="Buscar un segmento…"
                          vacio={opciones.segmentos.length
                            ? 'No hay segmentos disponibles.'
                            : 'No hay una segmentación publicada. Los segmentos no se pueden restringir todavía.'}
                        />
                      </div>
                    </div>
                  )}
                </article>
              )
            })}
          </div>

          <div className="info acceso-reportes-preview">
            <strong>Solo se muestran los roles con permiso de reportes.</strong>
            <p className="hint">
              La configuración aplica a los roles que pueden consultar la plataforma analítica. Los demás
              roles no tienen acceso a reportes y no necesitan reglas aquí.
            </p>
          </div>

          {aviso && (
            <p className={aviso.tipo === 'error' ? 'alert' : 'confirmacion'} role={aviso.tipo === 'error' ? 'alert' : 'status'}>
              {aviso.texto}
            </p>
          )}

          <div className="pcard acceso-reportes-guardar">
            <p className="acceso-reportes-estado">
              {hayCambios ? 'Hay cambios sin guardar.' : 'La configuración está guardada.'}
            </p>
            <div className="acceso-reportes-guardar-acciones">
              <button
                className="btn"
                type="button"
                onClick={() => setConfig(JSON.parse(JSON.stringify(original)))}
                disabled={!hayCambios || guardando}
              >
                Descartar cambios
              </button>
              <button className="btn primary" type="button" onClick={guardar} disabled={!hayCambios || guardando}>
                {guardando ? 'Guardando…' : 'Guardar configuración'}
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}
