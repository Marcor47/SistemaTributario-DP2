import { useEffect, useRef, useState } from 'react'
import AvisoAccesoReportes from '../components/AvisoAccesoReportes.jsx'
import ModalConfirmacion from '../components/ModalConfirmacion.jsx'
import SelectorColumnasAgrupadas from '../components/SelectorColumnasAgrupadas.jsx'
import SelectorMultiple from '../components/SelectorMultiple.jsx'
import { useSesionInterna } from '../context/sesionInterna.js'
import { listarRolesInternos } from '../services/authInternaService.js'
import {
  ACCESOS_REPORTES_SIMULADOS,
  guardarConfiguracionAccesoReportes,
  obtenerConfiguracionAccesoReportes,
  reiniciarConfiguracionAccesoReportes,
} from '../services/accesoReportesService.js'
import { listarSegmentosPublicados } from '../services/segmentosService.js'
import {
  MODULOS_ACCESO_REPORTES,
  ROLES_ACCESO_REPORTES,
  ROLES_CON_ACCESO_REPORTES_POR_DEFECTO,
  ZONAS_SUGERIDAS,
  actualizarReporte,
  aplicarConfiguracionATodosLosRoles,
  clonarConfiguracion,
  configuracionDeRol,
  copiarConfiguracionRol,
  marcarTodosLosReportes,
  opcionesSegmentosPublicados,
  reportesDelModulo,
  resumenDeRol,
} from '../utils/accesoReportes.js'
import { fechaHora } from '../utils/formato.js'
import { etiquetaRol, rolTienePermiso } from '../utils/roles.js'
import './Reportes.css'
import './ConfiguracionAccesoReportes.css'

const PERMISOS_REPORTES_POR_DEFECTO = 'REPORTES:CONSULTAR,REPORTES:EXPORTAR,SEGMENTOS:CONSULTAR'
const IDS_REPORTES = MODULOS_ACCESO_REPORTES.flatMap((modulo) => (
  reportesDelModulo(modulo.id).map((reporte) => reporte.id)
))

