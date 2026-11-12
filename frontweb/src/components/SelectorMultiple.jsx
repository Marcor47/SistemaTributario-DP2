import { useEffect, useMemo, useRef, useState } from 'react'
import {
  MAX_LONGITUD_VALOR_ALCANCE,
  MAX_VALORES_ALCANCE,
  filtrarOpcionesAlcance,
} from '../utils/accesoReportes.js'
import { normalizar } from '../utils/formato.js'
import './SelectorMultiple.css'

function comoOpcion(opcion) {
  if (typeof opcion === 'string') {
    const valor = opcion.trim()
    return { valor, etiqueta: valor, detalle: null }
  }
  const valor = String(opcion?.valor ?? opcion?.etiqueta ?? '').trim()
  return {
    valor,
    etiqueta: String(opcion?.etiqueta ?? valor),
    detalle: opcion?.detalle ?? null,
  }
}

/*
 * HU045: selector múltiple con búsqueda para zonas y segmentos.
 * El valor guardado es siempre el texto de la opción; el detalle es solo informativo.
 */
export default function SelectorMultiple({
  id,
  etiqueta,
  descripcion,
  valores = [],
  opciones = [],
  onCambiar,
  permitirPersonalizado = true,
  maximo = MAX_VALORES_ALCANCE,
  maxLongitud = MAX_LONGITUD_VALOR_ALCANCE,
  cargando = false,
  error = '',
  vacio = 'No hay opciones disponibles.',
  placeholder = 'Buscar o escribir…',
  deshabilitado = false,
}) {
  const [abierto, setAbierto] = useState(false)
  const [texto, setTexto] = useState('')
  const [indiceActivo, setIndiceActivo] = useState(0)
  const contenedor = useRef(null)
  const entrada = useRef(null)

  const seleccionados = Array.isArray(valores) ? valores : []
  const opcionesNormalizadas = useMemo(
    () => (Array.isArray(opciones) ? opciones : []).map(comoOpcion).filter((opcion) => opcion.valor),
    [opciones],
  )
  const opcionesFiltradas = useMemo(
    () => filtrarOpcionesAlcance(opcionesNormalizadas, texto),
    [opcionesNormalizadas, texto],
  )
  const limiteAlcanzado = seleccionados.length >= maximo
  const textoLimpio = texto.trim().slice(0, maxLongitud)
  const coincideExacto = opcionesNormalizadas.some((opcion) => normalizar(opcion.valor) === normalizar(textoLimpio))
  const puedeAgregar = permitirPersonalizado && Boolean(textoLimpio) && !coincideExacto && !limiteAlcanzado
  const opcionesLista = useMemo(() => {
    const lista = opcionesFiltradas.map((opcion) => ({ ...opcion, personalizado: false }))
    if (puedeAgregar) {
      lista.push({
        valor: textoLimpio,
        etiqueta: `Agregar «${textoLimpio}»`,
        detalle: null,
        personalizado: true,
      })
    }
    return lista
  }, [opcionesFiltradas, puedeAgregar, textoLimpio])
  const mostrarLista = abierto && !deshabilitado
  const indiceVisible = opcionesLista.length ? Math.min(indiceActivo, opcionesLista.length - 1) : 0

  useEffect(() => {
    if (!mostrarLista) return undefined
    function alClicFuera(evento) {
      if (!contenedor.current?.contains(evento.target)) setAbierto(false)
    }
    document.addEventListener('mousedown', alClicFuera)
    return () => document.removeEventListener('mousedown', alClicFuera)
  }, [mostrarLista])

  useEffect(() => {
    if (!mostrarLista) return
    const elemento = document.getElementById(`${id}-opcion-${indiceVisible}`)
    elemento?.scrollIntoView({ block: 'nearest' })
  }, [id, indiceVisible, mostrarLista])

  function abrir() {
    if (deshabilitado) return
    setAbierto(true)
    setIndiceActivo(0)
  }

  function alternar(valor) {
    const limpio = String(valor ?? '').trim().slice(0, maxLongitud)
    if (!limpio) return
    const clave = normalizar(limpio)
    const yaSeleccionado = seleccionados.some((item) => normalizar(item) === clave)
    if (yaSeleccionado) {
      onCambiar(seleccionados.filter((item) => normalizar(item) !== clave))
    } else if (!limiteAlcanzado) {
      onCambiar([...seleccionados, limpio])
    }
    setTexto('')
    setIndiceActivo(0)
    setAbierto(true)
    requestAnimationFrame(() => entrada.current?.focus())
  }

  function quitar(valor) {
    onCambiar(seleccionados.filter((item) => item !== valor))
  }

  function alTecla(evento) {
    if (evento.key === 'ArrowDown') {
      evento.preventDefault()
      if (!mostrarLista) {
        abrir()
        return
      }
      setIndiceActivo((actual) => (opcionesLista.length
        ? Math.min(actual + 1, opcionesLista.length - 1)
        : 0))
      return
    }
    if (evento.key === 'ArrowUp') {
      evento.preventDefault()
      if (!mostrarLista) {
        abrir()
        return
      }
      setIndiceActivo((actual) => Math.max(actual - 1, 0))
      return
    }
    if (evento.key === 'Home' && mostrarLista) {
      evento.preventDefault()
      setIndiceActivo(0)
      return
    }
    if (evento.key === 'End' && mostrarLista) {
      evento.preventDefault()
      setIndiceActivo(Math.max(0, opcionesLista.length - 1))
      return
    }
    if (evento.key === 'Enter') {
      if (!mostrarLista) {
        abrir()
        return
      }
      const opcion = opcionesLista[indiceVisible]
      if (!opcion) return
      evento.preventDefault()
      alternar(opcion.valor)
      return
    }
    if (evento.key === 'Escape') {
      setAbierto(false)
      return
    }
    if (evento.key === 'Backspace' && !texto && seleccionados.length) {
      evento.preventDefault()
      quitar(seleccionados[seleccionados.length - 1])
    }
  }

  const mensajeVacio = error
    ? 'No se pudieron cargar las opciones. Escribe un valor para agregarlo.'
    : vacio

  return (
    <div className="selector-multiple" ref={contenedor}>
      <label id={`${id}-etiqueta`} htmlFor={id} className="selector-multiple__etiqueta">{etiqueta}</label>
      <p className="hint selector-multiple__ayuda" id={`${id}-ayuda`}>{descripcion}</p>

      <div
        className={`selector-multiple__campo${mostrarLista ? ' selector-multiple__campo--abierto' : ''}${deshabilitado ? ' selector-multiple__campo--deshabilitado' : ''}`}
        onClick={(evento) => {
          if (evento.target.closest('button')) return
          abrir()
          entrada.current?.focus()
        }}
      >
        <div className="selector-multiple__chips">
          {seleccionados.length === 0
            ? <span className="selector-multiple__sin-restriccion">Sin restricción: se permiten todos.</span>
            : seleccionados.map((valor) => (
              <span className="selector-multiple__chip" key={valor}>
                <span>{valor}</span>
                <button
                  type="button"
                  className="selector-multiple__quitar"
                  aria-label={`Quitar ${valor}`}
                  onClick={() => quitar(valor)}
                  disabled={deshabilitado}
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true">
                    <path d="M6 6l12 12M18 6 6 18" />
                  </svg>
                </button>
              </span>
            ))}
          <input
            ref={entrada}
            id={id}
            className="selector-multiple__entrada"
            type="text"
            role="combobox"
            aria-expanded={mostrarLista}
            aria-controls={`${id}-lista`}
            aria-autocomplete="list"
            aria-activedescendant={mostrarLista && opcionesLista[indiceVisible] ? `${id}-opcion-${indiceVisible}` : undefined}
            aria-labelledby={`${id}-etiqueta`}
            aria-describedby={`${id}-ayuda${error ? ` ${id}-error` : ''}`}
            autoComplete="off"
            spellCheck="false"
            value={texto}
            maxLength={maxLongitud}
            placeholder={seleccionados.length ? 'Buscar o agregar…' : placeholder}
            disabled={deshabilitado}
            onChange={(evento) => {
              setTexto(evento.target.value)
              setIndiceActivo(0)
              setAbierto(true)
            }}
            onFocus={abrir}
            onClick={abrir}
            onBlur={(evento) => {
              if (!contenedor.current?.contains(evento.relatedTarget)) setAbierto(false)
            }}
            onKeyDown={alTecla}
          />
        </div>
        <span className="selector-multiple__flecha" aria-hidden="true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="m6 9 6 6 6-6" />
          </svg>
        </span>

        {mostrarLista && (
          <div
            id={`${id}-lista`}
            className="selector-multiple__panel"
            role="listbox"
            aria-multiselectable="true"
            aria-labelledby={`${id}-etiqueta`}
          >
            {cargando && <div className="selector-multiple__estado" role="status">Cargando opciones…</div>}
            {!cargando && opcionesLista.length === 0 && (
              <div className="selector-multiple__estado">{mensajeVacio}</div>
            )}
            {!cargando && opcionesLista.map((opcion, indice) => {
              const seleccionado = seleccionados.some((item) => normalizar(item) === normalizar(opcion.valor))
              const noDisponible = limiteAlcanzado && !seleccionado
              return (
                <div
                  key={`${opcion.valor}-${indice}`}
                  id={`${id}-opcion-${indice}`}
                  className={`selector-multiple__opcion${seleccionado ? ' selector-multiple__opcion--seleccionada' : ''}${opcion.personalizado ? ' selector-multiple__opcion--personalizada' : ''}${indice === indiceVisible ? ' selector-multiple__opcion--activa' : ''}${noDisponible ? ' selector-multiple__opcion--no-disponible' : ''}`}
                  role="option"
                  aria-selected={seleccionado}
                  aria-disabled={noDisponible || undefined}
                  onMouseDown={(evento) => evento.preventDefault()}
                  onMouseEnter={() => setIndiceActivo(indice)}
                  onClick={() => { if (!noDisponible) alternar(opcion.valor) }}
                >
                  <span className="selector-multiple__icono" aria-hidden="true">
                    {opcion.personalizado ? (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                        <path d="M12 5v14M5 12h14" />
                      </svg>
                    ) : seleccionado ? (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                        <path d="m5 12 4 4L19 6" />
                      </svg>
                    ) : (
                      <span className="selector-multiple__punto" />
                    )}
                  </span>
                  <span className="selector-multiple__texto">
                    <strong>{opcion.etiqueta}</strong>
                    {opcion.detalle && <small>{opcion.detalle}</small>}
                  </span>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {limiteAlcanzado && (
        <p className="selector-multiple__limite" role="status">Llegaste al máximo de {maximo} valores.</p>
      )}
      {error && <p className="selector-multiple__error" id={`${id}-error`} role="alert">{error}</p>}
    </div>
  )
}
