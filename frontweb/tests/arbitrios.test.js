import assert from 'node:assert/strict'
import test from 'node:test'
import { ROLES_CONSULTA_PREDIO, tieneRolInterno } from '../src/utils/roles.js'
import {
  FILTROS_ARBITRIOS_INICIALES,
  agruparCuotasPorTitular,
  estadoArbitrio,
  etiquetaCondicion,
  filtrarCuotas,
  opcionesDeArbitrios,
} from '../src/utils/arbitrios.js'

function datos() {
  return {
    predio: {
      codigoPredio: '058934-01',
      titulares: [
        {
          codigoContribuyente: '058934',
          nombre: 'MEDINA CRUZ, PATRICIA',
          condicionPropiedad: 'PROPIETARIO',
          porcentajePropiedad: 50,
        },
        {
          codigoContribuyente: '061177',
          nombre: 'MEDINA CRUZ, ROBERTO',
          condicionPropiedad: 'PROPIETARIO',
          porcentajePropiedad: 50,
        },
      ],
    },
    cuotas: [
      { idObligacion: 1, codigoContribuyente: '058934', anio: 2025, cuota: 1, periodo: '2025-T1', estado: 'PAGADA' },
      { idObligacion: 2, codigoContribuyente: '061177', anio: 2025, cuota: 1, periodo: '2025-T1', estado: 'VENCIDA' },
      { idObligacion: 3, codigoContribuyente: '058934', anio: 2025, cuota: 2, periodo: '2025-T2', estado: 'PAGADA' },
    ],
  }
}

test('HU005: agrupa las cuotas por titular y conserva el orden de la API', () => {
  const grupos = agruparCuotasPorTitular(datos())
  assert.equal(grupos.length, 2)
  assert.equal(grupos[0].codigoContribuyente, '058934')
  assert.equal(grupos[0].nombre, 'MEDINA CRUZ, PATRICIA')
  assert.equal(grupos[0].condicionPropiedad, 'PROPIETARIO')
  assert.equal(grupos[0].porcentajePropiedad, 50)
  assert.deepEqual(grupos[0].cuotas.map((cuota) => cuota.idObligacion), [1, 3])
  assert.equal(grupos[1].codigoContribuyente, '061177')
  assert.deepEqual(grupos[1].cuotas.map((cuota) => cuota.idObligacion), [2])
})

test('HU005: un titular sin datos en el predio se identifica por su código', () => {
  const [grupo] = agruparCuotasPorTitular({
    predio: { titulares: [] },
    cuotas: [{ idObligacion: 9, codigoContribuyente: '072405' }],
  })
  assert.equal(grupo.nombre, '072405')
  assert.equal(grupo.condicionPropiedad, null)
  assert.equal(grupo.porcentajePropiedad, null)
})

test('HU005: un predio sin cuotas produce una lista vacía', () => {
  assert.deepEqual(agruparCuotasPorTitular({ predio: { titulares: [] }, cuotas: [] }), [])
  assert.deepEqual(agruparCuotasPorTitular(), [])
})

test('HU005: traduce los estados de la API sin inventar otros', () => {
  assert.deepEqual(estadoArbitrio('PAGADA'), { etiqueta: 'Pagada', clase: 'ok' })
  assert.deepEqual(estadoArbitrio('PENDIENTE'), { etiqueta: 'Pendiente', clase: 'aviso' })
  assert.deepEqual(estadoArbitrio('VENCIDA'), { etiqueta: 'Vencida', clase: 'critico' })
  assert.deepEqual(estadoArbitrio('OTRO'), { etiqueta: 'OTRO', clase: 'neutro' })
  assert.deepEqual(estadoArbitrio(null), { etiqueta: 'Sin estado', clase: 'neutro' })
})

test('HU005: solo Analista de Rentas o Administrador consultan un predio', () => {
  assert.deepEqual(ROLES_CONSULTA_PREDIO, ['ANALISTA_RENTAS', 'ADMINISTRADOR'])
  assert.equal(tieneRolInterno({ roles: ['ADMINISTRADOR'] }), true)
  assert.equal(tieneRolInterno({ roles: ['ANALISTA_RENTAS'] }), true)
  assert.equal(tieneRolInterno({ roles: ['VENTANILLA'] }), false)
  assert.equal(tieneRolInterno({ roles: [] }), false)
  assert.equal(tieneRolInterno({}), false)
  assert.equal(tieneRolInterno(null), false)
})

test('HU005: la condición de propiedad se muestra con su nombre legible', () => {
  assert.equal(etiquetaCondicion('PROPIETARIO'), 'Propietario')
  assert.equal(etiquetaCondicion('COPROPIETARIO'), 'Copropietario')
  assert.equal(etiquetaCondicion('POSEEDOR'), 'POSEEDOR')
  assert.equal(etiquetaCondicion(null), '—')
})

test('HU005: las opciones de filtro salen de las cuotas y ordenan los años de reciente a antiguo', () => {
  const opciones = opcionesDeArbitrios([
    { anio: 2025, estado: 'PAGADA' },
    { anio: 2026, estado: 'VENCIDA' },
    { anio: 2025, estado: 'PENDIENTE' },
    { anio: 2026, estado: 'PAGADA' },
    { anio: 2024, estado: 'OTRO' },
    { anio: null, estado: null },
  ])

  assert.deepEqual(opciones.anios, [2026, 2025, 2024])
  assert.deepEqual(opciones.estados, [
    { valor: 'PAGADA', etiqueta: 'Pagada' },
    { valor: 'PENDIENTE', etiqueta: 'Pendiente' },
    { valor: 'VENCIDA', etiqueta: 'Vencida' },
    { valor: 'OTRO', etiqueta: 'OTRO' },
  ])
  assert.deepEqual(opcionesDeArbitrios([]), { anios: [], estados: [] })
})

test('HU005: filtra las cuotas por año y estado con lógica AND', () => {
  const cuotas = [
    { idObligacion: 1, anio: 2025, estado: 'PAGADA' },
    { idObligacion: 2, anio: 2025, estado: 'VENCIDA' },
    { idObligacion: 3, anio: 2026, estado: 'PAGADA' },
    { idObligacion: 4, anio: 2026, estado: 'PENDIENTE' },
  ]
  const ids = (lista) => lista.map((cuota) => cuota.idObligacion)

  assert.deepEqual(ids(filtrarCuotas(cuotas, FILTROS_ARBITRIOS_INICIALES)), [1, 2, 3, 4])
  assert.deepEqual(ids(filtrarCuotas(cuotas, { anio: '2025' })), [1, 2])
  assert.deepEqual(ids(filtrarCuotas(cuotas, { anio: 2026 })), [3, 4])
  assert.deepEqual(ids(filtrarCuotas(cuotas, { estado: 'PAGADA' })), [1, 3])
  assert.deepEqual(ids(filtrarCuotas(cuotas, { anio: '2025', estado: 'PAGADA' })), [1])
  assert.deepEqual(filtrarCuotas(cuotas, { anio: '2024' }), [])
  assert.deepEqual(filtrarCuotas(), [])
})
