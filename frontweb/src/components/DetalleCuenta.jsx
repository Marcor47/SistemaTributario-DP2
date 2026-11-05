import { useState } from 'react'
import {
  SERVICIOS,
  aniosDeCuenta,
  anioInicial,
  arbitriosPorPredio,
  cuotasPredial,
  etiquetaCuota,
  filtrarGrupos,
  montoServicio,
  resumenDelAnio,
} from '../utils/estadoCuenta.js'
import { paginar } from '../utils/paginacion.js'
import { fecha, moneda } from '../utils/formato.js'
import './DetalleCuenta.css'

const PREDIOS_POR_PAGINA = 10
// Con pocos predios el buscador estorba; aparece cuando la lista ya no cabe a la vista.
const PREDIOS_PARA_BUSCAR = 5

function Estado({ fila }) {
  const clase = fila.estado === 'Pagada' ? 'ok' : fila.estado === 'Vencida' ? 'critico' : 'aviso'
  const icono = fila.estado === 'Pagada' ? '✓' : fila.estado === 'Vencida' ? '!' : '…'
  return (
    <span className={`estado ${clase}`}>
      <span aria-hidden="true">{icono}</span> {fila.estado}
      {fila.estado === 'Vencida' && fila.diasVencida > 0 && <span className="estado-dias"> · {fila.diasVencida} días</span>}
    </span>
  )
}

function FilaTotales({ etiqueta, totales }) {
  if (!totales) return null
  return (
    <tr>
      <th scope="row">{etiqueta}</th>
      <td className="num">{moneda(totales.emitido)}</td>
      <td className="num">{moneda(totales.pagado)}</td>
      <td className="num">{moneda(totales.deuda)}</td>
      <td className="num">{moneda(totales.deudaVencida)}</td>
      <td className="num">{moneda(totales.interesMoratorio)}</td>
      <td className="num"><strong>{moneda(totales.totalAPagar)}</strong></td>
    </tr>
  )
}

function ResumenAnual({ resumen, timMensual }) {
  if (!resumen) return null
  return (
    <section className="pcard" aria-labelledby="titulo-resumen-anual">
      <h2 id="titulo-resumen-anual">Resumen {resumen.anio}: Impuesto Predial y arbitrios</h2>
      <p className="hint">
        El Impuesto Predial se calcula sobre el valor de todos los predios del contribuyente y se paga en 4 cuotas;
        los arbitrios se emiten por predio y por mes. El interés moratorio corre sobre lo vencido
        (TIM {Number(timMensual ?? 0).toLocaleString('es-PE')} % mensual).
      </p>
      <div className="tabla-scroll" role="region" aria-label={`Resumen del año ${resumen.anio}`} tabIndex={0}>
        <table className="tabla">
          <thead>
            <tr>
              <th scope="col">Tributo</th>
              <th scope="col" className="num">Emitido</th>
              <th scope="col" className="num">Pagado</th>
              <th scope="col" className="num">Deuda</th>
              <th scope="col" className="num">Deuda vencida</th>
              <th scope="col" className="num">Interés moratorio</th>
              <th scope="col" className="num">Total a pagar</th>
            </tr>
          </thead>
          <tbody>
            <FilaTotales etiqueta="Impuesto Predial" totales={resumen.impuestoPredial} />
            <FilaTotales etiqueta="Arbitrios municipales" totales={resumen.arbitrios} />
          </tbody>
          <tfoot>
            <FilaTotales etiqueta="Total del año" totales={resumen.total} />
          </tfoot>
        </table>
      </div>
    </section>
  )
}

