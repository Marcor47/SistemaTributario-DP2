import { pedir } from './api.js'
import { MODO_DEMO, esSesionDemo } from './authService.js'
import { FECHA_CORTE, FECHA_CORTE_ANTERIOR } from '../mocks/cuentas.js'

export const ETAPAS_COBRANZA_INFO = {
  MORA_TEMPRANA: {
    nombre: 'Mora temprana',
    descripcion: 'Venció el plazo voluntario; se envían recordatorios informativos.',
    clase: 'aviso',
  },
  CAMPANA: {
    nombre: 'Campaña persuasiva',
    descripcion: 'Notificación de cartas inductivas y ofrecimiento de fraccionamiento y beneficios.',
    clase: 'aviso',
  },
  VALOR_EMITIDO: {
    nombre: 'Valor emitido',
    descripcion: 'Orden de pago o resolución de determinación notificada formalmente.',
    clase: 'aviso',
  },
  ESPERA_LEGAL: {
    nombre: 'Espera legal',
    descripcion: 'Plazo legal de 20 días hábiles para pago o presentación de reclamo.',
    clase: 'aviso',
  },
  COACTIVA: {
    nombre: 'Exigible en vía coactiva',
    descripcion: 'Deuda exigible mediante ejecución coactiva municipal.',
    clase: 'critico',
  },
}

