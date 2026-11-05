const NF0 = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 0 })
const NF1 = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const NF2 = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export const soles = (v) => `S/ ${NF2.format(v)}`
export const solesEnteros = (v) => `S/ ${NF0.format(Math.round(v))}`
export const solesCompactos = (v) => {
  const a = Math.abs(v)
  if (a >= 1e6) return `S/ ${NF2.format(v / 1e6)} M`
  if (a >= 1e4) return `S/ ${NF1.format(v / 1e3)} mil`
  return `S/ ${NF0.format(Math.round(v))}`
}
export const porcentajeTexto = (v) => (v == null || !isFinite(v) ? '—' : `${NF1.format(v)} %`)
export const entero = (v) => NF0.format(Math.round(v))
export const redondear = (v) => (v == null ? null : Math.round(v * 100) / 100)
export const razon = (a, b) => (b > 0 ? (a / b) * 100 : null)
export const decimal = (v) => NF1.format(v)

export const FORMATOS = { monto: solesEnteros, porcentaje: porcentajeTexto, cantidad: entero }
export const FORMATOS_EJE = {
  monto: (v) => solesCompactos(v).replace('S/ ', ''),
  porcentaje: (v) => `${NF0.format(v)} %`,
  cantidad: (v) => NF0.format(v),
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
export const mesAbsoluto = (iso) => {
  const [anio, mes] = iso.split('-').map(Number)
  return anio * 12 + mes - 1
}
export const mesIso = (m) => `${Math.floor(m / 12)}-${String((m % 12) + 1).padStart(2, '0')}`
export const etiquetaMes = (m) => `${MESES[m % 12]} ${String(Math.floor(m / 12)).slice(2)}`
export const etiquetaMesLarga = (m) => `${MESES[m % 12]} ${Math.floor(m / 12)}`

export const PESTANAS = [
  { id: 'resumen', titulo: 'Resumen', descripcion: 'Recaudación, meta y morosidad de un vistazo.' },
  { id: 'pricos', titulo: 'PRICOS', descripcion: 'Principales contribuyentes: concentración de la emisión y cumplimiento.' },
  { id: 'morosos', titulo: 'Morosos', descripcion: 'Deuda vencida por zona, tributo, segmento y antigüedad.' },
  { id: 'buenos', titulo: 'Buenos Pagadores', descripcion: 'Contribuyentes que pagan todas sus cuotas a tiempo.' },
]

export const TIPOS_GRAFICO = {
  barras: 'Barras',
  barrash: 'Barras horizontales',
  linea: 'Líneas',
  area: 'Área',
  circular: 'Circular',
  dona: 'Dona',
  tabla: 'Tabla',
  calor: 'Mapa de calor',
  dispersion: 'Dispersión',
}

const R_TIEMPO_CIRCULAR = 'Una serie en el tiempo no representa partes de un todo. Usa líneas, barras o área.'
const R_VARIAS_CIRCULAR = 'Este gráfico compara varias series; el circular solo puede mostrar una.'
const R_TIEMPO_HORIZONTAL = 'El tiempo se lee de izquierda a derecha; en barras horizontales se pierde la tendencia.'
const R_CATEGORIA_LINEA = 'Las categorías no tienen un orden continuo; una línea sugeriría una tendencia que no existe.'
const R_RANKING_CIRCULAR = 'Un ranking compara magnitudes; en un circular es difícil ordenarlas y compararlas.'
const R_RANKING_VERTICAL = 'Los rankings se leen mejor en barras horizontales: los nombres caben completos y el orden se lee de arriba abajo.'
const R_TASA_CIRCULAR = 'Los porcentajes de cada zona no suman 100 %: no son partes de un todo.'
const R_TRAMO_CIRCULAR = 'Los tramos tienen un orden natural que el gráfico circular pierde.'
const R_TRAMO_LINEA = 'Son tramos discretos, no una medición continua en el tiempo.'
const R_MATRIZ = 'Los datos cruzan dos dimensiones (zona y tributo); solo el mapa de calor o la tabla las muestran a la vez.'
const R_RELACION = 'Cada punto relaciona dos medidas de un contribuyente; solo la dispersión o la tabla las muestran juntas.'
const LISTA_BASE = ['barras', 'barrash', 'linea', 'area', 'circular', 'dona', 'tabla']

export const COMPATIBILIDAD = {
  temporal: { permitidos: ['linea', 'barras', 'area', 'tabla'], motivos: { barrash: R_TIEMPO_HORIZONTAL, circular: R_TIEMPO_CIRCULAR, dona: R_TIEMPO_CIRCULAR } },
  temporalMulti: { permitidos: ['linea', 'barras', 'area', 'tabla'], motivos: { barrash: R_TIEMPO_HORIZONTAL, circular: R_VARIAS_CIRCULAR, dona: R_VARIAS_CIRCULAR } },
  composicion: { permitidos: ['dona', 'circular', 'barras', 'barrash', 'tabla'], motivos: { linea: R_CATEGORIA_LINEA, area: R_CATEGORIA_LINEA } },
  ranking: { permitidos: ['barrash', 'tabla'], motivos: { barras: R_RANKING_VERTICAL, linea: R_CATEGORIA_LINEA, area: R_CATEGORIA_LINEA, circular: R_RANKING_CIRCULAR, dona: R_RANKING_CIRCULAR } },
  tasa: { permitidos: ['barrash', 'barras', 'tabla'], motivos: { linea: R_CATEGORIA_LINEA, area: R_CATEGORIA_LINEA, circular: R_TASA_CIRCULAR, dona: R_TASA_CIRCULAR } },
  comparacion: { permitidos: ['barras', 'barrash', 'tabla'], motivos: { linea: R_CATEGORIA_LINEA, area: R_CATEGORIA_LINEA, circular: R_VARIAS_CIRCULAR, dona: R_VARIAS_CIRCULAR } },
  ordinal: { permitidos: ['barras', 'barrash', 'tabla'], motivos: { linea: R_TRAMO_LINEA, area: R_TRAMO_LINEA, circular: R_TRAMO_CIRCULAR, dona: R_TRAMO_CIRCULAR } },
  matriz: { permitidos: ['calor', 'tabla'], motivos: { barras: R_MATRIZ, linea: R_MATRIZ, circular: R_MATRIZ }, lista: ['calor', 'tabla', 'barras', 'linea', 'circular'] },
  relacion: { permitidos: ['dispersion', 'tabla'], motivos: { barras: R_RELACION, linea: R_RELACION, circular: R_RELACION }, lista: ['dispersion', 'tabla', 'barras', 'linea', 'circular'] },
}
export const tiposListados = (clase) => COMPATIBILIDAD[clase].lista || LISTA_BASE
export const ORDENABLES = new Set(['composicion', 'ranking', 'tasa', 'comparacion'])
export const SIN_ORDEN = {
  temporal: 'Se conserva el orden temporal para leer la tendencia.',
  temporalMulti: 'Se conserva el orden temporal para leer la tendencia.',
  ordinal: 'Los tramos conservan su orden natural.',
  matriz: 'Este gráfico no tiene un orden de categorías.',
  relacion: 'Este gráfico no tiene un orden de categorías.',
}

const COLOR_PRINCIPAL = '#3f5fb5'
const COLOR_EXITO = '#1baf7a'
const COLOR_GRIS = '#8c9199'
const COLOR_REFERENCIA = '#17406b'
export const PALETA = [COLOR_PRINCIPAL, '#eb6834', COLOR_EXITO, '#eda100', '#e87ba4', '#008300']
const COLORES_SEGMENTO = [COLOR_EXITO, '#eda100', '#e87ba4', COLOR_PRINCIPAL, '#eb6834', '#008300', COLOR_GRIS]
export const colorSegmento = (i) => COLORES_SEGMENTO[i % COLORES_SEGMENTO.length]
const colorDe = (i) => PALETA[i % PALETA.length]

const ANTIGUEDAD = ['Hasta 3 meses', '4 a 6 meses', '7 a 12 meses', 'Más de 12 meses']
const ANTICIPACION = ['Más de 30 días antes', '15 a 30 días antes', '1 a 14 días antes', 'El día del vencimiento']

export const FILTROS_INICIALES = (modelo) => ({ periodo: modelo.periodoInicial, zona: '', tributo: '', segmento: '' })

/* Periodos, etiquetas y definiciones de la pantalla a partir del catálogo de la carga publicada. */
export function crearModelo(catalogo) {
  const corte = mesAbsoluto(catalogo.fechaCorte.slice(0, 7))
  const primerMes = mesAbsoluto(catalogo.primerMes)
  const anioCorte = Math.floor(corte / 12)
  const inicioAnio = anioCorte * 12
  const periodos = {}
  periodos[String(anioCorte)] = {
    etiqueta: `${anioCorte} (a la fecha de corte)`,
    desde: inicioAnio,
    hasta: corte,
    previo: [inicioAnio - 12, corte - 12],
    etiquetaPrevia: `ene–${MESES[corte % 12]} ${anioCorte - 1}`,
  }
  for (let anio = anioCorte - 1; anio >= anioCorte - 2 && anio * 12 + 11 >= primerMes; anio--) {
    periodos[String(anio)] = {
      etiqueta: String(anio),
      desde: anio * 12,
      hasta: anio * 12 + 11,
      previo: [(anio - 1) * 12, (anio - 1) * 12 + 11],
      etiquetaPrevia: String(anio - 1),
    }
  }
  periodos.u12 = {
    etiqueta: 'Últimos 12 meses',
    desde: corte - 11,
    hasta: corte,
    previo: [corte - 23, corte - 12],
    etiquetaPrevia: 'los 12 meses previos',
  }
  periodos.acum = {
    etiqueta: `Acumulado ${Math.floor(primerMes / 12)}–${anioCorte}`,
    desde: primerMes,
    hasta: corte,
    previo: null,
    etiquetaPrevia: null,
  }

  const zonas = catalogo.zonas
  const tributos = catalogo.tributos
  const segmentos = catalogo.segmentos
  const usos = catalogo.usos
  const canales = catalogo.canales
  const indicesTributo = (filtros) => tributos.map((_, t) => t)
    .filter((t) => !filtros?.tributo || tributos[t].codigo === filtros.tributo)
  const indicesZona = (A) => zonas.map((_, z) => z).filter((z) => A.porZona.emitido[z] > 0 || A.porZona.recaudado[z] > 0)
  const mesesDe = (A) => {
    const inicio = mesAbsoluto(A.desde)
    const fin = mesAbsoluto(A.hasta)
    const lista = []
    for (let m = inicio; m <= fin; m++) lista.push(etiquetaMes(m))
    return lista
  }
  const conCuotas = (A) => A.porMes.cuotas.map((n, j) => (n > 0 ? j : -1)).filter((j) => j >= 0)
  const etiquetasConCuotas = (A) => {
    const inicio = mesAbsoluto(A.desde)
    return conCuotas(A).map((j) => etiquetaMes(inicio + j))
  }
  const alinear = (valores, largo) => Array.from({ length: largo }, (_, i) => (i < valores.length ? valores[i] : null))

  const graficos = {
    resumen: [
      {
        id: 'r1', titulo: 'Recaudación y emisión por mes', descripcion: 'Pagos recibidos en el mes frente a lo emitido en las cuotas que vencen ese mes.',
        clase: 'temporalMulti', porDefecto: 'barras', formato: 'monto', etiquetaCategoria: 'Mes',
        datos: (A) => ({ categorias: mesesDe(A), series: [{ nombre: 'Emisión', valores: [...A.porMes.emitido], color: COLOR_GRIS }, { nombre: 'Recaudación', valores: [...A.porMes.recaudado], color: COLOR_PRINCIPAL }] }),
        referencia: (R, A) => ({ nombre: 'Recaudación de la vista fijada', valores: alinear([...R.porMes.recaudado], A.porMes.recaudado.length), color: COLOR_REFERENCIA, punteada: true }),
      },
      {
        id: 'r2', titulo: 'Recaudación por tipo de tributo', descripcion: 'Composición de lo recaudado en el periodo.',
        clase: 'composicion', porDefecto: 'dona', formato: 'monto', dimension: 'tributo', aditivo: true, etiquetaCategoria: 'Tributo',
        datos: (A, filtros) => {
          const claves = indicesTributo(filtros)
          return { categorias: claves.map((t) => tributos[t].nombre), claves, series: [{ nombre: 'Recaudación', valores: claves.map((t) => A.porTributo.recaudado[t]), colores: claves.map(colorDe), color: colorDe(0) }] }
        },
      },
      {
        id: 'r3', titulo: 'Contribuyentes por segmento', descripcion: 'Segmentos de comportamiento de pago publicados.',
        clase: 'composicion', porDefecto: 'barras', formato: 'cantidad', dimension: 'segmento', aditivo: true, etiquetaCategoria: 'Segmento',
        datos: (A) => ({ categorias: [...segmentos], claves: segmentos.map((_, i) => i), series: [{ nombre: 'Contribuyentes', valores: [...A.porSegmento.contribuyentes], colores: segmentos.map((_, i) => colorSegmento(i)), color: colorSegmento(0) }] }),
      },
      {
        id: 'r4', titulo: 'Índice de morosidad por zona', descripcion: 'Cuotas vencidas sin pagar ÷ monto emitido en el periodo.',
        clase: 'tasa', porDefecto: 'barrash', formato: 'porcentaje', dimension: 'zona', aditivo: false, etiquetaCategoria: 'Zona',
        datos: (A) => {
          const claves = indicesZona(A)
          return { categorias: claves.map((z) => zonas[z].nombre), claves, series: [{ nombre: 'Índice de morosidad', valores: claves.map((z) => razon(A.porZona.deuda[z], A.porZona.emitido[z])), color: COLOR_PRINCIPAL }] }
        },
      },
    ],
    pricos: [
      {
        id: 'p1', titulo: 'Emisión frente a porcentaje cobrado', descripcion: 'Cada punto es un PRICO, hasta los 300 de mayor emisión; el tamaño indica su deuda vencida.',
        clase: 'relacion', porDefecto: 'dispersion', formato: 'monto', dimension: 'contribuyente', etiquetaCategoria: 'Contribuyente',
        datos: (A) => ({ puntos: A.puntosPricos }),
      },
      {
        id: 'p2', titulo: 'Cumplimiento por mes de vencimiento: PRICOS frente al resto', descripcion: 'Porcentaje cobrado, a la fecha de corte, de las cuotas que vencen cada mes.',
        clase: 'temporalMulti', porDefecto: 'linea', formato: 'porcentaje', etiquetaCategoria: 'Mes',
        datos: (A) => {
          const q = conCuotas(A)
          const c = A.pricosPorMes
          return { categorias: etiquetasConCuotas(A), series: [{ nombre: 'PRICOS', valores: q.map((j) => razon(c.cobradoPricos[j], c.emitidoPricos[j])), color: COLOR_PRINCIPAL }, { nombre: 'Resto de contribuyentes', valores: q.map((j) => razon(c.cobradoResto[j], c.emitidoResto[j])), color: COLOR_GRIS }] }
        },
      },
      {
        id: 'p3', titulo: 'Emisión de PRICOS por uso del predio', descripcion: 'A qué actividad corresponde lo emitido a los principales contribuyentes.',
        clase: 'composicion', porDefecto: 'dona', formato: 'monto', dimension: 'uso', aditivo: true, etiquetaCategoria: 'Uso',
        datos: (A) => ({ categorias: [...usos], claves: usos.map((_, i) => i), series: [{ nombre: 'Emisión PRICOS', valores: [...A.usoEmitido], colores: usos.map((_, i) => colorDe(i)), color: colorDe(0) }] }),
      },
      {
        id: 'p4', titulo: 'Emisión por tributo: PRICOS y resto', descripcion: 'Peso de los principales contribuyentes en cada tributo.',
        clase: 'comparacion', porDefecto: 'barras', formato: 'monto', dimension: 'tributo', etiquetaCategoria: 'Tributo',
        datos: (A, filtros) => {
          const claves = indicesTributo(filtros)
          return { categorias: claves.map((t) => tributos[t].nombre), claves, series: [{ nombre: 'PRICOS', valores: claves.map((t) => A.tributoEmitidoPricos[t]), color: COLOR_PRINCIPAL }, { nombre: 'Resto de contribuyentes', valores: claves.map((t) => A.porTributo.emitido[t] - A.tributoEmitidoPricos[t]), color: COLOR_GRIS }] }
        },
      },
    ],
    morosos: [
      {
        id: 'm1', titulo: 'Morosidad por zona y tributo', descripcion: 'Índice de morosidad de cada cruce. Más oscuro, más morosidad.',
        clase: 'matriz', porDefecto: 'calor', formato: 'porcentaje', dimension: 'zona', etiquetaCategoria: 'Zona',
        datos: (A, filtros) => {
          const filas = indicesZona(A)
          const columnas = indicesTributo(filtros)
          return { filas: filas.map((z) => zonas[z].nombre), columnas: columnas.map((t) => tributos[t].nombre), clavesFila: filas, clavesColumna: columnas, matriz: filas.map((z) => columnas.map((t) => razon(A.zonaTributoDeuda[z][t], A.zonaTributoEmitido[z][t]))) }
        },
      },
      {
        id: 'm2', titulo: 'Saldo de deuda vencida por segmento', descripcion: 'Deuda del periodo pendiente al cierre de cada mes.',
        clase: 'temporalMulti', porDefecto: 'area', formato: 'monto', apilado: true, dimensionSerie: 'segmento', etiquetaCategoria: 'Mes',
        datos: (A) => ({ categorias: mesesDe(A), series: segmentos.map((nombre, s) => ({ nombre, clave: s, valores: A.saldoPorSegmento[s], color: colorSegmento(s) })).filter((serie) => serie.valores.some((v) => v > 0)) }),
      },
      {
        id: 'm3', titulo: 'Antigüedad de la deuda vencida', descripcion: 'Monto pendiente según el tiempo transcurrido desde el vencimiento.',
        clase: 'ordinal', porDefecto: 'barras', formato: 'monto', etiquetaCategoria: 'Antigüedad',
        datos: (A) => ({ categorias: [...ANTIGUEDAD], series: [{ nombre: 'Deuda vencida', valores: [...A.antiguedad], color: COLOR_PRINCIPAL }] }),
      },
      {
        id: 'm4', titulo: 'Deuda vencida por zona', descripcion: 'Ranking de zonas por monto vencido sin pagar.',
        clase: 'ranking', porDefecto: 'barrash', formato: 'monto', dimension: 'zona', aditivo: true, ordenInicial: 'desc', etiquetaCategoria: 'Zona',
        datos: (A) => {
          const claves = indicesZona(A)
          return { categorias: claves.map((z) => zonas[z].nombre), claves, series: [{ nombre: 'Deuda vencida', valores: claves.map((z) => A.porZona.deuda[z]), color: COLOR_PRINCIPAL }] }
        },
      },
    ],
    buenos: [
      {
        id: 'b1', titulo: 'Regularidad de pago por mes de vencimiento', descripcion: 'Cuotas pagadas hasta su vencimiento ÷ cuotas emitidas, en cada mes.',
        clase: 'temporal', porDefecto: 'area', formato: 'porcentaje', etiquetaCategoria: 'Mes',
        datos: (A) => {
          const q = conCuotas(A)
          return { categorias: etiquetasConCuotas(A), series: [{ nombre: 'Pago puntual', valores: q.map((j) => razon(A.porMes.cuotasATiempo[j], A.porMes.cuotas[j])), color: COLOR_EXITO }] }
        },
        referencia: (R, A) => ({ nombre: 'Pago puntual de la vista fijada', valores: alinear(conCuotas(R).map((j) => razon(R.porMes.cuotasATiempo[j], R.porMes.cuotas[j])), conCuotas(A).length), color: COLOR_REFERENCIA, punteada: true }),
      },
      {
        id: 'b2', titulo: 'Buenos pagadores por zona', descripcion: 'Contribuyentes con todas sus cuotas del periodo pagadas a tiempo, frente al resto.',
        clase: 'comparacion', porDefecto: 'barras', formato: 'cantidad', apilado: true, dimension: 'zona', etiquetaCategoria: 'Zona',
        datos: (A) => {
          const claves = indicesZona(A)
          return { categorias: claves.map((z) => zonas[z].nombre), claves, series: [{ nombre: 'Buenos pagadores', valores: claves.map((z) => A.porZona.buenosPagadores[z]), color: COLOR_EXITO }, { nombre: 'Resto', valores: claves.map((z) => A.porZona.contribuyentes[z] - A.porZona.buenosPagadores[z]), color: '#c3c7cd' }] }
        },
      },
      {
        id: 'b3', titulo: 'Canal de los pagos puntuales', descripcion: 'Dónde se hacen los pagos realizados a tiempo.',
        clase: 'composicion', porDefecto: 'dona', formato: 'cantidad', etiquetaCategoria: 'Canal',
        datos: (A) => ({ categorias: canales.map((c) => c.nombre), claves: canales.map((_, i) => i), series: [{ nombre: 'Pagos puntuales', valores: [...A.canal], colores: canales.map((_, i) => colorDe(i)), color: colorDe(0) }] }),
      },
      {
        id: 'b4', titulo: 'Anticipación del pago puntual', descripcion: 'Cuánto antes del vencimiento se paga.',
        clase: 'ordinal', porDefecto: 'barras', formato: 'cantidad', etiquetaCategoria: 'Anticipación',
        datos: (A) => ({ categorias: [...ANTICIPACION], series: [{ nombre: 'Pagos puntuales', valores: [...A.anticipacion], color: COLOR_EXITO }] }),
      },
    ],
  }

  const columnasBase = [
    { clave: 'puesto', titulo: '#', numerica: true, valor: (_, puesto) => puesto },
    { clave: 'codigo', titulo: 'Código', valor: (f) => f.codigo },
    { clave: 'nombre', titulo: 'Contribuyente', fija: true, valor: (f) => f.nombre },
    { clave: 'zona', titulo: 'Zona', valor: (f) => zonas[f.zona]?.nombre ?? '—' },
    { clave: 'segmento', titulo: 'Segmento', valor: (f) => segmentos[f.segmento], segmento: true },
  ]
  const columna = (clave, titulo, valor, formato) => ({ clave, titulo, valor, formato, numerica: true })
  const ultimoPago = { clave: 'ultimo', titulo: 'Último pago', numerica: true, valor: (f) => f.ultimoPago, formato: (v) => (v ? etiquetaMesLarga(mesAbsoluto(v)) : 'Sin pagos') }

  const rankings = {
    resumen: {
      titulo: 'Top 10 contribuyentes por monto pagado',
      subtitulo: 'Pagos recibidos en el periodo, incluidos los de deuda de periodos anteriores.',
      columnas: [...columnasBase, columna('pagado', 'Pagado en el periodo', (f) => f.recaudado, soles), columna('emitido', 'Emisión del periodo', (f) => f.emitido, soles), columna('cobrado', '% cobrado', (f) => razon(f.cobrado, f.emitido), porcentajeTexto), columna('deuda', 'Deuda vencida', (f) => f.deuda, soles)],
    },
    pricos: {
      titulo: 'Top 10 PRICOS por emisión',
      subtitulo: 'Principales contribuyentes ordenados por monto emitido en el periodo.',
      columnas: [...columnasBase, { clave: 'uso', titulo: 'Uso principal', valor: (f) => usos[f.uso] ?? '—' }, columna('predios', 'Predios', (f) => f.predios, entero), columna('emitido', 'Emisión', (f) => f.emitido, soles), columna('cobrado', 'Cobrado', (f) => f.cobrado, soles), columna('cumplimiento', '% cumplimiento', (f) => razon(f.cobrado, f.emitido), porcentajeTexto), columna('deuda', 'Deuda vencida', (f) => f.deuda, soles)],
    },
    morosos: {
      titulo: 'Top 10 morosos por deuda vencida',
      subtitulo: 'Cuotas del periodo vencidas y no pagadas a la fecha de corte.',
      columnas: [...columnasBase, columna('deuda', 'Deuda vencida', (f) => f.deuda, soles), columna('vencidas', 'Cuotas vencidas', (f) => f.cuotasVencidas, entero), columna('atraso', 'Atraso máximo (meses)', (f) => f.atrasoMaximo, entero), { clave: 'tributo', titulo: 'Tributo principal de la deuda', valor: (f) => tributos[f.tributoPrincipal]?.nombre ?? '—' }, ultimoPago],
    },
    buenos: {
      titulo: 'Top 10 buenos pagadores por monto pagado',
      subtitulo: 'Contribuyentes con todas sus cuotas del periodo pagadas a tiempo.',
      columnas: [...columnasBase, columna('pagado', 'Pagado en el periodo', (f) => f.recaudado, soles), columna('aTiempo', 'Cuotas a tiempo', (f) => f.cuotasATiempo, entero), columna('emitido', 'Emisión del periodo', (f) => f.emitido, soles), columna('predios', 'Predios', (f) => f.predios, entero), { clave: 'tipo', titulo: 'Tipo de persona', valor: (f) => (f.juridica ? 'Jurídica' : 'Natural') }, ultimoPago],
    },
  }

  const avisos = {
    pricos: `PRICOS: contribuyentes con emisión anual desde ${solesEnteros(catalogo.umbralPrico)} en el año de la fecha de corte. El umbral es un parámetro configurable.`,
    buenos: 'Buen pagador: contribuyente que pagó todas sus cuotas del periodo hasta su fecha de vencimiento.',
    morosos: 'Moroso: contribuyente con al menos una cuota del periodo vencida y no pagada a la fecha de corte.',
  }

  return { catalogo, corte, primerMes, periodos, periodoInicial: String(anioCorte), zonas, tributos, segmentos, usos, canales, graficos, rankings, avisos }
}

export function consultaDeFiltros(modelo, filtros, extra = {}) {
  const periodo = modelo.periodos[filtros.periodo] ?? modelo.periodos[modelo.periodoInicial]
  return {
    desde: mesIso(periodo.desde),
    hasta: mesIso(periodo.hasta),
    zona: filtros.zona,
    tributo: filtros.tributo,
    segmento: filtros.segmento,
    ...extra,
  }
}

/* Consulta de los datos resaltados. Un resaltado por zona, segmento o tributo reemplaza a ese filtro. */
export function consultaDeSeleccion(modelo, filtros, seleccion) {
  const base = consultaDeFiltros(modelo, filtros)
  switch (seleccion.dimension) {
    case 'zona': return { ...base, zona: modelo.zonas[seleccion.clave].codigo }
    case 'segmento': return { ...base, segmento: modelo.segmentos[seleccion.clave] }
    case 'tributo': return { ...base, tributo: modelo.tributos[seleccion.clave].codigo }
    case 'uso': return { ...base, uso: modelo.usos[seleccion.clave] }
    case 'contribuyente': return { ...base, contribuyente: seleccion.clave }
    default: return base
  }
}

export function etiquetaSeleccion(modelo, seleccion) {
  if (!seleccion) return ''
  switch (seleccion.dimension) {
    case 'zona': return modelo.zonas[seleccion.clave].nombre
    case 'segmento': return `Segmento ${modelo.segmentos[seleccion.clave]}`
    case 'tributo': return modelo.tributos[seleccion.clave].nombre
    case 'uso': return `Uso: ${modelo.usos[seleccion.clave]}`
    case 'contribuyente': return seleccion.nombre
    default: return ''
  }
}

export function describirFiltros(modelo, filtros) {
  const periodo = modelo.periodos[filtros.periodo] ?? modelo.periodos[modelo.periodoInicial]
  const zona = filtros.zona ? modelo.zonas.find((z) => z.codigo === filtros.zona)?.nombre ?? filtros.zona : 'todas las zonas'
  const tributo = filtros.tributo ? modelo.tributos.find((t) => t.codigo === filtros.tributo)?.nombre ?? filtros.tributo : 'todos los tributos'
  const segmento = filtros.segmento || 'todos los segmentos'
  return [periodo.etiqueta, zona, tributo, segmento].join(' · ')
}

/* Clave a resaltar en un gráfico de la dimensión dada: undefined sin selección; null si debe mostrarse el subconjunto. */
export function claveResaltada(seleccion, dimension) {
  if (!seleccion) return undefined
  if (seleccion.dimension === dimension) return seleccion.clave
  if (seleccion.dimension === 'contribuyente') {
    if (dimension === 'zona') return seleccion.zona
    if (dimension === 'segmento') return seleccion.segmento
    if (dimension === 'uso') return seleccion.esPrico ? seleccion.uso : null
  }
  return null
}

export function coincideContribuyente(seleccion, fila) {
  if (!seleccion) return false
  switch (seleccion.dimension) {
    case 'zona': return fila.zona === seleccion.clave
    case 'segmento': return fila.segmento === seleccion.clave
    case 'uso': return fila.uso === seleccion.clave
    case 'contribuyente': return fila.id === seleccion.clave
    default: return false
  }
}

const variacionRelativa = (actual, referencia, mejoraAlSubir) => {
  if (referencia == null || !isFinite(referencia) || referencia === 0 || !isFinite(actual)) return null
  const cambio = ((actual - referencia) / Math.abs(referencia)) * 100
  return { valor: cambio, texto: `${cambio >= 0 ? '+' : '−'}${NF1.format(Math.abs(cambio))} %`, mejora: Math.abs(cambio) < 0.05 ? null : (cambio > 0) === mejoraAlSubir }
}

const variacionPuntos = (actual, referencia, mejoraAlSubir) => {
  if (referencia == null || !isFinite(referencia) || actual == null || !isFinite(actual)) return null
  const cambio = actual - referencia
  return { valor: cambio, texto: `${cambio >= 0 ? '+' : '−'}${NF1.format(Math.abs(cambio))} pp`, mejora: Math.abs(cambio) < 0.05 ? null : (cambio > 0) === mejoraAlSubir }
}

/* Avance de lo cobrado frente a la meta del periodo: 100 % es alcanzar la meta. */
export function avanceMeta(A) {
  if (!A.meta || !(A.meta.valor > 0)) return null
  const cobrado = razon(A.totales.cobrado, A.totales.emitido)
  if (cobrado == null) return null
  const avance = (cobrado / A.meta.valor) * 100
  return { avance, desvio: 100 - avance, umbral: A.meta.umbralDesvio, meta: A.meta.valor, cobrado }
}

const segmentoConMasDeuda = (A, modelo) => {
  let mayor = -1
  A.porSegmento.deuda.forEach((d, s) => { if (d > 0 && (mayor < 0 || d > A.porSegmento.deuda[mayor])) mayor = s })
  return mayor < 0 ? null : { nombre: modelo.segmentos[mayor], deuda: A.porSegmento.deuda[mayor] }
}

export function indicadores(modelo, pestana, A, R) {
  const t = A.totales
  const rt = R?.totales
  const morosidad = razon(t.deuda, t.emitido)
  const morosidadRef = rt ? razon(rt.deuda, rt.emitido) : null
  if (pestana === 'resumen') {
    const meta = avanceMeta(A)
    const metaRef = R ? avanceMeta(R) : null
    return [
      { etiqueta: 'Recaudación frente a emisión', valor: porcentajeTexto(razon(t.cobrado, t.emitido)), detalle: `Cobrado ${solesCompactos(t.cobrado)} de ${solesCompactos(t.emitido)} emitidos · pagos recibidos en el periodo: ${solesCompactos(t.recaudado)}`, variacion: rt && variacionPuntos(razon(t.cobrado, t.emitido), razon(rt.cobrado, rt.emitido), true), ayuda: 'Monto cobrado de las cuotas del periodo ÷ monto emitido del periodo. Los pagos recibidos incluyen deuda de periodos anteriores.' },
      { etiqueta: 'Índice de morosidad', valor: porcentajeTexto(morosidad), detalle: `${solesCompactos(t.deuda)} vencidos sin pagar`, variacion: rt && variacionPuntos(morosidad, morosidadRef, false), ayuda: 'Monto de cuotas vencidas no pagadas del periodo ÷ monto emitido del periodo × 100.' },
      meta
        ? { etiqueta: 'Avance frente a la meta', valor: porcentajeTexto(meta.avance), detalle: `Cobrado ${porcentajeTexto(meta.cobrado)} de la emisión; meta ${porcentajeTexto(meta.meta)}`, alerta: meta.desvio > meta.umbral, marca: meta.desvio > meta.umbral ? { texto: `Desvío de ${NF1.format(meta.desvio)} % bajo la meta (umbral ${NF1.format(meta.umbral)} %)` } : { ok: true, texto: meta.desvio > 0 ? `Dentro del umbral (desvío ${NF1.format(meta.desvio)} %)` : 'Meta cumplida' }, variacion: metaRef && variacionPuntos(meta.avance, metaRef.avance, true), ayuda: 'Porcentaje cobrado de la emisión ÷ meta del indicador de recaudación para el último mes del periodo.' }
        : { etiqueta: 'Avance frente a la meta', valor: '—', detalle: 'No hay una meta registrada para este periodo y estos filtros.', ayuda: 'Configura las metas en el módulo Metas de recaudación.' },
      { etiqueta: 'Contribuyentes activos', valor: entero(t.contribuyentes), detalle: 'Con cuotas emitidas en el periodo', segmentos: A.porSegmento.contribuyentes, variacion: rt && variacionRelativa(t.contribuyentes, rt.contribuyentes, true) },
    ]
  }
  if (pestana === 'pricos') {
    const p = A.pricos
    const rp = R?.pricos
    return [
      { etiqueta: 'PRICOS activos', valor: entero(p.cantidad), detalle: `${porcentajeTexto(razon(p.cantidad, t.contribuyentes))} de los contribuyentes activos`, variacion: rp && variacionRelativa(p.cantidad, rp.cantidad, true) },
      { etiqueta: 'Concentración de la emisión', valor: porcentajeTexto(razon(p.emitido, t.emitido)), detalle: `${solesCompactos(p.emitido)} emitidos a PRICOS`, ayuda: 'Parte de la emisión total que corresponde a los PRICOS.', variacion: rp && variacionPuntos(razon(p.emitido, t.emitido), razon(rp.emitido, rt.emitido), true) },
      { etiqueta: 'Cumplimiento de PRICOS', valor: porcentajeTexto(razon(p.cobrado, p.emitido)), detalle: 'Cobrado de lo emitido a PRICOS', variacion: rp && variacionPuntos(razon(p.cobrado, p.emitido), razon(rp.cobrado, rp.emitido), true) },
      { etiqueta: 'Deuda vencida de PRICOS', valor: solesCompactos(p.deuda), detalle: `${entero(p.morosos)} PRICOS con al menos una cuota vencida`, variacion: rp && variacionRelativa(p.deuda, rp.deuda, false) },
    ]
  }
  if (pestana === 'morosos') {
    const mayor = segmentoConMasDeuda(A, modelo)
    return [
      { etiqueta: 'Deuda vencida', valor: solesCompactos(t.deuda), detalle: 'Cuotas del periodo vencidas y no pagadas', variacion: rt && variacionRelativa(t.deuda, rt.deuda, false) },
      { etiqueta: 'Contribuyentes morosos', valor: entero(t.morosos), detalle: `${porcentajeTexto(razon(t.morosos, t.contribuyentes))} de los contribuyentes activos`, variacion: rt && variacionRelativa(t.morosos, rt.morosos, false) },
      { etiqueta: 'Índice de morosidad', valor: porcentajeTexto(morosidad), detalle: `Sobre ${solesCompactos(t.emitido)} emitidos`, variacion: rt && variacionPuntos(morosidad, morosidadRef, false), ayuda: 'Monto de cuotas vencidas no pagadas del periodo ÷ monto emitido del periodo × 100.' },
      { etiqueta: 'Segmento con más deuda', valor: mayor ? mayor.nombre : '—', detalle: mayor ? `${solesCompactos(mayor.deuda)} (${porcentajeTexto(razon(mayor.deuda, t.deuda))} de la deuda) · promedio por moroso: ${t.morosos ? solesEnteros(t.deuda / t.morosos) : '—'}` : 'Sin deuda vencida en el periodo', ayuda: 'Ayuda a decidir entre una campaña focalizada y la cobranza general.', texto: true },
    ]
  }
  const totalCanal = A.canal.reduce((a, b) => a + b, 0)
  const totalCanalRef = R ? R.canal.reduce((a, b) => a + b, 0) : 0
  const indiceEnLinea = modelo.canales.findIndex((c) => c.codigo === 'EN_LINEA')
  return [
    { etiqueta: 'Buenos pagadores', valor: entero(t.buenosPagadores), detalle: `${porcentajeTexto(razon(t.buenosPagadores, t.contribuyentes))} de los contribuyentes activos`, variacion: rt && variacionRelativa(t.buenosPagadores, rt.buenosPagadores, true) },
    { etiqueta: 'Recaudación que aportan', valor: porcentajeTexto(razon(t.recaudadoBuenosPagadores, t.recaudado)), detalle: `${solesCompactos(t.recaudadoBuenosPagadores)} de ${solesCompactos(t.recaudado)} recaudados`, variacion: rt && variacionPuntos(razon(t.recaudadoBuenosPagadores, t.recaudado), razon(rt.recaudadoBuenosPagadores, rt.recaudado), true) },
    { etiqueta: 'Regularidad de pago', valor: porcentajeTexto(razon(t.cuotasATiempo, t.cuotas)), ayuda: 'Cuotas pagadas hasta su fecha de vencimiento ÷ cuotas emitidas del periodo.', detalle: `${entero(t.cuotasATiempo)} de ${entero(t.cuotas)} cuotas pagadas a tiempo`, variacion: rt && variacionPuntos(razon(t.cuotasATiempo, t.cuotas), razon(rt.cuotasATiempo, rt.cuotas), true) },
    { etiqueta: 'Pagos puntuales en línea', valor: porcentajeTexto(razon(A.canal[indiceEnLinea], totalCanal)), detalle: 'Del total de pagos hechos a tiempo', variacion: R && variacionPuntos(razon(A.canal[indiceEnLinea], totalCanal), razon(R.canal[indiceEnLinea], totalCanalRef), true) },
  ]
}

/* Columnas del ranking en el orden del usuario y las que quedan visibles. */
export function columnasRanking(configuracion, ocultas = [], orden = []) {
  const posicion = (clave) => {
    const i = orden.indexOf(clave)
    return i < 0 ? 100 + configuracion.columnas.findIndex((c) => c.clave === clave) : i
  }
  const todas = [...configuracion.columnas].sort((a, b) => posicion(a.clave) - posicion(b.clave))
  return { todas, visibles: todas.filter((c) => !ocultas.includes(c.clave)) }
}

export function moverColumna(todas, clave, destino) {
  const claves = todas.map((c) => c.clave).filter((c) => c !== clave)
  claves.splice(Math.max(0, Math.min(destino, claves.length)), 0, clave)
  return claves
}

export function ordenarFilasRanking(filas, columna, direccion, puestos) {
  if (!columna) return filas
  return [...filas].sort((a, b) => {
    const va = columna.valor(a, puestos.get(a.id))
    const vb = columna.valor(b, puestos.get(b.id))
    const r = typeof va === 'number' || typeof vb === 'number'
      ? (va ?? -1) - (vb ?? -1)
      : String(va ?? '').localeCompare(String(vb ?? ''), 'es')
    return direccion === 'asc' ? r : -r
  })
}

export function estadoGuardable(estado) {
  const { pestana, filtros, tipos, ordenes, ocultas, ordenColumnas, ordenTabla, fijada } = estado
  return { pestana, filtros, tipos, ordenes, ocultas, ordenColumnas, ordenTabla, fijada }
}

/* Una vista guardada puede venir de una carga con otras zonas o segmentos: lo que ya no existe vuelve a "todos". */
export function normalizarEstado(modelo, guardado, base) {
  const filtros = { ...base.filtros, ...(guardado.filtros || {}) }
  if (!modelo.periodos[filtros.periodo]) filtros.periodo = modelo.periodoInicial
  if (filtros.zona && !modelo.zonas.some((z) => z.codigo === filtros.zona)) filtros.zona = ''
  if (filtros.tributo && !modelo.tributos.some((t) => t.codigo === filtros.tributo)) filtros.tributo = ''
  if (filtros.segmento && !modelo.segmentos.includes(filtros.segmento)) filtros.segmento = ''
  const fijada = guardado.fijada && modelo.periodos[guardado.fijada.periodo] ? guardado.fijada : null
  const pestana = PESTANAS.some((p) => p.id === guardado.pestana) ? guardado.pestana : base.pestana
  return { ...base, ...guardado, pestana, filtros, fijada }
}

/* Encabezados y filas del ranking tal como se ven, para exportarlos. */
export function tablaRanking(columnas, filas, puestos) {
  return {
    encabezados: columnas.map((c) => c.titulo),
    filas: filas.map((fila) => columnas.map((c) => {
      const valor = c.valor(fila, puestos.get(fila.id))
      if (c.clave === 'ultimo') return c.formato(valor)
      return valor ?? '—'
    })),
  }
}

export function vistaExportada({ modelo, estado, seleccion, indicadoresVista, graficos, ranking }) {
  const pestana = PESTANAS.find((p) => p.id === estado.pestana)
  return {
    pestana: estado.pestana,
    reporte: pestana.titulo,
    corte: modelo.catalogo.fechaCorte.split('-').reverse().join('/'),
    filtros: describirFiltros(modelo, estado.filtros),
    comparado: estado.fijada ? describirFiltros(modelo, estado.fijada) : null,
    resaltado: seleccion ? etiquetaSeleccion(modelo, seleccion) : null,
    indicadores: indicadoresVista.map((k) => [k.etiqueta, k.valor, k.detalle || '']),
    graficos,
    ranking,
  }
}