function TablaPredial({ cuotas, anio }) {
  return (
    <section className="pcard" aria-labelledby="titulo-predial">
      <h2 id="titulo-predial">Impuesto Predial {anio}</h2>
      {cuotas.length === 0 ? (
        <p className="hint">
          No hay Impuesto Predial emitido en {anio}: el contribuyente no tenía predios afectos al 1 de enero o goza
          de la deducción de pensionista.
        </p>
      ) : (
        <div className="tabla-scroll" role="region" aria-label={`Cuotas del Impuesto Predial ${anio}`} tabIndex={0}>
          <table className="tabla">
            <thead>
              <tr>
                <th scope="col">Cuota</th>
                <th scope="col">Vence</th>
                <th scope="col" className="num">Emitido</th>
                <th scope="col" className="num">Pagado</th>
                <th scope="col" className="num">Deuda</th>
                <th scope="col" className="num">Interés</th>
                <th scope="col">Estado</th>
                <th scope="col">Cobranza</th>
              </tr>
            </thead>
            <tbody>
              {cuotas.map((fila) => (
                <tr key={fila.id}>
                  <td>{etiquetaCuota(fila)}</td>
                  <td>{fecha(fila.fechaVencimiento)}</td>
                  <td className="num">{moneda(fila.emitido)}</td>
                  <td className="num">{moneda(fila.pagado)}</td>
                  <td className="num">{moneda(fila.deuda)}</td>
                  <td className="num">{fila.interesMoratorio > 0 ? moneda(fila.interesMoratorio) : '—'}</td>
                  <td><Estado fila={fila} /></td>
                  <td>{fila.estado === 'Vencida' ? fila.etapaCobranza ?? 'Mora temprana' : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function ArbitriosDePredio({ grupo }) {
  const { predio, filas, totales } = grupo
  return (
    <details className="predio-arbitrios">
      <summary>
        <span className="predio-arbitrios__titulo">
          <strong>{grupo.codigoPredio}</strong>
          <span className="hint"> · {predio.ubicacion ?? 'Predio sin ubicación registrada'}</span>
          {predio.porcentajePropiedad != null && Number(predio.porcentajePropiedad) < 100 && (
            <span className="etiqueta-copropiedad">Copropiedad {Number(predio.porcentajePropiedad)} %</span>
          )}
        </span>
        <span className="predio-arbitrios__montos">
          <span>Emitido {moneda(totales.emitido)}</span>
          <span className={totales.deuda > 0 ? 'texto-critico' : ''}>Deuda {moneda(totales.deuda)}</span>
          {totales.vencidas > 0 && <span className="estado critico">{totales.vencidas} vencidas</span>}
        </span>
      </summary>
      <div className="tabla-scroll" role="region" aria-label={`Arbitrios del predio ${grupo.codigoPredio}`} tabIndex={0}>
        <table className="tabla tabla-compacta">
          <thead>
            <tr>
              <th scope="col">Mes</th>
              <th scope="col">Vence</th>
              {SERVICIOS.map((servicio) => <th key={servicio.codigo} scope="col" className="num">{servicio.corto}</th>)}
              <th scope="col" className="num">Total</th>
              <th scope="col" className="num">Deuda</th>
              <th scope="col" className="num">Interés</th>
              <th scope="col">Estado</th>
            </tr>
          </thead>
          <tbody>
            {filas.map((fila) => (
              <tr key={fila.id}>
                <td>{etiquetaCuota(fila)}</td>
                <td>{fecha(fila.fechaVencimiento)}</td>
                {SERVICIOS.map((servicio) => (
                  <td key={servicio.codigo} className="num">{moneda(montoServicio(fila, servicio.codigo))}</td>
                ))}
                <td className="num">{moneda(fila.emitido)}</td>
                <td className="num">{moneda(fila.deuda)}</td>
                <td className="num">{fila.interesMoratorio > 0 ? moneda(fila.interesMoratorio) : '—'}</td>
                <td><Estado fila={fila} /></td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th scope="row" colSpan={2}>Total del año</th>
              {grupo.porServicio.map((servicio) => <th key={servicio.codigo} className="num">{moneda(servicio.monto)}</th>)}
              <th className="num">{moneda(totales.emitido)}</th>
              <th className="num">{moneda(totales.deuda)}</th>
              <th className="num">{moneda(totales.interesMoratorio)}</th>
              <th />
            </tr>
          </tfoot>
        </table>
      </div>
    </details>
  )
}

function ArbitriosPorPredio({ grupos, anio }) {
  const [texto, setTexto] = useState('')
  const [soloConDeuda, setSoloConDeuda] = useState(false)
  const [pagina, setPagina] = useState(1)
  const filtrados = filtrarGrupos(grupos, texto, soloConDeuda)
  const { visibles, paginaActual: actual, totalPaginas } = paginar(filtrados, pagina, PREDIOS_POR_PAGINA)
  const conFiltros = grupos.length > PREDIOS_PARA_BUSCAR

  return (
    <section className="pcard" aria-labelledby="titulo-arbitrios">
      <h2 id="titulo-arbitrios">Arbitrios {anio} por predio</h2>
      <p className="hint">
        {grupos.length === 0
          ? `No hay arbitrios emitidos en ${anio}.`
          : `${grupos.length} ${grupos.length === 1 ? 'predio' : 'predios'}. Despliega uno para ver sus meses y lo que corresponde a barrido de calles, recolección de residuos, parques y jardines y serenazgo.`}
      </p>
      {conFiltros && (
        <div className="filtros-predios">
          <div className="fld">
            <label htmlFor="buscar-predio-cuenta">Buscar predio</label>
            <input
              id="buscar-predio-cuenta"
              type="search"
              value={texto}
              placeholder="Código o dirección"
              onChange={(e) => { setTexto(e.target.value); setPagina(1) }}
            />
          </div>
          <label className="check">
            <input type="checkbox" checked={soloConDeuda} onChange={(e) => { setSoloConDeuda(e.target.checked); setPagina(1) }} />
            Solo predios con deuda
          </label>
          <span className="hint" role="status">{filtrados.length} de {grupos.length} predios</span>
        </div>
      )}
      {filtrados.length === 0 && grupos.length > 0 && <p className="hint">Ningún predio coincide con el filtro.</p>}
      {visibles.map((grupo) => <ArbitriosDePredio key={grupo.codigoPredio} grupo={grupo} />)}
      {totalPaginas > 1 && (
        <nav className="paginacion" aria-label="Páginas de predios">
          <button className="btn" type="button" disabled={actual <= 1} onClick={() => setPagina(actual - 1)}>← Anterior</button>
          <span>Página {actual} de {totalPaginas}</span>
          <button className="btn" type="button" disabled={actual >= totalPaginas} onClick={() => setPagina(actual + 1)}>Siguiente →</button>
        </nav>
      )}
    </section>
  )
}

export default function DetalleCuenta({ cuenta }) {
  const anios = aniosDeCuenta(cuenta)
  const [anio, setAnio] = useState(() => anioInicial(cuenta))
  const { contribuyente: c, fechaCorte, desactualizado } = cuenta
  const deudaTotal = (cuenta.resumenAnual ?? []).reduce((t, r) => t + Number(r.total.deuda), 0)
  const deudaVencida = (cuenta.resumenAnual ?? []).reduce((t, r) => t + Number(r.total.deudaVencida), 0)
  const interes = (cuenta.resumenAnual ?? []).reduce((t, r) => t + Number(r.total.interesMoratorio), 0)

  return (
    <>
      <section className="pcard" aria-label="Contribuyente">
        <h2>{c.nombre}</h2>
        <p className="hint">Código {c.codigo} · {c.tipoDoc} {c.doc}</p>
        <p className="corte"><strong>Fecha de corte:</strong> {fecha(fechaCorte)}</p>
        {desactualizado && (
          <div className="alert" role="alert">
            <strong>SAMNET no responde. </strong>
            Se muestra la última información disponible, con fecha de corte {fecha(fechaCorte)}.
          </div>
        )}
        {anios.length === 0 ? (
          <div className="info" role="status">El contribuyente no tiene cuotas emitidas.</div>
        ) : (
          <div className="resumen">
            <div><span className="pl">Deuda total</span><strong>{moneda(deudaTotal)}</strong></div>
            <div className={deudaVencida > 0 ? 'deuda' : ''}><span className="pl">Deuda vencida</span><strong>{moneda(deudaVencida)}</strong></div>
            <div className={interes > 0 ? 'deuda' : ''}><span className="pl">Interés moratorio</span><strong>{moneda(interes)}</strong></div>
            <div><span className="pl">Por pagar hoy (vencido + interés)</span><strong>{moneda(deudaVencida + interes)}</strong></div>
          </div>
        )}
      </section>

      {anios.length > 0 && (
        <>
          <div className="selector-anio" role="group" aria-label="Año de la cuenta">
            {anios.map((a) => (
              <button key={a} type="button" className={`btn${a === anio ? ' primary' : ''}`} aria-pressed={a === anio}
                onClick={() => setAnio(a)}>
                {a}
              </button>
            ))}
          </div>
          <ResumenAnual resumen={resumenDelAnio(cuenta, anio)} timMensual={cuenta.timMensual} />
          <TablaPredial cuotas={cuotasPredial(cuenta, anio)} anio={anio} />
          <ArbitriosPorPredio key={anio} grupos={arbitriosPorPredio(cuenta, anio)} anio={anio} />
        </>
      )}
    </>
  )
}
