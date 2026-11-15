import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import PaginacionTabla from '../components/PaginacionTabla.jsx'
import SegmentacionIA from '../components/SegmentacionIA.jsx'
import {
  aprobarCaso, decisionesPorCodigo, ejecutarCarga, listarCargas, listarCasos, obtenerCarga, obtenerCaso, rechazarCaso,
  reasignarCaso,
} from '../services/calidadDatosService.js'
import { fecha, fechaHora, hoyLima } from '../utils/formato.js'
import './ConstanciasNoAdeudo.css'

// HU025, HU026, HU028 y HU029: el Responsable de datos audita las cargas del ETL (volúmenes y reglas de calidad),
// ejecuta una carga a demanda, resuelve la bandeja de homologación (HU028) y audita sus decisiones (HU040).
const ESTADO_LOTE = { PUBLICADO: ['Publicada', 'ok'], RECHAZADO: ['Rechazada', 'critico'], EN_CURSO: ['En curso', 'aviso'] }
const RESULTADO_REGLA = { CUMPLE: ['Cumple', 'ok'], OBSERVA: ['Con observaciones', 'aviso'], FALLA: ['No cumple', 'critico'] }
const TIPO_EJECUCION = { PROGRAMADA: 'Programada', A_DEMANDA: 'A demanda' }
const TIPO_DECISION = { AUTOMATICA: ['Automática', 'neutro'], HUMANA: ['Humana', 'aviso'] }
const NIVEL = {
  N0_CORRESPONDENCIA_PREVIA: 'N0 · vínculo previo', N1_EXACTA_NORMALIZADA: 'N1 · coincidencia exacta',
  N2_SIMILITUD: 'N2 · similitud', N3_ALTA_NUEVA: 'N3 · registro nuevo',
}
const entero = (n) => Number(n ?? 0).toLocaleString('es-PE')

function Estado({ mapa, valor }) {
  const [texto, clase] = mapa[valor] ?? [valor, 'neutro']
  return <span className={`estado ${clase}`}>{texto}</span>
}

