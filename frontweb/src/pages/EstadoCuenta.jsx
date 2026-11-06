import { useEffect, useRef, useState } from 'react'
import { buscarCoincidencias } from '../services/contribuyenteService.js'
import { obtenerEstadoCuenta } from '../services/cuentaService.js'
import DetalleCuenta from '../components/DetalleCuenta.jsx'
import { MINIMO_BUSQUEDA, TIPOS_DOCUMENTO, limpiarDocumento, validarDocumento } from '../utils/documento.js'

const TIPO_CODIGO = { maximo: 20, ayuda: 'Código de contribuyente. Puedes buscar con parte del código.' }

function limpiarBusqueda(valor, tipo) {
  return tipo === 'CODIGO'
    ? String(valor ?? '').replace(/[^a-zA-Z0-9-]/g, '').toUpperCase().slice(0, TIPO_CODIGO.maximo)
    : limpiarDocumento(valor, tipo)
}

function validarBusqueda(tipo, valor) {
  if (tipo === 'CODIGO') return valor.length < MINIMO_BUSQUEDA ? `Ingresa al menos ${MINIMO_BUSQUEDA} caracteres del código.` : ''
  return validarDocumento(tipo, valor)
}

// Si la cuenta no se pudo cargar, el contribuyente sigue seleccionado (ficha, pagos, cobranza) y el error se muestra
// solo en la pestaña del estado de cuenta.
function Cuenta({ cuenta }) {
  if (cuenta.error) {
    return (
      <section className="pcard" aria-label="Estado de cuenta">
        <h2>{cuenta.contribuyente.nombre}</h2>
        <div className="alert" role="alert">{cuenta.error}</div>
      </section>
    )
  }
  return <DetalleCuenta key={cuenta.contribuyente.codigo} cuenta={cuenta} />
}

export default function EstadoCuenta({ onSeleccionarContribuyente, renderDetalle, permitirCodigo = false }) {
  const [tipoDocumento, setTipoDocumento] = useState('DNI')
  const [documento, setDocumento] = useState('')
  const [consulta, setConsulta] = useState('')
  const [lista, setLista] = useState(null) // coincidencias; null = aún no se busca
  const [cuenta, setCuenta] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')
  const [errorDocumento, setErrorDocumento] = useState('')
  const [abierto, setAbierto] = useState(false)
  const [activo, setActivo] = useState(-1)
  const [cargandoCuenta, setCargandoCuenta] = useState(false)
  const temporizador = useRef(null)
  const solicitud = useRef(0)
  const buscarDocumentoRef = useRef(null)
  const documentoSeleccionado = useRef(null)

  const tipo = tipoDocumento === 'CODIGO' ? TIPO_CODIGO : TIPOS_DOCUMENTO[tipoDocumento]

  function actualizarCuenta(nuevaCuenta) {
    setCuenta(nuevaCuenta)
    onSeleccionarContribuyente?.(nuevaCuenta?.contribuyente ?? null)
  }

  async function buscarDocumento(tipoDocumentoBuscado, numero) {
    const actual = ++solicitud.current
    setOcupado(true)
    setConsulta(`${tipoDocumentoBuscado === 'CODIGO' ? 'código' : tipoDocumentoBuscado} ${numero}`)
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
    if (validarBusqueda(tipoDocumento, documento)) return undefined

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
      actualizarCuenta(await obtenerEstadoCuenta(contribuyente))
    } catch (err) {
      actualizarCuenta({
        contribuyente,
        error: err.estado === 403
          ? 'Tu rol no tiene permiso para consultar estados de cuenta.'
          : err.message || 'No se pudo obtener el estado de cuenta. Intenta nuevamente.',
      })
    } finally {
      setCargandoCuenta(false)
    }
  }

  function seleccionarCoincidencia(contribuyente) {
    cancelarPendiente()
    const valorSeleccionado = tipoDocumento === 'CODIGO' ? contribuyente.codigo : contribuyente.doc
    if (valorSeleccionado !== documento) {
      documentoSeleccionado.current = valorSeleccionado
      setDocumento(valorSeleccionado)
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
    const numero = limpiarBusqueda(documento, tipoDocumento)
    const problema = validarBusqueda(tipoDocumento, numero)
    if (problema) {
      setErrorDocumento(problema)
      return
    }
    cancelarPendiente()
    buscarDocumento(tipoDocumento, numero)
  }

  function cambiarDocumento(e) {
    const numero = limpiarBusqueda(e.target.value, tipoDocumento)
    documentoSeleccionado.current = null
    cancelarPendiente()
    setDocumento(numero)
    setLista(null)
    setAbierto(false)
    setActivo(-1)
    setConsulta('')
    setError('')
    setErrorDocumento(numero.length >= MINIMO_BUSQUEDA ? validarBusqueda(tipoDocumento, numero) : '')
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
          <p className="hint">{permitirCodigo ? 'Busca por código de contribuyente o documento para consultar su información, pagos y beneficios.' : 'Busca por tipo y número de documento para consultar su ficha, predios, estado de cuenta y cobranza.'}</p>
        </div>
        <div className="filters-fields">
          <div className="fld fld-tipo-doc">
            <label htmlFor="tipo-documento">{permitirCodigo ? 'Buscar por' : 'Tipo de documento'}</label>
            <select id="tipo-documento" value={tipoDocumento} onChange={cambiarTipo} disabled={cargandoCuenta}>
              <option value="DNI">DNI</option>
              <option value="RUC">RUC</option>
              <option value="CE">CE</option>
              {permitirCodigo && <option value="CODIGO">Código</option>}
            </select>
          </div>
          <div className="fld fld-numero-doc">
            <label htmlFor="numero-documento">{tipoDocumento === 'CODIGO' ? 'Código de contribuyente' : 'Número de documento'}</label>
            <div className="documento-combobox" onBlur={cerrarSiSale}>
              <input
                id="numero-documento"
                type="text"
                inputMode={tipoDocumento === 'CE' || tipoDocumento === 'CODIGO' ? 'text' : 'numeric'}
                autoComplete="off"
                maxLength={tipo.maximo}
                value={documento}
                placeholder={tipoDocumento === 'CODIGO' ? 'Ej. 021390' : tipoDocumento === 'CE' ? 'Ej. 001234567' : tipoDocumento === 'RUC' ? 'Ej. 20456781239' : 'Ej. 41028765'}
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
                <ul id="coincidencias-documento" className="documento-opciones" role="listbox" aria-label="Coincidencias de contribuyentes">
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
                      <span className="hint">{permitirCodigo && `Código ${c.codigo} · `}{c.tipoDoc} {c.doc}</span>
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

      {(ocupado || cargandoCuenta) && <p className="hint" role="status">Consultando SAMNET…</p>}
      {error && <div className="alert" role="alert">{error}</div>}

      {!ocupado && !cargandoCuenta && lista === null && (
        <div className="info">
          {permitirCodigo ? 'Busca un contribuyente por código, DNI, RUC o carné de extranjería. Puedes buscar con parte del código o documento.' : 'Busca un contribuyente por DNI, RUC o carné de extranjería. La búsqueda admite parte del número de documento.'}
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
