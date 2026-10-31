import { Fragment, useMemo, useState } from 'react'
import { fecha } from '../utils/formato.js'
import { TAMANIOS_PAGINA, filtrarPredios, paginarPredios } from '../utils/predios.js'
import './TablaPredios.css'

const TIPO_PREDIO = {
  URBANO: 'Urbano',
  RUSTICO: 'Rústico',
  OTRO: 'Otro',
}

const CONDICION_PROPIEDAD = {
  PROPIETARIO: 'Propietario',
  COPROPIETARIO: 'Copropietario',
  POSESIONARIO: 'Posesionario',
}

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
  const [porPagina, setPorPagina] = useState(TAMANIOS_PAGINA[0])
  const [pagina, setPagina] = useState(1)
  const [abierto, setAbierto] = useState(null)

  const filtrados = useMemo(() => filtrarPredios(predios, busqueda), [predios, busqueda])
  const { visibles, paginaActual, totalPaginas, inicio, fin } = paginarPredios(filtrados, pagina, porPagina)

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

      {filtrados.length === 0 ? (
        <p className="predios-vacio" role="status">
          No hay predios que coincidan con la búsqueda.
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
                      </tr>
                      {expandido && (
                        <tr className="fila-predio__detalle" id={detalleId}>
                          <td colSpan={3}>
                            <dl className="predio-detalle-datos">
                              <DatoDetalle etiqueta="Número catastral" valor={predio.numeroCatastral} />
                              <DatoDetalle etiqueta="Uso" valor={predio.usoPredio} />
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
