import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext.jsx'
import { AccesoDenegado, AccesoRestringido, VerificandoAcceso } from './AccesoInterno.jsx'
import { obtenerDeclaracionJurada } from '../services/declaracionJuradaService.js'
import { ROLES_CONSULTA_PREDIO, tieneRolInterno } from '../utils/roles.js'
import { fecha, moneda } from '../utils/formato.js'
import './AccesoInterno.css'

const ETIQUETAS = {
  INSCRIPCION: 'Inscripción',
  ACTUALIZACION: 'Actualización',
  AMPLIACION: 'Ampliación',
  RECTIFICACION: 'Rectificación',
  PROCESADA: 'Procesada',
  PROPIETARIO: 'Propietario',
  'COMPRA-VENTA': 'Compra-venta',
  'REPRESENTANTE LEGAL': 'Representante legal',
}

function etiqueta(valor) {
  return ETIQUETAS[valor] ?? (valor || '—')
}

function autoavaluo(valor) {
  return valor == null || valor === '' ? 'No disponible' : moneda(valor)
}

function Campo({ nombre, children }) {
  return <div className="pf"><span className="pl">{nombre}</span><span className="pv">{children ?? '—'}</span></div>
}

function ResultadoDeclaracion({ datos }) {
  const { predio, declaracionVigente: vigente, historial, fechaConsulta, advertencias } = datos

  return (
    <section className="pcard dj-ficha" aria-label={`Declaración jurada del predio ${predio.codigoPredio}`}>
        <div className="dj-cabecera">
          <div>
            <h2>Declaración jurada del predio {predio.codigoPredio}</h2>
            <p className="hint">{predio.ubicacion || 'Ubicación no disponible'}</p>
          </div>
          <p className="hint dj-fecha">Consultado el {fecha(fechaConsulta)}</p>
        </div>
        <p className="hint dj-origen">Fuente: SAMNET · Código catastral: {predio.numeroCatastral || 'No disponible'}</p>

      {advertencias.length > 0 && (
        <div className="alert dj-advertencias" role="status">
          <strong>Observaciones de SAMNET</strong>
          <ul>{advertencias.map((aviso, indice) => <li key={`${aviso.codigo}-${indice}`}>{aviso.mensaje}</li>)}</ul>
        </div>
      )}

      <section className="dj-seccion" aria-label="Declaración jurada vigente">
        <div className="dj-cabecera">
          <h3>Declaración jurada vigente</h3>
          {vigente && <span className="estado ok">Vigente</span>}
        </div>
        {vigente ? (
          <>
            <dl className="dj-resumen">
              <div className="dj-autoavaluo"><dt>Autoavalúo declarado</dt><dd>{autoavaluo(vigente.autoavaluo)}</dd></div>
              <div><dt>Año</dt><dd>{vigente.anio ?? 'No disponible'}</dd></div>
              <div><dt>Número de declaración</dt><dd>{vigente.idDeclaracion}</dd></div>
            </dl>
            <div className="pfields dj-campos">
              <Campo nombre="Motivo">{etiqueta(vigente.motivo)}</Campo>
              <Campo nombre="Submotivo">{etiqueta(vigente.submotivo)}</Campo>
              <Campo nombre="Tipo de declarante">{etiqueta(vigente.tipoDeclarante)}</Campo>
              <Campo nombre="Estado de procesamiento">{etiqueta(vigente.estado)}</Campo>
            </div>
          </>
        ) : (
          <div className="dj-vacio">
            <strong>No hay una declaración jurada vigente para este predio.</strong>
            <p className="hint">{historial.length > 0
              ? 'Las declaraciones históricas se muestran a continuación.'
              : 'No hay declaraciones disponibles para mostrar.'}</p>
          </div>
        )}
      </section>

      <section className="dj-seccion" aria-label="Historial de declaraciones juradas">
        <h3>Historial de declaraciones juradas</h3>
        {historial.length > 0 ? (
          <div className="tabla-scroll" role="region" aria-label="Tabla de declaraciones históricas" tabIndex={0}>
            <table className="tabla dj-historial">
              <caption className="sr-only">Declaraciones históricas del predio {predio.codigoPredio}</caption>
              <thead>
                <tr>
                  <th scope="col">Declaración</th>
                  <th scope="col">Año</th>
                  <th scope="col" className="num">Autoavalúo</th>
                  <th scope="col">Motivo / submotivo</th>
                  <th scope="col">Declarante</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Vigencia</th>
                </tr>
              </thead>
              <tbody>
                {historial.map((declaracion) => (
                  <tr key={declaracion.idDeclaracion}>
                    <th scope="row">{declaracion.idDeclaracion}</th>
                    <td>{declaracion.anio ?? '—'}</td>
                    <td className="num dj-importe">{autoavaluo(declaracion.autoavaluo)}</td>
                    <td>{etiqueta(declaracion.motivo)}<span className="documento-descripcion">{etiqueta(declaracion.submotivo)}</span></td>
                    <td>{etiqueta(declaracion.tipoDeclarante)}</td>
                    <td>{etiqueta(declaracion.estado)}</td>
                    <td><span className="estado dj-no-vigente">No vigente</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <p className="hint dj-vacio">No hay declaraciones históricas registradas.</p>}
      </section>
      <p className="hint dj-nota">Observación: consulta de solo lectura. La vigencia corresponde a lo informado por SAMNET.</p>
    </section>
  )
}

const TITULOS_ERROR = {
  SESION_REQUERIDA: 'Tu sesión interna no está vigente',
  ERROR_HTTP: 'Sin permiso para consultar la declaración jurada',
  PREDIO_NO_ENCONTRADO: 'El predio no se encontró en SAMNET',
  DECLARACION_VIGENTE_AMBIGUA: 'No se puede determinar la declaración vigente',
  SAMNET_NO_DISPONIBLE: 'SAMNET no está disponible',
  SIN_CONEXION: 'No se pudo conectar con el servicio',
  RESPUESTA_INVALIDA: 'La respuesta no corresponde al predio seleccionado',
}

export default function DeclaracionJuradaPanel({ codigoPredio }) {
  const { sesion, limpiarSesion } = useAuth()
  const [intento, setIntento] = useState(0)
  const [expirada, setExpirada] = useState(false)
  const [consulta, setConsulta] = useState({ datos: null, error: null, ocupado: true })

  const esInterno = sesion.tipo === 'interno'
  const tieneRol = tieneRolInterno(sesion, ROLES_CONSULTA_PREDIO)

  useEffect(() => {
    if (!tieneRol) return undefined

    let activo = true
    const controlador = new AbortController()

    obtenerDeclaracionJurada(codigoPredio, { signal: controlador.signal })
      .then((datos) => {
        if (activo) setConsulta({ datos, error: null, ocupado: false })
      })
      .catch((error) => {
        if (!activo) return
        if (error.estado === 401) {
          // La sesión interna dura 15 minutos; se limpia el contexto y se pide reingresar.
          setExpirada(true)
          limpiarSesion()
          setConsulta({ datos: null, error: null, ocupado: false })
          return
        }
        setConsulta({ datos: null, error, ocupado: false })
      })

    return () => { activo = false; controlador.abort() }
  }, [codigoPredio, intento, tieneRol, limpiarSesion])

  function recargar() {
    setConsulta({ datos: null, error: null, ocupado: true })
    setIntento((valor) => valor + 1)
  }

  if (sesion.estado === 'cargando') {
    return <VerificandoAcceso mensaje="Verificando el acceso a la declaración jurada…" />
  }

  if (!esInterno) {
    return (
      <AccesoRestringido
        titulo={`Declaración jurada del predio ${codigoPredio}`}
        descripcion="Esta consulta está reservada al personal municipal con rol Analista de Rentas o Administrador."
        mensajeExpirada="Tu sesión interna venció. Vuelve a ingresar para consultar la declaración jurada."
        expirada={expirada}
      />
    )
  }

  if (!tieneRol) {
    return (
      <AccesoDenegado
        titulo="Acceso denegado"
        usuario={sesion.usuario}
        descripcion={`La declaración jurada del predio ${codigoPredio} solo la consulta el personal autorizado.`}
        rolesPermitidos={ROLES_CONSULTA_PREDIO}
      />
    )
  }

  return (
    <div className="declaracion-jurada" aria-busy={consulta.ocupado}>
      {consulta.ocupado ? (
        <p className="hint mensaje-carga" role="status">Consultando la declaración jurada del predio {codigoPredio} en SAMNET…</p>
      ) : consulta.error ? (
        <div className="alert dj-error" role="alert">
          <strong>{TITULOS_ERROR[consulta.error.codigo] || 'No se pudo consultar la declaración jurada'}</strong>
          <p className="hint">Predio {codigoPredio}</p>
          <p>{consulta.error.message || 'Intenta nuevamente en unos momentos.'}</p>
          <button className="btn" type="button" onClick={recargar}>Reintentar consulta</button>
        </div>
      ) : consulta.datos ? (
        <ResultadoDeclaracion datos={consulta.datos} />
      ) : (
        <div className="alert" role="alert">
          <strong>No se recibió información de la declaración jurada</strong>
          <p>Vuelve a abrir la pestaña para intentar la consulta nuevamente.</p>
        </div>
      )}
    </div>
  )
}
