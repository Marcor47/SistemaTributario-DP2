import { Fragment, useEffect, useMemo, useState } from 'react'
import { useSesionInterna } from '../context/sesionInterna.js'
import { aprobarSegmentacion, listarSegmentos, listarSegmentosPublicados } from '../services/segmentosService.js'
import { formatearPorcentaje } from '../utils/alertas.js'
import { fechaHora } from '../utils/formato.js'
import {
  agruparSegmentos,
  claseEstadoSegmento,
  etiquetaEstadoSegmento,
  interpretarVariablesCaracteristicas,
  participacionSegmento,
} from '../utils/segmentos.js'
import './Segmentos.css'

/*
 * HU031 (RF-043): revisión, aprobación y publicación de segmentos. La aprobación vive fuera de la plataforma analítica;
 * la plataforma solo muestra la segmentación publicada como filtro y en el mapa de segmentos.
 */
export default function Segmentos() {
  const { sesion } = useSesionInterna()
  const puedeAprobar = sesion?.permisos?.includes('SEGMENTOS:APROBAR') ?? false
  const [estado, setEstado] = useState({
    cargando: true,
    pendientes: [],
    publicados: [],
    retirados: [],
    error: null,
  })
  const [aprobando, setAprobando] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [errorOperacion, setErrorOperacion] = useState(null)

  function cargarSegmentos(signal) {
    return Promise.all([
      listarSegmentos('BORRADOR', { signal }),
      listarSegmentosPublicados({ signal }),
      listarSegmentos('RETIRADO', { signal }),
    ])
  }

  useEffect(() => {
    const controlador = new AbortController()
    cargarSegmentos(controlador.signal)
      .then(([borradores, publicados, retirados]) => {
        if (controlador.signal.aborted) return
        setEstado({
          cargando: false,
          pendientes: agruparSegmentos(borradores),
          publicados: agruparSegmentos(publicados),
          retirados: agruparSegmentos(retirados),
          error: null,
        })
      })
      .catch((error) => {
        if (controlador.signal.aborted) return
        setEstado({ cargando: false, pendientes: [], publicados: [], retirados: [], error })
      })
    return () => controlador.abort()
  }, [])

  async function recargar() {
    setEstado((anterior) => ({ ...anterior, cargando: true, error: null }))
    try {
      const [borradores, publicados, retirados] = await cargarSegmentos()
      setEstado({
        cargando: false,
        pendientes: agruparSegmentos(borradores),
        publicados: agruparSegmentos(publicados),
        retirados: agruparSegmentos(retirados),
        error: null,
      })
    } catch (error) {
      setEstado({ cargando: false, pendientes: [], publicados: [], retirados: [], error })
    }
  }

  const publicadosInfo = useMemo(() => {
    const segmentos = estado.publicados.flatMap((grupo) => grupo.segmentos)
    const grupo = estado.publicados[0]
    return {
      modelo: grupo ? `${grupo.modelo} v${grupo.versionModelo}` : null,
      texto: segmentos.length === 0
        ? 'No hay una segmentación publicada actualmente.'
        : segmentos.map((segmento) => `${segmento.nombre} (${segmento.tamano})`).join(' · '),
      aprobador: grupo?.aprobador || null,
      fechaAprobacion: grupo?.fechaAprobacion || null,
    }
  }, [estado.publicados])

  async function aprobar(grupo) {
    const cantidad = grupo.segmentos.length
    const detalle = cantidad === 1
      ? 'El segmento de esta segmentación quedará disponible como filtro de la plataforma analítica.'
      : `Los ${cantidad} segmentos de esta segmentación quedarán disponibles como filtro de la plataforma analítica.`
    const confirmado = window.confirm(
      `¿Aprobar y publicar la segmentación "${grupo.modelo}" versión ${grupo.versionModelo}? `
      + `Se retirará la segmentación publicada actual. ${detalle}`,
    )
    if (!confirmado) return

    setAprobando(grupo.clave)
    setAviso(null)
    setErrorOperacion(null)
    try {
      const respuesta = await aprobarSegmentacion(grupo.segmentos.map((segmento) => segmento.idSegmento))
      const publicados = respuesta.publicados.length
      setAviso(
        publicados === 1
          ? 'Se publicó 1 segmento. La segmentación anterior quedó retirada.'
          : `Se publicaron ${publicados} segmentos. La segmentación anterior quedó retirada.`,
      )
      await recargar()
    } catch (error) {
      setErrorOperacion(error)
    } finally {
      setAprobando(null)
    }
  }

  if (estado.cargando
    && estado.pendientes.length === 0
    && estado.publicados.length === 0
    && estado.retirados.length === 0
    && !estado.error) {
    return (
      <section className="pcard" aria-busy="true">
        <p className="hint" role="status">Consultando segmentaciones…</p>
      </section>
    )
  }

  return (
    <div className="segmentos" aria-busy={estado.cargando}>
      <section className="pcard segmentos-cabecera">
        <div>
          <h2>Segmentaciones de contribuyentes</h2>
          <p className="hint">
            Revisa las segmentaciones generadas por el modelo. La jefatura de Rentas aprueba una segmentación
            completa y, al publicarla, queda disponible como filtro en la plataforma analítica. Los segmentos no disparan
            acciones tributarias automáticas y solo muestran conteos de contribuyentes seudonimizados.
          </p>
        </div>
        <button type="button" className="btn" onClick={recargar} disabled={estado.cargando}>
          {estado.cargando ? 'Actualizando…' : 'Actualizar'}
        </button>
      </section>

      {!puedeAprobar && (
        <div className="info" role="note">
          Puedes consultar las segmentaciones, pero solo la jefatura de Rentas puede aprobarlas y publicarlas.
        </div>
      )}

      {estado.error && (
        <div className="alert" role="alert">
          <strong>No se pudieron consultar las segmentaciones.</strong>
          <p>{estado.error.message}</p>
          <button type="button" className="btn" onClick={recargar}>Reintentar</button>
        </div>
      )}
      {aviso && <div className="confirmacion" role="status">{aviso}</div>}
      {errorOperacion && (
        <div className="alert" role="alert">
          <strong>{errorOperacion.message}</strong>
          {errorOperacion.detalles?.length > 0 && (
            <ul>{errorOperacion.detalles.map((detalle) => <li key={detalle}>{detalle}</li>)}</ul>
          )}
        </div>
      )}

      {!estado.error && (
        <>
          <section className="pcard" aria-labelledby="segmentos-pendientes-titulo">
            <div className="segmentos-seccion-cab">
              <div>
                <h2 id="segmentos-pendientes-titulo">Pendientes de aprobación</h2>
                <p className="hint">La jefatura debe aprobar todos los segmentos de una misma segmentación.</p>
              </div>
              <span className={`estado ${estado.pendientes.length > 0 ? 'aviso' : 'neutro'}`}>
                {estado.pendientes.length} {estado.pendientes.length === 1 ? 'segmentación pendiente' : 'segmentaciones pendientes'}
              </span>
            </div>
            {estado.pendientes.length === 0 ? (
              <div className="info" role="status">
                No hay segmentaciones pendientes de aprobación. Cuando el modelo genere una nueva segmentación,
                aparecerá aquí.
              </div>
            ) : estado.pendientes.map((grupo) => (
              <GrupoPendiente
                key={grupo.clave}
                grupo={grupo}
                publicadosInfo={publicadosInfo}
                puedeAprobar={puedeAprobar}
                cargando={estado.cargando}
                aprobando={aprobando === grupo.clave}
                onAprobar={() => aprobar(grupo)}
              />
            ))}
          </section>

          <section className="pcard" aria-labelledby="segmentos-publicados-titulo">
            <div className="segmentos-seccion-cab">
              <div>
                <h2 id="segmentos-publicados-titulo">Segmentación publicada</h2>
                <p className="hint">Es la segmentación que la plataforma analítica ofrece como filtro.</p>
              </div>
              <span className="estado ok">Visible en la plataforma analítica</span>
            </div>
            {estado.publicados.length === 0 ? (
              <div className="info" role="status">
                Aún no hay una segmentación publicada. El filtro de segmento aparecerá en la plataforma analítica cuando
                la jefatura apruebe una segmentación.
              </div>
            ) : estado.publicados.map((grupo) => (
              <GrupoPublicado key={grupo.clave} grupo={grupo} />
            ))}
          </section>

          {estado.retirados.length > 0 && (
            <details className="pcard segmentos-historial">
              <summary>
                Historial de segmentaciones retiradas ({estado.retirados.length})
              </summary>
              {estado.retirados.map((grupo) => (
                <GrupoPublicado key={grupo.clave} grupo={grupo} />
              ))}
            </details>
          )}
        </>
      )}
    </div>
  )
}

