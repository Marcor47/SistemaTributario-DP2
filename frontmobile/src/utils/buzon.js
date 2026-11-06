export const TIPOS_DOCUMENTO = {
  CONSTANCIA: 'Constancia de No Adeudo',
  ESTADO_CUENTA: 'Estado de cuenta',
  RESPUESTA_SOLICITUD: 'Respuesta a solicitud de predio',
  RESPUESTA_RECLAMO: 'Respuesta a reclamo',
}

export function filtrarDocumentos(documentos, filtro) {
  return documentos.filter((d) => filtro === 'SIN_LEER' ? !d.leido : !filtro || d.tipo === filtro)
}

export function fechaHoraBuzon(valor) {
  const partes = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(valor || '')
  return partes ? `${partes[3]}/${partes[2]}/${partes[1]} ${partes[4]}:${partes[5]}` : '—'
}
