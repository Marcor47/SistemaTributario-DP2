import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import {
  MAX_LONGITUD_VALOR_ALCANCE,
  REPORTES_CONFIGURABLES,
  ROLES_ACCESO_REPORTES,
  ROLES_CON_ACCESO_REPORTES_POR_DEFECTO,
  VERSION_ACCESO_REPORTES,
  actualizarReporte,
  agregarValorAlcance,
  aplicarConfiguracionATodosLosRoles,
  configuracionDeRol,
  configuracionPorDefecto,
  copiarConfiguracionRol,
  filtrarOpcionesAlcance,
  marcarTodosLosReportes,
  normalizarConfiguracion,
  opcionesSegmentosPublicados,
  quitarValorAlcance,
  reporteConfigurable,
  resumenDeRol,
} from '../src/utils/accesoReportes.js'
import {
  ACCESOS_REPORTES_SIMULADOS,
  guardarConfiguracionAccesoReportes,
  obtenerConfiguracionAccesoReportes,
  reiniciarConfiguracionAccesoReportes,
} from '../src/services/accesoReportesService.js'
import { permisosDeRol, rolTienePermiso } from '../src/utils/roles.js'

function columnasConFijas(idReporte, opcionales = []) {
  const reporte = reporteConfigurable(idReporte)
  const seleccionadas = new Set(opcionales)
  return reporte.columnas.filter((columna) => (
    reporte.columnasFijas.includes(columna) || seleccionadas.has(columna)
  ))
}

test('HU045: la configuración inicial habilita reportes solo para los roles con acceso por defecto', () => {
  const configuracion = configuracionPorDefecto()
  assert.equal(configuracion.version, VERSION_ACCESO_REPORTES)
  assert.deepEqual(Object.keys(configuracion.roles), ROLES_ACCESO_REPORTES)
  for (const rol of ROLES_ACCESO_REPORTES) {
    const conAcceso = ROLES_CON_ACCESO_REPORTES_POR_DEFECTO.includes(rol)
    const resumen = resumenDeRol(configuracion, rol)
    assert.equal(resumen.permitidos, conAcceso ? REPORTES_CONFIGURABLES.length : 0)
    assert.equal(resumen.total, REPORTES_CONFIGURABLES.length)
    assert.equal(resumen.conAlcance, 0)
    assert.equal(resumen.columnasLimitadas, 0)
    for (const reporte of REPORTES_CONFIGURABLES) {
      const valor = configuracion.roles[rol].reportes[reporte.id]
      assert.equal(valor.permitido, conAcceso)
      assert.deepEqual(valor.zonas, [])
      assert.deepEqual(valor.segmentos, [])
      assert.deepEqual(valor.columnas, reporte.columnas)
    }
  }
  assert.equal(configuracionDeRol(configuracion, 'ROL_INEXISTENTE'), null)
})

test('HU045: los valores por defecto se pueden sembrar con los roles que tienen acceso real', () => {
  const configuracion = configuracionPorDefecto(['GERENCIA'])
  assert.equal(configuracion.roles.GERENCIA.reportes.MOROSOS.permitido, true)
  assert.equal(configuracion.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, false)
  assert.equal(configuracion.roles.ADMINISTRADOR.reportes.MOROSOS.permitido, false)
  assert.deepEqual(
    configuracion.roles.ANALISTA_RENTAS.reportes.MOROSOS.columnas,
    reporteConfigurable('MOROSOS').columnas,
  )
})

test('HU045: el mock anterior migra los roles sin acceso a reportes desactivados', () => {
  const anterior = configuracionPorDefecto()
  anterior.version = 1
  for (const rol of ROLES_ACCESO_REPORTES) {
    for (const reporte of REPORTES_CONFIGURABLES) {
      anterior.roles[rol].reportes[reporte.id].permitido = true
    }
  }

  const migrada = normalizarConfiguracion(anterior, ['GERENCIA'])
  assert.equal(migrada.version, VERSION_ACCESO_REPORTES)
  assert.equal(migrada.roles.GERENCIA.reportes.MOROSOS.permitido, true)
  assert.equal(migrada.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, false)
  assert.equal(migrada.roles.VENTANILLA.reportes.MOROSOS.permitido, false)
  assert.equal(migrada.roles.ADMINISTRADOR.reportes.MOROSOS.permitido, false)
})