function GrupoPendiente({ grupo, publicadosInfo, puedeAprobar, cargando, aprobando, onAprobar }) {
  return (
    <div className="segmentos-grupo">
      <div className="segmentos-grupo__cab">
        <div>
          <h3>{grupo.modelo} <span>v{grupo.versionModelo}</span></h3>
          <p className="hint">
            {grupo.segmentos.length} {grupo.segmentos.length === 1 ? 'segmento' : 'segmentos'}
            {' · '}
            {grupo.contribuyentes} {grupo.contribuyentes === 1 ? 'contribuyente asignado' : 'contribuyentes asignados'}
          </p>
        </div>
        <span className={`estado ${claseEstadoSegmento(grupo.estado)}`}>
          {etiquetaEstadoSegmento(grupo.estado)}
        </span>
      </div>

      <TablaSegmentos grupo={grupo} />

      <div className="segmentos-comparacion">
        <p>
          <strong>Segmentación publicada actual:</strong>{' '}
          {publicadosInfo.modelo ? `${publicadosInfo.modelo} · ` : ''}{publicadosInfo.texto}
        </p>
        {publicadosInfo.aprobador && (
          <p className="hint">
            Publicada por {publicadosInfo.aprobador} el {fechaHora(publicadosInfo.fechaAprobacion)}.
          </p>
        )}
        <p className="hint">
          Al aprobar, la segmentación publicada actual se retira y esta queda disponible como filtro de la plataforma analítica.
        </p>
      </div>

      {puedeAprobar && (
        <div className="segmentos-acciones">
          <button type="button" className="btn primary" onClick={onAprobar} disabled={aprobando || cargando}>
            {aprobando ? 'Publicando…' : 'Aprobar y publicar'}
          </button>
        </div>
      )}
    </div>
  )
}

