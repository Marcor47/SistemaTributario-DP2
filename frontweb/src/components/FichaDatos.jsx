import { fecha } from '../utils/formato.js'

function Campo({ etiqueta, valor }) {
  return (
    <div className="pf">
      <span className="pl">{etiqueta}</span>
      <span className="pv">{valor === '' || valor == null ? '—' : valor}</span>
    </div>
  )
}

function DatoPredio({ etiqueta, valor }) {
  return (
    <div>
      <dt>{etiqueta}</dt>
      <dd>{valor === '' || valor == null ? '—' : valor}</dd>
    </div>
  )
}

const TIPO_PERSONA = {
  NATURAL: 'Persona natural',
  JURIDICA: 'Persona jurídica',
}

const TIPO_PREDIO = {
  URBANO: 'Urbano',
  RUSTICO: 'Rústico',
  OTRO: 'Otro',
}

const CONDICION_PROPIEDAD = {
  PROPIETARIO: 'Propietario',
  COPROPIETARIO: 'Copropietario',
  POSESIONARIO: 'Posesionario',
}

function medida(valor, sufijo) {
  const numero = Number(valor)
  if (valor === '' || valor == null || !Number.isFinite(numero)) return '—'
  return `${numero.toLocaleString('es-PE', { maximumFractionDigits: 2 })} ${sufijo}`
}

export default function FichaDatos({ ficha, ocupado, error, onReintentar }) {
  if (ocupado) {
    return <p className="hint mensaje-carga" role="status">Consultando ficha del contribuyente…</p>
  }

  if (error) {
    return (
      <div className="alert" role="alert">
        <p>{error}</p>
        <button className="btn" type="button" onClick={onReintentar}>Reintentar ficha</button>
      </div>
    )
  }

  if (!ficha) {
    return <div className="info" role="status">Selecciona un contribuyente para consultar su ficha oficial.</div>
  }

  const activo = ficha.estado === 'ACTIVO'
  const predios = Array.isArray(ficha.predios) ? ficha.predios : []

  return (
    <>
      <section className="pcard" aria-label="Ficha oficial del contribuyente">
        <h2>Datos oficiales del contribuyente</h2>
        <p className="hint">Información consultada en SAMNET por tipo y número de documento.</p>
        <div className="ficha-estado">
          <span className={`estado ${activo ? 'ok' : 'critico'}`}>
            <span aria-hidden="true">{activo ? '✓' : '!'}</span> {ficha.estado}
          </span>
        </div>
        {!activo && (
          <div className="alert" role="status">
            El contribuyente está inactivo en SAMNET. La información se muestra solo para consulta.
          </div>
        )}
        <div className="pfields">
          <Campo etiqueta="Código de contribuyente" valor={ficha.codigo} />
          <Campo etiqueta="Nombre o razón social" valor={ficha.nombre} />
          <Campo etiqueta="Tipo de persona" valor={TIPO_PERSONA[ficha.tipoPersona] ?? ficha.tipoPersona} />
          <Campo etiqueta="Tipo de documento" valor={ficha.tipoDocumento} />
          <Campo etiqueta="Número de documento" valor={ficha.numeroDocumento} />
          <Campo etiqueta="Domicilio fiscal" valor={ficha.domicilioFiscal} />
        </div>
      </section>

      <section className="pcard" aria-labelledby="titulo-predios">
        <h2 id="titulo-predios">Predios del contribuyente</h2>
        <p className="hint">
          {predios.length === 0
            ? 'El contribuyente no figura como titular de predios registrados en SAMNET.'
            : `El contribuyente figura como titular en ${predios.length} ${predios.length === 1 ? 'predio' : 'predios'}.`}
        </p>
        {predios.length > 0 && (
          <ul className="predios-grid">
            {predios.map((predio, indice) => (
              <li key={predio.codigoPredio ?? indice}>
                <article className="predio-card" aria-label={`Predio ${predio.codigoPredio ?? ''}`.trim()}>
                  <header className="predio-card__cab">
                    <div>
                      <h3>{predio.ubicacion || 'Predio sin ubicación registrada'}</h3>
                      <p className="hint">
                        Código {predio.codigoPredio ?? '—'}
                        {predio.numeroCatastral ? ` · Catastro ${predio.numeroCatastral}` : ''}
                      </p>
                    </div>
                    {predio.condicionPropiedad && (
                      <span className="estado neutro">
                        {CONDICION_PROPIEDAD[predio.condicionPropiedad] ?? predio.condicionPropiedad}
                      </span>
                    )}
                  </header>
                  <dl className="predio-card__datos">
                    <DatoPredio etiqueta="Uso" valor={predio.usoPredio} />
                    <DatoPredio etiqueta="Tipo" valor={TIPO_PREDIO[predio.tipoPredio] ?? predio.tipoPredio} />
                    <DatoPredio etiqueta="Área del terreno" valor={medida(predio.areaTerreno, 'm²')} />
                    <DatoPredio etiqueta="Fecha de adquisición" valor={fecha(predio.fechaAdquisicion)} />
                    <DatoPredio etiqueta="% de propiedad" valor={medida(predio.porcentajePropiedad, '%')} />
                  </dl>
                </article>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
