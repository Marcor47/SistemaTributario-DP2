import { useRef, useState } from 'react'
import ArbitriosPanel from '../components/ArbitriosPanel.jsx'
import BarraBusqueda from '../components/BarraBusqueda.jsx'
import DeclaracionJuradaPanel from '../components/DeclaracionJuradaPanel.jsx'
import { buscarPredios } from '../services/predioService.js'
import { fecha, moneda } from '../utils/formato.js'
import './ConsultaPredio.css'

const PESTANAS = [
  { id: 'resumen', nombre: 'Resumen' },
  { id: 'declaracion', nombre: 'Declaración jurada' },
  { id: 'arbitrios', nombre: 'Arbitrios' },
]

function Campo({ etiqueta, valor }) {
  return (
    <div className="pf">
      <span className="pl">{etiqueta}</span>
      <span className="pv">{valor === '' || valor == null ? '—' : valor}</span>
    </div>
  )
}

function ResumenPredio({ predio }) {
  return (
    <>
      <section className="pcard" aria-label="Características del predio">
        <h2>Predio {predio.codPredio}</h2>
        <p className="hint">{predio.ubicacion}</p>
        <div className="pfields">
          <Campo etiqueta="Código predial" valor={predio.codPredio} />
          <Campo etiqueta="Código catastral" valor={predio.catastral} />
          <Campo etiqueta="Ubicación" valor={predio.ubicacion} />
          <Campo etiqueta="Estado" valor={predio.estado} />
          <Campo etiqueta="Uso" valor={predio.uso} />
          <Campo etiqueta="Tipo" valor={predio.tipo} />
          <Campo etiqueta="Categoría" valor={predio.categoria} />
          <Campo etiqueta="Giro" valor={predio.giro} />
          <Campo etiqueta="Área del terreno" valor={`${predio.areaTerreno} m²`} />
          <Campo etiqueta="Fecha de adquisición" valor={fecha(predio.fechaAdquisicion)} />
          <Campo etiqueta="Valor del terreno" valor={moneda(predio.valorTerreno)} />
          <Campo etiqueta="Valor de las construcciones" valor={moneda(predio.valorNiveles)} />
          <Campo etiqueta="Otras instalaciones" valor={moneda(predio.valorOtrasInstalaciones)} />
          <Campo etiqueta="Base imponible" valor={moneda(predio.baseImponible)} />
        </div>
        {predio.observaciones && <p className="hint">Observaciones: {predio.observaciones}</p>}
      </section>

      <section className="pcard" aria-label="Contribuyentes asociados">
        <h2>Contribuyente(s) asociado(s)</h2>
        <div className="tabla-scroll" role="region" aria-label="Tabla de contribuyentes asociados" tabIndex={0}>
        <table className="tabla">
          <thead>
            <tr>
              <th>Código</th>
              <th>Nombre o razón social</th>
              <th>Documento</th>
              <th className="num">% de propiedad</th>
            </tr>
          </thead>
          <tbody>
            {predio.contribuyentes.map((c) => (
              <tr key={c.codigo}>
                <td>{c.codigo}</td>
                <td>{c.nombre}</td>
                <td>{c.tipoDoc} {c.doc}</td>
                <td className="num">{c.pct} %</td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      </section>
    </>
  )
}

function DetallePredio({ predio, onVolver }) {
  const [vista, setVista] = useState('resumen')
  const botones = useRef([])

  function navegarPestanas(evento, indice) {
    let siguiente
    if (evento.key === 'ArrowRight') siguiente = (indice + 1) % PESTANAS.length
    else if (evento.key === 'ArrowLeft') siguiente = (indice + PESTANAS.length - 1) % PESTANAS.length
    else if (evento.key === 'Home') siguiente = 0
    else if (evento.key === 'End') siguiente = PESTANAS.length - 1
    else return
    evento.preventDefault()
    setVista(PESTANAS[siguiente].id)
    botones.current[siguiente].focus()
  }

  return (
    <div className="predio-detalle">
      {onVolver && <button className="btn volver" type="button" onClick={onVolver}>← Volver a los resultados</button>}
      <div className="pestanas-predio" role="tablist" aria-label="Información del predio">
        {PESTANAS.map((pestana, indice) => (
          <button
            key={pestana.id}
            ref={(elemento) => { botones.current[indice] = elemento }}
            id={`predio-pestana-${pestana.id}`}
            className={`btn${vista === pestana.id ? ' primary' : ''}`}
            type="button"
            role="tab"
            aria-selected={vista === pestana.id}
            aria-controls={`predio-panel-${pestana.id}`}
            tabIndex={vista === pestana.id ? 0 : -1}
            onClick={() => setVista(pestana.id)}
            onKeyDown={(evento) => navegarPestanas(evento, indice)}
          >
            {pestana.nombre}
          </button>
        ))}
      </div>
      <div id="predio-panel-resumen" role="tabpanel" aria-labelledby="predio-pestana-resumen" hidden={vista !== 'resumen'} tabIndex={0}>
        <ResumenPredio predio={predio} />
      </div>
      <div id="predio-panel-declaracion" role="tabpanel" aria-labelledby="predio-pestana-declaracion" hidden={vista !== 'declaracion'} tabIndex={0}>
        {vista === 'declaracion' && <DeclaracionJuradaPanel key={predio.codPredio} codigoPredio={predio.codPredio} />}
      </div>
      <div id="predio-panel-arbitrios" role="tabpanel" aria-labelledby="predio-pestana-arbitrios" hidden={vista !== 'arbitrios'} tabIndex={0}>
        {vista === 'arbitrios' && <ArbitriosPanel key={predio.codPredio} codigoPredio={predio.codPredio} />}
      </div>
    </div>
  )
}

export default function ConsultaPredio() {
  const [consulta, setConsulta] = useState('')
  const [resultados, setResultados] = useState(null) // null = aún no se busca
  const [seleccionado, setSeleccionado] = useState(null)
  const [ocupado, setOcupado] = useState(false)

  async function buscar(texto) {
    setOcupado(true)
    setConsulta(texto.trim())
    setSeleccionado(null)
    try {
      setResultados(await buscarPredios(texto))
    } finally {
      setOcupado(false)
    }
  }

  function limpiar() {
    setResultados(null)
    setSeleccionado(null)
    setConsulta('')
  }

  // Con un único resultado se muestra directo, sin pasar por la lista.
  const unico = resultados?.length === 1 ? resultados[0] : null
  const detalle = seleccionado ?? unico

  return (
    <>
      <BarraBusqueda
        etiqueta="Buscar predio"
        placeholder="Código predial, o código, nombre o documento (DNI / RUC) del contribuyente"
        onBuscar={buscar}
        onLimpiar={limpiar}
        ocupado={ocupado}
      />

      {ocupado && <p className="hint" role="status">Consultando SAMNET…</p>}

      {!ocupado && resultados === null && (
        <div className="info">
          Busca un predio por su código predial o por el contribuyente asociado. Consulta de solo lectura.
        </div>
      )}

      {!ocupado && resultados?.length === 0 && (
        <div className="alert" role="status">
          <strong>No se encontraron resultados. </strong>
          No existe ningún predio para «{consulta}» en los datos de demostración.
        </div>
      )}

      {!ocupado && detalle && (
        <DetallePredio key={detalle.codPredio} predio={detalle} onVolver={seleccionado ? () => setSeleccionado(null) : null} />
      )}

      {!ocupado && resultados?.length > 1 && !seleccionado && (
        <section className="pcard" aria-label="Resultados">
          <h2>{resultados.length} predios encontrados</h2>
          <p className="hint">Elige uno para ver sus características.</p>
          <div className="plist">
            {resultados.map((p) => (
              <button key={p.codPredio} type="button" className="presrow" onClick={() => setSeleccionado(p)}>
                <span>
                  <strong>{p.codPredio}</strong>
                  <span className="hint"> · {p.ubicacion}</span>
                </span>
                <span className="hint">{p.contribuyentes.map((c) => c.nombre).join(' / ')}</span>
              </button>
            ))}
          </div>
        </section>
      )}
    </>
  )
}