function GrupoPublicado({ grupo }) {
  return (
    <div className="segmentos-grupo">
      <div className="segmentos-grupo__cab">
        <div>
          <h3>{grupo.modelo} <span>v{grupo.versionModelo}</span></h3>
          <p className="hint">
            {grupo.segmentos.length} {grupo.segmentos.length === 1 ? 'segmento' : 'segmentos'}
            {' · '}
            {grupo.contribuyentes} {grupo.contribuyentes === 1 ? 'contribuyente' : 'contribuyentes'}
          </p>
          {grupo.aprobador && (
            <p className="hint">Aprobada por {grupo.aprobador} el {fechaHora(grupo.fechaAprobacion)}.</p>
          )}
        </div>
        <span className={`estado ${claseEstadoSegmento(grupo.estado)}`}>
          {etiquetaEstadoSegmento(grupo.estado)}
        </span>
      </div>
      <TablaSegmentos grupo={grupo} />
    </div>
  )
}

function TablaSegmentos({ grupo }) {
  const [abiertos, setAbiertos] = useState(() => new Set())

  function alternar(idSegmento) {
    setAbiertos((actuales) => {
      const siguientes = new Set(actuales)
      if (siguientes.has(idSegmento)) siguientes.delete(idSegmento)
      else siguientes.add(idSegmento)
      return siguientes
    })
  }

  return (
    <div className="tabla-scroll" role="region" aria-label={`Segmentos de ${grupo.modelo}`} tabIndex={0}>
      <table className="tabla segmentos-tabla">
        <caption className="sr-only">
          Segmentos de la segmentación {grupo.modelo} versión {grupo.versionModelo}
        </caption>
        <thead>
          <tr>
            <th scope="col">Segmento</th>
            <th scope="col">Descripción</th>
            <th scope="col" className="num">Contribuyentes</th>
            <th scope="col" className="num">% del total</th>
            <th scope="col">Variables</th>
          </tr>
        </thead>
        <tbody>
          {grupo.segmentos.map((segmento) => {
            const abierto = abiertos.has(segmento.idSegmento)
            const idDetalle = `variables-segmento-${segmento.idSegmento}`
            return (
              <Fragment key={segmento.idSegmento}>
                <tr className={abierto ? 'segmento-fila abierta' : 'segmento-fila'}>
                  <th scope="row">{segmento.nombre}</th>
                  <td>{segmento.descripcion || '—'}</td>
                  <td className="num">{segmento.tamano}</td>
                  <td className="num">{formatearPorcentaje(participacionSegmento(segmento, grupo))}</td>
                  <td>
                    <button
                      type="button"
                      className="segmento-variables-boton"
                      aria-expanded={abierto}
                      aria-controls={idDetalle}
                      onClick={() => alternar(segmento.idSegmento)}
                    >
                      <span aria-hidden="true">{abierto ? '▾' : '▸'}</span>
                      {abierto ? 'Ocultar variables' : 'Ver variables'}
                    </button>
                  </td>
                </tr>
                {abierto && (
                  <tr className="segmento-detalle" id={idDetalle}>
                    <td colSpan={5}>
                      <VariablesSegmento segmento={segmento} />
                    </td>
                  </tr>
                )}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function VariablesSegmento({ segmento }) {
  const { variables, indicadores, textoLibre } = interpretarVariablesCaracteristicas(segmento.variablesCaracteristicas)
  if (textoLibre) return <p className="segmento-variables-texto">{textoLibre}</p>
  if (variables.length === 0 && indicadores.length === 0) {
    return <p className="hint">Este segmento no tiene variables registradas.</p>
  }
  return (
    <div className="segmento-variables">
      {variables.length > 0 && (
        <div className="segmento-variables-bloque">
          <h4>Comportamiento de pago típico del segmento</h4>
          <p className="hint">Valor mediano de cada variable y cómo se compara con el conjunto de contribuyentes.</p>
          <table className="segmento-variables-tabla">
            <thead>
              <tr>
                <th scope="col">Variable</th>
                <th scope="col" className="num">Valor típico</th>
                <th scope="col">Frente al promedio</th>
              </tr>
            </thead>
            <tbody>
              {variables.map((variable) => (
                <tr key={variable.clave}>
                  <th scope="row">
                    {variable.etiqueta}
                    {variable.ayuda && <span className="segmento-variables-ayuda">{variable.ayuda}</span>}
                  </th>
                  <td className="num">{variable.valor}</td>
                  <td>
                    {variable.nivel
                      ? <span className={`segmento-nivel ${variable.nivel.clase}`}>{variable.nivel.texto}</span>
                      : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {indicadores.length > 0 && (
        <div className="segmento-variables-bloque">
          <h4>Otros indicadores</h4>
          <dl className="segmento-indicadores">
            {indicadores.map((indicador) => (
              <div key={indicador.clave}>
                <dt>{indicador.etiqueta}</dt>
                <dd>{indicador.valor}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </div>
  )
}
