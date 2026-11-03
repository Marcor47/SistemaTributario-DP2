const ESTADOS = new Set(['INICIADA', 'CONFIRMADA', 'RECHAZADA', 'EXPIRADA'])
const RUTA_SIMULADA = /^\/pasarela-simulada\/SIM-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function idPagoValido(id) {
  return typeof id === 'number' ? Number.isSafeInteger(id) && id > 0 : /^[1-9]\d{0,18}$/.test(String(id ?? ''))
}

export function normalizarPago(datos) {
  const campos = ['montoBase', 'montoDescuento', 'montoTotal']
  if (!datos || !idPagoValido(datos.idTransaccion) || !ESTADOS.has(datos.estado)
    || typeof datos.dePrueba !== 'boolean' || !Array.isArray(datos.obligaciones)
    || datos.obligaciones.length === 0
    || campos.some((campo) => datos[campo] == null || datos[campo] === '' || !Number.isFinite(Number(datos[campo])))) {
    throw respuestaInvalida()
  }
  const pago = { ...datos, ...Object.fromEntries(campos.map((campo) => [campo, Number(datos[campo])])) }
  if (pago.montoBase <= 0 || pago.montoDescuento < 0 || pago.montoTotal <= 0
    || pago.montoDescuento > pago.montoBase
    || Math.abs(pago.montoBase - pago.montoDescuento - pago.montoTotal) > 0.005
    || (pago.estado === 'CONFIRMADA' && !pago.comprobante)) throw respuestaInvalida()
  return pago
}

function respuestaInvalida() {
  return Object.assign(new Error('No se recibió una respuesta válida del pago. Consulta el estado de la operación.'), {
    codigo: 'RESPUESTA_PAGO_INVALIDA',
  })
}

export function validarPasarela(pago) {
  if (pago.dePrueba !== true || pago.estado !== 'INICIADA' || !RUTA_SIMULADA.test(pago.urlPasarela || '')) {
    throw Object.assign(new Error('La pasarela de esta orden no está disponible en la app. Consulta tu estado de cuenta.'), {
      codigo: 'PASARELA_NO_DISPONIBLE',
    })
  }
  return pago
}

export function mensajeErrorPago(error) {
  const mensajes = {
    BENEFICIO_INDETERMINADO: ['Acude a ventanilla', 'La Municipalidad debe verificar el beneficio aplicable antes de habilitar el pago virtual.', 'ventanilla'],
    SIN_COBRO_VIRTUAL: ['Tus beneficios requieren atención en ventanilla', 'El beneficio cubre toda la deuda. Acude a ventanilla para aplicarlo sin generar un cobro.', 'ventanilla'],
    SIN_DEUDA: ['No tienes saldo pendiente para pagar', 'Actualiza tu estado de cuenta para revisar la información más reciente.', 'cuenta'],
    SALDO_CAMBIO: ['El saldo o los beneficios cambiaron', 'Vuelve a tu cuenta y revisa una nueva orden con los importes actualizados.', 'cuenta'],
    PAGO_DEBE_CUBRIR_TODO: ['El pago debe cubrir el saldo completo', 'Actualiza la cuenta. El pago virtual incluye todas las obligaciones pendientes.', 'cuenta'],
    ORDEN_NO_ENCONTRADA: ['Esta orden ya no está disponible', 'Vuelve a consultar tu cuenta antes de iniciar un nuevo pago.', 'cuenta'],
    PAGO_NO_ENCONTRADO: ['No se encontró este pago en tu cuenta', 'Comprueba la referencia o vuelve a tu estado de cuenta.', 'cuenta'],
    PAGO_CERRADO: ['La orden ya está cerrada', 'Consulta el resultado de la operación antes de iniciar otro pago.', 'consultar'],
    PAGO_NO_AUTORIZADO: ['El pago no fue autorizado', 'Consulta el estado de esta operación antes de intentar confirmarla nuevamente.', 'consultar'],
    RESULTADO_DESCONOCIDO: ['Aún no pudimos comprobar el resultado', 'Consulta esta misma operación antes de iniciar otro pago.', 'consultar'],
    RESPUESTA_PAGO_INVALIDA: ['No se pudo comprobar la operación', 'Consulta el estado del pago para recuperar un resultado válido.', 'consultar'],
    PASARELA_NO_DISPONIBLE: ['Pasarela no disponible', 'Vuelve a tu cuenta para revisar la operación.', 'cuenta'],
    SAMNET_NO_DISPONIBLE: ['El servicio no está disponible', 'Intenta consultar nuevamente cuando se restablezca el servicio municipal.', 'consultar'],
  }
  const [titulo, descripcion, accion] = mensajes[error?.codigo] || [
    'No se pudo completar la operación', error?.message || 'Intenta nuevamente en unos momentos.', 'consultar',
  ]
  return { titulo, descripcion, accion }
}
