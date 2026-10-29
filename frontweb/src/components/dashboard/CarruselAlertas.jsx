import { useCallback, useEffect, useRef, useState } from 'react'
import TarjetaAlerta from './TarjetaAlerta.jsx'

function pasoDesplazamiento(pista) {
  const primera = pista.firstElementChild
  if (!primera) return pista.clientWidth
  const estilos = window.getComputedStyle(pista)
  const separacion = Number.parseFloat(estilos.columnGap || estilos.gap || '0') || 0
  return primera.getBoundingClientRect().width + separacion
}

export default function CarruselAlertas({ alertas }) {
  const pistaRef = useRef(null)
  const [posicion, setPosicion] = useState({ inicio: true, fin: alertas.length <= 1 })
  const clave = alertas.map((alerta) => alerta.idAlerta).join('|')

  const medir = useCallback(() => {
    const pista = pistaRef.current
    if (!pista) return
    const maximo = pista.scrollWidth - pista.clientWidth
    const inicio = pista.scrollLeft <= 2
    const fin = pista.scrollLeft >= maximo - 2
    setPosicion((actual) => (actual.inicio === inicio && actual.fin === fin ? actual : { inicio, fin }))
  }, [])

  useEffect(() => {
    const pista = pistaRef.current
    if (!pista) return undefined
    pista.scrollLeft = 0
    const cuadro = window.requestAnimationFrame(medir)
    window.addEventListener('resize', medir)
    return () => {
      window.cancelAnimationFrame(cuadro)
      window.removeEventListener('resize', medir)
    }
  }, [clave, medir])

  function desplazar(direccion) {
    const pista = pistaRef.current
    if (!pista) return
    const reducirMovimiento = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    pista.scrollBy({
      left: direccion * pasoDesplazamiento(pista),
      behavior: reducirMovimiento ? 'auto' : 'smooth',
    })
  }

  function manejarTeclado(evento) {
    if (evento.key === 'ArrowRight') {
      evento.preventDefault()
      desplazar(1)
    }
    if (evento.key === 'ArrowLeft') {
      evento.preventDefault()
      desplazar(-1)
    }
  }

  return (
    <div className={`dashboard-carrusel${alertas.length === 1 ? ' dashboard-carrusel--uno' : ''}`}>
      <div
        ref={pistaRef}
        className="dashboard-carrusel__pista"
        role="region"
        aria-label="Carrusel de alertas del periodo vigente"
        tabIndex={0}
        onScroll={medir}
        onKeyDown={manejarTeclado}
      >
        {alertas.map((alerta) => <TarjetaAlerta key={alerta.idAlerta} alerta={alerta} />)}
      </div>
      <div className="dashboard-carrusel__pie">
        <span className="hint">
          {posicion.fin
            ? `${alertas.length} ${alertas.length === 1 ? 'alerta' : 'alertas'} en el periodo vigente`
            : `Desliza para ver las ${alertas.length} alertas`}
        </span>
        <div className="dashboard-carrusel__botones">
          <button
            className="btn btn-compacto"
            type="button"
            onClick={() => desplazar(-1)}
            disabled={posicion.inicio}
            aria-label="Ver alertas anteriores"
          >
            Anterior
          </button>
          <button
            className="btn btn-compacto"
            type="button"
            onClick={() => desplazar(1)}
            disabled={posicion.fin}
            aria-label="Ver alertas siguientes"
          >
            Siguiente
          </button>
        </div>
      </div>
    </div>
  )
}
