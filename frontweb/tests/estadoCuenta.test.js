import assert from 'node:assert/strict'
import test from 'node:test'
import {
  anioInicial,
  aniosDeCuenta,
  arbitriosPorPredio,
  cuotasPredial,
  etiquetaCuota,
  filtrarGrupos,
  resumenDelAnio,
} from '../src/utils/estadoCuenta.js'

const servicios = (barrido, recoleccion) => [
  { servicio: 'BARRIDO_CALLES', monto: barrido },
  { servicio: 'RECOLECCION_RESIDUOS', monto: recoleccion },
]

// Cuenta como la devuelve /contribuyentes/{codigo}/estado-cuenta: predial por cuota y arbitrios por predio y mes.
const cuenta = {
  fechaCorte: '2026-10-19',
  predios: [
    { codigoPredio: '021390-01', ubicacion: 'Jr. Los Precursores 220', porcentajePropiedad: 100 },
    { codigoPredio: '021390-02', ubicacion: 'Jr. Los Precursores 224 (depósito)', porcentajePropiedad: 100 },
  ],
  resumenAnual: [
    { anio: 2025, total: {} },
    { anio: 2026, impuestoPredial: { emitido: 11250 }, arbitrios: { emitido: 4239 }, total: {} },
  ],
  filas: [
    { id: '1', tributoCodigo: 'IMPUESTO_PREDIAL', anio: 2026, cuota: 2, emitido: 2812.5, pagado: 0, deuda: 2812.5, interesMoratorio: 120, totalAPagar: 2932.5, estado: 'Vencida' },
    { id: '2', tributoCodigo: 'IMPUESTO_PREDIAL', anio: 2026, cuota: 1, emitido: 2812.5, pagado: 0, deuda: 2812.5, interesMoratorio: 200, totalAPagar: 3012.5, estado: 'Vencida' },
    { id: '3', tributoCodigo: 'ARBITRIOS', anio: 2026, cuota: 2, codigoPredio: '021390-02', emitido: 104.43, pagado: 104.43, deuda: 0, interesMoratorio: 0, totalAPagar: 0, estado: 'Pagada', servicios: servicios(15.55, 48.1) },
    { id: '4', tributoCodigo: 'ARBITRIOS', anio: 2026, cuota: 1, codigoPredio: '021390-01', emitido: 152.82, pagado: 0, deuda: 152.82, interesMoratorio: 10.5, totalAPagar: 163.32, estado: 'Vencida', servicios: servicios(16.69, 89.78) },
    { id: '5', tributoCodigo: 'ARBITRIOS', anio: 2026, cuota: 2, codigoPredio: '021390-01', emitido: 152.82, pagado: 0, deuda: 152.82, interesMoratorio: 10.5, totalAPagar: 163.32, estado: 'Vencida', servicios: servicios(16.69, 89.78) },
    { id: '6', tributoCodigo: 'ARBITRIOS', anio: 2025, cuota: 12, codigoPredio: '021390-01', emitido: 148.35, pagado: 0, deuda: 148.35, estado: 'Vencida', servicios: [] },
  ],
}

test('abre el año de la fecha de corte y lista los años del más reciente al más antiguo', () => {
  assert.deepEqual(aniosDeCuenta(cuenta), [2026, 2025])
  assert.equal(anioInicial(cuenta), 2026)
  assert.equal(anioInicial({ ...cuenta, fechaCorte: '2027-01-05' }), 2026)
  assert.equal(anioInicial({ filas: [] }), null)
  assert.equal(resumenDelAnio(cuenta, 2026).impuestoPredial.emitido, 11250)
})

test('separa el Impuesto Predial en sus cuotas y nombra los meses de los arbitrios', () => {
  assert.deepEqual(cuotasPredial(cuenta, 2026).map((fila) => fila.cuota), [1, 2])
  assert.equal(etiquetaCuota(cuotasPredial(cuenta, 2026)[0]), '1.ª cuota')
  assert.equal(etiquetaCuota({ tributoCodigo: 'ARBITRIOS', cuota: 9 }), 'Setiembre')
})

test('agrupa los arbitrios por predio con sus totales y lo que corresponde a cada servicio', () => {
  const grupos = arbitriosPorPredio(cuenta, 2026)
  assert.deepEqual(grupos.map((grupo) => grupo.codigoPredio), ['021390-01', '021390-02'])
  const principal = grupos[0]
  assert.deepEqual(principal.filas.map((fila) => fila.cuota), [1, 2])
  assert.equal(principal.predio.ubicacion, 'Jr. Los Precursores 220')
  assert.deepEqual(principal.totales, { emitido: 305.64, pagado: 0, deuda: 305.64, interesMoratorio: 21, totalAPagar: 326.64, vencidas: 2 })
  assert.deepEqual(principal.porServicio.slice(0, 2).map((s) => s.monto), [33.38, 179.56])
  assert.equal(principal.porServicio[3].monto, 0)
})

test('en listas largas filtra los predios por código, dirección o deuda', () => {
  const grupos = arbitriosPorPredio(cuenta, 2026)
  assert.deepEqual(filtrarGrupos(grupos, 'depósito', false).map((g) => g.codigoPredio), ['021390-02'])
  assert.deepEqual(filtrarGrupos(grupos, '', true).map((g) => g.codigoPredio), ['021390-01'])
})
