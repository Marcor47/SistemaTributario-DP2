// Datos ficticios de demostración. En producción el estado de cuenta viene del
// servicio de cuenta corriente de SAMNET a través de la capa de APIs municipal.

export const TRIBUTOS = ['Impuesto Predial', 'Limpieza Pública', 'Parques y Jardines', 'Seguridad Ciudadana']
export const PERIODOS = ['2025-T1', '2025-T2', '2025-T3', '2025-T4', '2026-T1', '2026-T2']

// Fin de cada periodo (vencimiento) para distinguir «Vencida» de «Pendiente».
const VENCIMIENTO = {
  '2025-T1': '2025-03-31', '2025-T2': '2025-06-30', '2025-T3': '2025-09-30',
  '2025-T4': '2025-12-31', '2026-T1': '2026-03-31', '2026-T2': '2026-06-30',
}

export const FECHA_CORTE = '2026-10-19'
export const FECHA_CORTE_ANTERIOR = '2026-10-16' // última información disponible si el servicio no responde

// Importe trimestral base por tributo y cuántos periodos recientes están impagos.
const PERFIL = {
  '014502': { factor: 1, impagos: 0, atrasos: [-5, 0, -2, 0, -4, 0] },
  '021390': { factor: 5.2, impagos: 3, atrasos: [0, 12, 8, 0, 0, 0] },
  '033218': { factor: 0.55, impagos: 0, atrasos: [0, 5, 0, 10, 0, 3] },
  '045067': { factor: 2.4, impagos: 1, atrasos: [4, 0, 0, 7, 8, 0] },
  '058934': { factor: 0.8, impagos: 2, atrasos: [0, 0, 6, 0, 0, 0] },
  '061177': { factor: 0.8, impagos: 2, atrasos: [2, 0, 0, 0, 0, 0] },
  // Lucía: 50 % de cuotas pagadas a tiempo en 2026 y 75 % en 2025.
  '072845': { factor: 1.15, impagos: 0, atrasos: [0, 0, 7, 0, 0, 9] },
}
const BASE = {
  'Impuesto Predial': 180,
  'Limpieza Pública': 95,
  'Parques y Jardines': 60,
  'Seguridad Ciudadana': 75,
}

export function generarCuenta(codigo, fechaCorte) {
  const { factor, impagos, atrasos } = PERFIL[codigo]
  const filas = []
  TRIBUTOS.forEach((tributo, t) => {
    PERIODOS.forEach((periodo, i) => {
      const emitido = Math.round(BASE[tributo] * factor * (1 + 0.02 * i) * 100) / 100
      const impago = i >= PERIODOS.length - impagos
      // El último periodo aún no vence al corte: queda pendiente aunque no se haya pagado.
      const vencido = VENCIMIENTO[periodo] < fechaCorte
      const pagado = impago ? 0 : emitido
      // Fechas adicionales para HU010; los montos y estados de HU004 se conservan.
      const fechaVencimiento = VENCIMIENTO[periodo]
      const pago = new Date(`${fechaVencimiento}T00:00:00Z`)
      pago.setUTCDate(pago.getUTCDate() + atrasos[i])
      filas.push({
        id: `${t}-${periodo}`,
        tributo,
        periodo,
        emitido,
        pagado,
        fechaVencimiento,
        fechaPago: impago ? null : pago.toISOString().slice(0, 10),
        deuda: Math.round((emitido - pagado) * 100) / 100,
        estado: !impago ? 'Pagada' : vencido ? 'Vencida' : 'Pendiente',
      })
    })
  })
  return filas
}
