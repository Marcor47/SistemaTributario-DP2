import { useContext, useEffect, useState } from 'react'
import { pedir } from '../services/api.js'
import { SesionInternaContext } from '../context/sesionInterna.js'
import { AccesoDenegado } from './AccesoInterno.jsx'
import './AccesoInterno.css'
import { fecha, moneda } from '../utils/formato.js'

// HU021 (RF-035): probabilidad y categoría del riesgo de mora del contribuyente, con los factores que la explican.
const CATEGORIAS = {
  BAJO: { texto: 'Riesgo bajo', clase: 'ok' },
  MEDIO: { texto: 'Riesgo medio', clase: 'aviso' },
  ALTO: { texto: 'Riesgo alto', clase: 'critico' },
}

export default function RiesgoMoraPanel({ contribuyente }) {
  const sesionContexto = useContext(SesionInternaContext)
  const sesion = sesionContexto?.sesion
  const [estado, setEstado] = useState({ cargando: true, datos: null, error: '', errorObj: null })

  useEffect(() => {
    const controlador = new AbortController()
    pedir(`/contribuyentes/${encodeURIComponent(contribuyente.codigo)}/riesgo-mora`, { signal: controlador.signal })
      .then((datos) => { if (!controlador.signal.aborted) setEstado({ cargando: false, datos, error: '', errorObj: null }) })
      .catch((err) => {
        if (!controlador.signal.aborted) setEstado({
          cargando: false,
          datos: null,
          error: err.estado === 403
            ? 'Tu usuario no tiene permiso para consultar el riesgo de mora de contribuyentes.'
            : (err.message || 'No se pudo calcular el riesgo de mora.'),
          errorObj: err,
        })
      })
    return () => controlador.abort()
  }, [contribuyente.codigo])

  if (estado.cargando) return <p className="hint" role="status">Calculando el riesgo de mora…</p>
  if (estado.errorObj?.estado === 403 || estado.errorObj?.codigo === 'ACCESO_DENEGADO') {
    return (
      <AccesoDenegado
        titulo="Acceso restringido — Riesgo de mora"
        usuario={sesion}
        descripcion="El cálculo predictivo de riesgo de mora del contribuyente está restringido a perfiles autorizados."
        rolesPermitidos={['ANALISTA_RENTAS', 'GERENCIA']}
      />
    )
  }
  if (estado.error) return <div className="info" role="status">{estado.error}</div>
  const { datos } = estado
  const categoria = CATEGORIAS[datos.categoria] ?? { texto: datos.categoria, clase: 'neutro' }
  const porcentaje = Number(datos.probabilidad) * 100

  return (
    <section className="pcard" aria-labelledby="riesgo-titulo">
      <h2 id="riesgo-titulo">Riesgo de mora</h2>
      <p className="hint">Estimado al {fecha(datos.fechaCorte)} con {datos.modelo.toLowerCase()}.</p>
      <div className="resumen">
        <div>
          <span className="pl">Probabilidad de mora</span>
          <strong>{porcentaje.toLocaleString('es-PE', { maximumFractionDigits: 1 })} %</strong>
          <span className={`estado ${categoria.clase}`}>{categoria.texto}</span>
        </div>
        <div><span className="pl">Cuotas evaluadas</span><strong>{datos.cuotasEvaluadas}</strong></div>
        <div className={datos.cuotasImpagas ? 'deuda' : ''}><span className="pl">Impagas</span><strong>{datos.cuotasImpagas}</strong></div>
        <div><span className="pl">Pagadas con atraso</span><strong>{datos.cuotasConAtraso}</strong></div>
        <div className={Number(datos.deudaVencida) > 0 ? 'deuda' : ''}><span className="pl">Deuda vencida</span><strong>{moneda(datos.deudaVencida)}</strong></div>
      </div>
      <h3>Factores de la clasificación</h3>
      <ul>{datos.factores.map((factor) => <li key={factor}>{factor}</li>)}</ul>
      <p className="hint">Bajo: menos de 30 %. Medio: de 30 % a menos de 60 %. Alto: 60 % o más. Orienta la cobranza preventiva; no reemplaza la revisión del caso.</p>
    </section>
  )
}