test('HU045: el catálogo cubre ambos módulos y todas las columnas tienen etiqueta', () => {
  const modulos = new Set(REPORTES_CONFIGURABLES.map((reporte) => reporte.modulo))
  assert.deepEqual([...modulos].sort(), ['REPORTERIA', 'REPORTES'])
  assert.equal(REPORTES_CONFIGURABLES.filter((reporte) => reporte.modulo === 'REPORTERIA').length, 4)
  assert.equal(REPORTES_CONFIGURABLES.filter((reporte) => reporte.modulo === 'REPORTES').length, 3)

  for (const reporte of REPORTES_CONFIGURABLES) {
    assert.ok(reporte.grupos.length > 0, `${reporte.id} debe tener grupos de columnas`)
    assert.equal(new Set(reporte.grupos.map((grupo) => grupo.clave)).size, reporte.grupos.length)
    assert.equal(new Set(reporte.columnas).size, reporte.columnas.length)
    const columnasDeGrupos = reporte.grupos.flatMap((grupo) => grupo.columnas)
    assert.deepEqual(new Set(columnasDeGrupos), new Set(reporte.columnas))
    for (const columna of reporte.columnas) {
      assert.ok(reporte.etiquetas[columna], `${reporte.id}: falta la etiqueta de ${columna}`)
    }

    assert.ok(reporte.columnasFijas.length > 0, `${reporte.id} debe declarar columnas fijas`)
    assert.ok(reporte.columnasFijas.every((columna) => reporte.columnas.includes(columna)))
    assert.ok(reporte.columnasConfigurables.every((columna) => !reporte.columnasFijas.includes(columna)))
    assert.deepEqual(
      new Set(reporte.columnasConfigurables),
      new Set(reporte.columnas.filter((columna) => !reporte.columnasFijas.includes(columna))),
    )
    assert.deepEqual(
      new Set(reporte.gruposConfigurables.flatMap((grupo) => grupo.columnas)),
      new Set(reporte.columnasConfigurables),
    )
    assert.ok(reporte.gruposConfigurables.every((grupo) => grupo.columnas.length > 0))
  }

  const morosos = reporteConfigurable('MOROSOS')
  assert.ok(morosos.columnas.includes('codigoPredio'))
  assert.ok(morosos.columnas.includes('montoConBeneficio'))
})

test('HU045: la normalización descarta roles, reportes y columnas desconocidas', () => {
  const normalizada = normalizarConfiguracion({
    actualizadoEn: '2026-11-12T10:00:00.000Z',
    actualizadoPor: 'Administrador',
    roles: {
      ANALISTA_RENTAS: {
        reportes: {
          MOROSOS: {
            permitido: false,
            zonas: [' Z1 ', 'z1', '', 2],
            segmentos: ['Al día'],
            columnas: ['codigo', 'columna-inventada', 'codigo'],
          },
          REPORTE_INEXISTENTE: { permitido: false },
        },
      },
      ROL_INVENTADO: { reportes: {} },
    },
  })

  assert.equal(normalizada.actualizadoPor, 'Administrador')
  assert.equal(normalizada.roles.ROL_INVENTADO, undefined)
  const morosos = normalizada.roles.ANALISTA_RENTAS.reportes.MOROSOS
  assert.equal(morosos.permitido, false)
  assert.deepEqual(morosos.zonas, ['Z1', '2'])
  assert.deepEqual(morosos.segmentos, ['Al día'])
  assert.deepEqual(morosos.columnas, columnasConFijas('MOROSOS'))
  assert.equal(normalizada.roles.ANALISTA_RENTAS.reportes.REPORTE_INEXISTENTE, undefined)
  assert.equal(normalizada.roles.GERENCIA.reportes.MOROSOS.permitido, true)
})

test('HU045: un reporte siempre conserva sus columnas fijas', () => {
  const normalizada = normalizarConfiguracion({
    roles: { ANALISTA_RENTAS: { reportes: { MOROSOS: { columnas: [] } } } },
  })
  assert.deepEqual(
    normalizada.roles.ANALISTA_RENTAS.reportes.MOROSOS.columnas,
    columnasConFijas('MOROSOS'),
  )
  const reporte = reporteConfigurable('MOROSOS')
  assert.ok(reporte.columnasFijas.includes('codigo'))
  assert.ok(reporte.columnasFijas.includes('deudaVencida'))
  assert.ok(!reporte.columnasConfigurables.includes('codigo'))
})

