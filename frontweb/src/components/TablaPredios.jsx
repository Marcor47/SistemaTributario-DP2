import { Fragment, useMemo, useState } from 'react'
import { fecha } from '../utils/formato.js'
import {
  CONDICION_PROPIEDAD,
  FILTROS_PREDIOS_INICIALES,
  TAMANIOS_PAGINA,
  TIPO_PREDIO,
  filtrarPredios,
  opcionesDePredios,
  paginarPredios,
} from '../utils/predios.js'
import './TablaPredios.css'

function texto(valor) {
  return valor === '' || valor == null ? '—' : valor
}

function medida(valor, sufijo) {
  const numero = Number(valor)
  if (valor === '' || valor == null || !Number.isFinite(numero)) return '—'
  return `${numero.toLocaleString('es-PE', { maximumFractionDigits: 2 })} ${sufijo}`
}

function DatoDetalle({ etiqueta, valor }) {
  return (
    <div>
      <dt>{etiqueta}</dt>
      <dd>{texto(valor)}</dd>
    </div>
  )
}

export default function TablaPredios({ predios = [] }) {
  const [busqueda, setBusqueda] = useState('')
  const [filtros, setFiltros] = useState(FILTROS_PREDIOS_INICIALES)
  const [porPagina, setPorPagina] = useState(TAMANIOS_PAGINA[0])
  const [pagina, setPagina] = useState(1)
  const [abierto, setAbierto] = useState(null)

  const opciones = useMemo(() => opcionesDePredios(predios), [predios])
  const filtrados = useMemo(
    () => filtrarPredios(predios, busqueda, filtros),
    [predios, busqueda, filtros],
  )
  const { visibles, paginaActual, totalPaginas, inicio, fin } = paginarPredios(filtrados, pagina, porPagina)
  const hayFiltros = Object.values(filtros).some(Boolean)

  function cambiarBusqueda(evento) {
    setBusqueda(evento.target.value)
    setPagina(1)
    setAbierto(null)
  }

  function limpiarBusqueda() {
    setBusqueda('')
    setPagina(1)
    setAbierto(null)
  }

  function cambiarFiltro(evento) {
    const { name, value } = evento.target
    setFiltros((actuales) => ({ ...actuales, [name]: value }))
    setPagina(1)
    setAbierto(null)
  }

  function limpiarFiltros() {
    setFiltros(FILTROS_PREDIOS_INICIALES)
    setPagina(1)
    setAbierto(null)
  }

  function cambiarPorPagina(evento) {
    setPorPagina(Number(evento.target.value))
    setPagina(1)
    setAbierto(null)
  }

  function cambiarPagina(nueva) {
    setPagina(nueva)
    setAbierto(null)
  }

  function alternar(codigo) {
    setAbierto((actual) => (actual === codigo ? null : codigo))
  }

  return (
    <div className="tabla-predios-contenedor">
      <div className="predios-herramientas">
        <div className="fld predios-buscador">
          <label htmlFor="buscar-predio">Buscar predio</label>
          <div className="predios-buscador__campo">
            <input
              id="buscar-predio"
              type="search"
              value={busqueda}
              onChange={cambiarBusqueda}
              placeholder="Código o dirección"
              autoComplete="off"
            />
            {busqueda && (
              <button className="btn predios-limpiar" type="button" onClick={limpiarBusqueda}>
                Limpiar
              </button>
            )}
          </div>
        </div>
        <div className="fld predios-tamano">
          <label htmlFor="predios-por-pagina">Filas por página</label>
          <select id="predios-por-pagina" value={porPagina} onChange={cambiarPorPagina}>
            {TAMANIOS_PAGINA.map((tamano) => (
              <option key={tamano} value={tamano}>{tamano}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="predios-filtros" role="group" aria-label="Filtros de predios">
        <div className="fld">
          <label htmlFor="predio-tipo">Tipo</label>
          <select id="predio-tipo" name="tipo" value={filtros.tipo} onChange={cambiarFiltro}>
            <option value="">Todos los tipos</option>
            {opciones.tipos.map((tipo) => (
              <option key={tipo.valor} value={tipo.valor}>{tipo.etiqueta}</option>
            ))}
          </select>
        </div>
        <div className="fld">
          <label htmlFor="predio-uso">Uso</label>
          <select id="predio-uso" name="uso" value={filtros.uso} onChange={cambiarFiltro}>
            <option value="">Todos los usos</option>
            {opciones.usos.map((uso) => (
              <option key={uso.valor} value={uso.valor}>{uso.etiqueta}</option>
            ))}
          </select>
        </div>
        <div className="fld">
          <label htmlFor="predio-condicion">Condición de propiedad</label>
          <select id="predio-condicion" name="condicion" value={filtros.condicion} onChange={cambiarFiltro}>
            <option value="">Todas las condiciones</option>
            {opciones.condiciones.map((condicion) => (
              <option key={condicion.valor} value={condicion.valor}>{condicion.etiqueta}</option>
            ))}
          </select>
        </div>
        {hayFiltros && (
          <button className="btn predios-limpiar-filtros" type="button" onClick={limpiarFiltros}>
            Limpiar filtros
          </button>
        )}
      </div>

      {filtrados.length === 0 ? (
        <p className="predios-vacio" role="status">
          No hay predios que coincidan con la búsqueda o los filtros seleccionados.
        </p>
      ) : (
        <>
          <div className="tabla-scroll" role="region" aria-label="Predios del contribuyente" tabIndex={0}>
            <table className="tabla tabla-predios">
              <caption className="sr-only">Predios del contribuyente</caption>
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Dirección</th>
                  <th scope="col">Tipo</th>
                  <th scope="col">Uso</th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((predio, indice) => {
                  const codigo = predio.codigoPredio ?? `predio-${inicio + indice}`
                  const detalleId = `detalle-predio-${codigo}`
                  const expandido = abierto === codigo
                  return (
                    <Fragment key={codigo}>
                      <tr
                        className={`fila-predio${expandido ? ' abierta' : ''}`}
                        onClick={() => alternar(codigo)}
                      >
                        <th scope="row">
                          <button
                            className="predio-toggle"
                            type="button"
                            aria-expanded={expandido}
                            aria-controls={detalleId}
                            aria-label={`Ver detalle del predio ${texto(predio.codigoPredio)}`}
                          >
                            <span className="predio-toggle__chevron" aria-hidden="true">›</span>
                            {texto(predio.codigoPredio)}
                          </button>
                        </th>
                        <td>{texto(predio.ubicacion)}</td>
                        <td>{texto(TIPO_PREDIO[predio.tipoPredio] ?? predio.tipoPredio)}</td>
                        <td>{texto(predio.usoPredio)}</td>
                      </tr>
                      {expandido && (
                        <tr className="fila-predio__detalle" id={detalleId}>
                          <td colSpan={4}>
                            <dl className="predio-detalle-datos">
                              <DatoDetalle etiqueta="Número catastral" valor={predio.numeroCatastral} />
                              <DatoDetalle etiqueta="Área del terreno" valor={medida(predio.areaTerreno, 'm²')} />
                              <DatoDetalle etiqueta="Fecha de adquisición" valor={fecha(predio.fechaAdquisicion)} />
                              <DatoDetalle
                                etiqueta="Condición de propiedad"
                                valor={CONDICION_PROPIEDAD[predio.condicionPropiedad] ?? predio.condicionPropiedad}
                              />
                              <DatoDetalle etiqueta="% de propiedad" valor={medida(predio.porcentajePropiedad, '%')} />
                            </dl>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="predios-paginacion">
            <p className="predios-conteo" role="status">
              Mostrando {inicio + 1}–{fin} de {filtrados.length} {filtrados.length === 1 ? 'predio' : 'predios'}
            </p>
            <div className="predios-paginacion__controles">
              <button
                className="btn"
                type="button"
                onClick={() => cambiarPagina(paginaActual - 1)}
                disabled={paginaActual <= 1}
              >
                Anterior
              </button>
              <span className="predios-pagina">Página {paginaActual} de {totalPaginas}</span>
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
    </div>
  )
}
