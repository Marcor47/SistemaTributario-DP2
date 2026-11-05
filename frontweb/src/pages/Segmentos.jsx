import { useEffect, useMemo, useState } from 'react'
import { useSesionInterna } from '../context/sesionInterna.js'
import { aprobarSegmentacion, listarSegmentos, listarSegmentosPublicados } from '../services/segmentosService.js'
import { formatearPorcentaje } from '../utils/alertas.js'
import { fechaHora } from '../utils/formato.js'
import {
  agruparSegmentos,
  claseEstadoSegmento,
  etiquetaEstadoSegmento,
  formatearVariablesCaracteristicas,
  participacionSegmento,
} from '../utils/segmentos.js'
import './Segmentos.css'

/*
 * HU031 (RF-043): revisión, aprobación y publicación de segmentos. La aprobación vive fuera del dashboard;
 * el dashboard solo muestra la segmentación publicada como filtro y en la distribución por segmento.
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
      ? 'El segmento de esta segmentación quedará disponible como filtro del dashboard.'
      : `Los ${cantidad} segmentos de esta segmentación quedarán disponibles como filtro del dashboard.`
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
            completa y, al publicarla, queda disponible como filtro en el dashboard. Los segmentos no disparan
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
                <p className="hint">Es la segmentación que el dashboard ofrece como filtro.</p>
              </div>
              <span className="estado ok">Visible en el dashboard</span>
            </div>
            {estado.publicados.length === 0 ? (
              <div className="info" role="status">
                Aún no hay una segmentación publicada. El filtro de segmento aparecerá en el dashboard cuando
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
          Al aprobar, la segmentación publicada actual se retira y esta queda disponible como filtro del dashboard.
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
          {grupo.segmentos.map((segmento) => (
            <tr key={segmento.idSegmento}>
              <th scope="row">{segmento.nombre}</th>
              <td>{segmento.descripcion || '—'}</td>
              <td className="num">{segmento.tamano}</td>
              <td className="num">{formatearPorcentaje(participacionSegmento(segmento, grupo))}</td>
              <td>
                <details className="segmento-variables">
                  <summary>Ver variables</summary>
                  <pre>{formatearVariablesCaracteristicas(segmento.variablesCaracteristicas)}</pre>
                </details>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
