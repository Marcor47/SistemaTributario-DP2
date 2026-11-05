import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
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

const TIPO_PREDIO = { URBANO: 'Urbano', RUSTICO: 'Rústico', OTRO: 'Otro' }

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
        <h2>Predio {predio.codigoPredio}</h2>
        <p className="hint">{predio.ubicacion}</p>
        <div className="pfields">
          <Campo etiqueta="Código predial" valor={predio.codigoPredio} />
          <Campo etiqueta="Código catastral" valor={predio.numeroCatastral} />
          <Campo etiqueta="Ubicación" valor={predio.ubicacion} />
          <Campo etiqueta="Uso" valor={predio.usoPredio} />
          <Campo etiqueta="Tipo" valor={TIPO_PREDIO[predio.tipoPredio] ?? predio.tipoPredio} />
          <Campo etiqueta="Zona" valor={predio.zona} />
          <Campo etiqueta="Área del terreno" valor={predio.areaTerreno == null ? null : `${Number(predio.areaTerreno).toLocaleString('es-PE')} m²`} />
          <Campo etiqueta="Fecha de adquisición" valor={fecha(predio.fechaAdquisicion)} />
          <Campo etiqueta="Autovalúo (base imponible)" valor={moneda(predio.baseImponible)} />
        </div>
      </section>

      <section className="pcard" aria-label="Contribuyentes asociados">
        <h2>Titulares</h2>
        {predio.titulares.length === 0 ? (
          <div className="alert" role="status">El predio no tiene titulares registrados en SAMNET.</div>
        ) : (
          <div className="tabla-scroll" role="region" aria-label="Tabla de titulares" tabIndex={0}>
            <table className="tabla">
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Nombre o razón social</th>
                  <th scope="col">Documento</th>
                  <th scope="col">Condición</th>
                  <th scope="col" className="num">% de propiedad</th>
                </tr>
              </thead>
              <tbody>
                {predio.titulares.map((t) => (
                  <tr key={t.codigo}>
                    <td>{t.codigo}</td>
                    <td>{t.nombre ?? '—'}</td>
                    <td>{t.tipoDocumento} {t.numeroDocumento}</td>
                    <td>{t.condicionPropiedad}</td>
                    <td className="num">{Number(t.porcentajePropiedad).toLocaleString('es-PE')} %</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
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
        {vista === 'declaracion' && <DeclaracionJuradaPanel key={predio.codigoPredio} codigoPredio={predio.codigoPredio} />}
      </div>
      <div id="predio-panel-arbitrios" role="tabpanel" aria-labelledby="predio-pestana-arbitrios" hidden={vista !== 'arbitrios'} tabIndex={0}>
        {vista === 'arbitrios' && <ArbitriosPanel key={predio.codigoPredio} codigoPredio={predio.codigoPredio} />}
      </div>
    </div>
  )
}

function ListaResultados({ resultado, consulta, onElegir, onPagina }) {
  const { contenido, pagina, totalElementos, totalPaginas } = resultado
  return (
    <section className="pcard" aria-label="Resultados">
      <h2>{totalElementos} predios encontrados</h2>
      <p className="hint">Resultados para «{consulta}». Elige uno para ver sus características, declaración jurada y arbitrios.</p>
      <div className="tabla-scroll" role="region" aria-label="Predios encontrados" tabIndex={0}>
        <table className="tabla">
          <thead>
            <tr>
              <th scope="col">Código</th>
              <th scope="col">Dirección</th>
              <th scope="col">Uso</th>
              <th scope="col">Titular(es)</th>
              <th scope="col" className="num">Autovalúo</th>
              <th scope="col"><span className="sr-only">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            {contenido.map((p) => (
              <tr key={p.codigoPredio}>
                <td>{p.codigoPredio}</td>
                <td>{p.ubicacion}</td>
                <td>{p.usoPredio ?? '—'}</td>
                <td>{p.titulares.length === 0 ? 'Sin titular' : p.titulares.map((t) => t.nombre ?? t.codigo).join(' / ')}</td>
                <td className="num">{moneda(p.baseImponible)}</td>
                <td><button className="btn" type="button" onClick={() => onElegir(p)}>Ver predio</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalPaginas > 1 && (
        <nav className="paginacion" aria-label="Páginas de resultados">
          <button className="btn" type="button" disabled={pagina === 0} onClick={() => onPagina(pagina - 1)}>← Anterior</button>
          <span>Página {pagina + 1} de {totalPaginas}</span>
          <button className="btn" type="button" disabled={pagina >= totalPaginas - 1} onClick={() => onPagina(pagina + 1)}>Siguiente →</button>
        </nav>
      )}
    </section>
  )
}

export default function ConsultaPredio() {
  const [parametros, setParametros] = useSearchParams()
  const consultaInicial = parametros.get('consulta') ?? ''
  // Desde la ficha del contribuyente se llega con ?consulta=<código predial> y la búsqueda arranca sola.
  const [consulta, setConsulta] = useState(consultaInicial.trim())
  const [resultado, setResultado] = useState(null) // null = aún no se busca
  const [seleccionado, setSeleccionado] = useState(null)
  const [ocupado, setOcupado] = useState(Boolean(consultaInicial.trim()))
  const [error, setError] = useState('')
  const solicitud = useRef(0)
  const busquedaInicial = useRef(consultaInicial.trim())

  async function consultar(texto, pagina, actual) {
    try {
      const respuesta = await buscarPredios(texto, { pagina })
      if (actual === solicitud.current) setResultado(respuesta)
    } catch (err) {
      if (actual === solicitud.current) {
        setResultado(null)
        setError(err.message || 'No se pudo consultar SAMNET. Intenta nuevamente.')
      }
    } finally {
      if (actual === solicitud.current) setOcupado(false)
    }
  }

  function buscar(texto, pagina = 0) {
    const limpio = texto.trim()
    setOcupado(true)
    setError('')
    setConsulta(limpio)
    setSeleccionado(null)
    consultar(limpio, pagina, ++solicitud.current)
  }

  useEffect(() => {
    if (busquedaInicial.current) consultar(busquedaInicial.current, 0, ++solicitud.current)
  }, [])

  function limpiar() {
    solicitud.current++
    setResultado(null)
    setSeleccionado(null)
    setConsulta('')
    setError('')
    if (parametros.has('consulta')) setParametros({})
  }

  const lista = resultado?.contenido ?? []
  // Con un único resultado se muestra directo, sin pasar por la lista.
  const unico = resultado?.totalElementos === 1 ? lista[0] : null
  const detalle = seleccionado ?? unico

  return (
    <>
      <BarraBusqueda
        etiqueta="Buscar predio"
        placeholder="Código predial, catastro o dirección; o código, nombre o documento (DNI / RUC) del titular"
        onBuscar={(texto) => buscar(texto)}
        onLimpiar={limpiar}
        ocupado={ocupado}
        valorInicial={consultaInicial}
      />

      {ocupado && <p className="hint" role="status">Consultando SAMNET…</p>}
      {error && <div className="alert" role="alert">{error}</div>}

      {!ocupado && !error && resultado === null && (
        <div className="info">
          Busca un predio por su código predial, código catastral o dirección, o por su titular. Si el titular tiene
          muchos predios, la lista se muestra por páginas. Consulta de solo lectura.
        </div>
      )}

      {!ocupado && resultado?.totalElementos === 0 && (
        <div className="alert" role="status">
          <strong>No se encontraron resultados. </strong>
          No existe ningún predio para «{consulta}» en SAMNET.
        </div>
      )}

      {!ocupado && detalle && (
        <DetallePredio key={detalle.codigoPredio} predio={detalle} onVolver={seleccionado ? () => setSeleccionado(null) : null} />
      )}

      {!ocupado && resultado?.totalElementos > 1 && !seleccionado && (
        <ListaResultados resultado={resultado} consulta={consulta} onElegir={setSeleccionado}
          onPagina={(pagina) => buscar(consulta, pagina)} />
      )}
    </>
  )
}
