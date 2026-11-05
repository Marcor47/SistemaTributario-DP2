import './PaginacionTabla.css'

const OPCIONES_PREDETERMINADAS = [10, 25, 50, 100]

function PaginaAnteriorIcono() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
}

function PaginaSiguienteIcono() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6" /></svg>
}

export default function PaginacionTabla({
  id = 'tabla', totalRegistros, pagina, tamanio, opciones = OPCIONES_PREDETERMINADAS,
  onCambiarPagina, onCambiarTamanio,
}) {
  const total = Math.max(0, Number(totalRegistros) || 0)
  const totalPaginas = Math.max(1, Math.ceil(total / tamanio))
  const paginaActual = Math.min(Math.max(1, Number(pagina) || 1), totalPaginas)
  const desde = total ? (paginaActual - 1) * tamanio + 1 : 0
  const hasta = Math.min(paginaActual * tamanio, total)

  function cambiarPagina(valor) {
    const siguiente = Number(valor)
    if (Number.isInteger(siguiente)) onCambiarPagina(Math.min(Math.max(1, siguiente), totalPaginas))
  }

  return <nav className="paginacion-tabla" aria-label="Paginación de la tabla">
    <span className="paginacion-tabla-resumen" aria-live="polite">{desde}-{hasta} de {total}</span>
    <div className="paginacion-tabla-controles">
      <label htmlFor={`${id}-tamanio`}>Filas</label>
      <select id={`${id}-tamanio`} value={tamanio} onChange={(event) => onCambiarTamanio(Number(event.target.value))}>
        {opciones.map((opcion) => <option key={opcion} value={opcion}>{opcion}</option>)}
      </select>
      <button className="paginacion-tabla-flecha" type="button" aria-label="Página anterior" onClick={() => cambiarPagina(paginaActual - 1)} disabled={paginaActual === 1}>
        <PaginaAnteriorIcono />
      </button>
      <label htmlFor={`${id}-pagina`}>Página</label>
      <input id={`${id}-pagina`} type="number" inputMode="numeric" min="1" max={totalPaginas} value={paginaActual} onChange={(event) => cambiarPagina(event.target.value)} />
      <span aria-hidden="true">de {totalPaginas}</span>
      <button className="paginacion-tabla-flecha" type="button" aria-label="Página siguiente" onClick={() => cambiarPagina(paginaActual + 1)} disabled={paginaActual === totalPaginas}>
        <PaginaSiguienteIcono />
      </button>
    </div>
  </nav>
}
