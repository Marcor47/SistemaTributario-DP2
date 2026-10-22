import { useState } from 'react'
import { buscarContribuyentes, obtenerEstadoCuenta } from '../services/cuentaService.js'
import { TRIBUTOS } from '../mocks/cuentas.js'
import { fecha, moneda } from '../utils/formato.js'

const suma = (filas, campo) => filas.reduce((a, f) => a + f[campo], 0)

function Estado({ valor }) {
  const clase = valor === 'Pagada' ? 'ok' : valor === 'Vencida' ? 'critico' : 'aviso'
  const icono = valor === 'Pagada' ? '✓' : valor === 'Vencida' ? '!' : '…'
  return <span className={`estado ${clase}`}><span aria-hidden="true">{icono}</span> {valor}</span>
}

function Cuenta({ cuenta, onVolver }) {
  const { contribuyente: c, filas, fechaCorte, desactualizado } = cuenta
  return (
    <>
      {onVolver && (
        <button className="btn" type="button" onClick={onVolver}>← Volver a los resultados</button>
      )}

      <section className="pcard" aria-label="Contribuyente">
        <h2>{c.nombre}</h2>
        <p className="hint">Código {c.codigo} · {c.tipoDoc} {c.doc}</p>
        <p className="corte"><strong>Fecha de corte:</strong> {fecha(fechaCorte)}</p>
        {desactualizado && (
          <div className="alert" role="alert">
            <strong>El servicio de cuenta corriente no responde. </strong>
            Se muestra la última información disponible, con fecha de corte {fecha(fechaCorte)}.
          </div>
        )}
        <div className="resumen">
          <div><span className="pl">Emitido</span><strong>{moneda(suma(filas, 'emitido'))}</strong></div>
          <div><span className="pl">Pagado</span><strong>{moneda(suma(filas, 'pagado'))}</strong></div>
          <div className={suma(filas, 'deuda') > 0 ? 'deuda' : ''}>
            <span className="pl">Deuda total</span><strong>{moneda(suma(filas, 'deuda'))}</strong>
          </div>
        </div>
      </section>

      {TRIBUTOS.map((tributo) => {
        const delTributo = filas.filter((f) => f.tributo === tributo)
        return (
          <section className="pcard" key={tributo} aria-label={tributo}>
            <h2>{tributo}</h2>
            <table className="tabla">
              <thead>
                <tr>
                  <th>Periodo</th>
                  <th className="num">Emitido</th>
                  <th className="num">Pagado</th>
                  <th className="num">Deuda</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {delTributo.map((f) => (
                  <tr key={f.id}>
                    <td>{f.periodo}</td>
                    <td className="num">{moneda(f.emitido)}</td>
                    <td className="num">{moneda(f.pagado)}</td>
                    <td className="num">{moneda(f.deuda)}</td>
                    <td><Estado valor={f.estado} /></td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th>Total</th>
                  <th className="num">{moneda(suma(delTributo, 'emitido'))}</th>
                  <th className="num">{moneda(suma(delTributo, 'pagado'))}</th>
                  <th className="num">{moneda(suma(delTributo, 'deuda'))}</th>
                  <th />
                </tr>
              </tfoot>
            </table>
          </section>
        )
      })}
    </>
  )
}

export default function EstadoCuenta({ onSeleccionarContribuyente, renderDetalle }) {
  const [filtros, setFiltros] = useState({ codigo: '', documento: '', nombre: '' })
  const [consulta, setConsulta] = useState('')
  const [lista, setLista] = useState(null) // contribuyentes encontrados; null = aún no se busca
  const [cuenta, setCuenta] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [servicioCaido, setServicioCaido] = useState(false)
  const [error, setError] = useState('')

  function actualizarCuenta(nuevaCuenta) {
    setCuenta(nuevaCuenta)
    onSeleccionarContribuyente?.(nuevaCuenta?.contribuyente ?? null)
  }

  async function cargarCuenta(codigo) {
    setOcupado(true)
    setError('')
    try {
      actualizarCuenta(await obtenerEstadoCuenta(codigo, { servicioCaido }))
    } catch {
      setError('No se pudo obtener el estado de cuenta. Intenta nuevamente.')
    } finally {
      setOcupado(false)
    }
  }

  async function buscar(e) {
    if (e) e.preventDefault()
    const { codigo, documento, nombre } = filtros
    if (!codigo.trim() && !documento.trim() && !nombre.trim()) return

    setOcupado(true)
    const partes = [
      codigo.trim() && `Código: ${codigo.trim()}`,
      documento.trim() && `Doc: ${documento.trim()}`,
      nombre.trim() && `Nombre: ${nombre.trim()}`,
    ].filter(Boolean)
    setConsulta(partes.join(' | '))
    actualizarCuenta(null)
    setError('')
    try {
      const encontrados = await buscarContribuyentes(filtros)
      setLista(encontrados)
      if (encontrados.length === 1) await cargarCuenta(encontrados[0].codigo)
    } finally {
      setOcupado(false)
    }
  }

  function limpiar() {
    setFiltros({ codigo: '', documento: '', nombre: '' })
    setLista(null)
    actualizarCuenta(null)
    setConsulta('')
    setError('')
  }

  const tieneCriterios = Boolean(filtros.codigo.trim() || filtros.documento.trim() || filtros.nombre.trim())
  const eligiendo = !ocupado && !cuenta && lista?.length > 1
  const volver = lista?.length > 1 ? () => actualizarCuenta(null) : null
  const detalle = cuenta && <Cuenta cuenta={cuenta} onVolver={volver} />

  return (
    <>
      <form className="filters-separados" onSubmit={buscar} role="search">
        <div className="filters-header">
          <h3>Buscar Contribuyente</h3>
          <p className="hint">Ingresa uno o más criterios de búsqueda:</p>
        </div>
        <div className="filters-fields">
          <div className="fld">
            <label htmlFor="busq-codigo">Código</label>
            <input
              id="busq-codigo"
              type="text"
              value={filtros.codigo}
              placeholder="ej. 021390"
              onChange={(e) => setFiltros((prev) => ({ ...prev, codigo: e.target.value }))}
            />
          </div>
          <div className="fld">
            <label htmlFor="busq-doc">DNI / RUC</label>
            <input
              id="busq-doc"
              type="text"
              value={filtros.documento}
              placeholder="ej. 41028765 o 20549812341"
              onChange={(e) => setFiltros((prev) => ({ ...prev, documento: e.target.value }))}
            />
          </div>
          <div className="fld">
            <label htmlFor="busq-nombre">Nombre o Razón Social</label>
            <input
              id="busq-nombre"
              type="text"
              value={filtros.nombre}
              placeholder="ej. García Torres o Inversiones S.A.C."
              onChange={(e) => setFiltros((prev) => ({ ...prev, nombre: e.target.value }))}
            />
          </div>
          <div className="filters-actions">
            <button className="btn primary" type="submit" disabled={ocupado || !tieneCriterios}>
              Buscar
            </button>
            <button className="btn" type="button" onClick={limpiar} disabled={ocupado}>
              Limpiar
            </button>
          </div>
        </div>
      </form>

      <label className="demo-opcion">
        <input type="checkbox" checked={servicioCaido} onChange={(e) => setServicioCaido(e.target.checked)} />
        Simular que el servicio de cuenta corriente no responde (solo demostración)
      </label>

      {ocupado && <p className="hint" role="status">Consultando SAMNET…</p>}
      {error && <div className="alert" role="alert">{error}</div>}

      {!ocupado && lista === null && (
        <div className="info">
          Busca un contribuyente por código o documento para ver su estado de cuenta, desglosado por tributo y periodo.
        </div>
      )}

      {!ocupado && lista?.length === 0 && (
        <div className="alert" role="status">
          <strong>No se encontraron resultados. </strong>
          No existe ningún contribuyente para «{consulta}» en los datos de demostración.
        </div>
      )}

      {eligiendo && (
        <section className="pcard" aria-label="Resultados">
          <h2>{lista.length} contribuyentes encontrados</h2>
          <p className="hint">Elige uno para ver su estado de cuenta.</p>
          <div className="plist">
            {lista.map((c) => (
              <button key={c.codigo} type="button" className="presrow" onClick={() => cargarCuenta(c.codigo)}>
                <strong>{c.nombre}</strong>
                <span className="hint">Código {c.codigo} · {c.tipoDoc} {c.doc}</span>
              </button>
            ))}
          </div>
        </section>
      )}

      {!ocupado && cuenta && (
        renderDetalle ? renderDetalle(detalle, volver) : detalle
      )}
    </>
  )
}
