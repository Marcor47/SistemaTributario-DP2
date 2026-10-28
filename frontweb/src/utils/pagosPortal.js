const ESTADOS = new Set(['INICIADA', 'CONFIRMADA', 'RECHAZADA', 'EXPIRADA'])
const RUTA_SIMULADA = /^\/pasarela-simulada\/SIM-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function idPagoValido(id) {
  return typeof id === 'number' ? Number.isSafeInteger(id) && id > 0 : /^[1-9]\d{0,18}$/.test(String(id ?? ''))
}

function contratoInvalido() {
  return Object.assign(new Error('No se recibió una respuesta válida del pago. Consulta su estado antes de continuar.'), {
    codigo: 'RESPUESTA_PAGO_INVALIDA',
  })
}

export function normalizarPago(datos) {
  if (!datos || !idPagoValido(datos.idTransaccion) || !ESTADOS.has(datos.estado)
      || typeof datos.dePrueba !== 'boolean' || !Array.isArray(datos.obligaciones)) throw contratoInvalido()
  const montos = ['montoBase', 'montoDescuento', 'montoTotal']
  if (montos.some((campo) => datos[campo] == null || datos[campo] === '' || !Number.isFinite(Number(datos[campo])))) {
    throw contratoInvalido()
  }
  const pago = { ...datos, ...Object.fromEntries(montos.map((campo) => [campo, Number(datos[campo])])) }
  if (pago.montoBase <= 0 || pago.montoDescuento < 0 || pago.montoTotal <= 0
      || pago.montoDescuento > pago.montoBase
      || Math.abs(pago.montoBase - pago.montoDescuento - pago.montoTotal) > 0.005
      || (pago.estado === 'CONFIRMADA' && !pago.comprobante)) throw contratoInvalido()
  return pago
}

export function rutaPasarelaSimulada(pago) {
  if (pago.dePrueba !== true || pago.estado !== 'INICIADA' || !idPagoValido(pago.idTransaccion)
      || !RUTA_SIMULADA.test(pago.urlPasarela || '')) {
    throw Object.assign(new Error('Esta orden no dispone de una pasarela simulada válida. No se confirmó ningún pago desde esta pantalla.'), {
      codigo: 'PASARELA_SIMULADA_INVALIDA',
    })
  }
  return `${pago.urlPasarela}?transaccion=${encodeURIComponent(pago.idTransaccion)}`
}

export function mensajeErrorPago(error) {
  if (error?.estado === 401) return { titulo: 'Tu sesión ha vencido', accion: 'sesion', texto: 'Inicia sesión para consultar o continuar tu pago.' }
  const mensajes = {
    BENEFICIO_INDETERMINADO: { titulo: 'Acude a ventanilla', accion: 'ventanilla', texto: 'No se pudo determinar el beneficio aplicable. El pago virtual está bloqueado hasta que la Municipalidad lo verifique.' },
    SIN_COBRO_VIRTUAL: { titulo: 'Tus beneficios requieren atención en ventanilla', accion: 'ventanilla', texto: 'Los beneficios cubren toda la deuda. No se creó un cobro de cero; acude a ventanilla para su aplicación.' },
    SIN_DEUDA: { titulo: 'No tienes deuda pendiente para pagar', accion: 'cuenta', texto: 'Actualiza tu estado de cuenta para revisar la información más reciente.' },
    SALDO_CAMBIO: { titulo: 'La deuda o los beneficios cambiaron', accion: 'cuenta', texto: 'Esta orden ya no corresponde al total actual. Vuelve a consultar tu cuenta y revisa una nueva orden antes de pagar.' },
    PAGO_DEBE_CUBRIR_TODO: { titulo: 'El pago debe cubrir todo el saldo pendiente', accion: 'cuenta', texto: 'Vuelve a consultar tu cuenta. El pago virtual incluye todas las obligaciones; no admite cuotas ni conceptos seleccionados.' },
    ORDEN_NO_ENCONTRADA: { titulo: 'La orden de prueba ya no está disponible', accion: 'cuenta', texto: 'El simulador no reconoce esta orden. Consulta tu cuenta antes de iniciar un nuevo pago de prueba.' },
    PAGO_NO_ENCONTRADO: { titulo: 'No se encontró este pago en tu cuenta', accion: 'cuenta', texto: 'Comprueba la referencia o vuelve a tu estado de cuenta.' },
    PAGO_CERRADO: { titulo: 'Esta orden ya está cerrada', accion: 'cuenta', texto: 'Consulta el resultado del pago antes de iniciar una nueva orden.' },
    RESULTADO_DESCONOCIDO: { titulo: 'Aún no pudimos comprobar el resultado', accion: 'consultar', texto: 'No inicies otro pago. Consulta el estado de esta misma operación para saber si quedó registrada.' },
  }
  return mensajes[error?.codigo] || {
    titulo: 'No se pudo completar la operación', accion: 'reintentar',
    texto: error?.message || 'Intenta nuevamente en unos momentos.',
  }
}