function Cargas() {
  const [cargas, setCargas] = useState(null)
  const [error, setError] = useState('')
  const [detalle, setDetalle] = useState(null)
  const [ejecutando, setEjecutando] = useState(false)
  const [aviso, setAviso] = useState('')
  const [pagina, setPagina] = useState(1)
  const [tamanio, setTamanio] = useState(10)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controlador = new AbortController()
    listarCargas({ signal: controlador.signal })
      .then((lista) => { if (!controlador.signal.aborted) { setCargas(lista); setError('') } })
      .catch((err) => { if (!controlador.signal.aborted) setError(err.message || 'No se pudo consultar las cargas.') })
    return () => controlador.abort()
  }, [revision])

  async function verDetalle(idLote) {
    setAviso('')
    try { setDetalle(await obtenerCarga(idLote)) } catch (err) { setError(err.message) }
  }

  async function ejecutar() {
    setEjecutando(true)
    setAviso('')
    setError('')
    try {
      const resultado = await ejecutarCarga(hoyLima())
      setAviso(`Carga ${resultado.idLote} ${resultado.lotePublicado ? 'publicada' : 'rechazada por calidad'}: ${entero(resultado.registrosLeidos)} registros leídos.`)
      setDetalle(resultado)
      setRevision((v) => v + 1)
    } catch (err) {
      setError(err.message || 'No se pudo ejecutar la carga.')
    } finally {
      setEjecutando(false)
    }
  }

  const visibles = (cargas ?? []).slice((pagina - 1) * tamanio, pagina * tamanio)
  return (
    <>
      <section className="pcard" aria-labelledby="cargas-titulo">
        <h2 id="cargas-titulo">Cargas del ETL</h2>
        <p className="hint">Cada carga extrae la información de SAMNET, aplica las reglas de calidad y, si las cumple, se publica para la plataforma analítica. La carga programada se ejecuta todos los días a la 1:30 con corte del día anterior.</p>
        <div className="filters">
          <button className="btn primary" type="button" onClick={ejecutar} disabled={ejecutando}>
            {ejecutando ? 'Ejecutando la carga…' : 'Ejecutar carga con corte de hoy'}
          </button>
        </div>
        {aviso && <p className="confirmacion" role="status">{aviso}</p>}
        {error && <div className="alert" role="alert">{error}</div>}
        {!cargas && !error && <p className="hint" role="status">Consultando las cargas…</p>}
        {cargas?.length === 0 && <p className="hint">No hay cargas registradas.</p>}
        {visibles.length > 0 && (
          <>
            <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Cargas del ETL">
              <table className="tabla">
                <caption className="sr-only">Cargas del ETL con sus volúmenes y estado</caption>
                <thead>
                  <tr>
                    <th scope="col">N.°</th><th scope="col">Fecha de corte</th><th scope="col">Tipo</th>
                    <th scope="col">Inicio</th><th scope="col" className="num">Leídos</th>
                    <th scope="col" className="num">Aceptados</th><th scope="col" className="num">Rechazados</th>
                    <th scope="col">Estado</th><th scope="col"><span className="sr-only">Acción</span></th>
                  </tr>
                </thead>
                <tbody>
                  {visibles.map((c) => (
                    <tr key={c.idLote}>
                      <th scope="row">{c.idLote}</th>
                      <td>{fecha(c.fechaCorte)}</td>
                      <td>{TIPO_EJECUCION[c.tipoEjecucion] ?? c.tipoEjecucion}</td>
                      <td>{fechaHora(c.fechaInicio)}</td>
                      <td className="num">{entero(c.registrosLeidos)}</td>
                      <td className="num">{entero(c.registrosAceptados)}</td>
                      <td className="num">{entero(c.registrosRechazados)}</td>
                      <td><Estado mapa={ESTADO_LOTE} valor={c.estado} /></td>
                      <td><button className="btn" type="button" onClick={() => verDetalle(c.idLote)}>Ver calidad</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <PaginacionTabla id="cargas-etl" totalRegistros={cargas.length} pagina={pagina} tamanio={tamanio}
              onCambiarPagina={setPagina} onCambiarTamanio={(v) => { setTamanio(v); setPagina(1) }} />
          </>
        )}
      </section>

      {detalle && (
        <section className="pcard" aria-labelledby="calidad-titulo">
          <h2 id="calidad-titulo">Calidad de la carga N.° {detalle.idLote}</h2>
          <p className="hint">Corte al {fecha(detalle.fechaCorte)} · {entero(detalle.registrosLeidos)} registros leídos · {entero(detalle.registrosRechazados)} en cuarentena.</p>
          {detalle.resultadosReglas.length === 0 ? <p className="hint">La carga no registra evaluaciones de reglas.</p> : (
            <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Reglas de calidad">
              <table className="tabla">
                <caption className="sr-only">Resultado de cada regla de calidad</caption>
                <thead><tr><th scope="col">Regla</th><th scope="col">Descripción</th><th scope="col" className="num">Evaluados</th><th scope="col" className="num">Con error</th><th scope="col">Resultado</th></tr></thead>
                <tbody>
                  {detalle.resultadosReglas.map((r) => (
                    <tr key={r.codigoRegla}>
                      <th scope="row">{r.codigoRegla}</th>
                      <td>{r.descripcionRegla}{r.detalle && <span className="hint"> · {r.detalle}</span>}</td>
                      <td className="num">{entero(r.registrosEvaluados)}</td>
                      <td className="num">{entero(r.registrosConError)}</td>
                      <td><Estado mapa={RESULTADO_REGLA} valor={r.resultado} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </>
  )
}

function Homologacion() {
  const [estadoFiltro, setEstadoFiltro] = useState('PENDIENTE')
  const [casos, setCasos] = useState(null)
  const [error, setError] = useState('')
  const [detalle, setDetalle] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [aviso, setAviso] = useState('')
  const [revision, setRevision] = useState(0)
  const [codigoDestino, setCodigoDestino] = useState('')

  useEffect(() => {
    const controlador = new AbortController()
    listarCasos(estadoFiltro, { signal: controlador.signal })
      .then((lista) => { if (!controlador.signal.aborted) { setCasos(lista); setError('') } })
      .catch((err) => { if (!controlador.signal.aborted) setError(err.message || 'No se pudo consultar la bandeja.') })
    return () => controlador.abort()
  }, [estadoFiltro, revision])

  async function abrir(idCaso) {
    setAviso('')
    setMotivo('')
    try { setDetalle(await obtenerCaso(idCaso)) } catch (err) { setError(err.message) }
  }

  async function resolver(accion) {
    if (!motivo.trim()) return
    setOcupado(true)
    setError('')
    try {
      if (accion === 'reasignar') {
        // El Responsable indica el código SAMNET de la entidad correcta; se ubica su registro maestro.
        const decisiones = await decisionesPorCodigo(detalle.caso.tipoEntidad, codigoDestino)
        const idMaestro = decisiones.find((d) => d.idMaestro !== detalle.candidato?.idMaestro)?.idMaestro ?? decisiones[0]?.idMaestro
        if (!idMaestro) throw new Error('El código indicado no tiene un registro maestro.')
        await reasignarCaso(detalle.caso.idCaso, idMaestro, motivo.trim())
        setAviso(`El registro se unió a la entidad del código ${codigoDestino.trim()}.`)
      } else {
        await (accion === 'aprobar' ? aprobarCaso : rechazarCaso)(detalle.caso.idCaso, motivo.trim())
        setAviso(accion === 'aprobar' ? 'El registro se unió a la entidad candidata.' : 'El registro quedó como una entidad distinta.')
      }
      setCodigoDestino('')
      setDetalle(null)
      setRevision((v) => v + 1)
    } catch (err) {
      setError(err.message || 'No se pudo resolver el caso.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <>
      <section className="pcard" aria-labelledby="homologacion-titulo">
        <h2 id="homologacion-titulo">Bandeja de homologación</h2>
        <p className="hint">Registros que el ETL encontró parecidos a otro contribuyente o predio. Decide si son la misma entidad; la decisión queda registrada y no se puede modificar.</p>
        <div className="filters">
          <div className="fld">
            <label htmlFor="homologacion-estado">Estado</label>
            <select id="homologacion-estado" value={estadoFiltro} onChange={(e) => { setEstadoFiltro(e.target.value); setDetalle(null) }}>
              <option value="PENDIENTE">Pendientes</option>
              <option value="APROBADO">Aprobados</option>
              <option value="RECHAZADO">Rechazados</option>
              <option value="REASIGNADO">Reasignados</option>
            </select>
          </div>
        </div>
        {aviso && <p className="confirmacion" role="status">{aviso}</p>}
        {error && <div className="alert" role="alert">{error}</div>}
        {!casos && !error && <p className="hint" role="status">Consultando la bandeja…</p>}
        {casos?.length === 0 && <p className="hint">No hay casos en este estado.</p>}
        {casos?.length > 0 && (
          <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Casos de homologación">
            <table className="tabla">
              <caption className="sr-only">Casos de homologación</caption>
              <thead><tr><th scope="col">Caso</th><th scope="col">Entidad</th><th scope="col">Carga</th><th scope="col" className="num">Puntaje</th><th scope="col">Registrado</th><th scope="col"><span className="sr-only">Acción</span></th></tr></thead>
              <tbody>
                {casos.map((c) => (
                  <tr key={c.idCaso}>
                    <th scope="row">{c.idCaso}</th>
                    <td>{c.tipoEntidad === 'PREDIO' ? 'Predio' : 'Contribuyente'}</td>
                    <td>{c.idLote} · corte {fecha(c.fechaCorte)}</td>
                    <td className="num">{Number(c.puntaje ?? 0).toLocaleString('es-PE', { maximumFractionDigits: 2 })}</td>
                    <td>{fechaHora(c.fechaCreacion)}</td>
                    <td><button className="btn" type="button" onClick={() => abrir(c.idCaso)}>Revisar</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {detalle && (
        <section className="pcard" aria-labelledby="caso-titulo">
          <h2 id="caso-titulo">Caso N.° {detalle.caso.idCaso}</h2>
          <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Registros comparados">
            <table className="tabla">
              <caption className="sr-only">Registros de origen de cada lado del caso</caption>
              <thead><tr><th scope="col">Lado</th><th scope="col">Código</th><th scope="col">Documento</th><th scope="col">Nombre o ubicación</th><th scope="col">Domicilio</th></tr></thead>
              <tbody>
                {[['Candidato', detalle.candidato], ['Registro nuevo', detalle.provisional]].flatMap(([lado, datos]) =>
                  (datos?.registros ?? []).map((r) => (
                    <tr key={`${lado}-${r.codigo}`}>
                      <th scope="row">{lado}</th>
                      <td>{r.codigo}</td>
                      <td>{[r.tipoDocumento, r.numeroDocumento].filter(Boolean).join(' ')}</td>
                      <td>{r.nombre}</td>
                      <td>{r.domicilio || '—'}</td>
                    </tr>
                  )))}
              </tbody>
            </table>
          </div>
          {detalle.caso.estado === 'PENDIENTE' && (
            <div className="fld">
              <label htmlFor="caso-motivo">Motivo de la decisión</label>
              <textarea id="caso-motivo" rows={3} maxLength={400} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
              <div className="filters">
                <button className="btn primary" type="button" disabled={ocupado || !motivo.trim()} onClick={() => resolver('aprobar')}>Es la misma entidad</button>
                <button className="btn" type="button" disabled={ocupado || !motivo.trim()} onClick={() => resolver('rechazar')}>Son entidades distintas</button>
              </div>
              <div className="filters">
                <div className="fld">
                  <label htmlFor="caso-destino">Unir a otra entidad (código SAMNET)</label>
                  <input id="caso-destino" value={codigoDestino} maxLength={20} onChange={(e) => setCodigoDestino(e.target.value)} />
                </div>
                <button className="btn" type="button" disabled={ocupado || !motivo.trim() || !codigoDestino.trim()} onClick={() => resolver('reasignar')}>Reasignar</button>
              </div>
            </div>
          )}
        </section>
      )}
    </>
  )
}

function AuditoriaDecisiones() {
  const [tipo, setTipo] = useState('CONTRIBUYENTE')
  const [codigo, setCodigo] = useState('')
  const [decisiones, setDecisiones] = useState(null)
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)

  async function buscar(evento) {
    evento.preventDefault()
    if (!codigo.trim()) return
    setOcupado(true)
    setError('')
    setDecisiones(null)
    try { setDecisiones(await decisionesPorCodigo(tipo, codigo)) } catch (err) { setError(err.message || 'No se pudo consultar las decisiones.') } finally { setOcupado(false) }
  }

  return (
    <section className="pcard" aria-labelledby="auditoria-titulo">
      <h2 id="auditoria-titulo">Auditoría de decisiones de homologación</h2>
      <p className="hint">Muestra por qué un contribuyente o predio quedó asociado a su registro maestro: el nivel que lo resolvió, el puntaje, los atributos que coincidieron y si la decisión fue automática o humana. Los registros de auditoría son de solo lectura.</p>
      <form className="filters" onSubmit={buscar}>
        <div className="fld">
          <label htmlFor="auditoria-tipo">Entidad</label>
          <select id="auditoria-tipo" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="CONTRIBUYENTE">Contribuyente</option>
            <option value="PREDIO">Predio</option>
          </select>
        </div>
        <div className="fld">
          <label htmlFor="auditoria-codigo">Código SAMNET</label>
          <input id="auditoria-codigo" value={codigo} maxLength={20} onChange={(e) => setCodigo(e.target.value)} />
        </div>
        <button className="btn primary" type="submit" disabled={ocupado || !codigo.trim()}>{ocupado ? 'Consultando…' : 'Consultar'}</button>
      </form>
      {error && <div className="alert" role="alert">{error}</div>}
      {decisiones?.length === 0 && <p className="hint">El registro no tiene decisiones registradas.</p>}
      {decisiones?.length > 0 && (
        <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Decisiones de homologación">
          <table className="tabla">
            <caption className="sr-only">Decisiones de homologación del registro maestro</caption>
            <thead>
              <tr>
                <th scope="col">Decisión</th><th scope="col">Registro maestro</th><th scope="col">Nivel</th>
                <th scope="col" className="num">Puntaje</th><th scope="col">Atributos coincidentes</th><th scope="col">Tipo</th>
                <th scope="col">Responsable</th><th scope="col">Fecha</th><th scope="col">Motivo</th>
              </tr>
            </thead>
            <tbody>
              {decisiones.map((d) => (
                <tr key={d.idDecision}>
                  <th scope="row">{d.idDecision}</th>
                  <td>{d.seudonimoMaestro}</td>
                  <td>{NIVEL[d.nivelResolutor] ?? d.nivelResolutor}</td>
                  <td className="num">{d.puntaje == null ? '—' : Number(d.puntaje).toLocaleString('es-PE', { maximumFractionDigits: 2 })}</td>
                  <td>{d.atributosCoincidentes || '—'}</td>
                  <td><Estado mapa={TIPO_DECISION} valor={d.tipoDecision} /></td>
                  <td>{d.nombreCompletoUsuario || (d.tipoDecision === 'AUTOMATICA' ? 'Proceso ETL' : '—')}</td>
                  <td>{fechaHora(d.fechaHora)}</td>
                  <td>{d.motivo || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

const SUBPESTANAS = [
  { id: 'cargas', titulo: 'Cargas del ETL', Componente: Cargas },
  { id: 'homologacion', titulo: 'Homologación', Componente: Homologacion },
  { id: 'auditoria', titulo: 'Auditoría de decisiones', Componente: AuditoriaDecisiones },
  { id: 'segmentacion', titulo: 'Segmentación con IA', Componente: SegmentacionIA },
]

export default function CalidadDatos() {
  const [parametros, setParametros] = useSearchParams()
  const activa = SUBPESTANAS.find((p) => p.id === parametros.get('pestana')) ?? SUBPESTANAS[0]
  return (
    <div className="cna">
      <nav className="cna-pestanas" role="tablist" aria-label="Calidad de datos">
        {SUBPESTANAS.map((p) => (
          <button key={p.id} type="button" role="tab" className="cna-pestana" aria-selected={activa.id === p.id}
            aria-controls="calidad-panel" onClick={() => setParametros(p.id === 'cargas' ? {} : { pestana: p.id }, { replace: true })}>
            {p.titulo}
          </button>
        ))}
      </nav>
      <section id="calidad-panel" role="tabpanel">
        <activa.Componente />
      </section>
    </div>
  )
}
