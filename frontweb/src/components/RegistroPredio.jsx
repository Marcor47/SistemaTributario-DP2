import { useEffect, useState } from 'react'
import { obtenerCatalogosPredio, registrarPredio } from '../services/predioService.js'
import { hoyLima } from '../utils/formato.js'

// HU036 (RF-048): el Analista de Rentas registra un predio nuevo con su contribuyente titular. Zonas y usos salen de
// los predios ya registrados; el backend valida el contribuyente, el código y los montos.
const INICIAL = {
  codigoContribuyente: '', codigoPredio: '', numeroCatastral: '', ubicacion: '', usoPredio: '', tipoPredio: 'URBANO',
  areaTerreno: '', baseImponible: '', fechaAdquisicion: '', zona: '', condicionPropiedad: 'PROPIETARIO',
  porcentajePropiedad: '100',
}

function Campo({ id, etiqueta, children, error }) {
  return (
    <div className={`fld${error ? ' fld-error' : ''}`}>
      <label htmlFor={id}>{etiqueta}</label>
      {children}
      {error && <span className="campo-error" role="alert">{error}</span>}
    </div>
  )
}

export default function RegistroPredio({ onRegistrado, onCancelar }) {
  const [datos, setDatos] = useState(INICIAL)
  const [catalogos, setCatalogos] = useState({ zonas: [], usos: [] })
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    const controlador = new AbortController()
    obtenerCatalogosPredio({ signal: controlador.signal })
      .then((c) => { if (!controlador.signal.aborted) setCatalogos(c) })
      .catch((err) => { if (!controlador.signal.aborted) setError(err.message || 'No se pudo cargar las zonas y usos.') })
    return () => controlador.abort()
  }, [])

  const cambiar = (campo) => (evento) => {
    setDatos((actual) => ({ ...actual, [campo]: evento.target.value }))
    setErrores((actual) => ({ ...actual, [campo]: undefined }))
  }

  function validar() {
    const faltan = {}
    if (!datos.codigoContribuyente.trim()) faltan.codigoContribuyente = 'Ingresa el código del contribuyente titular.'
    if (!/^[A-Za-z0-9-]{1,12}$/.test(datos.codigoPredio.trim())) faltan.codigoPredio = 'Usa letras, números y guion (hasta 12).'
    if (!datos.ubicacion.trim()) faltan.ubicacion = 'Ingresa la ubicación del predio.'
    if (!datos.zona) faltan.zona = 'Elige la zona.'
    if (!(Number(datos.areaTerreno) > 0)) faltan.areaTerreno = 'Debe ser mayor que 0.'
    if (datos.baseImponible === '' || Number(datos.baseImponible) < 0) faltan.baseImponible = 'Ingresa el autoavalúo (0 o más).'
    const porcentaje = Number(datos.porcentajePropiedad)
    if (!(porcentaje > 0 && porcentaje <= 100)) faltan.porcentajePropiedad = 'Debe estar entre 0.01 y 100.'
    setErrores(faltan)
    return Object.keys(faltan).length === 0
  }

  async function guardar(evento) {
    evento.preventDefault()
    setError('')
    if (!validar()) return
    setGuardando(true)
    try {
      const predio = await registrarPredio(datos)
      setDatos(INICIAL)
      onRegistrado(predio)
    } catch (err) {
      setError(err.detalles?.length ? `${err.message} ${err.detalles.join(' ')}` : err.message || 'No se pudo registrar el predio.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="pcard" aria-labelledby="registro-predio-titulo">
      <h2 id="registro-predio-titulo">Registrar predio nuevo</h2>
      <p className="hint">El predio se registra en el padrón con su titular y queda disponible para la emisión de tributos.</p>
      {error && <div className="alert" role="alert">{error}</div>}
      <form className="filters registro-predio" onSubmit={guardar} noValidate>
        <Campo id="rp-contribuyente" etiqueta="Código del contribuyente" error={errores.codigoContribuyente}>
          <input id="rp-contribuyente" value={datos.codigoContribuyente} maxLength={10} onChange={cambiar('codigoContribuyente')} />
        </Campo>
        <Campo id="rp-codigo" etiqueta="Código del predio" error={errores.codigoPredio}>
          <input id="rp-codigo" value={datos.codigoPredio} maxLength={12} onChange={cambiar('codigoPredio')} />
        </Campo>
        <Campo id="rp-catastral" etiqueta="Código catastral (opcional)">
          <input id="rp-catastral" value={datos.numeroCatastral} maxLength={30} onChange={cambiar('numeroCatastral')} />
        </Campo>
        <Campo id="rp-ubicacion" etiqueta="Ubicación" error={errores.ubicacion}>
          <input id="rp-ubicacion" value={datos.ubicacion} maxLength={250} placeholder="Vía, número, interior y urbanización" onChange={cambiar('ubicacion')} />
        </Campo>
        <Campo id="rp-zona" etiqueta="Zona" error={errores.zona}>
          <select id="rp-zona" value={datos.zona} onChange={cambiar('zona')}>
            <option value="">Elige la zona</option>
            {catalogos.zonas.map((z) => <option key={z} value={z}>{z}</option>)}
          </select>
        </Campo>
        <Campo id="rp-uso" etiqueta="Uso">
          <select id="rp-uso" value={datos.usoPredio} onChange={cambiar('usoPredio')}>
            <option value="">Sin especificar</option>
            {catalogos.usos.map((u) => <option key={u} value={u}>{u}</option>)}
          </select>
        </Campo>
        <Campo id="rp-tipo" etiqueta="Tipo de predio">
          <select id="rp-tipo" value={datos.tipoPredio} onChange={cambiar('tipoPredio')}>
            <option value="URBANO">Urbano</option>
            <option value="RUSTICO">Rústico</option>
            <option value="OTRO">Otro</option>
          </select>
        </Campo>
        <Campo id="rp-area" etiqueta="Área del terreno (m²)" error={errores.areaTerreno}>
          <input id="rp-area" type="number" min="0.01" step="0.01" value={datos.areaTerreno} onChange={cambiar('areaTerreno')} />
        </Campo>
        <Campo id="rp-base" etiqueta="Autoavalúo (S/)" error={errores.baseImponible}>
          <input id="rp-base" type="number" min="0" step="0.01" value={datos.baseImponible} onChange={cambiar('baseImponible')} />
        </Campo>
        <Campo id="rp-fecha" etiqueta="Fecha de adquisición">
          <input id="rp-fecha" type="date" max={hoyLima()} value={datos.fechaAdquisicion} onChange={cambiar('fechaAdquisicion')} />
        </Campo>
        <Campo id="rp-condicion" etiqueta="Condición del titular">
          <select id="rp-condicion" value={datos.condicionPropiedad} onChange={cambiar('condicionPropiedad')}>
            <option value="PROPIETARIO">Propietario</option>
            <option value="COPROPIETARIO">Copropietario</option>
            <option value="POSEEDOR">Poseedor</option>
          </select>
        </Campo>
        <Campo id="rp-porcentaje" etiqueta="Porcentaje de propiedad" error={errores.porcentajePropiedad}>
          <input id="rp-porcentaje" type="number" min="0.01" max="100" step="0.01" value={datos.porcentajePropiedad} onChange={cambiar('porcentajePropiedad')} />
        </Campo>
        <button className="btn primary" type="submit" disabled={guardando}>{guardando ? 'Registrando…' : 'Registrar predio'}</button>
        <button className="btn" type="button" onClick={onCancelar} disabled={guardando}>Cancelar</button>
      </form>
    </section>
  )
}
