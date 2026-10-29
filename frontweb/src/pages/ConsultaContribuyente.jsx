import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import CobranzaPanel from '../components/CobranzaPanel.jsx'
import FichaDatos from '../components/FichaDatos.jsx'
import { obtenerContribuyentePorDocumento } from '../services/contribuyenteService.js'
import EstadoCuenta from './EstadoCuenta.jsx'
import RegularidadPago from './RegularidadPago.jsx'

const PESTANAS = [
  { id: 'ficha', nombre: 'Ficha' },
  { id: 'cuenta', nombre: 'Estado de cuenta' },
  { id: 'cobranza', nombre: 'Cobranza' },
  { id: 'regularidad', nombre: 'Regularidad de pago' },
]

const VISTAS = new Set(PESTANAS.map((pestana) => pestana.id))

export default function ConsultaContribuyente() {
  const [contribuyente, setContribuyente] = useState(null)
  const [ficha, setFicha] = useState(null)
  const [ocupadoFicha, setOcupadoFicha] = useState(false)
  const [errorFicha, setErrorFicha] = useState('')
  const [parametros, setParametros] = useSearchParams()
  const botones = useRef([])
  const solicitudFicha = useRef(0)
  const vistaSolicitada = parametros.get('vista')
  const vista = VISTAS.has(vistaSolicitada) ? vistaSolicitada : 'ficha'
  const codigo = contribuyente?.codigo
  const tipoDoc = contribuyente?.tipoDoc
  const doc = contribuyente?.doc

  useEffect(() => {
    if (!codigo || !tipoDoc || !doc) return undefined
    let activo = true
    obtenerContribuyentePorDocumento(tipoDoc, doc)
      .then((resultado) => {
        if (activo) setFicha(resultado)
      })
      .catch((err) => {
        if (activo) {
          setErrorFicha(err.message || 'No se pudo consultar la ficha del contribuyente. Intenta nuevamente.')
        }
      })
      .finally(() => {
        if (activo) setOcupadoFicha(false)
      })

    return () => { activo = false }
  }, [codigo, tipoDoc, doc])

  function seleccionarContribuyente(nuevo) {
    const mismo = Boolean(
      nuevo
      && nuevo.codigo === codigo
      && nuevo.tipoDoc === tipoDoc
      && nuevo.doc === doc,
    )
    setContribuyente(nuevo)
    if (mismo) return
    setFicha(null)
    setErrorFicha('')
    setOcupadoFicha(Boolean(nuevo))
  }

  async function recargarFicha() {
    if (!tipoDoc || !doc) return
    const actual = ++solicitudFicha.current
    setErrorFicha('')
    setOcupadoFicha(true)
    try {
      const resultado = await obtenerContribuyentePorDocumento(tipoDoc, doc)
      if (actual === solicitudFicha.current) setFicha(resultado)
    } catch (err) {
      if (actual === solicitudFicha.current) {
        setErrorFicha(err.message || 'No se pudo consultar la ficha del contribuyente. Intenta nuevamente.')
      }
    } finally {
      if (actual === solicitudFicha.current) setOcupadoFicha(false)
    }
  }

  function cambiarVista(id) {
    const nuevos = new URLSearchParams(parametros)
    if (id === 'ficha') nuevos.delete('vista')
    else nuevos.set('vista', id)
    setParametros(nuevos)
  }

  function navegarPestanas(evento, indice) {
    let siguiente
    if (evento.key === 'ArrowRight') siguiente = (indice + 1) % PESTANAS.length
    else if (evento.key === 'ArrowLeft') siguiente = (indice + PESTANAS.length - 1) % PESTANAS.length
    else if (evento.key === 'Home') siguiente = 0
    else if (evento.key === 'End') siguiente = PESTANAS.length - 1
    else return
    evento.preventDefault()
    cambiarVista(PESTANAS[siguiente].id)
    botones.current[siguiente].focus()
  }

  return (
    <>
      <section className="pcard ficha-cabecera">
        <div>
          <h2>Ficha del contribuyente</h2>
          <p className="hint">Busca por tipo y número de documento para consultar su ficha, predios, estado de cuenta y cobranza.</p>
          {contribuyente && (
            <p className="hint">
              Consulta actual: <strong>{ficha?.nombre ?? contribuyente.nombre}</strong> · Código {contribuyente.codigo}
              {' · '}{contribuyente.tipoDoc} {contribuyente.doc}
            </p>
          )}
        </div>
        {contribuyente && ficha && (
          <span className={`estado ${ficha.estado === 'ACTIVO' ? 'ok' : 'critico'}`}>
            <span aria-hidden="true">{ficha.estado === 'ACTIVO' ? '✓' : '!'}</span> {ficha.estado}
          </span>
        )}
      </section>
      <EstadoCuenta
        onSeleccionarContribuyente={seleccionarContribuyente}
        renderDetalle={(detalleCuenta) => (
          <>
            <div className="pestanas-contribuyente" role="tablist" aria-label="Información del contribuyente">
              {PESTANAS.map((pestana, indice) => (
                <button
                  key={pestana.id}
                  ref={(elemento) => { botones.current[indice] = elemento }}
                  id={`pestana-${pestana.id}`}
                  className={`btn${vista === pestana.id ? ' primary' : ''}`}
                  type="button"
                  role="tab"
                  aria-selected={vista === pestana.id}
                  aria-controls={`panel-${pestana.id}`}
                  tabIndex={vista === pestana.id ? 0 : -1}
                  onClick={() => cambiarVista(pestana.id)}
                  onKeyDown={(evento) => navegarPestanas(evento, indice)}
                >
                  {pestana.nombre}
                </button>
              ))}
            </div>

            <div id="panel-ficha" role="tabpanel" aria-labelledby="pestana-ficha" hidden={vista !== 'ficha'} tabIndex={0}>
              <FichaDatos
                ficha={ficha}
                ocupado={ocupadoFicha}
                error={errorFicha}
                onReintentar={recargarFicha}
              />
            </div>

            <div id="panel-cuenta" role="tabpanel" aria-labelledby="pestana-cuenta" hidden={vista !== 'cuenta'} tabIndex={0}>
              {detalleCuenta}
            </div>

            <div id="panel-cobranza" role="tabpanel" aria-labelledby="pestana-cobranza" hidden={vista !== 'cobranza'} tabIndex={0}>
              {contribuyente && <CobranzaPanel key={contribuyente.codigo} contribuyente={contribuyente} />}
            </div>

            <div id="panel-regularidad" role="tabpanel" aria-labelledby="pestana-regularidad" hidden={vista !== 'regularidad'} tabIndex={0}>
              {contribuyente && (
                <RegularidadPago key={contribuyente.codigo} contribuyente={contribuyente} />
              )}
            </div>
          </>
        )}
      />
    </>
  )
}