test('HU045: actualizar un reporte limita zonas, segmentos y columnas del catálogo', () => {
  const configuracion = actualizarReporte(configuracionPorDefecto(), 'ANALISTA_RENTAS', 'MOROSOS', {
    permitido: false,
    zonas: ['Z1', 'Z1', 'Z2'],
    segmentos: ['Al día'],
    columnas: ['codigo', 'deudaVencida', 'deudaSinBeneficio', 'no-existe'],
  })

  const morosos = configuracion.roles.ANALISTA_RENTAS.reportes.MOROSOS
  assert.equal(morosos.permitido, false)
  assert.deepEqual(morosos.zonas, ['Z1', 'Z2'])
  assert.deepEqual(morosos.segmentos, ['Al día'])
  assert.deepEqual(morosos.columnas, columnasConFijas('MOROSOS', ['deudaSinBeneficio']))

  const resumen = resumenDeRol(configuracion, 'ANALISTA_RENTAS')
  assert.equal(resumen.permitidos, REPORTES_CONFIGURABLES.length - 1)
  assert.equal(resumen.conAlcance, 1)
  assert.equal(resumen.columnasLimitadas, 1)
})

test('HU045: las acciones en bloque copian y aplican la configuración por rol', () => {
  let configuracion = configuracionPorDefecto()
  configuracion = actualizarReporte(configuracion, 'GERENCIA', 'MOROSOS', {
    permitido: false,
    zonas: ['Z2'],
    columnas: ['codigo', 'deudaVencida'],
  })

  const copiada = copiarConfiguracionRol(configuracion, 'GERENCIA', 'ANALISTA_RENTAS')
  assert.equal(copiada.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, false)
  assert.deepEqual(copiada.roles.ANALISTA_RENTAS.reportes.MOROSOS.zonas, ['Z2'])
  assert.deepEqual(copiada.roles.ANALISTA_RENTAS.reportes.MOROSOS.columnas, columnasConFijas('MOROSOS'))
  assert.equal(copiada.roles.ANALISTA_RENTAS.reportes.PRICOS.permitido, true)

  const aplicada = aplicarConfiguracionATodosLosRoles(configuracion, 'GERENCIA')
  for (const rol of ROLES_ACCESO_REPORTES) {
    assert.equal(aplicada.roles[rol].reportes.MOROSOS.permitido, false)
    assert.deepEqual(aplicada.roles[rol].reportes.MOROSOS.zonas, ['Z2'])
  }

  const conVentanilla = actualizarReporte(configuracion, 'VENTANILLA', 'MOROSOS', { permitido: true })
  const soloConAcceso = aplicarConfiguracionATodosLosRoles(
    conVentanilla,
    'GERENCIA',
    ['GERENCIA', 'ANALISTA_RENTAS'],
  )
  assert.equal(soloConAcceso.roles.GERENCIA.reportes.MOROSOS.permitido, false)
  assert.equal(soloConAcceso.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, false)
  assert.equal(soloConAcceso.roles.VENTANILLA.reportes.MOROSOS.permitido, true)

  const sinReportes = marcarTodosLosReportes(configuracion, 'VENTANILLA', false)
  assert.ok(Object.values(sinReportes.roles.VENTANILLA.reportes).every((reporte) => reporte.permitido === false))
  assert.equal(sinReportes.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, true)
})

test('HU045: los permisos reales identifican qué roles tienen acceso a reportes', () => {
  assert.deepEqual(
    permisosDeRol({ permisos: 'USUARIOS:LISTAR, REPORTES:CONSULTAR ,reportes:exportar' }),
    ['USUARIOS:LISTAR', 'REPORTES:CONSULTAR', 'REPORTES:EXPORTAR'],
  )
  assert.deepEqual(permisosDeRol({ permisosLista: ['REPORTES:CONSULTAR'] }), ['REPORTES:CONSULTAR'])
  assert.deepEqual(permisosDeRol({ permisos: '' }), [])
  assert.deepEqual(permisosDeRol(null), [])

  assert.equal(rolTienePermiso({ permisos: 'REPORTES:CONSULTAR' }, 'reportes:consultar'), true)
  assert.equal(rolTienePermiso({ permisos: 'USUARIOS:LISTAR' }, 'REPORTES:CONSULTAR'), false)
  assert.equal(rolTienePermiso({ permisos: 'USUARIOS:LISTAR' }, ''), false)

  const roles = [
    { nombre: 'ADMINISTRADOR', permisos: 'USUARIOS:LISTAR,SEGMENTOS:CONSULTAR' },
    { nombre: 'GERENCIA', permisos: 'REPORTES:CONSULTAR,REPORTES:EXPORTAR' },
    { nombre: 'ANALISTA_RENTAS', permisos: 'REPORTES:CONSULTAR' },
    { nombre: 'VENTANILLA', permisos: 'FORMULARIOS:CONSULTAR' },
  ]
  assert.deepEqual(
    roles.filter((rol) => rolTienePermiso(rol, 'REPORTES:CONSULTAR')).map((rol) => rol.nombre),
    ['GERENCIA', 'ANALISTA_RENTAS'],
  )
})