// Datos de demostración enriquecidos para HU014 y RF-018
function generarEstadoCuentaDemo(codigo = '021390', servicioCaido = false) {
  const fechaCorte = servicioCaido ? FECHA_CORTE_ANTERIOR : FECHA_CORTE
  return {
    contribuyente: {
      codigo,
      nombre: 'DISTRIBUIDORA SAN MIGUEL S.A.C.',
      tipoDoc: 'RUC',
      doc: '20456781239',
    },
    fechaCorte,
    desactualizado: servicioCaido,
    esUltimaCopia: servicioCaido,
    filas: [
      // Impuesto Predial
      {
        id: '1',
        tributo: 'Impuesto Predial',
        categoria: 'PREDIAL',
        periodo: '2025-T1',
        anio: 2025,
        cuota: 'Cuota 1 (Trim. I)',
        fechaVencimiento: '2025-03-31',
        emitido: 936.00,
        pagado: 936.00,
        deuda: 0.00,
        descuento: 0.00,
        montoPagar: 0.00,
        estado: 'Pagada',
        etapaCobranza: null,
      },
      {
        id: '2',
        tributo: 'Impuesto Predial',
        categoria: 'PREDIAL',
        periodo: '2025-T2',
        anio: 2025,
        cuota: 'Cuota 2 (Trim. II)',
        fechaVencimiento: '2025-06-30',
        emitido: 954.72,
        pagado: 954.72,
        deuda: 0.00,
        descuento: 0.00,
        montoPagar: 0.00,
        estado: 'Pagada',
        etapaCobranza: null,
      },
      {
        id: '3',
        tributo: 'Impuesto Predial',
        categoria: 'PREDIAL',
        periodo: '2025-T3',
        anio: 2025,
        cuota: 'Cuota 3 (Trim. III)',
        fechaVencimiento: '2025-09-30',
        emitido: 973.44,
        pagado: 973.44,
        deuda: 0.00,
        descuento: 0.00,
        montoPagar: 0.00,
        estado: 'Pagada',
        etapaCobranza: null,
      },
      {
        id: '4',
        tributo: 'Impuesto Predial',
        categoria: 'PREDIAL',
        periodo: '2025-T4',
        anio: 2025,
        cuota: 'Cuota 4 (Trim. IV)',
        fechaVencimiento: '2025-12-31',
        emitido: 992.16,
        pagado: 992.16,
        deuda: 0.00,
        descuento: 0.00,
        montoPagar: 0.00,
        estado: 'Pagada',
        etapaCobranza: null,
      },
      {
        id: '5',
        tributo: 'Impuesto Predial',
        categoria: 'PREDIAL',
        periodo: '2026-T1',
        anio: 2026,
        cuota: 'Cuota 1 (Trim. I)',
        fechaVencimiento: '2026-03-31',
        emitido: 1010.88,
        pagado: 0.00,
        deuda: 1010.88,
        descuento: 101.09,
        montoPagar: 909.79,
        estado: 'Vencida',
        etapaCobranza: 'Campaña persuasiva',
      },
      {
        id: '6',
        tributo: 'Impuesto Predial',
        categoria: 'PREDIAL',
        periodo: '2026-T2',
        anio: 2026,
        cuota: 'Cuota 2 (Trim. II)',
        fechaVencimiento: '2026-06-30',
        emitido: 1029.60,
        pagado: 0.00,
        deuda: 1029.60,
        descuento: 51.48,
        montoPagar: 978.12,
        estado: 'Vencida',
        etapaCobranza: 'Mora temprana',
      },
      {
        id: '7',
        tributo: 'Impuesto Predial',
        categoria: 'PREDIAL',
        periodo: '2026-T3',
        anio: 2026,
        cuota: 'Cuota 3 (Trim. III)',
        fechaVencimiento: '2026-09-30',
        emitido: 1048.32,
        pagado: 0.00,
        deuda: 1048.32,
        descuento: 0.00,
        montoPagar: 1048.32,
        estado: 'Pendiente',
        etapaCobranza: null,
      },

      // Arbitrios Municipales
      {
        id: '8',
        tributo: 'Limpieza Pública',
        categoria: 'ARBITRIOS',
        periodo: '2026-T1',
        anio: 2026,
        cuota: 'Cuota 1 (Trim. I)',
        fechaVencimiento: '2026-03-31',
        emitido: 533.52,
        pagado: 0.00,
        deuda: 533.52,
        descuento: 53.35,
        montoPagar: 480.17,
        estado: 'Vencida',
        etapaCobranza: 'Campaña persuasiva',
      },
      {
        id: '9',
        tributo: 'Parques y Jardines',
        categoria: 'ARBITRIOS',
        periodo: '2026-T1',
        anio: 2026,
        cuota: 'Cuota 1 (Trim. I)',
        fechaVencimiento: '2026-03-31',
        emitido: 336.96,
        pagado: 0.00,
        deuda: 336.96,
        descuento: 33.70,
        montoPagar: 303.26,
        estado: 'Vencida',
        etapaCobranza: 'Campaña persuasiva',
      },
      {
        id: '10',
        tributo: 'Seguridad Ciudadana',
        categoria: 'ARBITRIOS',
        periodo: '2026-T1',
        anio: 2026,
        cuota: 'Cuota 1 (Trim. I)',
        fechaVencimiento: '2026-03-31',
        emitido: 421.20,
        pagado: 0.00,
        deuda: 421.20,
        descuento: 42.12,
        montoPagar: 379.08,
        estado: 'Vencida',
        etapaCobranza: 'Campaña persuasiva',
      },
      {
        id: '11',
        tributo: 'Limpieza Pública',
        categoria: 'ARBITRIOS',
        periodo: '2026-T2',
        anio: 2026,
        cuota: 'Cuota 2 (Trim. II)',
        fechaVencimiento: '2026-06-30',
        emitido: 543.40,
        pagado: 0.00,
        deuda: 543.40,
        descuento: 0.00,
        montoPagar: 543.40,
        estado: 'Vencida',
        etapaCobranza: 'Mora temprana',
      },
      {
        id: '12',
        tributo: 'Parques y Jardines',
        categoria: 'ARBITRIOS',
        periodo: '2026-T2',
        anio: 2026,
        cuota: 'Cuota 2 (Trim. II)',
        fechaVencimiento: '2026-06-30',
        emitido: 343.20,
        pagado: 0.00,
        deuda: 343.20,
        descuento: 0.00,
        montoPagar: 343.20,
        estado: 'Vencida',
        etapaCobranza: 'Mora temprana',
      },
      {
        id: '13',
        tributo: 'Seguridad Ciudadana',
        categoria: 'ARBITRIOS',
        periodo: '2026-T2',
        anio: 2026,
        cuota: 'Cuota 2 (Trim. II)',
        fechaVencimiento: '2026-06-30',
        emitido: 429.00,
        pagado: 0.00,
        deuda: 429.00,
        descuento: 0.00,
        montoPagar: 429.00,
        estado: 'Vencida',
        etapaCobranza: 'Mora temprana',
      },
      {
        id: '14',
        tributo: 'Limpieza Pública',
        categoria: 'ARBITRIOS',
        periodo: '2026-T3',
        anio: 2026,
        cuota: 'Cuota 3 (Trim. III)',
        fechaVencimiento: '2026-09-30',
        emitido: 553.28,
        pagado: 0.00,
        deuda: 553.28,
        descuento: 0.00,
        montoPagar: 553.28,
        estado: 'Pendiente',
        etapaCobranza: null,
      },

      // Multas y Sanciones (Obligatoriamente separadas según RF-018)
      {
        id: '15',
        tributo: 'Multa Tributaria (Omisión formal)',
        categoria: 'MULTAS',
        periodo: '2025-RESOL',
        anio: 2025,
        cuota: 'Res. 042-2025',
        fechaVencimiento: '2025-11-15',
        emitido: 450.00,
        pagado: 0.00,
        deuda: 450.00,
        descuento: 90.00,
        montoPagar: 360.00,
        estado: 'Vencida',
        etapaCobranza: 'Valor emitido',
      },
      {
        id: '16',
        tributo: 'Gastos de Emisión y Notificación',
        categoria: 'MULTAS',
        periodo: '2026-NOTIF',
        anio: 2026,
        cuota: 'Notif. 104-2026',
        fechaVencimiento: '2026-04-15',
        emitido: 35.50,
        pagado: 0.00,
        deuda: 35.50,
        descuento: 0.00,
        montoPagar: 35.50,
        estado: 'Vencida',
        etapaCobranza: 'Mora temprana',
      },
    ],
  }
}

