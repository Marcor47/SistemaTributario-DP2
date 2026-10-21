import { useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import EstadoCuenta from './EstadoCuenta.jsx'
import RegularidadPago from './RegularidadPago.jsx'

const PESTANAS = [
  { id: 'cuenta', nombre: 'Estado de cuenta' },
  { id: 'regularidad', nombre: 'Regularidad de pago' },
]

export default function FichaContribuyente() {
  const [contribuyente, setContribuyente] = useState(null)
  const [parametros, setParametros] = useSearchParams()
  const botones = useRef([])
  const vista = parametros.get('vista') === 'regularidad' ? 'regularidad' : 'cuenta'

  function cambiarVista(id) {
    const nuevos = new URLSearchParams(parametros)
    if (id === 'cuenta') nuevos.delete('vista')
    else nuevos.set('vista', id)
    setParametros(nuevos)
  }

  function navegarPestanas(evento, indice) {
    let siguiente
    if (evento.key === 'ArrowRight') siguiente = (indice + 1) % PESTANAS.length
    else if (evento.key === 'ArrowLeft') siguiente = (indice + PESTANAS.length - 1) % PESTANAS.length
    else if (evento.key === 'Home') siguiente = 0
    else if (evento.key === 'End') siguiente = PESTANAS.length - 1
    else return
    evento.preventDefault()
    cambiarVista(PESTANAS[siguiente].id)
    botones.current[siguiente].focus()
  }

  return (
    <>
      <section className="pcard">
        <h2>Ficha del contribuyente</h2>
        <p className="hint">Busca una sola vez para consultar su estado de cuenta y su regularidad de pago.</p>
      </section>
      <EstadoCuenta
        onSeleccionarContribuyente={setContribuyente}
        renderDetalle={(detalleCuenta, onVolver) => (
          <>
            <div className="pestanas-contribuyente" role="tablist" aria-label="Información del contribuyente">
              {PESTANAS.map((pestana, indice) => (
                <button
                  key={pestana.id}
                  ref={(elemento) => { botones.current[indice] = elemento }}
                  id={`pestana-${pestana.id}`}
                  className={`btn${vista === pestana.id ? ' primary' : ''}`}
                  type="button"
                  role="tab"
                  aria-selected={vista === pestana.id}
                  aria-controls={`panel-${pestana.id}`}
                  tabIndex={vista === pestana.id ? 0 : -1}
                  onClick={() => cambiarVista(pestana.id)}
                  onKeyDown={(evento) => navegarPestanas(evento, indice)}
                >
                  {pestana.nombre}
                </button>
              ))}
            </div>
            <div id="panel-cuenta" role="tabpanel" aria-labelledby="pestana-cuenta" hidden={vista !== 'cuenta'} tabIndex={0}>
              {detalleCuenta}
            </div>
            <div id="panel-regularidad" role="tabpanel" aria-labelledby="pestana-regularidad" hidden={vista !== 'regularidad'} tabIndex={0}>
              {contribuyente && (
                <RegularidadPago key={contribuyente.codigo} contribuyente={contribuyente} onVolver={onVolver} />
              )}
            </div>
          </>
        )}
      />
    </>
  )
}
