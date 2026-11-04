import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import PagoCuentaPortal from '../components/PagoCuentaPortal.jsx'
import AvisoPagoCuenta from '../components/AvisoPagoCuenta.jsx'
import { obtenerEstadoCuentaPortal } from '../services/portalCuentaService.js'
import { agruparCuentaPorPeriodo, filtrarPeriodos, resumirCuenta, resumirTributos } from '../utils/estadoCuentaPortal.js'
import { fecha, moneda } from '../utils/formato.js'
import './ConsultasTributarias.css'

const FILTROS_INICIALES = { anio: '', estado: '', texto: '', vista: 'obligaciones' }
const claseEstado = { Pagada: 'ok', Vencida: 'critico', Pendiente: 'aviso' }
const periodoLegible = (periodo) => {
  const trimestre = /^(\d{4})-T([1-4])$/.exec(periodo || '')
  return trimestre ? `${['Primer', 'Segundo', 'Tercer', 'Cuarto'][Number(trimestre[2]) - 1]} trimestre de ${trimestre[1]}` : periodo || 'No informado'
}

export default function EstadoCuentaPortal() {
  const ubicacion = useLocation()
  const [revision, setRevision] = useState(0)
  const [resultado, setResultado] = useState({ cargando: true, cuenta: null, error: null })
  const [filtros, setFiltros] = useState(FILTROS_INICIALES)
  const pagoId = new URLSearchParams(ubicacion.search).get('pago')

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
  const periodosConDeuda = grupos.filter((grupo) => grupo.deuda > 0).length

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
      <div className="consulta-cabecera consulta-herramientas"><p className="hint">Aquí puedes revisar lo que debes y los pagos que ya se aplicaron a tu cuenta.</p><button type="button" className="btn" onClick={recargar} disabled={cargando}>{cargando ? 'Actualizando…' : 'Actualizar estado de cuenta'}</button></div>
      {cargando && <div className="pcard consulta-vacio" role="status">Consultando tu estado de cuenta…</div>}
      {error && <div className="alert" role="alert"><strong>No se pudo consultar tu cuenta.</strong><p>{error.message}</p><button type="button" className="btn" onClick={recargar}>Reintentar</button></div>}
      {!cargando && cuenta && pagoId && <AvisoPagoCuenta key={`${pagoId}-${revision}`} idTransaccion={pagoId} />}
      {!cargando && cuenta && <>
        <section className="pcard cuenta-titular" aria-labelledby="cuenta-titular">
          <div className="consulta-cabecera cuenta-titular-cabecera"><div><p className="cuenta-etiqueta">Cuenta de</p><h2 id="cuenta-titular">{cuenta.contribuyente.nombre}</h2></div><span className="consulta-corte">Fecha de corte: <strong>{fecha(cuenta.fechaCorte)}</strong></span></div>
          <div className="cuenta-identificadores"><span>Código de contribuyente <strong>{cuenta.contribuyente.codigo}</strong></span><span>{cuenta.contribuyente.tipoDoc || 'Documento'} <strong>{cuenta.contribuyente.doc || 'No disponible'}</strong></span></div>
        </section>
        {cuenta.desactualizado && <div className="alert" role="status"><strong>Mostrando la última copia disponible al {fecha(cuenta.fechaCorte)}.</strong><p>El servicio no está disponible. Los pagos posteriores a esta fecha podrían no estar incluidos. Vuelve a actualizar la cuenta más tarde.</p></div>}
        <section className="consulta-indicadores cuenta-indicadores" aria-label="Resumen de toda la cuenta a la fecha de corte">
          <div className="pcard consulta-indicador principal"><span className="pl">Total por pagar</span><strong className="consulta-cifra">{moneda(resumen.deuda)}</strong><p className="hint">Es el saldo que aún falta pagar en toda tu cuenta.</p></div>
          <div className="pcard consulta-indicador"><span className="pl">De ese total, ya venció</span><strong className="consulta-cifra secundaria deuda">{moneda(resumen.vencido)}</strong><p className="hint">Está incluido en el total por pagar; no se suma otra vez.</p></div>
          <div className="pcard consulta-indicador"><span className="pl">Ya pagaste</span><strong className="consulta-cifra secundaria pagado">{moneda(resumen.pagado)}</strong><p className="hint">Pagos aplicados a estas obligaciones, incluso los parciales.</p></div>
        </section>
        {filas.length === 0 ? <section className="pcard consulta-vacio" role="status"><h2>Sin obligaciones registradas</h2><p>Tu estado de cuenta no contiene obligaciones a la fecha de corte indicada.</p></section> : <>
          {resumen.deuda === 0 && <div className="confirmacion" role="status"><strong>No registras deuda pendiente</strong> en las obligaciones de esta consulta, al {fecha(cuenta.fechaCorte)}.</div>}
          {resumen.deuda > 0 && <div className="cuenta-orientacion" role="status"><strong>{periodosConDeuda} {periodosConDeuda === 1 ? 'periodo tiene' : 'periodos tienen'} un saldo por pagar.</strong><span>Revisa el detalle por periodo o continúa con el pago de todo el saldo.</span></div>}
          <PagoCuentaPortal cuenta={cuenta} onActualizar={recargar} />
          <section className="pcard cuenta-detalle" aria-label="Detalle de mi cuenta">
            <div className="cuenta-seccion-cabecera"><div><p className="cuenta-etiqueta">Detalle de obligaciones</p><h2>{esPagos ? 'Periodos con pagos aplicados' : 'Deuda y pagos por periodo'}</h2></div><p className="hint">Abre «Ver tributos» para revisar los montos de cada concepto.</p></div>
            <div className="consulta-vistas" role="group" aria-label="Información de la cuenta">
              <button type="button" className={`btn ${!esPagos ? 'primary' : ''}`} aria-pressed={!esPagos} onClick={() => setFiltros((actual) => ({ ...actual, vista: 'obligaciones', estado: '' }))}>Todos los periodos ({grupos.length})</button>
              <button type="button" className={`btn ${esPagos ? 'primary' : ''}`} aria-pressed={esPagos} onClick={() => setFiltros((actual) => ({ ...actual, vista: 'pagos', estado: '' }))}>Periodos con pagos ({grupos.filter((grupo) => grupo.pagado > 0).length})</button>
            </div>
            <form className="filters consulta-filtros consulta-filtros-interiores" onSubmit={(e) => e.preventDefault()} aria-label="Filtrar estado de cuenta">
              <div className="fld"><label htmlFor="cuenta-anio">Año</label><select id="cuenta-anio" name="anio" value={filtros.anio} onChange={cambiarFiltro}><option value="">Todos los años</option>{anios.map((anio) => <option key={anio}>{anio}</option>)}</select></div>
              <div className="fld"><label htmlFor="cuenta-estado">Estado</label><select id="cuenta-estado" name="estado" value={filtros.estado} onChange={cambiarFiltro}><option value="">Todos los estados</option><option>Vencida</option><option>Pendiente</option><option>Pagada</option></select></div>
              <div className="fld"><label htmlFor="cuenta-busqueda">Buscar periodo o concepto</label><input id="cuenta-busqueda" name="texto" type="search" value={filtros.texto} onChange={cambiarFiltro} placeholder="Ej. 2026-T1 o Predial" /></div>
              {hayFiltros && <button type="button" className="btn" onClick={() => setFiltros({ ...FILTROS_INICIALES, vista: filtros.vista })}>Limpiar filtros</button>}
            </form>
            <p className="hint cuenta-tabla-ayuda">Cada fila agrupa el Impuesto Predial y los arbitrios del trimestre, cuando corresponda. Dentro de cada trimestre, las cuotas del mismo tributo se muestran en un solo subtotal. «Emitido» es el importe generado; «Pagado», lo ya aplicado; y «Por pagar», el saldo restante. {esPagos && 'Esta vista muestra importes aplicados, no comprobantes ni fechas de transacción.'}</p>
            <p className="consulta-conteo" role="status">Mostrando {filtradas.length} {filtradas.length === 1 ? 'periodo' : 'periodos'}{hayFiltros ? ' con los filtros elegidos' : ''}</p>
            {filtradas.length > 0 && <p className="cuenta-desliza">Desliza la tabla hacia los lados para ver todos los importes y el detalle.</p>}
            {filtradas.length === 0 ? <p className="consulta-vacio">{hayFiltros ? 'No hay obligaciones que coincidan con los filtros seleccionados.' : 'No hay pagos aplicados en esta cuenta.'}</p> : (
              <div className="tabla-scroll" tabIndex={0} role="region" aria-label={esPagos ? 'Pagos aplicados por periodo' : 'Mi estado de cuenta por periodo'}>
                <table className="tabla consulta-tabla-cuenta"><caption className="sr-only">Estado de cuenta al {fecha(cuenta.fechaCorte)}</caption>
                  <thead><tr><th scope="col">Periodo</th><th scope="col">Concepto</th><th scope="col">Vence</th><th scope="col" className="num">Emitido</th><th scope="col" className="num">Pagado</th><th scope="col" className="num">Por pagar</th><th scope="col">Situación</th><th scope="col">Detalle</th></tr></thead>
                  <tbody>{filtradas.map((grupo) => <tr key={grupo.id}><th scope="row"><span className="cuenta-periodo">{periodoLegible(grupo.periodo)}</span><small>{grupo.periodo}</small></th><td>{grupo.titulo}</td><td>{grupo.fechaVencimiento ? fecha(grupo.fechaVencimiento) : grupo.conceptos.some((fila) => fila.fechaVencimiento) ? 'Varios vencimientos' : 'No informado'}</td><td className="num">{moneda(grupo.emitido)}</td><td className="num">{moneda(grupo.pagado)}</td><td className="num cuenta-saldo"><strong>{moneda(grupo.deuda)}</strong></td><td><span className={`estado ${claseEstado[grupo.estado] || ''}`}>{grupo.estado}</span></td><td>{grupo.esTrimestral ? <details className="consulta-detalle-periodo"><summary>Ver tributos</summary><ul>{resumirTributos(grupo.conceptos).map((tributo) => <li key={tributo.tributo}><strong>{tributo.tributo}</strong>{tributo.cantidad > 1 && <small>{tributo.cantidad} cuotas incluidas</small>}<span>Emitido {moneda(tributo.emitido)} · Pagado {moneda(tributo.pagado)} · Por pagar {moneda(tributo.deuda)}</span></li>)}</ul></details> : '—'}</td></tr>)}</tbody>
                  <tfoot><tr><th scope="row" colSpan={3}>Total de los periodos mostrados</th><td className="num">{moneda(subtotal.emitido)}</td><td className="num">{moneda(subtotal.pagado)}</td><td className="num"><strong>{moneda(subtotal.deuda)}</strong></td><td colSpan={2} /></tr></tfoot>
                </table>
              </div>
            )}
            <p className="hint cuenta-seleccion-aviso">Los totales de esta tabla cambian con los filtros. El pago del saldo completo incluye toda tu deuda, aunque filtres los periodos.</p>
          </section>
        </>}
      </>}
    </div>
  )
}
