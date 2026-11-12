import {
  DIAS_RECORDATORIO_MAXIMO,
  DIAS_RECORDATORIO_MINIMO,
  DIAS_RECORDATORIO_POR_DEFECTO,
  calcularFechaProgramada,
  validarDiasRecordatorio,
} from '../utils/recordatorios.js'

/*
 * HU016 (RF-022): recordatorio de vencimiento por correo.
 *
 * El backend de esta historia todavía no está implementado. Este servicio mantiene el mismo
 * contrato que tendrá la API y guarda la configuración y los avisos simulados en el navegador.
 * Cuando se publique el backend, basta con reemplazar los cuerpos de las funciones por:
 *
 *   GET  /api/configuracion/recordatorios
 *   PUT  /api/configuracion/recordatorios  { "diasAnticipacion": 5 }
 *   GET  /api/obligaciones/{id}/recordatorio-vencimiento
 *
 * y eliminar `RECORDATORIOS_SIMULADOS`.
 */
export const RECORDATORIOS_SIMULADOS = true

const CLAVE_ALMACEN = 'hu016.recordatorios.v1'
const HORA_PROGRAMADA = '08:00:00'
const ESTADOS_POR_SEMILLA = ['ENVIADO', 'ENVIADO', 'FALLIDO', 'SIN_CORREO', 'NO_ENVIADO']

let memoria = estadoInicial()

function estadoInicial() {
  return {
    configuracion: {
      diasAnticipacion: DIAS_RECORDATORIO_POR_DEFECTO,
      actualizadoEn: null,
      actualizadoPor: null,
    },
    registros: {},
  }
}

function almacenDisponible() {
  try {
    return typeof globalThis.localStorage !== 'undefined' ? globalThis.localStorage : null
  } catch {
    return null
  }
}

function normalizarEstado(datos) {
  const base = estadoInicial()
  if (!datos || typeof datos !== 'object') return base

  const dias = Number(datos.configuracion?.diasAnticipacion)
  if (Number.isInteger(dias) && dias >= DIAS_RECORDATORIO_MINIMO && dias <= DIAS_RECORDATORIO_MAXIMO) {
    base.configuracion.diasAnticipacion = dias
  }
  if (typeof datos.configuracion?.actualizadoEn === 'string') {
    base.configuracion.actualizadoEn = datos.configuracion.actualizadoEn
  }
  if (typeof datos.configuracion?.actualizadoPor === 'string') {
    base.configuracion.actualizadoPor = datos.configuracion.actualizadoPor
  }
  if (datos.registros && typeof datos.registros === 'object' && !Array.isArray(datos.registros)) {
    base.registros = datos.registros
  }
  return base
}

function leerEstado() {
  const almacen = almacenDisponible()
  if (!almacen) return memoria
  try {
    const crudo = almacen.getItem(CLAVE_ALMACEN)
    return crudo ? normalizarEstado(JSON.parse(crudo)) : estadoInicial()
  } catch {
    return estadoInicial()
  }
}

function guardarEstado(estado) {
  memoria = estado
  const almacen = almacenDisponible()
  if (!almacen) return
  try {
    almacen.setItem(CLAVE_ALMACEN, JSON.stringify(estado))
  } catch {
    // El fallback en memoria mantiene la sesión aunque el almacenamiento no esté disponible.
  }
}

function respuestaConfiguracion(configuracion) {
  return {
    diasAnticipacion: configuracion.diasAnticipacion,
    minimo: DIAS_RECORDATORIO_MINIMO,
    maximo: DIAS_RECORDATORIO_MAXIMO,
    valorPorDefecto: DIAS_RECORDATORIO_POR_DEFECTO,
    actualizadoEn: configuracion.actualizadoEn,
    actualizadoPor: configuracion.actualizadoPor,
  }
}

function errorRecordatorio(codigo, mensaje) {
  return Object.assign(new Error(mensaje), {
    codigo,
    estado: 400,
    detalles: [mensaje],
  })
}

