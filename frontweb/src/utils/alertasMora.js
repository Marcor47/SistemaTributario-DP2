// HU012 (RF-013): utilidades de presentación de las alertas de mora temprana. Los valores los calcula el backend.

export const PERMISOS_PROCESAR_ALERTAS = ['ALERTAS:EVALUAR', 'ETL:GESTIONAR']

export const ESTADOS_AVISO = {
  ENVIADO: { texto: 'Enviado', clase: 'ok' },
  SIMULADO: { texto: 'Simulado', clase: 'aviso' },
  PENDIENTE: { texto: 'Pendiente', clase: 'aviso' },
  FALLIDO: { texto: 'Fallido', clase: 'critico' },
}

export const TIPOS_AVISO = {
  MORA_TEMPRANA: 'Mora temprana',
  RECORDATORIO_VENCIMIENTO: 'Recordatorio de vencimiento',
}

/** Un estado que el front aún no conoce se muestra tal cual, sin romper la tabla. */
export function estadoAviso(estado) {
  return ESTADOS_AVISO[estado] ?? { texto: estado || 'Sin estado', clase: 'neutro' }
}

export function tipoAviso(tipo) {
  return TIPOS_AVISO[tipo] ?? (tipo || '—')
}

/** Procesar un lote envía correos: solo quien tiene permiso de evaluar alertas o gestionar el ETL ve esa acción. */
export function puedeProcesarAlertas(sesion) {
  const permisos = sesion?.permisos
  return Array.isArray(permisos) && PERMISOS_PROCESAR_ALERTAS.some((permiso) => permisos.includes(permiso))
}

/** El id del lote es un entero positivo; se rechaza el texto vacío, los decimales y los valores no seguros. */
export function validarIdLote(texto) {
  const valor = String(texto ?? '').trim()
  if (!valor) return { valido: false, mensaje: 'Ingresa el número del lote.' }
  if (!/^\d+$/.test(valor)) return { valido: false, mensaje: 'El número de lote debe ser un entero positivo.' }
  const numero = Number(valor)
  if (!Number.isSafeInteger(numero) || numero < 1) {
    return { valido: false, mensaje: 'El número de lote debe ser un entero positivo.' }
  }
  return { valido: true, valor: numero }
}

/** Código (hasta 10) o número de documento (hasta 12) del contribuyente. */
export function validarContribuyente(texto) {
  const valor = String(texto ?? '').trim()
  if (!valor) return { valido: false, mensaje: 'Ingresa el código o el documento del contribuyente.' }
  if (valor.length > 20 || /[\s/?#%\\]/.test(valor)) {
    return { valido: false, mensaje: 'Ingresa solo el código o el número de documento, sin espacios ni símbolos.' }
  }
  return { valido: true, valor }
}

/** Los más recientes primero; los avisos aún sin fecha de envío (pendientes) quedan al final. */
export function ordenarAvisos(avisos) {
  return [...(avisos || [])].sort((a, b) => {
    const porFecha = String(b.fechaEnvio ?? '').localeCompare(String(a.fechaEnvio ?? ''))
    return porFecha !== 0 ? porFecha : Number(b.idAviso) - Number(a.idAviso)
  })
}

/** Cifras del resultado del procesamiento, en el orden en que se leen; `tono` resalta lo que requiere atención. */
export function cifrasProcesamiento(resultado) {
  const n = (valor) => Number(valor) || 0
  return [
    { clave: 'cuotasEvaluadas', etiqueta: 'Cuotas evaluadas', valor: n(resultado?.cuotasEvaluadas), tono: null },
    { clave: 'cuotasVencidasDetectadas', etiqueta: 'Cuotas vencidas sin pago', valor: n(resultado?.cuotasVencidasDetectadas), tono: null },
    { clave: 'alertasEnviadas', etiqueta: 'Avisos enviados', valor: n(resultado?.alertasEnviadas), tono: 'ok' },
    { clave: 'alertasSimuladas', etiqueta: 'Avisos simulados', valor: n(resultado?.alertasSimuladas), tono: 'aviso' },
    { clave: 'sinCorreoRegistrado', etiqueta: 'Sin correo registrado', valor: n(resultado?.sinCorreoRegistrado), tono: n(resultado?.sinCorreoRegistrado) > 0 ? 'aviso' : null },
    { clave: 'alertasFallidas', etiqueta: 'Envíos fallidos', valor: n(resultado?.alertasFallidas), tono: n(resultado?.alertasFallidas) > 0 ? 'critico' : null },
  ]
}
