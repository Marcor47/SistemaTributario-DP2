export function numeroOpcional(valor) {
  if (valor === null || valor === undefined || valor === '') return null
  const numero = Number(valor)
  return Number.isFinite(numero) ? numero : null
}

export function porcentaje(valor) {
  const numero = numeroOpcional(valor)
  return numero === null ? 'No disponible' : `${numero.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 4 })} %`
}

export function etiquetaTributo(clave) {
  return {
    PREDIAL: 'Impuesto Predial', IMPUESTO_PREDIAL: 'Impuesto Predial',
    LIMPIEZA_PUBLICA: 'Limpieza Pública', PARQUES_JARDINES: 'Parques y Jardines',
    SEGURIDAD_CIUDADANA: 'Seguridad Ciudadana',
  }[clave] || clave
}

// La consulta publicada no incluye los montos del detalle de cálculo.
export function normalizarMorosidad(respuesta) {
  const detalle = respuesta.total && typeof respuesta.total === 'object' ? respuesta.total : null
  const filas = (valores) => (valores || []).map((fila) => ({
    valorAlcance: fila.valorAlcance, indice: numeroOpcional(fila.indice),
    montoEmitido: numeroOpcional(fila.montoEmitido),
    montoVencido: numeroOpcional(fila.montoVencidoNoPagado ?? fila.montoVencido),
  }))
  return {
    lote: { idLote: respuesta.idLote, fechaCorte: respuesta.fechaCorte, anio: respuesta.anio,
      numeroPeriodo: respuesta.numeroPeriodo, periodo: respuesta.periodo },
    total: numeroOpcional(detalle ? detalle.indice : respuesta.total),
    montoEmitidoTotal: numeroOpcional(detalle?.montoEmitido),
    montoVencidoTotal: numeroOpcional(detalle?.montoVencidoNoPagado),
    porTributo: filas(respuesta.porTributo), porZona: filas(respuesta.porZona), esDemo: false,
  }
}
