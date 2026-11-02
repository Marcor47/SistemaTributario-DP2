import { useEffect, useMemo, useState } from 'react'
import { useSesionInterna } from '../context/sesionInterna.js'
import { AccesoDenegado, AccesoRestringido, VerificandoAcceso } from './AccesoInterno.jsx'
import { obtenerArbitriosPredio } from '../services/arbitriosService.js'
import {
  FILTROS_ARBITRIOS_INICIALES,
  agruparCuotasPorTitular,
  estadoArbitrio,
  etiquetaCondicion,
  filtrarCuotas,
  opcionesDeArbitrios,
} from '../utils/arbitrios.js'
import { TAMANIOS_PAGINA, paginar } from '../utils/paginacion.js'
import { ROLES_CONSULTA_PREDIO, tieneRolInterno } from '../utils/roles.js'
import { fecha, moneda } from '../utils/formato.js'
import './AccesoInterno.css'
import './ArbitriosPanel.css'

const TITULOS_ERROR = {
  SESION_REQUERIDA: 'Tu sesión interna no está vigente',
  ERROR_HTTP: 'Sin permiso para consultar arbitrios',
  PREDIO_NO_ENCONTRADO: 'El predio no se encontró en SAMNET',
  SAMNET_NO_DISPONIBLE: 'SAMNET no está disponible',
  SIN_CONEXION: 'No se pudo conectar con el servicio',
  RESPUESTA_INVALIDA: 'La respuesta no corresponde al predio seleccionado',
  CODIGO_PREDIO_INVALIDO: 'No se pudo identificar el predio',
}

function Campo({ etiqueta, valor }) {
  return (
    <div className="pf">
      <span className="pl">{etiqueta}</span>
      <span className="pv">{valor === '' || valor == null ? '—' : valor}</span>
    </div>
  )
}

function porcentaje(valor) {
  const n = Number(valor)
  if (valor == null || !isFinite(n)) return '—'
  return `${n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} %`
}

function conteo(cantidad, singular, plural) {
  const n = Number(cantidad) || 0
  return `${n} ${n === 1 ? singular : plural}`
}

