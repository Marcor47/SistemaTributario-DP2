import { useState } from 'react'

export default function BarraBusqueda({ etiqueta, placeholder, onBuscar, onLimpiar, ocupado }) {
  const [texto, setTexto] = useState('')

  function enviar(e) {
    e.preventDefault()
    onBuscar(texto)
  }

  function limpiar() {
    setTexto('')
    onLimpiar()
  }

  return (
    <form className="filters" onSubmit={enviar} role="search">
      <div className="fld">
        <label htmlFor="busqueda">{etiqueta}</label>
        <input
          id="busqueda"
          type="text"
          value={texto}
          placeholder={placeholder}
          onChange={(e) => setTexto(e.target.value)}
        />
      </div>
      <button className="btn primary" type="submit" disabled={ocupado || !texto.trim()}>
        Buscar
      </button>
      <button className="btn" type="button" onClick={limpiar} disabled={ocupado}>
        Limpiar
      </button>
    </form>
  )
}