function ReporteConfig({ reporte, valor, arrastre, segmentosPublicados, expandido, onAlternar, onCambiar }) {
  const columnasSonTodas = reporte.columnasConfigurables
    .every((columna) => valor.columnas.includes(columna))

  function columnasConOpcionales(opcionales) {
    const seleccionadas = new Set(opcionales)
    return reporte.columnas.filter((columna) => (
      reporte.columnasFijas.includes(columna) || seleccionadas.has(columna)
    ))
  }

  function alternarColumna(columna) {
    const incluidas = new Set(valor.columnas)
    if (incluidas.has(columna)) incluidas.delete(columna)
    else incluidas.add(columna)
    onCambiar({
      columnas: columnasConOpcionales(
        reporte.columnasConfigurables.filter((item) => incluidas.has(item)),
      ),
    })
  }

  function alternarGrupo(claveGrupo) {
    const grupo = reporte.gruposConfigurables.find((item) => item.clave === claveGrupo)
    if (!grupo) return
    const incluidas = new Set(valor.columnas)
    const todas = grupo.columnas.every((columna) => incluidas.has(columna))
    grupo.columnas.forEach((columna) => {
      if (todas) incluidas.delete(columna)
      else incluidas.add(columna)
    })
    onCambiar({
      columnas: columnasConOpcionales(
        reporte.columnasConfigurables.filter((item) => incluidas.has(item)),
      ),
    })
  }

  const tituloId = `acceso-reporte-${reporte.id}`

  return (
    <article className="acceso-reportes-reporte" aria-labelledby={tituloId}>
      <header className="acceso-reportes-reporte-cab">
        <div className="acceso-reportes-reporte-encabezado">
          <h3 id={tituloId}>
            <button
              type="button"
              className="acceso-reportes-reporte-titulo"
              aria-expanded={expandido}
              aria-controls={`${tituloId}-contenido`}
              onClick={onAlternar}
            >
              <span>{reporte.titulo}</span>
              <svg
                className="acceso-reportes-reporte-chevron"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="m6 9 6 6 6-6" />
              </svg>
            </button>
          </h3>
          <p className="hint">{reporte.descripcion}</p>
        </div>
        <label className="acceso-reportes-toggle">
          <input
            type="checkbox"
            checked={valor.permitido}
            onChange={(evento) => onCambiar({ permitido: evento.target.checked })}
          />
          <span className="acceso-reportes-toggle__pista" aria-hidden="true">
            <span className="acceso-reportes-toggle__bola" />
          </span>
          <span>{valor.permitido ? 'Permitido' : 'No permitido'}</span>
        </label>
      </header>

      <div
        id={`${tituloId}-contenido`}
        className="acceso-reportes-reporte-contenido"
        hidden={!expandido}
      >
        {valor.permitido ? (
          <>
            <div className="acceso-reportes-alcance-grid">
              <div className="acceso-reportes-alcance-caja">
                <SelectorMultiple
                  id={`zonas-${reporte.id}`}
                  etiqueta="Zonas permitidas"
                  descripcion="Vacío = todas las zonas. Selecciona o escribe las zonas que puede consultar este rol."
                  valores={valor.zonas}
                  opciones={ZONAS_SUGERIDAS}
                  onCambiar={(zonas) => onCambiar({ zonas })}
                  placeholder="Buscar o agregar una zona…"
                  vacio="No hay zonas sugeridas. Escribe una para agregarla."
                />
              </div>
              <div className="acceso-reportes-alcance-caja">
                <SelectorMultiple
                  id={`segmentos-${reporte.id}`}
                  etiqueta="Segmentos permitidos"
                  descripcion="Vacío = todos los segmentos. Selecciona los publicados o escribe uno nuevo."
                  valores={valor.segmentos}
                  opciones={segmentosPublicados.opciones}
                  cargando={segmentosPublicados.cargando}
                  error={segmentosPublicados.error}
                  onCambiar={(segmentos) => onCambiar({ segmentos })}
                  placeholder="Buscar o escribir un segmento…"
                  vacio="No hay segmentos publicados. Escribe el nombre para agregarlo."
                />
              </div>
            </div>

          <fieldset className="reporte-columnas acceso-reportes-columnas">
            <legend>Columnas visibles</legend>
            <p className="hint">
              Las columnas esenciales siempre se muestran. Puedes ocultar las columnas opcionales que no necesite este rol.
            </p>

            <div className="acceso-reportes-columnas-fijas">
              <p className="acceso-reportes-etiqueta">Siempre visibles</p>
              <div className="acceso-reportes-columnas-fijas__lista">
                {reporte.columnasFijas.map((columna) => (
                  <span className="acceso-reportes-columna-fija" key={columna}>
                    {reporte.etiquetas[columna]}
                  </span>
                ))}
              </div>
              <p className="hint">
                Estas columnas identifican el reporte o contienen su dato principal; no se pueden ocultar.
              </p>
            </div>

            {reporte.columnasConfigurables.length > 0 ? (
              <>
                <p className="reporte-columnas-subtitulo">Columnas opcionales</p>
                <div className="reporte-columnas-grupo">
                  <SelectorColumnasAgrupadas
                    grupos={reporte.gruposConfigurables}
                    columnasVisibles={valor.columnas}
                    etiquetas={reporte.etiquetas}
                    arrastre={arrastre}
                    permitirArrastre={false}
                    onAlternar={alternarColumna}
                    onAlternarGrupo={alternarGrupo}
                    onReordenarGrupos={() => {}}
                    onReordenarColumnas={() => {}}
                    ariaLabel={`Columnas opcionales del ${reporte.titulo}`}
                  />
                </div>
                <button
                  className="btn reporte-btn-restablecer"
                  type="button"
                  onClick={() => onCambiar({ columnas: reporte.columnas })}
                  disabled={columnasSonTodas}
                >
                  Restablecer columnas
                </button>
              </>
            ) : (
              <p className="hint">Todas las columnas de este reporte son obligatorias.</p>
            )}
          </fieldset>
          </>
        ) : (
          <p className="hint acceso-reportes-reporte-omitido">Este reporte no estará disponible para el rol seleccionado.</p>
        )}
      </div>
    </article>
  )
}

