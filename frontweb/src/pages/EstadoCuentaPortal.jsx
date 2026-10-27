import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import PagoCuentaPortal from '../components/PagoCuentaPortal.jsx'
import { obtenerEstadoCuentaPortal } from '../services/portalCuentaService.js'
import { agruparCuentaPorPeriodo, filtrarPeriodos, resumirCuenta } from '../utils/estadoCuentaPortal.js'
import { fecha, moneda } from '../utils/formato.js'
import './ConsultasTributarias.css'

const FILTROS_INICIALES = { anio: '', estado: '', texto: '', vista: 'obligaciones' }
const claseEstado = { Pagada: 'ok', Vencida: 'critico', Pendiente: 'aviso' }

export default function EstadoCuentaPortal() {
  const ubicacion = useLocation()
  const [revision, setRevision] = useState(0)
  const [resultado, setResultado] = useState({ cargando: true, cuenta: null, error: null })
  const [filtros, setFiltros] = useState(FILTROS_INICIALES)
  const [pagoConfirmado, setPagoConfirmado] = useState(null)

  useEffect(() => {
    const controlador = new AbortController()
    obtenerEstadoCuentaPortal({ signal: controlador.signal })
      .then((cuenta) => { if (!controlador.signal.aborted) setResultado({ cargando: false, cuenta, error: null }) })
      .catch((error) => { if (!controlador.signal.aborted) setResultado({ cargando: false, cuenta: null, error }) })
    return () => controlador.abort()
  }, [revision])

  function recargar() {
    setResultado({ cargando: true, cuenta: null, error: null })
    setRevision((valor) => valor + 1)
  }

  const { cuenta, error, cargando } = resultado
  const filas = cuenta?.filas || []
  const resumen = resumirCuenta(filas)
  const grupos = agruparCuentaPorPeriodo(filas)
  const filtradas = filtrarPeriodos(grupos, filtros)
  const subtotal = resumirCuenta(filtradas)
  const anios = [...new Set(filas.map((fila) => fila.anio).filter(Boolean))].sort((a, b) => b - a)
  const esPagos = filtros.vista === 'pagos'
  const hayFiltros = Boolean(filtros.anio || filtros.estado || filtros.texto)

  function cambiarFiltro(evento) {
    setFiltros((actual) => ({ ...actual, [evento.target.name]: evento.target.value }))
  }

  if (error?.estado === 401) return (
    <section className="pcard consulta-vacio" role="status">
      <h2>Inicia sesión para ver tu estado de cuenta</h2>
      <p>Tu sesión no está iniciada o ha caducado. Ingresa con tus credenciales de Servicios en Línea.</p>
      <Link className="btn primary" to="/login" state={{ from: ubicacion.pathname, motivo: 'servicios-en-linea' }}>Iniciar sesión</Link>
    </section>
  )

  return (
    <div className="consulta-tributaria" aria-busy={cargando}>
      <div className="consulta-cabecera consulta-herramientas"><p className="hint">Consulta las obligaciones vinculadas a tu sesión.</p><button type="button" className="btn" onClick={recargar} disabled={cargando}>{cargando ? 'Actualizando…' : 'Actualizar cuenta'}</button></div>
      {cargando && <div className="pcard consulta-vacio" role="status">Consultando tu estado de cuenta…</div>}
      {error && <div className="alert" role="alert"><strong>No se pudo consultar tu cuenta.</strong><p>{error.message}</p><button type="button" className="btn" onClick={recargar}>Reintentar</button></div>}
      {pagoConfirmado && <div className="confirmacion" role="status"><strong>{pagoConfirmado.dePrueba ? 'Pago de prueba confirmado.' : 'Pago confirmado.'}</strong> Operación #{pagoConfirmado.idTransaccion}. {pagoConfirmado.dePrueba && 'La operación es de demostración.'}</div>}
      {!cargando && cuenta && <>
        {cuenta.esDemo && <div className="info" role="note"><strong>Sesión de demostración.</strong> Los datos de esta cuenta son ficticios.</div>}
        <section className="pcard" aria-labelledby="cuenta-titular">
          <div className="consulta-cabecera"><div><p className="hint">Titular de la cuenta</p><h2 id="cuenta-titular">{cuenta.contribuyente.nombre}</h2></div><span className="consulta-corte">Fecha de corte: <strong>{fecha(cuenta.fechaCorte)}</strong></span></div>
          <div className="pfields"><div className="pf"><span className="pl">Código de contribuyente</span><strong className="pv">{cuenta.contribuyente.codigo}</strong></div><div className="pf"><span className="pl">{cuenta.contribuyente.tipoDoc || 'Documento'}</span><strong className="pv">{cuenta.contribuyente.doc || 'No disponible'}</strong></div></div>
        </section>
        {cuenta.desactualizado && <div className="alert" role="status"><strong>Mostrando la última copia disponible al {fecha(cuenta.fechaCorte)}.</strong><p>El servicio no está disponible. Los pagos posteriores a esta fecha podrían no estar incluidos. Vuelve a actualizar la cuenta más tarde.</p></div>}
        <section className="consulta-indicadores" aria-label="Resumen de toda la cuenta a la fecha de corte">
          <div className="pcard consulta-indicador principal"><span className="pl">Deuda total</span><strong className="consulta-cifra">{moneda(resumen.deuda)}</strong><p className="hint">Obligaciones pendientes y vencidas</p></div>
          <div className="pcard consulta-indicador"><span className="pl">Deuda vencida</span><strong className="consulta-cifra secundaria deuda">{moneda(resumen.vencido)}</strong><p className="hint">Saldo con vencimiento anterior al corte</p></div>
          <div className="pcard consulta-indicador"><span className="pl">Pagos aplicados</span><strong className="consulta-cifra secundaria pagado">{moneda(resumen.pagado)}</strong><p className="hint">Incluye pagos parciales de las obligaciones</p></div>
        </section>
        {filas.length === 0 ? <section className="pcard consulta-vacio" role="status"><h2>Sin obligaciones registradas</h2><p>Tu estado de cuenta no contiene obligaciones a la fecha de corte indicada.</p></section> : <>
          {resumen.deuda === 0 && <div className="confirmacion" role="status"><strong>No registras deuda pendiente</strong> en las obligaciones de esta consulta, al {fecha(cuenta.fechaCorte)}.</div>}
          <section className="pcard" aria-label="Detalle de mi cuenta">
            <div className="consulta-vistas" role="group" aria-label="Información de la cuenta">
              <button type="button" className={`btn ${!esPagos ? 'primary' : ''}`} aria-pressed={!esPagos} onClick={() => setFiltros((actual) => ({ ...actual, vista: 'obligaciones', estado: '' }))}>Periodos ({grupos.length})</button>
              <button type="button" className={`btn ${esPagos ? 'primary' : ''}`} aria-pressed={esPagos} onClick={() => setFiltros((actual) => ({ ...actual, vista: 'pagos', estado: '' }))}>Periodos con pagos ({grupos.filter((grupo) => grupo.pagado > 0).length})</button>
            </div>
            <form className="filters consulta-filtros consulta-filtros-interiores" onSubmit={(e) => e.preventDefault()} aria-label="Filtrar estado de cuenta">
              <div className="fld"><label htmlFor="cuenta-anio">Año</label><select id="cuenta-anio" name="anio" value={filtros.anio} onChange={cambiarFiltro}><option value="">Todos los años</option>{anios.map((anio) => <option key={anio}>{anio}</option>)}</select></div>
              <div className="fld"><label htmlFor="cuenta-estado">Estado</label><select id="cuenta-estado" name="estado" value={filtros.estado} onChange={cambiarFiltro}><option value="">Todos los estados</option><option>Vencida</option><option>Pendiente</option><option>Pagada</option></select></div>
              <div className="fld"><label htmlFor="cuenta-busqueda">Buscar periodo o concepto</label><input id="cuenta-busqueda" name="texto" type="search" value={filtros.texto} onChange={cambiarFiltro} placeholder="Ej. 2026-T1 o Predial" /></div>
              {hayFiltros && <button type="button" className="btn" onClick={() => setFiltros({ ...FILTROS_INICIALES, vista: filtros.vista })}>Limpiar filtros</button>}
            </form>
            <h2>{esPagos ? 'Pagos aplicados por periodo' : 'Estado de cuenta por trimestre'}</h2>
            <p className="hint">Los cuatro conceptos municipales se presentan en un total por trimestre. Puedes ver los conceptos incluidos en cada fila; los importes corresponden a la fecha de corte. {esPagos && 'Los pagos aplicados no son un historial de recibos o transacciones.'}</p>
            <p className="consulta-conteo" role="status">{filtradas.length} {filtradas.length === 1 ? 'periodo encontrado' : 'periodos encontrados'}</p>
            {filtradas.length === 0 ? <p className="consulta-vacio">{hayFiltros ? 'No hay obligaciones que coincidan con los filtros seleccionados.' : 'No hay pagos aplicados en esta cuenta.'}</p> : (
              <div className="tabla-scroll" tabIndex={0} role="region" aria-label={esPagos ? 'Pagos aplicados por periodo' : 'Mi estado de cuenta por periodo'}>
                <table className="tabla consulta-tabla-cuenta"><caption className="sr-only">Estado de cuenta al {fecha(cuenta.fechaCorte)}</caption>
                  <thead><tr><th scope="col">Concepto</th><th scope="col">Periodo</th><th scope="col">Vencimiento</th><th scope="col" className="num">Emitido</th><th scope="col" className="num">Pagado</th><th scope="col" className="num">Saldo pendiente</th><th scope="col">Estado</th><th scope="col">Detalle</th></tr></thead>
                  <tbody>{filtradas.map((grupo) => <tr key={grupo.id}><th scope="row">{grupo.titulo}</th><td>{grupo.periodo}</td><td>{grupo.fechaVencimiento ? fecha(grupo.fechaVencimiento) : grupo.conceptos.some((fila) => fila.fechaVencimiento) ? 'Varios vencimientos' : 'No informado'}</td><td className="num">{moneda(grupo.emitido)}</td><td className="num">{moneda(grupo.pagado)}</td><td className="num"><strong>{moneda(grupo.deuda)}</strong></td><td><span className={`estado ${claseEstado[grupo.estado] || ''}`}>{grupo.estado}</span></td><td>{grupo.esTrimestral ? <details className="consulta-detalle-periodo"><summary>Ver conceptos</summary><ul>{[...new Set(grupo.conceptos.map((fila) => fila.tributo))].map((concepto) => <li key={concepto}>{concepto}</li>)}</ul></details> : '—'}</td></tr>)}</tbody>
                  <tfoot><tr><th scope="row" colSpan={3}>Total de la selección</th><td className="num">{moneda(subtotal.emitido)}</td><td className="num">{moneda(subtotal.pagado)}</td><td className="num"><strong>{moneda(subtotal.deuda)}</strong></td><td colSpan={2} /></tr></tfoot>
                </table>
              </div>
            )}
          </section>
          <PagoCuentaPortal cuenta={cuenta} onPagado={(pago) => { setPagoConfirmado(pago); recargar() }} />
        </>}
      </>}
    </div>
  )
}
