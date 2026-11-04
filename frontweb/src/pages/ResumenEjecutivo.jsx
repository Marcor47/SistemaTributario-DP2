import { useMemo, useState } from 'react'
import { descargarResumen, generarResumen, marcarRevisado } from '../services/resumenEjecutivoService.js'
import {
  ETIQUETAS_ESTADO, MAXIMO_PALABRAS, MESES, armarPeriodo, contarPalabras, formatoPeriodo, formatoValor,
  verificarCifras,
} from '../utils/resumenEjecutivo.js'
import './ConsultasTributarias.css'
import './ResumenEjecutivo.css'

const ANIOS = Array.from({ length: 4 }, (_, i) => new Date().getFullYear() - i)

// El navegador guarda el archivo; no se envía nada a ningún servidor.
function guardarArchivo({ nombreArchivo, contenido }) {
  const url = URL.createObjectURL(new Blob([contenido], { type: 'text/plain;charset=utf-8' }))
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivo
  enlace.click()
  URL.revokeObjectURL(url)
}

export default function ResumenEjecutivo() {
  const [filtros, setFiltros] = useState({ anio: String(ANIOS[0]), mes: '' })
  const [resumen, setResumen] = useState(null)
  const [trabajando, setTrabajando] = useState(null) // 'generar' | 'revisar' | 'descargar'
  const [error, setError] = useState(null)

  const cifras = useMemo(
    () => (resumen ? verificarCifras(resumen.texto, resumen.indicadoresUsados) : []),
    [resumen],
  )
  const palabras = resumen ? contarPalabras(resumen.texto) : 0
  const sinRespaldo = cifras.filter((cifra) => !cifra.respaldada)
  const revisado = resumen?.estado === 'REVISADO'

  async function ejecutar(accion, tarea) {
    setTrabajando(accion)
    setError(null)
    try {
      await tarea()
    } catch (falla) {
      setError(falla)
    } finally {
      setTrabajando(null)
    }
  }

  function generar(evento) {
    evento.preventDefault()
    setResumen(null)
    ejecutar('generar', async () => setResumen(await generarResumen(armarPeriodo(filtros.anio, filtros.mes))))
  }

  const revisar = () => ejecutar('revisar', async () => setResumen(await marcarRevisado(resumen)))
  const descargar = () => ejecutar('descargar', async () => guardarArchivo(await descargarResumen(resumen)))

  return (
    <div className="consulta-tributaria" aria-busy={trabajando !== null}>
      <form className="filters consulta-filtros" onSubmit={generar} aria-label="Generar resumen ejecutivo">
        <div className="fld">
          <label htmlFor="resumen-anio">Año</label>
          <select id="resumen-anio" value={filtros.anio} onChange={(e) => setFiltros({ anio: e.target.value, mes: '' })}>
            {ANIOS.map((anio) => <option key={anio}>{anio}</option>)}
          </select>
        </div>
        <div className="fld">
          <label htmlFor="resumen-mes">Mes</label>
          <select id="resumen-mes" value={filtros.mes} onChange={(e) => setFiltros({ ...filtros, mes: e.target.value })} required>
            <option value="">Elige el mes</option>
            {MESES.map((mes, i) => <option key={mes} value={i + 1}>{mes}</option>)}
          </select>
        </div>
        <button className="btn primary" type="submit" disabled={trabajando !== null || !filtros.mes}>
          {trabajando === 'generar' ? 'Generando…' : 'Generar resumen'}
        </button>
      </form>

      {error && (
        <div className="alert" role="alert">
          <strong>{error.codigo === 'PERIODO_SIN_INDICADORES' ? 'El periodo no tiene indicadores.' : 'No se pudo completar la acción.'}</strong>
          <p>{error.message}</p>
        </div>
      )}

      {!resumen && !error && trabajando !== 'generar' && (
        <section className="pcard consulta-vacio" role="status">
          <h2>Sin resumen generado</h2>
          <p>Elige el periodo y genera el resumen. El texto se redacta con un modelo local, solo con los indicadores calculados de ese periodo.</p>
        </section>
      )}

      {resumen && <>
        {resumen.demo && (
          <div className="info" role="note">
            <strong>Datos ficticios de demostración.</strong> El servicio de resúmenes no está disponible: este texto no lo
            generó el modelo de lenguaje y no se guarda en el servidor.
          </div>
        )}

        <section className="pcard" aria-labelledby="resumen-titulo">
          <div className="consulta-cabecera">
            <div>
              <h2 id="resumen-titulo">Resumen de {formatoPeriodo(resumen.periodo)}</h2>
              <p className="hint">Revísalo contra los indicadores antes de descargarlo.</p>
            </div>
            <span className={`estado ${revisado ? 'ok' : 'aviso'}`}>{ETIQUETAS_ESTADO[resumen.estado] ?? resumen.estado}</span>
          </div>
          <p className="resumen-texto">{resumen.texto}</p>
          <p className="resumen-palabras hint">
            <span>{palabras} de {MAXIMO_PALABRAS} palabras</span>
            {palabras > MAXIMO_PALABRAS && <span className="estado critico">Supera el máximo</span>}
          </p>
        </section>

        <section className="pcard" aria-labelledby="resumen-cifras">
          <h2 id="resumen-cifras">Cifras del texto</h2>
          {cifras.length === 0
            ? <p className="hint">El texto no incluye cifras para comprobar.</p>
            : sinRespaldo.length === 0
              ? <p className="info" role="status">Las {cifras.length} cifras del texto coinciden con un indicador calculado.</p>
              : <div className="alert" role="alert">
                <strong>{sinRespaldo.length === 1 ? 'Una cifra no coincide' : `${sinRespaldo.length} cifras no coinciden`} con los indicadores.</strong>
                <ul className="resumen-cifras">{sinRespaldo.map((cifra, i) => <li key={`${cifra.cifra}-${i}`}>{cifra.cifra}</li>)}</ul>
              </div>}
        </section>

        <section className="pcard" aria-labelledby="resumen-indicadores">
          <h2 id="resumen-indicadores">Indicadores usados</h2>
          <div className="tabla-scroll" tabIndex={0} role="region" aria-label="Indicadores usados">
            <table className="tabla">
              <caption className="sr-only">Indicadores calculados que sustentan el resumen</caption>
              <thead><tr><th scope="col">Indicador</th><th scope="col" className="num">Valor</th></tr></thead>
              <tbody>
                {resumen.indicadoresUsados.map((indicador) => (
                  <tr key={indicador.codigo}><th scope="row">{indicador.nombre}</th><td className="num"><strong>{formatoValor(indicador)}</strong></td></tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="resumen-acciones">
            {!revisado && (
              <button type="button" className="btn primary" onClick={revisar} disabled={trabajando !== null}>
                {trabajando === 'revisar' ? 'Guardando…' : 'Marcar como revisado'}
              </button>
            )}
            <button type="button" className={revisado ? 'btn primary' : 'btn'} onClick={descargar} disabled={!revisado || trabajando !== null}>
              {trabajando === 'descargar' ? 'Descargando…' : 'Descargar (.txt)'}
            </button>
            {!revisado && <p className="hint">Solo se puede descargar después de marcarlo como revisado.</p>}
          </div>
        </section>
      </>}
    </div>
  )
}
