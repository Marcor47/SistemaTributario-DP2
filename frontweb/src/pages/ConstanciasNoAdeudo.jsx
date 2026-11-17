import { Suspense, lazy } from 'react'
import { useSearchParams } from 'react-router-dom'
import './ConstanciasNoAdeudo.css'

// Una sola pestaña para el flujo de la Constancia de No Adeudo del personal municipal: la bandeja de solicitudes que
// llegan por Servicios en Línea y la atención presencial en ventanilla con el historial de constancias emitidas.
const SUBPESTANAS = [
  {
    id: 'pendientes',
    titulo: 'Solicitudes por atender',
    descripcion: 'Revisa y gestiona las solicitudes de ambos canales: verifica la deuda, genera la orden, confirma el cobro presencial y emite la constancia.',
    Componente: lazy(() => import('./GestionConstancias.jsx')),
  },
  {
    id: 'ventanilla',
    titulo: 'Ventanilla',
    descripcion: 'Atiende al contribuyente en el mostrador: crea la solicitud, captura su firma y completa el trámite.',
    Componente: lazy(() => import('./ConstanciaVentanilla.jsx')),
  },
  {
    id: 'historial',
    titulo: 'Historial',
    descripcion: 'Consulta las constancias emitidas y las solicitudes rechazadas de ambos canales.',
    Componente: lazy(() => import('./HistorialConstancias.jsx')),
  },
]

export default function ConstanciasNoAdeudo() {
  const [parametros, setParametros] = useSearchParams()
  const activa = SUBPESTANAS.find((p) => p.id === parametros.get('pestana')) ?? SUBPESTANAS[0]

  function cambiar(id) {
    setParametros(id === SUBPESTANAS[0].id ? {} : { pestana: id }, { replace: true })
  }

  function alTecla(evento, indice) {
    const total = SUBPESTANAS.length
    const destino = evento.key === 'ArrowRight' ? (indice + 1) % total
      : evento.key === 'ArrowLeft' ? (indice + total - 1) % total : null
    if (destino === null) return
    evento.preventDefault()
    cambiar(SUBPESTANAS[destino].id)
    document.getElementById(`cna-pestana-${SUBPESTANAS[destino].id}`)?.focus()
  }

  return (
    <div className="cna">
      <nav className="cna-pestanas" role="tablist" aria-label="Constancias de No Adeudo">
        {SUBPESTANAS.map((p, i) => (
          <button
            key={p.id}
            id={`cna-pestana-${p.id}`}
            type="button"
            role="tab"
            className="cna-pestana"
            aria-selected={activa.id === p.id}
            aria-controls="cna-panel"
            tabIndex={activa.id === p.id ? 0 : -1}
            onClick={() => cambiar(p.id)}
            onKeyDown={(e) => alTecla(e, i)}
          >{p.titulo}</button>
        ))}
      </nav>
      <section id="cna-panel" role="tabpanel" aria-labelledby={`cna-pestana-${activa.id}`}>
        <p className="hint cna-descripcion">{activa.descripcion}</p>
        <Suspense fallback={<p className="mensaje-carga" role="status">Cargando…</p>}>
          <activa.Componente />
        </Suspense>
      </section>
    </div>
  )
}