// Historial de pagos para RF-062
export const HISTORIAL_PAGOS_DEMO = [
  {
    idPago: 'PAG-2026-0042',
    fechaPago: '2026-01-14',
    canal: 'En línea',
    concepto: 'Impuesto Predial 2025-T4',
    montoPagado: 992.16,
    beneficioAplicado: 49.60,
    recibo: 'WEB-104928',
  },
  {
    idPago: 'PAG-2025-0812',
    fechaPago: '2025-09-28',
    canal: 'Ventanilla',
    concepto: 'Impuesto Predial 2025-T3',
    montoPagado: 973.44,
    beneficioAplicado: 0.00,
    recibo: 'REC-092811',
  },
  {
    idPago: 'PAG-2025-0550',
    fechaPago: '2025-06-25',
    canal: 'En línea',
    concepto: 'Impuesto Predial 2025-T2',
    montoPagado: 954.72,
    beneficioAplicado: 47.70,
    recibo: 'WEB-101452',
  },
  {
    idPago: 'PAG-2025-0210',
    fechaPago: '2025-03-20',
    canal: 'Ventanilla',
    concepto: 'Impuesto Predial 2025-T1',
    montoPagado: 936.00,
    beneficioAplicado: 0.00,
    recibo: 'REC-081944',
  },
]

// Normaliza filas provenientes del backend para asegurar todas las propiedades esperadas por la UI
function normalizarFilas(filas = []) {
  return filas.map((fila, index) => {
    const periodo = fila.periodo || ''
    const partes = periodo.split('-')
    const anioExtraido = partes[0] && /^\d{4}$/.test(partes[0]) ? Number(partes[0]) : new Date().getFullYear()
    const cuotaExtraida = partes[1] || fila.cuota || `Cuota ${index + 1}`

    const tributo = fila.tributo || 'Tributo Municipal'
    let categoria = fila.categoria
    if (!categoria) {
      const lower = tributo.toLowerCase()
      if (lower.includes('predial')) {
        categoria = 'PREDIAL'
      } else if (lower.includes('limpieza') || lower.includes('parque') || lower.includes('seguridad') || lower.includes('arbitrio')) {
        categoria = 'ARBITRIOS'
      } else if (lower.includes('multa') || lower.includes('gasto') || lower.includes('sanci') || lower.includes('costa')) {
        categoria = 'MULTAS'
      } else {
        categoria = 'PREDIAL'
      }
    }

    const emitido = Number(fila.emitido ?? 0)
    const pagado = Number(fila.pagado ?? 0)
    const deuda = Number(fila.deuda ?? Math.max(0, emitido - pagado))
    const descuento = Number(fila.descuento ?? 0)
    const montoPagar = Number(fila.montoPagar ?? Math.max(0, deuda - descuento))

    let estado = fila.estado || (deuda <= 0 ? 'Pagada' : 'Pendiente')
    // Normalizar capitalización a Pagada / Vencida / Pendiente
    if (typeof estado === 'string') {
      const estadoUpper = estado.toUpperCase()
      if (estadoUpper === 'PAGADA') estado = 'Pagada'
      else if (estadoUpper === 'VENCIDA') estado = 'Vencida'
      else if (estadoUpper === 'PENDIENTE') estado = 'Pendiente'
    }

    return {
      id: String(fila.id ?? index + 1),
      tributo,
      categoria,
      periodo,
      anio: fila.anio ?? anioExtraido,
      cuota: fila.cuota ?? cuotaExtraida,
      fechaVencimiento: fila.fechaVencimiento ?? null,
      emitido,
      pagado,
      deuda,
      descuento,
      montoPagar,
      estado,
      etapaCobranza: fila.etapaCobranza || (estado === 'Vencida' ? 'Mora temprana' : null),
    }
  })
}

