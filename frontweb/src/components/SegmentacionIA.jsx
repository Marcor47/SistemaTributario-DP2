import { useEffect, useState } from 'react'
import { calcularSegmentacion, listarSegmentosBorrador } from '../services/calidadDatosService.js'
import { fecha } from '../utils/formato.js'

// HU030 (RF-043): el Responsable de datos ejecuta el modelo no supervisado (K-means) sobre la última carga publicada.
// Cada contribuyente activo recibe un segmento; los segmentos quedan en borrador hasta que la jefatura los apruebe.
const entero = (n) => Number(n ?? 0).toLocaleString('es-PE')
const decimal = (n) => Number(n ?? 0).toLocaleString('es-PE', { minimumFractionDigits: 3, maximumFractionDigits: 3 })

function TablaSegmentos({ segmentos }) {
  return (
    <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Segmentos generados">
      <table className="tabla">
        <caption className="sr-only">Segmentos generados con su tamaño y variables características</caption>
        <thead>
          <tr><th scope="col">Segmento</th><th scope="col" className="num">Contribuyentes</th><th scope="col">Variables características</th><th scope="col">Descripción</th></tr>
        </thead>
        <tbody>
          {segmentos.map((s) => (
            <tr key={s.idSegmento}>
              <th scope="row">{s.nombre}</th>
              <td className="num">{entero(s.tamano)}</td>
              <td>{s.variablesCaracteristicas || '—'}</td>
              <td>{s.descripcion || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function SegmentacionIA() {
  const [borrador, setBorrador] = useState(null)
  const [resultado, setResultado] = useState(null)
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    const controlador = new AbortController()
    listarSegmentosBorrador({ signal: controlador.signal })
      .then((lista) => { if (!controlador.signal.aborted) setBorrador(lista) })
      .catch((err) => { if (!controlador.signal.aborted) setError(err.message || 'No se pudo consultar los segmentos.') })
    return () => controlador.abort()
  }, [])

  async function ejecutar() {
    setOcupado(true)
    setError('')
    try {
      const respuesta = await calcularSegmentacion()
      setResultado(respuesta)
      setBorrador(respuesta.segmentos.filter((segmento) => segmento.estado === 'BORRADOR'))
    } catch (err) {
      setError(err.message || 'No se pudo ejecutar el modelo de segmentación.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <>
      <section className="pcard" aria-labelledby="segmentacion-titulo">
        <h2 id="segmentacion-titulo">Segmentación de contribuyentes con IA</h2>
        <p className="hint">
          El modelo agrupa a los contribuyentes activos según cómo pagan
          (porcentaje de cuotas pagadas a tiempo, días de atraso, deuda vencida, antigüedad y meses sin pagar)
          con la última carga publicada. Prueba varios números de grupos y elige el de mejor silueta. Los segmentos
          quedan en borrador: la jefatura de Rentas los aprueba en «Segmentación de contribuyentes» para publicarlos.
        </p>
        <div className="filters">
          <button className="btn primary" type="button" onClick={ejecutar} disabled={ocupado}>
            {ocupado ? 'Ejecutando el modelo…' : 'Ejecutar modelo de segmentación'}
          </button>
        </div>
        {error && <div className="alert" role="alert">{error}</div>}
        {resultado && (
          <p className="confirmacion" role="status">
            {resultado.nuevo ? 'Se generó' : 'La carga ya tenía calculado'} el modelo {resultado.versionModelo} (carga N.° {resultado.idLote},
            corte {fecha(resultado.fechaCorte)}): {entero(resultado.contribuyentes)} contribuyentes en {resultado.k} segmentos,
            silueta {decimal(resultado.silueta)}.
            {!resultado.nuevo && resultado.segmentos.some((s) => s.estado !== 'BORRADOR') && ' Esa segmentación ya fue revisada por la jefatura.'}
          </p>
        )}
      </section>

      {resultado?.evaluacion?.length > 0 && (
        <section className="pcard" aria-labelledby="evaluacion-titulo">
          <h2 id="evaluacion-titulo">Evaluación del número de segmentos</h2>
          <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Evaluación de k">
            <table className="tabla">
              <caption className="sr-only">Silueta, inercia y tamaño mínimo para cada número de segmentos probado</caption>
              <thead><tr><th scope="col">Segmentos (k)</th><th scope="col" className="num">Silueta</th><th scope="col" className="num">Inercia</th><th scope="col" className="num">Segmento más pequeño</th><th scope="col">Válido</th></tr></thead>
              <tbody>
                {resultado.evaluacion.map((e) => (
                  <tr key={e.k} aria-current={e.k === resultado.k ? 'true' : undefined}>
                    <th scope="row">{e.k}{e.k === resultado.k ? ' (elegido)' : ''}</th>
                    <td className="num">{decimal(e.silueta)}</td>
                    <td className="num">{entero(Math.round(e.inercia))}</td>
                    <td className="num">{entero(e.tamanoMinimo)}</td>
                    <td><span className={`estado ${e.valido ? 'ok' : 'neutro'}`}>{e.valido ? 'Sí' : 'No'}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="pcard" aria-labelledby="borrador-titulo">
        <h2 id="borrador-titulo">Segmentos pendientes de aprobación</h2>
        {!borrador && !error && <p className="hint" role="status">Consultando los segmentos…</p>}
        {borrador?.length === 0 && <p className="hint">No hay segmentos en borrador. Ejecuta el modelo para generar una nueva segmentación.</p>}
        {borrador?.length > 0 && <TablaSegmentos segmentos={borrador} />}
      </section>
    </>
  )
}