test('HU045: los valores de alcance se agregan sin duplicados y con límites', () => {
  assert.deepEqual(agregarValorAlcance(['Z1'], ' z1 '), ['Z1'])
  assert.deepEqual(agregarValorAlcance(['Z1'], ' Z2 '), ['Z1', 'Z2'])
  assert.deepEqual(quitarValorAlcance(['Z1', 'Z2'], ' z1 '), ['Z2'])
  assert.equal(agregarValorAlcance([], 'x'.repeat(90))[0].length, MAX_LONGITUD_VALOR_ALCANCE)
})

test('HU045: los nombres de segmentos publicados se limpian, deduplican y ordenan', () => {
  const opciones = opcionesSegmentosPublicados([
    { nombre: ' En riesgo ', tamano: 45 },
    { nombre: 'Buen pagador', tamano: 80 },
    { nombre: 'en riesgo', tamano: 99 },
    { nombre: '', tamano: 10 },
    { nombre: null },
    { nombre: 'Al día', tamano: 0 },
  ])

  assert.deepEqual(opciones.map((opcion) => opcion.valor), ['Al día', 'Buen pagador', 'En riesgo'])
  assert.equal(opciones[0].detalle, null)
  assert.equal(opciones[1].detalle, '80 contribuyentes')
  assert.equal(opciones[2].detalle, '45 contribuyentes')

  assert.deepEqual(opcionesSegmentosPublicados(null), [])
  assert.deepEqual(opcionesSegmentosPublicados([{ nombre: '   ' }]), [])
  assert.equal(opcionesSegmentosPublicados([{ nombre: 'Único', tamano: 1 }])[0].detalle, '1 contribuyente')
})

test('HU045: las opciones de alcance se filtran sin distinguir tildes ni mayúsculas', () => {
  const opciones = [
    { valor: 'Al día', detalle: '120 contribuyentes' },
    { valor: 'En riesgo', detalle: '45 contribuyentes' },
    'Z1',
  ]

  assert.deepEqual(filtrarOpcionesAlcance(opciones, ''), opciones)
  assert.deepEqual(filtrarOpcionesAlcance(opciones, 'al dia'), [opciones[0]])
  assert.deepEqual(filtrarOpcionesAlcance(opciones, 'RIESGO'), [opciones[1]])
  assert.deepEqual(filtrarOpcionesAlcance(opciones, 'contribuyentes'), [opciones[0], opciones[1]])
  assert.deepEqual(filtrarOpcionesAlcance(opciones, 'z1'), ['Z1'])
  assert.deepEqual(filtrarOpcionesAlcance(null, 'x'), [])
})

test('HU045: el servicio simulado guarda la configuración y la restablece', async () => {
  assert.equal(ACCESOS_REPORTES_SIMULADOS, true)
  reiniciarConfiguracionAccesoReportes()

  const inicial = await obtenerConfiguracionAccesoReportes()
  assert.equal(inicial.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, true)

  const modificada = actualizarReporte(inicial, 'ANALISTA_RENTAS', 'MOROSOS', {
    permitido: false,
    zonas: ['Z1'],
  })
  const guardada = await guardarConfiguracionAccesoReportes(modificada, { usuario: 'Administrador de prueba' })

  assert.equal(guardada.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, false)
  assert.deepEqual(guardada.roles.ANALISTA_RENTAS.reportes.MOROSOS.zonas, ['Z1'])
  assert.equal(guardada.actualizadoPor, 'Administrador de prueba')
  assert.ok(guardada.actualizadoEn)

  const leida = await obtenerConfiguracionAccesoReportes()
  assert.equal(leida.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, false)
  assert.deepEqual(leida.roles.ANALISTA_RENTAS.reportes.MOROSOS.zonas, ['Z1'])

  reiniciarConfiguracionAccesoReportes()
  const reiniciada = await obtenerConfiguracionAccesoReportes()
  assert.equal(reiniciada.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, true)
  assert.deepEqual(reiniciada.roles.ANALISTA_RENTAS.reportes.MOROSOS.zonas, [])
})

