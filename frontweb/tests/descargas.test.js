import assert from 'node:assert/strict'
import test from 'node:test'
import { nombreDescarga, nombreDesdeCabecera } from '../src/services/reporteService.js'

test('lee el nombre desde Content-Disposition, incluido filename*', () => {
  assert.equal(nombreDesdeCabecera('attachment; filename="reporte_morosos_20261108.xlsx"'), 'reporte_morosos_20261108.xlsx')
  assert.equal(nombreDesdeCabecera("attachment; filename*=UTF-8''reporte_morosos_20261108_vista.xlsx"), 'reporte_morosos_20261108_vista.xlsx')
  assert.equal(nombreDesdeCabecera(null), null)
})

test('genera un nombre de respaldo según el tipo y el formato', () => {
  const fecha = new Date().toLocaleDateString('en-CA').replaceAll('-', '')
  assert.equal(nombreDescarga('/reportes/MOROSOS/exportar?formato=EXCEL'), `reporte_morosos_${fecha}.xlsx`)
  assert.equal(nombreDescarga('/reportes/PRICOS/exportar?formato=PDF'), `reporte_pricos_${fecha}.pdf`)
  assert.equal(nombreDescarga('/reportes/buenos-pagadores-zona/exportar?formato=EXCEL'), `reporte_buenos_pagadores_${fecha}.xlsx`)
  assert.equal(nombreDescarga('/reportes/vistas/1/exportar?formato=PDF'), `reporte_${fecha}.pdf`)
})
