import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import {
  aprobarSegmentacion,
  listarSegmentos,
  listarSegmentosPublicados,
} from '../src/services/segmentosService.js'
import {
  agruparSegmentos,
  claseEstadoSegmento,
  etiquetaEstadoSegmento,
  formatearVariablesCaracteristicas,
  participacionSegmento,
} from '../src/utils/segmentos.js'

const SEGMENTOS = [
  {
    idSegmento: 2,
    idModelo: 1,
    modelo: 'kmeans-pagos',
    versionModelo: '1.0',
    estado: 'BORRADOR',
    nombre: 'Riesgo alto',
    descripcion: 'Contribuyentes con deuda vencida.',
    tamano: 2,
    variablesCaracteristicas: '{"deuda":"alta","regularidad":"baja"}',
  },
  {
    idSegmento: 1,
    idModelo: 1,
    modelo: 'kmeans-pagos',
    versionModelo: '1.0',
    estado: 'BORRADOR',
    nombre: 'Puntual',
    descripcion: 'Contribuyentes con pagos al día.',
    tamano: 3,
    variablesCaracteristicas: '{"deuda":"baja","regularidad":"alta"}',
  },
  {
    idSegmento: 3,
    idModelo: 2,
    modelo: 'kmeans-anterior',
    versionModelo: '0.9',
    estado: 'PUBLICADO',
    nombre: 'Publicado',
    descripcion: 'Segmentación vigente.',
    tamano: 4,
    variablesCaracteristicas: '{}',
    aprobador: 'gerencia',
    fechaAprobacion: '2026-10-19T12:00:00',
  },
]

test('HU031: lista segmentos y filtra por estado', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(new URL(url))
    return Response.json([])
  })

  await listarSegmentos()
  await listarSegmentos('BORRADOR')
  await listarSegmentosPublicados()

  assert.equal(urls[0].pathname, '/api/segmentos')
  assert.equal(urls[0].search, '')
  assert.equal(urls[1].searchParams.get('estado'), 'BORRADOR')
  assert.equal(urls[2].pathname, '/api/segmentos/publicados')
})

test('HU031: aprueba la segmentación enviando CSRF y todos los ids', async (t) => {
  const llamadas = []
  t.mock.method(globalThis, 'fetch', async (url, opciones = {}) => {
    llamadas.push({ url: String(url), opciones })
    if (String(url).endsWith('/auth/interno/csrf')) {
      return Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-hu031' })
    }
    return Response.json({ publicados: [{ idSegmento: 1 }, { idSegmento: 2 }], idsRetirados: [9] })
  })

  const respuesta = await aprobarSegmentacion([1, 2])

  assert.equal(respuesta.publicados.length, 2)
  assert.equal(llamadas.length, 2)
  assert.ok(llamadas[0].url.endsWith('/api/auth/interno/csrf'))
  assert.ok(llamadas[1].url.endsWith('/api/segmentos/aprobaciones'))
  assert.equal(llamadas[1].opciones.method, 'POST')
  assert.equal(llamadas[1].opciones.credentials, 'include')
  assert.equal(new Headers(llamadas[1].opciones.headers).get('X-XSRF-TOKEN'), 'csrf-hu031')
  assert.equal(new Headers(llamadas[1].opciones.headers).get('Content-Type'), 'application/json')
  assert.deepEqual(JSON.parse(llamadas[1].opciones.body), { idsSegmento: [1, 2] })
})

test('HU031: no aprueba una lista vacía', async () => {
  await assert.rejects(() => aprobarSegmentacion([]), { codigo: 'SEGMENTACION_REQUERIDA' })
})

test('HU031: agrupa por modelo y estado, ordena segmentos y suma contribuyentes', () => {
  const grupos = agruparSegmentos(SEGMENTOS)

  assert.equal(grupos.length, 2)
  assert.equal(grupos[0].estado, 'BORRADOR')
  assert.equal(grupos[0].modelo, 'kmeans-pagos')
  assert.equal(grupos[0].segmentos[0].nombre, 'Puntual')
  assert.equal(grupos[0].segmentos[1].nombre, 'Riesgo alto')
  assert.equal(grupos[0].contribuyentes, 5)
  assert.equal(grupos[1].estado, 'PUBLICADO')
  assert.equal(grupos[1].aprobador, 'gerencia')
  assert.equal(grupos[1].fechaAprobacion, '2026-10-19T12:00:00')
  assert.deepEqual(agruparSegmentos(null), [])
})

test('HU031: calcula la participación de cada segmento', () => {
  assert.equal(participacionSegmento({ tamano: 2 }, { contribuyentes: 5 }), 40)
  assert.equal(participacionSegmento({ tamano: 0 }, { contribuyentes: 5 }), 0)
  assert.equal(participacionSegmento({ tamano: 2 }, { contribuyentes: 0 }), null)
})

test('HU031: presenta los estados y el JSON de variables sin reinterpretarlo', () => {
  assert.equal(etiquetaEstadoSegmento('BORRADOR'), 'Pendiente de aprobación')
  assert.equal(etiquetaEstadoSegmento('PUBLICADO'), 'Publicado')
  assert.equal(etiquetaEstadoSegmento('RETIRADO'), 'Retirado')
  assert.equal(etiquetaEstadoSegmento(undefined), '—')
  assert.equal(claseEstadoSegmento('BORRADOR'), 'aviso')
  assert.equal(claseEstadoSegmento('PUBLICADO'), 'ok')
  assert.equal(claseEstadoSegmento('RETIRADO'), 'neutro')
  assert.equal(formatearVariablesCaracteristicas('{"deuda":"alta"}'), '{\n  "deuda": "alta"\n}')
  assert.equal(formatearVariablesCaracteristicas('texto libre'), 'texto libre')
  assert.equal(formatearVariablesCaracteristicas(''), 'Sin variables registradas.')
})

test('HU031: la aprobación tiene una pantalla propia y el dashboard solo muestra la publicación', () => {
  const app = readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8')
  const modulos = readFileSync(new URL('../src/utils/modulos.jsx', import.meta.url), 'utf8')
  const dashboard = readFileSync(new URL('../src/pages/Dashboard.jsx', import.meta.url), 'utf8')
  const pagina = readFileSync(new URL('../src/pages/Segmentos.jsx', import.meta.url), 'utf8')

  assert.match(app, /path="\/segmentos"/)
  assert.match(app, /permiso="SEGMENTOS:CONSULTAR"/)
  assert.match(modulos, /ruta: '\/segmentos'/)
  assert.match(modulos, /permiso: 'SEGMENTOS:CONSULTAR'/)
  assert.match(pagina, /SEGMENTOS:APROBAR/)
  assert.match(pagina, /aprobarSegmentacion/)
  assert.match(dashboard, /Gestionar segmentaciones/)
  assert.match(dashboard, /segmentacionPublicada/)
  assert.doesNotMatch(dashboard, /PanelSegmentos/)
})