function Titulares({ titulares }) {
  return (
    <div className="tabla-scroll" role="region" aria-label="Titulares del predio" tabIndex={0}>
      <table className="tabla arb-titulares">
        <thead>
          <tr>
            <th scope="col">Código</th>
            <th scope="col">Nombre o razón social</th>
            <th scope="col">Condición</th>
            <th scope="col" className="num">% de propiedad</th>
          </tr>
        </thead>
        <tbody>
          {titulares.map((titular) => (
            <tr key={titular.codigoContribuyente}>
              <td>{titular.codigoContribuyente}</td>
              <td>{titular.nombre || '—'}</td>
              <td>{etiquetaCondicion(titular.condicionPropiedad)}</td>
              <td className="num">{porcentaje(titular.porcentajePropiedad)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function GrupoCuotas({ grupo }) {
  return (
    <section className="arb-grupo" aria-label={`Cuotas de ${grupo.nombre}`}>
      <div className="arb-grupo__cab">
        <h4>{grupo.nombre}</h4>
        <span className="hint arb-grupo__codigo">{grupo.codigoContribuyente}</span>
        {grupo.condicionPropiedad && (
          <span className="arb-grupo__condicion">
            {etiquetaCondicion(grupo.condicionPropiedad)} · {porcentaje(grupo.porcentajePropiedad)}
          </span>
        )}
      </div>
      <div
        className="tabla-scroll"
        role="region"
        aria-label={`Tabla de cuotas de ${grupo.nombre}`}
        tabIndex={0}
      >
        <table className="tabla arb-tabla">
          <caption className="sr-only">Cuotas de arbitrios de {grupo.nombre}</caption>
          <thead>
            <tr>
              <th scope="col">Año</th>
              <th scope="col">Cuota</th>
              <th scope="col">Periodo</th>
              <th scope="col">Vencimiento</th>
              <th scope="col" className="num">Emitido</th>
              <th scope="col" className="num">Pagado</th>
              <th scope="col" className="num">Descontado</th>
              <th scope="col" className="num">Saldo</th>
              <th scope="col">Estado</th>
              <th scope="col" className="num">Días vencida</th>
            </tr>
          </thead>
          <tbody>
            {grupo.cuotas.map((cuota) => {
              const estado = estadoArbitrio(cuota.estado)
              return (
                <tr key={cuota.idObligacion}>
                  <td>{cuota.anio}</td>
                  <td>{cuota.cuota}</td>
                  <td>{cuota.periodo}</td>
                  <td>{fecha(cuota.fechaVencimiento)}</td>
                  <td className="num">{moneda(cuota.emitido)}</td>
                  <td className="num">{moneda(cuota.pagado)}</td>
                  <td className="num">{moneda(cuota.descontado)}</td>
                  <td className="num arb-saldo">{moneda(cuota.saldo)}</td>
                  <td><span className={`estado ${estado.clase}`}>{estado.etiqueta}</span></td>
                  <td className="num">{cuota.diasVencida > 0 ? `${cuota.diasVencida} días` : '—'}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function ResultadoArbitrios({ datos }) {
  const { predio, fechaCorte, resumen, cuotas } = datos
  const [filtros, setFiltros] = useState(FILTROS_ARBITRIOS_INICIALES)
  const [porPagina, setPorPagina] = useState(TAMANIOS_PAGINA[0])
  const [pagina, setPagina] = useState(1)

  const opciones = useMemo(() => opcionesDeArbitrios(cuotas), [cuotas])
  const filtradas = useMemo(() => filtrarCuotas(cuotas, filtros), [cuotas, filtros])
  const { visibles, paginaActual, totalPaginas, inicio, fin } = paginar(filtradas, pagina, porPagina)
  const grupos = agruparCuotasPorTitular({ predio, cuotas: visibles })
  const hayFiltros = Object.values(filtros).some(Boolean)

  function cambiarFiltro(evento) {
    const { name, value } = evento.target
    setFiltros((actuales) => ({ ...actuales, [name]: value }))
    setPagina(1)
  }

  function limpiarFiltros() {
    setFiltros(FILTROS_ARBITRIOS_INICIALES)
    setPagina(1)
  }

  function cambiarPorPagina(evento) {
    setPorPagina(Number(evento.target.value))
    setPagina(1)
  }

  function cambiarPagina(nueva) {
    setPagina(nueva)
  }

  return (
    <section className="pcard arb-ficha" aria-label={`Arbitrios del predio ${predio.codigoPredio}`}>
      <div className="arb-cabecera">
        <div>
          <h2>Arbitrios del predio {predio.codigoPredio}</h2>
          <p className="hint">{predio.ubicacion || 'Ubicación no disponible'}</p>
        </div>
        <p className="hint arb-fecha">Fecha de corte: {fecha(fechaCorte)}</p>
      </div>

      <div className="pfields arb-predio">
        <Campo etiqueta="Código predial" valor={predio.codigoPredio} />
        <Campo etiqueta="Ubicación" valor={predio.ubicacion} />
        <Campo etiqueta="Uso del predio" valor={predio.usoPredio} />
        <Campo etiqueta="Zona" valor={predio.zona} />
      </div>

      <section className="arb-seccion" aria-label="Titulares del predio">
        <h3>Titulares</h3>
        {predio.titulares?.length > 0
          ? <Titulares titulares={predio.titulares} />
          : <p className="hint arb-vacio">El predio no tiene titulares registrados.</p>}
      </section>

      {cuotas.length === 0 ? (
        <div className="arb-vacio" role="status">
          <strong>Este predio no tiene arbitrios registrados.</strong>
          <p className="hint">
            La consulta al {fecha(fechaCorte)} no devolvió cuotas de arbitrios para el predio{' '}
            {predio.codigoPredio}.
          </p>
        </div>
      ) : (
        <>
          <section className="arb-seccion" aria-label="Resumen de arbitrios">
            <h3>Resumen</h3>
            <div className="resumen arb-resumen">
              <div><span className="pl">Total emitido</span><strong>{moneda(resumen.totalEmitido)}</strong></div>
              <div><span className="pl">Total pagado</span><strong>{moneda(resumen.totalPagado)}</strong></div>
              <div><span className="pl">Total descontado</span><strong>{moneda(resumen.totalDescontado)}</strong></div>
              <div className="deuda"><span className="pl">Saldo total</span><strong>{moneda(resumen.totalSaldo)}</strong></div>
            </div>
            <div className="arb-conteo">
              <span className="estado ok">{conteo(resumen.cuotasPagadas, 'cuota pagada', 'cuotas pagadas')}</span>
              <span className="estado aviso">{conteo(resumen.cuotasPendientes, 'cuota pendiente', 'cuotas pendientes')}</span>
              <span className="estado critico">{conteo(resumen.cuotasVencidas, 'cuota vencida', 'cuotas vencidas')}</span>
            </div>
            <p className="hint arb-resumen-nota">
              Los totales corresponden a todas las cuotas del predio; los filtros de la lista no los modifican.
            </p>
          </section>

          <section className="arb-seccion" aria-label="Cuotas de arbitrios">
            <h3>Cuotas</h3>

            <div className="arb-filtros" role="group" aria-label="Filtros y paginación de cuotas">
              <div className="fld">
                <label htmlFor="arb-anio">Año</label>
                <select id="arb-anio" name="anio" value={filtros.anio} onChange={cambiarFiltro}>
                  <option value="">Todos los años</option>
                  {opciones.anios.map((anio) => (
                    <option key={anio} value={anio}>{anio}</option>
                  ))}
                </select>
              </div>
              <div className="fld">
                <label htmlFor="arb-estado">Estado</label>
                <select id="arb-estado" name="estado" value={filtros.estado} onChange={cambiarFiltro}>
                  <option value="">Todos los estados</option>
                  {opciones.estados.map((estado) => (
                    <option key={estado.valor} value={estado.valor}>{estado.etiqueta}</option>
                  ))}
                </select>
              </div>
              <div className="fld arb-tamano">
                <label htmlFor="arb-por-pagina">Filas por página</label>
                <select id="arb-por-pagina" value={porPagina} onChange={cambiarPorPagina}>
                  {TAMANIOS_PAGINA.map((tamano) => (
                    <option key={tamano} value={tamano}>{tamano}</option>
                  ))}
                </select>
              </div>
              {hayFiltros && (
                <button className="btn arb-limpiar" type="button" onClick={limpiarFiltros}>
                  Limpiar filtros
                </button>
              )}
            </div>

            {filtradas.length === 0 ? (
              <p className="arb-cuotas-vacio" role="status">
                No hay cuotas que coincidan con los filtros seleccionados.
              </p>
            ) : (
              <>
                {grupos.map((grupo) => <GrupoCuotas key={grupo.codigoContribuyente} grupo={grupo} />)}

                <div className="arb-paginacion">
                  <p className="arb-conteo-texto" role="status">
                    Mostrando {inicio + 1}–{fin} de {filtradas.length} {filtradas.length === 1 ? 'cuota' : 'cuotas'}
                  </p>
                  <div className="arb-paginacion__controles">
                    <button
                      className="btn"
                      type="button"
                      onClick={() => cambiarPagina(paginaActual - 1)}
                      disabled={paginaActual <= 1}
                    >
                      Anterior
                    </button>
                    <span className="arb-pagina">Página {paginaActual} de {totalPaginas}</span>
                    <button
                      className="btn"
                      type="button"
                      onClick={() => cambiarPagina(paginaActual + 1)}
                      disabled={paginaActual >= totalPaginas}
                    >
                      Siguiente
                    </button>
                  </div>
                </div>
              </>
            )}
          </section>
        </>
      )}

      <p className="hint arb-nota">
        Consulta de solo lectura. Los montos y estados los calcula el sistema a la fecha de corte;
        no incluyen el desglose de los tres arbitrios.
      </p>
    </section>
  )
}

export default function ArbitriosPanel({ codigoPredio }) {
  const { sesion, cargando, setSesion } = useSesionInterna()
  const [intento, setIntento] = useState(0)
  const [expirada, setExpirada] = useState(false)
  const [consulta, setConsulta] = useState({ datos: null, error: null, ocupado: true })

  const tieneRol = tieneRolInterno(sesion, ROLES_CONSULTA_PREDIO)

  useEffect(() => {
    if (!tieneRol) return undefined

    let activo = true
    const controlador = new AbortController()

    obtenerArbitriosPredio(codigoPredio, { signal: controlador.signal })
      .then((datos) => {
        if (activo) setConsulta({ datos, error: null, ocupado: false })
      })
      .catch((error) => {
        if (!activo) return
        if (error.estado === 401) {
          // La sesión interna dura 15 minutos; se limpia el contexto y se pide reingresar.
          setExpirada(true)
          setSesion(null)
          setConsulta({ datos: null, error: null, ocupado: false })
          return
        }
        setConsulta({ datos: null, error, ocupado: false })
      })

    return () => {
      activo = false
      controlador.abort()
    }
  }, [codigoPredio, intento, tieneRol, setSesion])

  function recargar() {
    setConsulta({ datos: null, error: null, ocupado: true })
    setIntento((valor) => valor + 1)
  }

  if (cargando) {
    return <VerificandoAcceso mensaje="Verificando el acceso a los arbitrios…" />
  }

  if (!sesion) {
    return (
      <AccesoRestringido
        titulo={`Arbitrios del predio ${codigoPredio}`}
        descripcion="Esta consulta está reservada al personal municipal con rol Analista de Rentas o Administrador."
        mensajeExpirada="Tu sesión interna venció. Vuelve a ingresar para consultar los arbitrios."
        expirada={expirada}
      />
    )
  }

  if (!tieneRol) {
    return (
      <AccesoDenegado
        titulo="Acceso denegado"
        usuario={sesion}
        descripcion={`Los arbitrios del predio ${codigoPredio} solo los consulta el personal autorizado.`}
        rolesPermitidos={ROLES_CONSULTA_PREDIO}
      />
    )
  }

  const tieneReintento = consulta.error && ![400, 401, 403, 404].includes(consulta.error.estado)

  return (
    <div className="arbitrios" aria-busy={consulta.ocupado}>
      {consulta.ocupado ? (
        <p className="hint mensaje-carga" role="status">
          Consultando los arbitrios del predio {codigoPredio} en SAMNET…
        </p>
      ) : consulta.error ? (
        <div className="alert arb-error" role="alert">
          <strong>{TITULOS_ERROR[consulta.error.codigo] || 'No se pudo consultar los arbitrios'}</strong>
          <p className="hint">Predio {codigoPredio}</p>
          <p>{consulta.error.message || 'Intenta nuevamente en unos momentos.'}</p>
          {tieneReintento && (
            <button className="btn" type="button" onClick={recargar}>Reintentar consulta</button>
          )}
        </div>
      ) : consulta.datos ? (
        <ResultadoArbitrios datos={consulta.datos} />
      ) : (
        <div className="alert" role="alert">
          <strong>No se recibió información de arbitrios</strong>
          <p>Vuelve a abrir la pestaña para intentar la consulta nuevamente.</p>
        </div>
      )}
    </div>
  )
}
