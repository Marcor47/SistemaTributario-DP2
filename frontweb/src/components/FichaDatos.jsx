function Campo({ etiqueta, valor }) {
  return (
    <div className="pf">
      <span className="pl">{etiqueta}</span>
      <span className="pv">{valor === '' || valor == null ? '—' : valor}</span>
    </div>
  )
}

const TIPO_PERSONA = {
  NATURAL: 'Persona natural',
  JURIDICA: 'Persona jurídica',
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

  return (
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
  )
}