function hoyIso() {
  const hoy = new Date()
  const mes = String(hoy.getMonth() + 1).padStart(2, '0')
  const dia = String(hoy.getDate()).padStart(2, '0')
  return `${hoy.getFullYear()}-${mes}-${dia}`
}

function semillaDe(id) {
  const texto = String(id)
  const numero = Number(texto)
  if (Number.isSafeInteger(numero) && numero >= 0) return numero
  let semilla = 0
  for (let indice = 0; indice < texto.length; indice += 1) {
    semilla = (semilla * 31 + texto.charCodeAt(indice)) % 1000003
  }
  return semilla
}

function correoDe(datos) {
  if (datos.correoDestino) return datos.correoDestino
  const codigo = String(datos.contribuyenteCodigo ?? 'contribuyente')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '')
  return `recordatorios+${codigo || 'contribuyente'}@correo.test`
}

function generarRegistro(id, datos, configuracion) {
  const diasAnticipacion = configuracion.diasAnticipacion
  const fechaProgramadaDia = calcularFechaProgramada(datos.fechaVencimiento, diasAnticipacion)
  const fechaProgramada = fechaProgramadaDia ? `${fechaProgramadaDia}T${HORA_PROGRAMADA}` : null
  const base = {
    idAviso: `SIM-${id}`,
    idObligacion: String(id),
    tipo: 'RECORDATORIO_VENCIMIENTO',
    estado: 'NO_ENVIADO',
    correoDestino: correoDe(datos),
    fechaProgramada,
    fechaEnvio: null,
    diasAnticipacion,
    origen: 'MOCK',
  }

  if (!fechaProgramadaDia) return base
  if (fechaProgramadaDia > hoyIso()) return { ...base, estado: 'PROGRAMADO' }

  const estado = ESTADOS_POR_SEMILLA[semillaDe(id) % ESTADOS_POR_SEMILLA.length]
  if (estado === 'ENVIADO') return { ...base, estado, fechaEnvio: fechaProgramada }
  if (estado === 'FALLIDO') return { ...base, estado, fechaEnvio: fechaProgramada }
  if (estado === 'SIN_CORREO') return { ...base, estado, correoDestino: null }
  return { ...base, estado }
}

export async function obtenerConfiguracionRecordatorio() {
  return respuestaConfiguracion(leerEstado().configuracion)
}

export async function guardarConfiguracionRecordatorio(diasAnticipacion, { usuario } = {}) {
  const validacion = validarDiasRecordatorio(diasAnticipacion)
  if (!validacion.valido) {
    throw errorRecordatorio('DIAS_RECORDATORIO_INVALIDO', validacion.mensaje)
  }

  const estado = leerEstado()
  estado.configuracion = {
    diasAnticipacion: validacion.valor,
    actualizadoEn: new Date().toISOString(),
    actualizadoPor: usuario || 'Gerencia de Rentas',
  }
  guardarEstado(estado)
  return respuestaConfiguracion(estado.configuracion)
}

export async function obtenerRecordatorioObligacion(obligacion) {
  const datos = obligacion && typeof obligacion === 'object' ? obligacion : { id: obligacion }
  const id = String(datos.id ?? '').trim()
  if (!id) {
    throw errorRecordatorio('OBLIGACION_INVALIDA', 'No se identificó la obligación para consultar su recordatorio.')
  }

  const estado = leerEstado()
  if (estado.registros[id]) return { ...estado.registros[id] }

  const registro = generarRegistro(id, datos, estado.configuracion)
  estado.registros[id] = registro
  guardarEstado(estado)
  return { ...registro }
}

/** Limpia la configuración y los avisos simulados de este navegador. */
export function reiniciarDatosSimulados() {
  memoria = estadoInicial()
  const almacen = almacenDisponible()
  if (!almacen) return
  try {
    almacen.removeItem(CLAVE_ALMACEN)
  } catch {
    // Sin almacenamiento disponible, el estado en memoria ya quedó restablecido.
  }
}
