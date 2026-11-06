import { useRef, useState } from 'react'
import { coincideContribuyente, colorSegmento, moverColumna } from '../../utils/reporteria.js'
import IconoReporte from './IconoReporte.jsx'
import useMenuFlotante from './useMenuFlotante.js'

export default function TablaRanking({
  configuracion, todas, visibles, filas, filaDestacada, puestos, ordenTabla, ocultas, seleccion, subtituloExtra,
  onOrdenar, onQuitarOrden, onSeleccionar, onCambiarOcultas, onCambiarOrdenColumnas, onRestablecerColumnas,
}) {
  const contenedorMenu = useRef(null)
  const menu = useMenuFlotante(contenedorMenu)
  const [sobre, setSobre] = useState(null)

  function alternarColumna(clave, visible) {
    onCambiarOcultas(visible ? ocultas.filter((c) => c !== clave) : [...ocultas, clave])
  }

  function fila(datos, extra) {
    const resaltada = coincideContribuyente(seleccion, datos)
    const atenuada = seleccion && !resaltada && seleccion.dimension !== 'tributo'
    const seleccionar = () => onSeleccionar('contribuyente', datos.id, datos)
    return (
      <tr
        key={datos.id}
        className={`pulsable${resaltada ? ' resaltada' : ''}${atenuada ? ' atenuada' : ''}${extra ? ' extra' : ''}`}
        tabIndex={0}
        aria-label={`${datos.nombre}${resaltada ? ' (resaltado)' : ''}. Pulsa Intro para resaltar en los gráficos.`}
        onClick={seleccionar}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); seleccionar() } }}
      >
        {visibles.map((c) => {
          const valor = c.valor(datos, puestos.get(datos.id))
          const texto = c.formato ? c.formato(valor) : valor ?? '—'
          const Celda = c.clave === 'nombre' ? 'th' : 'td'
          return (
            <Celda key={c.clave} scope={c.clave === 'nombre' ? 'row' : undefined} className={c.numerica ? 'num' : undefined}>
              {c.segmento && <span className="rep-punto-segmento" style={{ background: colorSegmento(datos.segmento) }} aria-hidden="true" />}
              {texto}
            </Celda>
          )
        })}
      </tr>
    )
  }

  return (
    <section className="rep-card rep-ranking" aria-labelledby="ranking-titulo">
      <header className="rep-card__cabecera">
        <div>
          <h3 id="ranking-titulo">{configuracion.titulo}</h3>
          <p className="rep-card__sub">{configuracion.subtitulo}{subtituloExtra}</p>
        </div>
        <div className="rep-ranking__acciones">
          {ordenTabla && <button className="btn" type="button" onClick={onQuitarOrden}>Orden del ranking</button>}
          <div className="rep-desplegable" ref={contenedorMenu}>
            <button className="btn" type="button" aria-haspopup="true" aria-expanded={menu.abierto} onClick={() => menu.setAbierto(!menu.abierto)}>
              <IconoReporte nombre="tabla" tamano={15} />Columnas
            </button>
            {menu.abierto && (
              <div className="rep-menu rep-menu--columnas" role="group" aria-label="Mostrar u ocultar columnas">
                {todas.map((c, i) => (
                  <label key={c.clave}>
                    <input type="checkbox" checked={!ocultas.includes(c.clave)} disabled={c.fija} onChange={(e) => alternarColumna(c.clave, e.target.checked)} />
                    {c.titulo}
                    <span className="rep-menu__mover">
                      <button type="button" aria-label={`Mover ${c.titulo} antes`} disabled={i === 0} onClick={(e) => { e.preventDefault(); onCambiarOrdenColumnas(moverColumna(todas, c.clave, i - 1)) }}>↑</button>
                      <button type="button" aria-label={`Mover ${c.titulo} después`} disabled={i === todas.length - 1} onClick={(e) => { e.preventDefault(); onCambiarOrdenColumnas(moverColumna(todas, c.clave, i + 1)) }}>↓</button>
                    </span>
                  </label>
                ))}
                <button className="btn rep-menu__restablecer" type="button" onClick={onRestablecerColumnas}>Restablecer columnas</button>
              </div>
            )}
          </div>
        </div>
      </header>
      {filas.length === 0
        ? <p className="rep-pista">No hay contribuyentes que cumplan el criterio con los filtros elegidos.</p>
        : (
          <div className="rep-ranking__tabla">
            <table className="rep-tabla">
              <caption className="sr-only">{configuracion.titulo}. Haz clic en un contribuyente para resaltarlo.</caption>
              <thead>
                <tr>
                  {visibles.map((c) => {
                    const direccion = ordenTabla?.clave === c.clave ? ordenTabla.direccion : null
                    return (
                      <th
                        key={c.clave}
                        scope="col"
                        className={`${c.numerica ? 'num ' : ''}${sobre === c.clave ? 'destino' : ''}`}
                        aria-sort={direccion === 'asc' ? 'ascending' : direccion === 'desc' ? 'descending' : 'none'}
                        draggable
                        title="Arrastra para mover la columna"
                        onDragStart={(e) => { e.dataTransfer.setData('text/plain', c.clave); e.dataTransfer.effectAllowed = 'move' }}
                        onDragOver={(e) => { e.preventDefault(); setSobre(c.clave) }}
                        onDragLeave={() => setSobre(null)}
                        onDrop={(e) => {
                          e.preventDefault()
                          setSobre(null)
                          const clave = e.dataTransfer.getData('text/plain')
                          if (clave && clave !== c.clave) onCambiarOrdenColumnas(moverColumna(todas, clave, todas.findIndex((x) => x.clave === c.clave)))
                        }}
                      >
                        <button type="button" onClick={() => onOrdenar(c.clave)}>
                          {c.titulo}<span aria-hidden="true">{direccion === 'asc' ? ' ▲' : direccion === 'desc' ? ' ▼' : ' ↕'}</span>
                        </button>
                      </th>
                    )
                  })}
                </tr>
              </thead>
              <tbody>
                {filas.map((datos) => fila(datos, false))}
                {filaDestacada && fila(filaDestacada, true)}
              </tbody>
            </table>
          </div>
        )}
      {filaDestacada && <p className="rep-pista">La fila separada es el contribuyente resaltado; no está entre los 10 primeros (puesto {puestos.get(filaDestacada.id)}).</p>}
    </section>
  )
}
