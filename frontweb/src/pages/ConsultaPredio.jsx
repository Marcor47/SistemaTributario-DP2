import { useState } from 'react'
import BarraBusqueda from '../components/BarraBusqueda.jsx'
import { buscarPredios } from '../services/predioService.js'
import { fecha, moneda } from '../utils/formato.js'

function Campo({ etiqueta, valor }) {
  return (
    <div className="pf">
      <span className="pl">{etiqueta}</span>
      <span className="pv">{valor === '' || valor == null ? '—' : valor}</span>
    </div>
  )
}

function DetallePredio({ predio, onVolver }) {
  return (
    <>
      {onVolver && (
        <button className="btn" type="button" onClick={onVolver}>
          ← Volver a los resultados
        </button>
      )}

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
      </section>
    </>
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
        <DetallePredio predio={detalle} onVolver={seleccionado ? () => setSeleccionado(null) : null} />
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
