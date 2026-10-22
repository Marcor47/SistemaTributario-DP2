import { useEffect, useRef, useState } from 'react'
import { buscarCoincidencias } from '../services/contribuyenteService.js'
import { obtenerEstadoCuenta } from '../services/cuentaService.js'
import { TRIBUTOS } from '../mocks/cuentas.js'
import { MINIMO_BUSQUEDA, TIPOS_DOCUMENTO, limpiarDocumento, validarDocumento } from '../utils/documento.js'
import { fecha, moneda } from '../utils/formato.js'

const suma = (filas, campo) => filas.reduce((a, f) => a + f[campo], 0)

function Estado({ valor }) {
  const clase = valor === 'Pagada' ? 'ok' : valor === 'Vencida' ? 'critico' : 'aviso'
  const icono = valor === 'Pagada' ? '✓' : valor === 'Vencida' ? '!' : '…'
  return <span className={`estado ${clase}`}><span aria-hidden="true">{icono}</span> {valor}</span>
}

function Cuenta({ cuenta }) {
  const { contribuyente: c, filas, fechaCorte, desactualizado } = cuenta
  return (
    <>
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
        {filas.length === 0 ? (
          <div className="info" role="status">
            No hay información de estado de cuenta disponible para este contribuyente en la demostración.
          </div>
        ) : (
          <div className="resumen">
            <div><span className="pl">Emitido</span><strong>{moneda(suma(filas, 'emitido'))}</strong></div>
            <div><span className="pl">Pagado</span><strong>{moneda(suma(filas, 'pagado'))}</strong></div>
            <div className={suma(filas, 'deuda') > 0 ? 'deuda' : ''}>
              <span className="pl">Deuda total</span><strong>{moneda(suma(filas, 'deuda'))}</strong>
            </div>
          </div>
        )}
      </section>

      {filas.length > 0 && TRIBUTOS.map((tributo) => {
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
  const [tipoDocumento, setTipoDocumento] = useState('DNI')
  const [documento, setDocumento] = useState('')
  const [consulta, setConsulta] = useState('')
  const [lista, setLista] = useState(null) // coincidencias; null = aún no se busca
  const [cuenta, setCuenta] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [servicioCaido, setServicioCaido] = useState(false)
  const [error, setError] = useState('')
  const [errorDocumento, setErrorDocumento] = useState('')
  const [abierto, setAbierto] = useState(false)
  const [activo, setActivo] = useState(-1)
  const [cargandoCuenta, setCargandoCuenta] = useState(false)
  const temporizador = useRef(null)
  const solicitud = useRef(0)
  const buscarDocumentoRef = useRef(null)
  const documentoSeleccionado = useRef(null)

  const tipo = TIPOS_DOCUMENTO[tipoDocumento]

  function actualizarCuenta(nuevaCuenta) {
    setCuenta(nuevaCuenta)
    onSeleccionarContribuyente?.(nuevaCuenta?.contribuyente ?? null)
  }

  async function buscarDocumento(tipoDocumentoBuscado, numero) {
    const actual = ++solicitud.current
    setOcupado(true)
    setConsulta(`${tipoDocumentoBuscado} ${numero}`)
    actualizarCuenta(null)
    setLista(null)
    setAbierto(false)
    setActivo(-1)
    setError('')
    setErrorDocumento('')
    try {
      const encontrados = await buscarCoincidencias(tipoDocumentoBuscado, numero)
      if (actual === solicitud.current) {
        setLista(encontrados)
        setAbierto(encontrados.length > 0)
        setActivo(encontrados.length > 0 ? 0 : -1)
      }
    } catch (err) {
      if (actual === solicitud.current) {
        setLista([])
        setAbierto(false)
        setActivo(-1)
        setError(err.message || 'No se pudo consultar el padrón. Intenta nuevamente.')
      }
    } finally {
      if (actual === solicitud.current) setOcupado(false)
    }
  }

  useEffect(() => {
    buscarDocumentoRef.current = buscarDocumento
  })

  useEffect(() => {
    if (documentoSeleccionado.current === documento) {
      documentoSeleccionado.current = null
      return undefined
    }
    if (documento.length < MINIMO_BUSQUEDA) return undefined
    if (validarDocumento(tipoDocumento, documento)) return undefined

    const timer = setTimeout(() => {
      temporizador.current = null
      buscarDocumentoRef.current?.(tipoDocumento, documento)
    }, 400)
    temporizador.current = timer

    return () => {
      clearTimeout(timer)
      if (temporizador.current === timer) temporizador.current = null
    }
  }, [tipoDocumento, documento])

  function cancelarPendiente() {
    if (temporizador.current) {
      clearTimeout(temporizador.current)
      temporizador.current = null
    }
    solicitud.current++
  }

  async function cargarCuenta(contribuyente) {
    setCargandoCuenta(true)
    setError('')
    try {
      actualizarCuenta(await obtenerEstadoCuenta(contribuyente, { servicioCaido }))
    } catch {
      setError('No se pudo obtener el estado de cuenta. Intenta nuevamente.')
    } finally {
      setCargandoCuenta(false)
    }
  }

  function seleccionarCoincidencia(contribuyente) {
    cancelarPendiente()
    if (contribuyente.doc !== documento) {
      documentoSeleccionado.current = contribuyente.doc
      setDocumento(contribuyente.doc)
    }
    setErrorDocumento('')
    setAbierto(false)
    setActivo(-1)
    cargarCuenta(contribuyente)
  }

  function abrirDropdown() {
    if (lista?.length > 0) {
      setAbierto(true)
      setActivo((actual) => (actual >= 0 ? actual : 0))
    }
  }

  function cerrarDropdown() {
    setAbierto(false)
    setActivo(-1)
  }

  function manejarTeclado(e) {
    if (e.key === 'Escape') {
      cerrarDropdown()
      return
    }
    if (e.key === 'Tab') {
      cerrarDropdown()
      return
    }
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!lista?.length) return
      e.preventDefault()
      if (!abierto) {
        setAbierto(true)
        setActivo(e.key === 'ArrowDown' ? 0 : lista.length - 1)
        return
      }
      setActivo((actual) => {
        if (e.key === 'ArrowDown') return (actual + 1) % lista.length
        return (actual - 1 + lista.length) % lista.length
      })
      return
    }
    if (e.key === 'Enter' && abierto && activo >= 0 && lista?.[activo]) {
      e.preventDefault()
      seleccionarCoincidencia(lista[activo])
    }
  }

  function cerrarSiSale(e) {
    if (!e.currentTarget.contains(e.relatedTarget)) cerrarDropdown()
  }

  function buscar(e) {
    if (e) e.preventDefault()
    const numero = limpiarDocumento(documento, tipoDocumento)
    const problema = validarDocumento(tipoDocumento, numero)
    if (problema) {
      setErrorDocumento(problema)
      return
    }
    cancelarPendiente()
    buscarDocumento(tipoDocumento, numero)
  }

  function cambiarDocumento(e) {
    const numero = limpiarDocumento(e.target.value, tipoDocumento)
    documentoSeleccionado.current = null
    cancelarPendiente()
    setDocumento(numero)
    setLista(null)
    setAbierto(false)
    setActivo(-1)
    setConsulta('')
    setError('')
    setErrorDocumento(numero.length >= MINIMO_BUSQUEDA ? validarDocumento(tipoDocumento, numero) : '')
    actualizarCuenta(null)
  }

  function cambiarTipo(e) {
    documentoSeleccionado.current = null
    cancelarPendiente()
    const nuevoTipo = e.target.value
    setTipoDocumento(nuevoTipo)
    setDocumento('')
    setLista(null)
    setAbierto(false)
    setActivo(-1)
    setConsulta('')
    setError('')
    setErrorDocumento('')
    actualizarCuenta(null)
  }

  function limpiar() {
    documentoSeleccionado.current = null
    cancelarPendiente()
    setDocumento('')
    setLista(null)
    setAbierto(false)
    setActivo(-1)
    actualizarCuenta(null)
    setConsulta('')
    setError('')
    setErrorDocumento('')
  }

  const tieneCriterios = documento.length >= MINIMO_BUSQUEDA
  const detalle = cuenta && <Cuenta cuenta={cuenta} />

  return (
    <>
      <form className="filters-separados" onSubmit={buscar} role="search">
        <div className="filters-header">
          <h3>Buscar contribuyente</h3>
          <p className="hint">Ingresa el tipo y el número de documento. Puedes buscar con una parte del número.</p>
        </div>
        <div className="filters-fields">
          <div className="fld fld-tipo-doc">
            <label htmlFor="tipo-documento">Tipo de documento</label>
            <select id="tipo-documento" value={tipoDocumento} onChange={cambiarTipo} disabled={cargandoCuenta}>
              <option value="DNI">DNI</option>
              <option value="RUC">RUC</option>
              <option value="CE">CE</option>
            </select>
          </div>
          <div className="fld fld-numero-doc">
            <label htmlFor="numero-documento">Número de documento</label>
            <div className="documento-combobox" onBlur={cerrarSiSale}>
              <input
                id="numero-documento"
                type="text"
                inputMode={tipoDocumento === 'CE' ? 'text' : 'numeric'}
                autoComplete="off"
                maxLength={tipo.maximo}
                value={documento}
                placeholder={tipoDocumento === 'CE' ? 'Ej. 001234567' : tipoDocumento === 'RUC' ? 'Ej. 20456781239' : 'Ej. 41028765'}
                role="combobox"
                aria-autocomplete="list"
                aria-expanded={abierto}
                aria-controls="coincidencias-documento"
                aria-activedescendant={abierto && activo >= 0 && lista?.[activo] ? `coincidencia-${lista[activo].codigo}` : undefined}
                aria-invalid={Boolean(errorDocumento)}
                aria-describedby={`documento-ayuda${errorDocumento ? ' documento-error' : ''}`}
                disabled={cargandoCuenta}
                onFocus={abrirDropdown}
                onKeyDown={manejarTeclado}
                onChange={cambiarDocumento}
              />
              {abierto && lista?.length > 0 && (
                <ul id="coincidencias-documento" className="documento-opciones" role="listbox" aria-label="Coincidencias de documento">
                  {lista.map((c, indice) => (
                    <li
                      id={`coincidencia-${c.codigo}`}
                      key={c.codigo}
                      role="option"
                      aria-selected={indice === activo}
                      className={indice === activo ? 'activa' : ''}
                      onMouseDown={(e) => e.preventDefault()}
                      onMouseEnter={() => setActivo(indice)}
                      onClick={() => seleccionarCoincidencia(c)}
                    >
                      <strong>{c.nombre}</strong>
                      <span className="hint">{c.tipoDoc} {c.doc}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
          <div className="filters-actions">
            <button className="btn primary" type="submit" disabled={ocupado || cargandoCuenta || !tieneCriterios}>
              Buscar
            </button>
            <button className="btn" type="button" onClick={limpiar} disabled={cargandoCuenta}>
              Limpiar
            </button>
          </div>
        </div>
        <div className="documento-mensajes">
          <span id="documento-ayuda" className="hint">{tipo.ayuda}</span>
          {errorDocumento && <span id="documento-error" className="campo-error" role="alert">{errorDocumento}</span>}
        </div>
      </form>

      <label className="demo-opcion">
        <input type="checkbox" checked={servicioCaido} onChange={(e) => setServicioCaido(e.target.checked)} />
        Simular que el servicio de cuenta corriente no responde (solo demostración)
      </label>

      {(ocupado || cargandoCuenta) && <p className="hint" role="status">Consultando SAMNET…</p>}
      {error && <div className="alert" role="alert">{error}</div>}

      {!ocupado && !cargandoCuenta && lista === null && (
        <div className="info">
          Busca un contribuyente por DNI, RUC o carné de extranjería. La búsqueda admite parte del número de documento.
        </div>
      )}

      {!ocupado && !error && lista?.length === 0 && (
        <div className="alert" role="status">
          <strong>No se encontraron coincidencias. </strong>
          No existe un contribuyente con {consulta} en el padrón.
        </div>
      )}

      {!ocupado && cuenta && (
        renderDetalle ? renderDetalle(detalle) : detalle
      )}
    </>
  )
}