export default function ConfiguracionAccesoReportes() {
  const { sesion } = useSesionInterna()
  const [config, setConfig] = useState(null)
  const [original, setOriginal] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [notificacion, setNotificacion] = useState(null)
  const [rolSeleccionado, setRolSeleccionado] = useState('')
  const [rolOrigen, setRolOrigen] = useState('')
  const [confirmacion, setConfirmacion] = useState(null)
  const [roles, setRoles] = useState({ cargando: true, datos: [], error: '' })
  const [segmentosPublicados, setSegmentosPublicados] = useState({ cargando: true, opciones: [], error: '' })
  const [reportesExpandidos, setReportesExpandidos] = useState({})
  const contadorNotificacion = useRef(0)
  const arrastreColumnas = useRef(false)

  const rolesConfigurables = roles.datos.filter((rol) => (
    ROLES_ACCESO_REPORTES.includes(rol.nombre) && rol.nombre !== 'ADMINISTRADOR'
  ))
  const nombresRolesConfigurables = rolesConfigurables.map((rol) => rol.nombre)
  const nombresConAccesoReal = roles.datos
    .filter((rol) => ROLES_ACCESO_REPORTES.includes(rol.nombre)
      && rolTienePermiso(rol, 'REPORTES:CONSULTAR'))
    .map((rol) => rol.nombre)
  const rolActual = rolesConfigurables.find((rol) => rol.nombre === rolSeleccionado) ?? null
  const rolActualTieneAccesoReal = rolActual ? rolTienePermiso(rolActual, 'REPORTES:CONSULTAR') : false
  const puedeExportarRol = rolActual ? rolTienePermiso(rolActual, 'REPORTES:EXPORTAR') : false
  const puedeVerSegmentosRol = rolActual ? rolTienePermiso(rolActual, 'SEGMENTOS:CONSULTAR') : false
  const resumen = config && rolSeleccionado ? resumenDeRol(config, rolSeleccionado) : null
  const cargandoTodo = cargando || roles.cargando
  const hayCambios = Boolean(config && original
    && JSON.stringify(config.roles) !== JSON.stringify(original.roles))

  function notificar(mensaje, tipo = 'exito') {
    contadorNotificacion.current += 1
    setNotificacion({ id: contadorNotificacion.current, mensaje, tipo })
  }

  useEffect(() => {
    let activo = true

    function aplicarRoles(datos, error = '') {
      if (!activo) return
      setRoles({ cargando: false, datos, error })
      setRolSeleccionado((actual) => {
        const disponibles = datos
          .filter((rol) => ROLES_ACCESO_REPORTES.includes(rol.nombre)
            && rol.nombre !== 'ADMINISTRADOR')
          .map((rol) => rol.nombre)
        return disponibles.includes(actual) ? actual : (disponibles[0] ?? '')
      })
    }

    listarRolesInternos()
      .then((datos) => aplicarRoles(datos))
      .catch(() => {
        const respaldo = ROLES_ACCESO_REPORTES
          .filter((nombre) => nombre !== 'ADMINISTRADOR')
          .map((nombre) => ({
            nombre,
            descripcion: '',
            permisos: ROLES_CON_ACCESO_REPORTES_POR_DEFECTO.includes(nombre)
              ? PERMISOS_REPORTES_POR_DEFECTO
              : '',
          }))
        aplicarRoles(
          respaldo,
          'No se pudieron consultar los permisos reales. Se muestran los roles definidos por defecto en las migraciones.',
        )
      })

    return () => { activo = false }
  }, [])

  useEffect(() => {
    if (roles.cargando) return undefined
    let activo = true
    const rolesConAcceso = roles.datos
      .filter((rol) => ROLES_ACCESO_REPORTES.includes(rol.nombre)
        && rolTienePermiso(rol, 'REPORTES:CONSULTAR'))
      .map((rol) => rol.nombre)

    obtenerConfiguracionAccesoReportes({ rolesConAcceso })
      .then((datos) => {
        if (!activo) return
        setConfig(datos)
        setOriginal(clonarConfiguracion(datos))
        setErrorCarga('')
      })
      .catch((error) => {
        if (activo) setErrorCarga(error.message || 'No se pudo consultar la configuración de acceso a reportes.')
      })
      .finally(() => {
        if (activo) setCargando(false)
      })

    return () => { activo = false }
  }, [roles])

  useEffect(() => {
    const controlador = new AbortController()
    listarSegmentosPublicados({ signal: controlador.signal })
      .then((datos) => {
        if (controlador.signal.aborted) return
        setSegmentosPublicados({
          cargando: false,
          opciones: opcionesSegmentosPublicados(datos),
          error: '',
        })
      })
      .catch((error) => {
        if (controlador.signal.aborted) return
        const mensaje = error?.estado === 403
          ? 'No se pudieron cargar los segmentos publicados porque el usuario no tiene permiso para consultarlos.'
          : 'No se pudieron cargar los segmentos publicados. Puedes escribir el nombre manualmente.'
        setSegmentosPublicados({ cargando: false, opciones: [], error: mensaje })
      })
    return () => controlador.abort()
  }, [])

  useEffect(() => {
    if (!notificacion) return undefined
    const temporizador = window.setTimeout(() => setNotificacion(null), 6000)
    return () => window.clearTimeout(temporizador)
  }, [notificacion])

  function actualizarReporteActual(idReporte, cambios) {
    setConfig((actual) => (actual ? actualizarReporte(actual, rolSeleccionado, idReporte, cambios) : actual))
    setNotificacion(null)
  }

  function marcarTodos(permitido) {
    setConfig((actual) => (actual ? marcarTodosLosReportes(actual, rolSeleccionado, permitido) : actual))
    setNotificacion(null)
  }

  function estaExpandido(idReporte) {
    return reportesExpandidos[idReporte] !== false
  }

  function alternarReporte(idReporte) {
    setReportesExpandidos((actual) => ({
      ...actual,
      [idReporte]: actual[idReporte] === false,
    }))
  }

  function cambiarTodosLosReportesExpandidos(expandido) {
    setReportesExpandidos(Object.fromEntries(IDS_REPORTES.map((idReporte) => [idReporte, expandido])))
  }

  function copiarDeRol() {
    if (!rolOrigen) return
    setConfig((actual) => (actual ? copiarConfiguracionRol(actual, rolOrigen, rolSeleccionado) : actual))
    notificar(`Se copió la configuración de ${etiquetaRol(rolOrigen)} a ${etiquetaRol(rolSeleccionado)}. Guarda para conservarla.`)
    setRolOrigen('')
  }

  function aplicarATodos() {
    setConfirmacion({
      tipo: 'aplicar',
      titulo: 'Aplicar a los roles con acceso',
      mensaje: `Se copiará la configuración de ${etiquetaRol(rolSeleccionado)} a los ${nombresRolesConfigurables.length} roles configurables. Los cambios actuales de esos roles se reemplazarán.`,
      textoConfirmar: 'Aplicar a todos',
    })
  }

  function restablecerTodo() {
    setConfirmacion({
      tipo: 'restablecer',
      titulo: 'Restablecer configuración',
      mensaje: 'Se descartarán los cambios sin guardar y todos los roles con acceso a reportes volverán a tener todos los reportes, sin restricciones de zona o segmento y con todas las columnas.',
      textoConfirmar: 'Restablecer',
      peligro: true,
    })
  }

  async function ejecutarConfirmacion() {
    const accion = confirmacion
    setConfirmacion(null)
    if (!accion) return

    if (accion.tipo === 'aplicar') {
      setConfig((actual) => (actual
        ? aplicarConfiguracionATodosLosRoles(actual, rolSeleccionado, nombresRolesConfigurables)
        : actual))
      notificar(`La configuración de ${etiquetaRol(rolSeleccionado)} se aplicó a todos los roles configurables. Guarda para conservarla.`)
      return
    }

    if (accion.tipo === 'restablecer') {
      reiniciarConfiguracionAccesoReportes()
      try {
        const datos = await obtenerConfiguracionAccesoReportes({ rolesConAcceso: nombresConAccesoReal })
        setConfig(datos)
        setOriginal(clonarConfiguracion(datos))
        setRolSeleccionado(nombresRolesConfigurables[0] ?? '')
        notificar('La configuración se restableció a los valores iniciales.')
      } catch (error) {
        notificar(error.message || 'No se pudo restablecer la configuración.', 'error')
      }
    }
  }

  async function guardar() {
    if (!config || !hayCambios || guardando) return
    setGuardando(true)
    try {
      const guardada = await guardarConfiguracionAccesoReportes(config, { usuario: sesion?.nombreCompleto })
      setConfig(guardada)
      setOriginal(clonarConfiguracion(guardada))
      notificar('La configuración de acceso a reportes se guardó en este navegador.')
    } catch (error) {
      notificar(error.message || 'No se pudo guardar la configuración.', 'error')
    } finally {
      setGuardando(false)
    }
  }

  const configuracionRol = config && rolSeleccionado ? configuracionDeRol(config, rolSeleccionado) : null

  return (
    <div className="config-acceso-reportes" aria-busy={cargandoTodo || guardando}>
      <section className="pcard acceso-reportes-cabecera">
        <div>
          <h2>Acceso y alcance de reportes</h2>
          <p className="hint">
            Configura por rol qué reportes puede consultar, qué zonas y segmentos, y qué columnas opcionales
            puede ver en cada reporte. La configuración se aplica a cada tipo de usuario, no a personas individuales.
          </p>
        </div>
        <span className="estado neutro">HU045 · RF-060</span>
      </section>

      {ACCESOS_REPORTES_SIMULADOS && (
        <div className="info acceso-reportes-demo" role="note">
          <strong>Datos simulados.</strong> El backend de HU045 todavía no está implementado. La configuración se
          guarda en este navegador y no modifica las consultas ni las exportaciones de los reportes. El aviso de
          las pantallas de reporte sí se muestra. Al publicarse el backend, se reemplazará por la API de acceso.
        </div>
      )}

      {notificacion && (
        <div
          key={notificacion.id}
          className={`notificacion-temporal ${notificacion.tipo === 'error' ? 'alert' : 'confirmacion'}`}
          role={notificacion.tipo === 'error' ? 'alert' : 'status'}
        >
          {notificacion.mensaje}
        </div>
      )}

      {roles.error && !roles.cargando && (
        <div className="info acceso-reportes-permisos-aviso" role="note">
          <strong>Permisos de respaldo.</strong> {roles.error}
        </div>
      )}

      {cargandoTodo && <p className="hint mensaje-carga" role="status">Consultando la configuración y los permisos de los roles…</p>}

      {!cargandoTodo && errorCarga && (
        <div className="alert" role="alert">
          <p>{errorCarga}</p>
          <button
            className="btn"
            type="button"
            onClick={() => {
              setCargando(true)
              setErrorCarga('')
              obtenerConfiguracionAccesoReportes({ rolesConAcceso: nombresConAccesoReal })
                .then((datos) => {
                  setConfig(datos)
                  setOriginal(clonarConfiguracion(datos))
                })
                .catch((error) => setErrorCarga(error.message || 'No se pudo consultar la configuración.'))
                .finally(() => setCargando(false))
            }}
          >
            Reintentar
          </button>
        </div>
      )}

      {!cargandoTodo && !errorCarga && config && rolesConfigurables.length === 0 && (
        <div className="alert" role="alert">
          <p>No hay roles configurables. El Administrador no se configura en esta pantalla.</p>
        </div>
      )}

      {!cargandoTodo && !errorCarga && config && rolSeleccionado && configuracionRol && (
        <div className="acceso-reportes-layout">
          <aside className="pcard acceso-reportes-roles" aria-labelledby="acceso-reportes-roles-titulo">
            <h2 id="acceso-reportes-roles-titulo">Roles</h2>
            <p className="hint">Se muestran todos los roles excepto Administrador.</p>
            <div className="acceso-reportes-rol-lista">
              {rolesConfigurables.map((rol) => {
                const resumenRol = resumenDeRol(config, rol.nombre)
                return (
                  <button
                    key={rol.nombre}
                    type="button"
                    className="acceso-reportes-rol"
                    aria-pressed={rol.nombre === rolSeleccionado}
                    onClick={() => setRolSeleccionado(rol.nombre)}
                  >
                    <strong>{etiquetaRol(rol.nombre)}</strong>
                    <small>
                      {resumenRol.permitidos}/{resumenRol.total} reportes
                      {' · '}
                      {resumenRol.conAlcance} con alcance limitado
                    </small>
                  </button>
                )
              })}
            </div>
            <div className="acceso-reportes-sin-acceso">
              <p className="acceso-reportes-etiqueta">Administrador</p>
              <p className="hint">El rol Administrador no se configura en esta pantalla.</p>
            </div>
          </aside>

          <div className="acceso-reportes-contenido">
            <section className="pcard acceso-reportes-rol-cabecera" aria-labelledby="acceso-reportes-rol-titulo">
              <div>
                <p className="acceso-reportes-etiqueta">Configurando el rol</p>
                <h2 id="acceso-reportes-rol-titulo">{etiquetaRol(rolSeleccionado)}</h2>
                <p className="hint">
                  {resumen.permitidos} de {resumen.total} reportes permitidos
                  {resumen.columnasLimitadas > 0 ? ` · ${resumen.columnasLimitadas} con columnas limitadas` : ''}
                  {resumen.conAlcance > 0 ? ` · ${resumen.conAlcance} con zonas o segmentos restringidos` : ' · sin restricciones de alcance'}.
                </p>
                <div className="acceso-reportes-permisos" aria-label="Permisos reales del rol">
                  <span
                    className={`estado ${rolActualTieneAccesoReal ? 'ok' : 'neutro'}`}
                    title="REPORTES:CONSULTAR"
                  >
                    {rolActualTieneAccesoReal ? 'Consulta de reportes' : 'Sin permiso en la BD'}
                  </span>
                  <span
                    className={`estado ${puedeExportarRol ? 'ok' : 'neutro'}`}
                    title="REPORTES:EXPORTAR"
                  >
                    {puedeExportarRol ? 'Exportación' : 'Sin exportación'}
                  </span>
                  <span
                    className={`estado ${puedeVerSegmentosRol ? 'ok' : 'neutro'}`}
                    title="SEGMENTOS:CONSULTAR"
                  >
                    {puedeVerSegmentosRol ? 'Mapa de segmentos' : 'Sin mapa de segmentos'}
                  </span>
                </div>
                <p className="hint">
                  Permisos reales del rol. Esta pantalla no modifica los permisos; solo configura el alcance de los reportes.
                </p>
                {!rolActualTieneAccesoReal && (
                  <div className="info acceso-reportes-permiso-base-aviso" role="note">
                    <strong>Sin REPORTES:CONSULTAR en la base de datos.</strong> Los switches de esta
                    pantalla solo cambian la configuración simulada; no habilitan el acceso real del rol.
                  </div>
                )}
              </div>
              <div className="acceso-reportes-acciones">
                <button className="btn" type="button" onClick={() => cambiarTodosLosReportesExpandidos(true)}>Expandir todos</button>
                <button className="btn" type="button" onClick={() => cambiarTodosLosReportesExpandidos(false)}>Contraer todos</button>
                <button className="btn" type="button" onClick={() => marcarTodos(true)}>Permitir todos</button>
                <button className="btn" type="button" onClick={() => marcarTodos(false)}>No permitir ninguno</button>
              </div>
            </section>

            <section className="pcard acceso-reportes-bulk" aria-labelledby="acceso-reportes-bulk-titulo">
              <h2 id="acceso-reportes-bulk-titulo">Acciones en bloque</h2>
              <p className="hint">Reutiliza la configuración de otro rol o aplícala a todos los roles configurables.</p>
              <div className="acceso-reportes-bulk-fila">
                <label htmlFor="acceso-reportes-rol-origen">Copiar configuración de</label>
                <select
                  id="acceso-reportes-rol-origen"
                  value={rolOrigen}
                  onChange={(evento) => setRolOrigen(evento.target.value)}
                >
                  <option value="">Selecciona un rol</option>
                  {rolesConfigurables
                    .filter((rol) => rol.nombre !== rolSeleccionado)
                    .map((rol) => (
                      <option key={rol.nombre} value={rol.nombre}>{etiquetaRol(rol.nombre)}</option>
                    ))}
                </select>
                <button className="btn" type="button" onClick={copiarDeRol} disabled={!rolOrigen}>Copiar</button>
                <button className="btn" type="button" onClick={aplicarATodos}>Aplicar a los roles configurables</button>
              </div>
            </section>

            {MODULOS_ACCESO_REPORTES.map((modulo) => (
              <section className="acceso-reportes-modulo" key={modulo.id} aria-labelledby={`acceso-reportes-modulo-${modulo.id}`}>
                <div className="acceso-reportes-modulo-cab">
                  <h3 id={`acceso-reportes-modulo-${modulo.id}`}>{modulo.titulo}</h3>
                  <p className="hint">{modulo.descripcion}</p>
                </div>
                {reportesDelModulo(modulo.id).map((reporte) => (
                  <ReporteConfig
                    key={reporte.id}
                    reporte={reporte}
                    valor={configuracionRol.reportes[reporte.id]}
                    arrastre={arrastreColumnas}
                    segmentosPublicados={segmentosPublicados}
                    expandido={estaExpandido(reporte.id)}
                    onAlternar={() => alternarReporte(reporte.id)}
                    onCambiar={(cambios) => actualizarReporteActual(reporte.id, cambios)}
                  />
                ))}
              </section>
            ))}

            <section className="pcard acceso-reportes-preview" aria-labelledby="acceso-reportes-preview-titulo">
              <h2 id="acceso-reportes-preview-titulo">Vista previa del aviso en reportes</h2>
              <p className="hint">
                Este es el aviso que verá el rol seleccionado en las pantallas de reportes. Se muestra siempre,
                independientemente de la configuración guardada.
              </p>
              <AvisoAccesoReportes rol={rolSeleccionado} />
            </section>

            <section className="pcard acceso-reportes-guardar" aria-label="Guardar configuración">
              <p className="acceso-reportes-estado" role="status" aria-live="polite">
                {hayCambios
                  ? 'Hay cambios sin guardar.'
                  : config.actualizadoEn
                    ? `Guardado el ${fechaHora(config.actualizadoEn)} por ${config.actualizadoPor || 'Administrador'}.`
                    : 'Configuración inicial: todos los reportes permitidos, sin restricciones.'}
              </p>
              <div className="acceso-reportes-guardar-acciones">
                <button className="btn" type="button" onClick={restablecerTodo} disabled={guardando}>Restablecer todo</button>
                <button className="btn primary" type="button" onClick={guardar} disabled={!hayCambios || guardando}>
                  {guardando ? 'Guardando…' : 'Guardar configuración'}
                </button>
              </div>
            </section>
          </div>
        </div>
      )}

      <ModalConfirmacion
        abierto={Boolean(confirmacion)}
        titulo={confirmacion?.titulo ?? ''}
        mensaje={confirmacion?.mensaje ?? ''}
        textoConfirmar={confirmacion?.textoConfirmar ?? 'Confirmar'}
        peligro={Boolean(confirmacion?.peligro)}
        onConfirmar={ejecutarConfirmacion}
        onCerrar={() => setConfirmacion(null)}
      />
    </div>
  )
}