test('HU045: el servicio siembra los valores iniciales según los roles con acceso', async () => {
  reiniciarConfiguracionAccesoReportes()
  const configuracion = await obtenerConfiguracionAccesoReportes({ rolesConAcceso: ['GERENCIA'] })
  assert.equal(configuracion.roles.GERENCIA.reportes.MOROSOS.permitido, true)
  assert.equal(configuracion.roles.ANALISTA_RENTAS.reportes.MOROSOS.permitido, false)
  assert.equal(configuracion.roles.VENTANILLA.reportes.MOROSOS.permitido, false)
  reiniciarConfiguracionAccesoReportes()
})

test('HU045: la pantalla, el menú y el aviso quedan conectados', () => {
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8')
  const modulos = readFileSync(new URL('../src/utils/modulos.jsx', import.meta.url), 'utf8')
  const reportes = readFileSync(new URL('../src/pages/Reportes.jsx', import.meta.url), 'utf8')
  const reporteria = readFileSync(new URL('../src/pages/Reporteria.jsx', import.meta.url), 'utf8')
  const configuracion = readFileSync(new URL('../src/pages/ConfiguracionAccesoReportes.jsx', import.meta.url), 'utf8')
  const aviso = readFileSync(new URL('../src/components/AvisoAccesoReportes.jsx', import.meta.url), 'utf8')
  const selector = readFileSync(new URL('../src/components/SelectorMultiple.jsx', import.meta.url), 'utf8')
  const migracion = readFileSync(new URL('../../back/src/main/resources/db/migration/V45__hu045_admin_consulta_segmentos.sql', import.meta.url), 'utf8')

  assert.match(app, /import ConfiguracionAccesoReportes/)
  assert.match(app, /path="\/configuracion\/reportes"/)
  assert.match(app, /permiso="USUARIOS:LISTAR"/)
  assert.match(modulos, /ruta: '\/configuracion\/reportes'/)
  assert.match(modulos, /permiso: 'USUARIOS:LISTAR'/)
  assert.match(reportes, /<AvisoAccesoReportes \/>/)
  assert.match(reporteria, /<AvisoAccesoReportes \/>/)
  assert.match(configuracion, /SelectorColumnasAgrupadas/)
  assert.match(configuracion, /SelectorMultiple/)
  assert.match(configuracion, /listarSegmentosPublicados/)
  assert.match(configuracion, /opcionesSegmentosPublicados/)
  assert.match(configuracion, /listarRolesInternos/)
  assert.match(configuracion, /rolTienePermiso/)
  assert.match(configuracion, /rolesConfigurables/)
  assert.match(configuracion, /rolesConAcceso/)
  assert.match(configuracion, /Sin REPORTES:CONSULTAR en la base de datos/)
  assert.match(configuracion, /El rol Administrador no se configura/)
  assert.match(configuracion, /Permisos reales del rol/)
  assert.match(configuracion, /REPORTES:CONSULTAR/)
  assert.match(configuracion, /acceso-reportes-reporte-titulo/)
  assert.match(configuracion, /aria-expanded=/)
  assert.match(configuracion, /aria-controls=/)
  assert.match(configuracion, /hidden=\{!expandido\}/)
  assert.match(configuracion, /Expandir todos/)
  assert.match(configuracion, /Contraer todos/)
  assert.match(configuracion, /Siempre visibles/)
  assert.match(configuracion, /columnasFijas/)
  assert.match(configuracion, /columnasConfigurables/)
  assert.match(configuracion, /gruposConfigurables/)
  assert.doesNotMatch(configuracion, /<datalist/)
  assert.match(selector, /role="combobox"/)
  assert.match(selector, /role="listbox"/)
  assert.match(selector, /aria-multiselectable="true"/)
  assert.match(migracion, /SEGMENTOS:CONSULTAR/)
  assert.match(migracion, /ADMINISTRADOR/)
  assert.match(aviso, /Información restringida por rol/)
  assert.match(aviso, /comun[ií]cate con el administrador del sistema/i)
})