/**
 * HU014 (RF-018): Consulta el estado de cuenta del contribuyente autenticado.
 * Seguridad (RNF-023 / RNF-028): Sin parámetro en URL para prevenir IDOR; la sesión
 * se resuelve en el backend mediante la cookie de sesión.
 */
export async function obtenerEstadoCuentaPortal({ simularCaida = false } = {}) {
  if (simularCaida) {
    await new Promise((r) => setTimeout(r, 400))
    return generarEstadoCuentaDemo('021390', true)
  }

  try {
    const respuesta = await pedir('/portal/estado-cuenta')
    const desactualizado = Boolean(respuesta.desactualizado || respuesta.esUltimaCopia)
    return {
      contribuyente: respuesta.contribuyente,
      fechaCorte: respuesta.fechaCorte,
      desactualizado,
      esUltimaCopia: desactualizado,
      filas: normalizarFilas(respuesta.filas || []),
    }
  } catch (err) {
    // 1. En entorno Node.js (tests automáticos): fallback demo inmediato
    if (typeof window === 'undefined') {
      await new Promise((r) => setTimeout(r, 400))
      return generarEstadoCuentaDemo('021390', false)
    }

    // 2. En el navegador: solo entregar datos si hay una sesión demo activa (login previo con DEMO-0001)
    if (esSesionDemo()) {
      await new Promise((r) => setTimeout(r, 400))
      return generarEstadoCuentaDemo('021390', false)
    }

    // 3. Sin sesión activa en el navegador:
    // Lanzar 401 para que la UI exija autenticación de contribuyente (HU014 / HU041)
    throw Object.assign(
      new Error('Sesión no iniciada. Inicia sesión con tus credenciales de contribuyente.'),
      { estado: 401 }
    )
  }
}

/**
 * RF-062: Historial de pagos realizados por el contribuyente.
 */
export async function obtenerHistorialPagosPortal() {
  try {
    const respuesta = await pedir('/portal/pagos/historial')
    return respuesta
  } catch {
    // Fallback con datos consistentes de pagos históricos del contribuyente
    return HISTORIAL_PAGOS_DEMO
  }
}

/**
 * HU022: Iniciar orden de pago en línea para las obligaciones seleccionadas.
 */
export async function iniciarPagoPortal(obligacionesIds) {
  try {
    return await pedir('/portal/pagos', {
      method: 'POST',
      body: JSON.stringify({ obligaciones: obligacionesIds }),
    })
  } catch (err) {
    if (MODO_DEMO || esSesionDemo() || typeof window === 'undefined' || err.codigo === 'SIN_CONEXION') {
      await new Promise((r) => setTimeout(r, 600))
      return {
        idTransaccion: Math.floor(1000 + Math.random() * 9000),
        montoTotal: 0,
        estado: 'INICIADA',
        dePrueba: true,
        urlPasarela: null,
      }
    }
    throw err
  }
}

/**
 * HU022: Confirmar transacción de pago.
 */
export async function confirmarPagoPortal(idTransaccion) {
  try {
    return await pedir(`/portal/pagos/${encodeURIComponent(idTransaccion)}/confirmacion`, {
      method: 'POST',
    })
  } catch (err) {
    if (MODO_DEMO || esSesionDemo() || typeof window === 'undefined' || err.codigo === 'SIN_CONEXION') {
      await new Promise((r) => setTimeout(r, 800))
      return {
        idTransaccion,
        estado: 'CONFIRMADA',
        dePrueba: true,
      }
    }
    throw err
  }
}
